#!/usr/bin/env bash
# Ship MySQL binary logs OFF the VPS, encrypted, for point-in-time recovery (PITR).
#
# The nightly dump (backup.sh, PITR=1) is the base; these binlogs are every change since. With both
# off-site, losing the server costs minutes of data (this script's interval), not a day.
#
# What it does, every run (a systemd timer, every 5 minutes — scripts/db/systemd/):
#   1. FLUSH BINARY LOGS, so the changes of the last few minutes land in a closed file.
#   2. For every closed binlog not shipped yet: encrypt (age) and upload to <prefix>/binlog/ via
#      offsite-copy.sh, then record its name in the state file. The open (current) log is never
#      shipped half-written.
#   Nothing is ever deleted here: MySQL expires local binlogs (binlog_expire_logs_seconds) and a
#   bucket lifecycle rule expires the off-site copies.
#
# Runs as root: the binlog files live in MySQL's datadir. Needs a MySQL login with RELOAD and
# REPLICATION CLIENT (same env resolution as backup.sh). Setup: docs/database/POINT_IN_TIME_RECOVERY.md.
#
# Config: BINLOG_STATE (default /var/lib/hms/binlog-shipped), OFFSITE_CONFIG (as offsite-copy.sh).
# Exit: 0 when every closed binlog is off-site; non-zero on the first failure (the timer retries).
set -euo pipefail

BINLOG_STATE="${BINLOG_STATE:-/var/lib/hms/binlog-shipped}"
BINLOG_WORK="${BINLOG_WORK:-/var/lib/hms/binlog-work}"   # never write into MySQL's datadir
ENV_LABEL="${ENV_LABEL:-production}"
export ENV_LABEL OFFSITE_CONFIG="${OFFSITE_CONFIG:-/etc/hms/offsite.env}"
DIR="$(cd "$(dirname "$0")" && pwd)"

# shellcheck source=scripts/db/load-env.sh
. "$DIR/load-env.sh"
hms_load_env || true
if [ -z "${MYSQL_HOST:-}" ] && [ -n "${SPRING_DATASOURCE_URL:-}" ]; then
  tmp="${SPRING_DATASOURCE_URL#jdbc:mysql://}"; hostport="${tmp%%/*}"
  MYSQL_HOST="${hostport%%:*}"; p="${hostport#*:}"; [ "$p" = "$hostport" ] && p=3306
  MYSQL_PORT="${MYSQL_PORT:-$p}"
fi
MYSQL_HOST="${MYSQL_HOST:-localhost}"; MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-${SPRING_DATASOURCE_USERNAME:-root}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"

CNF="$(mktemp)"; chmod 600 "$CNF"
umask 077
mkdir -p "$BINLOG_WORK"
trap 'rm -f "$CNF"; rm -f "$BINLOG_WORK"/* 2>/dev/null || true' EXIT
cat > "$CNF" <<EOF
[client]
host=$MYSQL_HOST
port=$MYSQL_PORT
user=$MYSQL_USER
password=$MYSQL_PASSWORD
EOF
sql() { mysql --defaults-extra-file="$CNF" -N -B -e "$1"; }

if [ "$(sql 'SELECT @@log_bin')" != "1" ]; then
  echo "ERROR: binary logging is OFF (log_bin). See docs/database/POINT_IN_TIME_RECOVERY.md" >&2
  exit 2
fi
BASE="$(sql 'SELECT @@log_bin_basename')"   # e.g. /var/lib/mysql/mysql-bin
BINDIR="$(dirname "$BASE")"

mkdir -p "$(dirname "$BINLOG_STATE")"
touch "$BINLOG_STATE"

sql 'FLUSH BINARY LOGS'

# Every binlog MySQL still has, oldest first; the last one is the open log -> skip it.
mapfile -t logs < <(sql 'SHOW BINARY LOGS' | awk '{print $1}')
[ "${#logs[@]}" -ge 2 ] || { echo "[binlog] nothing closed to ship yet"; exit 0; }
unset 'logs[${#logs[@]}-1]'

shipped=0
for name in "${logs[@]}"; do
  grep -qxF "$name" "$BINLOG_STATE" && continue
  file="$BINDIR/$name"
  [ -f "$file" ] || { echo "ERROR: binlog $file is listed by MySQL but not readable" >&2; exit 3; }
  # Encrypt a private copy (offsite-copy writes its temp file next to its input, and that must
  # never be MySQL's datadir), then drop the copy.
  cp "$file" "$BINLOG_WORK/$name"
  bash "$DIR/offsite-copy.sh" "$BINLOG_WORK/$name" binlog
  rm -f "$BINLOG_WORK/$name"
  echo "$name" >> "$BINLOG_STATE"
  shipped=$((shipped + 1))
done
echo "[binlog] OK — shipped $shipped closed binlog(s); all closed binlogs are off-site"
