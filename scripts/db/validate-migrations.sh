#!/usr/bin/env bash
# Static validation + safety gate for Flyway migrations. Runs in CI (no database needed) and
# stops a deployment before risky schema changes reach an environment.
#
# Checks:
#   - Flyway naming convention (V<n>__desc.sql / R__desc.sql)
#   - Duplicate version numbers
#   - Empty migration files
#   - Destructive / dangerous SQL — flagged unless the file carries an explicit ack line:
#       -- flyway:safety-ack: <reason>
#
# Usage: validate-migrations.sh [migrations_dir] [baseline_version]
#   migrations_dir   default: backend/src/main/resources/db/migration
#   baseline_version default: 11 (V6-V11 are inert pre-Flyway history; only V12+ are gated)
# Exit:  0 = ok; 1 = validation failed (naming/dupes/unacked destructive SQL above baseline).
set -uo pipefail

DIR="${1:-backend/src/main/resources/db/migration}"
BASELINE="${2:-11}"
fail=0

if [ ! -d "$DIR" ]; then
  echo "No migration directory at '$DIR' — nothing to validate."
  exit 0
fi

echo "== Validating Flyway migrations in $DIR =="

# Dangerous patterns (case-insensitive). Type changes via MODIFY/CHANGE are risky on populated tables.
DANGER='DROP[[:space:]]+TABLE|DROP[[:space:]]+DATABASE|DROP[[:space:]]+SCHEMA|DROP[[:space:]]+COLUMN|TRUNCATE|ALTER[[:space:]]+TABLE[[:space:]].*[[:space:]]DROP[[:space:]]|RENAME[[:space:]]+TABLE|[[:space:]]MODIFY[[:space:]]|[[:space:]]CHANGE[[:space:]]'

declare -A seen_versions
shopt -s nullglob
files=("$DIR"/*.sql)

if [ "${#files[@]}" -eq 0 ]; then
  echo "No .sql migrations found (baseline-only). OK."
  exit 0
fi

for f in "${files[@]}"; do
  base="$(basename "$f")"

  # README and non-migration files are skipped by extension already (*.sql only).
  # Naming convention.
  if [[ "$base" =~ ^V([0-9]+(\.[0-9]+)*)__.+\.sql$ ]]; then
    ver="${BASH_REMATCH[1]}"
    if [ -n "${seen_versions[$ver]:-}" ]; then
      echo "  [FAIL] duplicate version V$ver: $base and ${seen_versions[$ver]}"
      fail=1
    fi
    seen_versions[$ver]="$base"
  elif [[ "$base" =~ ^R__.+\.sql$ ]]; then
    : # repeatable migration — allowed
  else
    echo "  [FAIL] bad name '$base' — expected V<n>__desc.sql or R__desc.sql"
    fail=1
    continue
  fi

  # Non-empty (ignoring comments/blank).
  if ! grep -qvE '^[[:space:]]*(--.*)?$' "$f"; then
    echo "  [FAIL] $base contains no SQL statements"
    fail=1
  fi

  # Destructive SQL → require an explicit ack line. Only gate migrations ABOVE the baseline;
  # V1..baseline are inert pre-Flyway history that Flyway never executes.
  is_new=1
  if [[ "$base" =~ ^V([0-9]+) ]] && [ "${BASH_REMATCH[1]}" -le "$BASELINE" ]; then
    is_new=0
  fi
  if grep -qiE "$DANGER" "$f"; then
    if [ "$is_new" -eq 0 ]; then
      echo "  [INFO] $base has destructive SQL but is at/below baseline V$BASELINE (never executed) — skipped"
    elif grep -qiE '^[[:space:]]*--[[:space:]]*flyway:safety-ack:' "$f"; then
      echo "  [WARN] $base has destructive SQL — ACKED (explicit review recorded in file)"
    else
      echo "  [FAIL] $base has destructive/dangerous SQL without an ack."
      echo "         Review carefully; if intended, add: -- flyway:safety-ack: <reason>"
      grep -inE "$DANGER" "$f" | sed 's/^/           /'
      fail=1
    fi
  fi
done

echo "Validated ${#files[@]} migration file(s)."
if [ "$fail" -ne 0 ]; then
  echo "== Migration validation FAILED =="
  exit 1
fi
echo "== Migration validation passed =="
