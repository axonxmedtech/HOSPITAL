#!/usr/bin/env bash
# Encrypt a verified backup and copy it OFF the VPS to S3-compatible object storage.
#
# Why: backup.sh keeps dumps on the same server as the database. If that server is lost, so are the
# backups. This script puts an encrypted copy with a different provider.
#
# Usage: offsite-copy.sh <backup.sql.gz>
#
# Encryption: age (https://age-encryption.org), PUBLIC-KEY. The server holds only the recipient
# public key, so neither the server nor the bucket can decrypt a copy. The matching private key
# (identity) is kept OFF the server by the owner (password manager + one printed copy). Without
# it no off-site copy can be restored. See docs/database/OFFSITE_BACKUPS.md.
#
# Layout in the bucket:
#   <prefix>/daily/<file>.age     every run
#   <prefix>/monthly/<file>.age   additionally, the first run of each calendar month (UTC)
# Expiry is a bucket lifecycle rule (daily ~35 days, monthly ~400 days). This script never deletes
# anything, so its access key needs no delete permission. A stolen key can add files, not erase them.
#
# Config: OFFSITE_CONFIG (default /etc/hms/offsite.env, must be chmod 600), plain KEY=VALUE lines:
#   OFFSITE_S3_BUCKET       bucket name                                  (required)
#   OFFSITE_S3_PREFIX       key prefix, e.g. hms/production              (default hms/<ENV_LABEL>)
#   OFFSITE_S3_REGION       e.g. ap-south-1 (Mumbai)                     (default ap-south-1)
#   OFFSITE_S3_ENDPOINT     only for non-AWS providers (R2, B2, MinIO)   (optional)
#   OFFSITE_AGE_RECIPIENT   age public key, "age1..."                    (required)
#   AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY  write-only key for that bucket (required)
# Exit: 0 when the encrypted copy is uploaded and its size verified; non-zero otherwise.
set -euo pipefail

SRC="${1:?backup file required}"
[ -f "$SRC" ] || { echo "ERROR: backup not found: $SRC" >&2; exit 2; }

OFFSITE_CONFIG="${OFFSITE_CONFIG:-/etc/hms/offsite.env}"
ENV_LABEL="${ENV_LABEL:-production}"
AWS_CMD="${AWS_CMD:-aws}"   # overridable for tests

[ -f "$OFFSITE_CONFIG" ] || { echo "ERROR: off-site config not found: $OFFSITE_CONFIG" >&2; exit 10; }

# The config holds a storage key: refuse one that other users can read.
perm=$(stat -c '%a' "$OFFSITE_CONFIG" 2>/dev/null || stat -f '%Lp' "$OFFSITE_CONFIG")
case "$perm" in
  600 | 400) ;;
  *) echo "ERROR: $OFFSITE_CONFIG is mode $perm; run: chmod 600 $OFFSITE_CONFIG" >&2; exit 11 ;;
esac

# Parse, never source (same reasoning as load-env.sh). Only the known keys are read.
while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  line="${line#"${line%%[![:space:]]*}"}"
  case "$line" in '' | '#'*) continue ;; esac
  line="${line#export }"
  case "$line" in *=*) ;; *) continue ;; esac
  key="${line%%=*}"; val="${line#*=}"
  case "$val" in \"*\") val="${val#\"}"; val="${val%\"}" ;; \'*\') val="${val#\'}"; val="${val%\'}" ;; esac
  case "$key" in
    OFFSITE_S3_BUCKET | OFFSITE_S3_PREFIX | OFFSITE_S3_REGION | OFFSITE_S3_ENDPOINT | OFFSITE_AGE_RECIPIENT | AWS_ACCESS_KEY_ID | AWS_SECRET_ACCESS_KEY)
      export "$key=$val" ;;
  esac
done < "$OFFSITE_CONFIG"

missing=""
for k in OFFSITE_S3_BUCKET OFFSITE_AGE_RECIPIENT AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
  [ -n "${!k:-}" ] || missing="$missing $k"
done
[ -z "$missing" ] || { echo "ERROR: missing in $OFFSITE_CONFIG:$missing" >&2; exit 12; }
case "$OFFSITE_AGE_RECIPIENT" in
  age1*) ;;
  *) echo "ERROR: OFFSITE_AGE_RECIPIENT must be an age public key (age1...), never a private key" >&2; exit 13 ;;
esac

command -v age > /dev/null || { echo "ERROR: 'age' is not installed (apt install age)" >&2; exit 14; }

PREFIX="${OFFSITE_S3_PREFIX:-hms/$ENV_LABEL}"
PREFIX="${PREFIX%/}"
export AWS_DEFAULT_REGION="${OFFSITE_S3_REGION:-ap-south-1}"
aws_args=()
[ -n "${OFFSITE_S3_ENDPOINT:-}" ] && aws_args+=(--endpoint-url "$OFFSITE_S3_ENDPOINT")

NAME="$(basename "$SRC").age"
ENC="$(dirname "$SRC")/.$NAME.partial"
cleanup() { rm -f "$ENC"; }
trap cleanup EXIT

# ── Encrypt (to a private temp file next to the backup) ────────────────────
umask 077
age --encrypt --recipient "$OFFSITE_AGE_RECIPIENT" --output "$ENC" "$SRC"
BYTES=$(stat -c '%s' "$ENC" 2>/dev/null || wc -c < "$ENC" | tr -d ' ')
SHA=$(sha256sum "$ENC" 2>/dev/null | awk '{print $1}' || shasum -a 256 "$ENC" | awk '{print $1}')

upload() { # <s3 key>
  "$AWS_CMD" "${aws_args[@]}" s3 cp "$ENC" "s3://$OFFSITE_S3_BUCKET/$1" --only-show-errors \
    --metadata "sha256=$SHA,source=$(basename "$SRC")"
  # Prove it landed: the stored size must equal what we encrypted.
  got=$("$AWS_CMD" "${aws_args[@]}" s3api head-object --bucket "$OFFSITE_S3_BUCKET" --key "$1" \
    --query ContentLength --output text)
  if [ "$got" != "$BYTES" ]; then
    echo "ERROR: off-site copy $1 is $got bytes, expected $BYTES" >&2
    return 20
  fi
  echo "[offsite] OK — s3://$OFFSITE_S3_BUCKET/$1 ($BYTES bytes, sha256=$SHA)"
}

upload "$PREFIX/daily/$NAME"

# First backup of the month also goes to monthly/ (kept ~13 months by the lifecycle rule).
MONTH_MARK="$(dirname "$SRC")/.offsite-monthly-$ENV_LABEL"
this_month="$(date -u '+%Y-%m')"
if [ "$(cat "$MONTH_MARK" 2>/dev/null || true)" != "$this_month" ]; then
  upload "$PREFIX/monthly/$NAME"
  echo "$this_month" > "$MONTH_MARK"
fi
