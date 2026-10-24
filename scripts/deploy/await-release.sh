#!/usr/bin/env bash
# Waits for a just-restarted backend to prove it is the release being deployed, and healthy.
# Runs ON the server, after `systemctl restart`. Never accepts a bare HTTP 200.
#
# A release is accepted only when, on every check:
#   1. systemd reports the service active, with a MainPID that is NOT the pre-restart PID
#      (so the old JVM, which may still answer HTTP while it shuts down, can never pass);
#   2. /actuator/info reports runtime.pid EQUAL to that MainPID -- binding the HTTP answer to the
#      process systemd restarted, so a healthy response from another service, another instance,
#      the old JVM or a wrong port is refused -- and the exact expected 40-character revision
#      (git.commit.id.full);
#   3. /actuator/health/readiness is HTTP 200 with status UP -- which in this application means
#      Spring is accepting traffic, post-start schema initialisation is COMPLETE and the database
#      is up (see StartupInitializationState);
#   4. /actuator/health/liveness is HTTP 200 with status UP;
# and then stays so for --stable-checks consecutive checks on the same PID and revision.
#
# Usage:
#   await-release.sh --expected-sha <sha> --service <systemd unit> --previous-pid <pid>
#                    --base-url http://localhost:<health port>
#                    [--timeout 300] [--interval 5] [--stable-checks 6] [--allow-abbrev]
#
# --base-url is required: there is no default port, because a default can point at a different
# service on the same host (that is how staging verification once checked production).
#
# --allow-abbrev is ONLY for verifying a rollback to an artifact built before /actuator/info
# carried the full revision and runtime.pid. It accepts the 7+-character abbreviation when the
# full id is absent, and tolerates runtime.pid being ABSENT ENTIRELY (logged). A PID that is
# present must still match. A normal deployment must never pass it.
#
# Exit: 0 accepted, 1 not accepted (timed out, wrong revision, unhealthy, restarted), 2 bad usage.
# Prints revision, PID and status only -- never response bodies, configuration or credentials.
#
# SYSTEMCTL (default: systemctl) can be overridden; the test suite uses a stub.
set -uo pipefail

EXPECTED=""
SERVICE=""
PREVIOUS_PID=""
BASE_URL=""
TIMEOUT=300
INTERVAL=5
STABLE_CHECKS=6
ALLOW_ABBREV=false
SYSTEMCTL="${SYSTEMCTL:-systemctl}"

usage() { echo "usage: $0 --expected-sha <sha> --service <unit> --previous-pid <pid> [options]" >&2; exit 2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --expected-sha)  EXPECTED="${2:-}"; shift 2 ;;
    --service)       SERVICE="${2:-}"; shift 2 ;;
    --previous-pid)  PREVIOUS_PID="${2:-}"; shift 2 ;;
    --base-url)      BASE_URL="${2:-}"; shift 2 ;;
    --timeout)       TIMEOUT="${2:-}"; shift 2 ;;
    --interval)      INTERVAL="${2:-}"; shift 2 ;;
    --stable-checks) STABLE_CHECKS="${2:-}"; shift 2 ;;
    --allow-abbrev)  ALLOW_ABBREV=true; shift ;;
    *) echo "unknown argument: $1" >&2; usage ;;
  esac
done

EXPECTED="$(printf '%s' "$EXPECTED" | tr 'A-F' 'a-f')"
[ -n "$SERVICE" ] || usage
if ! printf '%s' "$BASE_URL" | grep -qE '^https?://[^/]+:[0-9]{1,5}$'; then
  echo "--base-url is required and must include an explicit port, e.g. http://localhost:8081" >&2
  usage
fi
[ -n "$PREVIOUS_PID" ] || usage
for n in "$TIMEOUT" "$INTERVAL" "$STABLE_CHECKS" "$PREVIOUS_PID"; do
  case "$n" in ''|*[!0-9]*) echo "numeric argument expected, got '$n'" >&2; usage ;; esac
done
[ "$STABLE_CHECKS" -ge 1 ] || usage
if ! printf '%s' "$EXPECTED" | grep -qE '^[0-9a-f]{40}$'; then
  echo "--expected-sha must be a full 40-character commit id" >&2
  usage
fi

TS() { date -u '+%Y-%m-%dT%H:%M:%SZ'; }

# Last values observed, for the log line and the stabilisation comparison.
PID="?"; HTTP_PID="?"; RUNNING="?"; READY="?"; LIVE="?"; REASON=""

fetch() { # url -> sets BODY and CODE; never echoes the body
  local out
  out="$(curl -s --max-time 5 -w '\n%{http_code}' "$1" 2>/dev/null || true)"
  CODE="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
  [ -n "$CODE" ] || CODE="000"
}

status_up() { printf '%s' "$1" | grep -q '"status":"UP"'; }

check_once() {
  REASON=""
  local active
  active="$("$SYSTEMCTL" is-active "$SERVICE" 2>/dev/null || true)"
  PID="$("$SYSTEMCTL" show -p MainPID --value "$SERVICE" 2>/dev/null || true)"
  PID="${PID:-0}"
  if [ "$active" != "active" ]; then REASON="service is '${active:-unknown}', not active"; return 1; fi
  if [ "$PID" = "0" ]; then REASON="service has no main process"; return 1; fi
  if [ "$PID" = "$PREVIOUS_PID" ]; then REASON="still the pre-restart process (PID $PID)"; return 1; fi

  fetch "$BASE_URL/actuator/info"
  RUNNING="$(printf '%s' "$BODY" | grep -oE '"full":"[0-9a-f]{40}"' | head -1 | cut -d'"' -f4)"
  if [ -z "$RUNNING" ] && $ALLOW_ABBREV; then
    RUNNING="$(printf '%s' "$BODY" | grep -oE '"abbrev":"[0-9a-f]{7,40}"' | head -1 | cut -d'"' -f4)"
  fi
  RUNNING="${RUNNING:-none}"
  HTTP_PID="$(printf '%s' "$BODY" | grep -oE '"runtime":\{"pid":[0-9]+' | head -1 | grep -oE '[0-9]+$')"
  HTTP_PID="${HTTP_PID:-none}"
  if [ "$CODE" != "200" ]; then REASON="/actuator/info answered HTTP $CODE"; return 1; fi
  if [ "$HTTP_PID" = "none" ]; then
    if ! $ALLOW_ABBREV; then REASON="no runtime.pid reported (cannot bind the response to PID $PID)"; return 1; fi
    LEGACY_NO_PID=true
  elif [ "$HTTP_PID" != "$PID" ]; then
    REASON="HTTP response is from PID $HTTP_PID, not the restarted PID $PID"; return 1
  fi
  if [ "$RUNNING" = "none" ]; then REASON="no revision reported"; return 1; fi
  if [ "$RUNNING" != "$EXPECTED" ]; then
    # The abbreviation is a prefix of the full id; only accepted for legacy rollback targets.
    if ! { $ALLOW_ABBREV && [ "${#RUNNING}" -lt 40 ] && [ "${EXPECTED#"$RUNNING"}" != "$EXPECTED" ]; }; then
      REASON="running revision is not the expected one"; return 1
    fi
  fi

  fetch "$BASE_URL/actuator/health/readiness"
  if [ "$CODE" = "200" ] && status_up "$BODY"; then READY="UP"; else READY="DOWN(HTTP $CODE)"; fi
  fetch "$BASE_URL/actuator/health/liveness"
  if [ "$CODE" = "200" ] && status_up "$BODY"; then LIVE="UP"; else LIVE="DOWN(HTTP $CODE)"; fi
  if [ "$READY" != "UP" ]; then REASON="readiness is $READY"; return 1; fi
  if [ "$LIVE" != "UP" ]; then REASON="liveness is $LIVE"; return 1; fi
  return 0
}

line() { # attempt-label
  echo "[$(TS)] $1 | PID $PID | HTTP PID $HTTP_PID | expected $EXPECTED | running $RUNNING | readiness $READY | liveness $LIVE${REASON:+ | $REASON}"
}

echo "[$(TS)] Verifying release: service=$SERVICE previous-pid=$PREVIOUS_PID timeout=${TIMEOUT}s interval=${INTERVAL}s stable-checks=$STABLE_CHECKS$($ALLOW_ABBREV && echo ' (legacy abbreviated revision allowed)')"
echo "Expected revision: $EXPECTED"
echo "Health endpoint: $BASE_URL"
LEGACY_NO_PID=false

deadline=$(( $(date +%s) + TIMEOUT ))
attempt=0
while :; do
  attempt=$((attempt + 1))
  if check_once; then
    line "Attempt $attempt: accepted, stabilising"
    break
  fi
  line "Attempt $attempt: waiting"
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "[$(TS)] FAILED: release not accepted within ${TIMEOUT}s. Last reason: $REASON"
    exit 1
  fi
  sleep "$INTERVAL"
done

STABLE_PID="$PID"
STABLE_REV="$RUNNING"
n=1
echo "[$(TS)] Stable $n/$STABLE_CHECKS"
while [ "$n" -lt "$STABLE_CHECKS" ]; do
  sleep "$INTERVAL"
  if ! check_once; then
    line "Stabilisation"
    echo "[$(TS)] FAILED: became unhealthy during stabilisation: $REASON"
    exit 1
  fi
  if [ "$PID" != "$STABLE_PID" ]; then
    echo "[$(TS)] FAILED: process restarted during stabilisation (PID $STABLE_PID -> $PID)"
    exit 1
  fi
  if [ "$RUNNING" != "$STABLE_REV" ]; then
    echo "[$(TS)] FAILED: revision changed during stabilisation"
    exit 1
  fi
  n=$((n + 1))
  echo "[$(TS)] Stable $n/$STABLE_CHECKS"
done

echo "Running revision: $RUNNING"
echo "Running PID: $STABLE_PID (HTTP runtime.pid $HTTP_PID)"
if $LEGACY_NO_PID; then
  echo "[$(TS)] NOTE: legacy rollback target reports no runtime.pid; HTTP-to-process binding was not possible (--allow-abbrev)."
fi
echo "[$(TS)] ACCEPTED: PID $STABLE_PID, readiness UP and liveness UP for $STABLE_CHECKS consecutive checks."
exit 0
