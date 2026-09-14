# 05 — PATIENT IDENTITY JOURNEY (PI-1 … PI-10)

**Cases:** `TC-E2E-013` … `TC-E2E-024` · **The most important document in the pack.**

## The one assertion everything here defends

> **No clinical record, prescription, bill, admission, document or pharmacy transaction may ever
> attach to another family member merely because they share a phone number.**

A phone number is a **lookup key, not an identity**. A parent and a child legitimately share one
mobile. The product therefore refuses to guess and asks a human — this document proves that the
human's answer is honoured everywhere, at every downstream stage.

**Standing limitation:** Phase A has **no database uniqueness backstop** (Phase D not shipped).
Test the _workflow_. **Do not assert that two simultaneous registrations on one number cannot both
succeed.**

## Identity fields to record at every stage

| Field                      | Where it comes from                                               | Why it matters                                                   |
| -------------------------- | ----------------------------------------------------------------- | ---------------------------------------------------------------- |
| **numeric `id`**           | `POST /…/patients` response; used by OPD/appointment/IPD payloads | what the workflow actually links on                              |
| **`publicId`** (UUID)      | same response                                                     | **globally unique across tenants** — the highest-risk identifier |
| **`customId`** = `PAT<id>` | shown in the UI as Patient ID                                     | what staff read aloud and what prints on PDFs                    |
| **name + age**             | the only two fields the conflict chooser exposes                  | what a human uses to decide                                      |

## Fixture — build once, reuse for PI-1…PI-10

| Ref    | Name                | DOB        | Phone        | Tenant                      | Purpose             |
| ------ | ------------------- | ---------- | ------------ | --------------------------- | ------------------- |
| **F0** | Solo Patient        | 1988-01-01 | `9900095000` | HOSPITAL_A                  | unique phone (PI-1) |
| **F1** | Vikas Kale (father) | 1985-06-15 | `9900080001` | HOSPITAL_A                  | PI-2                |
| **F2** | Ishaan Kale (child) | 2018-03-10 | `9900080001` | HOSPITAL_A                  | PI-2/PI-3           |
| **F3** | Sneha Kale (mother) | 1990-09-02 | `9900080001` | HOSPITAL_A                  | PI-3                |
| **F4** | Vikas Kale          | 1985-06-15 | `9900080001` | **HOSPITAL_B**              | PI-4                |
| **F5** | Nitin Rane          | 1984-02-20 | `9900090010` | **CLINIC_A**                | PI-5                |
| **F6** | Old Record          | 1970-07-07 | `9900033333` | HOSPITAL_A, **deactivated** | PI-9                |

```
F0 id=____ publicId=____________________ PAT____
F1 id=____ publicId=____________________ PAT____
F2 id=____ publicId=____________________ PAT____
F3 id=____ publicId=____________________ PAT____
F4 id=____ publicId=____________________ PAT____   (HOSPITAL_B)
F5 id=____ publicId=____________________ PAT____   (CLINIC_A)
```

---

### TC-E2E-013 — PI-1: a unique phone number never asks

`HOSPITAL_A · RECEPTIONIST · Patients · Critical`
**Steps:** `TC-HR-001` with F0.
**E2E assertion:** **no chooser appears**; the patient is created with `customId == "PAT" + id`; the ack fields (`duplicatePhoneAckFor/At/By`) are **all null** on the response; the audit shows `PATIENT_CREATED` and **no** acknowledgement entry. This is the control case — everything else is measured against it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-014 — PI-2: father then child on one number

`HOSPITAL_A · RECEPTIONIST · Patients · Critical`
**Steps:** `TC-HR-005` then `TC-HR-006` with F1 and F2.
**E2E assertion:** the chooser lists **only** F1, showing **name, age and Patient ID and nothing else** — no phone, no DOB, no address, no email (`TC-HR-011`). **Use This Patient** creates nobody. **Register Different Patient** creates F2 with its own `id`/`publicId`/`PAT`, records `duplicatePhoneAckFor = 9900080001`, and leaves **F1 completely unchanged** (same name, phone, no ack fields).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-015 — PI-3: three family members, exact selection

`HOSPITAL_A · RECEPTIONIST · Patients · Critical`
**Steps:** `TC-HR-007` with F3.
**E2E assertion:** the chooser now lists **both** F1 and F2 in registration order, each with its own button. Clicking **Use This Patient on the child** hands back **F2's** id — verify the id in the resulting workflow, not just the name on screen. A second **Register Different Patient** creates F3. Three active patients now share one number, each with its own `PAT`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-016 — PI-4: the same phone in another hospital

`HOSPITAL_A + HOSPITAL_B · RECEPTIONIST · Patients · Critical`
**Steps:** `TC-ISO-010` / `TC-HX-013` — register F4 in HOSPITAL_B on `9900080001`.
**E2E assertion:** HOSPITAL_B's chooser either does not appear or lists **only HOSPITAL_B patients**. **No Kale name from HOSPITAL_A may appear** — the 409 body would otherwise disclose another tenant's patient name and age. Then repeat in the other direction. Both tenants keep independent `PAT` numbers for the same human being; that is correct.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-017 — PI-5: the same phone across tenant **types**

`HOSPITAL_A + CLINIC_A · RECEPTIONIST · Patients · Critical`
**Steps:** `TC-CD-024` / `TC-CX-005` — register on `9900080001` in CLINIC_A, and on `9900090010` (CLINIC_A's Rane number) in HOSPITAL_A.
**E2E assertion:** neither chooser shows the other tenant's family. Crossing tenant **types** must be no more permissive than crossing tenants of the same type — and here the API namespaces differ (`/clinic` vs `/hospital`), which is exactly where a filter could be missed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-018 — PI-6: "Use This Patient" selects that exact patient in **every** entry point

`HOSPITAL_A · RECEPTIONIST · Patients/Appointments/OPD · Critical`
**Steps:** `TC-HR-005`, `TC-HR-023`, `TC-HR-030` — trigger the chooser from **all three** entry points (Add Patient, Add Appointment ▸ New Patient, Add OPD ▸ New Patient) and choose **the child** each time.
**E2E assertion:** in all three the resulting record (patient handoff, appointment, OPD) carries **F2's numeric id** — check the request payload in DevTools, not the label. **No new patient is created in any of the three.** A single match still asks: the product never auto-selects on a phone match.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-019 — PI-7: "Register Different Patient" from every entry point

`HOSPITAL_A · RECEPTIONIST · Patients/Appointments/OPD · Critical`
**Steps:** as PI-6 but choosing **Register Different Patient** each time.
**E2E assertion:** the second request carries `?acknowledgeDuplicatePhone=true`; a **new** patient is created with a new `PAT`; the acknowledgement is stored **server-side** with the acting user's email — a value forged in the request body is ignored (`TC-HR-012`); the audit entry masks the phone as `99******01`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-020 — PI-8: editing a phone number

`HOSPITAL_A · RECEPTIONIST · Patients · Critical`
**Steps:** `TC-VIS-007` and `TC-VIS-008`.
**E2E assertion:** three behaviours in one case —

1. Editing a patient **onto** an occupied number raises the chooser; Cancel leaves the phone unchanged.
2. Acknowledging saves with `duplicatePhoneAckFor` = the **new** number.
3. Moving the patient to a **free** number **clears** the acknowledgement, and an acknowledgement for X grants **nothing** on Y — moving F2 onto a third occupied number must raise the chooser again.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-021 — PI-9: an inactive patient does not reserve a number

`HOSPITAL_A · RECEPTIONIST · Patients · High`
**Steps:** `TC-VIS-004` with F6, then `TC-VIS-003`.
**E2E assertion:** registering on a deactivated patient's number raises **no chooser** and records **no acknowledgement**. The deactivated patient stays invisible in lists, search and every picker, **but their historical OPD case and prescription still open and still name them**. There is **no reactivation path** in this product — do not look for one (`status/IMPLEMENTATION-STATUS.md` §6).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## PI-10 — the full traversal

### TC-E2E-022 — ⭐ PI-10 (hospital): the child travels the entire system

`HOSPITAL_A · REC + DOC + PHA + NI + NUR · all modules · Critical`
**The single highest-value case in the entire QA pack.**

Run the **whole** `01-COMPLETE-HOSPITAL-JOURNEY.md` using **F2 (the child, Ishaan Kale)** as the
patient, while **F1 (the father)** and **F3 (the mother)** exist on the same number. Fill this
table at every stage; each row must read `PAT<F2>`:

| Stage          | Artefact                          | Patient shown | `PAT` number | Correct? |
| -------------- | --------------------------------- | ------------- | ------------ | -------- |
| Registration   | patient row                       | ____          | ____         | ☐        |
| Appointment    | appointment row + doctor's list   | ____          | ____         | ☐        |
| OPD            | OPD case + queue entry            | ____          | ____         | ☐        |
| Consultation   | ConsultationModal header          | ____          | ____         | ☐        |
| Prescription   | prescription PDF                  | ____          | ____         | ☐        |
| Case paper     | case paper PDF (incl. lab orders) | ____          | ____         | ☐        |
| Pharmacy       | Prescriptions row                 | ____          | ____         | ☐        |
| Pharmacy       | sale invoice PDF                  | ____          | ____         | ☐        |
| Billing        | OPD bill + receipt PDF            | ____          | ____         | ☐        |
| Follow-up      | follow-up row                     | ____          | ____         | ☐        |
| IPD            | admission + IPD number            | ____          | ____         | ☐        |
| Nursing        | My Patients + every clinical form | ____          | ____         | ☐        |
| Nursing        | printed nursing form (UHID field) | ____          | ____         | ☐        |
| OT _(if run)_  | surgery + each NABH form's UHID   | ____          | ____         | ☐        |
| ICU _(if run)_ | ICU stay + bed board              | ____          | ____         | ☐        |
| Discharge      | IPD bill + receipt                | ____          | ____         | ☐        |
| Documents      | uploaded document list            | ____          | ____         | ☐        |
| Audit          | every audit entry's subject       | ____          | ____         | ☐        |

**E2E assertion:** every row reads **F2**. Then open **F1's** record and **F3's** record and confirm each has **no** clinical record, prescription, bill, admission, document or pharmacy transaction from this journey. A single artefact naming the father or mother is **Critical** — capture it, stop the journey, and report immediately.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-023 — PI-10 (clinic): the same traversal in a clinic

`CLINIC_A · REC + DOC + PHA · OPD modules · Critical`
**Steps:** `TC-CX-004` in full, using the Rane child while the father and mother share the number.
**E2E assertion:** the same table as `TC-E2E-022`, minus IPD/nursing/OT/ICU (not supported in a clinic). PDFs must carry the **clinic's** header and the child's `PAT` number.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-024 — ⭐ Identity under isolation: the child's records are invisible to every other tenant

`HOSPITAL_A + B + CLINIC_A + B + PHARMACY_A + B · all · Isolation · Critical`
**Steps:** with F2's **numeric id** and **publicId** in hand, run the fetch/modify/download battery from `TC-E2E-085` (16-TENANT-ISOLATION-E2E) against every other tenant.
**E2E assertion:** no other tenant can fetch F2 by numeric id **or by publicId** (the publicId is globally unique — this is the sharpest test), download any F2 PDF, or see F2 in any list, search, dropdown or report. Every response is 403/404 with **no name, phone, DOB or `PAT` number in the body**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
