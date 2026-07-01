# Phase 0.6 — Role Framework Implementation Plan

**Goal:** Establish the extensible least-privilege role framework, register new roles (`MRD_OFFICER`, `QUALITY_OFFICER`, and others), and create a central, tested authorization matrix.

**Architecture:**
1. **Role Constants** — Create `com.hms.security.UserRole` containing static string constants for all existing roles and all roadmapped roles:
   - `SUPER_ADMIN`, `HOSPITAL_ADMIN`, `DOCTOR`, `RECEPTIONIST`, `NURSE`, `PHARMACIST`, `LAB_TECHNICIAN`, `RADIOLOGY_TECHNICIAN`
   - `MRD_OFFICER`, `QUALITY_OFFICER`, `DEPARTMENT_HEAD`, `STORE_KEEPER`, `PURCHASE_OFFICER`, `BIOMEDICAL_ENGINEER`, `CSSD_TECHNICIAN`, `BLOOD_BANK_TECHNICIAN`, `HOUSEKEEPING`, `ACCOUNTANT`, `HR_EXECUTIVE`, `IT_ADMIN`
2. **Security Configuration** — Update `SecurityConfig.java` to:
   - Include `MRD_OFFICER` and `QUALITY_OFFICER` in the authorized HMS roles allowed to access `/hospital/**` and `/ws/**`.
3. **Role PreAuthorize Matrix** — Wire the new roles into `@PreAuthorize` where appropriate (e.g. `MRD_OFFICER` on `deletePatient` or report viewing if needed, but for now we just verify standard endpoint mapping).
4. **TDD Matrix Test** — Write `SecurityRoleAuthTest.java` that uses MockMvc to verify:
   - `/platform/**` endpoints only allow `SUPER_ADMIN`, returning `403 Forbidden` for standard hospital roles (e.g. `DOCTOR`, `HOSPITAL_ADMIN`, `MRD_OFFICER`).
   - `/hospital/**` endpoints allow standard roles (`DOCTOR`, `HOSPITAL_ADMIN`) plus the new `MRD_OFFICER` and `QUALITY_OFFICER` roles.
   - `/hospital/**` rejects `SUPER_ADMIN` (returning `403 Forbidden`).

---

## Target Files & Packages
- `backend/src/main/java/com/hms/security/UserRole.java` [NEW]
- `backend/src/main/java/com/hms/config/SecurityConfig.java`
- `backend/src/test/java/com/hms/security/SecurityRoleAuthTest.java` [NEW]

---

## Tasks

### Task 1: Create Role Constants & Update SecurityConfig

- [ ] **Step 1: Create `UserRole.java`**
  Add the constants class:
  ```java
  package com.hms.security;

  public class UserRole {
      public static final String SUPER_ADMIN = "SUPER_ADMIN";
      public static final String HOSPITAL_ADMIN = "HOSPITAL_ADMIN";
      public static final String DOCTOR = "DOCTOR";
      public static final String RECEPTIONIST = "RECEPTIONIST";
      public static final String NURSE = "NURSE";
      public static final String PHARMACIST = "PHARMACIST";
      public static final String LAB_TECHNICIAN = "LAB_TECHNICIAN";
      public static final String RADIOLOGY_TECHNICIAN = "RADIOLOGY_TECHNICIAN";

      // Extensible roles per §1.2
      public static final String MRD_OFFICER = "MRD_OFFICER";
      public static final String QUALITY_OFFICER = "QUALITY_OFFICER";
      public static final String DEPARTMENT_HEAD = "DEPARTMENT_HEAD";
      public static final String STORE_KEEPER = "STORE_KEEPER";
      public static final String PURCHASE_OFFICER = "PURCHASE_OFFICER";
      public static final String BIOMEDICAL_ENGINEER = "BIOMEDICAL_ENGINEER";
      public static final String CSSD_TECHNICIAN = "CSSD_TECHNICIAN";
      public static final String BLOOD_BANK_TECHNICIAN = "BLOOD_BANK_TECHNICIAN";
      public static final String HOUSEKEEPING = "HOUSEKEEPING";
      public static final String ACCOUNTANT = "ACCOUNTANT";
      public static final String HR_EXECUTIVE = "HR_EXECUTIVE";
      public static final String IT_ADMIN = "IT_ADMIN";
  }
  ```

- [ ] **Step 2: Update `SecurityConfig.java`**
  Add `MRD_OFFICER` and `QUALITY_OFFICER` to:
  - WebSocket endpoints mapping: `.requestMatchers("/ws/**").hasAnyRole(...)`
  - Hospital mapping: `.requestMatchers("/hospital/**", "/api/pharmacy/**").hasAnyRole(...)`

- [ ] **Step 3: Verify compilation**
  Run `mvn -q -DskipTests compile`

- [ ] **Step 4: Commit**
  Commit message:
  ```
  feat(security): define extensible UserRole constants and update SecurityConfig mappings

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

### Task 2: Implement and Verify Authorization Matrix Test (TDD)

- [ ] **Step 1: Create `SecurityRoleAuthTest.java`**
  Write tests using `MockMvc` to verify the URL role constraints.
- [ ] **Step 2: Run tests and verify they pass**
  Run `mvn -q -Dtest=SecurityRoleAuthTest test`.
- [ ] **Step 3: Commit**
  Commit message:
  ```
  test(security): add SecurityRoleAuthTest for platform and hospital role matrix

  Co-Authored-By: Antigravity <antigravity@google.com>
  ```

---

## Verification Plan

### Automated Tests
- Run `mvn -q test` in `backend/` to verify all tests pass.
- Run `npm run build` in `frontend/` to verify build compiles.
