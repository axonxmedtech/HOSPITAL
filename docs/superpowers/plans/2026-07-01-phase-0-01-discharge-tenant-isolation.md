# Phase 0.1 — Discharge Summary Tenant Isolation (IDOR Fix) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the two live cross-tenant IDOR holes on the discharge flow (`planDischarge`, `confirmDischarge` load an IPD by id and never compare its `hospitalId` to the caller's) and make `discharge_summary` self-contained by carrying `hospital_id` / `patient_id` / `doctor_id` for defense-in-depth and direct tenant-scoped queries.

**Architecture:** Two-layer fix. (1) **Service guards** — add a fail-closed tenant-ownership check immediately after each `ipdAdmissionRepository.findById(...)` in the discharge paths, throwing `AccessDeniedException` before any state is read or written. (2) **Data model** — add three nullable, additive columns to `DischargeSummary` (auto-created by `ddl-auto=update`), backfill existing rows idempotently via `DatabaseMigrationRunner`, populate them on create in `planDischarge`, and mirror the change in the canonical `setup/schema-full.sql`. Every migration is additive and reversible; no existing endpoint contract changes.

**Tech Stack:** Spring Boot, Spring Data JPA (`ddl-auto=update`), Lombok, JUnit 5 + Mockito + AssertJ, MySQL. Migrations run at startup via `com.hms.config.DatabaseMigrationRunner` (idempotent, `information_schema`-guarded, individually try/caught).

---

## Context the engineer needs before starting

- **This is the first of several Phase 0 (Foundations) sub-plans**, sequenced first because it is security-critical and fully self-contained. Do not pull in other Phase 0 items (Patient model additions, staff identity, role framework, VitalSigns BP split, NurseTask decoupling) here — they have their own sub-plans.
- **Tenant model:** every hospital-scoped entity carries `hospital_id`. The caller's hospital is `securityHelper.getCurrentHospitalId()` (type `com.hms.security.SecurityContextHelper`, injected as the field `securityHelper`). An IDOR = loading a record by primary key and acting on it without confirming `record.getHospitalId().equals(currentHospitalId)`.
- **Files in play:**
  - `backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java` — `planDischarge` starts at line 900, `confirmDischarge` at line 946. Injected fields already present: `securityHelper`, `ipdAdmissionRepository`, `dischargeSummaryRepository`, `hospitalSettingRepository`, `hospitalRepository`, `billingRepository`, `auditLogService`, `webSocketHandler`.
  - `backend/src/main/java/com/hms/entity/DischargeSummary.java` — currently only `id`, `ipdAdmissionId` (unique, non-null), `finalDiagnosis`, `treatmentGiven`, `dischargeNotes`, `followUpDate`, `createdAt`.
  - `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java` — add one backfill method, register it in `runMigrations()`.
  - `setup/schema-full.sql` — `discharge_summary` DDL at lines 161-171 (canonical schema, must mirror entity).
  - `backend/src/test/java/com/hms/service/` — existing service tests use `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`, AssertJ. See `IpdAdmissionServiceTest` if present, else follow `NurseTaskServiceTest.java` conventions exactly.
- **Existing exception types:** `org.springframework.security.access.AccessDeniedException` (already thrown elsewhere in this service, e.g. `planDischarge` role gate at line 903) and `com.hms.exception.UnauthorizedException` (used as `UnauthorizedException` at line 108/532 for null hospital context).
- **The PDF endpoint already validates ownership correctly** (`IpdAdmissionController.getDischargeSummaryPdf`, line 153: `if (!ipd.getHospitalId().equals(hospitalId)) throw AccessDenied`). Do not change it. It is the reference pattern to copy.
- **Table names (verified):** IPD table is `ipd_admission` (singular). Discharge table is `discharge_summary`. Patients table is `patients`.

**Run all commands from `backend/`.** A single-test run is `mvn -q -Dtest=IpdAdmissionServiceTest#<method> test`.

---

### Task 1: Fail-closed tenant guard on `planDischarge`

`planDischarge(ipdId, req)` currently loads the IPD and checks only its status — a DOCTOR authenticated for hospital A can plan (and thereby create a `DISCHARGE_PLANNED` transition + discharge summary) for hospital B's admission by passing its id.

**Files:**
- Test: `backend/src/test/java/com/hms/service/IpdAdmissionServiceTest.java` (create if it does not exist)
- Modify: `backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java` (in `planDischarge`, immediately after the `findById` at line 906)

- [ ] **Step 1: Write the failing test**

If `IpdAdmissionServiceTest.java` does not exist, create it with this full contents. If it already exists, add only the `import`s that are missing and the single test method.

```java
package com.hms.service;

import com.hms.entity.DischargeSummary;
import com.hms.entity.IpdAdmission;
import com.hms.repository.DischargeSummaryRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.IpdAdmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpdAdmissionServiceTest {

    @Mock
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Mock
    private DischargeSummaryRepository dischargeSummaryRepository;

    @Mock
    private SecurityContextHelper securityHelper;

    @InjectMocks
    private IpdAdmissionService ipdAdmissionService;

    @Test
    void planDischarge_rejectsCrossTenantAdmission() {
        Long ipdId = 500L;
        // Caller is authenticated for hospital 1, admission belongs to hospital 2.
        when(securityHelper.getCurrentUserRole()).thenReturn("DOCTOR");
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        IpdAdmission foreignAdmission = new IpdAdmission();
        foreignAdmission.setId(ipdId);
        foreignAdmission.setHospitalId(2L);
        foreignAdmission.setStatus("ADMITTED");
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(foreignAdmission));

        com.hms.dto.PlanDischargeRequest req = new com.hms.dto.PlanDischargeRequest();

        assertThatThrownBy(() -> ipdAdmissionService.planDischarge(ipdId, req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Tenant mismatch");

        // The IDOR guard must fire before any discharge summary is persisted.
        verify(dischargeSummaryRepository, never()).save(any(DischargeSummary.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=IpdAdmissionServiceTest#planDischarge_rejectsCrossTenantAdmission test`
Expected: FAIL. The method proceeds past the status check and calls `dischargeSummaryRepository.save(...)` (which, unstubbed, returns null) instead of throwing `AccessDeniedException`, so the `assertThatThrownBy` assertion fails.

- [ ] **Step 3: Write minimal implementation**

In `IpdAdmissionService.planDischarge`, the current opening is:

```java
        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("ADMITTED")) {
            throw new IllegalArgumentException("Can only plan discharge for ADMITTED patients");
        }
```

Insert the tenant guard between the `findById` line and the status check, so tenancy fails closed first:

```java
        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));

        Long currentHospitalId = securityHelper.getCurrentHospitalId();
        if (currentHospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        if (ipd.getHospitalId() == null || !ipd.getHospitalId().equals(currentHospitalId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Tenant mismatch");
        }

        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("ADMITTED")) {
            throw new IllegalArgumentException("Can only plan discharge for ADMITTED patients");
        }
```

`UnauthorizedException` is already imported in this file (used at line 108). If the IDE reports it unresolved, use the fully-qualified `com.hms.exception.UnauthorizedException`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=IpdAdmissionServiceTest#planDischarge_rejectsCrossTenantAdmission test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/hms/service/IpdAdmissionServiceTest.java backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java
git commit -m "fix(security): reject cross-tenant planDischarge (IDOR)"
```

---

### Task 2: Fail-closed tenant guard on `confirmDischarge`

`confirmDischarge(ipdId)` loads the IPD and then does `Long hospitalId = ipd.getHospitalId();` — it trusts the record's own hospital and never compares it to the caller's. A receptionist/admin of hospital A can confirm discharge (finalize billing checks, stop prescriptions, set `DISCHARGED`) on hospital B's admission.

**Files:**
- Test: `backend/src/test/java/com/hms/service/IpdAdmissionServiceTest.java` (add one method + mocks)
- Modify: `backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java` (in `confirmDischarge`, immediately after the `findById` at line 947)

- [ ] **Step 1: Write the failing test**

Add these mock fields to the test class if not already present (from Task 1 you already have `ipdAdmissionRepository` and `securityHelper`; add the settings repo):

```java
    @Mock
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;
```

Add this test method:

```java
    @Test
    void confirmDischarge_rejectsCrossTenantAdmission() {
        Long ipdId = 501L;
        // Caller is authenticated for hospital 1, admission belongs to hospital 2.
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        IpdAdmission foreignAdmission = new IpdAdmission();
        foreignAdmission.setId(ipdId);
        foreignAdmission.setHospitalId(2L);
        foreignAdmission.setStatus("DISCHARGE_PLANNED");
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(foreignAdmission));

        assertThatThrownBy(() -> ipdAdmissionService.confirmDischarge(ipdId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Tenant mismatch");

        // The guard must fire before the hospital-settings lookup and any state change.
        verify(hospitalSettingRepository, never()).findByHospital_Id(any());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=IpdAdmissionServiceTest#confirmDischarge_rejectsCrossTenantAdmission test`
Expected: FAIL. Without the guard, `confirmDischarge` reads `ipd.getHospitalId()` (2L), calls `hospitalSettingRepository.findByHospital_Id(2L)`, and proceeds — no `AccessDeniedException` is thrown, so the assertion fails (and the `never()` verify would also fail).

- [ ] **Step 3: Write minimal implementation**

In `IpdAdmissionService.confirmDischarge`, the current opening is:

```java
    public IpdAdmission confirmDischarge(Long ipdId) {
        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));

        String role = securityHelper.getCurrentUserRole();
        Long hospitalId = ipd.getHospitalId();
```

Insert the tenant guard between the `findById` line and the `String role` line:

```java
    public IpdAdmission confirmDischarge(Long ipdId) {
        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));

        Long currentHospitalId = securityHelper.getCurrentHospitalId();
        if (currentHospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        if (ipd.getHospitalId() == null || !ipd.getHospitalId().equals(currentHospitalId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Tenant mismatch");
        }

        String role = securityHelper.getCurrentUserRole();
        Long hospitalId = ipd.getHospitalId();
```

Leave the existing `Long hospitalId = ipd.getHospitalId();` line as-is; after the guard it is provably equal to `currentHospitalId`, and keeping it avoids touching the many downstream references to `hospitalId` in this method.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=IpdAdmissionServiceTest#confirmDischarge_rejectsCrossTenantAdmission test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/hms/service/IpdAdmissionServiceTest.java backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java
git commit -m "fix(security): reject cross-tenant confirmDischarge (IDOR)"
```

---

### Task 3: Add nullable tenant columns to the `DischargeSummary` entity

Make the discharge summary self-contained so future queries can be tenant-scoped directly and PDFs/exports never rely on a join to reach the hospital. Columns are **nullable and additive** — `ddl-auto=update` creates them automatically at startup; existing rows are backfilled in Task 4.

**Files:**
- Modify: `backend/src/main/java/com/hms/entity/DischargeSummary.java`

- [ ] **Step 1: Add the fields**

Add these three fields to `DischargeSummary`, directly after the existing `ipdAdmissionId` field (line 24). Lombok `@Data` generates the getters/setters.

```java
    @Column(name = "hospital_id")
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "doctor_id")
    private Long doctorId;
```

They are intentionally nullable (no `nullable = false`) so the additive migration is safe on a live DB with existing rows. A later Phase 0 hardening pass may enforce `NOT NULL` after backfill is confirmed everywhere.

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/entity/DischargeSummary.java
git commit -m "feat(discharge): add nullable hospital_id/patient_id/doctor_id to DischargeSummary"
```

---

### Task 4: Populate the new columns on create in `planDischarge`

New discharge summaries must be born tenant-stamped. Set the three columns from the (already tenant-validated) IPD admission when the summary is created.

**Files:**
- Modify: `backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java` (in `planDischarge`, the `new DischargeSummary()` block at lines 911-917)
- Test: `backend/src/test/java/com/hms/service/IpdAdmissionServiceTest.java` (add one method)

- [ ] **Step 1: Write the failing test**

Add this test method to `IpdAdmissionServiceTest`. It asserts the persisted summary carries the admission's tenant/patient/doctor identifiers. Uses an `ArgumentCaptor`.

Add these imports if missing:

```java
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
```

Test method:

```java
    @Test
    void planDischarge_stampsTenantPatientDoctorOnSummary() {
        Long ipdId = 502L;
        when(securityHelper.getCurrentUserRole()).thenReturn("DOCTOR");
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        // getCurrentUserEmail() is used by the audit-log block (inside try/catch); make it lenient.
        lenient().when(securityHelper.getCurrentUserEmail()).thenReturn("doc@hospital.com");

        IpdAdmission admission = new IpdAdmission();
        admission.setId(ipdId);
        admission.setHospitalId(7L);
        admission.setPatientId(42L);
        admission.setDoctorId(9L);
        admission.setStatus("ADMITTED");
        admission.setIpdNumber("IPD-7-001");
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(admission));
        when(dischargeSummaryRepository.save(any(DischargeSummary.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        com.hms.dto.PlanDischargeRequest req = new com.hms.dto.PlanDischargeRequest();
        req.setFinalDiagnosis("Recovered");

        ipdAdmissionService.planDischarge(ipdId, req);

        ArgumentCaptor<DischargeSummary> captor = ArgumentCaptor.forClass(DischargeSummary.class);
        verify(dischargeSummaryRepository).save(captor.capture());
        DischargeSummary saved = captor.getValue();
        assertThat(saved.getHospitalId()).isEqualTo(7L);
        assertThat(saved.getPatientId()).isEqualTo(42L);
        assertThat(saved.getDoctorId()).isEqualTo(9L);
        assertThat(saved.getIpdAdmissionId()).isEqualTo(ipdId);
    }
```

> Note on collaborators: `planDischarge` also saves the admission and calls `auditLogService`/`webSocketHandler`, but those are wrapped in try/catch and are not `@InjectMocks`-required for this assertion. `ipdAdmissionRepository.save(...)` on an unstubbed mock returns null harmlessly; the audit/websocket blocks swallow their own exceptions. If Mockito reports `UnnecessaryStubbingException` for `getCurrentUserEmail`, the `lenient()` wrapper already prevents it.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=IpdAdmissionServiceTest#planDischarge_stampsTenantPatientDoctorOnSummary test`
Expected: FAIL — `saved.getHospitalId()` is null because `planDischarge` does not yet set the new columns.

- [ ] **Step 3: Write minimal implementation**

In `planDischarge`, the current creation block is:

```java
        com.hms.entity.DischargeSummary ds = new com.hms.entity.DischargeSummary();
        ds.setIpdAdmissionId(ipdId);
        ds.setFinalDiagnosis(req.getFinalDiagnosis());
        ds.setTreatmentGiven(req.getTreatmentGiven());
        ds.setDischargeNotes(req.getDischargeNotes());
        ds.setFollowUpDate(req.getFollowUpDate());
        dischargeSummaryRepository.save(ds);
```

Add the three stamps right after `ds.setIpdAdmissionId(ipdId);`:

```java
        com.hms.entity.DischargeSummary ds = new com.hms.entity.DischargeSummary();
        ds.setIpdAdmissionId(ipdId);
        ds.setHospitalId(ipd.getHospitalId());
        ds.setPatientId(ipd.getPatientId());
        ds.setDoctorId(ipd.getDoctorId());
        ds.setFinalDiagnosis(req.getFinalDiagnosis());
        ds.setTreatmentGiven(req.getTreatmentGiven());
        ds.setDischargeNotes(req.getDischargeNotes());
        ds.setFollowUpDate(req.getFollowUpDate());
        dischargeSummaryRepository.save(ds);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=IpdAdmissionServiceTest#planDischarge_stampsTenantPatientDoctorOnSummary test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/hms/service/IpdAdmissionServiceTest.java backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java
git commit -m "feat(discharge): stamp hospital/patient/doctor on new discharge summaries"
```

---

### Task 5: Idempotent backfill migration for existing `discharge_summary` rows

Existing rows created before Task 3/4 have null tenant columns. Backfill them from `ipd_admission` on startup, following the established `DatabaseMigrationRunner` pattern (guarded by a data check, wrapped in try/catch, logs on apply).

**Files:**
- Modify: `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`

- [ ] **Step 1: Register the migration**

In `runMigrations()`, add the call at the end of the method (after `fixRadiologyOrdersMedicalRecordIdColumn();`):

```java
        backfillDischargeSummaryTenantColumns();
```

- [ ] **Step 2: Add the migration method**

Add this method to the class (e.g. after `fixRadiologyOrdersMedicalRecordIdColumn`). It runs only when the new columns exist (created by `ddl-auto=update`) and at least one row still has a null `hospital_id`, so it is idempotent and cheap on subsequent boots.

```java
    /**
     * Backfills discharge_summary.hospital_id / patient_id / doctor_id from the parent
     * ipd_admission for rows created before those columns existed. Additive + idempotent:
     * only touches rows where hospital_id IS NULL, and no-ops once every row is stamped.
     */
    private void backfillDischargeSummaryTenantColumns() {
        try {
            Integer columnExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'hospital_id'",
                Integer.class
            );
            if (columnExists == null || columnExists == 0) {
                return; // ddl-auto has not created the column yet; nothing to backfill
            }

            Integer nullRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM discharge_summary WHERE hospital_id IS NULL",
                Integer.class
            );
            if (nullRows == null || nullRows == 0) {
                return; // already fully backfilled
            }

            int updated = jdbcTemplate.update(
                "UPDATE discharge_summary ds " +
                "JOIN ipd_admission ia ON ia.id = ds.ipd_admission_id " +
                "SET ds.hospital_id = ia.hospital_id, " +
                "    ds.patient_id  = ia.patient_id, " +
                "    ds.doctor_id   = ia.doctor_id " +
                "WHERE ds.hospital_id IS NULL"
            );
            log.info("DB migration applied: backfilled {} discharge_summary tenant column rows", updated);
        } catch (Exception e) {
            log.warn("DB migration skipped (discharge_summary tenant backfill): {}", e.getMessage());
        }
    }
```

> The column names `hospital_id`, `patient_id`, `doctor_id` on `ipd_admission` match the `IpdAdmission` entity fields used in the service (`ipd.getHospitalId()`, `getPatientId()`, `getDoctorId()`). If a column name differs in your DB, the `catch` logs a warning and the app still boots — the backfill is best-effort, correctness at write-time is guaranteed by Task 4.

- [ ] **Step 3: Verify it compiles**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java
git commit -m "chore(db): backfill discharge_summary tenant columns on startup"
```

---

### Task 6: Mirror the schema change in canonical `schema-full.sql`

CLAUDE.md requires `setup/schema-full.sql` to stay the source of truth and mirror the JPA entities.

**Files:**
- Modify: `setup/schema-full.sql` (the `discharge_summary` CREATE TABLE at lines 161-171)

- [ ] **Step 1: Update the DDL**

Replace the current `discharge_summary` table definition:

```sql
CREATE TABLE `discharge_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `discharge_notes` text,
  `final_diagnosis` text,
  `follow_up_date` date DEFAULT NULL,
  `ipd_admission_id` bigint NOT NULL,
  `treatment_given` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_5poa40gpt44a152gibdlfe6sb` (`ipd_admission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

with:

```sql
CREATE TABLE `discharge_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `discharge_notes` text,
  `final_diagnosis` text,
  `follow_up_date` date DEFAULT NULL,
  `ipd_admission_id` bigint NOT NULL,
  `treatment_given` text,
  `hospital_id` bigint DEFAULT NULL,
  `patient_id` bigint DEFAULT NULL,
  `doctor_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_5poa40gpt44a152gibdlfe6sb` (`ipd_admission_id`),
  KEY `idx_discharge_summary_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 2: Provide the ALTER for existing databases**

Confirm this ALTER (equivalent to what `ddl-auto=update` + Task 5 do) is recorded for DBAs applying changes manually — add it as a comment block immediately above the `discharge_summary` CREATE TABLE, or to your migrations changelog if one exists:

```sql
-- Phase 0.1 additive migration (safe on live data):
-- ALTER TABLE `discharge_summary`
--   ADD COLUMN `hospital_id` bigint DEFAULT NULL,
--   ADD COLUMN `patient_id` bigint DEFAULT NULL,
--   ADD COLUMN `doctor_id` bigint DEFAULT NULL,
--   ADD KEY `idx_discharge_summary_hospital` (`hospital_id`);
-- UPDATE `discharge_summary` ds JOIN `ipd_admission` ia ON ia.id = ds.ipd_admission_id
--   SET ds.hospital_id = ia.hospital_id, ds.patient_id = ia.patient_id, ds.doctor_id = ia.doctor_id
--   WHERE ds.hospital_id IS NULL;
```

- [ ] **Step 3: Commit**

```bash
git add setup/schema-full.sql
git commit -m "docs(schema): mirror discharge_summary tenant columns in canonical schema"
```

---

### Task 7: Green-build verification gate

Do-no-harm requires the full backend test suite and the frontend build to stay green before this sub-plan is considered done.

**Files:** none (verification only)

- [ ] **Step 1: Run the full backend test suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, including the two IDOR tests and the stamping test from this plan, and all pre-existing tests still passing.

- [ ] **Step 2: Confirm the frontend build is unaffected**

This sub-plan touches only backend files, so the frontend build must be unchanged. Run from `frontend/`:

Run: `npm run build`
Expected: build succeeds (no changes expected; this is a regression guard).

- [ ] **Step 3: Manual IDOR spot-check checklist (record results in the PR description)**

- [ ] With a DOCTOR JWT for hospital A, `POST /hospital/ipd/{id}/plan-discharge` where `{id}` is an admission of hospital B ⇒ HTTP 4xx / access denied (not 200).
- [ ] With a RECEPTIONIST JWT for hospital A, `POST /hospital/ipd/{id}/confirm-discharge` where `{id}` belongs to hospital B ⇒ access denied.
- [ ] A newly planned discharge summary row has `hospital_id`, `patient_id`, `doctor_id` populated.
- [ ] After restart, any legacy discharge_summary rows have their tenant columns backfilled (log line: "backfilled N discharge_summary tenant column rows").

---

## Self-Review

**1. Spec coverage** (against roadmap §3 Phase 0 "DischargeSummary isolation fix — add hospital_id/patient_id/doctor_id + ownership checks + cross-tenant test — IDOR fix"):
- Ownership checks ⇒ Tasks 1 (planDischarge) & 2 (confirmDischarge). ✅
- Cross-tenant tests ⇒ Tasks 1 & 2 each ship a failing-first cross-tenant test. ✅
- Add hospital_id/patient_id/doctor_id ⇒ Task 3 (entity) + Task 4 (populate on write) + Task 5 (backfill legacy) + Task 6 (canonical schema). ✅
- Additive/reversible migration + no broken endpoint contract ⇒ all columns nullable, no signature/route changes, `ddl-auto=update` handles column creation, backfill is idempotent. ✅
- Green-build gate ⇒ Task 7. ✅

**2. Placeholder scan:** No TBD/TODO/"handle edge cases" — every code step shows complete code and exact commands. ✅

**3. Type consistency:** `securityHelper` (`SecurityContextHelper`), `ipdAdmissionRepository`, `dischargeSummaryRepository`, `hospitalSettingRepository.findByHospital_Id`, `AccessDeniedException`, `UnauthorizedException`, and entity setters `setHospitalId/setPatientId/setDoctorId` are consistent across all tasks and match the verified source. `PlanDischargeRequest` is the real request DTO used by `planDischarge`. `IpdAdmission` getters `getHospitalId/getPatientId/getDoctorId` are used consistently. ✅

**Scope note:** `addIpdFollowup` (line 526) has the same "loads IPD by id" shape but gets its own guard in a later Phase 0 clinical-documentation sub-plan; it is out of scope here to keep this plan focused on the discharge flow named in the roadmap.
