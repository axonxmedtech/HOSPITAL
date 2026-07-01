# Phase 0.3 — NurseTask Decoupling Implementation Plan

**Goal:** Decouple nurse tasks (`NurseTask`) from doctor orders (`DoctorOrder`) so tasks can originate from risk protocols and nursing judgment, and capture why a task was missed.

**Architecture:** 
1. **Entity Changes** — In `NurseTask.java`, make `doctorOrderId` nullable. Add `source` (enum/string: `DOCTOR_ORDER` | `RISK_PROTOCOL` | `NURSING`) and `taskType` (string/category). Add `missedReason` (text).
2. **Service & Controller Changes** — Update `NurseTaskService.executeTask` to accept an optional `missedReason` parameter, persisting it to `missedReason` if the status is not `DONE`. Update `NurseTaskController` to extract `missedReason` from the request payload (with fallback to `notes` for backward compatibility with existing UI).
3. **Database Migrations** — Update `DatabaseMigrationRunner.java` with a new startup migration method `decoupleNurseTasksSchema()` to:
   - Make `doctor_order_id` nullable (safe drop of NOT NULL constraint via `ALTER TABLE nurse_tasks MODIFY COLUMN doctor_order_id BIGINT DEFAULT NULL`).
   - Backfill `source = 'DOCTOR_ORDER'` for all existing rows (since they all have a `doctor_order_id` today).
4. **Canonical Schema** — Mirror the changes in `setup/schema-full.sql` by updating `CREATE TABLE nurse_tasks` and documenting the ALTER comments.

**Tech Stack:** Spring Boot, Spring Data JPA, Lombok, JUnit 5 + Mockito + AssertJ, MySQL.

---

## Context the engineer needs before starting

- **Tenant model:** Tenant isolation is enforced in `NurseTaskService` via `task.getHospitalId().equals(hospitalId)` and `task.getIpdAdmissionId().equals(admissionId)`. We must maintain these checks.
- **Files in play:**
  - `backend/src/main/java/com/hms/entity/NurseTask.java`
  - `backend/src/main/java/com/hms/service/hospital/NurseTaskService.java`
  - `backend/src/main/java/com/hms/controller/hospital/NurseTaskController.java`
  - `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
  - `setup/schema-full.sql`
  - `backend/src/test/java/com/hms/service/NurseTaskServiceTest.java`

- **Execution Command (run from `backend/`):** `mvn -q -Dtest=NurseTaskServiceTest test`

---

## Tasks

### Task 1: Update the `NurseTask` entity

Make `doctorOrderId` nullable, and add the new columns.

- [ ] **Step 1: Modify `NurseTask.java`**
  Modify `doctorOrderId` annotation to be nullable, and add the new columns:
  ```java
  @Column(name = "doctor_order_id") // removed nullable = false
  private Long doctorOrderId;

  @Column(name = "source", length = 30)
  private String source = "DOCTOR_ORDER"; // default value for new tasks

  @Column(name = "task_type", length = 50)
  private String taskType;

  @Column(name = "missed_reason", columnDefinition = "TEXT")
  private String missedReason;
  ```

- [ ] **Step 2: Verify compilation**
  Run: `mvn -q -DskipTests compile`
  Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**
  Commit message:
  ```
  feat(nursetask): make doctor_order_id nullable and add source/task_type/missed_reason columns

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 2: Service & Controller updates with TDD

Modify `NurseTaskService` to persist tasks without doctor orders and save the missed reason. Modify `NurseTaskController` to forward `missedReason`.

- [ ] **Step 1: Write failing service tests in `NurseTaskServiceTest.java`**
  Add tests proving:
  1. Creating a decoupled task (null `doctorOrderId` + `source=NURSING`) is supported.
  2. Marking a task as `SKIPPED` saves the `missedReason`.
  3. Tenant isolation checks remain intact.

  ```java
  @Test
  void executeTask_savesMissedReasonForSkippedTask() {
      Long hospitalId = 1L;
      Long admissionId = 10L;
      Long taskId = 101L;

      when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
      when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");

      NurseTask task = new NurseTask();
      task.setId(taskId);
      task.setHospitalId(hospitalId);
      task.setIpdAdmissionId(admissionId);
      task.setStatus("PENDING");
      task.setSource("NURSING");
      task.setTaskType("OBSERVATION");

      when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
      when(taskRepository.save(any(NurseTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

      NurseTask executed = nurseTaskService.executeTask(
              admissionId, taskId, "SKIPPED", "Skipping for now", null, null, null, null, "Patient was sleeping");

      assertThat(executed.getStatus()).isEqualTo("SKIPPED");
      assertThat(executed.getNotes()).isEqualTo("Skipping for now");
      assertThat(executed.getMissedReason()).isEqualTo("Patient was sleeping");
      assertThat(executed.getSource()).isEqualTo("NURSING");
      assertThat(executed.getTaskType()).isEqualTo("OBSERVATION");
  }

  @Test
  void executeTask_throwsUnauthorizedForCrossTenantTask() {
      Long hospitalId = 1L;
      Long admissionId = 10L;
      Long taskId = 102L;

      when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

      NurseTask task = new NurseTask();
      task.setId(taskId);
      task.setHospitalId(2L); // Different hospital
      task.setIpdAdmissionId(admissionId);
      task.setStatus("PENDING");

      when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

      assertThatThrownBy(() -> nurseTaskService.executeTask(
              admissionId, taskId, "DONE", "Given", null, null, null, null, null))
              .isInstanceOf(com.hms.exception.UnauthorizedException.class)
              .hasMessageContaining("Access denied");
  }
  ```

- [ ] **Step 2: Run test to verify it fails**
  Run: `mvn -q -Dtest=NurseTaskServiceTest test`
  Expected: FAIL (compilation errors / no method match).

- [ ] **Step 3: Modify `NurseTaskService.executeTask` signature and logic**
  Update the method signature and set `missedReason`:
  ```java
  @Transactional
  public NurseTask executeTask(Long admissionId, Long taskId, String status, String notes,
                               Double administeredQuantity, String route, String injectionSite, String preVitals,
                               String missedReason) {
      mrdService.validateAdmissionActive(admissionId);
      Long hospitalId = securityHelper.getCurrentHospitalId();
      if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

      if (!"DONE".equals(status) && !"SKIPPED".equals(status) && !"REFUSED".equals(status) && !"HELD".equals(status)) {
          throw new IllegalArgumentException("status must be DONE, SKIPPED, REFUSED, or HELD");
      }

      NurseTask task = taskRepository.findById(taskId)
              .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
      if (!task.getHospitalId().equals(hospitalId))
          throw new UnauthorizedException("Access denied");
      if (!task.getIpdAdmissionId().equals(admissionId)) {
          throw new UnauthorizedException("Task does not belong to this admission");
      }
      if (!"PENDING".equals(task.getStatus()))
          throw new IllegalStateException("Task is already " + task.getStatus());

      task.setStatus(status);
      task.setExecutedAt(LocalDateTime.now());
      task.setExecutedByName(securityHelper.getCurrentUserEmail());
      task.setNotes(notes);
      task.setAdministeredQuantity(administeredQuantity);
      task.setRoute(route);
      task.setInjectionSite(injectionSite);
      task.setPreVitals(preVitals);
      
      if (!"DONE".equals(status)) {
          task.setMissedReason(missedReason);
      } else {
          task.setMissedReason(null);
      }
      
      return taskRepository.save(task);
  }
  ```

  Also, update the existing tests in `NurseTaskServiceTest.java` that call `executeTask` to include a trailing `null` for the `missedReason` parameter.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `mvn -q -Dtest=NurseTaskServiceTest test`
  Expected: PASS.

- [ ] **Step 5: Modify `NurseTaskController.java` to extract and pass `missedReason`**
  ```java
  String missedReason = (String) body.get("missedReason");
  if (missedReason == null && ("SKIPPED".equals(status) || "REFUSED".equals(status) || "HELD".equals(status))) {
      missedReason = notes; // fallback if only notes are passed by UI
  }
  ```
  Pass `missedReason` as the last parameter to `taskService.executeTask`.

- [ ] **Step 6: Verify full test suite passes**
  Run: `mvn -q test`
  Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**
  Commit message:
  ```
  feat(nursetask): update executeTask service and controller to wire missed_reason

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 3: Idempotent Database Migration

Write a migration to make `doctor_order_id` nullable and backfill `source` for existing rows.

- [ ] **Step 1: Register migration in `DatabaseMigrationRunner.java`**
  Add at the end of `runMigrations()`:
  ```java
  decoupleNurseTasksSchema();
  ```

- [ ] **Step 2: Implement `decoupleNurseTasksSchema()`**
  ```java
  /**
   * Modifies nurse_tasks to make doctor_order_id nullable and backfills source.
   * Additive + idempotent: modifying column nullability and setting source is safe to rerun.
   */
  private void decoupleNurseTasksSchema() {
      try {
          // 1. Make doctor_order_id nullable
          Integer isNullable = jdbcTemplate.queryForObject(
              "SELECT IS_NULLABLE = 'YES' FROM information_schema.COLUMNS " +
              "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_tasks' AND COLUMN_NAME = 'doctor_order_id'",
              Integer.class
          );
          if (isNullable != null && isNullable == 0) {
              jdbcTemplate.execute("ALTER TABLE nurse_tasks MODIFY COLUMN doctor_order_id BIGINT DEFAULT NULL");
              log.info("DB migration applied: nurse_tasks.doctor_order_id is now nullable");
          }

          // 2. Backfill source column for existing rows
          Integer sourceColumnExists = jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM information_schema.COLUMNS " +
              "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_tasks' AND COLUMN_NAME = 'source'",
              Integer.class
          );
          if (sourceColumnExists != null && sourceColumnExists > 0) {
              Integer pendingSource = jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM nurse_tasks WHERE source IS NULL",
                  Integer.class
              );
              if (pendingSource != null && pendingSource > 0) {
                  int updated = jdbcTemplate.update(
                      "UPDATE nurse_tasks SET source = 'DOCTOR_ORDER' WHERE source IS NULL"
                  );
                  log.info("DB migration applied: backfilled {} nurse_tasks rows with source='DOCTOR_ORDER'", updated);
              }
          }
      } catch (Exception e) {
          log.warn("DB migration skipped (decoupleNurseTasksSchema): {}", e.getMessage());
      }
  }
  ```

- [ ] **Step 3: Verify compile**
  Run: `mvn -q -DskipTests compile`
  Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**
  Commit message:
  ```
  chore(db): add decoupleNurseTasksSchema migration for nullability and backfill

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 4: Mirror in `setup/schema-full.sql`

- [ ] **Step 1: Modify `setup/schema-full.sql`**
  Modify the `nurse_tasks` table:
  - Remove `NOT NULL` from `doctor_order_id`.
  - Add the new columns inside `CREATE TABLE IF NOT EXISTS nurse_tasks`.
  - Add the documentation comments.

  ```sql
  CREATE TABLE IF NOT EXISTS nurse_tasks (
      id BIGINT AUTO_INCREMENT PRIMARY KEY,
      doctor_order_id BIGINT DEFAULT NULL, -- changed from NOT NULL
      ipd_admission_id BIGINT NOT NULL,
      hospital_id BIGINT NOT NULL,
      scheduled_at DATETIME,
      executed_at DATETIME,
      executed_by BIGINT,
      executed_by_name VARCHAR(100),
      status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      notes TEXT,
      administered_quantity DECIMAL(5,2) NULL,
      route VARCHAR(50) NULL,
      injection_site VARCHAR(100) NULL,
      pre_vitals VARCHAR(255) NULL,
      source VARCHAR(30) NOT NULL DEFAULT 'DOCTOR_ORDER',
      task_type VARCHAR(50) DEFAULT NULL,
      missed_reason TEXT DEFAULT NULL,
      CONSTRAINT fk_nt_order FOREIGN KEY (doctor_order_id) REFERENCES doctor_orders(id)
  );
  ```

  And add the migration comments above the table:
  ```sql
  -- Phase 0.3 additive migration:
  -- ALTER TABLE nurse_tasks MODIFY COLUMN doctor_order_id BIGINT DEFAULT NULL;
  -- ALTER TABLE nurse_tasks ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'DOCTOR_ORDER';
  -- ALTER TABLE nurse_tasks ADD COLUMN task_type VARCHAR(50) DEFAULT NULL;
  -- ALTER TABLE nurse_tasks ADD COLUMN missed_reason TEXT DEFAULT NULL;
  -- UPDATE nurse_tasks SET source = 'DOCTOR_ORDER' WHERE source IS NULL;
  ```

- [ ] **Step 2: Commit**
  Commit message:
  ```
  docs(schema): mirror nurse_tasks schema changes in setup/schema-full.sql

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

## Verification Plan

### Automated Tests
- Run `mvn -q test` in `backend/` to verify the entire test suite passes.
- Run `npm run build` in `frontend/` to verify frontend build compiles successfully.

### Manual Verification
- Verify that restarting the Spring Boot backend runs the migrations and prints:
  `DB migration applied: nurse_tasks.doctor_order_id is now nullable` (if run on a DB containing the old schema).
- Run a request to `/api/ipd/{admissionId}/tasks/{taskId}/execute` with status `SKIPPED` and verify `missed_reason` is updated in the database.
