# 02 — COMPLETE CLINIC JOURNEY

**Cases:** `TC-E2E-043` … `TC-E2E-050` · Tenant **CLINIC_A** · login at `/login/clinic`

A clinic is an outpatient-only tenant. The journey ends at follow-up — there is no admission — and
the final case proves the hospital-only domains stay shut.

## State table

| #   | Stage        | Actor          | Entity                                  | Identifier                            | Before    | Action              | After           | Evidence    |
| --- | ------------ | -------------- | --------------------------------------- | ------------------------------------- | --------- | ------------------- | --------------- | ----------- |
| 1   | Tenant       | S-SA           | Clinic                                  | `id=____`                             | —         | create, clinic plan | Active          | screenshot  |
| 2   | Staff        | S-CADM         | Doctor / Receptionist / Pharmacist      | `____`                                | —         | create              | Active          | screenshot  |
| 3   | Config       | S-CADM         | Fees 555 · Vitals · Operations          | —                                     | —         | configure           | saved           | screenshot  |
| 4   | Registration | S-CREC         | Patient                                 | **`PAT____`** id `__` publicId `____` | —         | create              | `REGISTERED`    | screenshot  |
| 5   | Appointment  | S-CREC         | Appointment                             | `____`                                | —         | book                | **`SCHEDULED`** | screenshot  |
| 6   | OPD          | S-CREC         | OPD                                     | `____`                                | —         | create              | **`QUEUED`**    | screenshot  |
| 7   | Consultation | S-CDOC         | OPD                                     | same                                  | `QUEUED`  | complete            | **`COMPLETED`** | case paper  |
| 8   | Lab          | S-CDOC         | LabOrder                                | —                                     | —         | order `CBC`         | on case paper   | PDF         |
| 9   | Billing      | S-CREC         | Bill                                    | `____`                                | —         | generated           | **`PENDING`**   | screenshot  |
| 10  | Payment      | S-CREC         | Bill                                    | same                                  | `PENDING` | pay                 | **`PAID`**      | receipt PDF |
| 11  | Pharmacy     | S-CPHA         | Sale                                    | `____`                                | —         | dispense            | created         | invoice PDF |
| 12  | Stock        | system         | clinic batch                            | `____`                                | qty `__`  | sale                | `__ − n`        | screenshot  |
| 13  | Follow-up    | S-CREC         | Follow-up                               | `____`                                | —         | from consultation   | Upcoming        | screenshot  |
| 14  | Reports      | S-CADM         | Overview / Reports                      | —                                     | —         | read                | reconciles      | screenshot  |
| 15  | **Denial**   | S-CADM/REC/DOC | IPD · Wards · Beds · ICU · OT · Nursing | —                                     | —         | attempt             | **denied**      | API log     |

---

### TC-E2E-043 — Stages 1–3: a clinic stands up with the right shape

`CLINIC_A · SA + ADM · Setup · Critical`
**Steps:** `TC-SA-019` · `TC-SA-009` · `TC-CD-001` · `TC-CD-004` · `TC-CA-002` · `TC-CA-003` · `TC-CA-006` · `TC-CA-007`.
**E2E assertion:** the clinic plan offers **only** OPD, APPOINTMENTS, BILLING, PHARMACY, MEDICAL_INVENTORY, REPORTS; the admin lands on **`/hospital/admin`** with the clinic's name in the header (correct by design); the sidebar shows the supported tabs and **none** of IPD / Wards & Beds / ICU / OT / Nursing / Hospital Inventory; every API call goes to **`/clinic/...`**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-044 — Stages 4–6: identity flows into appointment and OPD

`CLINIC_A · RECEPTIONIST · Patients/OPD · Critical`
**Steps:** `TC-CR-001` · `TC-CR-011` · `TC-CR-014`.
**E2E assertion:** the same numeric id, publicId and `PAT` number appear on all three records. Note that `PAT` numbering is **global across tenants** — a clinic patient may be `PAT57` next to a hospital `PAT56`; that is expected, not a leak.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-045 — Stages 7–8: consultation, prescription, lab, PDFs

`CLINIC_A · DOCTOR · OPD · Critical`
**Steps:** `TC-CDR-003` · `TC-CDR-004` · `TC-CDR-006` · `TC-CDR-014`.
**E2E assertion:** the OPD moves `QUEUED → COMPLETED`; both PDFs carry the **clinic's** header and the patient's `PAT` number; the vitals table shows only the clinic's enabled vitals; the lab orders picked appear on the case paper. **Lab ordering is `PARTIAL`** — there is no results screen; do not raise that as a bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-046 — Stages 9–10: one visit, one bill, correct fee

`CLINIC_A · RECEPTIONIST · Billing · Critical`
**Steps:** `TC-CR-020` · `TC-CA-006`.
**E2E assertion:** exactly one bill at the **clinic's** configured fee (555 + case paper), `PENDING → PAID`, receipt PDF with the clinic header; the clinic Overview collection rises by exactly that amount. Hospital fees must have no influence.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-047 — Stages 11–12: clinic pharmacy dispensing

`CLINIC_A · DOC → PHA · Pharmacy · Critical`
**Steps:** `TC-E2E-042` / `TC-CX-006`.
**E2E assertion:** prescription→sale continuity holds; clinic stock decrements; the clinic pharmacist sees **only** clinic stock and clinic prescriptions. Note the counter's mode toggle is labelled **"Hospital Rx Mode"** even in a clinic — a **LOW** cosmetic observation, not a functional defect.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-048 — Stages 13–14: follow-up, reports and audit reconcile

`CLINIC_A · REC + ADM · Reports · High`
**Steps:** `TC-CR-019` · `TC-CA-018` · `TC-CA-019` · `TC-CA-021`.
**E2E assertion:** hand-count 1 patient, 1 appointment, 1 OPD, 1 bill, 1 sale, 1 follow-up; every Overview and Reports figure equals that count; the audit log contains every action with the right actor and a **masked** phone on any duplicate acknowledgement. **No IPD or Beds card appears on the clinic Overview.**
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-049 — ⭐ Stage 15: the hospital-only domains must stay shut

`CLINIC_A · ADM + REC + DOC · Authorization · Critical`
**Steps:** run `TC-CX-010` in full — the complete `/clinic` drift matrix — with all three role tokens.
**E2E assertion:** ICU, OT, nursing, `/hospital/dashboard` and hospital-inventory are genuinely closed (404 or 403 — record which, because it tells you _why_).
**The IPD, ward and bed rows are known drift**: `/clinic/ipd` (13 endpoints), `/clinic/wards` (7) and `/clinic/beds` (5) carry **no** `@TenantType` and **no** `@RequireModule`. Product policy is **a clinic must not operate IPD**. Therefore:

- a **200** on any of those rows is a **FAIL / IMPLEMENTATION_DRIFT** at **HIGH**;
- a **created admission, ward or bed** raises it to **Critical**;
- a response containing **HOSPITAL_A's** data raises it to **Critical** and stops testing.
  Record row ids; **do not clean them up yourself**.
  **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-050 — Clinic identity + isolation closeout

`CLINIC_A + CLINIC_B + HOSPITAL_A · all · Identity/Isolation · Critical`
**Steps:** `TC-E2E-023` (PI-10 clinic) · `TC-CX-008` (C6) · `TC-CX-009` (C7).
**E2E assertion:** the family journey attaches every record to the child; CLINIC_B and HOSPITAL_A can reach **nothing** of CLINIC_A's by numeric id or publicId; CLINIC_A reaches nothing of theirs. The sharpest sub-case: a **HOSPITAL_A receptionist is authorized by role on `/clinic/**`** — only tenant scoping stands between them and clinic data.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
