# ROLE & PERMISSION MATRIX

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** 34 (`TC-PERM-001` … `TC-PERM-034`)

## How this matrix was built

Every one of the **457** backend endpoints was parsed for its effective authorization:

1. the method-level `@PreAuthorize`, else
2. the **class-level** `@PreAuthorize`, else
3. **nothing** — in which case the only gate is the `SecurityConfig` URL rule, which admits
   **all seven tenant roles** on `/hospital/**`.

Plus `@RequireModule` (plan gate) and `@TenantType` (tenant-type gate).

> **Rule for this document: the UI proves nothing.** A missing button is not authorization. Every
> ❌ in this matrix must be confirmed with a real API call per `05-API-TEST-TRACK.md`.

**Legend** — `ADM` Hospital Admin · `DOC` Doctor · `REC` Receptionist · `PHA` Pharmacist ·
`NUR` Nurse · `NI` Nurse Incharge · `OTI` OT Incharge.
`*NAME*` = an **OT permission string**, not a role (see §3).
🚩 = this controller has at least one endpoint with **no `@PreAuthorize` at all**.
⚠️ **ALL 7** = every tenant role can call it.

---

## 1. The derived matrix (backend truth)

| Controller (screen area)    | Module gate        | Tenant gate | GET (View)                                     | POST (Create)                                                                                                             | PUT (Edit)         | DELETE             |
| --------------------------- | ------------------ | ----------- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------ | ------------------ |
| **AdmissionForm**           | —                  | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | —                  | —                  |
| **Appointment**             | APPOINTMENTS       | —           | ADM DOC REC                                    | ADM REC                                                                                                                   | ADM DOC REC        | ADM                |
| **Bed**                     | —                  | —           | ADM NI                                         | ADM NI                                                                                                                    | —                  | —                  |
| **Billing**                 | —                  | —           | ADM DOC REC                                    | ADM DOC REC                                                                                                               | ADM DOC REC        | —                  |
| **ConsultationNotePreset**  | —                  | —           | ADM DOC                                        | ADM DOC                                                                                                                   | ADM DOC            | ADM DOC            |
| **Doctor** 🚩               | —                  | —           | ⚠️ **ALL 7**                                   | ADM DOC                                                                                                                   | ADM                | ADM DOC REC        |
| **Faq**                     | —                  | —           | —                                              | —                                                                                                                         | —                  | —                  |
| **FollowUp**                | —                  | —           | ADM DOC REC                                    | ADM DOC REC                                                                                                               | —                  | —                  |
| **FormAccess**              | —                  | —           | ADM                                            | —                                                                                                                         | ADM                | —                  |
| **HospitalAudit**           | —                  | —           | ADM                                            | —                                                                                                                         | —                  | —                  |
| **HospitalAuth** 🚩         | BILLING            | —           | ⚠️ **ALL 7**                                   | ⚠️ **ALL 7**                                                                                                              | ⚠️ **ALL 7**       | —                  |
| **HospitalCalendar**        | NURSING            | —           | ADM NI                                         | ADM NI                                                                                                                    | ADM NI             | ADM NI             |
| **HospitalDashboard**       | —                  | HOSPITAL    | ADM                                            | —                                                                                                                         | —                  | —                  |
| **HospitalFee**             | BILLING            | —           | ADM DOC REC                                    | ADM                                                                                                                       | ADM                | ADM                |
| **HospitalInventory**       | HOSPITAL_INVENTORY | —           | ADM DOC REC                                    | ADM DOC REC                                                                                                               | ADM DOC REC        | ADM DOC REC        |
| **HospitalService**         | —                  | —           | ADM DOC REC                                    | ADM DOC REC                                                                                                               | ADM DOC REC        | ADM DOC REC        |
| **HospitalStats**           | REPORTS            | —           | ADM DOC REC                                    | —                                                                                                                         | —                  | —                  |
| **HospitalTicket**          | —                  | —           | ADM                                            | ADM                                                                                                                       | —                  | —                  |
| **IcuAlertThreshold**       | ICU                | —           | ADM                                            | —                                                                                                                         | ADM                | —                  |
| **IcuDashboard**            | ICU                | HOSPITAL    | ADM DOC REC NUR NI                             | —                                                                                                                         | —                  | —                  |
| **IcuInfusion**             | ICU                | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | —                  | —                  |
| **IcuIo**                   | ICU                | —           | ADM DOC REC NUR NI                             | ADM DOC NUR NI                                                                                                            | —                  | —                  |
| **IcuScoreTypeSetting**     | ICU                | —           | ADM                                            | —                                                                                                                         | ADM                | —                  |
| **IcuSeverityScore**        | ICU                | —           | ADM DOC REC NUR NI                             | ADM DOC NUR NI                                                                                                            | —                  | —                  |
| **IcuStay**                 | ICU                | HOSPITAL    | ADM DOC REC NUR NI                             | —                                                                                                                         | ADM DOC            | —                  |
| **IcuVentilator**           | ICU                | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | —                  | —                  |
| **IcuVentilatorParameter**  | ICU                | —           | ADM                                            | ADM                                                                                                                       | ADM DOC REC NUR NI | —                  |
| **InitialAssessment**       | —                  | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | —                  | —                  |
| **Inventory**               | —                  | —           | ADM PHA                                        | ADM PHA                                                                                                                   | —                  | —                  |
| **IpdAdmission**            | —                  | —           | ADM DOC REC                                    | ADM DOC REC                                                                                                               | ADM DOC            | —                  |
| **ManualTask**              | NURSING            | —           | ADM                                            | ADM                                                                                                                       | NUR                | —                  |
| **Manufacturer**            | —                  | —           | ADM PHA                                        | ADM PHA                                                                                                                   | ADM PHA            | —                  |
| **MedicationAdmin**         | —                  | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | —                  | —                  |
| **Medicine**                | MEDICAL_INVENTORY  | —           | ADM DOC REC                                    | ADM DOC REC                                                                                                               | ADM DOC REC        | ADM DOC REC        |
| **MedicineCategory**        | —                  | —           | ADM PHA                                        | ADM PHA                                                                                                                   | ADM PHA            | —                  |
| **MedicineMaster**          | —                  | —           | ADM DOC PHA _INVENTORY_MANAGER,PHARMACY_ADMIN_ | ADM PHA                                                                                                                   | ADM PHA            | —                  |
| **Notification**            | NURSING            | —           | NUR                                            | —                                                                                                                         | NUR                | —                  |
| **Nurse**                   | NURSING            | —           | ADM                                            | ADM                                                                                                                       | ADM                | ADM                |
| **NurseAssignment**         | NURSING            | —           | ADM DOC REC                                    | ADM                                                                                                                       | ADM                | ADM                |
| **NurseAttendance**         | NURSING            | —           | ADM NI                                         | ADM NI                                                                                                                    | —                  | —                  |
| **NurseCoverage**           | NURSING            | —           | ADM NI                                         | ADM NI                                                                                                                    | —                  | ADM NI             |
| **NurseIncharge**           | NURSING            | —           | ADM NI                                         | ADM NI                                                                                                                    | —                  | —                  |
| **NurseSchedule**           | NURSING            | —           | ADM NI                                         | ADM NI                                                                                                                    | —                  | ADM NI             |
| **NurseWorkspace**          | NURSING            | —           | NUR                                            | —                                                                                                                         | —                  | —                  |
| **NursingNote**             | —                  | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | ADM DOC REC NUR NI | ADM DOC REC NUR NI |
| **Opd**                     | —                  | —           | ADM DOC REC                                    | ADM DOC REC                                                                                                               | —                  | —                  |
| **OtIncharge**              | —                  | HOSPITAL    | ADM                                            | ADM                                                                                                                       | ADM                | ADM                |
| **OtPermission**            | OT                 | HOSPITAL    | _OT_SETTINGS_                                  | _OT_SETTINGS_                                                                                                             | _OT_SETTINGS_      | —                  |
| **OtPolicy**                | OT                 | HOSPITAL    | _OT_SETTINGS,OT_VIEW_                          | _OT_SETTINGS_                                                                                                             | _OT_SETTINGS_      | —                  |
| **OtRoom**                  | OT                 | HOSPITAL    | _OT_VIEW_                                      | _OT_SETTINGS_                                                                                                             | _OT_SETTINGS_      | _OT_SETTINGS_      |
| **Patient**                 | —                  | —           | ADM DOC REC                                    | ADM DOC REC                                                                                                               | ADM REC            | ADM DOC            |
| **PatientDocument**         | —                  | —           | ADM DOC REC NUR NI                             | ADM DOC REC                                                                                                               | —                  | —                  |
| **Pharmacist**              | —                  | —           | ADM                                            | ADM                                                                                                                       | ADM                | ADM                |
| **Pharmacy**                | —                  | —           | ADM PHA                                        | ADM PHA                                                                                                                   | —                  | —                  |
| **PharmacyBranch**          | —                  | —           | ADM                                            | ADM                                                                                                                       | ADM                | ADM                |
| **PharmacyReports**         | —                  | —           | ADM PHA                                        | —                                                                                                                         | —                  | —                  |
| **PharmacySale**            | —                  | —           | ADM PHA                                        | ADM PHA                                                                                                                   | —                  | —                  |
| **PrescriptionPreset**      | —                  | —           | ADM DOC                                        | ADM DOC                                                                                                                   | ADM DOC            | ADM DOC            |
| **Purchase**                | —                  | —           | ADM PHA                                        | ADM PHA                                                                                                                   | —                  | —                  |
| **Receptionist**            | —                  | —           | ADM                                            | ADM                                                                                                                       | ADM                | ADM                |
| **Recovery**                | OT                 | HOSPITAL    | _OT_VIEW_                                      | _OT_RECOVERY,OT_TRANSFER,OT_VIEW_                                                                                         | —                  | —                  |
| **RecoveryBay**             | OT                 | HOSPITAL    | _OT_VIEW_                                      | _OT_VIEW_                                                                                                                 | _OT_SETTINGS_      | _OT_SETTINGS_      |
| **RecoveryBoard**           | OT                 | HOSPITAL    | _OT_VIEW_                                      | —                                                                                                                         | —                  | —                  |
| **SugarChart**              | —                  | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | ADM DOC REC NUR NI | ADM DOC REC NUR NI |
| **Supplier**                | —                  | —           | ADM PHA                                        | ADM PHA                                                                                                                   | ADM PHA            | ADM PHA            |
| **Surgery**                 | OT                 | HOSPITAL    | _OT_CREATE,OT_SCHEDULE,OT_VIEW_                | _OT_ANAESTHESIA_CLEARANCE,OT_APPROVE,OT_CLOSE,OT_COMPLETE,OT_CREATE,OT_EMERGENCY_OVERRIDE,OT_PRE_OP,OT_SCHEDULE,OT_START_ | —                  | —                  |
| **SurgeryExecution**        | OT                 | HOSPITAL    | _OT_VIEW_                                      | _OT_COMPLETE,OT_VIEW_                                                                                                     | —                  | —                  |
| **SurgeryForm**             | OT                 | HOSPITAL    | _OT_FORM_EDIT,OT_FORM_VIEW_                    | _OT_FORM_EDIT,OT_FORM_VIEW_                                                                                               | —                  | —                  |
| **SurgeryTeam**             | OT                 | HOSPITAL    | _OT_SETTINGS,OT_VIEW_                          | _OT_VIEW_                                                                                                                 | —                  | _OT_ASSIGN_TEAM_   |
| **TimeSlot**                | NURSING            | —           | ADM NI                                         | ADM REC NI                                                                                                                | ADM                | ADM                |
| **VitalSettings**           | —                  | —           | ADM                                            | ADM                                                                                                                       | ADM DOC REC NUR NI | ADM                |
| **Vitals**                  | —                  | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | ADM DOC REC NUR NI | —                  |
| **VulnerabilityAssessment** | —                  | —           | ADM DOC REC NUR NI                             | ADM DOC REC NUR NI                                                                                                        | —                  | —                  |
| **Ward**                    | —                  | —           | ADM DOC REC PHA                                | ADM                                                                                                                       | ADM                | ADM                |

> **Caveat on the two gate columns.** `Module gate` and `Tenant gate` were derived by scanning
> near each mapping. Where a controller shows a gate you did not expect (for example
> `HospitalAuth` showing `BILLING`), **treat it as unverified** and settle it with an API call.
> That is exactly what `TC-PERM-013` … `TC-PERM-020` are for.

---

## 2. What this matrix reveals — the five things to test first

| #   | Finding                                                                                                                                                                                                                                      | Cases                       |
| --- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------- |
| 1   | **`HospitalAuth` = ALL 7 on GET/POST/PUT.** Ten tenant-settings endpoints have no `@PreAuthorize`, so a nurse or receptionist may be able to read and change fees, operations, barcode, nurse-login mode, OT-incharge mode and subscription. | `TC-PERM-005`, `TC-API-006` |
| 2   | **`Doctor` GET = ALL 7.** Two prescription-PDF endpoints carry no role check while every neighbouring method does.                                                                                                                           | `TC-PERM-006`, `TC-API-007` |
| 3   | **`Notification` = NUR only**, but the bell is rendered on the **Nurse Incharge** dashboard too.                                                                                                                                             | `TC-PERM-007`               |
| 4   | **`MedicineMaster` lists two roles nobody can hold** — `PHARMACY_ADMIN`, `INVENTORY_MANAGER`.                                                                                                                                                | `TC-PERM-008`               |
| 5   | **Clinical-record controllers admit `REC`** — `NursingNote`, `SugarChart`, `Vitals`, `InitialAssessment`, `VulnerabilityAssessment`, `MedicationAdmin` all allow a **Receptionist** to create and edit.                                      | `TC-PERM-009`               |

---

## 3. OT uses a second permission system

The OT controllers do **not** gate on roles. They gate on permission strings resolved by
`security/OtPermissions` from a per-hospital `role_permissions` table, configurable in
**Settings → OT Permissions**:

`OT_VIEW` · `OT_CREATE` · `OT_SCHEDULE` · `OT_APPROVE` · `OT_START` · `OT_COMPLETE` ·
`OT_CLOSE` · `OT_PRE_OP` · `OT_ANAESTHESIA_CLEARANCE` · `OT_EMERGENCY_OVERRIDE` ·
`OT_ASSIGN_TEAM` · `OT_RECOVERY` · `OT_TRANSFER` · `OT_FORM_VIEW` · `OT_FORM_EDIT` · `OT_SETTINGS`

So "can a doctor start a surgery?" is **configuration**, not a fixed rule. Test it per hospital —
`TC-PERM-010` … `TC-PERM-012`.

---

## 4. Role → tenant-type reachability (`SecurityConfig:92-105`)

| Role           | `/platform/**` | `/hospital/**` | `/clinic/**` | `/pharmacy/**`                  |
| -------------- | -------------- | -------------- | ------------ | ------------------------------- |
| SUPER_ADMIN    | ✅             | ❌             | ❌           | ❌                              |
| HOSPITAL_ADMIN | ❌             | ✅             | ✅           | ✅                              |
| DOCTOR         | ❌             | ✅             | ✅           | ⚠️ permitted, **not supported** |
| RECEPTIONIST   | ❌             | ✅             | ✅           | ⚠️ permitted, **not supported** |
| PHARMACIST     | ❌             | ✅             | ✅           | ✅                              |
| NURSE          | ❌             | ✅             | ❌           | ❌                              |
| NURSE_INCHARGE | ❌             | ✅             | ❌           | ❌                              |
| OT_INCHARGE    | ❌             | ✅             | ❌           | ❌                              |

---

# TEST CASES

## A. Role isolation inside one tenant

### TC-PERM-001 — Receptionist cannot create staff (UI **and** API)

| Tenant     | Role         | Module          | Priority     | Endpoint                                                                                    | Method | Auth         | Expected status |
| ---------- | ------------ | --------------- | ------------ | ------------------------------------------------------------------------------------------- | ------ | ------------ | --------------- |
| HOSPITAL_A | RECEPTIONIST | User management | **Critical** | `/hospital/doctors`, `/hospital/nurses`, `/hospital/receptionists`, `/hospital/pharmacists` | POST   | receptionist | **403**         |

**Steps**

1. Log in as `rec.hospa@qa.test`. Confirm the sidebar has **no** Doctors / Nurses / Receptionists / Pharmacists tabs. _(UI check)_
2. As admin, capture the exact `POST /hospital/doctors` payload from DevTools.
3. Replay it with the receptionist token. _(API check)_
4. Repeat for the other three staff endpoints.
5. Log in as admin and confirm no new staff exists.

**Expected** — tabs hidden **and** all four POSTs return **403**. No record created.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-002 — Doctor cannot create or delete staff

| Tenant     | Role   | Module          | Priority     | Endpoint                                      | Method       | Auth   | Expected status |
| ---------- | ------ | --------------- | ------------ | --------------------------------------------- | ------------ | ------ | --------------- |
| HOSPITAL_A | DOCTOR | User management | **Critical** | `/hospital/nurses`, `/hospital/receptionists` | POST, DELETE | doctor | **403**         |

**Steps** With the doctor token, POST to `/hospital/nurses` and DELETE `/hospital/receptionists/{id}`.

**Expected** — **403** both. Note: the matrix shows Doctor **can** POST to `/hospital/doctors` (`ADM DOC`) — verify and record whether a doctor really can create another doctor. If yes, mark `NEEDS_PRODUCT_CONFIRMATION`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-003 — Nurse cannot manage nurses

| Tenant     | Role  | Module  | Priority | Endpoint           | Method            | Auth  | Expected status |
| ---------- | ----- | ------- | -------- | ------------------ | ----------------- | ----- | --------------- |
| HOSPITAL_A | NURSE | Nursing | **High** | `/hospital/nurses` | POST, PUT, DELETE | nurse | **403**         |

**Why:** `NurseController` is class-level `@PreAuthorize("hasRole('HOSPITAL_ADMIN')")`.

**Steps** With the nurse token, attempt POST, PUT `/hospital/nurses/{id}`, DELETE `/hospital/nurses/{id}`, and `POST /hospital/nurses/{id}/promote`.

**Expected** — **403** on all four. A nurse cannot promote themselves to Incharge.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-004 — Pharmacist cannot reach clinical records

| Tenant     | Role       | Module   | Priority | Endpoint                                                           | Method    | Auth       | Expected status |
| ---------- | ---------- | -------- | -------- | ------------------------------------------------------------------ | --------- | ---------- | --------------- |
| HOSPITAL_A | PHARMACIST | Clinical | **High** | `/hospital/nurse/vitals`, `/hospital/nurse/notes`, `/hospital/opd` | GET, POST | pharmacist | **403**         |

**Steps** With the pharmacist token, GET and POST each path.

**Expected** — **403**. The matrix shows these admit `ADM DOC REC NUR NI` — pharmacist is absent.
**Also record:** the matrix shows `Ward` GET admits `PHA`. Confirm `GET /hospital/wards` with the pharmacist token and note the result; if 200, mark `NEEDS_PRODUCT_CONFIRMATION`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-005 — ⚠️ Tenant settings reachable by every role

| Tenant     | Role               | Module   | Priority     | Endpoint                                         | Method   | Auth      | Expected status  |
| ---------- | ------------------ | -------- | ------------ | ------------------------------------------------ | -------- | --------- | ---------------- |
| HOSPITAL_A | REC, NUR, PHA, DOC | Settings | **Critical** | `/hospital/settings/*`, `/hospital/subscription` | GET, PUT | non-admin | **403** expected |

**This is the single highest-value permission case in the pack.** Full steps are in
[`../05-API-TEST-TRACK.md` `TC-API-006`](../05-API-TEST-TRACK.md). Execute that, then record the
outcome here as well so the matrix has a verdict.

**Expected (product intent)** — 403 for every non-admin role on every settings endpoint.
**If 200 + the value changes:** **Critical** bug.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-006 — ⚠️ Prescription PDF has no role check

| Tenant     | Role          | Module             | Priority | Endpoint                                  | Method | Auth       | Expected status |
| ---------- | ------------- | ------------------ | -------- | ----------------------------------------- | ------ | ---------- | --------------- |
| HOSPITAL_A | NUR, PHA, REC | Clinical documents | **High** | `/hospital/doctors/prescription/{id}/pdf` | GET    | non-doctor | record          |

**Steps** See `TC-API-007`. Execute with nurse, pharmacist and receptionist tokens, then with a **HOSPITAL_B** token.

**Expected** — the same-tenant results are a product question (`NEEDS_PRODUCT_CONFIRMATION`); the **cross-tenant** result must be 403/404 and is **Critical** if it is 200.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-007 — ⚠️ Notification bell on the Nurse Incharge dashboard

| Tenant     | Role           | Module        | Priority   | Endpoint                                   | Method | Auth           | Expected status |
| ---------- | -------------- | ------------- | ---------- | ------------------------------------------ | ------ | -------------- | --------------- |
| HOSPITAL_A | NURSE_INCHARGE | Notifications | **Medium** | `/hospital/notifications`, `/unread-count` | GET    | nurse incharge | **403**         |

**Context:** all four notification endpoints are `@PreAuthorize("hasRole('NURSE')")`, but
`NurseInchargeDashboard.jsx:147` renders `<NotificationBell />`. `UI_WITHOUT_BACKEND`.

**Steps**

1. Log in as `ni.hospa@qa.test`. Look at the header — is a bell icon present?
2. Open DevTools → Network and watch for `GET /hospital/notifications` and `/unread-count`.
3. Click the bell.
4. Log in as `nurse.hospa@qa.test` and repeat for comparison.

**Expected**

- Nurse: bell works, calls return **200**.
- Nurse Incharge: the bell is **visible** but its calls return **403** — a broken control in the UI.
- Record whether the failure is silent or shows an error. Raise as **Medium** (`UI_WITHOUT_BACKEND`), referencing `status/IMPLEMENTATION-STATUS.md`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-008 — Dead roles cannot be created or used

| Tenant                  | Role | Module          | Priority |
| ----------------------- | ---- | --------------- | -------- |
| HOSPITAL_A / PHARMACY_A | —    | User management | **Low**  |

**Context:** `MedicineMasterController:45,75` grants `PHARMACY_ADMIN` and `INVENTORY_MANAGER`, but
neither is ever assigned and neither appears in `SecurityConfig`. `DEAD_UNREACHABLE`.

**Steps** 1. In every staff-creation screen, inspect the role dropdown. 2. Confirm neither role is offered anywhere.

**Expected** — neither role is selectable in any tenant. **Do not write positive workflow tests for them.** If either _is_ offered, raise a **Medium** bug: a user could be created who cannot pass the URL filter and would be locked out of the whole application.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-009 — ⚠️ Receptionist writing clinical records

| Tenant     | Role         | Module             | Priority | Endpoint                                                                                 | Method | Auth         | Expected status |
| ---------- | ------------ | ------------------ | -------- | ---------------------------------------------------------------------------------------- | ------ | ------------ | --------------- |
| HOSPITAL_A | RECEPTIONIST | Nursing / Clinical | **High** | `/hospital/nurse/vitals`, `/notes`, `/sugar-chart`, `/initial-assessment`, `/medication` | POST   | receptionist | record          |

**Context:** the matrix shows all of these admit `ADM DOC REC NUR NI`. A receptionist creating a
nursing note or administering medication is clinically questionable.

**Steps**

1. Log in as `rec.hospa@qa.test` and check whether these screens are reachable in the UI at all.
2. With the receptionist token, POST a vitals record and a nursing note against an admitted patient (capture the payload from the nurse's UI first).
3. Log in as the nurse and see whether the receptionist-created record appears, and who it is attributed to.

**Expected** — record the actual behaviour. Mark **`NEEDS_PRODUCT_CONFIRMATION`**: is reception writing clinical records intended (front-desk data entry) or an over-broad grant? Do **not** file it as a bug until product answers.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## B. OT permission system

### TC-PERM-010 — OT permissions screen drives OT access

| Tenant     | Role           | Module | Priority | Endpoint                   |
| ---------- | -------------- | ------ | -------- | -------------------------- |
| HOSPITAL_A | HOSPITAL_ADMIN | OT     | **High** | `/hospital/ot/permissions` |

**Steps** 1. As admin open **Settings → OT Permissions**. 2. Record the full grid of roles × OT permissions. 3. Note which role currently holds `OT_VIEW`, `OT_CREATE`, `OT_SCHEDULE`, `OT_START`, `OT_COMPLETE`.

**Expected** — the grid renders and saves. This configuration, not the role name, decides OT access.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-011 — Revoking an OT permission denies the API

| Tenant     | Role   | Module | Priority     | Endpoint              | Method | Auth   | Expected status      |
| ---------- | ------ | ------ | ------------ | --------------------- | ------ | ------ | -------------------- |
| HOSPITAL_A | DOCTOR | OT     | **Critical** | `/hospital/surgeries` | POST   | doctor | **403** after revoke |

**Steps**

1. Confirm the doctor currently holds `OT_CREATE`; as `doc1.hospa@qa.test` raise a surgery request and capture the POST.
2. As admin, **revoke `OT_CREATE`** from DOCTOR. Save.
3. Replay the captured POST with the doctor's still-valid token.
4. Log in as the doctor and check whether the request control is hidden.
5. Re-grant and confirm it works again.

**Expected** — step 3 returns **403** even with a valid token; step 4 hides the control; step 5 restores both. **Previously created surgeries are untouched.**

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-012 — OT Incharge dashboard vs OT backend surface

| Tenant     | Role        | Module | Priority   |
| ---------- | ----------- | ------ | ---------- |
| HOSPITAL_A | OT_INCHARGE | OT     | **Medium** |

**Context:** the OT Incharge dashboard has only **two** tabs (OT Board, Requests) while the OT
backend is far larger. `BACKEND_UI_COVERAGE_MISMATCH`.

**Steps** 1. Log in as `ot.hospa@qa.test`. Record every tab and control. 2. With its token, call `GET /hospital/ot/rooms`, `/hospital/ot/recovery`, `/hospital/surgeries`. 3. Compare what the API allows against what the UI offers.

**Expected**

- The two-tab dashboard is **the supported UI for this baseline** — do not raise "missing screens" bugs.
- Where the API grants more than the UI exposes, record it as `BACKEND_UI_COVERAGE_MISMATCH` with the endpoint list. **Do not invent UI.**

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## C. Tenant-type restrictions (negative, per product decision)

### TC-PERM-013 — Nurse roles refused on `/clinic/**`

| Tenant     | Role  | Module        | Priority | Endpoint           | Method | Auth  | Expected status |
| ---------- | ----- | ------------- | -------- | ------------------ | ------ | ----- | --------------- |
| HOSPITAL_A | NURSE | Authorization | **High** | `/clinic/patients` | GET    | nurse | **403**         |

**Steps** See `TC-API-008`. With nurse, nurse-incharge and OT-incharge tokens call `/clinic/patients` and `/pharmacy/patients`.

**Expected** — **403** on all six.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-014 — Clinic cannot create nursing / OT roles

| Tenant   | Role           | Module          | Priority |
| -------- | -------------- | --------------- | -------- |
| CLINIC_A | HOSPITAL_ADMIN | User management | **High** |

**Steps** 1. Log in as `admin.clina@qa.test`. 2. Look for Nurses / Nurse Assignments / OT Incharge tabs.

**Expected** — **absent**. NURSING and OT are not sellable to CLINIC. If any tab is present, open it and record whether its API returns 403 (UI bug) or 200 (`IMPLEMENTATION_DRIFT`, **High**).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-015 — ⚠️ Clinic IPD endpoints (unsupported)

| Tenant   | Role                         | Module | Priority | Endpoint      | Method    | Auth   | Expected status  |
| -------- | ---------------------------- | ------ | -------- | ------------- | --------- | ------ | ---------------- |
| CLINIC_A | HOSPITAL_ADMIN, RECEPTIONIST | IPD    | **High** | `/clinic/ipd` | GET, POST | clinic | **403** expected |

**Steps** Execute `TC-API-009` in full and record the verdict here.

**Expected (product intent)** — 403. **Verified code position:** 13 `/clinic/ipd` endpoints carry
neither `@TenantType` nor `@RequireModule`, so a 200 is likely. Record it as
`IMPLEMENTATION_DRIFT`, **High**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-016 — ⚠️ Pharmacy OPD / IPD / Beds / Wards (unsupported)

| Tenant     | Role           | Module             | Priority | Endpoint                                   | Method | Auth           | Expected status  |
| ---------- | -------------- | ------------------ | -------- | ------------------------------------------ | ------ | -------------- | ---------------- |
| PHARMACY_A | HOSPITAL_ADMIN | OPD/IPD/Wards/Beds | **High** | `/pharmacy/opd`, `/ipd`, `/beds`, `/wards` | GET    | pharmacy admin | **403** expected |

**Steps** Execute `TC-API-010` and record the verdict here. Also confirm the pharmacy **UI** offers no OPD/IPD/Wards tabs.

**Expected (product intent)** — 403 and no tabs. Any 200 is `IMPLEMENTATION_DRIFT`, **High**; any 200 **containing data** is **Critical**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-017 — ⚠️ Doctor / Receptionist on a Pharmacy tenant (unsupported)

| Tenant     | Role                 | Module        | Priority |
| ---------- | -------------------- | ------------- | -------- |
| PHARMACY_A | DOCTOR, RECEPTIONIST | Authorization | **High** |

**Steps** Execute `TC-API-011` and record the verdict here, including the landing URL a
pharmacy-tenant DOCTOR receives.

**Expected (product intent)** — these roles are not creatable in a pharmacy tenant.
**Observed code behaviour to confirm:** `SecurityConfig:104-105` admits them, and `LandingRedirect`
sends any DOCTOR to `/hospital/doctor` regardless of tenant type.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-018 — Hospital-only dashboard endpoint rejects clinic and pharmacy

| Tenant                | Role           | Module    | Priority | Endpoint              | Method | Auth                    | Expected status |
| --------------------- | -------------- | --------- | -------- | --------------------- | ------ | ----------------------- | --------------- |
| CLINIC_A / PHARMACY_A | HOSPITAL_ADMIN | Dashboard | **High** | `/hospital/dashboard` | GET    | clinic / pharmacy admin | **403**         |

**Why:** `HospitalDashboardController` is the one non-OT/ICU controller carrying `@TenantType(HOSPITAL)`.

**Steps** Call `GET /hospital/dashboard` with a CLINIC_A admin token, then a PHARMACY_A admin token, then a HOSPITAL_A admin token.

**Expected** — **403**, **403**, **200**. This is the reference example of `@TenantType` working correctly; compare it against `TC-PERM-015`/`016` where the annotation is absent.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-019 — OT endpoints reject non-hospital tenants

| Tenant   | Role           | Module | Priority | Endpoint                                    | Method | Auth         | Expected status |
| -------- | -------------- | ------ | -------- | ------------------------------------------- | ------ | ------------ | --------------- |
| CLINIC_A | HOSPITAL_ADMIN | OT     | **High** | `/hospital/surgeries`, `/hospital/ot/rooms` | GET    | clinic admin | **403**         |

**Steps** With a CLINIC_A admin token call both endpoints. Repeat with PHARMACY_A.

**Expected** — **403** (OT controllers carry `@TenantType(HOSPITAL)` **and** `@RequireModule("OT")`; both gates should fire).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-020 — ICU endpoints reject non-hospital tenants

| Tenant   | Role           | Module | Priority | Endpoint                                          | Method | Auth         | Expected status |
| -------- | -------------- | ------ | -------- | ------------------------------------------------- | ------ | ------------ | --------------- |
| CLINIC_A | HOSPITAL_ADMIN | ICU    | **High** | `/hospital/icu`, `/hospital/icu/alert-thresholds` | GET    | clinic admin | **403**         |

**Steps** As above with CLINIC_A and PHARMACY_A tokens.

**Expected** — **403**. ICU is hospital-only and additionally `@RequireModule("ICU")`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## D. Per-action verification (View / Create / Edit / Delete / Print / Download)

### TC-PERM-021 — Patients: full CRUD matrix by role

| Tenant     | Role | Module   | Priority     | Endpoint             |
| ---------- | ---- | -------- | ------------ | -------------------- |
| HOSPITAL_A | all  | Patients | **Critical** | `/hospital/patients` |

**Derived expectation:** GET `ADM DOC REC` · POST `ADM DOC REC` · PUT `ADM REC` · DELETE `ADM DOC`.

**Steps** — for **each** of the seven roles, run all four verbs with that role's token and fill the grid:

| Role           | GET | POST | PUT | DELETE |
| -------------- | --- | ---- | --- | ------ |
| HOSPITAL_ADMIN | ___ | ___  | ___ | ___    |
| DOCTOR         | ___ | ___  | ___ | ___    |
| RECEPTIONIST   | ___ | ___  | ___ | ___    |
| PHARMACIST     | ___ | ___  | ___ | ___    |
| NURSE          | ___ | ___  | ___ | ___    |
| NURSE_INCHARGE | ___ | ___  | ___ | ___    |
| OT_INCHARGE    | ___ | ___  | ___ | ___    |

**Expected** — 200 where the derived matrix says allowed, **403** everywhere else. Note especially whether **DOCTOR can PUT** (matrix says no) and whether **NURSE can GET** (matrix says no).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-022 — Beds: only Admin and Nurse Incharge

| Tenant     | Role          | Module | Priority | Endpoint         | Method    | Auth       | Expected status |
| ---------- | ------------- | ------ | -------- | ---------------- | --------- | ---------- | --------------- |
| HOSPITAL_A | REC, DOC, NUR | Beds   | **High** | `/hospital/beds` | GET, POST | non-ADM/NI | **403**         |

**Steps** Call GET and POST `/hospital/beds` with receptionist, doctor and nurse tokens.

**Expected** — **403**. Then confirm reception can still **admit a patient to a bed** through the IPD flow (which goes via `/hospital/ipd`, where `REC` is allowed). If reception cannot admit, that is a **Critical** workflow break — record it.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-023 — Billing: who may view, create, edit

| Tenant     | Role | Module  | Priority | Endpoint            |
| ---------- | ---- | ------- | -------- | ------------------- |
| HOSPITAL_A | all  | Billing | **High** | `/hospital/billing` |

**Derived expectation:** GET/POST/PUT `ADM DOC REC`; no DELETE for anyone.

**Steps** Run GET, POST, PUT, DELETE with each of the seven tokens. Record the grid as in `TC-PERM-021`.

**Expected** — nurse, nurse-incharge, OT-incharge and pharmacist all **403**. **DELETE returns 403/405 for everyone** — bills must not be deletable.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-024 — Audit logs are admin-only

| Tenant     | Role      | Module | Priority     | Endpoint               | Method | Auth               | Expected status |
| ---------- | --------- | ------ | ------------ | ---------------------- | ------ | ------------------ | --------------- |
| HOSPITAL_A | non-admin | Audit  | **Critical** | `/hospital/audit-logs` | GET    | REC, DOC, NUR, PHA | **403**         |

**Steps** Call it with each non-admin token, then with the admin token.

**Expected** — **403** for all four; **200** for admin. An audit trail a non-admin can read (or that shows another tenant's entries) is **Critical**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-025 — Print / download authorization

| Tenant     | Role | Module                       | Priority |
| ---------- | ---- | ---------------------------- | -------- |
| HOSPITAL_A | all  | Clinical documents / Billing | **High** |

**Steps** For each downloadable artefact — prescription PDF, case paper, OPD medicines PDF, IPD prescription PDF, billing receipt, patient report PDF — attempt the download as **every** role and record status + whether a file actually downloads.

| Artefact           | ADM | DOC | REC | PHA | NUR | NI  | OTI |
| ------------------ | --- | --- | --- | --- | --- | --- | --- |
| Prescription PDF   | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| Case paper         | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| Billing receipt    | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| Patient report PDF | ___ | ___ | ___ | ___ | ___ | ___ | ___ |

**Expected** — a role denied the underlying record must also be denied its PDF. **A PDF endpoint is a data endpoint.** Cross-reference `TC-PERM-006`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-026 — Form Access configuration is admin-only and enforced server-side

| Tenant     | Role                            | Module         | Priority | Endpoint                |
| ---------- | ------------------------------- | -------------- | -------- | ----------------------- |
| HOSPITAL_A | HOSPITAL_ADMIN / NURSE / DOCTOR | Files & Access | **High** | `/hospital/form-access` |

**Steps**

1. As admin open **Settings → Files & Access**. Set `VITALS` to **DOCTOR only**. Save.
2. As the nurse, open an admitted patient → Vitals. Confirm the **entry form is hidden** but existing records remain visible.
3. With the nurse token, POST a vitals record anyway (payload captured earlier).
4. Set `VITALS` **Off** entirely; check both the doctor and the nurse view.
5. Restore to **BOTH**.

**Expected**

- Step 3 must return **403** — enforcement is server-side (`FormAccessService.assertCanEdit`), not just a hidden form. A 200 here is a **High** bug.
- Step 4: the tab is hidden for everyone.
- `PUT /hospital/form-access` with a non-admin token: **403**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-027 — Nurse ward scope (Nurse Incharge sees only their wards)

| Tenant     | Role           | Module  | Priority |
| ---------- | -------------- | ------- | -------- |
| HOSPITAL_A | NURSE_INCHARGE | Nursing | **High** |

**Preconditions:** create a second incharge and give them a different ward, so two incharges exist with disjoint wards.

**Steps** 1. Log in as each incharge. 2. Open **My Ward Patients** and **Beds**. 3. With incharge #1's token, request a patient/bed belonging to incharge #2's ward by id.

**Expected** — each incharge sees only their own wards; the cross-ward API request is **403/404**. Ward scope is enforced by `NurseInchargeGuard`, not just by filtering the list.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-028 — Staff nurse sees only assigned patients

| Tenant     | Role  | Module  | Priority |
| ---------- | ----- | ------- | -------- |
| HOSPITAL_A | NURSE | Nursing | **High** |

**Steps** 1. As incharge, assign patient P1 to `nurse.hospa@qa.test` and leave another admitted patient unassigned. 2. Log in as the nurse → **My Patients**. 3. With the nurse token, request the unassigned patient by id.

**Expected** — only the assigned patient is listed; the unassigned one returns **403/404** (`NurseAccessGuard`).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

## E. UI-vs-API consistency sweep

### TC-PERM-029 — Every hidden tab is backed by a server refusal

| Tenant     | Role | Module        | Priority     |
| ---------- | ---- | ------------- | ------------ |
| HOSPITAL_A | all  | Authorization | **Critical** |

**Method** — for each role: list the tabs the role does **not** see; for each such area pick its
primary GET endpoint from §1 and call it with that role's token.

| Role         | Hidden tab | Endpoint tested               | Status | Verdict |
| ------------ | ---------- | ----------------------------- | ------ | ------- |
| RECEPTIONIST | Doctors    | `GET /hospital/doctors`       | ___    | ___     |
| RECEPTIONIST | Audit Logs | `GET /hospital/audit-logs`    | ___    | ___     |
| RECEPTIONIST | Settings   | `GET /hospital/settings/fees` | ___    | ___     |
| DOCTOR       | Nurses     | `GET /hospital/nurses`        | ___    | ___     |
| NURSE        | Billing    | `GET /hospital/billing`       | ___    | ___     |
| PHARMACIST   | OPD        | `GET /hospital/opd`           | ___    | ___     |
| OT_INCHARGE  | Patients   | `GET /hospital/patients`      | ___    | ___     |

**Expected** — **403** in every row. Any **200** means the UI is the only protection: raise
**Critical**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-030 — Every visible tab actually works

| Tenant     | Role | Module        | Priority |
| ---------- | ---- | ------------- | -------- |
| HOSPITAL_A | all  | Authorization | **High** |

**Steps** For each role, click **every** tab it can see, with DevTools → Network open.

**Expected** — no tab returns **403** or **500**. A visible tab whose API refuses it is
`UI_WITHOUT_BACKEND` (like `TC-PERM-007`) and should be raised as **Medium**, listing the tab and
the endpoint.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-031 — Disabled buttons are also enforced

| Tenant     | Role                | Module        | Priority |
| ---------- | ------------------- | ------------- | -------- |
| HOSPITAL_A | RECEPTIONIST, NURSE | Authorization | **High** |

**Steps** Find three controls that render **disabled** for a role (rather than hidden). For each, capture the request the enabled version would send and replay it with that role's token.

**Expected** — **403** each time. A disabled button is a hint, not a control.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-032 — Role change takes effect on the next login only

| Tenant     | Role                   | Module        | Priority |
| ---------- | ---------------------- | ------------- | -------- |
| HOSPITAL_A | NURSE → NURSE_INCHARGE | Authorization | **High** |

**Steps** See `TC-AUTH-020`. After promotion, confirm the **old** token is rejected and the **new** login lands on the incharge dashboard with incharge permissions.

**Expected** — no window in which the old role's token still grants the old permissions.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-033 — Deactivated staff lose API access immediately

| Tenant     | Role   | Module        | Priority     | Expected status |
| ---------- | ------ | ------------- | ------------ | --------------- |
| HOSPITAL_A | DOCTOR | Authorization | **Critical** | **401/403**     |

**Steps** 1. Log in as `doc1.hospa@qa.test`; keep the token. 2. As admin, deactivate that doctor. 3. Replay a request with the captured token. 4. Attempt a fresh login.

**Expected** — step 3 refused, step 4 refused. **Record whether step 3 still returns 200**: if the token survives deactivation until expiry (up to 12 hours), that is a **Critical** finding, because deactivation is the mechanism used when someone leaves.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-PERM-034 — Single-doctor admin gets both role surfaces (and nothing more)

| Tenant      | Role                              | Module                        | Priority |
| ----------- | --------------------------------- | ----------------------------- | -------- |
| CLINIC_SOLO | HOSPITAL_ADMIN + `isSingleDoctor` | Authorization / Special modes | **High** |

**Steps** 1. Log in as `admin.clinsolo@qa.test`. 2. Reach both `/hospital/doctor` and `/hospital/admin`. 3. With that token, call a doctor-only endpoint and an admin-only endpoint. 4. Call a **nurse-only** endpoint.

**Expected** — doctor and admin endpoints succeed; the nurse endpoint returns **403**. The dual role must grant exactly two role surfaces, not blanket access. Full coverage in `SPECIAL-MODES.md`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______
