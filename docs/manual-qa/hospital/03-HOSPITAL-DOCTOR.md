# 03 — HOSPITAL DOCTOR

**Baseline:** `aa143a7` · **Cases:** `TC-HD-001` … `TC-HD-048` · Tenant HOSPITAL_A · login `doc1.hospa@qa.test` → `/hospital/doctor`

**Tabs:** Overview · Patients · Appointments · OPD · Follow-ups · IPD · Billing · Operation Theatre · ICU Dashboard · ICU Bed Board · Medicine Inventory

**`ConsultationModal` (verified labels):** SYMPTOMS / DIAGNOSIS sections with **Symptoms**, **Diagnosis**, **Treatment Notes**; **Symptom Presets**, **Diagnosis Presets**; medicines via **Add Medicine** / **Edit Medicine** with `Dosage (e.g., 500mg)`, `Duration (e.g., 5 Days)`, `Instruction`; lab checkboxes `CBC, LFT, RFT, RBS, Lipid Profile, TSH, HbA1c, Urine Routine`; `Consultation Fee`, `Case Paper Fee`; follow-up date; **Complete Consultation** / **Save Changes**. Row actions: `Start Consultation`, `Complete`, `View Details`, `View History`, `View Prescription`, `Print Prescription`, `Print Case Paper`, `Admit to IPD`, `Follow-up`.

> **Lab status (corrected at Tranche 2):** ordering **is** implemented — ticking lab tests creates `LabOrder` rows (`DoctorService:621-631`) that print on the case paper. There is **no** lab results/management UI. Classified `PARTIAL`. Do not invent a results workflow.

---

## A. QUEUE & OVERVIEW

### TC-HD-001 — Doctor Overview and own queue

`HOSPITAL_A · DOCTOR · Overview · High · Overview`
**Steps:** 1. Log in. 2. Read Overview widgets (today's appointments, queue, follow-ups). 3. Compare with hand counts for **this** doctor.
**Expected:** figures are this doctor's; queue lists `QUEUED` OPD cases assigned to them, in order; `GET /hospital/opd/queue/my` used.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-002 — Doctor sees only own queue; other doctor's cases (discovery)

`HOSPITAL_A · DOCTOR · OPD · High · OPD`
**Steps:** cases exist for Dr Meera and Dr Arjun. As Meera: open OPD tab and the queue; try to open Arjun's case; `GET /hospital/opd/{arjun_case_id}` with Meera's token.
**Expected:** queue = own only. Whether the **OPD tab** lists colleagues' cases and whether the API returns 200 → **record**. `NEEDS_PRODUCT_CONFIRMATION` (Tranche-1 item 13). Do not classify pass/fail.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-003 — Queue ordering and realtime arrival

`HOSPITAL_A · DOCTOR · OPD · Medium · Overview/OPD`
**Steps:** reception creates 3 OPDs for this doctor (tab 2); watch the doctor's queue.
**Expected:** appear in creation order without manual refresh (WS `REFRESH_DATA`); positions renumber after one is completed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. CONSULTATION (OPD)

### TC-HD-004 — Start Consultation

`HOSPITAL_A · DOCTOR · OPD · Critical · OPD ▸ Start Consultation`
**Steps:** queue row → **Start Consultation**.
**Expected:** `ConsultationModal` opens showing **the correct patient** (name + `PAT` id + age/gender) and the vitals reception captured; patient status → `CONSULTING`; reception's list reflects it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-005 — Complete consultation with symptoms, diagnosis, notes, medicines

`HOSPITAL_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** 1. Symptoms `Fever, body ache`. 2. Diagnosis `Viral fever`. 3. Treatment Notes. 4. **Add Medicine** ×2 with Dosage/Duration/Instruction. 5. Fees. 6. **Complete Consultation**.
**Expected:** `Consultation submitted successfully`; OPD → `COMPLETED`; patient status `COMPLETED`; bill generated; prescription retrievable; audit entry.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-006 — Medicine autocomplete, unresolved text, edit, remove

`HOSPITAL_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** 1. Type `QA Para` → pick from catalogue. 2. Type `Someunknownmed` and **do not** select; try to Complete. 3. **Edit Medicine**; change dosage. 4. Remove a line. 5. Add with blank dosage.
**Expected:** step 2 — the unresolved text is not silently prescribed; the UI blocks or warns (`unresolvedMedicineText`) — record the exact message; edits and removals persist into the saved prescription; validation on required medicine fields.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-007 — Symptom / Diagnosis / Prescription presets applied

`HOSPITAL_A · DOCTOR · OPD · Medium · ConsultationModal`
**Steps:** use **Symptom Presets** and **Diagnosis Presets**; apply a prescription preset (`Fever pack`); save a new preset from the current consultation (`Preset saved`).
**Expected:** presets fill the fields; newly saved preset appears for this doctor; deleting the preset later leaves this consultation untouched (`TC-HA-030`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-008 — ⭐ Lab tests ordered (PARTIAL feature)

`HOSPITAL_A · DOCTOR · OPD · High · ConsultationModal`
**Steps:** 1. Tick lab required; select `CBC` and `Lipid Profile`. 2. Complete. 3. Print Case Paper. 4. Untick lab required in a second consultation but leave tests selected; Complete. 5. Look for any lab results screen anywhere in the product.
**Expected:** step 3 — the case-paper PDF lists CBC and Lipid Profile; step 4 — `labTests` is cleared when `labRequired` is false (no orders created); step 5 — **no lab results UI exists**: ordering only. `PARTIAL` — do not raise "cannot enter results" as a bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-009 — In-clinic medicines / procedures section

`HOSPITAL_A · DOCTOR · OPD · Medium · ConsultationModal`
**Steps:** with In-Clinic ON add an in-clinic item; save as preset (`In-clinic preset saved`); Complete; check the bill and the In-Clinic print.
**Expected:** item billed; print controlled by `printInClinic`; with In-Clinic OFF (`TC-HAS-004`) the section is absent.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-010 — Fees inside consultation reflect admin Fees

`HOSPITAL_A · DOCTOR · OPD/Billing · Critical · ConsultationModal`
**Steps:** admin sets consultation fee 777 (`TC-HAC-019`); start a consultation.
**Expected:** `Consultation Fee` prefilled 777; `Case Paper Fee` per settings; editing the fee (if allowed) changes only this bill — record whether the doctor may override.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-011 — Follow-up date set during consultation

`HOSPITAL_A · DOCTOR · Follow-ups · High · ConsultationModal`
**Steps:** set follow-up +3 days; Complete; open Follow-ups (doctor, reception, admin).
**Expected:** appears under `Upcoming`; a past date lands in `Overdue`; blank = no follow-up row.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-012 — Save Changes vs Complete Consultation

`HOSPITAL_A · DOCTOR · OPD · High · ConsultationModal`
**Steps:** open a completed consultation → **Save Changes** after editing the diagnosis; reopen.
**Expected:** edit persists without re-billing or duplicating the prescription; status stays `COMPLETED`; audit shows an update.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-013 — Validation & empty consultation

`HOSPITAL_A · DOCTOR · OPD · High · ConsultationModal`
**Steps:** Complete with everything blank; with diagnosis only; with a medicine missing dosage.
**Expected:** required-field messages (record which fields are mandatory); no partial save; no 500.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-014 — Double-submit Complete Consultation

`HOSPITAL_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** double-click **Complete Consultation**; check bills and prescriptions.
**Expected:** exactly **one** consultation, **one** bill, **one** prescription. Two bills for one visit is a **Critical** billing-integrity bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-015 — Failure / refresh mid-consultation

`HOSPITAL_A · DOCTOR · OPD · High · ConsultationModal`
**Steps:** fill the modal; stop the backend; Complete (`Consultation failed`); restart; Complete again. Separately: F5 mid-edit.
**Expected:** readable error, form retained, retry produces one consultation; F5 loses unsaved text (expected; record whether any draft is kept).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-016 — ⭐ Wrong-patient guard: consultation attaches to the case opened

`HOSPITAL_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** with the parent/child family (`TC-HR-007`) create OPD cases for **Aarav** and **Rahul**. Open Aarav's case, complete it, then open Rahul's.
**Expected:** each consultation, prescription, bill and PDF carries the **exact** patient of that case — `PAT<Aarav id>` on Aarav's, never the parent's. This is the clinical-safety assertion of the whole duplicate-phone feature.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PRESCRIPTIONS, PRINTING, DOCUMENTS

### TC-HD-017 — Print Prescription

`HOSPITAL_A · DOCTOR · Documents · High · OPD row`
**Steps:** **Print Prescription** (`/hospital/doctors/prescription/opd/{opdId}/pdf`); open the PDF.
**Expected:** hospital header, patient **name + PAT id**, age/gender, doctor name + specialization, medicines with dosage/duration/instruction, date. **UHID/Patient No must be the patient's id, never the hospital's.**
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-018 — Print Case Paper

`HOSPITAL_A · DOCTOR · Documents · High · OPD row`
**Steps:** **Print Case Paper** (`/hospital/opd/{id}/pdf`).
**Expected:** VITAL SIGNS table built from **enabled vitals only** (`TC-HAS-010`); symptoms/diagnosis/notes; lab orders listed; patient identity correct.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-019 — Print controls follow Print & Payment settings

`HOSPITAL_A · DOCTOR · Documents · Medium · OPD`
**Steps:** admin turns Prescription print OFF (`TC-HAS-009`); doctor reloads.
**Expected:** the control is hidden; the endpoint still answers (record) — a print policy, not authorization.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-020 — ⚠️ Prescription PDF role check (drift)

`HOSPITAL_A · NURSE, PHA, REC · Documents · High · API`
**Steps:** = `TC-API-007` / `TC-PERM-006`.
**Expected:** same-tenant result recorded (`NEEDS_PRODUCT_CONFIRMATION`); **HOSPITAL_B token must be 403/404** — a 200 is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-021 — Patient documents from the doctor view

`HOSPITAL_A · DOCTOR · Documents · High · View Details ▸ Documents`
**Steps:** upload a **synthetic** PDF/JPG; preview (`PatientDocumentPreviewModal`); download; archive; upload a 20 MB file and a `.exe`.
**Expected:** upload/preview/download work; archive hides but keeps; oversize and disallowed types rejected with a readable message, never a 500. Local-storage unavailability shows `Document storage is not available` — that is an environment condition, record it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-022 — View History

`HOSPITAL_A · DOCTOR · Patients · Medium · View History`
**Steps:** open `HistoryDrawer` for a patient with 2 past consultations.
**Expected:** chronological list with diagnosis and medicines; opens the correct prescription; nothing from another patient.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. IPD FROM THE DOCTOR SIDE

### TC-HD-023 — Admit to IPD (request)

`HOSPITAL_A · DOCTOR · IPD · Critical · OPD ▸ Admit to IPD`
**Steps:** on a completed/consulting case click **Admit to IPD**; fill reason/diagnosis; submit.
**Expected:** an IPD **request** is created; reception's IPD → `requested` badge increments; no bed is taken yet.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-024 — `/ipd/:id` as doctor: full case

`HOSPITAL_A · DOCTOR · IPD · Critical · /ipd/:id`
**Steps:** open the admission; review Overview, Vitals, Medication, Notes, Initial Assessment, Vulnerability, Sugar Chart, I/O, Ventilator, Severity Scores, Consent Forms, Documents; add an IPD prescription; **stop** a prescription; administer hospital items; set a follow-up.
**Expected:** sub-tabs mirror the nurse's set and obey **Files & Access** (`TC-HAS-013`); prescription add/stop persist; administered items hit stock and the bill.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-025 — Plan discharge → reception confirms

`HOSPITAL_A · DOCTOR · IPD · Critical · /ipd/:id`
**Steps:** **plan discharge** (summary/advice); reception **confirm discharge**.
**Expected:** status flows to `DISCHARGED`; bed → `cleaning`; final bill available; discharge summary printable if implemented (record).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-026 — Invalid IPD transitions

`HOSPITAL_A · DOCTOR · IPD · High · API`
**Steps:** confirm discharge twice; change bed after discharge; administer medication to a discharged admission.
**Expected:** refused with 400/409; no duplicate discharge; bed state unchanged by the refused calls.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-027 — Doctor cannot admit directly (bed assignment is reception/admin)

`HOSPITAL_A · DOCTOR · IPD · High · API`
**Steps:** `POST /hospital/ipd/admit` with the doctor token.
**Expected:** matrix says POST = `ADM DOC REC` → likely 200. **Record**; if a doctor can pick a bed, note it as `NEEDS_PRODUCT_CONFIRMATION` against the documented "reception admits" workflow.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. APPOINTMENTS · PATIENTS · FOLLOW-UPS · BILLING (doctor views)

### TC-HD-028 — Doctor appointment list and status

`HOSPITAL_A · DOCTOR · Appointments · High · Appointments`
**Steps:** `my-appointments`; mark one `COMPLETED`; cancel one; **Add Appointment** (doctor may create).
**Expected:** only this doctor's by default; status changes allowed (`ADM DOC REC`); creation — matrix says POST = `ADM REC` → doctor likely **403**; record if the UI shows the button (`UI_WITHOUT_BACKEND`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-029 — Doctor patient list and edit rights

`HOSPITAL_A · DOCTOR · Patients · High · Patients`
**Steps:** list; search; **View Details**; try edit; try delete; API `PUT` and `DELETE /hospital/patients/{id}`.
**Expected:** GET/POST allowed; **PUT = `ADM REC`** → doctor 403; **DELETE = `ADM DOC`** → doctor allowed. Confirm the UI matches (no Edit button, a Delete action present) — any mismatch is `UI_WITHOUT_BACKEND`/drift.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-030 — Doctor registers a patient (SOLO reception mode)

`HOSPITAL_A · DOCTOR · Patients · High · Settings-dependent`
**Steps:** admin sets Reception Mode `SOLO` (`TC-HAS-001`); doctor reloads.
**Expected:** doctor gains Add Patient / Add OPD controls; duplicate-phone chooser behaves identically (`TC-HR-005`…`007`). Restore.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-031 — Doctor follow-up actions

`HOSPITAL_A · DOCTOR · Follow-ups · High · Follow-ups`
**Steps:** = `TC-HAC-009` as doctor (`FollowUpPanel`: `Due Today`, `Upcoming`, `Overdue`, `Reschedule`, `Show recent only`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-032 — Doctor billing view and Mark Paid under billingHandler

`HOSPITAL_A · DOCTOR · Billing · Critical · Billing`
**Steps:** with `billingHandler = DOCTOR` (`TC-HAS-002`): open Billing; **Mark Paid**; print receipt. Then set `RECEPTIONIST` and retry.
**Expected:** control present only when the setting allows; API `PUT /hospital/billing/{id}/status` admits `ADM DOC REC` regardless — **record** whether the setting is enforced server-side or UI-only (`NEEDS_PRODUCT_CONFIRMATION` if UI-only).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-033 — Medicine Inventory (doctor, read)

`HOSPITAL_A · DOCTOR · Inventory · Medium · Medicine Inventory`
**Steps:** open; search a medicine; check stock shown matches the pharmacist's view; attempt an edit.
**Expected:** consistent stock; POST/PUT allowed per matrix (`ADM DOC REC`) — record whether the UI exposes it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. OT & ICU (doctor)

### TC-HD-034 — Doctor requests a surgery

`HOSPITAL_A · DOCTOR · OT · Critical · IPD case ▸ Surgery Request`
**Steps:** from an IPD case open `SurgeryRequestModal`; procedure name, priority `ELECTIVE`; **Create Request**.
**Expected:** `Surgery request created`; blank name → `Procedure name is required`; appears on the OT Board as `REQUESTED`; requires `OT_CREATE` (`TC-PERM-011`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-035 — Doctor in the OT lifecycle

`HOSPITAL_A · DOCTOR · OT · High · Operation Theatre`
**Steps:** per the OT Permissions grid attempt approve / pre-op / anaesthesia clearance / start / complete; fill an operative note; sign a WHO checklist phase.
**Expected:** each action allowed only with the matching permission; denied ones 403 and hidden.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-036 — Doctor ICU actions

`HOSPITAL_A · DOCTOR · ICU · High · ICU`
**Steps:** open ICU Dashboard and Bed Board; open an ICU stay; update it (`PUT /hospital/icu/{id}` = `ADM DOC`); record I/O and a severity score.
**Expected:** allowed per matrix; changes visible to nurse and incharge.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## G. PERMISSIONS · ISOLATION · UI

### TC-HD-037 — Doctor cannot reach admin areas

`HOSPITAL_A · DOCTOR · Authorization · Critical · UI/API`
**Steps:** sidebar has no Settings/Audit/Nurses/Wards; API `GET /hospital/audit-logs`, `GET /hospital/nurses`, `POST /hospital/beds`, `PUT /hospital/form-access`.
**Expected:** 403 each (`TC-PERM-029`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-038 — Doctor cannot reach nurse workspace / notifications

`HOSPITAL_A · DOCTOR · Authorization · High · API`
**Steps:** `GET /hospital/nurse`, `GET /hospital/notifications`.
**Expected:** 403 both (`NUR` only).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-039 — Doctor tenant isolation

`HOSPITAL_A + B · DOCTOR · all · Critical · UI/API`
**Steps:** = `TC-ISO-005`, `006`, `017`, `018`, `020`, `050` with the doctor token.
**Expected:** 403/404 everywhere; no PDF bytes for B's records.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-040 — Deactivated doctor mid-session

`HOSPITAL_A · DOCTOR · Auth · Critical · API`
**Steps:** = `TC-PERM-033`.
**Expected:** record whether the captured token still works after deactivation (**Critical** if it does).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-041 — Module-off variants for the doctor

`HOSPITAL_M · DOCTOR · all · High · sidebar/API`
**Steps:** doctor of Hospital M (OPD-only): list tabs; call IPD/OT/ICU/Billing endpoints.
**Expected:** only Overview/Patients/OPD/Follow-ups; gated APIs 403; ungated ones recorded (`TC-MOD-010`…`012`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-042 — Consultation modal UI states

`HOSPITAL_A · DOCTOR · UI · Medium · ConsultationModal`
**Steps:** open/close via ✕, Cancel, ESC, backdrop; long diagnosis text; 15 medicines; scroll; disabled Complete while submitting.
**Expected:** no layout break; modal scrolls internally; submit disabled during the request.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-043 — Empty / loading / error per doctor tab

`HOSPITAL_A · DOCTOR · UI · Medium · all`
**Steps:** fresh tenant; slow network; backend down.
**Expected:** EmptyState, spinner, readable error (`DoctorDashboard.errorState` behaviour).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-044 — Long/odd patient data in consultation and PDFs

`HOSPITAL_A · DOCTOR · UI · Low · ConsultationModal/PDF`
**Steps:** patient with a 100-char name, blank address, no email; complete a consultation; print both PDFs.
**Expected:** wraps, no overflow, `—` for blanks, never `null`/`undefined`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-045 — Re-login persistence

`HOSPITAL_A · DOCTOR · Persistence · High · all`
**Steps:** logout/login; reopen consultations, prescriptions, follow-ups, IPD case.
**Expected:** all persist.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-046 — Two doctors, two tabs, simultaneous consultations

`HOSPITAL_A · DOCTOR ×2 · OPD · High · ConsultationModal`
**Steps:** Meera and Arjun each complete a consultation for **different** patients at the same time (two browser tabs).
**Expected:** each consultation/bill/prescription attaches to the right patient and doctor; no cross-contamination.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-047 — Same case opened by two doctors

`HOSPITAL_A · DOCTOR ×2 · OPD · Medium · ConsultationModal`
**Steps:** both open the same OPD case; both Complete.
**Expected:** the second gets a clear conflict/already-completed message — record behaviour; never two bills.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HD-048 — Single-doctor mode (hospital variant)

`HOSPITAL_A (isSingleDoctor) · HOSPITAL_ADMIN · Auth · High · Navbar`
**Steps:** = `TC-MODE-001`…`004` on a **hospital** tenant created with Single Doctor ON.
**Expected:** switcher present; admin completes consultations; no third role granted.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
