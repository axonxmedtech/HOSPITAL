#!/usr/bin/env bash
# Scenario tests for await-release.sh. Needs bash, curl and python3 (present on CI runners).
#
# A stub HTTP server answers /actuator/info, /readiness and /liveness from files the test can
# change while await-release.sh is running, and a stub systemctl reports the PID and state.
# Timings are shortened (interval 1s) so the whole suite takes well under a minute.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/await-release.sh"
WORK="$(mktemp -d)"
trap 'kill "$SERVER_PID" 2>/dev/null; wait "$SERVER_PID" 2>/dev/null; rm -rf "$WORK"' EXIT

NEW_SHA="3c238f80c06ad96a3caf55291819c48162b17f6e"
OLD_SHA="e089b87f04acc6b7f613e3813b5316d5e1eba47a"
OTHER_SHA="1111111111111111111111111111111111111111"
OLD_PID=4242
NEW_PID=5151

# ── stub systemctl ─────────────────────────────────────────────────────────
cat > "$WORK/systemctl" <<'EOF'
#!/usr/bin/env bash
case "$1" in
  is-active) cat "$STUB/active" ;;
  show)      cat "$STUB/pid" ;;
esac
EOF
chmod +x "$WORK/systemctl"
export SYSTEMCTL="$WORK/systemctl"
export STUB="$WORK/state"
mkdir -p "$STUB"

# ── stub HTTP server (files under $STUB: <name>.body and <name>.code) ──────
cat > "$WORK/server.py" <<'EOF'
import http.server, os, sys
STUB = os.environ["STUB"]
ROUTES = {"/actuator/info": "info", "/actuator/health/readiness": "readiness",
          "/actuator/health/liveness": "liveness"}
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        name = ROUTES.get(self.path)
        if not name or not os.path.exists(os.path.join(STUB, name + ".code")):
            self.send_response(404); self.end_headers(); return
        code = int(open(os.path.join(STUB, name + ".code")).read().strip())
        body = open(os.path.join(STUB, name + ".body"), "rb").read()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *a):
        pass
srv = http.server.HTTPServer(("127.0.0.1", 0), H)
print(srv.server_address[1], flush=True)
srv.serve_forever()
EOF
python3 "$WORK/server.py" > "$WORK/port" &
SERVER_PID=$!
for _ in $(seq 1 50); do [ -s "$WORK/port" ] && break; sleep 0.1; done
PORT="$(head -1 "$WORK/port")"
BASE="http://127.0.0.1:$PORT"
DEAD_BASE="http://127.0.0.1:9"   # nothing listens on the discard port

# ── helpers ────────────────────────────────────────────────────────────────
info_full()   { printf '{"git":{"commit":{"id":{"full":"%s","abbrev":"%s"}}}}' "$1" "${1:0:7}" > "$STUB/info.body"; echo 200 > "$STUB/info.code"; }
info_abbrev() { printf '{"git":{"commit":{"id":{"abbrev":"%s"}}}}' "${1:0:7}" > "$STUB/info.body"; echo 200 > "$STUB/info.code"; }
probe()       { printf '{"status":"%s"}' "$2" > "$STUB/$1.body"; echo "$3" > "$STUB/$1.code"; }
service()     { echo "$1" > "$STUB/active"; echo "$2" > "$STUB/pid"; }

healthy_new() { service active "$NEW_PID"; info_full "$NEW_SHA"; probe readiness UP 200; probe liveness UP 200; }

run() { # run <expected-sha> [extra args...]; returns the script's exit code
  local sha="$1"; shift
  bash "$SCRIPT" --expected-sha "$sha" --service hms-test --previous-pid "$OLD_PID" \
    --base-url "$BASE" --timeout 4 --interval 1 --stable-checks 3 "$@" > "$WORK/out.log" 2>&1
}

PASS=0; FAIL=0
expect() { # expect <name> <expected-exit> <actual-exit>
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); echo "PASS  $1";
  else FAIL=$((FAIL + 1)); echo "FAIL  $1 (expected exit $2, got $3)"; sed 's/^/        /' "$WORK/out.log"; fi
}

# 1. expected SHA + READY + new PID -> accepted
healthy_new; run "$NEW_SHA"; expect "1 expected SHA + READY + new PID is accepted" 0 $?

# 2. the old application answering HTTP 200 with the previous SHA -> rejected
service active "$OLD_PID"; info_full "$OLD_SHA"; probe readiness UP 200; probe liveness UP 200
run "$NEW_SHA"; expect "2 old SHA + HTTP 200 is rejected" 1 $?

# 2b. previous SHA even on a new PID -> rejected
healthy_new; info_full "$OLD_SHA"; run "$NEW_SHA"; expect "2b previous SHA on a new process is rejected" 1 $?

# 3. unexpected SHA -> rejected
healthy_new; info_full "$OTHER_SHA"; run "$NEW_SHA"; expect "3 unexpected SHA is rejected" 1 $?

# 4. expected SHA but readiness DOWN -> rejected
healthy_new; probe readiness DOWN 503; run "$NEW_SHA"; expect "4 expected SHA + readiness DOWN is rejected" 1 $?

# 4b. readiness OUT_OF_SERVICE (initialisation still running, never finishes) -> rejected
healthy_new; probe readiness OUT_OF_SERVICE 503; run "$NEW_SHA"; expect "4b readiness OUT_OF_SERVICE until timeout is rejected" 1 $?

# 5. endpoint unavailable -> retries, then times out
healthy_new
bash "$SCRIPT" --expected-sha "$NEW_SHA" --service hms-test --previous-pid "$OLD_PID" \
  --base-url "$DEAD_BASE" --timeout 3 --interval 1 --stable-checks 3 > "$WORK/out.log" 2>&1
rc=$?
attempts=$(grep -c "Attempt .*: waiting" "$WORK/out.log")
expect "5 unavailable endpoint fails after timeout" 1 "$rc"
expect "5 ...and retried more than once ($attempts attempts)" true "$([ "$attempts" -ge 2 ] && echo true || echo false)"

# 6. initialisation failure: readiness DOWN on the new revision -> rejected
healthy_new; probe readiness DOWN 503; run "$NEW_SHA"; expect "6 initialization failure (readiness DOWN) is rejected" 1 $?

# 7. readiness becomes healthy during startup -> eventually accepted
healthy_new; probe readiness OUT_OF_SERVICE 503
( sleep 2; probe readiness UP 200 ) & bg=$!
run "$NEW_SHA" --timeout 10; expect "7 readiness turning UP during startup is eventually accepted" 0 $?
wait "$bg"

# 8a. readiness drops during stabilisation -> rejected
healthy_new
( sleep 1.5; probe readiness DOWN 503 ) & bg=$!
run "$NEW_SHA" --stable-checks 5; expect "8a readiness DOWN during stabilisation is rejected" 1 $?
wait "$bg"

# 8b. the process restarts (PID changes) during stabilisation -> rejected
healthy_new
( sleep 1.5; service active 6262 ) & bg=$!
run "$NEW_SHA" --stable-checks 5; expect "8b PID change during stabilisation is rejected" 1 $?
wait "$bg"

# 9. unchanged old PID, even reporting the new SHA and READY -> rejected
healthy_new; service active "$OLD_PID"; run "$NEW_SHA"; expect "9 unchanged pre-restart PID is rejected" 1 $?

# 9b. service not active -> rejected
healthy_new; service failed 0; run "$NEW_SHA"; expect "9b inactive service is rejected" 1 $?

# 10. abbreviated revision: rejected by default, accepted only with --allow-abbrev
healthy_new; info_abbrev "$NEW_SHA"; run "$NEW_SHA"
expect "10a abbreviated revision is rejected without --allow-abbrev" 1 $?
healthy_new; info_abbrev "$NEW_SHA"; run "$NEW_SHA" --allow-abbrev
expect "10b abbreviated revision is accepted with --allow-abbrev" 0 $?
healthy_new; info_abbrev "$OLD_SHA"; run "$NEW_SHA" --allow-abbrev
expect "10c --allow-abbrev still rejects a non-matching abbreviation" 1 $?

# Usage: a short expected SHA is refused outright, even with --allow-abbrev
bash "$SCRIPT" --expected-sha "${NEW_SHA:0:7}" --service hms-test --previous-pid "$OLD_PID" \
  --base-url "$BASE" --allow-abbrev > "$WORK/out.log" 2>&1
expect "11 a non-40-character expected SHA is a usage error" 2 $?

# Logs carry revision/PID/status, never response bodies.
healthy_new; run "$NEW_SHA"
if grep -q "Expected revision: $NEW_SHA" "$WORK/out.log" && grep -q "Running revision: $NEW_SHA" "$WORK/out.log" \
   && ! grep -q '"git"' "$WORK/out.log"; then rc=0; else rc=1; fi
expect "12 log shows expected/running revision and no response body" 0 "$rc"

echo
echo "await-release.sh: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
