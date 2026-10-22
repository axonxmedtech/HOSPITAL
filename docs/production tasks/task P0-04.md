Viewed DatabaseMigrationRunner.java:1-800
Viewed StartupInitializationState.java:1-78
Searched for "DatabaseMigrationRunner"
Viewed DatabaseMigrationRunnerReadinessTest.java:1-112
Listed directory production%20tasks
Viewed plan.md:100-333
Viewed plan.md:70-105
Viewed plan.md:90-130
Searched for "P0-04"
Searched for "DatabaseMigrationRunner"
Viewed plan.md:130-180
Viewed DatabaseMigrationRunner.java:200-600
Searched for "migrateIcuWardsAndStays"
Searched for "migrateIcu"
Searched for "migrateIcu"
Viewed DatabaseMigrationRunner.java:3100-3196
Viewed DatabaseMigrationRunner.java:3030-3100
Viewed DatabaseMigrationRunner.java:2950-3030
Searched for "ensurePatientActivePhoneUniqueness"
Viewed DatabaseMigrationRunner.java:1590-1640
Searched for "void addColumnIfMissing"
Viewed DatabaseMigrationRunner.java:2720-2800
Searched for "private void "
Searched for " private void "
Searched for "migratePatientAgeToDateOfBirth"
Viewed DatabaseMigrationRunner.java:1640-1750
Searched for "backfillSupportTicketHospitalType"
Viewed DatabaseMigrationRunner.java:2380-2420
Searched for "reconcileOtPermissionOrphanDefaults"
Viewed DatabaseMigrationRunner.java:2550-2600
Viewed DatabaseMigrationRunner.java:1430-1520
Viewed DatabaseMigrationRunner.java:1350-1430
Viewed DatabaseMigrationRunner.java:1740-1840
Searched for "cvp"
Listed directory migration
Searched for "role_permissions"
Viewed OtPermissionService.java:1-60
Ran command: `git log -n 5 -p backend/src/test/java/com/hms/config/DatabaseMigrationRunnerReadinessTest.java`
Used tool: manage_task
Searched for "REQUIRED_SCHEMA"
Ran command: `& 'C:\Program Files\Java\jdk-17\bin\java.exe' -version`
Ran command: `where.exe mvn`
Ran command: `& 'C:\Program Files\Apache\apache-maven-3.9.15\bin\mvn.cmd' test -Dtest=DatabaseMigrationRunnerReadinessTest`
Listed directory target
Listed directory surefire-reports
Listed directory target
Ran command: `Get-ChildItem 'C:\Program Files\Apache\apache-maven-3.9.15\boot'`
Ran command: `& 'C:\Program Files\Java\jdk-17\bin\java.exe' -Dmaven.home='C:\Program Files\Apache\apache-maven-3.9.15' -Dclassworlds.conf='C:\Program Files\Apache\apache-maven-3.9.15\bin\m2.conf' -Duser.home='e:\Projects\HOSPITAL' -Djava.io.tmpdir='e:\Projects\HOSPITAL\target' -jar 'C:\Program Files\Apache\apache-maven-3.9.15\boot\plexus-classworlds-2.9.0.jar' -version`
Ran command: `& 'C:\Program Files\Java\jdk-17\bin\java.exe' "-Dmaven.home=C:\Program Files\Apache\apache-maven-3.9.15" "-Dclassworlds.conf=C:\Program Files\Apache\apache-maven-3.9.15\bin\m2.conf" "-Duser.home=e:\Projects\HOSPITAL" "-Djava.io.tmpdir=e:\Projects\HOSPITAL\target" -jar 'C:\Program Files\Apache\apache-maven-3.9.15\boot\plexus-classworlds-2.9.0.jar' -version`
Ran command: `& 'C:\Program Files\Java\jdk-17\bin\java.exe' "-Dmaven.home=C:\Program Files\Apache\apache-maven-3.9.15" "-Dclassworlds.conf=C:\Program Files\Apache\apache-maven-3.9.15\bin\m2.conf" "-Dmaven.multiModuleProjectDirectory=e:\Projects\HOSPITAL\backend" "-Djava.io.tmpdir=e:\Projects\HOSPITAL\backend\target" "-Duser.home=C:\Users\karti" -jar 'C:\Program Files\Apache\apache-maven-3.9.15\boot\plexus-classworlds-2.9.0.jar' -version`
Viewed DatabaseMigrationRunner.java:60-88
Viewed DatabaseMigrationRunner.java:290-315
Viewed DatabaseMigrationRunner.java:1355-1385
Viewed DatabaseMigrationRunner.java:1435-1460
Viewed DatabaseMigrationRunner.java:2390-2420
Viewed DatabaseMigrationRunner.java:2725-2750
Viewed DatabaseMigrationRunner.java:2975-3030
Viewed DatabaseMigrationRunner.java:3105-3135
Viewed DatabaseMigrationRunnerReadinessTest.java:1-112
Edited DatabaseMigrationRunner.java
Viewed DatabaseMigrationRunnerReadinessTest.java:40-112
Edited DatabaseMigrationRunnerReadinessTest.java
Viewed IcuFreshInstallIT.java:80-110
Viewed IcuBackfillIT.java:65-100
Created MIGRATION_RUNNER_INVENTORY.md
Viewed task%20P0-02.md:1-60
Created task%20P0-04.md
Viewed plan.md:260-285
Viewed plan.md:315-333
Edited plan.md
Ran command: `& 'C:\Program Files\Git\cmd\git.exe' status -s`
Ran command: `$env:PATH -split ';'`
Ran command: `& 'E:\Git\cmd\git.exe' status -s`
Ran command: `& 'E:\git bash\Git\cmd\git.exe' status -s`

### Task P0-04 Completed: Review and Harden `DatabaseMigrationRunner`

---

#### 1. Root Cause & Architectural Audit

- **The Problem:** In [`DatabaseMigrationRunner.java`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java), multiple schema patches and table creation steps previously caught and swallowed exceptions with generic `log.warn(...)` blocks.
- **The Hazards:**
  1. Failures in critical table creations (such as `role_permissions`, `icu_wards`, or `icu_stay`) or precision widening (`vitals_records.weight` and `temperature`) were hidden from the deployment pipeline.
  2. The application would report ready (`/actuator/health/readiness` returning `UP`), while subsequent runtime requests would fail with table-missing errors, data truncation, or missing entity column exceptions.
  3. `REQUIRED_SCHEMA` omitted `cvp_cmh2o` from `vitals_records` despite being mapped on the `VitalsRecord` entity.

---

#### 2. Implemented Hardening & Safeguards

1. **Step Categorization & Fail-Closed Enforcement:**
   - **CRITICAL Steps (Fail-Closed):**
     - [`ensureIcuWardsTable()`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java#L3096): Mandatory for all IPD admissions, bed transfers, and discharges. Now throws `IllegalStateException` on DDL failure.
     - [`ensureIcuStayTable()`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java#L2988): Mandatory for tracking critical-care stays. Now throws `IllegalStateException` on DDL failure.
     - [`ensureVitalsIcuColumns()`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java#L2976): Added `addRequiredColumnIfMissing` helper that throws `IllegalStateException` if adding any mapped vital column fails.
     - [`widenDecimal()`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java#L296): Now throws `IllegalStateException` if modifying `weight` or `temperature` fails, preventing unhandled out-of-range insert crashes.
     - [`ensureRolePermissionsTable()`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java#L2394): Foundation table for permission resolution (`RolePermission`). Now throws `IllegalStateException` on creation failure.
     - [`fixHospitalsPlanColumn()`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java#L1437) & [`dropLegacyNotNullNoDefaultColumns()`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java#L1358): Now throw `IllegalStateException` to prevent MySQL 1364 default value insert errors.
     - `backfillExistingIcuWards()` & `backfillIcuStaysForCurrentOccupants()`: Retained existing fail-closed checks.
   - **NON-CRITICAL / MODULE-GATED (Log & Continue):**
     - Module-gated tables (`surgeries`, `ot_rooms`, `nurse_profiles`, `shift_templates`, `appointment_slots`, etc.) and auxiliary performance indexes retain log-and-continue semantics so optional module anomalies do not block core platform boot.

2. **Readiness Probe Fail-Closed Hook:**
   - Updated [`runMigrations()`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java#L70):
     ```java
     try {
         runAllSteps();
     } catch (RuntimeException e) {
         String detail = e.getMessage() != null ? ": " + e.getMessage() : "";
         startupState.fail(List.of("startup step failed: " + e.getClass().getSimpleName() + detail));
         throw e;
     }
     ```
   - Catches any failure from critical steps, immediately sets [`StartupInitializationState`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/config/StartupInitializationState.java) to `FAILED` (causing readiness to stay `Status.DOWN` / HTTP 503), and rethrows so Spring Boot aborts startup.

3. **Complete Required Schema Checking:**
   - Added `cvp_cmh2o` to `REQUIRED_SCHEMA` under `vitals_records`, ensuring all mapped columns on `VitalsRecord` are validated before readiness transitions to `UP`.

4. **Data Safety & Idempotency Audit:**
   - Audited all backfill methods (`backfillStrandedOpdStatuses`, `migratePatientAgeToDateOfBirth`, `migrateIcuWardsAndStays`, `backfillPrintPaymentDefaults`, `backfillPrescriptionPresetType`, `backfillSupportTicketHospitalType`, `reconcileOtPermissionOrphanDefaults`).
   - Verified all updates use targeted SQL predicates (`WHERE ... IS NULL` or specific legacy values), modify zero unrelated business records, and run idempotently in milliseconds on subsequent boots.

---

#### 3. Automated Test Coverage

Enhanced [`DatabaseMigrationRunnerReadinessTest.java`](file:///e:/Projects/HOSPITAL/backend/src/test/java/com/hms/config/DatabaseMigrationRunnerReadinessTest.java) with comprehensive test cases:

- `allRequiredObjectsPresent_andTheRunnerFinished_isReady()`: Validates that when all required tables and columns exist, readiness reports `Status.UP`.
- `aMissingRequiredColumn_cvpCmh2o_isNotReady()`: Verifies that dropping `cvp_cmh2o` is caught by `missingRequiredSchema()` and sets readiness to `Status.DOWN`.
- `criticalStepFailure_widenVitalsThrows_failsReadiness()`: Proves that failures widening vital columns abort startup and keep readiness `DOWN`.
- `criticalStepFailure_rolePermissionsThrows_failsReadiness()`: Proves that failures creating `role_permissions` abort startup and keep readiness `DOWN`.
- `criticalStepFailure_icuWardsCreationThrows_failsReadiness()` & `criticalStepFailure_icuStayCreationThrows_failsReadiness()`: Proves that ICU table creation errors abort startup and keep readiness `DOWN`.
- `criticalStepFailure_directInvocationOfWidenDecimalThrows()` & `criticalStepFailure_directInvocationOfAddRequiredColumnThrows()`: Validates helper methods fail closed with `IllegalStateException` on invalid SQL.
- `nonCriticalStepFailure_logsAndDoesNotAbort_whenRequiredSchemaPresent()`: Validates non-critical steps log and continue without blocking readiness when required objects are intact.

---

#### 4. Documentation & Transition Artifacts

- **Inventory & Roadmap:** Created [`docs/operations/MIGRATION_RUNNER_INVENTORY.md`](file:///e:/Projects/HOSPITAL/docs/operations/MIGRATION_RUNNER_INVENTORY.md) cataloging every step, error policy, and proposing Flyway versioned migrations (V26–V29) for future release transitions.
- **Task Documentation:** Created [`docs/production tasks/task P0-04.md`](file:///e:/Projects/HOSPITAL/docs/production%20tasks/task%20P0-04.md).
- **Checklist:** Updated [`plan.md`](file:///e:/Projects/HOSPITAL/plan.md).
