# 08 — HOSPITAL OPERATION THEATRE

**Baseline:** `aa143a7` · **Cases:** `TC-HOT-001` … `TC-HOT-038` · Tenant HOSPITAL_A · OT module + `@TenantType(HOSPITAL)`

**Lifecycle (`SurgeryStatus`):** `REQUESTED` → `APPROVED` → `SCHEDULED` → `PRE_OP` → `IN_PROGRESS` → `COMPLETED` → `CLOSED`; plus `CANCELLED`, `POSTPONED`.
**Actions (`/hospital/surgeries/{publicId}/…`):** `approve`, `schedule`, `pre-op`, `anaesthesia-clearance`, `emergency-override`, `start`, `complete`, `cancel`, `close`, `postpone`; plus `/milestones`, `/who-checklist/{phase}/sign`, `/operative-note`.
**Authorization is by OT permission, not role** — 16 keys configured per hospital in **Settings ▸ OT Permissions** (`TC-HAS-022`). Gates are further shaped by **OT Policies** (`TC-HAS-024`).
**Screens:** Admin ▸ Operation Theatre / OT Theatres / OT Analytics · Reception ▸ Operation Theatre · OT Incharge ▸ **OT Board**, **Requests** · Doctor ▸ request from an IPD case · Nurse ▸ **Consent Forms**.

**15 NABH forms** (`surgeryFormsRegistry.jsx` `type` → title):
`BLOOD_CONSENT` Blood Consent Form · `IO_CHART` Input & Output Chart · `GA_CONSENT` Consent Form for General Anaesthesia · `DRUG_ADMIN_SHEET` Drug Administration Sheet · `INFORMED_CONSENT_ANAES` Informed Consent — Anaesthesia · `INFORMED_CONSENT_SURGERY` Informed Consent — Surgery · `PRE_OP_CHECKLIST` Pre-Operative Checklist · `PRE_ANAES_EVAL` Pre-Anaesthesia Evaluation · `GENERAL_ANAESTHESIA` General Anaesthesia · `SURGICAL_CASE_RECORD` Surgical Case Record · `POST_OP_CARE_PLAN` Post-Operative Care Plan · `POST_OP_CHECKLIST_10` Post-Operative Checklist · `POST_OP_CHECKLIST_02` Post-Operative Checklist (+ I/O page) · `POST_ANAES_RECOVERY` Post-Anaesthesia Recovery Chart · `WHO_CHECKLIST` WHO Surgical Safety Checklist

---

## A. SETUP & PERMISSIONS

### TC-HOT-001 — OT module and tenant gate

`HOSPITAL_A / CLINIC_A / HOSPITAL_M · HOSPITAL_ADMIN · OT · High · sidebar/API`
**Steps:** A (OT on) shows OT tabs; Hospital M (no OT) hides them and `GET /hospital/surgeries` → 403; CLINIC_A → 403 (`TC-PERM-019`).
**Expected:** OT is the best-gated area — both `@RequireModule("OT")` and `@TenantType(HOSPITAL)` fire.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-002 — Record the OT Permissions default grid

`HOSPITAL_A · HOSPITAL_ADMIN · OT · High · Settings ▸ OT Permissions`
**Steps:** open the grid (rows = 16 permissions, columns = Hospital Admin, Doctor, Receptionist, Nurse, Nurse Incharge, OT Incharge); write it down; **Reset to defaults**.
**Expected:** `OT permissions reset to defaults`; the recorded grid becomes the expected baseline for every case below.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-003 — OT theatres/rooms available for scheduling

`HOSPITAL_A · HOSPITAL_ADMIN · OT · High · OT Theatres`
**Steps:** = `TC-HA-026` / `TC-HAS-026`: create `OT Room 1`; check the schedule picker; deactivate; re-check.
**Expected:** `Theatre added`/`updated`/`removed`; `AVAILABLE` status; removed rooms leave scheduled surgeries intact.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. REQUEST → SCHEDULE

### TC-HOT-004 — Doctor raises a surgery request

`HOSPITAL_A · DOCTOR · OT · Critical · SurgeryRequestModal`
**Steps:** = `TC-HD-034`.
**Expected:** `Surgery request created`; status `REQUESTED`; appears on the OT Board and in OT Incharge ▸ Requests; blank procedure → `Procedure name is required`; requires `OT_CREATE`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-005 — Approve (policy-dependent)

`HOSPITAL_A · HOSPITAL_ADMIN/OTI · OT · High · OT Board`
**Steps:** with `APPROVAL_MODE` requiring approval, **approve** the request; then set the policy to not require approval and raise another request.
**Expected:** `REQUESTED` → `APPROVED` when required; when not required, scheduling is possible straight from `REQUESTED`. Requires `OT_APPROVE`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-006 — Schedule a surgery

`HOSPITAL_A · RECEPTIONIST/OTI · OT · Critical · ScheduleSurgeryModal`
**Steps:** **Schedule**: surgeon (a doctor **or** `Other` free-text operator), optional anaesthetist, theatre/room, date and interval, priority `ELECTIVE`.
**Expected:** `Surgery scheduled` (`Scheduling…`); status `SCHEDULED`; appears on `OtDayBoard` in the chosen slot; requires `OT_SCHEDULE`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-007 — Room double-booking

`HOSPITAL_A · RECEPTIONIST · OT · Critical · ScheduleSurgeryModal`
**Steps:** schedule a second surgery in the same room with an overlapping interval, via UI and via API.
**Expected:** refused with a clear message; no overlapping bookings on the board.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-008 — Schedule validation

`HOSPITAL_A · RECEPTIONIST · OT · High · ScheduleSurgeryModal`
**Steps:** past date/time; no room; no surgeon; end before start.
**Expected:** each refused with a field message; nothing scheduled.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-009 — Surgery team capture

`HOSPITAL_A · OTI · OT · High · SurgeryTeamModal`
**Steps:** with `TEAM_CAPTURE` policy on, add team members by `CaseRoles`; save; remove one (`OT_ASSIGN_TEAM`).
**Expected:** team persists on the surgery and prints on the Surgical Case Record; removal requires `OT_ASSIGN_TEAM`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PRE-OP GATES → START → COMPLETE

### TC-HOT-010 — Pre-op checklist gate

`HOSPITAL_A · OTI/DOCTOR · OT · Critical · OT Board`
**Steps:** with `PRE_OP_CHECKLIST` required (Corporate/NABH preset), attempt **Start** without completing pre-op; then mark pre-op and retry.
**Expected:** first attempt refused with the reason; after pre-op, start succeeds. With the Small-hospital preset the gate is advisory — record the difference (`PreOpSafetyService`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-011 — Anaesthesia clearance gate

`HOSPITAL_A · DOCTOR · OT · Critical · AnaesthesiaClearanceModal`
**Steps:** with `ANAESTHESIA_CLEARANCE` required, start without clearance; record clearance (`OT_ANAESTHESIA_CLEARANCE`); start again.
**Expected:** refused then allowed; clearance recorded with actor and time.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-012 — Emergency override

`HOSPITAL_A · HOSPITAL_ADMIN · OT · Critical · OT Board`
**Steps:** on a blocked start use **emergency override** (`OT_EMERGENCY_OVERRIDE`); provide a reason if demanded.
**Expected:** start proceeds; the override is recorded and auditable; a user without the permission cannot override.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-013 — Start surgery

`HOSPITAL_A · OTI · OT · Critical · SurgeryExecutionModal`
**Steps:** **Start** (`OT_START`); observe the board and the OT bed/room.
**Expected:** status `IN_PROGRESS`; the OT ward's single bed becomes occupied if an OT ward is used; milestones can be recorded.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-014 — Milestones and WHO checklist signing

`HOSPITAL_A · OTI/NURSE · OT · High · SurgeryExecutionModal`
**Steps:** record milestones (`/milestones`); sign each WHO phase (`/who-checklist/{phase}/sign`) with `WHO_CHECKLIST_MODE` on; attempt to sign a phase twice.
**Expected:** each phase signs once with signer and timestamp; re-signing refused or versioned (record); completion may be blocked until all phases signed under NABH.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-015 — Operative note

`HOSPITAL_A · DOCTOR · OT · High · SurgeryExecutionModal`
**Steps:** write and save the operative note (`/operative-note`); reopen.
**Expected:** persists; appears on the Surgical Case Record print.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-016 — Complete surgery

`HOSPITAL_A · OTI · OT · Critical · SurgeryExecutionModal`
**Steps:** **Complete** (`OT_COMPLETE`); check the board, the OT bed and the IPD case.
**Expected:** status `COMPLETED`; OT bed → **cleaning** (not straight to available); the IPD case reflects the surgery; `COMPLETED` appears on the board.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-017 — Recovery tracking

`HOSPITAL_A · OTI/NURSE · OT · High · RecoveryModal / Recovery Board`
**Steps:** with `RECOVERY_TRACKING` on, move the patient to a recovery bay (`/hospital/ot/recovery-bays`, `OT_RECOVERY`/`OT_TRANSFER`); view the Recovery Board; discharge from recovery.
**Expected:** bay occupancy updates; board shows the patient; transfer back to the ward works. With the policy off the step is absent.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-018 — Close surgery

`HOSPITAL_A · HOSPITAL_ADMIN · OT · High · OT Board`
**Steps:** **close** a completed surgery (`OT_CLOSE`); try to edit forms afterwards.
**Expected:** status `CLOSED`; record whether forms become read-only (expected) — document.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. CANCEL · POSTPONE · INVALID TRANSITIONS

### TC-HOT-019 — Cancel with a reason

`HOSPITAL_A · OTI · OT · High · OT Board`
**Steps:** cancel a `SCHEDULED` elective surgery with `CANCELLATION_REASON` required (`TC-HAS-025`); then an emergency one where it is not required.
**Expected:** reason demanded for elective, optional for emergency; status `CANCELLED`; room slot freed; reason from `CancellationReasons` recorded.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-020 — Postpone

`HOSPITAL_A · OTI · OT · Medium · OT Board`
**Steps:** **postpone** a scheduled surgery; reschedule it.
**Expected:** status `POSTPONED`; slot freed; rescheduling returns it to `SCHEDULED` (`SCHEDULED → POSTPONED` and `SCHEDULED → SCHEDULED` are valid per the state machine).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-021 — Invalid transitions

`HOSPITAL_A · OTI · OT · Critical · API`
**Steps:** complete a `REQUESTED` surgery; start a `CANCELLED` one; cancel a `COMPLETED` one; approve twice; start twice.
**Expected:** every one refused by `SurgeryStateMachine` with 400/409; no double start; no status regression.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-022 — Stale command detection

`HOSPITAL_A · OTI ×2 · OT · High · OT Board`
**Steps:** two tabs open the same scheduled surgery; tab 1 reschedules; tab 2 (stale) reschedules.
**Expected:** tab 2 is refused as stale (`lifecycle_version`), not silently applied.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. ⭐ THE 15 NABH FORMS

> **Run this protocol for each of the 15 forms.** Record one row per form in the table at the end.
> ① Nurse (or the role holding `OT_FORM_EDIT`) opens the surgery patient ▸ **Consent Forms** ▸ the form.
> ② **Header identity check:** the form shows the **patient's** name and **UHID No / PRN = the patient's PAT id** — _never the hospital's customId_. (This was a real defect fixed in Checkpoint 2A; it is the single most important assertion here.)
> ③ Fill the required fields; **Save**.
> ④ Re-open / F5 → values persist.
> ⑤ Edit and save again (if supported).
> ⑥ **Print** → the printed output repeats the same patient identity, the hospital header, the surgery and the date.
> ⑦ With the form's access set to the other role (`TC-HAS-016`), the entry form is hidden but records stay visible/printable; the API write returns **403**.
> ⑧ The same form for a **HOSPITAL_B** surgery id → **403/404** (`TC-ISO-032`).

### TC-HOT-023 — Blood Consent Form (`BLOOD_CONSENT`)

`HOSPITAL_A · NURSE · OT · Critical · Consent Forms`
**Expected:** protocol ①–⑧ pass; UHID = patient PRN.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-024 — Consent for General Anaesthesia (`GA_CONSENT`)

`HOSPITAL_A · NURSE · OT · Critical · Consent Forms` — protocol ①–⑧.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-025 — Informed Consent — Anaesthesia (`INFORMED_CONSENT_ANAES`)

`HOSPITAL_A · NURSE · OT · Critical · Consent Forms` — protocol ①–⑧.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-026 — Informed Consent — Surgery (`INFORMED_CONSENT_SURGERY`)

`HOSPITAL_A · NURSE · OT · Critical · Consent Forms` — protocol ①–⑧.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-027 — Pre-Anaesthesia Evaluation (`PRE_ANAES_EVAL`)

`HOSPITAL_A · DOCTOR · OT · High · Consent Forms` — protocol; feeds the anaesthesia-clearance gate (`TC-HOT-011`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-028 — Pre-Operative Checklist (`PRE_OP_CHECKLIST`)

`HOSPITAL_A · NURSE · OT · Critical · Consent Forms` — protocol; feeds the pre-op gate (`TC-HOT-010`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-029 — WHO Surgical Safety Checklist (`WHO_CHECKLIST`)

`HOSPITAL_A · OTI/NURSE · OT · Critical · Consent Forms` — protocol + phase signing (`TC-HOT-014`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-030 — General Anaesthesia record (`GENERAL_ANAESTHESIA`)

`HOSPITAL_A · DOCTOR · OT · High · Consent Forms` — protocol.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-031 — Surgical Case Record (`SURGICAL_CASE_RECORD`)

`HOSPITAL_A · DOCTOR · OT · Critical · Consent Forms` — protocol; also shows the team (`TC-HOT-009`) and operative note (`TC-HOT-015`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-032 — Drug Administration Sheet (`DRUG_ADMIN_SHEET`)

`HOSPITAL_A · NURSE · OT · Critical · Consent Forms` — protocol; drug entries with time and signature.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-033 — Input & Output Chart (`IO_CHART`)

`HOSPITAL_A · NURSE · OT · High · Consent Forms` — protocol; totals computed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-034 — Post-Operative Checklist (`POST_OP_CHECKLIST_10`) and (+ I/O page) (`POST_OP_CHECKLIST_02`)

`HOSPITAL_A · NURSE · OT · High · Consent Forms`
**Steps:** protocol for **both** variants; confirm they are two distinct records and the `_02` variant carries the extra I/O page.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-035 — Post-Operative Care Plan (`POST_OP_CARE_PLAN`)

`HOSPITAL_A · NURSE · OT · High · Consent Forms` — protocol.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-036 — Post-Anaesthesia Recovery Chart (`POST_ANAES_RECOVERY`)

`HOSPITAL_A · NURSE · OT · High · Consent Forms` — protocol; ties to recovery tracking (`TC-HOT-017`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

**Per-form recording table**

| Form                     | Identity OK (UHID = patient) | Save | Persist | Print | Access-off hides form | B-tenant 403/404 |
| ------------------------ | ---------------------------- | ---- | ------- | ----- | --------------------- | ---------------- |
| BLOOD_CONSENT            |                              |      |         |       |                       |                  |
| GA_CONSENT               |                              |      |         |       |                       |                  |
| INFORMED_CONSENT_ANAES   |                              |      |         |       |                       |                  |
| INFORMED_CONSENT_SURGERY |                              |      |         |       |                       |                  |
| PRE_ANAES_EVAL           |                              |      |         |       |                       |                  |
| PRE_OP_CHECKLIST         |                              |      |         |       |                       |                  |
| WHO_CHECKLIST            |                              |      |         |       |                       |                  |
| GENERAL_ANAESTHESIA      |                              |      |         |       |                       |                  |
| SURGICAL_CASE_RECORD     |                              |      |         |       |                       |                  |
| DRUG_ADMIN_SHEET         |                              |      |         |       |                       |                  |
| IO_CHART                 |                              |      |         |       |                       |                  |
| POST_OP_CHECKLIST_10     |                              |      |         |       |                       |                  |
| POST_OP_CHECKLIST_02     |                              |      |         |       |                       |                  |
| POST_OP_CARE_PLAN        |                              |      |         |       |                       |                  |
| POST_ANAES_RECOVERY      |                              |      |         |       |                       |                  |

---

## F. BOARD · ANALYTICS · AUDIT · ISOLATION

### TC-HOT-037 — OT Board, day board, waitlist, print

`HOSPITAL_A · OTI · OT · High · OT Board`
**Steps:** review `OtBoard` statuses `REQUESTED`/`SCHEDULED`/`COMPLETED` and the `Preferred` marker; `OtDayBoard` by date; the waiting list (a query, not a status); **OT list print** (`OtListPrint`).
**Expected:** board matches the surgeries created; printed list shows patient identities correctly; requires `OT_VIEW`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HOT-038 — OT analytics, audit and tenant isolation

`HOSPITAL_A + B · HOSPITAL_ADMIN/OTI · OT · Critical · OT Analytics / API`
**Steps:** 1. OT Analytics counts vs the surgeries created (`TC-HAC-014`). 2. Audit Logs contain the state transitions (`surgery_state_transitions`). 3. = `TC-ISO-030`…`033` with B's tokens.
**Expected:** analytics reconcile; every transition audited with actor; B cannot read, schedule, start, complete or fetch A's surgeries or forms.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
