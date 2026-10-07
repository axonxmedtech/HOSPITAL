#!/usr/bin/env bash
# Regression tests for the health-port scoping bug: environment-level variables are empty in a
# job that does not declare `environment:`, so the staging caller once passed an empty port and
# the deploy stopped at validation. The port is now resolved once, inside the environment-scoped
# job, by resolve-health-port.sh -- and must still fail closed when it cannot be resolved.
#
# Behavioural checks run the two real workflow steps back to back (resolve -> validate-config).
# Static checks pin the workflow wiring so the bug cannot quietly return.
# The greps below match literal `${{ ... }}` workflow text, so single quotes are intended.
# shellcheck disable=SC2016
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
RESOLVE="$HERE/resolve-health-port.sh"
VALIDATE="$HERE/validate-config.sh"
WF="$ROOT/.github/workflows"
OUT="$(mktemp)"
trap 'rm -f "$OUT"' EXIT

PASS=0; FAIL=0
ok()   { PASS=$((PASS + 1)); echo "PASS  $1"; }
bad()  { FAIL=$((FAIL + 1)); echo "FAIL  $1"; sed 's/^/        /' "$OUT"; }
is()   { if [ "$2" = "$3" ]; then ok "$1"; else echo "expected [$2], got [$3]" >> "$OUT"; bad "$1"; fi; }

# resolve <env> <override> <staging-var> <production-var> -> sets RC and PORT
resolve() {
  local o
  o=$(INPUT_PORT="$2" ENV_NAME="$1" STAGING_PORT="$3" PRODUCTION_PORT="$4" bash "$RESOLVE" 2> "$OUT")
  RC=$?
  PORT="${o#port=}"
}
# validated <port> -> exit code of the real validation step for that resolved value
validated() { HMS_HEALTH_PORT="$1" bash "$VALIDATE" HMS_HEALTH_PORT:port >> "$OUT" 2>&1; echo $?; }

# Lines that are not YAML comments.
code() { grep -vE '^[[:space:]]*#' "$@"; }

# ── A. the staging (and production) caller no longer resolves the environment variable ──
for f in deploy-staging.yml deploy-prod.yml; do
  hits=$(code "$WF/$f" | grep -nE 'health_port|HEALTH_PORT' || true)
  : > "$OUT"; echo "$hits" >> "$OUT"
  is "A. $f passes no health_port and reads no *_HEALTH_PORT variable" "" "$hits"
done

# ── B/C. inside the deploy job: Staging -> STAGING_HEALTH_PORT, Production -> PRODUCTION_HEALTH_PORT ──
resolve Staging "" 8081 9999;    is "B. Staging resolves STAGING_HEALTH_PORT"            "0:8081" "$RC:$PORT"
is "B. the resolved Staging port passes validation" 0 "$(validated "$PORT")"
resolve Production "" 8081 9090; is "C. Production resolves PRODUCTION_HEALTH_PORT"      "0:9090" "$RC:$PORT"
is "C. the resolved Production port passes validation" 0 "$(validated "$PORT")"
resolve Staging 7000 8081 9090;  is "explicit health_port input overrides the variable"  "0:7000" "$RC:$PORT"

# The resolver step reads both variables and runs in the job that declares the environment.
job_env=$(grep -nE '^    environment:' "$WF/_deploy.yml" | head -1 | cut -d: -f1)
env_name=$(sed -n "$((job_env + 1))p" "$WF/_deploy.yml")
resolve_line=$(grep -n 'name: Resolve health port' "$WF/_deploy.yml" | cut -d: -f1)
: > "$OUT"
if [ -n "$job_env" ] && echo "$env_name" | grep -q 'name: ${{ inputs.environment }}' \
   && [ "${resolve_line:-0}" -gt "$job_env" ] \
   && grep -q 'STAGING_PORT: ${{ vars.STAGING_HEALTH_PORT }}' "$WF/_deploy.yml" \
   && grep -q 'PRODUCTION_PORT: ${{ vars.PRODUCTION_HEALTH_PORT }}' "$WF/_deploy.yml" \
   && grep -q 'ENV_NAME: ${{ inputs.environment }}' "$WF/_deploy.yml"; then rc=0; else rc=1; fi
is "B/C. _deploy.yml resolves the port inside its environment-scoped deploy job" 0 "$rc"

# ── D. an unsupported environment resolves to no port, even with an override ──
resolve SomethingElse "" 8081 9090; is "D. unsupported environment -> no port, resolver fails" "1:" "$RC:$PORT"
is "D. ... and validation rejects it" 1 "$(validated "$PORT")"
resolve staging "" 8081 9090;       is "D. a mis-cased environment is not treated as Staging"    "1:" "$RC:$PORT"
resolve "" "" 8081 9090;            is "D. an empty environment name resolves to no port"         "1:" "$RC:$PORT"
resolve SomethingElse 8081 8081 9090; is "D. an override cannot rescue an unsupported environment" "1:" "$RC:$PORT"

# ── E/F. a missing variable fails validation, which runs before any server contact ──
resolve Staging "" "" 9090;    is "E. missing STAGING_HEALTH_PORT -> empty port" "0:" "$RC:$PORT"
is "E. ... rejected by validation" 1 "$(validated "$PORT")"
resolve Production "" 8081 ""; is "F. missing PRODUCTION_HEALTH_PORT -> empty port (never Staging's)" "0:" "$RC:$PORT"
is "F. ... rejected by validation" 1 "$(validated "$PORT")"
first_ssh=$(grep -nE 'name: SSH Connection Diagnostics|uses: appleboy/' "$WF/_deploy.yml" | head -1 | cut -d: -f1)
validate_line=$(grep -n 'name: Validate deployment configuration' "$WF/_deploy.yml" | cut -d: -f1)
: > "$OUT"
if [ "${resolve_line:-0}" -lt "${validate_line:-0}" ] && [ "${validate_line:-0}" -lt "${first_ssh:-0}" ]; then rc=0; else rc=1; fi
is "E/F. resolve and validate run before the first step that contacts the server" 0 "$rc"

# ── G. an invalid resolved port fails validation ──
for v in abc 0 65536 "80 81" "8081"$'\n'"port=1"; do
  resolve Staging "" "$v" ""
  is "G. invalid Staging value [${v//$'\n'/\\n}] is rejected" 1 "$(validated "$PORT")"
done

# ── H/I. no numeric or vars.PORT fallback for the health port in any deploy workflow ──
health_lines=$(cat "$WF/_deploy.yml" "$WF/deploy-staging.yml" "$WF/deploy-prod.yml" "$WF/deploy-rollback.yml" \
               | grep -E 'health_port|HEALTH_PORT|hport|HPORT|STAGING_PORT|PRODUCTION_PORT|INPUT_PORT')
hits=$(echo "$health_lines" | grep -E "\|\||8080" || true)
: > "$OUT"; echo "$hits" >> "$OUT"
is "H. no '|| ...' or 8080 fallback on any health-port line" "" "$hits"
hits=$(echo "$health_lines" | grep -E 'vars\.PORT' || true)
: > "$OUT"; echo "$hits" >> "$OUT"
is "I. no vars.PORT on any health-port line" "" "$hits"

# ── J. manual rollback resolves its port with the same resolver, in its environment-scoped job ──
rb="$WF/deploy-rollback.yml"
rb_env=$(grep -nE '^    environment:' "$rb" | head -1 | cut -d: -f1)
rb_resolve=$(grep -n 'name: Resolve health port' "$rb" | cut -d: -f1)
rb_validate=$(grep -n 'name: Validate configuration' "$rb" | cut -d: -f1)
rb_exec=$(grep -n 'name: Execute rollback on VPS' "$rb" | cut -d: -f1)
: > "$OUT"
if [ -n "$rb_env" ] && sed -n "$((rb_env + 1))p" "$rb" | grep -q 'name: ${{ inputs.environment }}' \
   && [ "${rb_resolve:-0}" -gt "$rb_env" ] && [ "$rb_resolve" -lt "${rb_validate:-0}" ] && [ "$rb_validate" -lt "${rb_exec:-0}" ] \
   && grep -q 'resolve-health-port.sh' "$rb" \
   && grep -q 'STAGING_PORT: ${{ vars.STAGING_HEALTH_PORT }}' "$rb" \
   && grep -q 'PRODUCTION_PORT: ${{ vars.PRODUCTION_HEALTH_PORT }}' "$rb" \
   && ! grep -q 'steps.env.outputs.hport' "$rb"; then rc=0; else rc=1; fi
is "J. rollback resolves -> validates -> executes inside its environment-scoped job" 0 "$rc"
is "J. rollback validation and execution both use the resolved port" 2 "$(grep -c 'steps.hport.outputs.port' "$rb")"

# ── K. validation, release + automatic-rollback verification and extended verification share ONE port ──
d="$WF/_deploy.yml"
consumers=$(grep -nE '(HMS_HEALTH_PORT|HPORT)(: |=)' "$d" | grep -v 'steps.hport.outputs.port' || true)
: > "$OUT"; echo "$consumers" >> "$OUT"
is "K. every health-port consumer in _deploy.yml reads steps.hport.outputs.port" "" "$consumers"
is "K. exactly three consumers: validation, restart/verify, extended verification" 3 "$(grep -c 'steps.hport.outputs.port' "$d")"
is "K. the port is resolved in exactly one step" 1 "$(grep -c 'resolve-health-port.sh' "$d")"
is "K. inputs.health_port is read only by the resolver" 1 "$(grep -c 'inputs.health_port' "$d")"
is "K. release and automatic-rollback verification both use the one BASE_URL" 2 "$(grep -c -- '--base-url "$BASE_URL"' "$d")"

echo
echo "resolve-health-port.sh: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
