# 06 — PRESCRIPTION → PHARMACY

**Cases:** `TC-E2E-037` … `TC-E2E-042` · Tenants HOSPITAL_A and CLINIC_A

The handoff from prescriber to dispenser is where a wrong-patient error becomes a wrong-medicine
error. This journey tracks identity **and** stock across that boundary.

## State table

| #   | Stage          | Actor  | Entity        | Identifier | Before      | Action                  | After              | Evidence         |
| --- | -------------- | ------ | ------------- | ---------- | ----------- | ----------------------- | ------------------ | ---------------- |
| 1   | Stock          | S-ADM  | Batch `QA-B1` | `____`     | qty **100** | —                       | 100                | screenshot       |
| 2   | Consultation   | S-DOC  | OPD           | `____`     | `QUEUED`    | complete w/ 2 medicines | `COMPLETED`        | prescription PDF |
| 3   | Prescription   | system | Rx            | `____`     | —           | created                 | `ACTIVE`/`Pending` | screenshot       |
| 4   | Pharmacy queue | S-PHA  | Prescriptions | same       | —           | list                    | row present        | screenshot       |
| 5   | Dispense       | S-PHA  | Sale          | `____`     | —           | sell 5                  | created            | invoice PDF      |
| 6   | Stock          | system | `QA-B1`       | same       | 100         | sale                    | **95**             | screenshot       |
| 7   | Ledger         | system | batch ledger  | —          | —           | —                       | one outward row    | screenshot       |
| 8   | Money          | S-PHA  | sale total    | —          | —           | —                       | 5 × rate           | invoice          |

---

### TC-E2E-037 — ⭐ The prescription reaches the pharmacist as the _same_ patient

`HOSPITAL_A · DOC → PHA · Pharmacy · Critical`
**Steps:** `TC-HP-010`.
**E2E assertion:** the Prescriptions row shows **the same name and `PAT` number** as the consultation; **See Consultation** opens **that** OPD case; the medicines, dosages and durations match the prescription PDF exactly. A row showing a different patient, or a prescription whose medicines differ from the PDF, is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-038 — Dispensing moves the right batch by the right amount

`HOSPITAL_A · PHARMACIST · Pharmacy · Critical`
**Steps:** `TC-HP-011` or `TC-HP-012` · `TC-PI-013`.
**E2E assertion:** the sale links to **this** patient; **only the chosen batch** decrements, by **exactly** the quantity sold; the batch ledger gains **one** row; the invoice total equals quantity × rate and matches the on-screen total to the paisa.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-039 — ⭐ A family member's prescription is never dispensed by accident

`HOSPITAL_A · DOC → PHA · Pharmacy · Critical`
**The clinical-safety case of this document.**
**Steps:** 1. With the Kale family (F1 father, F2 child, F3 mother — one phone), have the doctor complete **two** consultations: one for **F2** with a paediatric medicine, one for **F1** with an adult medicine. 2. S-PHA opens **Prescriptions** and searches by the shared phone number `9900080001`. 3. Dispense **the child's** prescription.
**E2E assertion:** the Prescriptions list shows **both** prescriptions as **separate rows with different patient names and different `PAT` numbers** — never merged, never one row. Selecting the child's row dispenses **the child's** medicine against **the child's** id; the invoice names the child. The father's prescription remains `ACTIVE`/undispensed. Dispensing the wrong family member's medicine is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-040 — Dispensing guards hold at the boundary

`HOSPITAL_A · PHARMACIST · Pharmacy · Critical`
**Steps:** `TC-HP-014` (insufficient) · `TC-HP-016` (expired/blocked) · `TC-HP-017` (double-click) · `TC-HP-018` (backend failure).
**E2E assertion:** after **every** refused attempt, re-read the batch: the quantity must be **unchanged** and the ledger must have **no** new row. A refusal that still moved stock is **Critical**; so is a double-click producing two sales.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-041 — Money reconciles across the boundary

`HOSPITAL_A · PHA + REC + ADM · Billing · High`
**Steps:** `TC-HP-020` · `TC-HB-013` · `TC-HP-037`.
**E2E assertion:** the sale appears in the pharmacist's Billing history, the Dashboard total and the admin Pharmacy/Analytics tab with **the same amount**; the hospital's OPD billing collection is **not** inflated by the pharmacy sale (they are separate cards — record the actual model and confirm no double counting).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-042 — The same journey in a clinic

`CLINIC_A · DOC → PHA · Pharmacy · Critical`
**Steps:** `TC-CX-006` (C4) · `TC-CP-006` · `TC-CP-007`.
**E2E assertion:** identical continuity, with the **clinic's** header on the invoice and the clinic patient's `PAT` number. Then confirm the clinic pharmacist cannot see HOSPITAL_A prescriptions or stock (`TC-CP-016`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
