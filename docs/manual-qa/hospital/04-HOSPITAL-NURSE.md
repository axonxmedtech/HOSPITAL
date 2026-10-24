# 04 — HOSPITAL NURSE (staff nurse)

**Baseline:** `aa143a7` · **Cases:** `TC-HN-001` … `TC-HN-040` · Tenant HOSPITAL_A · login `nurse.hospa@qa.test` → `/hospital/nurse`
**Requires:** NURSING module + _Separate Nurse Login_ **ON** (`TC-HAS-006`).

**Tabs:** Dashboard · My Patients · My Tasks · My Shifts · My Attendance · Forms · ICU Beds
**`NursePatientDetail` sub-tabs (verified):** Overview · Vitals · Medication · Notes · Initial Assessment · Vulnerability Assessment · Sugar Chart · Intake / Output · Ventilator · Severity Scores · Consent Forms · Documents
**`NurseFormsView` standalone forms:** Admission Form · General Consent Form · Sugar Chart · Vulnerability Assessment
**Task states:** `PENDING` → **Start Task** (`Task started`) → **Complete Task** (`Task completed successfully`) · `COMPLETED` · `CANCELLED`; priorities `HIGH`/`MEDIUM`
**Attendance values:** `PRESENT` · `ABSENT` · `LATE` · `LEAVE` · `HOLIDAY` · `Half Day`

> Every clinical sub-tab obeys **Files & Access** (`TC-HAS-013`…`016`): Off ⇒ hidden; the other role ⇒ read-only (records visible, entry form hidden). Server-side enforced.

---

## A. ACCESS & DASHBOARD

### TC-HN-001 — Nurse login only when Separate Nurse Login is ON

`HOSPITAL_A/B · NURSE · Auth · Critical · /login/hospital`
**Steps:** 1. HOSPITAL_B (setting OFF): attempt login as its staff nurse. 2. HOSPITAL_A (ON): log in as `nurse.hospa@qa.test`. 3. Admin turns A's setting OFF while the nurse is logged in; nurse clicks a tab.
**Expected:** step 1 refused/no credentials; step 2 lands `/hospital/nurse`; step 3 — **record** whether the live session is revoked or survives (`NEEDS_PRODUCT_CONFIRMATION`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-002 — Nurse dashboard overview

`HOSPITAL_A · NURSE · Dashboard · High · Dashboard`
**Steps:** open; read counts (my patients, pending tasks, today's shift, attendance).
**Expected:** `NurseOverviewView` figures match My Patients / My Tasks / My Shifts; `GET /hospital/nurse` (workspace) is the source; on-shift state derived from today's schedule, never a manual toggle.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-003 — Notification bell (nurse — works)

`HOSPITAL_A · NURSE · Notifications · Medium · header`
**Steps:** incharge creates a task for this nurse; nurse watches the bell; open the dropdown; mark one read; **mark all read**.
**Expected:** unread count increments; `GET /hospital/notifications` and `/unread-count` return **200**; read state persists. (Contrast `TC-HN-035`.)
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. MY PATIENTS & VISIBILITY

### TC-HN-004 — Only assigned patients appear

`HOSPITAL_A · NURSE · My Patients · Critical · My Patients`
**Steps:** = `TC-VIS-013`. Two admitted patients, only one assigned to this nurse.
**Expected:** exactly one row; the unassigned patient is absent even though admitted to the same ward.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-005 — Unassigned patient by id is refused (API)

`HOSPITAL_A · NURSE · My Patients · Critical · API`
**Steps:** `GET /hospital/nurse/...` / the patient-detail endpoints using the **unassigned** patient's admission id with the nurse token; then POST vitals against it.
**Expected:** **403/404** (`NurseAccessGuard`); nothing written.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-006 — Assignment/unassignment changes visibility live

`HOSPITAL_A · NURSE · My Patients · High · My Patients`
**Steps:** incharge assigns a second patient; nurse refreshes; incharge unassigns; nurse refreshes.
**Expected:** appears then disappears; previously written records remain attributed to this nurse and stay visible to doctor/incharge.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-007 — Patient identity on the detail header

`HOSPITAL_A · NURSE · My Patients · Critical · NursePatientDetail`
**Steps:** open the assigned patient.
**Expected:** header shows patient **name**, **PAT id / UHID = the patient's identifier**, age, gender, ward, bed, admission (IPD) number. **Never the hospital's customId.**
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. CLINICAL FORMS (run the same 8-point protocol for each)

> **Per-form protocol:** ① open the sub-tab ② confirm the patient identity in the header **and** on any printed output ③ save a record with valid data ④ re-open / F5 → persists ⑤ invalid data → validation ⑥ edit if supported ⑦ print if supported (UHID = patient) ⑧ attempt the same write against a **non-assigned** patient and against **HOSPITAL_B** → 403/404.

### TC-HN-008 — Vitals

`HOSPITAL_A · NURSE · Nursing · Critical · Vitals`
**Data:** BP `120/80`, pulse 78, temp 98.6, SpO2 97, RR 16.
**Expected:** saved with timestamp and performer = this nurse (setting ON); invalid BP `80/120` rejected; correction entries supported (`VitalsService` correction flow) — record; visible to doctor in `/ipd/:id`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-009 — Medication administration

`HOSPITAL_A · NURSE · Nursing · Critical · Medication`
**Steps:** doctor added an IPD prescription; nurse records administration (`MedicationPanel`); attempt to administer a **stopped** prescription.
**Expected:** MAR row created with time and nurse; stopped prescription cannot be administered; read-only for roles without edit rights (`readOnly` prop).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-010 — Notes (Re-Assessment Sheet)

`HOSPITAL_A · NURSE · Nursing · High · Notes`
**Steps:** protocol; print the sheet.
**Expected:** saved/persisted; print shows patient UHID = `PAT id`; `NOTES` Off hides the tab for everyone (`TC-HAS-014`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-011 — Initial Assessment (incl. pain score)

`HOSPITAL_A · NURSE · Nursing · High · Initial Assessment`
**Expected:** one assessment per admission (record whether a second is blocked or versioned); pain score captured; print identity correct.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-012 — Vulnerability Assessment

`HOSPITAL_A · NURSE · Nursing · High · Vulnerability Assessment`
**Expected:** protocol; printed form shows **UHID No = patient's PRN/PAT id**, not the hospital id.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-013 — Sugar Chart

`HOSPITAL_A · NURSE · Nursing · High · Sugar Chart`
**Steps:** add entries F/PP with values; delete/soft-delete an entry; print.
**Expected:** chart lists in time order; deletion is soft (`isActive`); print identity correct.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-014 — Intake / Output (ICU)

`HOSPITAL_A · NURSE · ICU · High · Intake / Output`
**Expected:** entries with type/volume/time; totals computed; requires ICU module (`TC-MOD-017`); correction entries supported.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-015 — Ventilator panel

`HOSPITAL_A · NURSE · ICU · High · Ventilator`
**Steps:** record the parameters configured in `TC-HAS-018`, under `Ventilator Settings` and `Ventilator Observations / Measurements`.
**Expected:** only enabled parameters offered; values persist; a disabled parameter keeps historical values.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-016 — Severity Scores

`HOSPITAL_A · NURSE · ICU · High · Severity Scores`
**Steps:** record a score; a `Total only` type; a disabled type.
**Expected:** per `TC-HAS-019`; score surfaces on the ICU stay card/dashboard.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-017 — Infusions

`HOSPITAL_A · NURSE · ICU · High · (InfusionPanel)`
**Steps:** start an infusion, change its rate, stop it.
**Expected:** rate changes recorded as history; corrections supported; requires ICU.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-018 — Consent Forms tab (NABH) from the nurse side

`HOSPITAL_A · NURSE · OT · Critical · Consent Forms`
**Steps:** for a surgery patient open Consent Forms; fill and save `BLOOD_CONSENT`; print.
**Expected:** available only for a scheduled surgery; obeys OT form access (`TC-HAS-016`); print shows **UHID No = patient PRN**, never the hospital id (regression guard from Checkpoint 2A).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-019 — Documents tab (nurse)

`HOSPITAL_A · NURSE · Documents · High · Documents`
**Steps:** view documents; attempt upload.
**Expected:** GET allowed (`ADM DOC REC NUR NI`); **POST = `ADM DOC REC`** → nurse upload **403**; if an upload control is visible, that is `UI_WITHOUT_BACKEND` — record.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-020 — Files & Access read-only mode

`HOSPITAL_A · NURSE · Nursing · Critical · all sub-tabs`
**Steps:** admin sets VITALS = Doctor only; nurse opens Vitals; nurse token POSTs vitals.
**Expected:** records visible and printable, **entry form hidden**, API **403** (`assertCanEdit`). Repeat for one OT form.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-021 — Standalone Forms tab

`HOSPITAL_A · NURSE · Forms · High · Forms`
**Steps:** open **Forms**: `Admission Form`, `General Consent Form`, `Sugar Chart`, `Vulnerability Assessment`. Fill and print the Admission Form.
**Expected:** each opens for a chosen assigned patient; Admission Form is one-per-admission (`UK_admission_forms_ipd`) — a second attempt updates rather than duplicates; print identity correct.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-022 — ⭐ Wrong-patient and wrong-tenant writes

`HOSPITAL_A + B · NURSE · Nursing · Critical · API`
**Steps:** = `TC-ISO-022`: POST every nursing endpoint against (a) a non-assigned A patient, (b) a HOSPITAL_B admission id.
**Expected:** 403/404 in all cases; nothing written anywhere. Verify in both tenants.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. TASKS

### TC-HN-023 — Task lifecycle

`HOSPITAL_A · NURSE · Tasks · High · My Tasks`
**Steps:** admin/incharge creates a `HIGH` task; nurse **Start Task** (`Starting…`) then **Complete Task** (`Completing…`).
**Expected:** toasts `Task started`, `Task completed successfully`; status `PENDING` → `COMPLETED`; visible to admin/incharge; `PUT /hospital/nurse-tasks` is `NUR`-only — admin cannot complete it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-024 — Task negative paths

`HOSPITAL_A · NURSE · Tasks · Medium · My Tasks`
**Steps:** complete an already-completed task; start a `CANCELLED` task; complete another nurse's task by id (API); backend down during complete (`Failed to complete task`).
**Expected:** refused/400; other nurse's task 403; readable error and retry works.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-025 — Task list filters and empty state

`HOSPITAL_A · NURSE · Tasks · Low · My Tasks`
**Expected:** priority badges `HIGH`/`MEDIUM`; completed tasks separated or filtered; empty state when none (`Failed to load assigned tasks` only on error).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. SHIFTS & ATTENDANCE

### TC-HN-026 — My Shifts reflects the incharge's schedule

`HOSPITAL_A · NURSE · Shifts · High · My Shifts`
**Steps:** incharge fills next week's schedule (`TC-HA-023`); nurse opens My Shifts; incharge edits the shift template.
**Expected:** shifts listed with date/time/ward; **template edits rewrite future schedules only** — today's snapshot keeps the old times; error path `Failed to load your shifts`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-027 — My Attendance (read)

`HOSPITAL_A · NURSE · Attendance · Medium · My Attendance`
**Steps:** incharge marks `PRESENT` today and `LEAVE` yesterday; nurse opens My Attendance.
**Expected:** values `Present`, `Absent`, `Late`, `Leave`, `Holiday`, `Half Day` render; the nurse **cannot** edit (attendance endpoints are `ADM NI`) — confirm no edit control and API 403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-028 — On-shift derivation

`HOSPITAL_A · NURSE · Shifts · Medium · Dashboard`
**Steps:** schedule a shift covering now; then one that ended.
**Expected:** the dashboard's on-shift indicator is derived from today's schedule; there is **no manual toggle** — do not look for one.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. ICU BEDS (nurse)

### TC-HN-029 — ICU Beds tab

`HOSPITAL_A · NURSE · ICU · High · ICU Beds`
**Steps:** open; find the ICU patient assigned to this nurse; open their detail.
**Expected:** board shows ICU beds with occupancy; nurse can reach only assigned patients' records; ICU-module gated.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-030 — Nurse cannot change bed status

`HOSPITAL_A · NURSE · Beds · High · API`
**Steps:** `PUT`/`POST /hospital/beds` with the nurse token.
**Expected:** **403** (`Bed` = `ADM NI`). Bed cleaning is the incharge's action (`TC-HNI-014`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## G. PERMISSIONS · ISOLATION · UI

### TC-HN-031 — Nurse cannot reach non-nursing areas

`HOSPITAL_A · NURSE · Authorization · Critical · UI/API`
**Steps:** sidebar has no Patients/Appointments/OPD/Billing/Settings/Audit; API `GET /hospital/patients`, `/hospital/billing`, `/hospital/audit-logs`, `POST /hospital/nurses`.
**Expected:** 403 each.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-032 — Nurse is hospital-only

`HOSPITAL_A · NURSE · Authorization · High · API`
**Steps:** = `TC-API-008`: `/clinic/patients`, `/pharmacy/patients`.
**Expected:** **403** both.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-033 — ⚠️ Nurse and the settings endpoints (drift)

`HOSPITAL_A · NURSE · Settings · Critical · API`
**Steps:** = `TC-API-006` with the nurse token.
**Expected (intent):** 403. Record actual.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-034 — Nurse tenant isolation sweep

`HOSPITAL_A + B · NURSE · all · Critical · API`
**Steps:** = `TC-ISO-022`, `029`, `032`, `049`, `050` with nurse tokens of both tenants.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-035 — Cross-reference: incharge notification mismatch

`HOSPITAL_A · NURSE_INCHARGE · Notifications · Medium · header`
**Steps:** = `TC-PERM-007`.
**Expected:** nurse bell works (`TC-HN-003`); **incharge bell 403** — known `UI_WITHOUT_BACKEND`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-036 — Module off: NURSING revoked mid-session

`HOSPITAL_A · NURSE · Entitlement · Critical · UI/API`
**Steps:** Super Admin removes NURSING; nurse (logged in) clicks a tab; then re-login.
**Expected:** next request 403; after re-login the nurse has no usable dashboard — record exactly what they see (`NEEDS_PRODUCT_CONFIRMATION`: should login be blocked for a role whose module is gone?). Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-037 — Refresh / re-login persistence

`HOSPITAL_A · NURSE · Persistence · High · all`
**Steps:** after C–E, F5 and logout/login; reopen every record.
**Expected:** all persist; F5 returns to Dashboard (global rule).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-038 — Double-submit on clinical forms

`HOSPITAL_A · NURSE · Nursing · Critical · all forms`
**Steps:** double-click Save on Vitals, Notes, Sugar Chart, Medication.
**Expected:** one record each. Duplicate clinical records are a **Critical** data-integrity issue.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-039 — Nurse UI states

`HOSPITAL_A · NURSE · UI · Medium · all tabs`
**Steps:** empty (no assigned patient), loading, backend down; long patient name; modals close via ✕/ESC/backdrop.
**Expected:** EmptyState on each tab; readable errors (`Failed to load …`); no crash.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HN-040 — Responsive (nurse at a ward terminal / tablet)

`HOSPITAL_A · NURSE · UI · Low · all`
**Steps:** 768px and 1024px; open a patient and each clinical sub-tab.
**Expected:** sub-tab strip scrolls; forms usable; print preview fits.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
