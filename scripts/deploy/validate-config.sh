#!/usr/bin/env bash
# Validate that required deployment configuration is present BEFORE touching the server.
# Checks each named environment variable is non-empty. NEVER prints values — only names.
#
# Usage: validate-config.sh VAR_NAME_1 VAR_NAME_2[:port] ...
#   A ":port" suffix also requires the value to be a TCP port: digits only, 1-65535. Used for the
#   health port, which has no default: a default once pointed staging verification at another
#   service on the same host.
# Exit:  0 = all present and valid; 1 = one or more missing/invalid (deployment must stop).
set -uo pipefail

missing=()
invalid=()
for spec in "$@"; do
  name="${spec%%:*}"
  kind=""
  [ "$spec" != "$name" ] && kind="${spec#*:}"
  val="${!name:-}"
  if [ -z "$val" ]; then
    missing+=("$name")
    continue
  fi
  if [ "$kind" = "port" ]; then
    if ! printf '%s' "$val" | grep -qE '^[0-9]{1,5}$' || [ "$((10#$val))" -lt 1 ] || [ "$((10#$val))" -gt 65535 ]; then
      invalid+=("$name")
    fi
  fi
done

rc=0
if [ "${#missing[@]}" -gt 0 ]; then
  echo "::error::Deployment blocked — missing required configuration: ${missing[*]}"
  rc=1
fi
if [ "${#invalid[@]}" -gt 0 ]; then
  echo "::error::Deployment blocked — invalid port (must be 1-65535): ${invalid[*]}"
  rc=1
fi
if [ "$rc" -ne 0 ]; then
  echo "Set these as GitHub Environment Variables/Secrets. (Values are never printed by this check.)"
  exit 1
fi
echo "Configuration check passed: all $# required value(s) present."
