#!/usr/bin/env bash
# Verify the frontend host serves the hardened nginx config (frontend/nginx/*.conf).
#
#   scripts/deploy/check-http-headers.sh https://staging-app.axonxmedtech.com
#   scripts/deploy/check-http-headers.sh http://localhost:8080 --no-tls   # local / Docker
#
# Exits non-zero and lists every failed check. Read-only: it only sends GET requests.
set -uo pipefail

BASE="${1:?usage: $0 <base-url> [--no-tls]}"
BASE="${BASE%/}"
NO_TLS="${2:-}"
FAILS=0
CURL=(curl -sS --max-time 15)

pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; FAILS=$((FAILS + 1)); }

# headers <path> [extra curl args...] → response headers, lower-cased names
headers() {
  local path="$1"; shift
  "${CURL[@]}" -o /dev/null -D - "$@" "$BASE$path" | tr -d '\r' | awk -F': ' 'BEGIN{OFS=": "} {$1=tolower($1); print}'
}
has() { grep -qi "^$2" <<<"$1"; }
value() { grep -i "^$2:" <<<"$1" | head -1 | cut -d' ' -f2-; }
status() { "${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE$1"; }

echo "Checking $BASE"

root=$(headers /)
if [[ -z "$root" ]]; then
  echo "  FAIL  no response from $BASE"
  exit 1
fi

echo "Security headers (/):"
for h in x-content-type-options x-frame-options referrer-policy permissions-policy x-robots-tag; do
  has "$root" "$h:" && pass "$h: $(value "$root" "$h")" || fail "$h missing"
done
if has "$root" "content-security-policy:" || has "$root" "content-security-policy-report-only:"; then
  mode=$(has "$root" "content-security-policy:" && echo enforced || echo report-only)
  pass "content-security-policy ($mode)"
else
  fail "content-security-policy (enforced or report-only) missing"
fi
if [[ "$NO_TLS" != "--no-tls" ]]; then
  has "$root" "strict-transport-security:" && pass "strict-transport-security: $(value "$root" strict-transport-security)" \
    || fail "strict-transport-security missing"
fi
has "$root" "server: nginx/" && fail "server header reveals the nginx version ($(value "$root" server))" \
  || pass "server version hidden"

echo "Caching:"
cc=$(value "$root" cache-control)
[[ "$cc" == *no-cache* || "$cc" == *no-store* ]] && pass "index.html Cache-Control: $cc" \
  || fail "index.html should be no-cache (got: ${cc:-none})"

asset=$("${CURL[@]}" "$BASE/" | grep -oE '/assets/[^"]+\.js' | head -1)
if [[ -z "$asset" ]]; then
  fail "no /assets/*.js referenced from index.html"
else
  ah=$(headers "$asset" -H 'Accept-Encoding: gzip')
  acc=$(value "$ah" cache-control)
  [[ "$acc" == *immutable* && "$acc" == *max-age=31536000* ]] && pass "$asset Cache-Control: $acc" \
    || fail "$asset should be cached immutable for a year (got: ${acc:-none})"
  [[ "$(value "$ah" content-encoding)" == gzip* ]] && pass "$asset served gzip" || fail "$asset not compressed"
  has "$ah" "x-content-type-options:" && pass "$asset keeps security headers" \
    || fail "$asset lost the security headers (add_header inheritance)"
fi

echo "Paths:"
[[ "$(status /assets/does-not-exist-0000.js)" == 404 ]] && pass "missing asset → 404" \
  || fail "missing asset should be 404, not index.html"
for p in /.env /.git/config; do
  [[ "$(status "$p")" == 404 ]] && pass "$p → 404" || fail "$p should be 404"
done
rt=$(headers /robots.txt)
[[ "$(value "$rt" content-type)" == text/plain* ]] && pass "/robots.txt is a real file" \
  || fail "/robots.txt is not served as text/plain (SPA fallback?)"
[[ "$(status /some/client/route)" == 200 ]] && pass "client-side routes fall back to index.html" \
  || fail "client-side route did not return 200"

echo
if [[ "$FAILS" -gt 0 ]]; then
  echo "$FAILS check(s) failed."
  exit 1
fi
echo "All checks passed."
