# Phase 0.7 — Admission Decoupling & Monitoring Vitals Implementation Plan

**Goal:**
1. Allow admitting a patient directly from Emergency (without an OPD record) in the `IpdAdmissionService`.
2. Secure `IpdAdmissionService.addIpdFollowup` against IDOR by validating the request tenant matches the admission record tenant.
3. Establish the new `monitoring_vitals` database table and Hibernate entity to capture vitals recorded in the Operating Theatre (Intraoperative) and Recovery Room (PACU) with a `context` discriminator.

---

## Target Files & Packages
- `backend/src/main/java/com/hms/entity/MonitoringVitals.java` [NEW]
- `backend/src/main/java/com/hms/repository/MonitoringVitalsRepository.java` [NEW]
- `backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java`
- `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
- `setup/schema-full.sql`

---

## Tasks

### Task 1: Decoupling & IDOR Guard in `IpdAdmissionService`

- [ ] **Step 1: Add `admitFromEmergency` method**
  Implement:
  ```java
  @Transactional
  public IpdAdmission admitFromEmergency(Long patientId, Long doctorId, Long wardId, Long bedId, String admissionType, String primaryDiagnosis)
  ```
  This creates an IPD admission directly, assigns a sequential IPD number, marks the bed as occupied, creates an initial billing item (if the billing module is present), saves initial bed history, broadcasts a websocket refresh, and audits the action.

- [ ] **Step 2: Add tenant-isolation check to `addIpdFollowup`**
  Add the IDOR tenant guard in `addIpdFollowup` (~line 526):
  ```java
  Long hospitalId = securityHelper.getCurrentHospitalId();
  if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
  if (!ipd.getHospitalId().equals(hospitalId)) {
      throw new UnauthorizedException("Access Denied: Record belongs to another tenant");
  }
  ```

---

### Task 2: Create MonitoringVitals Entity & Repository

- [ ] **Step 1: Create `MonitoringVitals.java`**
  Create the Hibernate entity with fields:
  - `id` (Long, GenerationType.IDENTITY)
  - `ipdAdmissionId` (Long, not null)
  - `hospitalId` (Long, not null)
  - `context` (String, not null - `"INTRAOP"` or `"PACU"`)
  - `pulse` (Integer)
  - `bpSystolic` (Integer)
  - `bpDiastolic` (Integer)
  - `spo2` (Integer)
  - `respiratoryRate` (Integer)
  - `temperature` (BigDecimal, 4 precision, 1 scale)
  - `recordedBy` (Long)
  - `recordedByName` (String, 100)
  - `recordedAt` (LocalDateTime)

- [ ] **Step 2: Create `MonitoringVitalsRepository.java`**
  Create repository extending `JpaRepository`.

---

### Task 3: Database Migration & Schema Mirroring

- [ ] **Step 1: Implement `migrateAdmissionMonitoringSchema()` in `DatabaseMigrationRunner.java`**
  Add runner logic to:
  - Create the `monitoring_vitals` table if it does not exist.
  - Register the method in `runMigrations()`.

- [ ] **Step 2: Mirror in `setup/schema-full.sql`**
  Add the `CREATE TABLE monitoring_vitals` query.

---

### Task 4: Integration & IDOR Tests

- [ ] **Step 1: Write `IpdAdmissionServiceTest.java`**
  - Verify direct emergency admission creates valid IPD and bed allocation correctly.
  - Verify `addIpdFollowup` rejects cross-tenant requests (throws `UnauthorizedException`).
  - Verify `monitoring_vitals` table persists data correctly.

---

## Verification Plan

### Automated Tests
- Run `mvn -q test` in `backend/` to verify all tests pass.
- Run `npm run build` in `frontend/` to verify Vite compile check.
