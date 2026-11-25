#!/usr/bin/env bash
# Point-in-time restore: last nightly dump + binary logs replayed up to a chosen moment.
#
# Usage: pitr-restore.sh <dump.sql.gz> <binlog-dir> "<YYYY-MM-DD HH:MM:SS>" [target_database]
#   dump.sql.gz   a nightly dump taken with PITR=1 (its header holds the binlog position)
#   binlog-dir    the decrypted binlog files from <prefix>/binlog/ (offsite-fetch.sh), any order
#   stop time     replay changes up to (not including) this moment, in the SERVER's time zone
#   target        default <db>_pitr_check — a scratch DB. The live DB needs FORCE_RESTORE=yes
#                 (enforced by restore.sh, which this calls for the base restore).
#
# Steps: 1) restore.sh restores + validates the dump into the target;
#        2) mysqlbinlog replays, from the dump's recorded position, every change to the live
#           database up to the stop time, rewritten into the target database.
# Runbook (drill it): docs/database/POINT_IN_TIME_RECOVERY.md.
set -euo pipefail
DUMP="${1:?dump.sql.gz required}"
BINDIR="${2:?binlog directory required}"
STOP="${3:?stop time required, e.g. \"2026-12-01 14:30:00\"}"
[ -f "$DUMP" ] || { echo "ERROR: not found: $DUMP" >&2; exit 2; }
[ -d "$BINDIR" ] || { echo "ERROR: not a directory: $BINDIR" >&2; exit 2; }
case "$STOP" in
  [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]\ [0-9][0-9]:[0-9][0-9]:[0-9][0-9]) ;;
  *) echo "ERROR: stop time must look like \"YYYY-MM-DD HH:MM:SS\"" >&2; exit 2 ;;
esac
DIR="$(cd "$(dirname "$0")" && pwd)"

# shellcheck source=scripts/db/load-env.sh
. "$DIR/load-env.sh"
hms_load_env || true
LIVE_DB="${MYSQL_DATABASE:-}"
if [ -z "${MYSQL_HOST:-}" ] && [ -n "${SPRING_DATASOURCE_URL:-}" ]; then
  tmp="${SPRING_DATASOURCE_URL#jdbc:mysql://}"; hostport="${tmp%%/*}"
  MYSQL_HOST="${hostport%%:*}"; p="${hostport#*:}"; [ "$p" = "$hostport" ] && p=3306
  MYSQL_PORT="${MYSQL_PORT:-$p}"; rest="${tmp#*/}"; LIVE_DB="${LIVE_DB:-${rest%%\?*}}"
fi
[ -n "$LIVE_DB" ] || { echo "ERROR: could not determine the live database name" >&2; exit 2; }
MYSQL_HOST="${MYSQL_HOST:-localhost}"; MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-${SPRING_DATASOURCE_USERNAME:-root}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"
TARGET="${4:-${LIVE_DB}_pitr_check}"

# ── Where the dump's snapshot sits in the binlog stream ────────────────────
coords=$(gzip -dc "$DUMP" | head -60 | grep -m1 -E "CHANGE (REPLICATION SOURCE|MASTER) TO" || true)
START_FILE=$(printf '%s' "$coords" | sed -nE "s/.*_LOG_FILE='([^']+)'.*/\1/p")
START_POS=$(printf '%s' "$coords" | sed -nE 's/.*_LOG_POS=([0-9]+).*/\1/p')
if [ -z "$START_FILE" ] || [ -z "$START_POS" ]; then
  echo "ERROR: $DUMP has no binlog position — it was not taken with PITR=1" >&2
  exit 3
fi

# Binlogs from the start file on, in order (names sort: mysql-bin.000123, ...).
mapfile -t files < <(find "$BINDIR" -maxdepth 1 -type f -name "${START_FILE%.*}.*" \
  ! -name '*.index' | sort | awk -v s="$BINDIR/$START_FILE" '$0 >= s')
[ "${#files[@]}" -gt 0 ] || { echo "ERROR: $START_FILE (the dump's start) is not in $BINDIR" >&2; exit 4; }
[ "${files[0]}" = "$BINDIR/$START_FILE" ] || {
  echo "ERROR: $START_FILE is missing from $BINDIR — the replay would skip changes" >&2; exit 4; }
# A gap in the sequence would silently drop changes: refuse it.
prev=""
for f in "${files[@]}"; do
  n=$((10#${f##*.}))
  if [ -n "$prev" ] && [ "$n" -ne $((prev + 1)) ]; then
    echo "ERROR: binlog sequence has a gap before $(basename "$f") — fetch the missing file(s)" >&2
    exit 5
  fi
  prev=$n
done

echo "[pitr] base: $DUMP (binlog $START_FILE @ $START_POS) → database '$TARGET'"
bash "$DIR/restore.sh" "$DUMP" "$TARGET"

CNF="$(mktemp)"; chmod 600 "$CNF"
trap 'rm -f "$CNF"' EXIT
cat > "$CNF" <<EOF
[client]
host=$MYSQL_HOST
port=$MYSQL_PORT
user=$MYSQL_USER
password=$MYSQL_PASSWORD
EOF

echo "[pitr] replaying ${#files[@]} binlog file(s) up to $STOP ..."
# --start-position applies to the first file only. --database keeps only the HMS database's
# changes; --rewrite-db lands them in the target. --skip-gtids: replay into a different server/db.
mysqlbinlog --skip-gtids --start-position="$START_POS" --stop-datetime="$STOP" \
  --database="$LIVE_DB" --rewrite-db="$LIVE_DB->$TARGET" "${files[@]}" \
  | mysql --defaults-extra-file="$CNF" "$TARGET"

TABLES=$(mysql --defaults-extra-file="$CNF" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TARGET'")
LAST=$(mysql --defaults-extra-file="$CNF" -N -e \
  "SELECT COALESCE(MAX(timestamp),'-') FROM \`$TARGET\`.audit_logs" 2>/dev/null || echo "-")
echo "[pitr] OK — '$TARGET' restored to $STOP ($TABLES tables; newest audit entry: $LAST)"
echo "[pitr] Check it, then follow the runbook to switch over. Scratch: DROP DATABASE \`$TARGET\`;"
