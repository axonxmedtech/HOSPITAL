# Sub-plan: Phase 1 — Consent Management Engine (Forms 05 & 01)

This sub-plan outlines the test-driven development (TDD) implementation of the shared **Consent Management Engine**, General Consent, and Blood Transfusion Consent.

---

## Target Files & Packages
- `backend/src/main/java/com/hms/service/hospital/ConsentService.java` [NEW]
- `backend/src/main/java/com/hms/controller/hospital/ConsentController.java` [NEW]
- `backend/src/test/java/com/hms/service/ConsentServiceTest.java` [NEW]

---

## Tasks & Deliverables

### Task 1: Create ConsentService Core & Business Rules

- [ ] **Step 1: Implement `createConsentDraft(...)`**
  - Takes `patientId`, `admissionId`, `consentType` (`GENERAL` or `BLOOD`), and `language`.
  - Asserts patient belongs to the hospital context (tenant isolation).
  - Asserts admission status is active.
  - Rejects if another active draft/signed/submitted consent exists for `(admissionId, consentType)`.
  - For `BLOOD`, requires `bloodRequestId` to be linked in `BloodConsentDetail`.
- [ ] **Step 2: Implement `signConsent(...)`**
  - Allows capturing signatures for `PATIENT`, `GUARDIAN`, `WITNESS`, or `INTERPRETER`.
  - If patient is a minor (DOB-derived age < 18 or explicitly recorded age < 18), require `GUARDIAN` signature.
  - Updates the parent `patient_consent` state to `SIGNED`.
- [ ] **Step 3: Implement `submitConsent(...)`**
  - Performs final server-side validations:
    - If `interpreterRequired` is true, require interpreter name and signature.
    - If minor, require guardian details, relationship, and signature.
    - Updates status to `LOCKED` (making it immutable).

---

### Task 2: Create ConsentController Endpoints

- [ ] **Step 1: Implement Endpoints**
  - `POST /hospital/consents` (draft creation) - limited to `RECEPTIONIST`, `DOCTOR`, or `HOSPITAL_ADMIN`.
  - `GET /hospital/consents/{id}` - checks hospital ID context before returning details.
  - `GET /hospital/patients/{patientId}/consents`
  - `POST /hospital/consents/{id}/sign`
  - `POST /hospital/consents/{id}/submit`
  - `GET /hospital/consents/{id}/print`

---

### Task 3: Unit & Integration Tests (TDD)

- [ ] **Step 1: Create `ConsentServiceTest.java`**
  - Test tenant isolation: cross-tenant access must throw `UnauthorizedException` or return empty lists.
  - Test minor guardian validation rules.
  - Test unique active consent constraint validation.
  - Test transition of states: `DRAFT` -> `SIGNED` -> `LOCKED` (immutable).

---

## Verification Plan

### Automated Tests
- Run `mvn -q -Dtest=ConsentServiceTest test` to verify all consent engine rules pass.
- Run the full suite `mvn -q test` on the backend.
