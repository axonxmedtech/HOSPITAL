# Phase 0.5 — Canonical Staff Identity Implementation Plan

**Goal:** Unify the three staff representations (`User`, `Doctor`, `Nurse`) under one identity via `user_id` FKs, and add the capacity/demographic flags the clinical phases need, without deleting existing tables.

**Architecture:**
1. **Entity Changes** — Add the following fields:
   - `User.java`: `department` (String), `designation` (String), `isTrainer` (Boolean, default false).
   - `Doctor.java`: `userId` (Long), `isAnaesthetist` (Boolean, default false), `isSurgeon` (Boolean, default false), `isPathologist` (Boolean, default false), `isRadiologist` (Boolean, default false), `isIntensivist` (Boolean, default false), `isCmo` (Boolean, default false).
   - `Nurse.java`: `userId` (Long), `isScrub` (Boolean, default false), `isOt` (Boolean, default false), `isPacu` (Boolean, default false), `isIcu` (Boolean, default false).
2. **Service Changes** — Ensure new doctor/nurse creation logic links them to their newly created `User` account:
   - `DoctorService.addDoctor(...)`: Save the `User` first, retrieve its `id`, set it as `userId` on the `Doctor` object, then save `Doctor`.
   - `NurseService.createNurse(...)`: Retrieve the saved `User`'s ID, set it as `userId` on the `Nurse` profile, then save `Nurse`.
3. **Database Migrations** — In `DatabaseMigrationRunner.java`, add `migrateStaffIdentitySchema()` to:
   - Add new columns dynamically if they do not exist.
   - Backfill `user_id` on existing `doctors` and `nurses` by matching email addresses.
4. **Canonical Schema** — Mirror changes in `setup/schema-full.sql`.

---

## Target Files & Packages
- `backend/src/main/java/com/hms/entity/User.java`
- `backend/src/main/java/com/hms/entity/Doctor.java`
- `backend/src/main/java/com/hms/entity/Nurse.java`
- `backend/src/main/java/com/hms/service/hospital/DoctorService.java`
- `backend/src/main/java/com/hms/service/hospital/NurseService.java`
- `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
- `setup/schema-full.sql`

---

## Tasks

### Task 1: Update Entities

- [ ] **Step 1: Modify `User.java`**
  Add fields at the end:
  ```java
  @Column(name = "department", length = 100)
  private String department;

  @Column(name = "designation", length = 100)
  private String designation;

  @Column(name = "is_trainer", nullable = false)
  private Boolean isTrainer = false;
  ```
- [ ] **Step 2: Modify `Doctor.java`**
  Add fields:
  ```java
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "is_anaesthetist", nullable = false)
  private Boolean isAnaesthetist = false;

  @Column(name = "is_surgeon", nullable = false)
  private Boolean isSurgeon = false;

  @Column(name = "is_pathologist", nullable = false)
  private Boolean isPathologist = false;

  @Column(name = "is_radiologist", nullable = false)
  private Boolean isRadiologist = false;

  @Column(name = "is_intensivist", nullable = false)
  private Boolean isIntensivist = false;

  @Column(name = "is_cmo", nullable = false)
  private Boolean isCmo = false;
  ```
- [ ] **Step 3: Modify `Nurse.java`**
  Add fields:
  ```java
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "is_scrub", nullable = false)
  private Boolean isScrub = false;

  @Column(name = "is_ot", nullable = false)
  private Boolean isOt = false;

  @Column(name = "is_pacu", nullable = false)
  private Boolean isPacu = false;

  @Column(name = "is_icu", nullable = false)
  private Boolean isIcu = false;
  ```
- [ ] **Step 4: Verify Compilation**
  Run `mvn -q -DskipTests compile` to verify code compiles.
- [ ] **Step 5: Commit**
  Commit message:
  ```
  feat(staff): add fields and user_id foreign keys to User, Doctor, and Nurse entities

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 2: Service Layer & TDD Verification

- [ ] **Step 1: Write TDD tests for Doctor and Nurse creation links**
  Add tests in `DoctorServiceTest.java` (create if needed, or add to existing tests) and `NurseServiceTest.java` asserting:
  1. Adding a doctor automatically links `userId` from the created `User`.
  2. Creating a nurse automatically links `userId` from the created `User`.
- [ ] **Step 2: Update `DoctorService.java`**
  Rearrange `addDoctor` to save the `User` first and assign its `id` to the `doctor.userId` field.
- [ ] **Step 3: Update `NurseService.java`**
  In `createNurse`, set the saved `User`'s ID as `userId` on the created `Nurse` profile record.
- [ ] **Step 4: Run tests and verify success**
  Run `mvn -q test`.
- [ ] **Step 5: Commit**
  Commit message:
  ```
  feat(staff): update DoctorService and NurseService to link profiles to User accounts

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 3: Database Migrations & Mirroring

- [ ] **Step 1: Register migration in `DatabaseMigrationRunner.java`**
  Call `migrateStaffIdentitySchema();` in `runMigrations()`.
- [ ] **Step 2: Implement `migrateStaffIdentitySchema()`**
  - Schema additions (if columns don't exist):
    - `users`: `department`, `designation`, `is_trainer`
    - `doctors`: `user_id`, `is_anaesthetist`, `is_surgeon`, `is_pathologist`, `is_radiologist`, `is_intensivist`, `is_cmo`
    - `nurses`: `user_id`, `is_scrub`, `is_ot`, `is_pacu`, `is_icu`
  - Backfill queries:
    - `UPDATE doctors d JOIN users u ON d.email = u.email SET d.user_id = u.id WHERE d.user_id IS NULL;`
    - `UPDATE nurses n JOIN users u ON n.email = u.email SET n.user_id = u.id WHERE n.user_id IS NULL;`
- [ ] **Step 3: Update `setup/schema-full.sql`**
  Add new fields/columns to the table structures for `users`, `doctors`, and `nurses`. Add commented alter statements.
- [ ] **Step 4: Verify test suite runs and passes**
  Run: `mvn -q test`
- [ ] **Step 5: Commit**
  Commit message:
  ```
  chore(db): implement migrateStaffIdentitySchema migration and schema-full update

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

## Verification Plan

### Automated Tests
- Run `mvn -q test` in `backend/` to verify all tests pass.
- Run `npm run build` in `frontend/` to verify build compiles.

### Manual Verification
- View database contents after boot and verify that pre-existing Doctors/Nurses have their `user_id` correctly backfilled.
