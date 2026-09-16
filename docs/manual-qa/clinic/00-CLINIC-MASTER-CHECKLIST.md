# 00 — CLINIC MASTER CHECKLIST (completeness proof)

**Baseline:** `origin/staging` @ `aa143a720b9cc7fa6f275792e3acbf1269c0350f` (re-fetched at Tranche 3; **0 files changed**).
**Clinic cases:** 134 (`TC-CD` 40 · `TC-CA` 26 · `TC-CR` 22 · `TC-CDR` 20 · `TC-CP` 16 · `TC-CX` 10).

**Status key:** `TESTED` · `SHARED_REFERENCE` · `PARTIAL` · `PLACEHOLDER` · `DEAD_UNREACHABLE` · `NOT_SUPPORTED` · `IMPLEMENTATION_DRIFT` · `NEEDS_PRODUCT_CONFIRMATION`

> **Clinic has no frontend of its own.** It renders the Hospital components at `/hospital/*` URLs
> and differs only in the API namespace (`/clinic/**`), the module set and the role set. Surfaces
> marked `SHARED_REFERENCE` are covered by running the named Hospital case **as a clinic user**;
> `01-CLINIC-DELTA-FROM-HOSPITAL.md` is the authority.

## Documents

| Doc                                 | Scope                                                   | Cases | Prefix    |
| ----------------------------------- | ------------------------------------------------------- | ----- | --------- |
| `01-CLINIC-DELTA-FROM-HOSPITAL.md`  | **authoritative comparison + all negative/drift cases** | 40    | `TC-CD-`  |
| `02-CLINIC-ADMIN.md`                | admin setup, settings, reports, audit                   | 26    | `TC-CA-`  |
| `03-CLINIC-RECEPTIONIST.md`         | patients, identity, appointments, OPD, billing          | 22    | `TC-CR-`  |
| `04-CLINIC-DOCTOR.md`               | consultation, prescriptions, lab, PDFs                  | 20    | `TC-CDR-` |
| `05-CLINIC-PHARMACY-INTEGRATION.md` | clinic pharmacy module                                  | 16    | `TC-CP-`  |
| `06-CLINIC-CROSS-ROLE-WORKFLOWS.md` | journeys C1–C8                                          | 10    | `TC-CX-`  |
| `07-CLINIC-REGRESSION-CHECKLIST.md` | P0 (14) / P1 (18) / P2 (22)                             | —     | —         |

---

## 1. Clinic admin dashboard — every tab classified

| Tab (label)                                                          | requiredModule     | Status                                       | Cases                          |
| -------------------------------------------------------------------- | ------------------ | -------------------------------------------- | ------------------------------ |
| Overview                                                             | —                  | `TESTED`                                     | `TC-CD-033`, `TC-CA-018`       |
| Patients                                                             | OPD                | `SHARED_REFERENCE` + identity re-specified   | `TC-CA-017`, `TC-CR-001`…`010` |
| Appointments                                                         | APPOINTMENTS       | `SHARED_REFERENCE`                           | `TC-CR-011`…`013`              |
| OPD                                                                  | OPD                | `SHARED_REFERENCE`                           | `TC-CR-014`…`018`              |
| Follow-ups                                                           | OPD                | `SHARED_REFERENCE`                           | `TC-CR-019`                    |
| Pharmacy                                                             | PHARMACY           | `TESTED`                                     | `TC-CA-016`, `TC-CP-*`         |
| Pharmacists                                                          | PHARMACY           | `TESTED`                                     | `TC-CA-003`                    |
| Medicine Inventory                                                   | MEDICAL_INVENTORY  | `TESTED`                                     | `TC-CA-014`                    |
| Billing                                                              | BILLING            | `SHARED_REFERENCE`                           | `TC-CR-020`                    |
| Fees                                                                 | BILLING            | `TESTED`                                     | `TC-CA-006`                    |
| Doctors                                                              | OPD                | `TESTED`                                     | `TC-CA-002`                    |
| Receptionists                                                        | OPD                | `TESTED`                                     | `TC-CA-003`                    |
| Reports & Analytics                                                  | REPORTS            | `TESTED`                                     | `TC-CA-019`, `TC-CA-020`       |
| Audit Logs                                                           | —                  | `TESTED`                                     | `TC-CA-021`                    |
| Settings                                                             | —                  | `TESTED`                                     | `TC-CA-008`…`011`              |
| Support                                                              | —                  | `TESTED`                                     | `TC-CA-022`, `TC-CD-032`       |
| Quick Notes · Symptom · Diagnosis · Prescription · In-Clinic Presets | — / `inClinic`     | `TESTED`                                     | `TC-CA-013`, `TC-CDR-007`      |
| **IPD**                                                              | IPD                | **`NOT_SUPPORTED` + `IMPLEMENTATION_DRIFT`** | `TC-CD-010`…`015`              |
| **Wards & Beds**                                                     | IPD                | **`NOT_SUPPORTED` + `IMPLEMENTATION_DRIFT`** | `TC-CD-016`, `TC-CD-017`       |
| ICU Dashboard · ICU Bed Board                                        | ICU                | `NOT_SUPPORTED` (not aliased)                | `TC-CD-018`                    |
| Operation Theatre · OT Incharge · OT Theatres · OT Analytics         | OT                 | `NOT_SUPPORTED` (not aliased)                | `TC-CD-018`                    |
| Nurses · Nurse Assignments · Nurse Tasks · Time Slots · Calendar     | NURSING            | `NOT_SUPPORTED` (not aliased)                | `TC-CD-018`, `TC-CD-005`       |
| Hospital Inventory                                                   | HOSPITAL_INVENTORY | `NOT_SUPPORTED` (correctly gated)            | `TC-CD-019`, `TC-CA-015`       |
| Pathology                                                            | `PATHOLOGY`        | `PLACEHOLDER`                                | `TC-MOD-030`                   |

## 2. Clinic role dashboards

| Role                                 | Landing                                | Tabs                                                                           | Status             | Cases                    |
| ------------------------------------ | -------------------------------------- | ------------------------------------------------------------------------------ | ------------------ | ------------------------ |
| HOSPITAL_ADMIN                       | `/hospital/admin`                      | 16 + presets                                                                   | `TESTED`           | `02`                     |
| RECEPTIONIST                         | `/hospital/receptionist`               | Overview, Patients, Appointments, OPD, Follow-ups, Billing, Medicine Inventory | `TESTED`           | `03`                     |
| DOCTOR                               | `/hospital/doctor`                     | same 7                                                                         | `TESTED`           | `04`                     |
| PHARMACIST                           | `/hospital/pharmacy`                   | 13 pharmacy tabs                                                               | `TESTED`           | `05`                     |
| NURSE · NURSE_INCHARGE · OT_INCHARGE | —                                      | —                                                                              | `NOT_SUPPORTED`    | `TC-CD-007`, `TC-CD-008` |
| `isSingleDoctor` dual role           | `/hospital/doctor` ⇄ `/hospital/admin` | —                                                                              | `SHARED_REFERENCE` | `TC-CD-040`              |

## 3. Modals, nested views, PDFs

| Surface                                                                    | Status             | Cases                                  |
| -------------------------------------------------------------------------- | ------------------ | -------------------------------------- |
| `PatientModal`, `DuplicatePhoneConflictModal`                              | `TESTED`           | `TC-CR-003`…`007`                      |
| `AppointmentModal`, New OPD modal (reception + admin)                      | `TESTED`           | `TC-CR-012`, `TC-CR-015`               |
| `ConsultationModal` (incl. lab picker, presets, in-clinic)                 | `TESTED`           | `TC-CDR-004`…`009`                     |
| `PatientDetailsModal`, `HistoryDrawer`, documents panel/preview            | `SHARED_REFERENCE` | `TC-CR-010`, `TC-CDR-016`              |
| `FollowUpPanel`, `BillingTable`, `ConfirmationModal`, `EmptyState`         | `SHARED_REFERENCE` | `TC-CR-019`, `TC-CR-020`, `TC-CR-022`  |
| Pharmacy views (13 tabs, `DispenseModal`, `PurchaseForm`, …)               | `TESTED`           | `05`                                   |
| Settings cards: Operations, Print & Payment, Vitals, Files & Access        | `TESTED`           | `TC-CA-008`…`011`                      |
| Settings cards: ICU ×3, OT ×3                                              | `NOT_SUPPORTED`    | `TC-CD-029`, `TC-CD-034`               |
| `IpdAdmitModal`, ward/bed views, OT modals, nurse panels, NABH forms       | `NOT_SUPPORTED`    | `TC-CD-010`, `TC-CD-018`               |
| PDFs: prescription, case paper, OPD docs, billing receipt, patients report | `TESTED`           | `TC-CDR-014`, `TC-CD-028`, `TC-CA-020` |
| Pharmacy invoice + CSV ledger export                                       | `TESTED`           | `TC-CP-008`, `TC-CP-013`               |

## 4. Non-`TESTED` register

| Item                                                             | Status                       | Evidence                              | Case                      |
| ---------------------------------------------------------------- | ---------------------------- | ------------------------------------- | ------------------------- |
| `/clinic/ipd` — 13 endpoints                                     | `IMPLEMENTATION_DRIFT`       | no `@TenantType`, no `@RequireModule` | `TC-CD-010`…`015`         |
| `/clinic/wards` — 7 · `/clinic/beds` — 5                         | `IMPLEMENTATION_DRIFT`       | same                                  | `TC-CD-016`, `TC-CD-017`  |
| OPD & PHARMACY module gates                                      | `IMPLEMENTATION_DRIFT`       | no `@RequireModule` anywhere          | `TC-CD-037`, `TC-CP-015`  |
| Lab ordering                                                     | `PARTIAL`                    | orders created, no results UI         | `TC-CD-027`, `TC-CDR-006` |
| Pathology                                                        | `PLACEHOLDER`                | module key not grantable              | `TC-MOD-030`              |
| Files & Access offering IPD/OT form keys to a clinic             | `NEEDS_PRODUCT_CONFIRMATION` | card is not module-gated              | `TC-CD-031`               |
| Nurse-login / OT-incharge toggles visible in clinic Operations   | `NEEDS_PRODUCT_CONFIRMATION` | shared settings DTO                   | `TC-CD-030`               |
| Doctors/Receptionists tabs gated on `OPD` while APIs are ungated | `NEEDS_PRODUCT_CONFIRMATION` | `requiredModule: 'OPD'`               | `TC-CD-037`               |
| "Hospital Rx Mode" wording inside a clinic                       | `LOW` cosmetic               | shared component label                | `TC-CP-008`               |
| Doctors viewing colleagues' OPD                                  | `NEEDS_PRODUCT_CONFIRMATION` | same open question as hospital        | `TC-CDR-002`              |

**Unmapped surfaces: 0.**
