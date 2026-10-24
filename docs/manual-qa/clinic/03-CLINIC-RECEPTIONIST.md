# 03 — CLINIC RECEPTIONIST

**Baseline:** `aa143a7` · **Cases:** `TC-CR-001` … `TC-CR-022` · Tenant CLINIC_A · `rec.clina@qa.test` → `/hospital/receptionist`

**Tabs (clinic):** Overview · Patients · Appointments · OPD · Follow-ups · Billing · Medicine Inventory.
**Absent:** IPD, Operation Theatre, ICU Dashboard, ICU Bed Board.

Shared behaviour is referenced to `02-HOSPITAL-RECEPTIONIST.md`; only clinic deltas and the
identity journey are specified here.

---

## A. PATIENTS & IDENTITY

### TC-CR-001 — Register a clinic patient

`CLINIC_A · RECEPTIONIST · Patients · Critical · Patients ▸ Add Patient`
**Steps:** = `TC-HR-001` as clinic. Record `id`, `publicId`, `customId`.
**Expected:** `Patient added successfully`; `customId == "PAT"+id`; the POST goes to **`/clinic/patients`**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-002 — Validation: required, phone, DOB, email, name

`CLINIC_A · RECEPTIONIST · Patients · High · Add Patient`
**Steps:** = `TC-HR-002`/`003` as clinic.
**Expected:** identical messages; no row created.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-003 — ⭐ Parent → child chooser → Use This Patient

`CLINIC_A · RECEPTIONIST · Patients · Critical · Add Patient`
**Steps:** = `TC-CD-021`.
**Expected:** chooser lists only the father with name/age/PAT id; **no child created**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-004 — ⭐ Register Different Patient

`CLINIC_A · RECEPTIONIST · Patients · Critical · Add Patient`
**Steps:** = `TC-CD-022`.
**Expected:** child created with its own identity; ack bound to the number; father unchanged; audit masked.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-005 — ⭐ Third member; select the exact child

`CLINIC_A · RECEPTIONIST · Patients · Critical · Add Patient`
**Steps:** = `TC-CD-023`.
**Expected:** all matches listed in registration order; the clicked patient is the one selected.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-006 — Conflict body exposes only approved fields

`CLINIC_A · RECEPTIONIST · Patients · Critical · API`
**Steps:** = `TC-HR-011` against `/clinic/patients`.
**Expected:** `conflicts[]` keys exactly `id, publicId, customId, name, age`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-007 — Forged acknowledgement ignored

`CLINIC_A · RECEPTIONIST · Patients · Critical · API`
**Steps:** = `TC-HR-012` against `/clinic/patients`.
**Expected:** body-supplied ack fields discarded; 409 still returned without the query parameter.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-008 — Cross-tenant phone: no leak either way

`CLINIC_A + HOSPITAL_A + CLINIC_B · RECEPTIONIST · Patients · Critical · Add Patient`
**Steps:** = `TC-CD-024`.
**Expected:** no other tenant's patient ever appears in a chooser.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-009 — Edit, phone change, ack lapse, inactive patient

`CLINIC_A · RECEPTIONIST · Patients · High · Edit Patient`
**Steps:** = `TC-CD-025`, `TC-CD-026`, `TC-HR-013`.
**Expected:** as hospital; `customId` never changes on edit.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-010 — Search, today/history, pagination, details, delete

`CLINIC_A · RECEPTIONIST · Patients · Medium · Patients`
**Steps:** = `TC-HR-015`…`018` as clinic.
**Expected:** as hospital; soft delete keeps history.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. APPOINTMENTS · OPD · FOLLOW-UPS · BILLING

### TC-CR-011 — Book for an existing patient

`CLINIC_A · RECEPTIONIST · Appointments · Critical · Add Appointment`
**Steps:** = `TC-HR-021` as clinic.
**Expected:** `Appointment scheduled successfully`; `SCHEDULED`; visible to the clinic doctor.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-012 — ⭐ New-patient booking never auto-selects on a phone match

`CLINIC_A · RECEPTIONIST · Appointments · Critical · Add Appointment`
**Steps:** = `TC-HR-023` as clinic, using the Rane family number.
**Expected:** chooser appears even with one match; **Use This Patient** books that exact id; **Register Different Patient** creates the walk-in with the acknowledgement.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-013 — Slots, double booking, validation, status, reschedule

`CLINIC_A · RECEPTIONIST · Appointments · High · Appointments`
**Steps:** = `TC-HR-022`, `024`, `025`, `026`, `027` as clinic.
**Expected:** as hospital; `APPOINTMENTS` module gate confirmed by `TC-CD-036`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-014 — Create OPD with vitals

`CLINIC_A · RECEPTIONIST · OPD · Critical · New OPD / Case`
**Steps:** = `TC-HR-029` as clinic.
**Expected:** `OPD Case created successfully — ID: …`; appears in the clinic doctor's queue.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-015 — OPD inline new patient + duplicate phone (three paths)

`CLINIC_A · RECEPTIONIST · OPD · Critical · New OPD / Case`
**Steps:** = `TC-HR-030` as clinic.
**Expected:** Use This Patient → OPD for that id, no patient created, form flips to Existing; Register Different → patient + OPD; Cancel → form intact.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-016 — OPD vitals validation and disabled vitals

`CLINIC_A · RECEPTIONIST · OPD · High · New OPD / Case`
**Steps:** = `TC-HR-031` with the clinic's vitals configuration (`TC-CA-007`).
**Expected:** built-in validation intact; disabled vitals absent and dropped server-side.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-017 — OPD list, queue, case paper PDF

`CLINIC_A · RECEPTIONIST · OPD · High · OPD`
**Steps:** = `TC-HR-032` as clinic; open the PDF.
**Expected:** PDF header = **clinic** name; patient line = patient's `PAT` id (`TC-CD-028`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-018 — Reception cannot consult; post-consultation actions

`CLINIC_A · RECEPTIONIST · OPD/Billing · Critical · OPD/Billing`
**Steps:** = `TC-HR-033` and `TC-HR-034` as clinic.
**Expected:** no consultation control; `POST /clinic/doctors/consultation` **403**; View Prescription, Mark Paid and receipt print work per Billing Handler.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-019 — Follow-ups

`CLINIC_A · RECEPTIONIST · Follow-ups · High · Follow-ups`
**Steps:** = `TC-HR-037`/`038` as clinic (`/clinic/follow-ups`).
**Expected:** Due Today / Upcoming / Overdue buckets; arrive / reschedule / complete / cancel; link back to the source consultation.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-020 — Billing lifecycle

`CLINIC_A · RECEPTIONIST · Billing · Critical · Billing`
**Steps:** = `TC-HB-001`, `003`, `004`, `005`, `008`, `015`…`019`, `022` as clinic (`/clinic/billing`).
**Expected:** one bill per visit; partial→full payments; receipt PDF with the clinic header; **no DELETE for anyone**; double-click produces one payment.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. NEGATIVE · ISOLATION · UI

### TC-CR-021 — No IPD/OT/ICU surface for clinic reception

`CLINIC_A · RECEPTIONIST · Authorization · Critical · sidebar/API`
**Steps:** confirm the four tabs are absent; then run `TC-CD-011`, `013`, `016`, `017`, `018` **with the receptionist token**.
**Expected:** tabs absent; IPD/ward/bed results **recorded** (drift expected — a receptionist admitting in a clinic would be the most operationally plausible misuse); ICU/OT 404/403.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CR-022 — Clinic reception isolation + UI + persistence

`CLINIC_A + CLINIC_B + HOSPITAL_A · RECEPTIONIST · multi · Critical · UI/API`
**Steps:** = `TC-CD-039` from the reception session (patients, appointments, OPD, bills, documents by id and publicId; dropdown leakage; report totals), then `TC-HR-053`…`058` as clinic.
**Expected:** 403/404 throughout, no data in bodies, no foreign entries in dropdowns; UI states, modals, long names, currency and re-login persistence as hospital.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
