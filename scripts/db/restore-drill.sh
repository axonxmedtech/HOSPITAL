#!/usr/bin/env bash
# Automated restore drill (weekly, .github/workflows/db-restore-drill.yml). Runs ON the VPS.
#
# Proves the newest nightly backup can actually be restored — a backup nobody has restored is not
# a backup. Fails (non-zero, so the workflow fails and GitHub emails it) when:
#   * there is no backup, or the newest is older than MAX_AGE_HOURS (the nightly job stopped),
#   * restore.sh cannot restore it into a scratch database,
#   * a core table is missing or empty in the copy while it has rows live, or the copy has MORE
#     rows than live (the wrong database was dumped), or the table count is far from live.
# The scratch database is dropped afterwards, pass or fail. Live data is only read, never written.
#
# Config: BACKUP_DIR (default /var/backups/hms), ENV_LABEL, MAX_AGE_HOURS (default 26),
#         DRILL_LOG (default $BACKUP_DIR/restore-drills.log — one line per drill, the record).
set -euo pipefail
BACKUP_DIR="${BACKUP_DIR:-/var/backups/hms}"
ENV_LABEL="${ENV_LABEL:-production}"
MAX_AGE_HOURS="${MAX_AGE_HOURS:-26}"
DRILL_LOG="${DRILL_LOG:-$BACKUP_DIR/restore-drills.log}"
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
SCRATCH="${LIVE_DB}_restore_drill"

CNF="$(mktemp)"; chmod 600 "$CNF"
cat > "$CNF" <<EOF
[client]
host=$MYSQL_HOST
port=$MYSQL_PORT
user=$MYSQL_USER
password=$MYSQL_PASSWORD
EOF
q() { mysql --defaults-extra-file="$CNF" -N -B -e "$1"; }
cleanup() {
  q "DROP DATABASE IF EXISTS \`$SCRATCH\`" >/dev/null 2>&1 || true
  rm -f "$CNF"
}
trap cleanup EXIT

record() { # <PASS|FAIL> <message>
  printf '%s %s %s %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$1" "${NEWEST:-none}" "$2" >> "$DRILL_LOG" || true
}
fail() { echo "ERROR: $*" >&2; record FAIL "$*"; exit 1; }

# ── The newest backup, and is it fresh? ────────────────────────────────────
NEWEST=$(ls -1t "$BACKUP_DIR"/"${LIVE_DB}-${ENV_LABEL}"-*.sql.gz 2>/dev/null | head -1 || true)
[ -n "$NEWEST" ] || fail "no backup found in $BACKUP_DIR for ${LIVE_DB}-${ENV_LABEL}"
mtime=$(stat -c '%Y' "$NEWEST" 2>/dev/null || stat -f '%m' "$NEWEST")
age_h=$(( ($(date +%s) - mtime) / 3600 ))
[ "$age_h" -le "$MAX_AGE_HOURS" ] || fail "newest backup $(basename "$NEWEST") is ${age_h}h old (max ${MAX_AGE_HOURS}h) — the nightly backup has stopped"
echo "[drill] newest backup: $(basename "$NEWEST") (${age_h}h old)"

# ── Restore into the scratch DB (restore.sh verifies the dump first) ───────
q "DROP DATABASE IF EXISTS \`$SCRATCH\`"
bash "$DIR/restore.sh" "$NEWEST" "$SCRATCH" || fail "restore.sh could not restore $(basename "$NEWEST")"

# ── Compare the copy with live ─────────────────────────────────────────────
count_tables() { q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$1'"; }
live_tables=$(count_tables "$LIVE_DB"); copy_tables=$(count_tables "$SCRATCH")
diff=$(( live_tables - copy_tables )); [ "$diff" -lt 0 ] && diff=$(( -diff ))
# A migration deployed since the dump adds a table or two; dozens missing means a broken dump.
[ "$diff" -le 5 ] || fail "copy has $copy_tables tables, live has $live_tables"

for t in hospitals users patients audit_logs; do
  live=$(q "SELECT COUNT(*) FROM \`$LIVE_DB\`.\`$t\`" 2>/dev/null) || fail "live table $t unreadable"
  copy=$(q "SELECT COUNT(*) FROM \`$SCRATCH\`.\`$t\`" 2>/dev/null) || fail "table $t missing from the restored copy"
  if [ "$live" -gt 0 ] && [ "$copy" -eq 0 ]; then fail "table $t is empty in the copy but has $live rows live"; fi
  if [ "$copy" -gt "$live" ]; then fail "table $t has more rows in the copy ($copy) than live ($live) — wrong database dumped?"; fi
  echo "[drill] $t: $copy restored / $live live"
done

record PASS "$copy_tables tables restored"
echo "[drill] PASSED — $(basename "$NEWEST") restores cleanly ($copy_tables tables). Scratch DB dropped."
