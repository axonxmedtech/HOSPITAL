# 06 — CLINIC CROSS-ROLE WORKFLOWS

**Baseline:** `aa143a7` · **Cases:** `TC-CX-001` … `TC-CX-010` · Tenant CLINIC_A unless stated

## Session convention

| Label      | Context               | Account                          |
| ---------- | --------------------- | -------------------------------- |
| **S-CADM** | Chrome tab 1          | `admin.clina@qa.test`            |
| **S-CREC** | Chrome tab 2          | `rec.clina@qa.test`              |
| **S-CDOC** | Chrome tab 3          | `doc.clina@qa.test`              |
| **S-CPHA** | Chrome tab 4          | `pharm.clina@qa.test`            |
| **S-SA**   | Incognito window      | Super Admin at `/platform/login` |
| **S-CB**   | Second browser        | `admin.clinb@qa.test` — CLINIC_B |
| **S-HA**   | Second browser, tab 2 | `rec.hospa@qa.test` — HOSPITAL_A |

All clinic logins start at **`/login/clinic`**.

---

## C1 — Admin setup chain

### TC-CX-001 — C1: admin creates doctor, receptionist, fee and time-slot equivalent

`CLINIC_A · ADM · multi · Critical`
**Steps**

1. **S-CADM** Staff ▸ Doctors → create `Dr Sanjay Bhosale` / `doc.clina@qa.test`.
2. Staff ▸ Receptionists → create `Ashwini Kadam` / `rec.clina@qa.test`.
3. Finance ▸ Fees → consultation **555**, follow-up **111**, custom fee `Dressing` 50.
4. Settings ▸ Vitals → disable SpO2, add custom vital `QA Clinic Pain`.
5. Settings ▸ Operations → Billing Handler `RECEPTIONIST`, Bill Payment `After OPD`.
6. **S-CDOC** and **S-CREC** log in; confirm landing and tab sets.
7. **S-CREC** open Add Appointment → the doctor is offered; open Add OPD → the vitals form shows `QA Clinic Pain` and **no SpO2**.
   **Expected:** every configuration reaches the downstream screen without a re-login (settings are read live); the doctor appears in both pickers.
   **Note:** **Time Slots and Calendar do not exist in a clinic** (NURSING-gated) — the appointment slot list comes from the appointment rules, not the nursing shift templates. Record which slots the clinic appointment form offers.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C2 — Patient → appointment → consultation → billing → follow-up

### TC-CX-002 — C2: full clinic outpatient journey

`CLINIC_A · REC+DOC+ADM · multi · Critical`
**Steps**

1. **S-CREC** Patients → **Add Patient** `Clinic Journey`, `9900091001`, DOB 1992-05-05, Female. Record `id`/`publicId`/`customId`.
2. **S-CADM** Patients → the patient is visible immediately.
3. **S-CREC** Appointments → **Add Appointment** → existing patient → Dr Sanjay → tomorrow → a free slot → **Schedule**.
4. **S-CDOC** Appointments → the appointment is listed.
5. **S-CREC** OPD → **Add OPD** → existing patient → Dr Sanjay → BP `120/80`, `QA Clinic Pain` value → Problem `Cough` → **Create OPD**.
6. **S-CDOC** queue → **Start Consultation** → header shows `Clinic Journey` + `PAT<id>` → symptoms, diagnosis, notes → add a medicine → tick lab `CBC` → follow-up **+5 days** → **Complete Consultation**.
7. **S-CDOC** **Print Prescription** and **Print Case Paper**.
8. **S-CREC** Billing → bill `PENDING` at **555 + case-paper fee** → **Mark Paid** → **Print** receipt.
9. **S-CADM** Overview → collection increased by exactly that amount; Reports & Analytics agree.
10. **S-CREC** Follow-ups → the row is under **Upcoming**; open the source consultation.
11. **S-CADM** Audit Logs → every action recorded with the acting user.
    **Expected:** one patient, one appointment, one OPD, one consultation, one bill, one prescription, one follow-up, one lab order. **Every PDF carries the clinic's name and the patient's `PAT` id.** Figures reconcile at step 9.
    **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CX-003 — C2 variant: Bill Payment = Before OPD

`CLINIC_A · ADM+REC+DOC · Billing · High`
**Steps:** **S-CADM** set Bill Payment `Before OPD`; repeat C2 steps 5–9.
**Expected:** payment fields required at OPD entry; the bill is `PAID` from creation; completion creates **no second bill**. Restore the setting.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C3 — ⭐ Parent/child shared phone in a clinic

### TC-CX-004 — C3: the clinical record must attach to the CHILD

`CLINIC_A · REC+DOC+PHA · Patients · Critical`
**Steps**

1. **S-CREC** register father `Nitin Rane`, `9900090010`, DOB 1984-02-20. Record `id_father`.
2. **S-CREC** register child `Ira Rane`, same phone, DOB 2019-07-05 → chooser lists **only Nitin** (name, age, `PAT` id — no phone/DOB/address) → **Register Different Patient**. Record `id_child`.
3. **S-CREC** register mother `Asmita Rane`, same phone → chooser lists **both** → click **Use This Patient on Ira** → confirm **nobody is created** and the flow continues with `id_child`.
4. Repeat step 3 and this time **Register Different Patient** → the mother is created.
5. **S-CREC** Appointments → **Add Appointment** → search `9900090010` → all three offered → pick **Ira** → Schedule.
6. **S-CREC** OPD → **Add OPD** for **Ira**.
7. **S-CDOC** **Start Consultation** → header **must read `Ira Rane` / `PAT<id_child>`** → paediatric prescription → follow-up → Complete.
8. **S-CDOC** print both PDFs → **both name Ira, never Nitin**.
9. **S-CPHA** Prescriptions → the row shows **Ira** → dispense → invoice names **Ira**.
10. **S-CREC** Billing → the bill sits under **Ira**; open Nitin's bills → **the child's bill is absent**.
11. **S-CREC** Patients → open Nitin → unchanged: same name, phone, no records from this visit.
12. **S-CADM** Audit Logs → two `PATIENT_DUPLICATE_PHONE_ACKNOWLEDGED` entries with the phone **masked**.
    **Expected:** at every step the appointment, OPD, consultation, prescription, invoice and bill attach to **Ira**. The chooser never exposes phone, DOB, address or email. The father's record is untouched.
    ⚠️ **Do not assert** that two simultaneous registrations on one number are impossible — the database backstop (Phase D) has not shipped.
    **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CX-005 — C3 cross-tenant: the same family phone elsewhere

`CLINIC_A + CLINIC_B + HOSPITAL_A · REC · Patients · Critical`
**Steps:** = `TC-CD-024`: register on `9900090010` in **CLINIC_B**, then in **HOSPITAL_A**; and on HOSPITAL_A's `9900080001` in **CLINIC_A**.
**Expected:** no chooser, or a chooser listing **only that tenant's own** patients. Any Rane or Kale name crossing tenants is a **Critical** disclosure.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C4 — Prescription → clinic pharmacy

### TC-CX-006 — C4: clinic dispensing chain

`CLINIC_A · ADM+DOC+PHA · Pharmacy · Critical`
**Steps**

1. **S-CADM/S-CPHA** Inventory → `QA Clinic Paracetamol` batch `QC-B1` qty **60**. Record the exact figure.
2. **S-CDOC** complete a consultation for `Clinic Journey` prescribing it.
3. **S-CPHA** **Prescriptions** → the row appears with the patient's `PAT` id → **See Consultation** opens the clinic OPD.
4. **S-CPHA** Billing Counter ▸ **Hospital Rx Mode** → load the patient → batch `QC-B1` → qty **4** → `CASH` → complete → print the invoice.
5. **S-CPHA** Inventory → **56**.
6. **S-CPHA** Dashboard and Reports → today's sales include it; **S-CADM** Overview pharmacy card agrees and the billing collection is **not** double-counted.
7. **S-CPHA** Returns ▸ `PATIENT` → **Search Bill** → refund 1 → **Process Patient Refund** → Inventory **57**.
   **Expected:** one sale, one decrement, one refund, one increment; the invoice carries the clinic header and the patient's `PAT` id.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C5 — Module revocation mid-session

### TC-CX-007 — C5: revoke APPOINTMENTS while a clinic user is working

`PLATFORM → CLINIC_A · SA + ADM + REC · Entitlement · Critical`
**Steps**

1. **S-CREC** open Appointments; in DevTools **Copy as cURL** the appointment-list request; keep the token. Do not log out.
2. **S-SA** edit the clinic plan to remove **APPOINTMENTS** (or assign `QA-CLINIC-MIN`).
3. **S-CREC** click another tab, then refresh.
4. Replay the captured cURL with the still-valid token.
5. **S-SA** restore the module.
6. **S-CREC** refresh → the tab returns → open Appointments.
   **Expected:** the Appointments tab disappears after refresh; the replayed call returns **403** even with a valid token; **the previously created appointments still exist** on restore. A **200** at step 4 is a **Critical** entitlement bypass.
   **Note:** repeat with **OPD** and observe the difference — OPD has **no backend gate** (`TC-CD-037`), so the tab hides while the API stays open. That contrast is the finding.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C6 / C7 — Isolation

### TC-CX-008 — C6: CLINIC_A resource vs CLINIC_B user

`CLINIC_A + CLINIC_B · all roles · Isolation · Critical`
**Steps** — from **S-CB** with CLINIC_B's tokens:

1. List and search patients for `Nitin Rane`, `Ira Rane`, `9900090010`, their `PAT` ids.
2. Fetch each CLINIC_A record by **numeric id** and by **publicId**: patient, appointment, OPD, bill, prescription, document, sale, staff user.
3. Attempt to modify: update the patient, pay the bill, refund the sale, reset the doctor's password.
4. Attempt to link: create an OPD/appointment in CLINIC_B using CLINIC_A's `patientId`.
5. Attempt to download: prescription PDF, case paper, receipt, clinical document.
6. Check every dropdown in CLINIC_B — patient, doctor, medicine batch.
7. Compare CLINIC_B's Overview, Reports and Audit Logs against CLINIC_B's own hand counts.
   **Expected:** every read **403/404** with **no** name, phone, DOB, amount or filename in the body; every write refused **and CLINIC_A verified unchanged afterwards**; no CLINIC_A row in any dropdown, report or total. **Any 200 carrying CLINIC_A data stops testing immediately — Critical.**
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-CX-009 — C7: clinic resource vs hospital user, and the reverse

`CLINIC_A + HOSPITAL_A · all roles · Isolation · Critical`
**Steps**

1. **S-HA** (HOSPITAL_A receptionist) fetch CLINIC_A's patient/OPD/bill/document by numeric id and publicId on **both** `/hospital/...` and `/clinic/...`.
2. **S-CREC** (CLINIC_A) do the same against HOSPITAL_A's records on both namespaces.
3. Attempt modifications in both directions.
4. **S-HA** attempt `GET /clinic/patients` (role is admitted to `/clinic/**` — only tenant scoping stands).
   **Expected:** 403/404 everywhere; both tenants verified unchanged. Step 4 is the sharpest case: a hospital receptionist **is** authorized on the clinic namespace by role, so if `hospitalId` scoping ever slipped, this is where it would show.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C8 — Hospital-only workflow attempted from a clinic

### TC-CX-010 — C8: clinic attempts IPD, wards, beds, ICU, OT, nursing

`CLINIC_A · ADM+REC+DOC · Authorization · Critical`
**Steps** — run the full `/clinic` drift battery and record every status in one table:

| Attempt            | Endpoint                                    | Method | Role        | Expected (policy) | Actual | Data returned? | Row created? |
| ------------------ | ------------------------------------------- | ------ | ----------- | ----------------- | ------ | -------------- | ------------ |
| IPD list           | `/clinic/ipd`                               | GET    | ADM/REC/DOC | 403               | ____   | ____           | —            |
| IPD lookup         | `/clinic/ipd/{HOSPITAL_A id}`               | GET    | ADM         | 403/404           | ____   | ____           | —            |
| **IPD admit**      | `/clinic/ipd/admit`                         | POST   | REC         | 403               | ____   | —              | ____         |
| IPD discharge      | `/clinic/ipd/{id}/confirm-discharge`        | POST   | REC         | 403               | ____   | —              | ____         |
| Ward list          | `/clinic/wards`                             | GET    | ADM         | 403               | ____   | ____           | —            |
| **Ward create**    | `/clinic/wards`                             | POST   | ADM         | 403               | ____   | —              | ____         |
| Bed list           | `/clinic/beds`                              | GET    | ADM         | 403               | ____   | ____           | —            |
| **Bed create**     | `/clinic/beds`                              | POST   | ADM         | 403               | ____   | —              | ____         |
| ICU                | `/clinic/icu` · `/hospital/icu`             | GET    | ADM         | 404 · 403         | ____   | ____           | —            |
| OT                 | `/clinic/surgeries` · `/hospital/surgeries` | GET    | ADM         | 404 · 403         | ____   | ____           | —            |
| Nursing            | `/clinic/nurses` · `/hospital/nurses`       | GET    | ADM         | 404 · 403         | ____   | ____           | —            |
| Hospital dashboard | `/hospital/dashboard`                       | GET    | ADM         | 403               | ____   | ____           | —            |
| Hospital inventory | `/clinic/hospital-inventory`                | GET    | ADM         | 403               | ____   | ____           | —            |

**Also check the UI:** no IPD/Wards/ICU/OT/Nursing tab; no **Admit to IPD** control on a clinic OPD case.

**Expected:** ICU, OT, nursing, `/hospital/dashboard` and hospital-inventory are genuinely closed. **The IPD, ward and bed rows are the known drift** — a 200, and especially a created row, is a **FAIL / IMPLEMENTATION_DRIFT** at **HIGH**, or **Critical** if it touched another tenant's data. Record row ids; do not clean up.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
