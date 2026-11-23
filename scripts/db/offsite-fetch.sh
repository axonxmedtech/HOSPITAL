#!/usr/bin/env bash
# Download an off-site backup and decrypt it back to a .sql.gz that restore.sh accepts.
#
# Usage:
#   offsite-fetch.sh list [daily|monthly]                    # newest last
#   offsite-fetch.sh get <file.sql.gz.age> <identity-file> [out-dir]
#
# <identity-file> is the age PRIVATE key the owner keeps off the server. Put it on the machine
# only for the restore, then delete it (shred -u). Reads the same OFFSITE_CONFIG as offsite-copy.sh
# (bucket, prefix, region, endpoint, access key); a read-capable key is needed for `get`.
# Then:  bash scripts/db/restore.sh <out-dir>/<file>.sql.gz      # scratch DB + validation
set -euo pipefail

OFFSITE_CONFIG="${OFFSITE_CONFIG:-/etc/hms/offsite.env}"
ENV_LABEL="${ENV_LABEL:-production}"
AWS_CMD="${AWS_CMD:-aws}"
[ -f "$OFFSITE_CONFIG" ] || { echo "ERROR: off-site config not found: $OFFSITE_CONFIG" >&2; exit 10; }

while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  line="${line#"${line%%[![:space:]]*}"}"
  case "$line" in '' | '#'*) continue ;; esac
  line="${line#export }"
  case "$line" in *=*) ;; *) continue ;; esac
  key="${line%%=*}"; val="${line#*=}"
  case "$val" in \"*\") val="${val#\"}"; val="${val%\"}" ;; \'*\') val="${val#\'}"; val="${val%\'}" ;; esac
  case "$key" in
    OFFSITE_S3_BUCKET | OFFSITE_S3_PREFIX | OFFSITE_S3_REGION | OFFSITE_S3_ENDPOINT | AWS_ACCESS_KEY_ID | AWS_SECRET_ACCESS_KEY)
      export "$key=$val" ;;
  esac
done < "$OFFSITE_CONFIG"
: "${OFFSITE_S3_BUCKET:?OFFSITE_S3_BUCKET missing in $OFFSITE_CONFIG}"

PREFIX="${OFFSITE_S3_PREFIX:-hms/$ENV_LABEL}"; PREFIX="${PREFIX%/}"
export AWS_DEFAULT_REGION="${OFFSITE_S3_REGION:-ap-south-1}"
aws_args=()
[ -n "${OFFSITE_S3_ENDPOINT:-}" ] && aws_args+=(--endpoint-url "$OFFSITE_S3_ENDPOINT")

cmd="${1:-}"
case "$cmd" in
  list)
    tier="${2:-daily}"
    case "$tier" in daily | monthly) ;; *) echo "ERROR: tier must be daily or monthly" >&2; exit 2 ;; esac
    "$AWS_CMD" "${aws_args[@]}" s3 ls "s3://$OFFSITE_S3_BUCKET/$PREFIX/$tier/" | sort
    ;;
  get)
    name="${2:?file name required (see: offsite-fetch.sh list)}"
    identity="${3:?age identity (private key) file required}"
    out="${4:-/var/backups/hms/restore}"
    [ -f "$identity" ] || { echo "ERROR: identity file not found: $identity" >&2; exit 2; }
    command -v age > /dev/null || { echo "ERROR: 'age' is not installed (apt install age)" >&2; exit 14; }
    case "$name" in */* | *..*) echo "ERROR: give the bare file name, not a path" >&2; exit 2 ;; esac
    case "$name" in *.sql.gz.age) ;; *) echo "ERROR: expected a .sql.gz.age file" >&2; exit 2 ;; esac
    umask 077
    mkdir -p "$out"
    enc="$out/$name"; plain="$out/${name%.age}"
    [ -e "$plain" ] && { echo "ERROR: $plain already exists (refusing to overwrite)" >&2; exit 3; }
    ok=false
    # On any failure leave nothing behind: no ciphertext, no half-written plaintext.
    trap '$ok || rm -f "$enc" "$plain"' EXIT
    tier=daily
    "$AWS_CMD" "${aws_args[@]}" s3api head-object --bucket "$OFFSITE_S3_BUCKET" --key "$PREFIX/daily/$name" > /dev/null 2>&1 || tier=monthly
    "$AWS_CMD" "${aws_args[@]}" s3 cp "s3://$OFFSITE_S3_BUCKET/$PREFIX/$tier/$name" "$enc" --only-show-errors
    age --decrypt --identity "$identity" --output "$plain" "$enc"
    rm -f "$enc"
    gzip -t "$plain" || { echo "ERROR: decrypted file is not a valid gzip" >&2; exit 4; }
    ok=true
    echo "[offsite] decrypted → $plain"
    echo "Next: bash scripts/db/restore.sh '$plain'   (restores into a scratch DB and validates)"
    ;;
  *)
    echo "usage: $0 list [daily|monthly] | get <file.sql.gz.age> <identity-file> [out-dir]" >&2
    exit 2
    ;;
esac
