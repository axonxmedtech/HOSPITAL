# Phase 0.2 — VitalSigns Structured BP + Expanded Observations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragile free-text `VitalSigns.blood_pressure` string with structured `bp_systolic`/`bp_diastolic` integers (plus `pain_score`, `weight`, `oxygen_support`, `remarks`), migrate the NEWS2 EWS calculator to score off the integer column, and backfill legacy rows — all without breaking the existing string-based write path, the frontend, or old rows.

**Architecture:** Additive, dual-write, fallback-read. (1) Add six nullable columns to `VitalSigns`; keep `blood_pressure` for backward compatibility and display. (2) `recordVitals` accepts either structured `bpSystolic`/`bpDiastolic` OR a legacy `bloodPressure` string, and **writes both** the integer columns and a normalized `blood_pressure` string, so every reader (EWS, frontend table) keeps working. It also starts persisting the already-modeled `respiratoryRate` (currently silently dropped — a latent EWS bug) plus the new fields. (3) `calculateEws` prefers `bp_systolic` and falls back to parsing `blood_pressure` for legacy rows. (4) An idempotent `DatabaseMigrationRunner` patch backfills `bp_systolic`/`bp_diastolic` from existing well-formed `blood_pressure` strings. (5) `schema-full.sql` mirrors the columns.

**Tech Stack:** Spring Boot, Spring Data JPA (`ddl-auto=update`), Lombok, JUnit 5 + Mockito + AssertJ, MySQL. Startup migrations via `com.hms.config.DatabaseMigrationRunner`.

---

## Context the engineer needs before starting

- **This is Phase 0.2**, on branch `phase-0-01-discharge-isolation` (Phase 0 work accumulates on this branch). Do not start other Phase 0 items here.
- **Verified current state:**
  - `backend/src/main/java/com/hms/entity/VitalSigns.java` fields: `id`, `ipdAdmissionId`, `hospitalId`, `bloodPressure` (`@Column(name="blood_pressure", length=20)` String), `pulse` (Integer), `temperature` (BigDecimal, precision 4 scale 1), `spo2` (Integer), `respiratoryRate` (`@Column(name="respiratory_rate")` Integer), `recordedBy` (Long), `recordedByName` (String), `recordedAt` (LocalDateTime). Lombok `@Data`.
  - **Only write site:** `NurseAssessmentService.recordVitals(Long admissionId, Map<String,Object> data)` at `backend/src/main/java/com/hms/service/hospital/NurseAssessmentService.java:57`. It currently sets `ipdAdmissionId`, `hospitalId`, `bloodPressure` (from `data.get("bloodPressure")`), `pulse`, `temperature`, `spo2`, `recordedByName`, `recordedAt` — and **does NOT set `respiratoryRate`** even though the column and the frontend field exist. Helper methods `toInt(Object)` and `toBigDecimal(Object)` already exist in that class.
  - **Only EWS read site:** `CdssEvaluationService.calculateEws(Long)` at `backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java:143`. Line 159 sets `result.setBloodPressure(v.getBloodPressure())`; line 165 computes `scoreSbp(parseSystolic(v.getBloodPressure()))`. `parseSystolic(String)` (line 276) splits on `/` or whitespace and parses the first token. `scoreSbp(Integer)` (line 294): `null→0; ≤90→3; ≤100→2; ≤110→1; ≤219→0; else 3`.
  - `EwsResultDTO` (`backend/src/main/java/com/hms/dto/EwsResultDTO.java`) has a `String bloodPressure` field (keep it).
  - Frontend sends `bloodPressure` as a single text field (`frontend/src/components/nurse/VitalsForm.jsx`) and displays `v.bloodPressure` (`PatientClinicalRecord.jsx`). **No frontend change in this sub-plan** — the backend keeps `blood_pressure` populated. A structured BP input is a later UX task.
  - Migration runner pattern: `information_schema`-guarded, idempotent, `try/catch` per patch, `log.info` on apply. `runMigrations()` currently ends with `backfillDischargeSummaryTenantColumns();` (added in Phase 0.1).
  - `vital_signs` table exists in `setup/schema-full.sql`.
- **Table name:** `vital_signs`.
- Run all Maven commands from `backend/`.

---

### Task 1: Add nullable structured/observation columns to `VitalSigns`

**Files:**
- Modify: `backend/src/main/java/com/hms/entity/VitalSigns.java`

- [ ] **Step 1: Add the fields**

Add these fields after the existing `bloodPressure` field (keep `bloodPressure` — do not remove it). Lombok `@Data` generates accessors.

```java
    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "weight", precision = 5, scale = 2)
    private java.math.BigDecimal weight;

    @Column(name = "oxygen_support", length = 50)
    private String oxygenSupport;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;
```

All nullable (no `nullable=false`) — additive and safe on live data; `ddl-auto=update` creates them at startup.

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/entity/VitalSigns.java
git commit -m "feat(vitals): add structured bp_systolic/bp_diastolic + pain/weight/o2/remarks to VitalSigns

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: EWS scores off `bp_systolic` with legacy string fallback

`calculateEws` must prefer the structured integer and only fall back to parsing the string for legacy rows. This preserves existing behavior for old rows while making new rows precise.

**Files:**
- Test: `backend/src/test/java/com/hms/service/CdssEvaluationServiceTest.java` (add two methods)
- Modify: `backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java` (`calculateEws` lines 159 & 165, plus a small helper)

- [ ] **Step 1: Write the failing tests**

Add these two tests to `CdssEvaluationServiceTest` (it already imports `VitalSigns`, `BigDecimal`, `List`, JUnit assertions, Mockito, and has `@Mock VitalSignsRepository vitalSignsRepo`, `ADMISSION_ID`, and the `service` under test):

```java
    @Test
    void calculateEws_prefersStructuredSystolicOverLegacyString() {
        VitalSigns v = new VitalSigns();
        // Structured says hypotensive (85 -> SBP score 3); legacy string says normal (120).
        v.setBpSystolic(85);
        v.setBpDiastolic(50);
        v.setBloodPressure("120/80");
        v.setPulse(75);                       // score 0
        v.setTemperature(new BigDecimal("36.8")); // score 0
        v.setSpo2(98);                        // score 0
        v.setRespiratoryRate(16);             // score 0

        when(vitalSignsRepo.findByIpdAdmissionIdOrderByRecordedAtDesc(ADMISSION_ID))
                .thenReturn(List.of(v));

        EwsResultDTO result = service.calculateEws(ADMISSION_ID);

        // If EWS used the structured 85 -> sbpScore 3 -> total 3 -> MEDIUM.
        // If it (wrongly) used the string 120 -> sbpScore 0 -> NORMAL.
        assertEquals(3, result.getSbpScore());
        assertEquals("MEDIUM", result.getSeverity());
    }

    @Test
    void calculateEws_fallsBackToLegacyStringWhenSystolicNull() {
        VitalSigns v = new VitalSigns();
        v.setBpSystolic(null);                // legacy row: no structured value
        v.setBloodPressure("85/50");          // parse -> 85 -> SBP score 3
        v.setPulse(75);
        v.setTemperature(new BigDecimal("36.8"));
        v.setSpo2(98);
        v.setRespiratoryRate(16);

        when(vitalSignsRepo.findByIpdAdmissionIdOrderByRecordedAtDesc(ADMISSION_ID))
                .thenReturn(List.of(v));

        EwsResultDTO result = service.calculateEws(ADMISSION_ID);

        assertEquals(3, result.getSbpScore());
        assertEquals("MEDIUM", result.getSeverity());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=CdssEvaluationServiceTest#calculateEws_prefersStructuredSystolicOverLegacyString+calculateEws_fallsBackToLegacyStringWhenSystolicNull test`
Expected: the *prefers-structured* test FAILS (current code parses the string `120` → sbpScore 0 → NORMAL). The *fallback* test likely PASSES already (still uses the string) — that is fine; it guards the fallback path stays working after the change.

- [ ] **Step 3: Write minimal implementation**

In `CdssEvaluationService.calculateEws`, change the two lines that consume BP. Current:

```java
        result.setBloodPressure(v.getBloodPressure());
```
```java
        int sbpScore   = scoreSbp(parseSystolic(v.getBloodPressure()));
```

Replace the display line with a formatted value that prefers the structured columns, and the score line to prefer the structured systolic:

```java
        result.setBloodPressure(formatBloodPressure(v));
```
```java
        int sbpScore   = scoreSbp(systolicFor(v));
```

Add these two private helpers to the class (e.g. directly below `parseSystolic`):

```java
    /** Prefer the structured systolic; fall back to parsing the legacy blood_pressure string. */
    private Integer systolicFor(VitalSigns v) {
        if (v.getBpSystolic() != null) return v.getBpSystolic();
        return parseSystolic(v.getBloodPressure());
    }

    /** Display string: "sys/dia" from structured columns when present, else the legacy string. */
    private String formatBloodPressure(VitalSigns v) {
        if (v.getBpSystolic() != null && v.getBpDiastolic() != null) {
            return v.getBpSystolic() + "/" + v.getBpDiastolic();
        }
        if (v.getBpSystolic() != null) return String.valueOf(v.getBpSystolic());
        return v.getBloodPressure();
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=CdssEvaluationServiceTest test`
Expected: PASS — the two new tests plus the pre-existing `calculateEws_normalVitals_*`, `calculateEws_abnormalVitals_*`, `calculateEws_noVitals_*` (which use the string path and still pass via fallback).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/hms/service/CdssEvaluationServiceTest.java backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java
git commit -m "feat(cdss): score EWS from structured bp_systolic with legacy string fallback

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: `recordVitals` dual-writes structured BP + persists new fields

`recordVitals` must accept either a structured pair (`bpSystolic`/`bpDiastolic`) or the legacy `bloodPressure` string, and always populate both representations. It must also start persisting `respiratoryRate` (currently dropped) and the new `painScore`/`weight`/`oxygenSupport`/`remarks`.

**Files:**
- Test: `backend/src/test/java/com/hms/service/NurseAssessmentServiceTest.java` (create)
- Modify: `backend/src/main/java/com/hms/service/hospital/NurseAssessmentService.java` (`recordVitals` at line 57 + a private BP-parse helper)

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/hms/service/NurseAssessmentServiceTest.java`:

```java
package com.hms.service;

import com.hms.entity.VitalSigns;
import com.hms.repository.NurseAssessmentRepository;
import com.hms.repository.VitalSignsRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.MrdService;
import com.hms.service.hospital.NurseAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NurseAssessmentServiceTest {

    @Mock private NurseAssessmentRepository assessmentRepository;
    @Mock private VitalSignsRepository vitalsRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private MrdService mrdService;

    @InjectMocks
    private NurseAssessmentService service;

    private void stubCommon() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        lenient().when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(vitalsRepository.save(any(VitalSigns.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordVitals_parsesLegacyBloodPressureStringIntoStructuredColumns() {
        stubCommon();
        Map<String, Object> data = new HashMap<>();
        data.put("bloodPressure", "110/70");
        data.put("pulse", 80);
        data.put("respiratoryRate", 18);

        VitalSigns saved = service.recordVitals(10L, data);

        assertThat(saved.getBpSystolic()).isEqualTo(110);
        assertThat(saved.getBpDiastolic()).isEqualTo(70);
        assertThat(saved.getBloodPressure()).isEqualTo("110/70");
        assertThat(saved.getRespiratoryRate()).isEqualTo(18);
    }

    @Test
    void recordVitals_acceptsStructuredBpAndBackfillsLegacyString() {
        stubCommon();
        Map<String, Object> data = new HashMap<>();
        data.put("bpSystolic", 120);
        data.put("bpDiastolic", 80);
        data.put("painScore", 3);
        data.put("weight", "72.5");
        data.put("oxygenSupport", "ROOM_AIR");
        data.put("remarks", "stable");

        VitalSigns saved = service.recordVitals(10L, data);

        assertThat(saved.getBpSystolic()).isEqualTo(120);
        assertThat(saved.getBpDiastolic()).isEqualTo(80);
        assertThat(saved.getBloodPressure()).isEqualTo("120/80");
        assertThat(saved.getPainScore()).isEqualTo(3);
        assertThat(saved.getWeight()).isEqualByComparingTo(new BigDecimal("72.5"));
        assertThat(saved.getOxygenSupport()).isEqualTo("ROOM_AIR");
        assertThat(saved.getRemarks()).isEqualTo("stable");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=NurseAssessmentServiceTest test`
Expected: FAIL — `getBpSystolic()` is null (recordVitals does not populate the structured columns yet; `respiratoryRate`, `painScore`, etc. are not set).

- [ ] **Step 3: Write minimal implementation**

In `NurseAssessmentService.recordVitals`, the current body (lines 62-71) is:

```java
        VitalSigns v = new VitalSigns();
        v.setIpdAdmissionId(admissionId);
        v.setHospitalId(hospitalId);
        v.setBloodPressure((String) data.get("bloodPressure"));
        v.setPulse(toInt(data.get("pulse")));
        v.setTemperature(toBigDecimal(data.get("temperature")));
        v.setSpo2(toInt(data.get("spo2")));
        v.setRecordedByName(securityHelper.getCurrentUserEmail());
        v.setRecordedAt(LocalDateTime.now());
        return vitalsRepository.save(v);
```

Replace it with:

```java
        VitalSigns v = new VitalSigns();
        v.setIpdAdmissionId(admissionId);
        v.setHospitalId(hospitalId);

        // Structured BP: accept explicit systolic/diastolic, else parse the legacy string.
        Integer systolic = toInt(data.get("bpSystolic"));
        Integer diastolic = toInt(data.get("bpDiastolic"));
        String legacyBp = (String) data.get("bloodPressure");
        if ((systolic == null || diastolic == null) && legacyBp != null) {
            int[] parsed = parseBloodPressure(legacyBp);
            if (systolic == null) systolic = parsed[0] == -1 ? null : parsed[0];
            if (diastolic == null) diastolic = parsed[1] == -1 ? null : parsed[1];
        }
        v.setBpSystolic(systolic);
        v.setBpDiastolic(diastolic);
        // Keep the legacy string populated (normalize from structured when only structured was sent).
        if (legacyBp != null && !legacyBp.isBlank()) {
            v.setBloodPressure(legacyBp);
        } else if (systolic != null && diastolic != null) {
            v.setBloodPressure(systolic + "/" + diastolic);
        } else if (systolic != null) {
            v.setBloodPressure(String.valueOf(systolic));
        }

        v.setPulse(toInt(data.get("pulse")));
        v.setTemperature(toBigDecimal(data.get("temperature")));
        v.setSpo2(toInt(data.get("spo2")));
        v.setRespiratoryRate(toInt(data.get("respiratoryRate")));
        v.setPainScore(toInt(data.get("painScore")));
        v.setWeight(toBigDecimal(data.get("weight")));
        v.setOxygenSupport((String) data.get("oxygenSupport"));
        v.setRemarks((String) data.get("remarks"));
        v.setRecordedByName(securityHelper.getCurrentUserEmail());
        v.setRecordedAt(LocalDateTime.now());
        return vitalsRepository.save(v);
```

Add this private helper to the class (near `toInt`/`toBigDecimal`):

```java
    /** Parses "120/80" (or "120 80") into [systolic, diastolic]; -1 for a token that is missing or non-numeric. */
    private int[] parseBloodPressure(String bp) {
        int[] out = { -1, -1 };
        if (bp == null || bp.isBlank()) return out;
        String[] parts = bp.trim().split("[/\\s]+");
        try { if (parts.length >= 1) out[0] = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
        try { if (parts.length >= 2) out[1] = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
        return out;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=NurseAssessmentServiceTest test`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/hms/service/NurseAssessmentServiceTest.java backend/src/main/java/com/hms/service/hospital/NurseAssessmentService.java
git commit -m "feat(vitals): dual-write structured BP and persist RR/pain/weight/o2/remarks in recordVitals

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Idempotent backfill for legacy `vital_signs` rows + schema mirror

Backfill `bp_systolic`/`bp_diastolic` from existing well-formed `blood_pressure` strings, and mirror the columns in the canonical schema.

**Files:**
- Modify: `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
- Modify: `setup/schema-full.sql`

- [ ] **Step 1: Register the migration**

In `runMigrations()`, add after `backfillDischargeSummaryTenantColumns();`:

```java
        backfillVitalSignsStructuredBp();
```

- [ ] **Step 2: Add the migration method**

Only runs when the column exists and there are legacy rows with a well-formed `NNN/NNN` string but no structured systolic. `SUBSTRING_INDEX` splits the string; the `REGEXP` guard ensures we only touch numeric `n/n` values, so garbage strings are left untouched.

```java
    /**
     * Backfills vital_signs.bp_systolic / bp_diastolic from a well-formed legacy
     * blood_pressure string (e.g. "120/80"). Additive + idempotent: only rows where
     * bp_systolic IS NULL and blood_pressure matches a numeric "n/n" pattern.
     */
    private void backfillVitalSignsStructuredBp() {
        try {
            Integer columnExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vital_signs' AND COLUMN_NAME = 'bp_systolic'",
                Integer.class
            );
            if (columnExists == null || columnExists == 0) {
                return; // ddl-auto has not created the column yet
            }

            Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vital_signs " +
                "WHERE bp_systolic IS NULL AND blood_pressure REGEXP '^[0-9]+[/ ][0-9]+$'",
                Integer.class
            );
            if (pending == null || pending == 0) {
                return; // nothing to backfill
            }

            int updated = jdbcTemplate.update(
                "UPDATE vital_signs " +
                "SET bp_systolic  = CAST(SUBSTRING_INDEX(REPLACE(blood_pressure, ' ', '/'), '/', 1) AS UNSIGNED), " +
                "    bp_diastolic = CAST(SUBSTRING_INDEX(REPLACE(blood_pressure, ' ', '/'), '/', -1) AS UNSIGNED) " +
                "WHERE bp_systolic IS NULL AND blood_pressure REGEXP '^[0-9]+[/ ][0-9]+$'"
            );
            log.info("DB migration applied: backfilled {} vital_signs structured BP rows", updated);
        } catch (Exception e) {
            log.warn("DB migration skipped (vital_signs structured BP backfill): {}", e.getMessage());
        }
    }
```

- [ ] **Step 3: Compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Mirror in `schema-full.sql`**

Find the `vital_signs` CREATE TABLE block in `setup/schema-full.sql`. Add these six column lines after the existing `blood_pressure` column line (keep `blood_pressure`), and add a documenting comment block immediately above the block's `DROP TABLE IF EXISTS \`vital_signs\`;` (or above its CREATE TABLE):

Columns to add inside the CREATE TABLE:
```sql
  `bp_systolic` int DEFAULT NULL,
  `bp_diastolic` int DEFAULT NULL,
  `pain_score` int DEFAULT NULL,
  `weight` decimal(5,2) DEFAULT NULL,
  `oxygen_support` varchar(50) DEFAULT NULL,
  `remarks` text,
```

Comment block:
```sql
-- Phase 0.2 additive migration (safe on live data):
-- ALTER TABLE `vital_signs`
--   ADD COLUMN `bp_systolic` int DEFAULT NULL,
--   ADD COLUMN `bp_diastolic` int DEFAULT NULL,
--   ADD COLUMN `pain_score` int DEFAULT NULL,
--   ADD COLUMN `weight` decimal(5,2) DEFAULT NULL,
--   ADD COLUMN `oxygen_support` varchar(50) DEFAULT NULL,
--   ADD COLUMN `remarks` text;
-- UPDATE `vital_signs`
--   SET bp_systolic  = CAST(SUBSTRING_INDEX(REPLACE(blood_pressure,' ','/'),'/',1) AS UNSIGNED),
--       bp_diastolic = CAST(SUBSTRING_INDEX(REPLACE(blood_pressure,' ','/'),'/',-1) AS UNSIGNED)
--   WHERE bp_systolic IS NULL AND blood_pressure REGEXP '^[0-9]+[/ ][0-9]+$';
```

If the `vital_signs` table does not appear in `schema-full.sql`, note that in your report and skip only the SQL edit (the entity + runner still stand); do not fabricate a table definition.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java setup/schema-full.sql
git commit -m "chore(db): backfill vital_signs structured BP + mirror canonical schema

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Green-build verification gate

**Files:** none (verification only)

- [ ] **Step 1: Full backend suite**

Run: `mvn -q test`
Expected: exit 0 (all suites green, including `CdssEvaluationServiceTest` and the new `NurseAssessmentServiceTest`).

- [ ] **Step 2: Frontend build regression guard**

This sub-plan touches no frontend files. From `frontend/`:
Run: `npm run build`
Expected: succeeds (unchanged).

- [ ] **Step 3: Manual spot-check checklist (record in PR description)**

- [ ] `POST` vitals with legacy `{"bloodPressure":"130/85"}` ⇒ stored row has `bp_systolic=130`, `bp_diastolic=85`, `blood_pressure="130/85"`.
- [ ] `POST` vitals with `{"bpSystolic":130,"bpDiastolic":85}` ⇒ same row shape, `blood_pressure="130/85"`.
- [ ] `POST` vitals with `respiratoryRate` now persists (previously dropped) and EWS reflects it.
- [ ] EWS for a patient with a structured hypotensive reading scores SBP off the integer, not the string.
- [ ] After restart, legacy `vital_signs` rows with `"n/n"` strings get `bp_systolic`/`bp_diastolic` backfilled (log: "backfilled N vital_signs structured BP rows"); malformed strings are left untouched.

---

## Self-Review

**1. Spec coverage** (roadmap §3 Phase 0 "VitalSigns — split blood_pressure String → bp_systolic/bp_diastolic INT, add pain_score/weight/oxygen_support/remarks, migrate calculateEws off parseSystolic"):
- Split into `bp_systolic`/`bp_diastolic` INT ⇒ Task 1 (columns) + Task 3 (dual-write) + Task 4 (backfill). ✅
- Add `pain_score`/`weight`/`oxygen_support`/`remarks` ⇒ Task 1 (columns) + Task 3 (persisted). ✅
- Migrate `calculateEws` off `parseSystolic` ⇒ Task 2 (prefers `bp_systolic`, `parseSystolic` retained only as legacy fallback). ✅
- Do-no-harm: `blood_pressure` retained + dual-written; frontend untouched and still fed; nullable additive columns; idempotent guarded backfill; legacy EWS rows still scored via fallback (proven by a test); latent dropped-`respiratoryRate` bug fixed. ✅
- Green-build gate ⇒ Task 5. ✅

**2. Placeholder scan:** No TBD/TODO/vague steps — every code step is complete, with exact commands and expected results. The only conditional ("if `vital_signs` not in schema-full.sql") has an explicit, safe instruction. ✅

**3. Type consistency:** Accessors `getBpSystolic/setBpSystolic`, `getBpDiastolic/setBpDiastolic`, `getPainScore/setPainScore`, `getWeight/setWeight` (BigDecimal), `getOxygenSupport/setOxygenSupport`, `getRemarks/setRemarks` are used consistently across Tasks 1-3. `systolicFor`/`formatBloodPressure` (CdssEvaluationService) and `parseBloodPressure` (NurseAssessmentService) are each defined in the file that uses them. `toInt`/`toBigDecimal` are pre-existing helpers in NurseAssessmentService. `EwsResultDTO.bloodPressure` remains a String. ✅

**Scope notes:** (a) `NurseAssessment` (the initial-assessment entity) also has a `bloodPressure` string; it is a distinct entity and out of scope here — this sub-plan only touches the recurring `VitalSigns` observation. (b) A structured two-field BP input in the nurse UI is deferred to a later UX task; the backend accepts both shapes so that change is non-breaking when it lands.
