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
# Point-in-time recovery (PITR=1, docs/database/POINT_IN_TIME_RECOVERY.md): record the exact
# binary-log position the dump's snapshot corresponds to, as a comment in the dump header, so
# pitr-restore.sh knows where to start replaying. Opt-in because it needs the RELOAD and
# REPLICATION CLIENT privileges and binary logging switched on; without it nothing changes.
pitr_args=()
if [ "${PITR:-0}" = "1" ]; then
  if mysqldump --help 2>/dev/null | grep -q -- '--source-data'; then
    pitr_args=(--source-data=2)
  else
    pitr_args=(--master-data=2) # MySQL < 8.0.26
  fi
fi

# Consistent, self-contained dump. --single-transaction = online (no table locks) for InnoDB.
mysqldump --defaults-extra-file="$CNF" \
  --single-transaction --quick --routines --triggers --events \
  --set-gtid-purged=OFF --no-tablespaces --hex-blob \
  ${pitr_args[@]+"${pitr_args[@]}"} \
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

# The binlog coordinates the dump recorded (PITR=1), e.g. mysql-bin.000123 / 4567.
BINLOG_FILE=""; BINLOG_POS=""
if [ "${PITR:-0}" = "1" ]; then
  coords=$(gzip -dc "$OUT" | head -60 | grep -m1 -E "CHANGE (REPLICATION SOURCE|MASTER) TO" || true)
  BINLOG_FILE=$(printf '%s' "$coords" | sed -nE "s/.*_LOG_FILE='([^']+)'.*/\1/p")
  BINLOG_POS=$(printf '%s' "$coords" | sed -nE 's/.*_LOG_POS=([0-9]+).*/\1/p')
  if [ -z "$BINLOG_FILE" ] || [ -z "$BINLOG_POS" ]; then
    echo "ERROR: PITR=1 but the dump recorded no binlog position (is log_bin ON?) — removing $OUT" >&2
    rm -f "$OUT"
    exit 9
  fi
fi

# Sidecar metadata (checksum + facts) for the audit trail.
SHA=$(sha256sum "$OUT" | awk '{print $1}')
cat > "$OUT.meta.json" <<EOF
{"file":"$(basename "$OUT")","database":"$MYSQL_DATABASE","environment":"$ENV_LABEL",
 "createdAt":"$(date -u '+%Y-%m-%dT%H:%M:%SZ')","bytes":$SIZE,"sha256":"$SHA",
 "binlogFile":"$BINLOG_FILE","binlogPos":"$BINLOG_POS"}
EOF

echo "[backup] OK — ${SIZE} bytes, sha256=$SHA"

# ── Retention: delete backups older than RETENTION_DAYS, but never below KEEP_MIN newest ──
mapfile -t all < <(ls -1t "$BACKUP_DIR"/${MYSQL_DATABASE}-${ENV_LABEL}-*.sql.gz 2>/dev/null)
if [ "${#all[@]}" -gt "$KEEP_MIN" ]; then
  for old in $(find "$BACKUP_DIR" -name "${MYSQL_DATABASE}-${ENV_LABEL}-*.sql.gz" -type f -mtime "+${RETENTION_DAYS}"); do
    # Skip if it is among the KEEP_MIN newest.
    keep=false
    for i in $(seq 0 $((KEEP_MIN-1))); do [ "${all[$i]:-}" = "$old" ] && keep=true; done
    $keep || { echo "[retention] pruning $(basename "$old")"; rm -f "$old" "$old.meta.json"; }
  done
fi

# ── Off-site copy (encrypted, different provider) ──────────────────────────
# The local dump above is kept whatever happens here.
OFFSITE="${OFFSITE:-auto}"
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
