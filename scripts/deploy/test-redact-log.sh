#!/usr/bin/env bash
# Tests for redact-log.sh: each sensitive form is replaced, and ordinary deployment diagnostics come
# through unchanged. Fixture values are synthetic.
# Fixtures are literal text (`${{ ... }}`, `$2a$...`), so single quotes are intended.
# shellcheck disable=SC2016
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REDACT="$HERE/redact-log.sh"

PASS=0; FAIL=0
same() { # same <name> <input> <expected output>
  local got
  got=$(printf '%s\n' "$2" | bash "$REDACT")
  if [ "$got" = "$3" ]; then PASS=$((PASS + 1)); echo "PASS  $1";
  else FAIL=$((FAIL + 1)); echo "FAIL  $1"; echo "        expected: $3"; echo "        got:      $got"; fi
}
unchanged() { same "$1 (unchanged)" "$2" "$2"; }

HASH='$2a$10$abcdefghijklmnopqrstuuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'   # 53-char body, like BCrypt
JWT='eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl'   # synthetic, unsigned fixture  pragma: allowlist secret

same "Authorization: Bearer header" "GET /x Authorization: Bearer abc.def-123" "GET /x Authorization: Bearer [REDACTED]"
same "Authorization: Basic header"  "authorization=Basic dXNlcjpwYXNz"         "authorization=Basic [REDACTED]"
same "bare Bearer token"            "token was Bearer abc123xyz"               "token was Bearer [REDACTED]"
same "JWT"                          "jwt $JWT seen"                            "jwt [REDACTED_JWT] seen"
same "BCrypt hash"                  "binding parameter (2:VARCHAR) <- [$HASH]" "binding parameter (2:VARCHAR) <- [[REDACTED_HASH]]"
same "credentials in a URL"         "connecting to mysql://app:s3cret@db:3306/x" "connecting to mysql://[REDACTED]@db:3306/x"
same "JDBC query-string password"   "jdbc:mysql://localhost:3306/hms?user=app&password=s3cret&useSSL=false" "jdbc:mysql://localhost:3306/hms?user=[REDACTED]&password=[REDACTED]&useSSL=false"
same "key=value password"           "spring.datasource.password=s3cret"        "spring.datasource.password=[REDACTED]"
same "env-style secret"             "Environment=JWT_SECRET=abcdef0123456789"  "Environment=JWT_SECRET=[REDACTED]"
same "key: value password"          "[DataInitializer]   Password: hunter2"   "[DataInitializer]   Password: [REDACTED]"
same "JSON password"                '{"email":"x","password":"hunter2"}'       '{"email":"x","password":[REDACTED]}'
same "quoted token value"           "api_key='abc 123' next"                   "api_key=[REDACTED] next"
same "cookie header"                "Cookie: JSESSIONID=abc123"                "Cookie: [REDACTED]"
same "session id"                   "session_id=abc123; path=/"                "session_id=[REDACTED]; path=/"
same "e-mail address"               "login for jane.doe@clinic.example.org ok" "login for [REDACTED_EMAIL] ok"

unchanged "Tomcat start"       "INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8081 (http) with context path '/'"
unchanged "Spring start"       "INFO  c.h.HospitalManagementSystemApplication - Started HospitalManagementSystemApplication in 35.854 seconds (process running for 37.636)"
unchanged "startup state"      "INFO  c.h.c.StartupInitializationState - Startup initialization complete"
unchanged "Flyway"             "INFO  o.f.core.internal.command.DbMigrate - Schema \`hms_staging\` is up to date. No migration necessary."
unchanged "JWT filter config"  "DEBUG c.h.security.JwtAuthenticationFilter - Filter 'jwtAuthenticationFilter' configured for use"
unchanged "validator message"  "Production configuration validated: JWT secret and frontend origins are acceptable."
unchanged "token version"      "tokenVersion=3 token_version=4"
unchanged "systemd line"       "Oct 07 14:26:14 srv1739753 systemd[1]: Started hms-staging.service - HMS Staging."
unchanged "verifier line"      "[2026-10-07T08:56:54Z] Stable 1/6"
unchanged "empty line"         ""

echo
echo "redact-log.sh: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
