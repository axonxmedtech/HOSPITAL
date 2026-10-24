#!/usr/bin/env bash
# Tests for check-deploy-ref.sh, plus static guards on the workflows that reach a server:
#   * no workflow reads the old repository-level SSH secrets (any branch could read those);
#   * every job that uses the deploy key runs inside a GitHub Environment, so the key is an
#     environment secret, released only to branches that environment allows;
#   * every such job runs check-deploy-ref.sh before it touches a server.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SCRIPT="$HERE/check-deploy-ref.sh"
WF="$ROOT/.github/workflows"
OUT="$(mktemp)"
trap 'rm -f "$OUT"' EXIT

PASS=0; FAIL=0
expect() { # expect <name> <expected-exit> <actual-exit>
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); echo "PASS  $1";
  else FAIL=$((FAIL + 1)); echo "FAIL  $1 (expected exit $2, got $3)"; sed 's/^/        /' "$OUT"; fi
}
run() { bash "$SCRIPT" "$@" > "$OUT" 2>&1; }

run Production refs/heads/main;                expect "production from main is allowed"                 0 $?
run Production refs/heads/main main;           expect "production deploy of main from main is allowed"  0 $?
run Production refs/heads/staging;             expect "production from staging is refused"              1 $?
run Production refs/heads/feature/x;           expect "production from a feature branch is refused"     1 $?
run Production refs/tags/v1.0.0;               expect "production from a tag is refused"                1 $?
run Production refs/pull/12/merge;             expect "production from a pull request is refused"       1 $?
run Production refs/heads/main-hotfix;         expect "a branch merely starting with main is refused"   1 $?
run Production refs/heads/main staging;        expect "production deploy of another branch is refused"  1 $?
run Production "";                             expect "production with an empty ref is refused"         1 $?
run Staging refs/heads/staging;                expect "staging from staging is allowed"                 0 $?
run Staging refs/heads/staging staging;        expect "staging deploy of staging from staging allowed"  0 $?
run Staging refs/heads/main;                   expect "staging rollback/backup from main is allowed"    0 $?
run Staging refs/heads/main staging;           expect "staging deploy dispatched from main is refused"  1 $?
run Staging refs/heads/feature/x;              expect "staging from a feature branch is refused"        1 $?
run production refs/heads/main;                expect "environment names are exact (case-sensitive)"    1 $?
run Development refs/heads/main;               expect "an unknown environment is refused"               1 $?
run Production;                                expect "missing ref is a usage error"                    2 $?
run Production refs/heads/main main extra;     expect "too many arguments is a usage error"             2 $?

# ── Static workflow guards ───────────────────────────────────────────────────────────────────
# Comment lines are ignored throughout: comments may explain the old names.
code_lines() { grep -nE "$1" "$2" | grep -vE '^[0-9]+:[[:space:]]*#'; }

hits=""
for f in "$WF"/*.yml; do
  h=$(code_lines 'secrets\.(SSH_PRIVATE_KEY|SSH_USERNAME)\b' "$f" | sed "s|^|${f#"$ROOT"/}:|")
  [ -n "$h" ] && hits="${hits}${h}"$'\n'
done
[ -z "$hits" ]; expect "no workflow reads the repository-level SSH secrets" 0 $?
[ -n "$hits" ] && printf '%s' "$hits" | sed 's/^/        /'

# Jobs that use the deploy key must declare an environment and run the ref check. Parsed with
# Python's YAML loader (preinstalled on GitHub runners) so the job boundaries are real.
python3 - "$WF" > "$OUT" 2>&1 <<'PY'
import glob, os, sys, yaml
bad = []
for path in sorted(glob.glob(os.path.join(sys.argv[1], "*.yml"))):
    doc = yaml.safe_load(open(path)) or {}
    for name, job in (doc.get("jobs") or {}).items():
        text = yaml.safe_dump(job)
        if "DEPLOY_SSH_KEY" not in text:
            continue
        where = f"{os.path.basename(path)}:{name}"
        if not job.get("environment"):
            bad.append(f"{where} uses DEPLOY_SSH_KEY without an environment")
        if "check-deploy-ref.sh" not in text:
            bad.append(f"{where} uses DEPLOY_SSH_KEY without running check-deploy-ref.sh")
print("\n".join(bad))
sys.exit(1 if bad else 0)
PY
expect "every job holding the deploy key has an environment and a ref check" 0 $?

grep -q 'check-deploy-ref.sh Production' "$WF/deploy-prod.yml"
expect "deploy-prod.yml refuses non-main refs before building" 0 $?

# The reusable deploy workflow must receive the ENVIRONMENT's deploy key. Declaring DEPLOY_SSH_* under
# on.workflow_call.secrets turned them into caller inputs that nobody passes: they arrived empty and hid
# the environment's values (staging run 37947902662, "missing required configuration: HMS_SSH_KEY").
python3 - "$WF" > "$OUT" 2>&1 <<'PY'
import glob, os, sys, yaml
wf = sys.argv[1]
bad = []
deploy = yaml.safe_load(open(os.path.join(wf, "_deploy.yml")))
on = deploy.get("on", deploy.get(True)) or {}                  # PyYAML reads the key `on` as True
declared = ((on.get("workflow_call") or {}).get("secrets") or {})
for name in declared:
    if name.upper().startswith("DEPLOY_SSH"):
        bad.append(f"_deploy.yml declares {name} as a workflow_call secret (it would shadow the environment secret)")
for path in sorted(glob.glob(os.path.join(wf, "*.yml"))):
    doc = yaml.safe_load(open(path)) or {}
    for name, job in (doc.get("jobs") or {}).items():
        if str(job.get("uses", "")).endswith("/_deploy.yml") and job.get("secrets") != "inherit":
            bad.append(f"{os.path.basename(path)}:{name} calls _deploy.yml without `secrets: inherit`")
for name, job in (deploy.get("jobs") or {}).items():
    for step in job.get("steps") or []:
        text = yaml.safe_dump(step)
        cond = str(step.get("if", ""))
        if "DEPLOY_SSH_KEY" in text and "always()" in cond and "steps.validate.outcome == 'success'" not in cond:
            bad.append(f"_deploy.yml step '{step.get('name')}' uses the key under always() without a validated configuration")
    if not any(st.get("id") == "validate" for st in job.get("steps") or []):
        bad.append(f"_deploy.yml:{name} has no step with id 'validate'")
print("\n".join(bad))
sys.exit(1 if bad else 0)
PY
expect "reusable deploy reads the environment's key (no shadowing, callers inherit, no unvalidated SSH)" 0 $?

echo ""
echo "check-deploy-ref: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
