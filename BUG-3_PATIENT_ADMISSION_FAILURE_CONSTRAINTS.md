# Bug 3 Resolution Report: Patient ICU/IPD Admission Failure & Single Bed Constraint

## 1. Issue Summary
Clicking the "Add Patient" / "Admit Patient" button in the ICU module failed silently without creating an admission record or throwing visible console errors. In addition, existing logic permitted a single patient to be concurrently admitted to multiple active beds across different wards without validation checks.

## 2. Root Cause Analysis
- **Type Mismatch in Frontend Dropdown**: The bed selector in the admission modal passed string IDs (`"5"`), while the backend expected numeric Long IDs or matched objects by exact reference.
- **Missing Active Admission Validation**: `IpdAdmissionService.java` created new `IpdAdmission` records without verifying if the patient already had an active (`ADMITTED`) status in another bed.
- **Unhandled UI Exception**: Frontend submit handlers lacked standard error try/catch blocks with toast notifications, leading to unhandled promise rejections that failed silently.

## 3. Changes Implemented

### Backend Changes (`backend/src/main/java/.../ipd/`)
- Added active admission verification in `IpdAdmissionService.java`:
  ```java
  boolean hasActiveAdmission = ipdAdmissionRepository.existsByPatientIdAndStatus(patientId, "ADMITTED");
  if (hasActiveAdmission) {
      throw new IllegalStateException("Patient already has an active IPD/ICU admission.");
  }
  ```
- Implemented automatic bed state transition (`AVAILABLE` -> `OCCUPIED`) upon successful admission submission within an atomic transaction (`@Transactional`).

### Frontend Changes (`frontend/src/components/modals/AdmitPatientModal.jsx`)
- Fixed bed and ward ID parsing (converting String values to Long before payload submission).
- Added user-facing error notification alerts when admission fails (e.g., bed unavailable or patient already admitted).
- Updated bed selector dropdown to dynamically filter out non-`AVAILABLE` beds (hiding `OCCUPIED`, `MAINTENANCE`, `CLEANING` beds).

## 4. Edge Cases Identified & Solved
- **Re-admission Attempt**: Attempting to admit a patient who is currently admitted in another ward now cleanly returns a `400 Bad Request` with an explicit user message.
- **Race Condition on Bed Selection**: Added backend bed status check right before saving admission to prevent two receptionists from admitting two patients to the same bed simultaneously.
- **Discharged Patient Re-admission**: Confirmed that previously discharged patients (`status: DISCHARGED`) can be re-admitted without conflict.

## 5. Empirical Verification
- Submitted admission for Patient ID 1 to Bed ICU-101; bed status changed to `OCCUPIED` and IPD record created.
- Attempted to admit Patient ID 1 again to Bed ICU-102; UI displayed error: `"Patient already has an active IPD/ICU admission."`
