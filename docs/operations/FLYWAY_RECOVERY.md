# Operator Runbook: Flyway Migration Failure Triage & Recovery

**Project:** AxonX Medtech Hospital Management System (HMS)  
**Document ID:** SOP-OPS-FLYWAY-001  
**Scope:** Staging and Production Database Schema Migrations  
**Severity:** P0 Release / Startup Blocker

---

## 1. Overview & Policy

Under Task **P0-02**, automatic Flyway repair on normal application startup is **STRICTLY PROHIBITED**.

### The Principle

- In a production healthcare system, database schema migrations must **fail closed**.
- Automatic repair silently deletes failed migration records (`success = 0`) and aligns recorded checksums to modified files without human review.
- If an applied migration script was modified or a migration partially failed, application startup halts immediately with a clear error message.
- An operator must investigate the root cause before authorizing any repair or re-migration.

---

## 2. Diagnosing Startup Failures

When Flyway blocks startup, Spring Boot logs will display one of two primary failure modes:

### Mode 1: Checksum Mismatch (`FlywayValidateException`)

```text
org.flywaydb.core.api.exception.FlywayValidateException: Validate failed:
Migrations have failed validation
Migration checksum mismatch for migration version 24
-> Applied to database : 1234567890
-> Resolved locally    : -9876543210
```

**Meaning:** The SQL script file `V24__add_import_batch_active_uniqueness.sql` in the application JAR has a different checksum than what was recorded when that migration was executed on this database.

### Mode 2: Failed Migration Row (`FlywayException`)

```text
org.flywaydb.core.api.FlywayException:
Schema `hms` contains a failed migration to version 24 !
Please revert any changes to the database and run repair before migrating again.
```

**Meaning:** A previous deployment executed migration V24, but it failed midway (e.g. syntax error, constraint violation, lock timeout) and recorded `success = 0` in `flyway_schema_history`. Flyway refuses to proceed because the database may be in an inconsistent state.

---

## 3. Investigation Steps

Connect to the affected database using a read-only administrative connection and inspect `flyway_schema_history`:

```sql
-- 1. Inspect recent migrations and status
SELECT installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

-- 2. Check specifically for failed migrations
SELECT *
FROM flyway_schema_history
WHERE success = 0;
```

---

## 4. Remediation Procedures

### Remediation Path A: Resolving Checksum Mismatches

1. **Investigate the Git History:**
   Find out what changed in the SQL script:
   ```bash
   git log -p -n 5 backend/src/main/resources/db/migration/V<version>__*.sql
   ```
2. **Determine Validity:**
   - **Case A.1 (Accidental / Unapproved Modification):** An applied migration script was modified after release (e.g. whitespace change, formatting, or unintended edit).  
     _Action:_ Revert the SQL script in git to match the original applied commit, rebuild the release artifact, and redeploy. **Do not run repair.**
   - **Case A.2 (Authorized Hotfix / Re-alignment):** The engineering and architecture owners explicitly approved an idempotent script fix for an applied migration (documented in release notes).  
     _Action:_ Proceed to [Section 5: Authorized Manual Repair](#5-authorized-manual-repair).

---

### Remediation Path B: Resolving Failed Migrations (`success = 0`)

1. **Check Database Schema State:**
   - Review the DDL statements in the failing migration script.
   - Query `information_schema` to determine which tables, columns, or indexes from that script were created before the failure occurred.
2. **Roll Back / Clean Up Partial DDL:**
   - If tables/columns were partially created, drop or revert the partial artifacts manually so the migration can run cleanly from scratch.
3. **Resolve the Underlying Cause:**
   - If the failure was due to duplicate data violating a new unique index, clean up or resolve the conflicting data using preflight scripts.
   - If the failure was due to lock timeout or permissions, ensure adequate locks and grants are available.
4. **Repair Schema History:**
   - Once the database is cleaned and verified, proceed to [Section 5: Authorized Manual Repair](#5-authorized-manual-repair) to clear the failed migration entry.

---

## 5. Authorized Manual Repair

Once the operator has verified the database state and obtained authorization:

### Method 1: Spring Boot One-Time Repair Flag (Recommended)

Run the application with the explicit repair property enabled:

```bash
java -jar hms-backend-<version>.jar \
  --spring.profiles.active=production \
  --hms.flyway.repair-on-startup=true
```

**What this does:**

1. Emits a prominent `EMERGENCY OVERRIDE` warning in audit logs.
2. Invokes `flyway.repair()`, removing `success = 0` entries and realigning verified checksums.
3. Immediately invokes `flyway.migrate()`.
4. After startup succeeds, **stop the instance** and restart under normal mode (without `--hms.flyway.repair-on-startup=true`).

### Method 2: Maven Plugin (Dev/Staging Rehearsal Environments)

In non-production rehearsal environments:

```bash
mvn flyway:repair -Dflyway.configFiles=...
```

---

## 6. Verification Checklist Before Declaring Healthy

- [ ] Query `SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0;` returns `0`.
- [ ] Application starts cleanly **without** `--hms.flyway.repair-on-startup=true`.
- [ ] Health endpoint `/actuator/health/readiness` reports `UP`.
- [ ] Core patient, doctor, and hospital record counts match expected preflight counts.
- [ ] Audit logs record the operator incident and authorized remediation rationale.
