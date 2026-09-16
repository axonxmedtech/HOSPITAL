# 02 — HOSPITAL RECEPTIONIST

**Baseline:** `aa143a7` · **Cases:** `TC-HR-001` … `TC-HR-058` · Tenant HOSPITAL_A · login `rec.hospa@qa.test` at `/login/hospital` → `/hospital/receptionist`

**Tabs (module-gated):** Overview · Patients · Appointments · OPD · Follow-ups · IPD · Billing · Medicine Inventory · Operation Theatre · ICU Dashboard · ICU Bed Board

**Shared UI facts (from code):** `PatientModal` title `Add New Patient` / `Edit Patient`; fields **Full Name**, **Phone Number**, DOB (Day/Month/Year selects), Gender, **Email Address** (optional), **Address**, **Medical History / Allergies**, Insurance (UI-only, never sent). Rules: name/DOB/gender/phone required; phone = exactly 10 digits; DOB not future, not >120 y. Row actions: `View Details`, `Edit`, `Create OPD`, `View Prescription`, `View IPD details`. OPD modal `New OPD / Case` with `Existing Patient` / `New Patient` toggle, `Create OPD` button. Duplicate-phone chooser: **Mobile number already registered** → per-row **Use This Patient** · **Register Different Patient** · **Cancel**.

> Global: F5 → Overview (`TC-AUTH-015`). Phase A has **no DB uniqueness yet** — never assert that two simultaneous submits cannot both succeed.

---

## A. PATIENTS

### TC-HR-001 — Register a patient (happy path)

`HOSPITAL_A · RECEPTIONIST · Patients · Critical · Patients ▸ Add Patient`
**Data:** `Rahul Patil`, `9900011111`, DOB 02/04/1987, Male, address, history `None`.
**Steps:** 1. Patients → **Add Patient**. 2. Fill. 3. Save. 4. DevTools: capture `id`, `publicId`, `customId` from `POST /hospital/patients`.
**Expected:** `Patient added successfully`; row shows name, `PAT<id>`, age (computed from DOB), phone; `customId == "PAT"+id`; audit `PATIENT_CREATED`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-002 — Required-field validation

`HOSPITAL_A · RECEPTIONIST · Patients · High · Add Patient`
**Steps:** Save with blank name / blank phone / no DOB / no gender; then valid name only.
**Expected:** field-level messages; no request sent (Network empty); modal stays open.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-003 — Invalid phone / DOB / email / name

`HOSPITAL_A · RECEPTIONIST · Patients · High · Add Patient`
**Steps:** phone `990001111` (9), `99000111111` (11), `+919900011111`, `99000 11111`; DOB tomorrow; DOB 1890; email `abc`; name `R@hul 😀`.
**Expected:** each rejected client-side or server-side (400) with a specific message (`Phone number must be exactly 10 digits`, `Date of birth cannot be in the future`, `…more than 120 years ago`); no row.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-004 — Age derives from DOB

`HOSPITAL_A · RECEPTIONIST · Patients · Medium · Patients`
**Steps:** register DOB = today−10y+1day; view list; edit DOB to today−10y; view.
**Expected:** age shows 9 then 10 (computed live, never stored); child (2017) shows 8/9 correctly.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-005 — ⭐ Child shares father's phone → chooser → Use This Patient (no child created)

`HOSPITAL_A · RECEPTIONIST · Patients · Critical · Add Patient`
**Pre:** P1 Rahul exists on `9900011111`.
**Steps:** 1. Add Patient `Aarav Patil`, `9900011111`, DOB 21/09/2017, Male. 2. Save. 3. Read the dialog. 4. Click **Use This Patient** on Rahul. 5. Patients list.
**Expected:** dialog **Mobile number already registered** lists `Rahul Patil`, `Age 39`, `Patient ID PAT<id>`; **no** phone/DOB/address/email shown; after step 4 the modal closes with Rahul selected/handed back and **no Aarav row exists**; `POST /hospital/patients` was called exactly once (the refused 409).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-006 — ⭐ Register Different Patient → child gets own identity; ack bound to X

`HOSPITAL_A · RECEPTIONIST · Patients · Critical · Add Patient`
**Steps:** 1. Repeat `TC-HR-005` steps 1–3. 2. Click **Register Different Patient**. 3. Patients list; DevTools second POST.
**Expected:** second POST carries `?acknowledgeDuplicatePhone=true`; Aarav created with own `PAT` id; response shows `duplicatePhoneAckFor: "9900011111"`, `duplicatePhoneAckBy: rec.hospa@qa.test`; **Rahul unchanged** (same id, name, phone, no ack fields); audit `PATIENT_DUPLICATE_PHONE_ACKNOWLEDGED` with phone masked `99******11`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-007 — ⭐ Third family member → chooser lists ALL matches → pick exact child

`HOSPITAL_A · RECEPTIONIST · Patients · Critical · Add Patient`
**Steps:** 1. Add `Sunita Patil`, `9900011111`. 2. Dialog. 3. Click **Use This Patient** on **Aarav** (the second row). 4. Repeat, click **Register Different Patient**.
**Expected:** dialog lists **Rahul then Aarav** (registration order, each with own button); step 3 selects Aarav's exact id (verify handed-back `id`); step 4 creates Sunita; now three active patients on one number, one ack per acknowledged row.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-008 — Chooser Cancel keeps the typed form

`HOSPITAL_A · RECEPTIONIST · Patients · Medium · Add Patient`
**Steps:** trigger chooser; **Cancel**.
**Expected:** back to `Add New Patient` with every field still filled; nothing created.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-009 — Inactive patient does not block the number

`HOSPITAL_A · RECEPTIONIST · Patients · High · Add Patient`
**Steps:** = `TC-VIS-004`.
**Expected:** no chooser; created without ack.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-010 — Same phone in HOSPITAL_B never appears

`HOSPITAL_A + B · RECEPTIONIST · Patients · Critical · Add Patient`
**Steps:** = `TC-ISO-010`.
**Expected:** A's chooser never lists B's patients and vice versa.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-011 — Chooser exposes only approved fields (API)

`HOSPITAL_A · RECEPTIONIST · Patients · Critical · API`
**Steps:** replay the 409 POST via curl; read `conflicts[]`.
**Expected:** keys exactly `id, publicId, customId, name, age`; body `code: CONFLICT`, `error: "This mobile number is already registered to a patient at this hospital."`; no `phone`, `dateOfBirth`, `address`, `email`, `medicalHistory`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-012 — Forged ack fields in the body are ignored

`HOSPITAL_A · RECEPTIONIST · Patients · Critical · API`
**Steps:** POST a patient on `9900011111` with body `duplicatePhoneAckFor:"9900011111"` and **no** query param.
**Expected:** **409** chooser response; nothing created. With the query param, created — response ack fields set server-side (`By` = your email, not any forged value).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-013 — Edit patient (name, address, history)

`HOSPITAL_A · RECEPTIONIST · Patients · High · Edit Patient`
**Steps:** row → **Edit**; change name → `Rahul R Patil`, address, history; Save; F5; open the doctor's view.
**Expected:** `Patient updated successfully`; persists; `customId` unchanged; doctor sees new name; audit `PATIENT_UPDATED`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-014 — Edit phone onto an existing number → chooser; ack lapses on change away

`HOSPITAL_A · RECEPTIONIST · Patients · Critical · Edit Patient`
**Steps:** = `TC-VIS-007` and `TC-VIS-008`.
**Expected:** chooser on collision; **Register Different Patient** saves with ack for the new number; moving to a free number clears ack fields; ack for X never exempts Y.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-015 — View Details / timeline

`HOSPITAL_A · RECEPTIONIST · Patients · High · View Details`
**Steps:** row → **View Details** (`PatientDetailsModal`): demographics, timeline (`/hospital/patients/{publicId}/timeline`), documents, latest bill.
**Expected:** fields match; timeline lists OPD/IPD/bills in order; no other patient's data.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-016 — Search by name / phone / PAT id

`HOSPITAL_A · RECEPTIONIST · Patients · High · Patients`
**Steps:** `rahul`, `9900011111`, `PAT<id>`, `zz`.
**Expected:** partial, case-insensitive; phone search returns all three family members; empty state for `zz`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-017 — Today / History view and pagination

`HOSPITAL_A · RECEPTIONIST · Patients · Medium · Patients`
**Steps:** switch `today` view; register a patient at 23:58 IST vs 00:02 (if feasible) ; paginate >10.
**Expected:** today = business-day IST (`BusinessClock`); paging stable; page size respected.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-018 — Delete (soft) with reason

`HOSPITAL_A · RECEPTIONIST · Patients · Critical · Patients`
**Steps:** delete `Old Record` with reason `QA`; confirm; search; open historical OPD.
**Expected:** `ConfirmationModal` required; row gone; history intact (`TC-VIS-003`); `DELETE /hospital/patients/{publicId}?reason=` soft. Receptionist **can** delete? Matrix says DELETE = `ADM DOC` — **record**; if the UI shows a delete action for reception yet API 403s, raise `UI_WITHOUT_BACKEND`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-019 — Double-submit / API failure / retry

`HOSPITAL_A · RECEPTIONIST · Patients · High · Add Patient`
**Steps:** double-click Save; stop backend and Save; restart and Save.
**Expected:** one row; readable toast on failure (`extractApiError`), form retained; retry succeeds without duplicate.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-020 — Patients tab hidden without OPD; API ungated

`HOSPITAL_M · RECEPTIONIST · Patients · High · sidebar/API`
**Steps:** Hospital M receptionist (plan BILLING-only): sidebar; `GET/POST /hospital/patients`.
**Expected:** tab hidden (`requiredModule: OPD`); API — record (`Patient` controller has no `@RequireModule`; likely 200 → drift).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. APPOINTMENTS

### TC-HR-021 — Book for existing patient

`HOSPITAL_A · RECEPTIONIST · Appointments · Critical · Add Appointment`
**Steps:** 1. Appointments → Add. 2. Patient combobox: type `Rahul` → pick `Rahul Patil - 9900011111`. 3. Doctor. 4. Date via Day/Month/Year. 5. Available slot button. 6. **Schedule**.
**Expected:** `Appointment scheduled successfully`; status `SCHEDULED`; appears in `today`/list; doctor's Appointments tab shows it; past date → `Appointment date cannot be in the past`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-022 — Slot availability & double booking

`HOSPITAL_A · RECEPTIONIST · Appointments · High · Add Appointment`
**Steps:** book 10:00 with Dr Meera; open Add again for the same doctor/date; observe 10:00; try via API a second `POST` with the same time.
**Expected:** 10:00 shows booked/disabled (`Select slot for 10:00 (booked)`); API second post → 409/400 (record); past-time slots today are hidden.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-023 — ⭐ New Patient booking on a matched phone never auto-selects

`HOSPITAL_A · RECEPTIONIST · Appointments · Critical · Add Appointment`
**Steps:** tick **New Patient (not registered yet)**; `Aarav Patil`, `9900011111`, DOB, Gender Male; doctor/date/slot; Schedule.
**Expected:** chooser appears **even with one match**; `POST /hospital/appointments` first call → 409 with `conflicts`; **Use This Patient** re-posts with that `patientId` and `acknowledgeDuplicatePhone:false`; **Register Different Patient** re-posts with `acknowledgeDuplicatePhone:true` and walk-in details (`address = Walk-in`, DOB default today if omitted — record).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-024 — Appointment validation

`HOSPITAL_A · RECEPTIONIST · Appointments · High · Add Appointment`
**Steps:** no patient; no doctor; no date; no slot; new patient with 9-digit phone / no DOB / no gender.
**Expected:** each field message; no POST.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-025 — Status changes and cancel

`HOSPITAL_A · RECEPTIONIST · Appointments · High · Appointments`
**Steps:** change status → `COMPLETED`; another → `CANCELLED`; delete a third (soft).
**Expected:** `PUT /{id}/status` works for `ADM DOC REC`; cancelled row styled; deleted row hidden; doctor view updates; invalid status string via API → 400.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-026 — Reschedule (edit)

`HOSPITAL_A · RECEPTIONIST · Appointments · High · Appointments`
**Steps:** edit date/time/doctor via `PUT /hospital/appointments/{id}`; refresh.
**Expected:** persists; original slot freed; new slot booked.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-027 — Filters, search, today widget, stats

`HOSPITAL_A · RECEPTIONIST · Appointments · Medium · Appointments`
**Steps:** filter by doctor/date/status; search patient; compare `today` widget and stats with hand count.
**Expected:** correct; empty state when none.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-028 — APPOINTMENTS module off

`HOSPITAL_M · RECEPTIONIST · Appointments · High · sidebar/API`
**Steps:** = `TC-MOD-013`.
**Expected:** tab hidden and API 403 (reference gate).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. OPD

### TC-HR-029 — Create OPD for existing patient with vitals

`HOSPITAL_A · RECEPTIONIST · OPD · Critical · New OPD / Case`
**Steps:** 1. **Add OPD**. 2. `Existing Patient` → `search patient…` → `Asha Rao`/`Rahul`. 3. Doctor. 4. BP `120/80`, Temp, Pulse, Weight, Height, SpO2, custom vitals. 5. `Problem / Reason` `Fever`; Visit Type `NEW`. 6. (Bill Payment FIRST) payment method. 7. **Create OPD**.
**Expected:** `OPD Case created successfully — ID: <caseId>`; doctor's queue shows it; `createdOpd` modal offers print; audit.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-030 — New Patient inline in OPD + duplicate phone (3 paths)

`HOSPITAL_A · RECEPTIONIST · OPD · Critical · New OPD / Case`
**Steps:** toggle `New Patient` → `New Patient Details` fields; phone `9900011111`; Create OPD → chooser: (a) Use This Patient Aarav; (b) Register Different Patient; (c) Cancel.
**Expected:** (a) OPD for Aarav's id, no patient created, form flips to `Existing Patient` with `Aarav Patil [PAT…]`; (b) new patient + OPD; (c) form intact. A **failed OPD after successful create** flips mode to existing so retry does not re-register.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-031 — Vitals validation & disabled vitals

`HOSPITAL_A · RECEPTIONIST · OPD · High · New OPD / Case`
**Steps:** = `TC-HAS-012`; plus with SpO2 disabled by admin, confirm the field is absent and a sneaked API value is dropped.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-032 — OPD list: today/history, queue, search, PDF

`HOSPITAL_A · RECEPTIONIST · OPD · High · OPD`
**Steps:** list; queue position; search; **Print case paper** (`/hospital/opd/{id}/pdf`); documents PDF.
**Expected:** correct; PDF header = hospital; patient line = name + **PAT id**; VITAL SIGNS from enabled vitals; problem text.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-033 — Reception cannot consult

`HOSPITAL_A · RECEPTIONIST · OPD · High · OPD/API`
**Steps:** open a case; look for Start/Complete consultation; `POST /hospital/doctors/consultation` with reception token.
**Expected:** no control; **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-034 — Post-consultation: View Prescription, bill, Mark Paid

`HOSPITAL_A · RECEPTIONIST · OPD/Billing · Critical · OPD / Billing`
**Steps:** after doctor completes: row → **View Prescription**; Billing → bill row `PENDING` → **Mark Paid** (if billingHandler allows reception) → **Print**.
**Expected:** prescription PDF; bill `PAID`; receipt PDF; `PUT /hospital/billing/{id}/status` audit.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-035 — Duplicate OPD for same patient same day

`HOSPITAL_A · RECEPTIONIST · OPD · Medium · New OPD`
**Steps:** create two OPDs for Rahul today.
**Expected:** record behaviour (allowed vs warned) — `NEEDS_PRODUCT_CONFIRMATION`; no 500.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-036 — OPD double-submit & in-flight guard

`HOSPITAL_A · RECEPTIONIST · OPD · High · New OPD`
**Steps:** double-click **Create OPD**.
**Expected:** button disables (`opdInFlight`); exactly one OPD (and one patient if new).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. FOLLOW-UPS

### TC-HR-037 — Follow-ups tab (reception)

`HOSPITAL_A · RECEPTIONIST · Follow-ups · High · Follow-ups`
**Steps:** = `TC-HAC-008`/`009` as reception: view buckets; mark **arrive** (creates the follow-up OPD?); reschedule; cancel.
**Expected:** `FollowUp` POSTs admit `ADM DOC REC`; arrive → record what it creates (OPD case with follow-up fee) — document actual; overdue styling.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-038 — Follow-up ↔ original OPD link

`HOSPITAL_A · RECEPTIONIST · Follow-ups · Medium · Follow-ups`
**Steps:** from a follow-up row open the source consultation.
**Expected:** navigates to the original OPD/prescription; patient identity consistent.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. IPD (reception)

### TC-HR-039 — Admit from doctor request (IpdAdmitModal)

`HOSPITAL_A · RECEPTIONIST · IPD · Critical · IPD ▸ requested`
**Pre:** doctor requested admission from Rahul's OPD; `General Ward A` has incharge and `GA-01` available.
**Steps:** 1. IPD → `requested` sub-tab (badge count). 2. Admit → ward `General Ward A` → bed `GA-01` → admission type → primary diagnosis → Admit.
**Expected:** admission `ADMITTED`; IPD number assigned (unique, `ipd_number`); bed `GA-01` → **occupied**; row → `/ipd/:id`; Latha sees patient under Unassigned.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-040 — No available bed / occupied bed / ward without incharge

`HOSPITAL_A · RECEPTIONIST · IPD · Critical · IpdAdmitModal`
**Steps:** (a) all beds occupied → open modal; (b) via API `POST /hospital/ipd/admit` with an occupied bed id; (c) ward with no incharge.
**Expected:** (a) bed picker empty with message; (b) **409** conflict, no admission; (c) refused with a clear message.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-041 — Duplicate admission for an already-admitted patient

`HOSPITAL_A · RECEPTIONIST · IPD · Critical · IPD`
**Steps:** attempt a second admission for Rahul while ADMITTED (UI and API).
**Expected:** refused (pessimistic lock per patient); one active admission.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-042 — Change bed (transfer) and discharge from reception

`HOSPITAL_A · RECEPTIONIST · IPD · Critical · /ipd/:id`
**Steps:** **change bed** to `GA-02`; then doctor plans discharge; reception **confirm discharge**.
**Expected:** `GA-01` → cleaning, `GA-02` occupied; on discharge `GA-02` → cleaning; status `DISCHARGED`; final bill; `PUT /change-bed` is `ADM DOC` — **record** if reception is denied (then transfer is doctor/admin only).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-043 — `/ipd/:id` direct and wrong tenant

`HOSPITAL_A + B · RECEPTIONIST · IPD · Critical · /ipd/:id`
**Steps:** = `TC-ISO-020` plus refresh on `/ipd/:id`.
**Expected:** own id renders after F5 (real route); B's id → not found; ids±1.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-044 — Refresh / failure mid-admission

`HOSPITAL_A · RECEPTIONIST · IPD · High · IpdAdmitModal`
**Steps:** fill modal; F5 (loses tab, expected); refill; stop backend; Admit; restart; Admit.
**Expected:** no half-admission; bed not left occupied by a failed attempt; retry succeeds once.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. BILLING · INVENTORY · OT · ICU (reception views)

### TC-HR-045 — Billing tab

`HOSPITAL_A · RECEPTIONIST · Billing · High · Billing`
**Steps:** = `07` cases `TC-HB-001`…`006` as reception.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-046 — Medicine Inventory (read)

`HOSPITAL_A · RECEPTIONIST · Inventory · Medium · Medicine Inventory`
**Steps:** open; search; attempt an edit control; `POST /hospital/medicines` with reception token.
**Expected:** list visible; matrix says `ADM DOC REC` for POST — record whether reception can add stock (`NEEDS_PRODUCT_CONFIRMATION`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-047 — Operation Theatre tab (reception: schedule/start/complete)

`HOSPITAL_A · RECEPTIONIST · OT · High · Operation Theatre`
**Steps:** = `08` `TC-HOT-004`…`008` as reception (per OT Permissions defaults).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-048 — ICU Dashboard / Bed Board (reception, read)

`HOSPITAL_A · RECEPTIONIST · ICU · Medium · ICU`
**Steps:** open both; attempt a write.
**Expected:** read (`IcuDashboard` GET admits REC); writes 403 (`IcuStay` PUT `ADM DOC`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## G. OVERVIEW · PERMISSIONS · UI

### TC-HR-049 — Reception Overview widgets

`HOSPITAL_A · RECEPTIONIST · Overview · Medium · Overview`
**Steps:** compare today's appointments, queue, follow-ups, IPD requests badge with hand counts; **Add OPD** / **Add Patient** shortcuts.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-050 — Reception cannot reach admin/clinical-only endpoints

`HOSPITAL_A · RECEPTIONIST · Authorization · Critical · API`
**Steps:** = `TC-PERM-001`, `TC-PERM-029` rows for RECEPTIONIST; plus `GET /hospital/nurses`, `/hospital/beds` (POST), `/hospital/audit-logs`, `/hospital/settings/fees` (drift).
**Expected:** 403 except the known drift endpoints (record).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-051 — ⚠️ Reception writing nursing records (discovery)

`HOSPITAL_A · RECEPTIONIST · Clinical · High · API`
**Steps:** = `TC-PERM-009`.
**Expected:** record; `NEEDS_PRODUCT_CONFIRMATION`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-052 — Tenant isolation from reception

`HOSPITAL_A + B · RECEPTIONIST · all · Critical · UI/API`
**Steps:** = `TC-ISO-003`…`012`, `013`…`018`, `019`…`021`, `034`…`036`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-053 — Long names / nulls / dates / currency

`HOSPITAL_A · RECEPTIONIST · UI · Low · Patients / Billing`
**Steps:** 100-char patient name; blank email/address; view list, appointment combobox, bill, PDF.
**Expected:** truncation; `—` for blanks; dates `dd MMM yyyy`; amounts 2 decimals with ₹.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-054 — Modals: open/close/ESC/backdrop/back

`HOSPITAL_A · RECEPTIONIST · UI · Low · all modals`
**Steps:** open Add Patient, Add Appointment, Add OPD, IpdAdmitModal, PatientDetailsModal; close via ✕, Cancel, ESC, backdrop; browser Back while open.
**Expected:** consistent close; no orphaned overlay; focus returns.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-055 — Loading / empty / error states per tab

`HOSPITAL_A · RECEPTIONIST · UI · Medium · all tabs`
**Steps:** fresh tenant tabs; slow network; backend down.
**Expected:** spinner → EmptyState; error banner via `safeLoadMessage` (never a raw Java exception string).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-056 — Responsive

`HOSPITAL_A · RECEPTIONIST · UI · Low · all`
**Steps:** 375/768px on Patients, Add OPD, IPD admit.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-057 — Realtime refresh from other roles

`HOSPITAL_A · RECEPTIONIST · Realtime · Medium · OPD`
**Steps:** doctor completes consultation (tab 2); reception OPD list (tab 1).
**Expected:** status updates via WS without reload.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HR-058 — Re-login persistence sweep

`HOSPITAL_A · RECEPTIONIST · Persistence · High · all`
**Steps:** after A–F, logout/login; re-open each record created.
**Expected:** everything persists exactly.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
