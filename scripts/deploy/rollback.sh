#!/usr/bin/env bash
# Operator-triggered rollback — runs ON the VPS. Restores the previous artifact set that the
# last deployment saved to ../rollback_backup, restarts the service, and re-verifies health.
# Writes a plain-text rollback report to stdout (captured by the workflow for the audit trail).
#
# Usage: rollback.sh <repo_path> <service_name> <liveness_url>
#        (<liveness_url> is http://host:port/api/public/health; verification uses the same base URL)
# Requires: a prior deployment created ../rollback_backup (prev_backend.jar, prev_frontend_dist,
#           prev_sha.txt). Documented limitation: does NOT roll back the database.
#
# Success is verified by await-release.sh, not by an HTTP 200: the restarted process must be new,
# report the previous revision, and be ready and live through a stabilisation window.
set -uo pipefail

REPO="${1:?repo_path required}"
SERVICE="${2:?service_name required}"
URL="${3:?liveness_url required}"
BACKUP="$(dirname "$REPO")/rollback_backup"
TS() { date -u '+%Y-%m-%dT%H:%M:%SZ'; }

echo "===== ROLLBACK REPORT ====="
echo "started:    $(TS)"
echo "service:    $SERVICE"
echo "repo:       $REPO"
echo "backup dir: $BACKUP"

if [ ! -d "$BACKUP" ]; then
  echo "result:     FAILED — no rollback backup found. Nothing to restore."
  echo "next:       Restore from a known-good release artifact manually (see DEPLOYMENT.md)."
  exit 1
fi

PREV_SHA="$(cat "$BACKUP/prev_sha.txt" 2>/dev/null || echo unknown)"
echo "target sha: $PREV_SHA"
BASE_URL="${URL%/api/public/health}"

# Copied out of the checkout before it moves: the previous commit may predate the verifier.
AWAIT="$(mktemp)"
if ! cp "$REPO/scripts/deploy/await-release.sh" "$AWAIT" 2>/dev/null; then
  echo "result:     FAILED — release verifier not found in $REPO. Nothing was changed."
  rm -f "$AWAIT"
  exit 1
fi

cd "$REPO" || { echo "result:     FAILED — cannot enter $REPO. Nothing was changed."; rm -f "$AWAIT"; exit 1; }
if [ "$PREV_SHA" != "unknown" ]; then
  git reset --hard "$PREV_SHA" 2>&1 | sed 's/^/  git: /' || echo "  git: reset failed (continuing with artifacts)"
fi

echo "restoring previous artifacts..."
mkdir -p backend/target frontend/dist
cp -f "$BACKUP/prev_backend.jar" backend/target/hospital-management-system-1.0.0.jar 2>/dev/null && echo "  restored backend jar" || echo "  WARN: no backend jar in backup"
rm -rf frontend/dist && cp -r "$BACKUP/prev_frontend_dist" frontend/dist 2>/dev/null && echo "  restored frontend dist" || echo "  WARN: no frontend dist in backup"

PREV_PID=$(systemctl show -p MainPID --value "$SERVICE" 2>/dev/null || echo 0)
echo "restarting $SERVICE (current PID ${PREV_PID:-0})..."
sudo systemctl restart "$SERVICE"

echo "verifying release..."
HEALTHY=false
# --allow-abbrev: the previous artifact may predate the full revision in /actuator/info.
if [ "$PREV_SHA" != "unknown" ] && bash "$AWAIT" --expected-sha "$PREV_SHA" --service "$SERVICE" \
     --previous-pid "${PREV_PID:-0}" --base-url "$BASE_URL" \
     --timeout 300 --interval 5 --stable-checks 6 --allow-abbrev; then
  HEALTHY=true
fi
rm -f "$AWAIT"

if $HEALTHY; then
  echo "result:     SUCCESS — previous application version restored, verified and ready."
  echo "database:   NOT rolled back. Restore the pre-deploy backup if data must be reverted."
  echo "finished:   $(TS)"
  exit 0
else
  echo "result:     FAILED — the previous version could not be verified after rollback."
  echo "next:       Inspect 'journalctl -u $SERVICE -n 120'. Manual recovery required."
  echo "finished:   $(TS)"
  exit 1
fi
