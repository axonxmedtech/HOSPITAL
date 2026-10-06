#!/usr/bin/env bash
# Tests for validate-config.sh's health-port check, plus a static guard that no deployment
# workflow silently falls back to a default health port.
#
# The health port has no default because a default once pointed staging verification at another
# service on the same host. A missing or invalid value must stop the deploy in the validation
# step -- which runs before anything on the server changes -- and never be printed.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SCRIPT="$HERE/validate-config.sh"
OUT="$(mktemp)"
trap 'rm -f "$OUT"' EXIT

PASS=0; FAIL=0
expect() { # expect <name> <expected-exit> <actual-exit>
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); echo "PASS  $1";
  else FAIL=$((FAIL + 1)); echo "FAIL  $1 (expected exit $2, got $3)"; sed 's/^/        /' "$OUT"; fi
}

check() { # check <value-or-UNSET> -> exit code of the validator for HMS_HEALTH_PORT:port
  if [ "$1" = "UNSET" ]; then
    env -u HMS_HEALTH_PORT HMS_OTHER=x bash "$SCRIPT" HMS_OTHER HMS_HEALTH_PORT:port > "$OUT" 2>&1
  else
    HMS_OTHER=x HMS_HEALTH_PORT="$1" bash "$SCRIPT" HMS_OTHER HMS_HEALTH_PORT:port > "$OUT" 2>&1
  fi
}

check UNSET;  expect "missing health port is rejected"            1 $?
check "";     expect "empty health port is rejected"              1 $?
check "abc";  expect "non-numeric health port is rejected"        1 $?
check "80a1"; expect "partly numeric health port is rejected"     1 $?
check "-1";   expect "negative health port is rejected"           1 $?
check "0";    expect "port 0 is rejected"                         1 $?
check "65536"; expect "port 65536 is rejected"                    1 $?
check "99999"; expect "port 99999 is rejected"                    1 $?
check "123456"; expect "a six-digit port is rejected"             1 $?
check "8081"; expect "8081 is accepted"                           0 $?
check "1";    expect "port 1 is accepted"                         0 $?
check "65535"; expect "port 65535 is accepted"                    0 $?

check "secret-looking-9x"
if grep -q "HMS_HEALTH_PORT" "$OUT" && ! grep -q "secret-looking-9x" "$OUT"; then rc=0; else rc=1; fi
expect "the failure names the variable and never prints its value" 0 "$rc"

HMS_A=x bash "$SCRIPT" HMS_A > "$OUT" 2>&1
expect "existing name-only usage is unchanged" 0 $?

# Static guard: no deployment workflow may substitute a default health port.
WF="$ROOT/.github/workflows"
hits=$(grep -nE "(health_port|HEALTH_PORT|hport|HPORT).*\|\| *'?[0-9]+" \
         "$WF/_deploy.yml" "$WF/deploy-staging.yml" "$WF/deploy-prod.yml" "$WF/deploy-rollback.yml" || true)
if [ -z "$hits" ]; then rc=0; else rc=1; echo "$hits" > "$OUT"; fi
expect "no deployment workflow falls back to a default health port" 0 "$rc"

# Static guard: every health-port consumer is validated with the :port rule before use.
if grep -q "HMS_HEALTH_PORT:port" "$WF/_deploy.yml" && grep -q "HMS_HEALTH_PORT:port" "$WF/deploy-rollback.yml"; then
  rc=0; else rc=1; fi
expect "deploy and rollback workflows validate the health port before acting" 0 "$rc"

echo
echo "validate-config.sh: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
