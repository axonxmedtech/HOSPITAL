# Flyway migrations

Versioned schema migrations for HMS. Full policy: [docs/database/DATABASE_MIGRATIONS.md](../../../../../docs/database/DATABASE_MIGRATIONS.md).

## Baseline — start new migrations at **V12**

`V6`–`V11` here are **pre-Flyway historical artifacts**. They were committed before Flyway was
wired up and were **never executed** (some even contain Postgres-only syntax). The live schema was
actually built by Hibernate `ddl-auto` + `DatabaseMigrationRunner` + `setup/schema-full.sql`.

Staging/prod therefore enable Flyway with **`baseline-on-migrate=true`, `baseline-version=11`**:
existing populated databases are adopted at V11 and Flyway **never runs V1–V11** against them — no
table is recreated, no data touched. **Author new changes as `V12__…`, `V13__…` (MySQL syntax).**

Do **not** edit or "fix" V6–V11 — they are inert below the baseline.

## Rules
- **Naming:** `V<n>__short_description.sql` (double underscore) or `R__description.sql` (repeatable).
- **Never edit an applied migration** (Flyway checksums them) — fix-forward with the next `V<n>__`.
- **Additive first (backward compatible):** new tables / nullable columns / backfills. Stage
  destructive changes across releases (see policy doc — hospitals may run older versions).
- **Destructive SQL needs an explicit ack.** `scripts/db/validate-migrations.sh` (CI) flags
  `DROP TABLE/COLUMN`, `TRUNCATE`, `DELETE` without `WHERE`, `RENAME`, type `MODIFY/CHANGE` in
  migrations **above the baseline**. To intentionally allow one, add:
  `-- flyway:safety-ack: <reason and review reference>`
- **One logical change per migration**; keep them small and reviewable.

## Environments
- **dev/test:** Flyway disabled (`spring.flyway.enabled=false`); Hibernate `ddl-auto=update`.
- **staging:** Flyway enabled, `ddl-auto=update`, baseline V11, `clean-disabled`. `update` is
  required because `DatabaseMigrationRunner` (which owns ~40 tables + ~50 columns) runs on
  `ApplicationReadyEvent` — under `validate` a single missing object aborts startup before the
  runner can create it. `update` only adds tables/columns (never drops), so it stays non-destructive.
- **prod:** Flyway enabled, `ddl-auto=validate`, baseline V11, `clean-disabled` (a full-schema drop
  can never run). Prod must stay on `validate` — see the guardrail in `application-prod.properties`.
  Any schema the runner adds must reach prod via a `V12+` Flyway migration before that code deploys.
