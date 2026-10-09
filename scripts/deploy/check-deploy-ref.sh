#!/usr/bin/env bash
# Refuse a deployment, rollback or backup started from a branch that may not reach the environment.
#
# Usage: check-deploy-ref.sh <environment> <github-ref> [required-branch]
#   Production  only refs/heads/main.
#   Staging     refs/heads/staging, or refs/heads/main (a rollback or manual backup run from main).
#   required-branch, when given, must ALSO match the ref exactly: a deploy of `staging` must run from
#   the staging branch, so a manual dispatch on another branch cannot ship that branch's commit.
# Exit: 0 = allowed; 1 = refused; 2 = usage error.
#
# This guard is read from the branch being run, so it catches mistakes, not a hostile branch that
# edits it. The enforcement that a branch cannot bypass lives in GitHub: each environment's
# deployment-branch rule, plus the SSH key being an environment secret that no other branch can read.
set -uo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "::error::usage: check-deploy-ref.sh <environment> <github-ref> [required-branch]"
  exit 2
fi
ENV_NAME="$1"
REF="$2"
REQUIRED="${3:-}"

refuse() {
  echo "::error::Refusing to run against $ENV_NAME from $REF: $1"
  exit 1
}

case "$ENV_NAME" in
  Production)
    [ "$REF" = "refs/heads/main" ] || refuse "Production accepts only refs/heads/main."
    ;;
  Staging)
    [ "$REF" = "refs/heads/staging" ] || [ "$REF" = "refs/heads/main" ] \
      || refuse "Staging accepts only refs/heads/staging or refs/heads/main."
    ;;
  *)
    refuse "unknown environment (expected Production or Staging)."
    ;;
esac

if [ -n "$REQUIRED" ] && [ "$REF" != "refs/heads/$REQUIRED" ]; then
  refuse "this run deploys branch '$REQUIRED' and must be started from it."
fi

echo "Ref check passed: $ENV_NAME from $REF."
