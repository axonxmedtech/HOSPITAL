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
# info_full <sha> [pid|none]: a current artifact; the PID defaults to whatever the stub
# systemctl reports, i.e. the HTTP answer comes from the restarted process.
info_full() {
  local pid="${2:-$(cat "$STUB/pid" 2>/dev/null || echo 0)}"
  if [ "$pid" = "none" ]; then
    printf '{"git":{"commit":{"id":{"full":"%s","abbrev":"%s"}}}}' "$1" "${1:0:7}" > "$STUB/info.body"
  else
    printf '{"git":{"commit":{"id":{"full":"%s","abbrev":"%s"}}},"runtime":{"pid":%s}}' "$1" "${1:0:7}" "$pid" > "$STUB/info.body"
  fi
  echo 200 > "$STUB/info.code"
}
# info_abbrev <sha> [pid]: a legacy artifact -- abbreviated revision and, unless given, no PID.
info_abbrev() {
  if [ -n "${2:-}" ]; then
    printf '{"git":{"commit":{"id":{"abbrev":"%s"}}},"runtime":{"pid":%s}}' "${1:0:7}" "$2" > "$STUB/info.body"
  else
    printf '{"git":{"commit":{"id":{"abbrev":"%s"}}}}' "${1:0:7}" > "$STUB/info.body"
  fi
  echo 200 > "$STUB/info.code"
}
probe()       { printf '{"status":"%s"}' "$2" > "$STUB/$1.body"; echo "$3" > "$STUB/$1.code"; }
service()     { echo "$1" > "$STUB/active"; echo "$2" > "$STUB/pid"; }

healthy_new() { service active "$NEW_PID"; info_full "$NEW_SHA" "$NEW_PID"; probe readiness UP 200; probe liveness UP 200; }

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
service active "$OLD_PID"; info_full "$OLD_SHA" "$OLD_PID"; probe readiness UP 200; probe liveness UP 200
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
( sleep 1.5; service active 6262; info_full "$NEW_SHA" 6262 ) & bg=$!
run "$NEW_SHA" --stable-checks 5; expect "8b PID change during stabilisation is rejected" 1 $?
wait "$bg"

# 9. unchanged old PID, even reporting the new SHA and READY -> rejected
healthy_new; service active "$OLD_PID"; info_full "$NEW_SHA" "$OLD_PID"; run "$NEW_SHA"; expect "9 unchanged pre-restart PID is rejected" 1 $?

# 9b. service not active -> rejected
healthy_new; service failed 0; run "$NEW_SHA"; expect "9b inactive service is rejected" 1 $?

# 10. abbreviated revision: rejected by default, accepted only with --allow-abbrev
healthy_new; info_abbrev "$NEW_SHA"; run "$NEW_SHA"
expect "10a abbreviated revision is rejected without --allow-abbrev" 1 $?
healthy_new; info_abbrev "$NEW_SHA"; run "$NEW_SHA" --allow-abbrev
expect "10b abbreviated revision is accepted with --allow-abbrev" 0 $?
healthy_new; info_abbrev "$OLD_SHA"; run "$NEW_SHA" --allow-abbrev
expect "10c --allow-abbrev still rejects a non-matching abbreviation" 1 $?

# ── HTTP-to-process binding (runtime.pid == MainPID) ───────────────────────

# 13. correct SHA, but the HTTP answer comes from a different process
healthy_new; info_full "$NEW_SHA" 7777; run "$NEW_SHA"; expect "13 correct SHA + wrong HTTP PID is rejected" 1 $?

# 14. a healthy wrong service: different revision and different PID
healthy_new; info_full "$OTHER_SHA" 7777; run "$NEW_SHA"; expect "14 healthy wrong service with another PID is rejected" 1 $?

# 15. the wrong port, answered by production: main's abbreviated revision, no runtime.pid
healthy_new; info_abbrev "$OLD_SHA"; run "$NEW_SHA"; expect "15 wrong port returning main's revision is rejected" 1 $?

# 16. a NEW release that does not report runtime.pid
healthy_new; info_full "$NEW_SHA" none; run "$NEW_SHA"
rc=$?; expect "16 new release without runtime.pid is rejected" 1 "$rc"
expect "16 ...with a reason that names runtime.pid" true "$(grep -q 'no runtime.pid reported' "$WORK/out.log" && echo true || echo false)"

# 17. the HTTP PID changes during stabilisation while systemd's MainPID does not
healthy_new
( sleep 1.5; info_full "$NEW_SHA" 8888 ) & bg=$!
run "$NEW_SHA" --stable-checks 5; expect "17 HTTP PID change during stabilisation is rejected" 1 $?
wait "$bg"

# 18. the same PID and SHA stay healthy through the full 6/6 window
healthy_new; run "$NEW_SHA" --stable-checks 6 --timeout 12
rc=$?; expect "18 same PID/SHA healthy through 6/6 is accepted" 0 "$rc"
expect "18 ...and all six stability checks were logged" true "$(grep -q 'Stable 6/6' "$WORK/out.log" && echo true || echo false)"

# 19. legacy rollback target that DOES report a PID, but not the restarted one
healthy_new; info_abbrev "$NEW_SHA" 7777; run "$NEW_SHA" --allow-abbrev
expect "19 legacy rollback with a mismatched runtime.pid is rejected" 1 $?

# 20. legacy rollback target reporting the matching PID
healthy_new; info_abbrev "$NEW_SHA" "$NEW_PID"; run "$NEW_SHA" --allow-abbrev
expect "20 legacy rollback with a matching runtime.pid is accepted" 0 $?

# 21. legacy rollback without runtime.pid is accepted only with --allow-abbrev, and says so
healthy_new; info_abbrev "$NEW_SHA"; run "$NEW_SHA" --allow-abbrev
rc=$?; expect "21 legacy rollback without runtime.pid is accepted with --allow-abbrev" 0 "$rc"
expect "21 ...and logs that binding was not possible" true "$(grep -q 'reports no runtime.pid' "$WORK/out.log" && echo true || echo false)"

# 22. no default health port: --base-url missing, or without an explicit port, is a usage error
bash "$SCRIPT" --expected-sha "$NEW_SHA" --service hms-test --previous-pid "$OLD_PID" > "$WORK/out.log" 2>&1
expect "22a missing --base-url is a usage error" 2 $?
bash "$SCRIPT" --expected-sha "$NEW_SHA" --service hms-test --previous-pid "$OLD_PID" \
  --base-url "http://127.0.0.1" > "$WORK/out.log" 2>&1
expect "22b --base-url without an explicit port is a usage error" 2 $?

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
