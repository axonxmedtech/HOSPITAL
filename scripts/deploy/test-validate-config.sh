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

# Clean values: a carriage return, newline or surrounding whitespace stops the deploy.
clean() { # clean <value> [kind] -> exit code for HMS_VAL[:kind]
  local spec="HMS_VAL"; [ -n "${2:-}" ] && spec="HMS_VAL:$2"
  HMS_VAL="$1" bash "$SCRIPT" "$spec" > "$OUT" 2>&1
}
clean "/var/www/hms/production/repo_prod";          expect "a clean path is accepted"                      0 $?
clean $'/var/www/hms/production/repo_prod\r\n';     expect "a Windows-pasted value (CRLF) is rejected"     1 $?
clean $'hms-production\n';                          expect "a trailing newline is rejected"                1 $?
clean ' hms-production';                            expect "a leading space is rejected"                   1 $?
clean 'hms-production ';                            expect "a trailing space is rejected"                  1 $?
clean $'main\tx';                                   expect "an inner tab is accepted (not leading/trailing)" 0 $?
clean $'8081\r' port;                               expect "a port with a carriage return is rejected"     1 $?
# A key-shaped multi-line value. Built from parts so secret scanners don't flag a fake key.
pem="PRIVATE"" KEY"
key="-----BEGIN OPENSSH ${pem}-----"$'\n'"AAAA"$'\n'"-----END OPENSSH ${pem}-----"$'\n'
clean "$key" multiline;                             expect "a multi-line SSH key is accepted"              0 $?
clean "${key//$'\n'/$'\r\n'}" multiline;            expect "an SSH key with CRLF line endings is rejected" 1 $?
clean "$key";                                       expect "a multi-line value without :multiline is rejected" 1 $?
clean $'secret-value-7q\r'
if grep -q "HMS_VAL" "$OUT" && ! grep -q "secret-value-7q" "$OUT"; then rc=0; else rc=1; fi
expect "the unclean-value failure names the variable and never prints its value" 0 "$rc"

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

# Static guard: SSH keys are validated as :multiline (so CRLF is caught but newlines allowed).
if grep -q "HMS_SSH_KEY:multiline" "$WF/_deploy.yml" && grep -q "HMS_KEY:multiline" "$WF/deploy-rollback.yml" \
   && grep -q "HMS_KEY:multiline" "$WF/db-backup.yml"; then rc=0; else rc=1; fi
expect "deploy, rollback and backup workflows validate SSH keys as :multiline" 0 "$rc"

echo
echo "validate-config.sh: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
