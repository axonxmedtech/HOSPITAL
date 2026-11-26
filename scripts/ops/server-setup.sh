#!/usr/bin/env bash
# One-time server setup for the launch-readiness work: point-in-time recovery (binlogs, grants,
# shipping timer) and the Prometheus scrape token. Runs ON the VPS, as a sudo-capable user.
#
#   bash scripts/ops/server-setup.sh check   <staging|production>            read-only report (default)
#   bash scripts/ops/server-setup.sh apply   <staging|production> [options]  do what is missing
#
# apply options:
#   --dry-run          print every action, change nothing
#   --restart-mysql    allowed to restart MySQL (needed once, to turn binlogs on: ~10-30 s outage)
#   --restart-backend  allowed to restart the backend service (needed to pick up the scrape token)
#
# Safe to run again: every step checks first and skips what is already done. Without the restart
# flags it never restarts anything — it tells you a restart is pending instead.
# What each step means: docs/database/POINT_IN_TIME_RECOVERY.md, docs/monitoring/OBSERVABILITY.md.
#
# If staging and production share ONE MySQL server, binlogs are per server: enable the shipping
# timer for production only (it covers both databases; restores filter by database).
set -euo pipefail

MODE="${1:-check}"
ENV_NAME="${2:-}"
shift 2 2>/dev/null || true
DRY=0; RESTART_MYSQL=0; RESTART_BACKEND=0
for a in "$@"; do
  case "$a" in
    --dry-run) DRY=1 ;;
    --restart-mysql) RESTART_MYSQL=1 ;;
    --restart-backend) RESTART_BACKEND=1 ;;
    *) echo "unknown option: $a" >&2; exit 2 ;;
  esac
done
case "$MODE" in check | apply) ;; *) echo "usage: $0 check|apply staging|production [options]" >&2; exit 2 ;; esac
case "$ENV_NAME" in
  production) REPO=/var/www/hms/production/repo_prod; SERVICE=hms-production ;;
  staging)    REPO=/var/www/hms/staging/repo_stage;  SERVICE=hms-staging ;;
  *) echo "usage: $0 check|apply staging|production [options]" >&2; exit 2 ;;
esac
REPO="${HMS_REPO:-$REPO}"
ENV_FILE="$REPO/backend/.env"
CNF_FILE=/etc/mysql/mysql.conf.d/hms-binlog.cnf
UNIT="hms-binlog-ship-$ENV_NAME"
OFFSITE_CONFIG="${OFFSITE_CONFIG:-/etc/hms/offsite.env}"

ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
todo() { printf '  \033[33m•\033[0m %s\n' "$*"; PENDING=$((PENDING + 1)); }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$*"; PROBLEMS=$((PROBLEMS + 1)); }
run()  { # print, and (in apply without --dry-run) do
  if [ "$MODE" = apply ] && [ "$DRY" = 0 ]; then echo "    \$ $*"; eval "$@"; else echo "    would run: $*"; fi
}
PENDING=0; PROBLEMS=0

[ -f "$ENV_FILE" ] || { echo "ERROR: $ENV_FILE not found (set HMS_REPO if the app lives elsewhere)" >&2; exit 3; }
# A missing key is an empty value, not an error (grep finds nothing -> pipefail must not abort).
envval() { { grep -E "^\s*(export\s+)?$1=" "$ENV_FILE" || true; } | tail -1 | sed -E "s/^\s*(export\s+)?$1=//; s/^[\"']//; s/[\"']$//"; }
DB_USER="$(envval SPRING_DATASOURCE_USERNAME)"
[ -n "$DB_USER" ] || DB_USER="$(envval MYSQL_USER)"
msql() { sudo mysql -N -B -e "$1"; }   # root over the local socket (auth_socket), no password

echo "== HMS server setup — $ENV_NAME ($MODE$( [ "$DRY" = 1 ] && echo ', dry run')) =="

# ── 1. Binary logging ─────────────────────────────────────────────────────
echo "1. MySQL binary logging"
LOG_BIN="$(msql 'SELECT @@log_bin' 2>/dev/null || echo '?')"
FORMAT="$(msql 'SELECT @@binlog_format' 2>/dev/null || echo '?')"
if [ "$LOG_BIN" = 1 ] && [ "$FORMAT" = ROW ]; then
  ok "binlogs ON, format ROW"
else
  if [ ! -f "$CNF_FILE" ]; then
    todo "write $CNF_FILE"
    run "sudo tee $CNF_FILE >/dev/null <<'CNF'
[mysqld]
server_id                  = 1
log_bin                    = mysql-bin
binlog_format              = ROW
sync_binlog                = 1
binlog_expire_logs_seconds = 1209600
CNF"
  else
    ok "$CNF_FILE present"
  fi
  if [ "$RESTART_MYSQL" = 1 ]; then
    todo "restart MySQL to turn binlogs on"
    run "sudo systemctl restart mysql"
    run "sleep 5"
  else
    todo "MySQL restart pending (log_bin=$LOG_BIN, format=$FORMAT) — re-run apply with --restart-mysql at a quiet hour"
  fi
fi

# ── 2. Grants for the backup login ────────────────────────────────────────
echo "2. Backup login privileges (RELOAD, REPLICATION CLIENT) for '$DB_USER'"
if [ -z "$DB_USER" ]; then
  bad "could not read the database user from $ENV_FILE"
else
  HOSTS=()
  while IFS= read -r h; do [ -n "$h" ] && HOSTS+=("$h"); done \
    < <(msql "SELECT host FROM mysql.user WHERE user = '${DB_USER//\'/}'" 2>/dev/null || true)
  [ "${#HOSTS[@]}" -gt 0 ] || bad "MySQL has no user '$DB_USER'"
  for h in ${HOSTS[@]+"${HOSTS[@]}"}; do
    grants="$(msql "SHOW GRANTS FOR '${DB_USER//\'/}'@'$h'" 2>/dev/null || true)"
    if printf '%s' "$grants" | grep -q "RELOAD" && printf '%s' "$grants" | grep -q "REPLICATION CLIENT"; then
      ok "'$DB_USER'@'$h' already has them"
    else
      todo "grant to '$DB_USER'@'$h'"
      run "sudo mysql -e \"GRANT RELOAD, REPLICATION CLIENT ON *.* TO '${DB_USER//\'/}'@'$h'\""
    fi
  done
fi

# ── 3. Binlog shipping timer ──────────────────────────────────────────────
echo "3. Binlog shipping timer ($UNIT)"
if [ ! -f "$OFFSITE_CONFIG" ]; then
  bad "$OFFSITE_CONFIG missing — set up off-site backups first (docs/database/OFFSITE_BACKUPS.md)"
fi
command -v age >/dev/null || bad "'age' not installed (sudo apt install age)"
if systemctl is-enabled "$UNIT.timer" >/dev/null 2>&1; then
  ok "$UNIT.timer enabled ($(systemctl is-active "$UNIT.timer" 2>/dev/null || true))"
else
  todo "install and enable $UNIT.timer"
  run "sed -e 's|repo_prod|${REPO##*/}|g; s|/var/www/hms/production|${REPO%/*}|g; s|ENV_LABEL=production|ENV_LABEL=$ENV_NAME|' \
    '$REPO/scripts/db/systemd/hms-binlog-ship.service' | sudo tee /etc/systemd/system/$UNIT.service >/dev/null"
  run "sudo cp '$REPO/scripts/db/systemd/hms-binlog-ship.timer' /etc/systemd/system/$UNIT.timer"
  run "sudo systemctl daemon-reload"
  if [ "$LOG_BIN" = 1 ]; then
    run "sudo systemctl enable --now $UNIT.timer"
  else
    todo "timer installed but NOT started: binlogs are still off (step 1). Re-run apply after the MySQL restart."
  fi
fi

# ── 4. Prometheus scrape token ────────────────────────────────────────────
echo "4. METRICS_SCRAPE_TOKEN in $ENV_FILE"
TOKEN="$(envval METRICS_SCRAPE_TOKEN)"
if [ "${#TOKEN}" -ge 32 ]; then
  ok "set (${#TOKEN} characters; value not shown)"
else
  todo "generate a token and add it (the value is never printed)"
  run "printf '\nMETRICS_SCRAPE_TOKEN=%s\n' \"\$(openssl rand -hex 32)\" | sudo tee -a '$ENV_FILE' >/dev/null"
  if [ "$RESTART_BACKEND" = 1 ]; then
    run "sudo systemctl restart $SERVICE"
  else
    todo "backend restart pending ($SERVICE) — re-run apply with --restart-backend, or let the next deploy restart it"
  fi
fi
echo "   Prometheus: put the same value in /etc/prometheus/hms_scrape_token (chmod 600):"
echo "     sudo sh -c \"grep -E '^METRICS_SCRAPE_TOKEN=' '$ENV_FILE' | cut -d= -f2- > /etc/prometheus/hms_scrape_token\""

# ── 5. Self-checks ────────────────────────────────────────────────────────
echo "5. Checks"
PORT="$(envval SERVER_PORT)"; PORT="${PORT:-8080}"
TOKEN="$(envval METRICS_SCRAPE_TOKEN)"
if [ "${#TOKEN}" -ge 32 ] && command -v curl >/dev/null; then
  code="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:$PORT/actuator/prometheus" || echo 000)"
  case "$code" in
    200) ok "metrics scrape with the token works (HTTP 200)" ;;
    401) todo "scrape refused (401): the backend has not picked up the token yet — restart it" ;;
    *)   bad "scrape returned HTTP $code on 127.0.0.1:$PORT" ;;
  esac
fi
if [ "$LOG_BIN" = 1 ] && systemctl list-unit-files "$UNIT.service" >/dev/null 2>&1; then
  last="$(systemctl show -p Result --value "$UNIT.service" 2>/dev/null || true)"
  [ "$last" = success ] && ok "last binlog shipment: success" || todo "binlog shipment not yet run/succeeded — check: journalctl -u $UNIT -n 30"
fi

echo
echo "== $PENDING step(s) pending, $PROBLEMS problem(s) =="
if [ "$PENDING" = 0 ] && [ "$PROBLEMS" = 0 ]; then
  echo "All done on $ENV_NAME. Last step (GitHub): set the repository variable DB_BACKUP_PITR=1."
fi
[ "$PROBLEMS" = 0 ]
