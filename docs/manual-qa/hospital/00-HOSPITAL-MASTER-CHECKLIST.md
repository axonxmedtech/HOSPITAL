# 00 — HOSPITAL MASTER CHECKLIST (completeness proof)

**Baseline:** `origin/staging` @ `aa143a720b9cc7fa6f275792e3acbf1269c0350f` (re-fetched at Tranche 2; **0 files changed** since the Tranche 1 baseline).
**Tranche 2 cases:** 464 (`TC-HA`, `TC-HAC`, `TC-HAS`, `TC-HR`, `TC-HD`, `TC-HN`, `TC-HNI`, `TC-HP`, `TC-HB`, `TC-HOT`, `TC-HICU`, `TC-HWB`, `TC-HX`).

Every Hospital UI surface found during inspection appears below with a status and a test
reference. **Nothing discovered was dropped.**

**Status key:** `TESTED` · `PARTIAL` · `PLACEHOLDER` · `DEAD_UNREACHABLE` · `NEEDS_PRODUCT_CONFIRMATION`

---

## 1. Documents in this pack

| Doc                                   | Scope                                            | Cases | ID prefix  |
| ------------------------------------- | ------------------------------------------------ | ----- | ---------- |
| `01-HOSPITAL-ADMIN-SETUP.md`          | staff, master data, presets, inventory, audit    | 46    | `TC-HA-`   |
| `01b-HOSPITAL-ADMIN-CLINICAL.md`      | admin clinical tabs + reports                    | 26    | `TC-HAC-`  |
| `01c-HOSPITAL-ADMIN-SETTINGS.md`      | all 11 settings cards + downstream               | 44    | `TC-HAS-`  |
| `02-HOSPITAL-RECEPTIONIST.md`         | reception end-to-end                             | 58    | `TC-HR-`   |
| `03-HOSPITAL-DOCTOR.md`               | consultation, prescriptions, IPD                 | 48    | `TC-HD-`   |
| `04-HOSPITAL-NURSE.md`                | staff nurse + 12 clinical panels                 | 40    | `TC-HN-`   |
| `05-HOSPITAL-NURSE-INCHARGE.md`       | ward scope, schedule, attendance, coverage, beds | 32    | `TC-HNI-`  |
| `06-HOSPITAL-PHARMACY-DEPT.md`        | hospital pharmacy module                         | 40    | `TC-HP-`   |
| `07-HOSPITAL-BILLING.md`              | OPD/IPD/pharmacy billing lifecycle               | 30    | `TC-HB-`   |
| `08-HOSPITAL-OT.md`                   | OT lifecycle + 15 NABH forms                     | 38    | `TC-HOT-`  |
| `09-HOSPITAL-ICU.md`                  | ICU lifecycle + the ICU-without-IPD question     | 22    | `TC-HICU-` |
| `10-HOSPITAL-WARDS-BEDS.md`           | ward/bed lifecycle and integrity                 | 24    | `TC-HWB-`  |
| `11-HOSPITAL-CROSS-ROLE-WORKFLOWS.md` | 8 journeys H1–H8                                 | 16    | `TC-HX-`   |
| `12-HOSPITAL-REGRESSION-CHECKLIST.md` | P0/P1/P2 selection (no new IDs)                  | —     | —          |

**Why this split** (adapted from the requested list): the admin dashboard carries **40 tabs and 11
settings cards** — far too much for one file — so it is divided by _purpose_ (setup / clinical /
settings) rather than by tab. `09` (ICU) and `10` (Wards & Beds) were kept as requested because
both are cross-role lifecycles rather than one role's screen.

---

## 2. HOSPITAL ADMIN dashboard — all 40 tabs mapped

Sidebar groups are shown as they render. `requiredModule` is the value read from the code.

| Group              | Tab (label)              | requiredModule     | Status          | Cases                                       |
| ------------------ | ------------------------ | ------------------ | --------------- | ------------------------------------------- |
| —                  | **Overview**             | —                  | TESTED          | `TC-HA-001`…`003`, `TC-HX-001`              |
| Patient Management | **Patients**             | OPD                | TESTED          | `TC-HA-027`, `TC-HR-001`…`020`              |
| Patient Management | **Appointments**         | APPOINTMENTS       | TESTED          | `TC-HAC-001`…`003`, `TC-HR-021`…`028`       |
| Patient Management | **OPD**                  | OPD                | TESTED          | `TC-HAC-004`…`007`, `TC-HR-029`…`036`       |
| Patient Management | **IPD**                  | IPD                | TESTED          | `TC-HAC-010`/`011`, `TC-HR-039`…`044`       |
| Patient Management | **Operation Theatre**    | OT                 | TESTED          | `TC-HAC-017`, `TC-HOT-001`…`038`            |
| Patient Management | **Pathology**            | `PATHOLOGY`        | **PLACEHOLDER** | `TC-HA-038`, `TC-HAC-023`, `TC-MOD-030`     |
| —                  | **Follow-ups**           | OPD                | TESTED          | `TC-HAC-008`/`009`, `TC-HR-037`/`038`       |
| Rooms              | **Wards & Beds**         | IPD                | TESTED          | `TC-HAC-015`, `TC-HWB-001`…`024`            |
| Critical Care      | **ICU Dashboard**        | ICU                | TESTED          | `TC-HAC-016`, `TC-HICU-004`                 |
| Critical Care      | **ICU Bed Board**        | ICU                | TESTED          | `TC-HICU-005`                               |
| Staff              | **Doctors**              | OPD                | TESTED          | `TC-HA-004`…`010`                           |
| Staff              | **Pharmacists**          | PHARMACY           | TESTED          | `TC-HA-014`/`015`                           |
| Staff              | **Receptionists**        | OPD                | TESTED          | `TC-HA-011`…`013`                           |
| Staff              | **OT Incharge**          | OT                 | TESTED          | `TC-HA-025`                                 |
| Staff              | **OT Theatres**          | OT                 | TESTED          | `TC-HA-026`, `TC-HAS-026`, `TC-HOT-003`     |
| Nursing            | **Nurses**               | NURSING            | TESTED          | `TC-HA-016`…`020`                           |
| Nursing            | **Nurse Assignments**    | NURSING            | TESTED          | `TC-HA-021`, `TC-HNI-006`/`007`             |
| Nursing            | **Nurse Tasks**          | NURSING            | TESTED          | `TC-HA-022`, `TC-HN-023`…`025`              |
| Nursing            | **Time Slots**           | NURSING            | TESTED          | `TC-HA-023`, `TC-HNI-011`/`012`             |
| Nursing            | **Calendar**             | NURSING            | TESTED          | `TC-HA-024`, `TC-HNI-023`                   |
| Pharmacy           | **Pharmacy**             | PHARMACY           | TESTED          | `TC-HA-037`, `TC-HP-001`…`040`              |
| Inventory          | **Medicine Inventory**   | MEDICAL_INVENTORY  | TESTED          | `TC-HA-034`/`036`, `TC-HR-046`, `TC-HD-033` |
| Inventory          | **Hospital Inventory**   | HOSPITAL_INVENTORY | TESTED          | `TC-HA-035`                                 |
| Finance            | **Billing**              | BILLING            | TESTED          | `TC-HAC-018`, `TC-HB-001`…`030`             |
| Finance            | **Fees**                 | BILLING            | TESTED          | `TC-HAC-019`, `TC-HAS-028`                  |
| Reports            | **Reports & Analytics**  | REPORTS            | TESTED          | `TC-HAC-012`/`013`                          |
| Reports            | **OT Analytics**         | OT                 | TESTED          | `TC-HAC-014`, `TC-HOT-038`                  |
| Reports            | **Audit Logs**           | —                  | TESTED          | `TC-HA-033`, `TC-HAS-037`                   |
| Presets            | **Quick Notes**          | —                  | TESTED          | `TC-HA-028`                                 |
| Presets            | **Symptom Presets**      | —                  | TESTED          | `TC-HA-029`, `TC-HD-007`                    |
| Presets            | **Diagnosis Presets**    | —                  | TESTED          | `TC-HA-029`, `TC-HD-007`                    |
| Presets            | **Prescription Presets** | —                  | TESTED          | `TC-HA-030`, `TC-HD-007`                    |
| Presets            | **In-Clinic Presets**    | `inClinic` setting | TESTED          | `TC-HA-031`, `TC-HAS-004`, `TC-HD-009`      |
| Administration     | **Settings**             | —                  | TESTED          | `TC-HAS-001`…`044`                          |
| Administration     | **Support**              | —                  | TESTED          | `TC-HA-032`, `TC-SA-055`                    |

**Pharmacy-tenant-only tabs** — these five exist in `HospitalAdminDashboard` but render **only when
`hospitalType === 'PHARMACY'`**, so they are **out of scope for the Hospital tenant** and belong to
Tranche 3: `Pharmacists` (pharmacy variant), `Pharmacies`, `Suppliers`, `Billing` (pharmacy),
`Analytics` (pharmacy). Status for Hospital: **N/A — not reachable**. Recorded here so nothing
from the original 40-tab list disappears silently.

**Sidebar group headers** (`group-patient-management`, `group-rooms`, `group-critical-care`,
`group-staff`, `group-nursing`, `group-pharmacy`, `group-inventory`, `group-finance`,
`group-reports`, `group-presets`, `group-administration`) are containers, not screens — covered by
`TC-HA-046` and `TC-HAC-021`.

---

## 3. Role dashboards — every tab mapped

### RECEPTIONIST → `/hospital/receptionist` (11 tabs)

| Tab                           | Status | Cases                      |
| ----------------------------- | ------ | -------------------------- |
| Overview                      | TESTED | `TC-HR-049`                |
| Patients                      | TESTED | `TC-HR-001`…`020`          |
| Appointments                  | TESTED | `TC-HR-021`…`028`          |
| OPD                           | TESTED | `TC-HR-029`…`036`          |
| Follow-ups                    | TESTED | `TC-HR-037`/`038`          |
| IPD                           | TESTED | `TC-HR-039`…`044`          |
| Billing                       | TESTED | `TC-HR-045`, `TC-HB-*`     |
| Medicine Inventory            | TESTED | `TC-HR-046`                |
| Operation Theatre             | TESTED | `TC-HR-047`, `TC-HOT-006`  |
| ICU Dashboard / ICU Bed Board | TESTED | `TC-HR-048`, `TC-HICU-005` |

### DOCTOR → `/hospital/doctor` (11 tabs)

| Tab                           | Status | Cases             |
| ----------------------------- | ------ | ----------------- |
| Overview                      | TESTED | `TC-HD-001`       |
| Patients                      | TESTED | `TC-HD-029`/`030` |
| Appointments                  | TESTED | `TC-HD-028`       |
| OPD                           | TESTED | `TC-HD-002`…`016` |
| Follow-ups                    | TESTED | `TC-HD-031`       |
| IPD                           | TESTED | `TC-HD-023`…`027` |
| Billing                       | TESTED | `TC-HD-032`       |
| Operation Theatre             | TESTED | `TC-HD-034`/`035` |
| ICU Dashboard / ICU Bed Board | TESTED | `TC-HD-036`       |
| Medicine Inventory            | TESTED | `TC-HD-033`       |

### NURSE → `/hospital/nurse` (7 tabs + 12 patient panels)

| Surface                                                                                                                                                                      | Status | Cases                                           |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----------------------------------------------- |
| Dashboard · My Patients · My Tasks · My Shifts · My Attendance · Forms · ICU Beds                                                                                            | TESTED | `TC-HN-002`,`004`,`023`,`026`,`027`,`021`,`029` |
| Panels: Overview · Vitals · Medication · Notes · Initial Assessment · Vulnerability · Sugar Chart · Intake/Output · Ventilator · Severity Scores · Consent Forms · Documents | TESTED | `TC-HN-007`…`019`                               |
| Infusions panel                                                                                                                                                              | TESTED | `TC-HN-017`, `TC-HICU-010`                      |
| Notification bell (nurse)                                                                                                                                                    | TESTED | `TC-HN-003`                                     |

### NURSE INCHARGE → `/hospital/nurse-incharge` (10 tabs)

| Tab                          | Status                                                | Cases                       |
| ---------------------------- | ----------------------------------------------------- | --------------------------- |
| Dashboard                    | TESTED                                                | `TC-HNI-001`                |
| My Nurses                    | TESTED                                                | `TC-HNI-003`…`005`          |
| My Ward Patients             | TESTED                                                | `TC-HNI-007`, `TC-HNI-010`  |
| Unassigned Patients          | TESTED                                                | `TC-HNI-006`, `TC-HNI-008`  |
| Schedule                     | TESTED                                                | `TC-HNI-011`/`012`          |
| Attendance                   | TESTED                                                | `TC-HNI-013`                |
| Beds                         | TESTED                                                | `TC-HNI-014`…`018`          |
| ICU Beds                     | TESTED                                                | `TC-HNI-019`                |
| Coverage                     | TESTED                                                | `TC-HNI-020`…`022`          |
| Calendar                     | TESTED                                                | `TC-HNI-023`                |
| Notification bell (incharge) | **NEEDS_PRODUCT_CONFIRMATION** (`UI_WITHOUT_BACKEND`) | `TC-HNI-002`, `TC-PERM-007` |

### PHARMACIST → `/hospital/pharmacy` (13 tabs)

| Tab                 | Status | Cases             |
| ------------------- | ------ | ----------------- |
| Dashboard           | TESTED | `TC-HP-003`       |
| Billing Counter     | TESTED | `TC-HP-012`…`019` |
| Billing (history)   | TESTED | `TC-HP-020`       |
| Prescriptions       | TESTED | `TC-HP-010`/`011` |
| Inventory           | TESTED | `TC-HP-004`       |
| Purchase Management | TESTED | `TC-HP-008`/`009` |
| Suppliers           | TESTED | `TC-HP-005`       |
| Manufacturers       | TESTED | `TC-HP-006`       |
| Returns & Refunds   | TESTED | `TC-HP-021`…`024` |
| Expiry Management   | TESTED | `TC-HP-025`…`028` |
| Reports & Analytics | TESTED | `TC-HP-029`       |
| Audit Logs          | TESTED | `TC-HP-030`       |
| Settings            | TESTED | `TC-HP-031`/`032` |

### OT INCHARGE → `/hospital/ot-incharge` (2 tabs)

| Tab                                          | Status                                       | Cases                      |
| -------------------------------------------- | -------------------------------------------- | -------------------------- |
| OT Board                                     | TESTED                                       | `TC-HOT-037`               |
| Requests                                     | TESTED                                       | `TC-HOT-004`/`005`         |
| _(backend OT surface beyond these two tabs)_ | **PARTIAL — `BACKEND_UI_COVERAGE_MISMATCH`** | `TC-PERM-012`, `TC-HA-025` |

### Shared route

| Route                      | Status | Cases                                                |
| -------------------------- | ------ | ---------------------------------------------------- |
| `/ipd/:id` (REC, DOC, ADM) | TESTED | `TC-HAC-011`, `TC-HD-024`, `TC-HR-043`, `TC-ISO-020` |

---

## 4. Settings cards — all 11 mapped

| Card                                                      | Status           | Cases                                  |
| --------------------------------------------------------- | ---------------- | -------------------------------------- |
| Operations Settings                                       | TESTED           | `TC-HAS-001`…`008`                     |
| Print & Payment                                           | TESTED           | `TC-HAS-009`, `TC-HB-009`              |
| Vitals                                                    | TESTED           | `TC-HAS-010`…`012`, `TC-HAS-036`       |
| Nursing Records (Files & Access)                          | TESTED           | `TC-HAS-013`…`015`                     |
| IPD Forms (Files & Access)                                | TESTED           | `TC-HAS-017`                           |
| OT / Surgery Forms (Files & Access)                       | TESTED           | `TC-HAS-016`                           |
| Ventilator Parameters                                     | TESTED           | `TC-HAS-018`, `TC-HICU-008`            |
| Severity Scores                                           | TESTED           | `TC-HAS-019`, `TC-HICU-009`            |
| ICU Alert Thresholds                                      | TESTED           | `TC-HAS-020`, `TC-HICU-011`            |
| OT Permissions                                            | TESTED           | `TC-HAS-022`/`023`, `TC-HOT-002`       |
| OT Policies                                               | TESTED           | `TC-HAS-024`/`025`, `TC-HOT-010`/`019` |
| _(Fees — Finance tab, not the Settings grid)_             | TESTED           | `TC-HAC-019`, `TC-HAS-028`             |
| _(PlansTab — **Super Admin only**, not a tenant setting)_ | N/A for Hospital | `TC-SA-006`…`017`                      |

---

## 5. Modals, nested screens and shared components

| Component                                                                                                                                                             | Status                               | Cases                           |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ | ------------------------------- |
| `PatientModal` (Add/Edit Patient)                                                                                                                                     | TESTED                               | `TC-HR-001`…`014`               |
| `DuplicatePhoneConflictModal`                                                                                                                                         | TESTED                               | `TC-HR-005`…`008`, `TC-HX-011`  |
| `AppointmentModal`                                                                                                                                                    | TESTED                               | `TC-HR-021`…`024`, `TC-HAC-001` |
| New OPD / Case modal (reception)                                                                                                                                      | TESTED                               | `TC-HR-029`…`031`               |
| New OPD Case modal (admin)                                                                                                                                            | TESTED                               | `TC-HAC-004`/`005`              |
| `ConsultationModal`                                                                                                                                                   | TESTED                               | `TC-HD-004`…`016`, `TC-HD-042`  |
| `IpdAdmitModal`                                                                                                                                                       | TESTED                               | `TC-HR-039`/`040`, `TC-HR-044`  |
| `PatientDetailsModal` / `HistoryDrawer`                                                                                                                               | TESTED                               | `TC-HR-015`, `TC-HD-022`        |
| `PatientDocumentsPanel` / `PatientDocumentPreviewModal`                                                                                                               | TESTED                               | `TC-HD-021`, `TC-HN-019`        |
| `ConfirmationModal`                                                                                                                                                   | TESTED                               | `TC-HA-008`, `TC-HWB-004`/`009` |
| `FollowUpPanel`                                                                                                                                                       | TESTED                               | `TC-HAC-008`/`009`, `TC-HD-031` |
| `BillingTable`                                                                                                                                                        | TESTED                               | `TC-HB-015`/`016`               |
| `LowStockBanner`                                                                                                                                                      | TESTED                               | `TC-HA-034`                     |
| `MedicineAutocomplete`                                                                                                                                                | TESTED                               | `TC-HD-006`                     |
| `NotificationBell`                                                                                                                                                    | TESTED (nurse) / mismatch (incharge) | `TC-HN-003`, `TC-HNI-002`       |
| `DispenseModal`                                                                                                                                                       | TESTED                               | `TC-HP-011`                     |
| `PurchaseForm` · `CategoryForm` · `ManufacturerForm`                                                                                                                  | TESTED                               | `TC-HP-008`, `TC-HP-006`        |
| `SurgeryRequestModal` · `ScheduleSurgeryModal` · `SurgeryExecutionModal` · `SurgeryTeamModal` · `AnaesthesiaClearanceModal` · `RecoveryModal` · `DayCareSurgeryModal` | TESTED                               | `TC-HOT-004`…`020`              |
| `SurgeryFormFrame` + 15 NABH forms                                                                                                                                    | TESTED                               | `TC-HOT-023`…`036`              |
| `OtBoard` · `OtDayBoard` · `OtListPrint` · `OtAnalyticsStrip` · `OtNotesSection`                                                                                      | TESTED                               | `TC-HOT-037`/`038`              |
| `IcuDashboard` · `IcuBedBoard` · `IcuStayCard`                                                                                                                        | TESTED                               | `TC-HICU-004`/`005`             |
| `WardsAndBeds` · `WardBedsView` · `WardModal` · `BedListDrawer` · `WardCard`                                                                                          | TESTED                               | `TC-HWB-001`…`024`              |
| `NursePatientDetail` + 12 panels · `AdmissionFormModal` · `ConsentFormsPanel` · `NurseFormsView`                                                                      | TESTED                               | `TC-HN-007`…`021`               |
| `TimeSlotsView` · `HospitalCalendar`                                                                                                                                  | TESTED                               | `TC-HA-023`/`024`               |
| `HospitalInventoryTab` · `MedicineInventoryTab`                                                                                                                       | TESTED                               | `TC-HA-034`/`035`               |
| `ProfileModal`                                                                                                                                                        | TESTED                               | `TC-HAS-030`                    |
| `PresetModalShell` · `NotePresetsManager` · `PrescriptionPresetsManager` · `InClinicPresetsManager`                                                                   | TESTED                               | `TC-HA-028`…`031`               |

---

## 6. Print / PDF surfaces

| Output                                                               | Endpoint                                         | Status               | Cases                          |
| -------------------------------------------------------------------- | ------------------------------------------------ | -------------------- | ------------------------------ |
| Prescription (appointment)                                           | `/hospital/doctors/prescription/{id}/pdf`        | TESTED + drift watch | `TC-HD-017`, `TC-HD-020`       |
| Prescription (OPD)                                                   | `/hospital/doctors/prescription/opd/{opdId}/pdf` | TESTED               | `TC-HD-017`                    |
| Case paper                                                           | `/hospital/opd/{id}/pdf`                         | TESTED               | `TC-HD-018`, `TC-HAC-006`      |
| OPD documents                                                        | `/hospital/opd/{id}/documents/pdf`               | TESTED               | `TC-HAC-006`                   |
| OPD medicines / IPD medicines / IPD prescription                     | `/hospital/patients/{opd                         | ipd}/…/pdf`          | TESTED                         | `TC-HAC-006`, `TC-HD-024` |
| Billing receipt                                                      | `/hospital/billing/{id}/pdf`                     | TESTED               | `TC-HB-008`                    |
| Patients report                                                      | `/hospital/patients/report/pdf`                  | TESTED               | `TC-HAC-013`, `TC-ISO-054`     |
| Pharmacy invoice                                                     | pharmacy sale print                              | TESTED               | `TC-HP-012`/`020`              |
| 15 NABH forms (browser print)                                        | client-rendered                                  | TESTED               | `TC-HOT-023`…`036`             |
| Nursing form prints (vitals, notes, sugar, vulnerability, admission) | client-rendered                                  | TESTED               | `TC-HN-010`…`013`, `TC-HN-021` |
| OT list print                                                        | client-rendered                                  | TESTED               | `TC-HOT-037`                   |

---

## 7. Non-`TESTED` register (nothing hidden)

| Surface                                                                                                       | Status                                     | Why                                                                                                                                                                | Case                                         |
| ------------------------------------------------------------------------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------- |
| **Pathology tab**                                                                                             | `PLACEHOLDER`                              | "Pathology Module is currently under development."; gate key `PATHOLOGY` is not in `ALL_MODULES`, so it can never be enabled                                       | `TC-HA-038`                                  |
| **Lab ordering**                                                                                              | **`PARTIAL`** ⟵ _corrected at Tranche 2_   | `ConsultationModal` has a lab picker (8 tests) and `DoctorService:621-631` **creates `LabOrder` rows** that print on the case paper. **No results/management UI.** | `TC-HD-008`                                  |
| **OT Incharge dashboard**                                                                                     | `PARTIAL` / `BACKEND_UI_COVERAGE_MISMATCH` | 2 tabs vs a 10-controller OT backend; two-tab UI is the supported baseline                                                                                         | `TC-PERM-012`                                |
| **Nurse-incharge notifications**                                                                              | `UI_WITHOUT_BACKEND`                       | bell rendered, API is `NURSE`-only                                                                                                                                 | `TC-HNI-002`                                 |
| **Tenant settings endpoints**                                                                                 | `IMPLEMENTATION_DRIFT`                     | 10 endpoints with no `@PreAuthorize`                                                                                                                               | `TC-HAS-041`, `TC-API-006`                   |
| **Prescription PDF endpoints**                                                                                | `IMPLEMENTATION_DRIFT`                     | 2 endpoints with no role check                                                                                                                                     | `TC-HD-020`, `TC-API-007`                    |
| **OPD / PHARMACY module gates**                                                                               | `IMPLEMENTATION_DRIFT`                     | no `@RequireModule` → tab hidden, API open                                                                                                                         | `TC-HR-020`, `TC-HP-036`, `TC-MOD-010`/`012` |
| **IPD / wards / beds module gate**                                                                            | `PARTIAL`                                  | one `@RequireModule("IPD")`; wards and beds ungated                                                                                                                | `TC-HWB-020`, `TC-MOD-011`                   |
| **Patient reactivation**                                                                                      | not implemented                            | no `setIsActive(true)` path for Patient                                                                                                                            | `TC-VIS-003`                                 |
| **Duplicate-phone DB backstop**                                                                               | not shipped (Phase D)                      | ack columns only, no unique index                                                                                                                                  | `TC-HX-011` note                             |
| **Hospital bill cancel/refund**                                                                               | `PARTIAL`                                  | no cancellation action found                                                                                                                                       | `TC-HB-023`                                  |
| Receptionist writing clinical records                                                                         | `NEEDS_PRODUCT_CONFIRMATION`               | controllers admit `REC`                                                                                                                                            | `TC-HR-051`                                  |
| Pharmacist listing wards                                                                                      | `NEEDS_PRODUCT_CONFIRMATION`               | `Ward` GET admits `PHA`                                                                                                                                            | `TC-HWB-019`                                 |
| Doctor creating doctors                                                                                       | `NEEDS_PRODUCT_CONFIRMATION`               | `Doctor` POST admits `DOC`                                                                                                                                         | `TC-HA-010`                                  |
| Prescription PDF for pharmacist/nurse                                                                         | `NEEDS_PRODUCT_CONFIRMATION`               | same-tenant access intent unclear                                                                                                                                  | `TC-HD-020`                                  |
| ICU without IPD                                                                                               | `NEEDS_PRODUCT_CONFIRMATION`               | runtime discovery case written                                                                                                                                     | `TC-HICU-021`                                |
| Second doctor on a single-doctor tenant                                                                       | `NEEDS_PRODUCT_CONFIRMATION`               | nothing enforces a count                                                                                                                                           | `TC-MODE-006`                                |
| Doctors viewing colleagues' OPD                                                                               | `NEEDS_PRODUCT_CONFIRMATION`               | queue is own-only; tab scope unclear                                                                                                                               | `TC-HD-002`                                  |
| Admin completing a consultation (non-single-doctor)                                                           | `NEEDS_PRODUCT_CONFIRMATION`               | endpoint admits `HOSPITAL_ADMIN`                                                                                                                                   | `TC-HAC-007`                                 |
| Doctor admitting directly (bed choice)                                                                        | `NEEDS_PRODUCT_CONFIRMATION`               | `POST /hospital/ipd` admits `DOC`                                                                                                                                  | `TC-HD-027`                                  |
| Billing Handler server-side enforcement                                                                       | `NEEDS_PRODUCT_CONFIRMATION`               | may be UI-only                                                                                                                                                     | `TC-HB-026`                                  |
| Pharmacy sale ↔ hospital bill model                                                                           | `NEEDS_PRODUCT_CONFIRMATION`               | relationship undocumented                                                                                                                                          | `TC-HP-037`, `TC-HB-013`                     |
| Removing a ward's incharge with patients admitted                                                             | `NEEDS_PRODUCT_CONFIRMATION`               | behaviour unknown                                                                                                                                                  | `TC-HNI-028`                                 |
| Nurse deactivation with a live assignment                                                                     | `NEEDS_PRODUCT_CONFIRMATION`               | release behaviour unknown                                                                                                                                          | `TC-HA-020`                                  |
| Separate-nurse-login turned OFF mid-session                                                                   | `NEEDS_PRODUCT_CONFIRMATION`               | revocation behaviour unknown                                                                                                                                       | `TC-HN-001`                                  |
| NURSING revoked while a nurse is logged in                                                                    | `NEEDS_PRODUCT_CONFIRMATION`               | landing behaviour unknown                                                                                                                                          | `TC-HN-036`                                  |
| Duplicate OPD for one patient on one day                                                                      | `NEEDS_PRODUCT_CONFIRMATION`               | allowed? warned?                                                                                                                                                   | `TC-HR-035`                                  |
| Overpayment on a bill                                                                                         | `NEEDS_PRODUCT_CONFIRMATION`               | advance vs refusal                                                                                                                                                 | `TC-HB-005`                                  |
| **Pharmacy-tenant-only admin tabs** (Pharmacies, Suppliers, pharmacy Billing/Analytics, pharmacy Pharmacists) | N/A for Hospital                           | render only when `hospitalType === 'PHARMACY'`                                                                                                                     | Tranche 3                                    |
| `PHARMACY_ADMIN`, `INVENTORY_MANAGER` roles                                                                   | `DEAD_UNREACHABLE`                         | never assignable                                                                                                                                                   | `TC-PERM-008`                                |

---

## 8. Coverage assertions

| #   | Assertion                                           | Where                                                                                               |
| --- | --------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 1   | All 7 hospital roles have a dedicated document      | `01`–`06`, `08`                                                                                     |
| 2   | `isSingleDoctor` covered for hospital               | `TC-HD-048`, `TC-MODE-001`…`009`                                                                    |
| 3   | Module-enabled and module-disabled variants         | `TC-HA-002`, `TC-HAC-021`, `TC-HR-020`/`028`, `TC-HD-041`, `TC-HP-036`, `TC-HWB-020`, `TC-HICU-018` |
| 4   | Cross-role workflows                                | `11` H1–H8                                                                                          |
| 5   | Cross-module effects                                | `TC-HX-003`/`006`, `TC-HA-034`/`035`, `TC-HWB-010`                                                  |
| 6   | Permission failures (UI **and** API)                | every doc's final section; `TC-PERM-029`                                                            |
| 7   | Persistence / refresh / re-login                    | `TC-HR-058`, `TC-HD-045`, `TC-HN-037`, `TC-HNI-032`, `TC-HP-040`, `TC-HB-030`                       |
| 8   | Tenant isolation referenced from every role         | `TC-HR-052`, `TC-HD-039`, `TC-HN-034`, `TC-HNI-026`, `TC-HP-035`, `TC-HB-027`, `TC-HX-016`          |
| 9   | Parent/child shared phone                           | `TC-HR-005`…`011`, `TC-HX-011`…`013`                                                                |
| 10  | OPD → IPD → discharge → bed release                 | `TC-HX-003`, `TC-HWB-010`                                                                           |
| 11  | Prescription → pharmacy → inventory                 | `TC-HX-006`                                                                                         |
| 12  | Billing lifecycle                                   | `07` + `TC-HX-001`/`002`                                                                            |
| 13  | OT lifecycle + 15 NABH forms                        | `08` + `TC-HX-008`                                                                                  |
| 14  | ICU lifecycle                                       | `09` + `TC-HX-010`                                                                                  |
| 15  | Nurse / incharge lifecycle                          | `04`, `05`, `TC-HX-003`/`005`                                                                       |
| 16  | Settings downstream effects                         | `01c` (9-step protocol on every card)                                                               |
| 17  | Phase A concurrency limitation stated, not asserted | `TC-HX-011`, `§7` above                                                                             |
