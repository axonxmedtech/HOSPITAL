#!/usr/bin/env bash
# Fail if a workflow can expose a credential through a GitHub Actions *variable*.
#
# Variables are not masked: GitHub prints every step's env values in the log. SONAR_TOKEN once lived
# in a repository variable behind a `secrets.SONAR_TOKEN || vars.SONAR_TOKEN` fallback, and its
# value appeared in plain text in the logs of this public repository. Credentials belong in
# secrets, and only there.
#
# Deliberately narrow: it looks for credential-shaped variable reads and for SONAR_TOKEN being
# taken from anything other than the secret, and ignores YAML comment lines.
#
# Usage: check-workflow-secrets.sh [workflow-dir]   (default: .github/workflows)
# Exit:  0 = clean; 1 = a finding (each printed with file:line); 2 = no workflow files found.
set -uo pipefail

DIR="${1:-.github/workflows}"
shopt -s nullglob
files=("$DIR"/*.yml "$DIR"/*.yaml)
if [ "${#files[@]}" -eq 0 ]; then
  echo "::error::no workflow files found under $DIR"
  exit 2
fi

fail=0

# Lines of every workflow matching <ERE>, as file:line:content, comment lines excluded.
matching() {
  local f
  for f in "${files[@]}"; do
    grep -nE "$1" "$f" | grep -vE '^[0-9]+:[[:space:]]*#' | sed "s|^|$f:|"
  done
}

finding() { # finding <description> <hits>
  [ -n "$2" ] || return 0
  echo "FAIL: $1"
  printf '%s\n' "$2" | sed 's/^/  /'
  fail=1
}

VAR_READ="vars[[:space:]]*(\.|\[[[:space:]]*['\"])"

finding "SONAR_TOKEN read from a variable" \
  "$(matching "${VAR_READ}SONAR_TOKEN")"
finding "a secret falls back to a variable" \
  "$(matching 'secrets\.[A-Za-z0-9_]+[[:space:]]*\|\|[[:space:]]*vars')"
finding "credential-like name read from a variable" \
  "$(matching "${VAR_READ}[A-Za-z0-9_]*(TOKEN|SECRET|PASSWORD|PASSWD|PRIVATE_KEY|API_KEY|WEBHOOK)")"
finding "SONAR_VAR (the old plain-text token channel)" \
  "$(matching 'SONAR_VAR')"

# Every SONAR_TOKEN environment assignment must be exactly the secret.
EXACT='^[0-9]+:[[:space:]]*SONAR_TOKEN[[:space:]]*:[[:space:]]*\$\{\{[[:space:]]*secrets\.SONAR_TOKEN[[:space:]]*\}\}[[:space:]]*$'
bad=""
for f in "${files[@]}"; do
  hits=$(grep -nE '^[[:space:]]*SONAR_TOKEN[[:space:]]*:' "$f" | grep -vE "$EXACT" | sed "s|^|$f:|")
  [ -n "$hits" ] && bad="${bad:+$bad
}$hits"
done
finding "SONAR_TOKEN assigned from something other than secrets.SONAR_TOKEN" "$bad"

if [ "$fail" -ne 0 ]; then
  echo "::error::A workflow reads a credential from a GitHub variable (unmasked). Use secrets only."
  exit 1
fi
echo "Workflow secret check passed: ${#files[@]} workflow file(s), no credential read from a variable."
