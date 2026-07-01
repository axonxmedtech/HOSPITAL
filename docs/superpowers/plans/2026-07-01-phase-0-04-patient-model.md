# Phase 0.4 — Patient Model Additions & Record Merge Implementation Plan

**Goal:** Bring the patient record up to NABH identity requirements, expand fields on frontend/backend, and implement duplicate/temporary patient record merging.

**Architecture:**
1. **Entity Changes** — In `Patient.java`, add the following nullable columns (auto-created by `ddl-auto=update`):
   - `date_of_birth` (DATE / `java.time.LocalDate`)
   - `guardian_name` (VARCHAR 100)
   - `guardian_relationship` (VARCHAR 50)
   - `preferred_language` (VARCHAR 50)
   - `blood_group` (VARCHAR 10)
   - `uhid` (VARCHAR 50)
   - `is_temporary` (BOOLEAN, default false)
   - `is_unknown` (BOOLEAN, default false)
   - `is_merged` (BOOLEAN, default false)
   - `merged_to_id` (BIGINT)
2. **Service & Controller Changes** — Add `mergePatients(Long survivorId, Long loserId)` to `PatientService.java`. It validates that both patients exist and belong to the active hospital (tenant-isolated), repoints all child foreign keys to the survivor, sets the loser's status to merged/inactive, and audit-logs the merge. Expose `POST /hospital/patients/merge` in `PatientController.java`.
3. **Database Migrations** — Update `DatabaseMigrationRunner.java` with a startup migration to:
   - Add new patient columns as nullable.
   - Guarded backfill: generate `uhid = CONCAT('UHID-', hospital_id, '-', id)` for existing patients.
   - Synchronize `age` and `date_of_birth` in both directions for legacy records.
4. **Frontend Form** — Update `PatientModal.jsx` to render the new optional fields under Zone B ("Add more details"), preserving the required 4-field progressive-disclosure UX.
5. **Canonical Schema** — Mirror changes in `setup/schema-full.sql`.

**Tech Stack:** Spring Boot, Spring Data JPA, Lombok, React/Vite, JUnit 5 + Mockito + AssertJ.

---

## Target Files & Packages
- `backend/src/main/java/com/hms/entity/Patient.java`
- `backend/src/main/java/com/hms/repository/PatientRepository.java`
- `backend/src/main/java/com/hms/service/hospital/PatientService.java`
- `backend/src/main/java/com/hms/controller/hospital/PatientController.java`
- `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
- `setup/schema-full.sql`
- `frontend/src/components/PatientModal.jsx`

---

## Tasks

### Task 1: Update `Patient` entity & Repository

Add the new fields to `Patient.java` and repository methods.

- [ ] **Step 1: Modify `Patient.java`**
  Add the new fields inside the `Patient` class:
  ```java
  @Column(name = "date_of_birth")
  private java.time.LocalDate dateOfBirth;

  @Column(name = "guardian_name", length = 100)
  private String guardianName;

  @Column(name = "guardian_relationship", length = 50)
  private String guardianRelationship;

  @Column(name = "preferred_language", length = 50)
  private String preferredLanguage;

  @Column(name = "blood_group", length = 10)
  private String bloodGroup;

  @Column(name = "uhid", length = 50)
  private String uhid;

  @Column(name = "is_temporary", nullable = false)
  private Boolean isTemporary = false;

  @Column(name = "is_unknown", nullable = false)
  private Boolean isUnknown = false;

  @Column(name = "is_merged", nullable = false)
  private Boolean isMerged = false;

  @Column(name = "merged_to_id")
  private Long mergedToId;
  ```
  Ensure when saving/updating, if `dateOfBirth` is provided, `age` is derived if age is null (and vice-versa).

- [ ] **Step 2: Verify compile**
  Run: `mvn -q -DskipTests compile`
  Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**
  Commit message:
  ```
  feat(patient): add NABH identity and merge columns to Patient entity

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 2: Implement Record Merge Service and Controller

- [ ] **Step 1: Write TDD tests in `PatientServiceTest` or a new `PatientMergeTest.java`**
  Write tests in `backend/src/test/java/com/hms/service/PatientServiceTest.java` (create if needed, or add to existing tests) proving:
  1. Merging re-points child FKs (e.g., appointments, billing, IPD admissions) to the survivor patient.
  2. Loser patient is marked merged/inactive.
  3. Merging cross-tenant patients throws `AccessDeniedException` / `UnauthorizedException`.

- [ ] **Step 2: Implement `mergePatients` in `PatientService.java`**
  ```java
  @Transactional
  public void mergePatients(Long survivorId, Long loserId) {
      Long hospitalId = securityHelper.getCurrentHospitalId();
      if (hospitalId == null) {
          throw new UnauthorizedException("Hospital ID not found in context");
      }

      Patient survivor = patientRepository.findById(survivorId)
              .filter(p -> p.getHospitalId().equals(hospitalId))
              .orElseThrow(() -> new RuntimeException("Survivor patient not found"));

      Patient loser = patientRepository.findById(loserId)
              .filter(p -> p.getHospitalId().equals(hospitalId))
              .orElseThrow(() -> new RuntimeException("Duplicate patient not found"));

      logger.info("Merging patient {} into survivor {} for hospital {}", loserId, survivorId, hospitalId);

      // Repoint child foreign keys
      jdbcTemplate.update("UPDATE appointments SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE billing SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE ipd_admission SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE lab_orders SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE lab_results SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE medical_records SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE patient_allergies SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE pharmacy_sales SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE radiology_orders SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE radiology_results SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE whatsapp_message_log SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE cdss_alert_log SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);
      jdbcTemplate.update("UPDATE discharge_summary SET patient_id = ? WHERE patient_id = ?", survivorId, loserId);

      // Mark loser merged and soft-delete
      loser.setIsMerged(true);
      loser.setMergedToId(survivorId);
      loser.setIsActive(false);
      patientRepository.save(loser);

      // Audit Log
      try {
          auditLogService.logAction(
                  "PATIENT_MERGED",
                  "Patient ID " + loserId + " was merged into Patient ID " + survivorId,
                  securityHelper.getCurrentUserEmail(),
                  hospitalId,
                  "PATIENT",
                  survivor.getPublicId(),
                  "Duplicate/Temporary record merge"
          );
      } catch (Exception e) {
          logger.warn("Failed to create audit log for patient merge", e);
      }
  }
  ```

  Also, update the `addPatient` method in `PatientService` to assign default UHID if not provided:
  ```java
  if (savedPatient.getUhid() == null) {
      savedPatient.setUhid("UHID-" + savedPatient.getHospitalId() + "-" + savedPatient.getId());
      savedPatient = patientRepository.save(savedPatient);
  }
  ```

- [ ] **Step 3: Add `POST /hospital/patients/merge` to `PatientController.java`**
  ```java
  @PostMapping("/merge")
  @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
  public ResponseEntity<?> mergePatients(@RequestBody java.util.Map<String, Long> request) {
      Long survivorId = request.get("survivorId");
      Long loserId = request.get("loserId");
      if (survivorId == null || loserId == null) {
          return ResponseEntity.badRequest().body("Both survivorId and loserId are required");
      }
      if (survivorId.equals(loserId)) {
          return ResponseEntity.badRequest().body("Cannot merge a patient into themselves");
      }
      patientService.mergePatients(survivorId, loserId);
      return ResponseEntity.ok("Patients merged successfully");
  }
  ```

- [ ] **Step 4: Verify test suite runs and passes**
  Run: `mvn -q test`

- [ ] **Step 5: Commit**
  Commit message:
  ```
  feat(patient): implement mergePatients logic in service and controller

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 3: Idempotent Database Migration

- [ ] **Step 1: Register in `DatabaseMigrationRunner.java`**
  Add after `decoupleNurseTasksSchema();`:
  ```java
  migratePatientModelSchema();
  ```

- [ ] **Step 2: Implement `migratePatientModelSchema()`**
  ```java
  private void migratePatientModelSchema() {
      try {
          // 1. Generate UHID for existing patients if NULL
          Integer countNullUhid = jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM patients WHERE uhid IS NULL",
              Integer.class
          );
          if (countNullUhid != null && countNullUhid > 0) {
              int updated = jdbcTemplate.update(
                  "UPDATE patients SET uhid = CONCAT('UHID-', hospital_id, '-', id) WHERE uhid IS NULL"
              );
              log.info("DB migration applied: generated {} patient UHIDs", updated);
          }

          // 2. Synchronize DOB -> Age
          int dobToAge = jdbcTemplate.update(
              "UPDATE patients SET age = TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) " +
              "WHERE age IS NULL AND date_of_birth IS NOT NULL"
          );
          if (dobToAge > 0) {
              log.info("DB migration applied: synced date_of_birth -> age for {} patients", dobToAge);
          }

          // 3. Synchronize Age -> DOB (estimate birth date using Jan 1 of estimated birth year)
          int ageToDob = jdbcTemplate.update(
              "UPDATE patients SET date_of_birth = DATE_SUB(CONCAT(YEAR(CURDATE()) - age, '-01-01'), INTERVAL 0 DAY) " +
              "WHERE date_of_birth IS NULL AND age IS NOT NULL"
          );
          if (ageToDob > 0) {
              log.info("DB migration applied: synced age -> date_of_birth for {} patients", ageToDob);
          }
      } catch (Exception e) {
          log.warn("DB migration skipped (migratePatientModelSchema): {}", e.getMessage());
      }
  }
  ```

- [ ] **Step 3: Commit**
  Commit message:
  ```
  chore(db): add migratePatientModelSchema for DOB sync and UHID backfill

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 4: Mirror in `setup/schema-full.sql`

- [ ] **Step 1: Modify `setup/schema-full.sql`**
  Update `CREATE TABLE IF NOT EXISTS patients`:
  - Add `date_of_birth`, `guardian_name`, `guardian_relationship`, `preferred_language`, `blood_group`, `uhid`, `is_temporary`, `is_unknown`, `is_merged`, `merged_to_id`.
  - Document ALTER comments.

- [ ] **Step 2: Commit**
  Commit message:
  ```
  docs(schema): mirror patient schema changes in setup/schema-full.sql

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 5: Frontend Form Fields

- [ ] **Step 1: Modify `PatientModal.jsx`**
  Under Zone B (`showMoreDetails`), add inputs for the new identity fields. Ensure they bind to the correct fields in `formData`.
  Keep required validations only on the 4 canonical fields (progressive disclosure).

- [ ] **Step 2: Verify frontend compile**
  Run `npm run build` in `frontend/`.

- [ ] **Step 3: Commit**
  Commit message:
  ```
  feat(frontend): add optional identity fields to PatientModal registration

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

## Verification Plan

### Automated Tests
- Run `mvn -q test` in `backend/` to verify tests pass.
- Run `npm run build` in `frontend/` to verify build compiles.

### Manual Verification
- Register a patient on the frontend, expand "Add more details", fill in the new fields, and verify they persist in the database.
- Call the merge endpoint `/hospital/patients/merge` and verify child records are updated and the loser is marked inactive.
