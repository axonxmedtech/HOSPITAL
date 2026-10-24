# 01b — HOSPITAL ADMIN: CLINICAL TABS & REPORTS

**Baseline:** `aa143a7` · **Cases:** `TC-HAC-001` … `TC-HAC-026` · Tenant HOSPITAL_A · admin login

Admin sees the same clinical tabs as reception/doctor plus reports. Full workflow depth lives in
`02` (reception), `03` (doctor), `07`–`10`. This document proves the **admin variant**: visibility,
admin-only actions, reports, and the admin OPD intake modal (`New OPD Case`).

---

## A. APPOINTMENTS (admin)

### TC-HAC-001 — Admin creates appointment via AppointmentModal

`HOSPITAL_A · HOSPITAL_ADMIN · Appointments · High · Patient Management ▸ Appointments`
**Steps:** 1. **Add Appointment**. 2. Existing patient: type `Rahul` in the patient combobox, pick; doctor; Date (Day/Month/Year selects); pick a slot button `Select slot for HH:MM`; **Schedule**. 3. Toggle **New Patient (not registered yet)**; Patient Name, Phone `9900011111`, DOB, Gender; Schedule.
**Expected:** step 2 → `Appointment scheduled successfully`, status `SCHEDULED`; step 3 → **Mobile number already registered** chooser (never silent reuse); **Use This Patient** books that exact id; **Register Different Patient** creates the walk-in.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-002 — Admin appointment list, filters, status, delete

`HOSPITAL_A · HOSPITAL_ADMIN · Appointments · High · Appointments`
**Steps:** list; filter by date/doctor/status; open one; change status; **Delete** (confirm).
**Expected:** statuses `SCHEDULED`/`COMPLETED`/`CANCELLED`; delete is soft (`is_active=0`) and needs confirmation; the doctor's Appointments tab reflects it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-003 — Appointment stats card vs list

`HOSPITAL_A · HOSPITAL_ADMIN · Appointments · Medium · Appointments`
**Steps:** count today's/total/completed by hand; compare with `GET /hospital/appointments/stats` widgets.
**Expected:** equal; excludes cancelled per the widget's label (record definition).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. OPD (admin intake modal `New OPD Case`)

### TC-HAC-004 — Admin New OPD Case with existing patient

`HOSPITAL_A · HOSPITAL_ADMIN · OPD · Critical · Patient Management ▸ OPD ▸ New OPD`
**Steps:** 1. **New OPD**. 2. `Existing Patient` (default) → type in `patient name to search…`, pick `Rahul Patil`. 3. Doctor; vitals per enabled vitals; Problem / Reason; Visit Type; payment fields if Bill Payment = First. 4. **Create OPD Case**.
**Expected:** `OPD Case created successfully — ID: …`; appears in OPD list and in the doctor's queue; `POST /hospital/opd` sent once with `patientId`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-005 — Admin New OPD Case with New Patient inline + duplicate phone

`HOSPITAL_A · HOSPITAL_ADMIN · OPD · Critical · New OPD`
**Steps:** 1. Toggle **New Patient**. 2. Fill `New Patient Details` with phone `9900011111`. 3. Create OPD Case. 4. Chooser → **Use This Patient** (Aarav). 5. Repeat → **Register Different Patient**. 6. Cancel path.
**Expected:** step 4 creates OPD for Aarav's id with **no** new patient; step 5 creates a patient then the OPD; **Cancel** leaves the typed form intact; a failed OPD after a successful create flips the form to `Existing Patient` so a retry does not re-register.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-006 — OPD list, queue, filters, PDFs (admin)

`HOSPITAL_A · HOSPITAL_ADMIN · OPD · High · OPD`
**Steps:** list; filter today/history; search patient; open case; print case paper (`/hospital/opd/{id}/pdf`) and documents PDF; `IPD requests` badge.
**Expected:** correct rows; PDF shows hospital header, patient name/**PAT id**, VITAL SIGNS table built from enabled vitals; IPD-requests count matches doctor requests.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-007 — Admin cannot consult unless single-doctor

`HOSPITAL_A · HOSPITAL_ADMIN · OPD · High · OPD`
**Steps:** open a `QUEUED` case; look for Start Consultation; `POST /hospital/doctors/consultation` with admin token.
**Expected:** no consultation control in the UI for a normal admin; API — matrix says `DOC, HOSPITAL_ADMIN` → record (likely 200). `NEEDS_PRODUCT_CONFIRMATION`: should a non-single-doctor admin be able to write a consultation via API? Cross-ref `TC-MODE-003`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. FOLLOW-UPS (admin)

### TC-HAC-008 — Follow-ups tab: Due Today / Upcoming / Overdue

`HOSPITAL_A · HOSPITAL_ADMIN · Follow-ups · High · Patient Management ▸ Follow-ups`
**Pre:** doctor set follow-ups at +0, +3 and −2 days during consultations.
**Steps:** open; toggle `Show recent only`; read the three buckets.
**Expected:** `Due Today`, `Upcoming`, `Overdue` badges correct; each row shows patient, doctor, date, source OPD.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-009 — Follow-up actions: arrive / reschedule / complete / cancel

`HOSPITAL_A · HOSPITAL_ADMIN · Follow-ups · High · Follow-ups`
**Steps:** **Reschedule** to +5 days; mark arrived; complete; cancel another.
**Expected:** each POST (`/hospital/follow-ups/{medicalRecordId}/…`) updates the row; cancelled leaves the bucket; the original OPD/consultation link still opens.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. IPD (admin)

### TC-HAC-010 — IPD tab: Current vs Requested

`HOSPITAL_A · HOSPITAL_ADMIN · IPD · High · Patient Management ▸ IPD`
**Steps:** doctor requests admission; admin opens IPD → `requested` sub-tab; admit via **IpdAdmitModal** (ward, bed); `current` sub-tab.
**Expected:** request → admission; bed `GA-01` becomes occupied; row opens `/ipd/:id`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-011 — `/ipd/:id` as admin: full detail, nurse sub-tabs mirrored, actions

`HOSPITAL_A · HOSPITAL_ADMIN · IPD · High · /ipd/:id`
**Steps:** open; review Overview, vitals/notes/etc. (read-only per Files & Access), prescriptions, hospital items, follow-up, **plan discharge**, **confirm discharge**, **change bed**.
**Expected:** actions per `IpdAdmissionService` (ADM DOC for PUT); discharge flips bed to `cleaning`; final IPD bill available.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. REPORTS & ANALYTICS · OT ANALYTICS

### TC-HAC-012 — Reports & Analytics totals vs test records

`HOSPITAL_A · HOSPITAL_ADMIN · Reports · High · Reports ▸ Reports & Analytics`
**Steps:** 1. From the QA journey count: patients, OPD by day, revenue by day, top doctors. 2. Open the tab; set date range = today; read each chart/table; export if a button exists.
**Expected:** every total matches; date filter narrows correctly; empty range → empty state; REPORTS-gated (`TC-MOD-019`); receptionist/doctor token `GET /hospital/stats` → matrix `ADM DOC REC` → record.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-013 — Reports never include HOSPITAL_B

`HOSPITAL_A · HOSPITAL_ADMIN · Reports · Critical · Reports`
**Steps:** = `TC-ISO-053`/`054` from this tab.
**Expected:** B excluded from every figure and PDF.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-014 — OT Analytics

`HOSPITAL_A · HOSPITAL_ADMIN · OT Analytics · Medium · Reports ▸ OT Analytics`
**Steps:** after `08` journeys: open; read counts by status, theatre utilisation, strip (`OtAnalyticsStrip`).
**Expected:** counts equal the surgeries created; OT-gated; `OT_VIEW` permission respected.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. WARDS & BEDS · ICU · OT · BILLING · FEES (admin entry points)

### TC-HAC-015 — Wards & Beds (admin) — see `10`

`HOSPITAL_A · HOSPITAL_ADMIN · Wards & Beds · High · Rooms ▸ Wards & Beds`
**Steps:** execute `TC-HWB-001`…`010` from the admin tab.
**Expected:** as documented there.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-016 — ICU Dashboard / Bed Board (admin) — see `09`

`HOSPITAL_A · HOSPITAL_ADMIN · ICU · High · Critical Care`
**Steps:** execute `TC-HICU-001`…`006` as admin.
**Expected:** as documented there.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-017 — Operation Theatre tab (admin) — see `08`

`HOSPITAL_A · HOSPITAL_ADMIN · OT · High · Patient Management ▸ Operation Theatre`
**Steps:** execute `TC-HOT-001`…`012` as admin (board, requests, schedule, start/complete per OT Permissions).
**Expected:** admin actions per the permission grid.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-018 — Billing tab (admin) — see `07`

`HOSPITAL_A · HOSPITAL_ADMIN · Billing · High · Finance ▸ Billing`
**Steps:** BillingTable: Bill No, Patient Name, Date, Amount, Status; **Mark Paid**; **Print**.
**Expected:** as `07`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-019 — Fees tab → consultation fee applied

`HOSPITAL_A · HOSPITAL_ADMIN → REC · Fees · Critical · Finance ▸ Fees`
**Steps:** 1. Set consultation fee 777; follow-up fee 111; add custom fee `Dressing` 50 (`/settings/fees/custom`). 2. Reception creates an OPD (Bill Payment = First shows amount). 3. Doctor completes; open the bill. 4. Restore.
**Expected:** bill line = 777; follow-up visit bills 111; `Dressing` selectable as a bill item; change persists after reload and re-login. Non-admin PUT `/hospital/settings/fees` → see `TC-API-006` (drift).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## G. ADMIN CROSS-VISIBILITY & NEGATIVE

### TC-HAC-020 — Admin sees every doctor's OPD/appointments

`HOSPITAL_A · HOSPITAL_ADMIN · OPD · High · OPD / Appointments`
**Steps:** cases for Dr Meera and Dr Arjun exist; admin opens OPD and Appointments.
**Expected:** both doctors' rows visible; doctor filter works.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-021 — Admin tabs hidden on missing modules (per-tab)

`HOSPITAL_M · HOSPITAL_ADMIN · all · High · sidebar`
**Steps:** = `TC-MOD-009`; additionally confirm group headers with **no visible tabs** are hidden (e.g. Critical Care, Nursing).
**Expected:** empty groups do not render.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-022 — Doctors/Receptionists tabs gated by OPD

`HOSPITAL_A · HOSPITAL_ADMIN · Staff · Medium · sidebar`
**Steps:** plan without OPD (BILLING only) → log in.
**Expected:** Doctors and Receptionists tabs **hidden** (`requiredModule: 'OPD'`) yet `POST /hospital/doctors` → record (ungated). `NEEDS_PRODUCT_CONFIRMATION`: intended?
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-023 — Patient Management group: Pathology never listed

`HOSPITAL_A · HOSPITAL_ADMIN · Pathology · Low · sidebar`
**Steps:** expand Patient Management.
**Expected:** Patients, Appointments, OPD, IPD, Operation Theatre only; Pathology absent (`TC-HA-038`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-024 — Admin WebSocket refresh

`HOSPITAL_A · HOSPITAL_ADMIN · Realtime · Medium · Overview / OPD`
**Steps:** admin on OPD (tab 1); reception creates an OPD (tab 2).
**Expected:** tab 1 list updates without manual refresh (`REFRESH_DATA` broadcast); if WS is down, manual refresh works and no error spam.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-025 — Admin cannot reach nurse-only workspace

`HOSPITAL_A · HOSPITAL_ADMIN · Authorization · High · API`
**Steps:** admin token `GET /hospital/nurse` (NurseWorkspace, `NUR` only) and `GET /hospital/notifications`.
**Expected:** 403 both.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HAC-026 — Empty states across admin clinical tabs

`HOSPITAL_A (fresh) · HOSPITAL_ADMIN · UI · Low · all`
**Steps:** on a fresh tenant open Appointments, OPD, Follow-ups, IPD, Billing, Reports.
**Expected:** each shows an `EmptyState` component, not a blank table or spinner forever.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
