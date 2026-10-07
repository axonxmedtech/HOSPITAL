#!/usr/bin/env bash
# Resolve the backend health port for a deployment, ONCE, inside the environment-scoped job.
#
# Environment-level GitHub variables are only readable in a job that declares `environment:`, so
# the caller of a reusable workflow cannot pass them in: they arrive empty. The deploy and
# rollback jobs therefore pass both candidates here and this picks one:
#   INPUT_PORT       explicit override (optional); used as-is when non-empty
#   ENV_NAME         the GitHub Environment the job runs in: Staging | Production
#   STAGING_PORT     vars.STAGING_HEALTH_PORT
#   PRODUCTION_PORT  vars.PRODUCTION_HEALTH_PORT
#
# Prints `port=<value>` for $GITHUB_OUTPUT. There is no default: an unset variable resolves to an
# empty port, which validate-config.sh rejects before anything on the server changes. Any other
# environment name resolves to no port, override or not, and fails here.
set -uo pipefail

port=""
rc=0
case "${ENV_NAME:-}" in
  Staging)    port="${STAGING_PORT:-}" ;;
  Production) port="${PRODUCTION_PORT:-}" ;;
  *)
    # Never borrow Staging's or Production's port for an environment we don't know -- not even
    # with an explicit override.
    echo "::error::Deployment blocked — no health port mapping for this environment (expected Staging or Production)" >&2
    rc=1 ;;
esac
[ "$rc" -eq 0 ] && [ -n "${INPUT_PORT:-}" ] && port="$INPUT_PORT"
# A multi-line value could forge other step outputs; treat it as no port at all.
case "$port" in *$'\n'*|*$'\r'*) port="" ;; esac

echo "port=$port"
exit "$rc"
