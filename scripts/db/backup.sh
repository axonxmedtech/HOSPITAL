#!/usr/bin/env bash
# Automated MySQL backup for HMS — runs ON the VPS. Timestamped, gzipped, verified, and pruned by
# retention. NEVER overwrites an existing backup. Intended to run on a schedule AND before any
# production schema change.
#
# Connection (in priority order):
#   MYSQL_HOST/PORT/DATABASE/USER/PASSWORD, else parsed from SPRING_DATASOURCE_URL +
#   SPRING_DATASOURCE_USERNAME/PASSWORD (source the app's env file first).
# Config:
#   BACKUP_DIR      (default /var/backups/hms)
#   ENV_LABEL       (default production) — used in the filename
#   RETENTION_DAYS  (default 30)   KEEP_MIN (default 7 — never prune below this many)
#   OFFSITE         auto (default) | required | skip — encrypted copy off the VPS via
#                   offsite-copy.sh (config: OFFSITE_CONFIG, default /etc/hms/offsite.env).
#                   auto: copy when configured, a failed copy is a warning (pre-deploy use).
#                   required: no config or a failed copy fails the run (the scheduled backup).
# Exit: 0 on a verified backup; non-zero on any failure (caller should stop the deploy).
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/hms}"
ENV_LABEL="${ENV_LABEL:-production}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
KEEP_MIN="${KEEP_MIN:-7}"
TS() { date -u '+%Y%m%dT%H%M%SZ'; }

# ── Load the app env file (safe parse — never `.` it; see load-env.sh) ─────
# shellcheck source=scripts/db/load-env.sh
. "$(dirname "$0")/load-env.sh"
hms_load_env || true

# ── Resolve connection ─────────────────────────────────────────────────────
if [ -z "${MYSQL_HOST:-}" ] && [ -n "${SPRING_DATASOURCE_URL:-}" ]; then
  tmp="${SPRING_DATASOURCE_URL#jdbc:mysql://}"
  hostport="${tmp%%/*}"
  MYSQL_HOST="${hostport%%:*}"
  p="${hostport#*:}"; [ "$p" = "$hostport" ] && p=3306
  MYSQL_PORT="${MYSQL_PORT:-$p}"
  rest="${tmp#*/}"; MYSQL_DATABASE="${MYSQL_DATABASE:-${rest%%\?*}}"
fi
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-${SPRING_DATASOURCE_USERNAME:-root}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"
# No hardcoded database default on purpose. A default here is how a broken SPRING_DATASOURCE_URL
# turned into "dump a database that isn't ours" instead of a visible failure.
MYSQL_DATABASE="${MYSQL_DATABASE:-}"

if [ -z "$MYSQL_DATABASE" ]; then
  echo "ERROR: could not determine the database name." >&2
  echo "  env file loaded: ${HMS_ENV_FILE:-<none found>}" >&2
  echo "  SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:+<set>}${SPRING_DATASOURCE_URL:-<empty>}" >&2
  echo "  Set MYSQL_DATABASE explicitly, or point ENV_FILE at a file defining SPRING_DATASOURCE_URL." >&2
  exit 2
fi

mkdir -p "$BACKUP_DIR"
CNF="$(mktemp)"; chmod 600 "$CNF"
cat > "$CNF" <<EOF
[client]
host=$MYSQL_HOST
port=$MYSQL_PORT
user=$MYSQL_USER
password=$MYSQL_PASSWORD
EOF
cleanup() { rm -f "$CNF"; }
trap cleanup EXIT

OUT="$BACKUP_DIR/${MYSQL_DATABASE}-${ENV_LABEL}-$(TS).sql.gz"
if [ -e "$OUT" ]; then
  echo "ERROR: backup target already exists (refusing to overwrite): $OUT" >&2
  exit 3
fi

echo "[backup] $MYSQL_DATABASE @ $MYSQL_HOST:$MYSQL_PORT → $OUT"
# Consistent, self-contained dump. --single-transaction = online (no table locks) for InnoDB.
mysqldump --defaults-extra-file="$CNF" \
  --single-transaction --quick --routines --triggers --events \
  --set-gtid-purged=OFF --no-tablespaces --hex-blob \
  "$MYSQL_DATABASE" | gzip -9 > "$OUT"

# ── Verify ─────────────────────────────────────────────────────────────────
if ! gzip -t "$OUT" 2>/dev/null; then
  echo "ERROR: backup failed gzip integrity check — removing $OUT" >&2
  rm -f "$OUT"
  exit 4
fi
SIZE=$(stat -c '%s' "$OUT" 2>/dev/null || wc -c < "$OUT")
if [ "${SIZE:-0}" -lt 1024 ]; then
  echo "ERROR: backup suspiciously small (${SIZE} bytes) — removing $OUT" >&2
  rm -f "$OUT"
  exit 5
fi
# Must contain a recognizable dump footer (dump completed).
if ! gzip -dc "$OUT" | tail -5 | grep -qi 'Dump completed'; then
  echo "WARNING: backup missing 'Dump completed' footer — keeping but flag for review." >&2
fi

# Sidecar metadata (checksum + facts) for the audit trail.
SHA=$(sha256sum "$OUT" | awk '{print $1}')
chmod 600 "$OUT" 2>/dev/null || true

# ── Local encryption with Strong Master Key (AES-256-CBC with PBKDF2) ──────
BACKUP_ENV="${BACKUP_ENV:-/etc/hms/backup.env}"
if [ -z "${BACKUP_SECRET_KEY:-}" ] && [ -f "$BACKUP_ENV" ]; then
  # Parse BACKUP_SECRET_KEY safely from /etc/hms/backup.env
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    line="${line#"${line%%[![:space:]]*}"}"
    case "$line" in '' | '#'*) continue ;; esac
    line="${line#export }"
    case "$line" in *=*) ;; *) continue ;; esac
    key="${line%%=*}"; val="${line#*=}"
    case "$val" in \"*\") val="${val#\"}"; val="${val%\"}" ;; \'*\') val="${val#\'}"; val="${val%\'}" ;; esac
    if [ "$key" = "BACKUP_SECRET_KEY" ]; then
      BACKUP_SECRET_KEY="$val"
    fi
  done < "$BACKUP_ENV"
fi

ENC_FILE=""
ENC_SIZE=0
ENC_SHA=""
if [ -n "${BACKUP_SECRET_KEY:-}" ]; then
  ENC_FILE="$OUT.enc"
  umask 027
  openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt -in "$OUT" -out "$ENC_FILE" -pass "pass:$BACKUP_SECRET_KEY"
  ENC_SIZE=$(stat -c '%s' "$ENC_FILE" 2>/dev/null || wc -c < "$ENC_FILE" | tr -d ' ')
  ENC_SHA=$(sha256sum "$ENC_FILE" 2>/dev/null | awk '{print $1}' || shasum -a 256 "$ENC_FILE" | awk '{print $1}')
  chmod 640 "$ENC_FILE" 2>/dev/null || true
  echo "[backup] Encrypted (AES-256) → $ENC_FILE ($ENC_SIZE bytes, sha256=$ENC_SHA)"
else
  echo "[backup] WARNING: BACKUP_SECRET_KEY not set; backup remains unencrypted." >&2
fi

cat > "$OUT.meta.json" <<EOF
{"file":"$(basename "$OUT")","database":"$MYSQL_DATABASE","environment":"$ENV_LABEL",
 "createdAt":"$(date -u '+%Y-%m-%dT%H:%M:%SZ')","bytes":$SIZE,"sha256":"$SHA",
 "encFile":"${ENC_FILE:+$(basename "$ENC_FILE")}","encBytes":$ENC_SIZE,"encSha256":"$ENC_SHA",
 "encrypted":$( [ -n "$ENC_FILE" ] && echo "true" || echo "false" )}
EOF
chmod 640 "$OUT.meta.json" 2>/dev/null || true

echo "[backup] OK — ${SIZE} bytes, sha256=$SHA"

# ── Retention: delete backups older than RETENTION_DAYS, but never below KEEP_MIN newest ──
mapfile -t all < <(ls -1t "$BACKUP_DIR"/${MYSQL_DATABASE}-${ENV_LABEL}-*.sql.gz 2>/dev/null)
if [ "${#all[@]}" -gt "$KEEP_MIN" ]; then
  for old in $(find "$BACKUP_DIR" -name "${MYSQL_DATABASE}-${ENV_LABEL}-*.sql.gz" -type f -mtime "+${RETENTION_DAYS}"); do
    # Skip if it is among the KEEP_MIN newest.
    keep=false
    for i in $(seq 0 $((KEEP_MIN-1))); do [ "${all[$i]:-}" = "$old" ] && keep=true; done
    $keep || { echo "[retention] pruning $(basename "$old")"; rm -f "$old" "$old.enc" "$old.meta.json"; }
  done
fi

# ── Off-site copy (encrypted, different provider) ──────────────────────────
# The local dump above is kept whatever happens here.
OFFSITE="${OFFSITE:-skip}"
OFFSITE_CONFIG="${OFFSITE_CONFIG:-/etc/hms/offsite.env}"
export OFFSITE_CONFIG ENV_LABEL
case "$OFFSITE" in
  skip) echo "[offsite] skipped (OFFSITE=skip)" ;;
  auto | required)
    if [ ! -f "$OFFSITE_CONFIG" ]; then
      if [ "$OFFSITE" = required ]; then
        echo "ERROR: off-site copy required but $OFFSITE_CONFIG does not exist (local backup kept: $OUT)" >&2
        echo "  Set it up: docs/database/OFFSITE_BACKUPS.md" >&2
        exit 6
      fi
      echo "WARNING: no off-site copy — $OFFSITE_CONFIG not configured. This backup exists ONLY on this server." >&2
    elif ! bash "$(dirname "$0")/offsite-copy.sh" "$OUT"; then
      if [ "$OFFSITE" = required ]; then
        echo "ERROR: off-site copy failed (local backup kept: $OUT)" >&2
        exit 7
      fi
      echo "WARNING: off-site copy failed — this backup exists ONLY on this server." >&2
    fi
    ;;
  *) echo "ERROR: OFFSITE must be auto, required or skip (got '$OFFSITE')" >&2; exit 8 ;;
esac

echo "$OUT"
