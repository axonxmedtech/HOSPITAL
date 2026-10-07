#!/usr/bin/env bash
# Redact credentials and personal identifiers from a service log before it is uploaded as a
# workflow artifact. Reads stdin, writes stdout.
#
# Deployment artifacts of this public repository are readable by any signed-in GitHub user. The
# service journal they carry once held bound SQL values: staff e-mail addresses and BCrypt password
# hashes. Every line is kept so the log stays a usable diagnostic; only the sensitive value in it is
# replaced:
#   Authorization headers, Bearer tokens and JWTs; BCrypt hashes; credentials embedded in URLs or
#   JDBC query strings; values of password/secret/token/key/cookie/session-like keys; e-mail
#   addresses.
set -euo pipefail

exec perl -pe '
  s{(authorization["\x27]?\s*[:=]\s*)(?:(bearer|basic)\s+)?[^\s,;"\x27]+}{$1 . ($2 ? "$2 " : "") . "[REDACTED]"}gie;
  s{\bbearer\s+[A-Za-z0-9._~+/=-]+}{Bearer [REDACTED]}gi;
  s{\beyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]*}{[REDACTED_JWT]}g;
  s{\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}}{[REDACTED_HASH]}g;
  s{\b([a-z][a-z0-9+.-]*://)[^/\s:@]+:[^/\s@]+@}{${1}[REDACTED]@}gi;
  s{([?&;](?:password|passwd|pwd|user|username|token|secret)=)[^&;\s]*}{${1}[REDACTED]}gi;
  s{\b([A-Za-z0-9_.-]*(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|credential|cookie|session[_-]?id))(["\x27]?\s*[:=]\s*)("[^"]*"|\x27[^\x27]*\x27|[^\s,;&]+)}{${1}${2}[REDACTED]}gi;
  s{\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b}{[REDACTED_EMAIL]}g;
'
