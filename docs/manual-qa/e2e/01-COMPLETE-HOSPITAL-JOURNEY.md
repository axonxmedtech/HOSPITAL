# 01 — COMPLETE HOSPITAL JOURNEY ("a day in the hospital")

**Cases:** `TC-E2E-001` … `TC-E2E-012` · Tenant **HOSPITAL_A** · all 8 actors

One continuous journey. Do **not** reset data between stages — the whole point is continuity
across handoffs. Execute the referenced case for each step, then verify the **E2E assertion**,
which no single-screen case can see.

---

## Journey state table — fill this in as you go

| #   | Stage             | Actor       | Record                                        | Identifier                            | Status before | Action                    | Status after                                 | Evidence         |
| --- | ----------------- | ----------- | --------------------------------------------- | ------------------------------------- | ------------- | ------------------------- | -------------------------------------------- | ---------------- |
| 1   | Tenant enabled    | S-SA        | Hospital                                      | `id=____`                             | —             | create + assign full plan | Active                                       | screenshot       |
| 2   | Staff             | S-ADM       | Doctor                                        | `____`                                | —             | create                    | Active                                       | screenshot       |
| 3   | Staff             | S-ADM       | Receptionist / Nurse / NI / Pharmacist / OT-I | `____`                                | —             | create                    | Active                                       | screenshot       |
| 4   | Rooms             | S-ADM       | Ward `General Ward A`                         | `____`                                | —             | create + set incharge     | —                                            | screenshot       |
| 5   | Rooms             | S-ADM       | Bed `GA-01`                                   | `____`                                | —             | create                    | **`available`**                              | screenshot       |
| 6   | Config            | S-ADM       | Fees / Vitals / Operations                    | —                                     | —             | configure                 | saved                                        | screenshot       |
| 7   | Registration      | S-REC       | Patient                                       | **`PAT____`** id `__` publicId `____` | —             | create                    | `REGISTERED`, `isActive=1`                   | screenshot       |
| 8   | Appointment       | S-REC       | Appointment                                   | `____`                                | —             | book                      | **`SCHEDULED`**                              | screenshot       |
| 9   | OPD               | S-REC       | OPD case                                      | `____`                                | —             | create                    | **`QUEUED`**                                 | screenshot       |
| 10  | Consultation      | S-DOC       | OPD case                                      | same                                  | `QUEUED`      | start                     | patient → `CONSULTING`                       | screenshot       |
| 11  | Consultation      | S-DOC       | OPD case                                      | same                                  | `CONSULTING`  | complete                  | OPD → **`COMPLETED`**, patient → `COMPLETED` | case paper PDF   |
| 12  | Prescription      | S-DOC       | Prescription                                  | `____`                                | —             | create                    | saved                                        | prescription PDF |
| 13  | Lab               | S-DOC       | LabOrder ×2                                   | —                                     | —             | order                     | on case paper                                | case paper PDF   |
| 14  | Billing           | S-REC       | Bill                                          | `____`                                | —             | generated                 | **`PENDING`**                                | screenshot       |
| 15  | Payment           | S-REC       | Bill                                          | same                                  | `PENDING`     | pay full                  | **`PAID`**                                   | receipt PDF      |
| 16  | Dispensing        | S-PHA       | Sale                                          | `____`                                | —             | dispense Rx               | created                                      | invoice PDF      |
| 17  | Stock             | system      | Batch `QA-B1`                                 | `____`                                | qty `__`      | sale −5                   | qty `__ − 5`                                 | screenshot       |
| 18  | Admission request | S-DOC       | OPD case                                      | same                                  | `COMPLETED`   | Admit to IPD              | `ipdAdmitRecommended=true`                   | screenshot       |
| 19  | Admission         | S-REC       | IPD                                           | `____` IPD no. `____`                 | —             | admit to `GA-01`          | **`ADMITTED`**                               | screenshot       |
| 20  | Bed               | system      | `GA-01`                                       | same                                  | `available`   | assign                    | **`occupied`**                               | screenshot       |
| 21  | OPD link          | system      | OPD case                                      | same                                  | `COMPLETED`   | admission                 | **`IN_IPD`**                                 | screenshot       |
| 22  | Assignment        | S-NI        | Patient→Nurse                                 | —                                     | Unassigned    | assign                    | assigned                                     | screenshot       |
| 23  | Nursing           | S-NUR       | Vitals / Notes / Assessment                   | `____`                                | —             | record                    | saved, attributed to nurse                   | screenshot       |
| 24  | Task              | S-NI→S-NUR  | Task                                          | `____`                                | `PENDING`     | start→complete            | **`COMPLETED`**                              | screenshot       |
| 25  | IPD charges       | S-DOC/S-ADM | Rx + hospital items                           | —                                     | —             | administer                | on IPD bill                                  | screenshot       |
| 26  | Discharge plan    | S-DOC       | IPD                                           | same                                  | `ADMITTED`    | plan discharge            | still `ADMITTED`                             | screenshot       |
| 27  | Discharge         | S-REC       | IPD                                           | same                                  | `ADMITTED`    | confirm                   | **`DISCHARGED`**                             | screenshot       |
| 28  | Bed               | system      | `GA-01`                                       | same                                  | `occupied`    | discharge                 | **`cleaning`**                               | screenshot       |
| 29  | Bed               | S-NI        | `GA-01`                                       | same                                  | `cleaning`    | mark cleaned              | **`available`**                              | screenshot       |
| 30  | Final bill        | S-REC       | IPD bill                                      | `____`                                | `PENDING`     | pay                       | **`PAID`**                                   | receipt PDF      |
| 31  | Follow-up         | S-REC       | Follow-up                                     | `____`                                | —             | from consultation         | Upcoming                                     | screenshot       |
| 32  | Reports           | S-ADM       | Overview / Reports                            | —                                     | —             | read                      | reconciles                                   | screenshot       |
| 33  | Audit             | S-ADM       | Audit log                                     | —                                     | —             | read                      | all actions present                          | screenshot       |

---

### TC-E2E-001 — Stages 1–6: setup reaches every downstream screen

`HOSPITAL_A · SA + ADM · Setup · Critical`
**Steps:** `TC-SA-018` · `TC-SA-007` · `TC-HA-004` · `TC-HA-011` · `TC-HA-014` · `TC-HA-016` · `TC-HA-025` · `TC-HWB-001` · `TC-HWB-007` · `TC-HAC-019` · `TC-HAS-010`.
**E2E assertion:** after setup and **without any re-login**, the doctor created in stage 2 is offered in **both** reception pickers (Appointment and OPD), the ward is selectable in IPD admit, the bed shows `available` in **all four** views (`TC-HWB-015`), and the fee set in stage 6 is the figure the consultation later pre-fills.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-002 — Stages 7–9: one patient identity flows into appointment and OPD

`HOSPITAL_A · REC · Patients/OPD · Critical`
**Steps:** `TC-HR-001` · `TC-HR-021` · `TC-HR-029`.
**E2E assertion:** the **same numeric id, publicId and `PAT` number** appear on the patient row, the appointment row and the OPD case. Record all three in the state table. `customId == "PAT" + id`. The OPD case opens at **`QUEUED`**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-003 — Stages 10–13: the consultation handoff

`HOSPITAL_A · DOC · OPD · Critical`
**Steps:** `TC-HD-004` · `TC-HD-005` · `TC-HD-008` · `TC-HD-017` · `TC-HD-018`.
**E2E assertion:** the consultation modal opens on **the patient reception created**, showing reception's vitals; on completion the OPD moves `QUEUED → COMPLETED` and the **patient** moves `CONSULTING → COMPLETED`; both PDFs carry the patient's `PAT` number and the hospital header; the lab orders picked appear on the case paper.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-004 — Stages 14–15: one visit produces exactly one bill

`HOSPITAL_A · REC · Billing · Critical`
**Steps:** `TC-HB-001` · `TC-HB-003` · `TC-HB-008`.
**E2E assertion:** **exactly one** bill exists for this visit, amount = configured consultation fee + case-paper fee (+ in-clinic items); it is `PENDING` before payment and `PAID` after; the Overview collection rises by **exactly** that amount and by nothing else.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-005 — Stages 16–17: prescription reaches the pharmacist and moves stock

`HOSPITAL_A · DOC → PHA · Pharmacy · Critical`
**Steps:** `TC-HP-010` · `TC-HP-011` or `TC-HP-012` · `TC-HA-034`.
**E2E assertion:** the pharmacist's Prescriptions row names **this** patient with **this** `PAT` number and links back to **this** consultation; the sale decrements **only** the chosen batch, by exactly the quantity sold; the invoice carries the same `PAT` number.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-006 — Stages 18–21: admission links OPD, patient, ward and bed

`HOSPITAL_A · DOC → REC · IPD · Critical`
**Steps:** `TC-HD-023` · `TC-HR-039`.
**E2E assertion:** the admission carries **the same patient identity**; a unique IPD number is issued; `GA-01` moves `available → occupied` **in all four views simultaneously** and disappears from reception's bed picker; **the source OPD case moves to `IN_IPD`**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-007 — Stages 22–24: nursing sees the patient only after assignment

`HOSPITAL_A · NI → NUR · Nursing · Critical`
**Steps:** `TC-HNI-006` · `TC-HN-004` · `TC-HN-008` · `TC-HN-010` · `TC-HN-023`.
**E2E assertion:** before assignment the staff nurse sees **nothing**; after assignment she sees exactly this patient; every record she writes is attributed to **her** and is immediately visible to the doctor in `/ipd/:id`; the task moves `PENDING → COMPLETED` and the admin sees the change.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-008 — Stages 25–30: discharge, bed release and the final bill

`HOSPITAL_A · DOC → REC → NI · IPD/Billing/Beds · Critical`
**Steps:** `TC-HA-035` · `TC-HD-024` · `TC-HD-025` · `TC-HB-010` · `TC-HB-011` · `TC-HNI-014`.
**E2E assertion:** every chargeable event from stages 23–25 appears on the IPD bill; on confirm-discharge the admission becomes `DISCHARGED` and `GA-01` becomes **`cleaning` — not `available`**; the bed is still absent from the admit picker; only after the incharge marks it cleaned does it return to `available` and become admissible again.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-009 — Stages 31–33: follow-up, reports and audit reconcile the whole day

`HOSPITAL_A · REC → ADM · Reports/Audit · Critical`
**Steps:** `TC-HD-011` · `TC-HAC-008` · `TC-HAC-012` · `TC-HA-033`.
**E2E assertion:** count by hand from the state table — 1 patient, 1 appointment, 1 OPD, 1 consultation, 1 prescription, 1 sale, 1 admission, 1 discharge, 2 bills, 1 follow-up. **Every figure on Overview, Reports and the pharmacy dashboard must equal that hand count**, and the Audit Log must contain an entry for every action with the correct acting user.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-010 — Optional branch: OT during the stay

`HOSPITAL_A · DOC + OTI + NUR · OT · High`
**Steps:** the full `TC-HX-008` (H4) inserted between stages 24 and 26.
**E2E assertion:** the surgery attaches to **this** admission and **this** patient; every NABH form prints the **patient's** UHID, never the hospital's; on completion the OT bed goes to `cleaning`; the surgery appears on this patient's IPD case and in OT Analytics exactly once.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-011 — Optional branch: ICU during the stay

`HOSPITAL_A · REC + DOC + NUR + NI · ICU · High`
**Steps:** the full `TC-HX-010` (H5), transferring this patient into `ICU-01` and back out.
**E2E assertion:** the ICU stay is created on transfer in and closed on transfer out; the general bed and the ICU bed each pass through `cleaning`; the alert threshold fires on a low SpO2 and clears; the patient never appears in two beds at once.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-012 — ⭐ Whole-journey identity invariant

`HOSPITAL_A · all · Patient identity · Critical`
**Steps:** none new — re-read the evidence you collected.
**E2E assertion:** open every artefact produced in stages 7–33 side by side: patient row, appointment, OPD case, case paper PDF, prescription PDF, pharmacy invoice, OPD bill receipt, IPD admission, nursing records, IPD bill receipt, follow-up row, document list, audit entries.
**The same numeric id, the same publicId, the same `PAT` number and the same patient name must appear on every single one.** Any artefact naming a different patient is **Critical** — capture it and stop.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
