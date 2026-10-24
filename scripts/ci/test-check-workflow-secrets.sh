#!/usr/bin/env bash
# Tests for check-workflow-secrets.sh: every way the Sonar token (or any credential) could come back
# from a GitHub variable fails the check, the fixed workflows pass, and comments are ignored.
# Fixtures are literal text (`${{ ... }}`, `$2a$...`), so single quotes are intended.
# shellcheck disable=SC2016
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CHECK="$HERE/check-workflow-secrets.sh"
WORK="$(mktemp -d)"
OUT="$WORK/out"
trap 'rm -rf "$WORK"' EXIT

PASS=0; FAIL=0
expect() { # expect <name> <expected-exit> <actual-exit>
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); echo "PASS  $1";
  else FAIL=$((FAIL + 1)); echo "FAIL  $1 (expected exit $2, got $3)"; sed 's/^/        /' "$OUT"; fi
}

# fixture <name> <yaml body> -> a directory holding one workflow file with that body
fixture() {
  mkdir -p "$WORK/$1"
  printf 'name: t\non: push\njobs:\n  j:\n    runs-on: ubuntu-latest\n    steps:\n%s\n' "$2" > "$WORK/$1/w.yml"
  echo "$WORK/$1"
}

GOOD='      - name: presence
        env:
          SONAR_TOKEN_PRESENT: ${{ secrets.SONAR_TOKEN != '"''"' }}
          SONAR_ORG: ${{ vars.SONAR_ORGANIZATION }}
        run: echo ok
      # vars.SONAR_TOKEN used to be read here; comments are not code
      - name: scan
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: echo scan'

bash "$CHECK" "$(fixture good "$GOOD")" > "$OUT" 2>&1
expect "the fixed pattern (presence boolean + secret-only scan) passes" 0 $?

bash "$CHECK" "$ROOT/.github/workflows" > "$OUT" 2>&1
expect "the repository's workflows pass" 0 $?

bad() { # bad <name> <step yaml>
  bash "$CHECK" "$(fixture "$1" "      - name: x
        env:
$2
        run: echo x")" > "$OUT" 2>&1
  expect "rejects: $1" 1 $?
}

bad var-dot             '          T: ${{ vars.SONAR_TOKEN }}'
bad var-bracket         "          T: \${{ vars['SONAR_TOKEN'] }}"
bad secret-or-var       '          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN || vars.SONAR_TOKEN }}'
bad secret-or-other-var '          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN || vars.SONAR_LOGIN }}'
bad sonar-from-var      '          SONAR_TOKEN: ${{ vars.SONAR_LOGIN }}'
bad sonar-from-other    '          SONAR_TOKEN: ${{ secrets.SOMETHING_ELSE }}'
bad sonar-var-channel   '          SONAR_VAR: ${{ secrets.SONAR_TOKEN }}'
bad password-var        '          DB: ${{ vars.DB_PASSWORD }}'
bad webhook-var         '          HOOK: ${{ vars.SLACK_WEBHOOK }}'
bad api-key-var         '          K: ${{ vars.NVD_API_KEY }}'

mkdir -p "$WORK/empty"
bash "$CHECK" "$WORK/empty" > "$OUT" 2>&1
expect "no workflow files is an error, not a pass" 2 $?

bash "$CHECK" "$(fixture leak "      - name: x
        env:
          T: \${{ vars.SONAR_TOKEN }}
        run: echo x")" > "$OUT" 2>&1
if grep -q 'w.yml:[0-9]*:' "$OUT"; then rc=0; else rc=1; fi
expect "a finding names the file and line" 0 "$rc"

echo
echo "check-workflow-secrets.sh: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
