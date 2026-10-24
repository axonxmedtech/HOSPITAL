# 04 — CLINIC DOCTOR

**Baseline:** `aa143a7` · **Cases:** `TC-CDR-001` … `TC-CDR-020` · Tenant CLINIC_A · `doc.clina@qa.test` → `/hospital/doctor`

**Tabs (clinic):** Overview · Patients · Appointments · OPD · Follow-ups · Billing · Medicine Inventory.
**Absent:** IPD, Operation Theatre, ICU Dashboard, ICU Bed Board.

---

### TC-CDR-001 — Clinic doctor landing and tab set

`CLINIC_A · DOCTOR · Auth · Critical · /hospital/doctor`
**Steps:** log in at `/login/clinic`; list tabs; `GET /auth/me`; watch the Network namespace.
**Expected:** lands `/hospital/doctor`; `hospitalType: CLINIC`; the seven tabs above and no IPD/OT/ICU; calls go to `/clinic/...`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-002 — Queue and own-cases scope

`CLINIC_A · DOCTOR · OPD · High · Overview/OPD`
**Steps:** = `TC-HD-001`/`002` as clinic, with two clinic doctors.
**Expected:** the queue is this doctor's; record whether the OPD tab lists the colleague's cases (`NEEDS_PRODUCT_CONFIRMATION`, same open question as hospital).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-003 — Start consultation shows the right patient

`CLINIC_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** = `TC-HD-004` as clinic.
**Expected:** header shows the patient's name and `PAT` id and reception's vitals; patient status `CONSULTING`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-004 — Complete consultation: symptoms, diagnosis, notes, medicines

`CLINIC_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** = `TC-HD-005` as clinic.
**Expected:** `Consultation submitted successfully`; OPD `COMPLETED`; **one** bill; prescription saved.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-005 — Medicine autocomplete and unresolved text

`CLINIC_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** = `TC-HD-006` as clinic; the catalogue is the shared platform list plus clinic stock.
**Expected:** unresolved free text is not silently prescribed; edit/remove persist.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-006 — ⭐ Lab ordering in a clinic (PARTIAL)

`CLINIC_A · DOCTOR · OPD · High · ConsultationModal`
**Steps:** = `TC-CD-027`.
**Expected:** `LabOrder` rows created and printed on the case paper; **no results UI** — not a bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-007 — Presets applied in a clinic consultation

`CLINIC_A · DOCTOR · OPD · Medium · ConsultationModal`
**Steps:** = `TC-HD-007` with the presets from `TC-CA-013`.
**Expected:** symptom/diagnosis/prescription presets fill the fields; saving a new preset works.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-008 — In-clinic items section follows the In-Clinic setting

`CLINIC_A · DOCTOR · OPD · Medium · ConsultationModal`
**Steps:** = `TC-HD-009` with `TC-CA-008`'s In-Clinic toggle.
**Expected:** section present when ON and billed; absent when OFF; the In-Clinic Presets tab follows live.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-009 — Fees inside the consultation

`CLINIC_A · DOCTOR · Billing · Critical · ConsultationModal`
**Steps:** = `TC-HD-010` with the clinic fee from `TC-CA-006`.
**Expected:** consultation fee prefilled from the clinic's settings, not a hospital's.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-010 — Follow-up set during consultation

`CLINIC_A · DOCTOR · Follow-ups · High · ConsultationModal`
**Steps:** = `TC-HD-011` as clinic.
**Expected:** appears under Upcoming for doctor, reception and admin.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-011 — Save Changes on a completed consultation

`CLINIC_A · DOCTOR · OPD · High · ConsultationModal`
**Steps:** = `TC-HD-012` as clinic.
**Expected:** edit persists without re-billing or duplicating the prescription.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-012 — Validation, failure, refresh, double-submit

`CLINIC_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** = `TC-HD-013`, `014`, `015` as clinic.
**Expected:** required-field messages; **one** consultation, **one** bill, **one** prescription on a double click; readable error and clean retry on backend failure.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-013 — ⭐ Wrong-patient guard with the clinic family

`CLINIC_A · DOCTOR · OPD · Critical · ConsultationModal`
**Steps:** = `TC-HD-016` using the Rane family (`TC-CD-021`…`023`): create OPD cases for **Ira** (child) and **Nitin** (father); consult Ira's case.
**Expected:** the consultation, prescription, bill and both PDFs carry **Ira's** name and `PAT` id — never the father's.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-014 — Prescription and case-paper PDFs

`CLINIC_A · DOCTOR · Documents · High · PDFs`
**Steps:** = `TC-HD-017`/`018` as clinic; = `TC-CD-028`.
**Expected:** clinic header; patient identifier (never the tenant's); enabled-vitals table; lab orders listed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-015 — ⚠️ Prescription PDF role check (drift) in the clinic namespace

`CLINIC_A · PHARMACIST/REC · Documents · High · API`
**Steps:** = `TC-API-007` against `/clinic/doctors/prescription/opd/{id}/pdf` with the clinic pharmacist and receptionist tokens; then with **CLINIC_B** and **HOSPITAL_A** tokens.
**Expected:** same-tenant result **recorded** (`NEEDS_PRODUCT_CONFIRMATION`); **cross-tenant must be 403/404** — a 200 is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-016 — Patient documents and history

`CLINIC_A · DOCTOR · Documents · High · View Details`
**Steps:** = `TC-HD-021`/`022` as clinic (`/clinic` alias on `PatientDocumentController`): upload a synthetic file, preview, download, archive; open History.
**Expected:** works; oversize/disallowed types rejected readably; history shows only this patient.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-017 — Doctor patient/appointment rights in a clinic

`CLINIC_A · DOCTOR · Patients/Appointments · High · API`
**Steps:** = `TC-HD-028`/`029` against `/clinic/...`: GET/POST/PUT/DELETE patients; POST appointments.
**Expected:** PUT patients **403** (`ADM REC`), DELETE allowed (`ADM DOC`), appointment POST **403** (`ADM REC`) — confirm the UI matches; any mismatch is `UI_WITHOUT_BACKEND`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-018 — ⚠️ Clinic doctor cannot admit or reach hospital-only domains

`CLINIC_A · DOCTOR · Authorization · Critical · UI/API`
**Steps:** 1. On a clinic OPD case look for **Admit to IPD** — it must be absent (`TC-CD-010`). 2. With the doctor token run `TC-CD-011`, `013`, `014`, `016`, `017`, `018`.
**Expected:** no admit control; ICU/OT/nursing closed (404/403); IPD/ward/bed results **recorded** — a clinic doctor creating an admission request is `IMPLEMENTATION_DRIFT`, **HIGH**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-019 — Clinic doctor isolation

`CLINIC_A + CLINIC_B + HOSPITAL_A · DOCTOR · Isolation · Critical · API`
**Steps:** = `TC-HD-039` adapted: fetch and modify another tenant's patient, OPD, consultation, prescription PDF and document by numeric id and publicId.
**Expected:** 403/404 with no clinical content; target tenants unchanged.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CDR-020 — Clinic doctor UI, persistence, module-off variant

`CLINIC_B · DOCTOR · multi · Medium · all`
**Steps:** = `TC-HD-042`…`045` as clinic; then on CLINIC_B (OPD-only plan) list tabs and call `/clinic/billing`, `/clinic/appointments`, `/clinic/medicines`, `/clinic/stats`.
**Expected:** UI states and persistence as hospital; on the minimal plan those four tabs are hidden and all four endpoints **403**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
