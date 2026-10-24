# 07 — HOSPITAL BILLING

**Baseline:** `aa143a7` · **Cases:** `TC-HB-001` … `TC-HB-030` · Tenant HOSPITAL_A · BILLING module

**Endpoints:** `GET /hospital/billing` · `PUT /{id}/status` · `PUT /{id}/items` · `GET /{id}/pdf` · `GET /ipd/{ipdId}/bill` · `POST /{billingId}/pay` · `GET /patient/{patientPublicId}`
**Statuses:** `PENDING` · `PARTIAL` · `PAID` (+ `CLOSED` shown in `BillingTable`)
**`BillingTable` columns:** Bill No · Patient Name · Date · Amount · Status · actions **Mark Paid**, **Print**
**Roles:** view/create/edit = `ADM DOC REC`; **no DELETE for anyone**. Who may take payment is also governed by **Billing Handler** (`TC-HAS-002`).

---

## A. OPD BILL

### TC-HB-001 — OPD bill generated on consultation completion

`HOSPITAL_A · DOCTOR → RECEPTIONIST · Billing · Critical · Billing`
**Steps:** 1. Admin fee = 777, case-paper fee set. 2. Reception creates an OPD (Bill Payment `LAST`). 3. Doctor completes the consultation. 4. Reception opens Billing.
**Expected:** exactly **one** bill for that visit, `PENDING`, amount = consultation + case-paper (+ in-clinic items); Bill No present; patient name and `PAT` id correct.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-002 — Bill Payment = FIRST creates a paid bill at OPD entry

`HOSPITAL_A · RECEPTIONIST · Billing · Critical · New OPD / Billing`
**Steps:** = `TC-HAS-003`; create an OPD with payment method `CASH` and a reference.
**Expected:** bill exists immediately, `PAID`, with the method and reference recorded; the doctor's completion does **not** create a second bill.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-003 — Mark Paid

`HOSPITAL_A · RECEPTIONIST · Billing · Critical · Billing ▸ Mark Paid`
**Steps:** on a `PENDING` bill click **Mark Paid**; refresh; check the patient timeline and the admin Overview collection.
**Expected:** status `PAID`; today's collection increases by exactly that amount; audit entry; re-clicking does not double-count.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-004 — Partial payment

`HOSPITAL_A · RECEPTIONIST · Billing · Critical · Billing ▸ pay`
**Steps:** bill 1000 → `POST /hospital/billing/{id}/pay` (or the UI payment control) with 400; then 600.
**Expected:** after the first payment status `PARTIAL` with balance 600; after the second `PAID` with balance 0; both payments listed; collection totals include both.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-005 — Overpayment, zero and negative payment

`HOSPITAL_A · RECEPTIONIST · Billing · Critical · Billing ▸ pay`
**Steps:** pay 2000 on a 1000 bill; pay 0; pay −100; pay on an already `PAID` bill.
**Expected:** each refused with a clear message; balance never negative; no duplicate payment rows. Record whether overpayment is allowed as advance — `NEEDS_PRODUCT_CONFIRMATION` if accepted.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-006 — Payment method and reference

`HOSPITAL_A · RECEPTIONIST · Billing · High · Billing ▸ pay`
**Steps:** pay by `CASH` (no reference), then by a non-cash method with and without a reference.
**Expected:** reference required for non-cash (per `validateOpdPayment`); method shown on the receipt.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-007 — Edit bill items

`HOSPITAL_A · RECEPTIONIST · Billing · High · Billing ▸ items`
**Steps:** `PUT /hospital/billing/{id}/items` (or the UI): add a custom fee `Dressing` 50; remove a line; change a quantity.
**Expected:** total recalculates; a `PAID` bill — record whether edits are refused (they should be); audit entry.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-008 — Receipt / invoice PDF

`HOSPITAL_A · RECEPTIONIST · Billing · High · Billing ▸ Print`
**Steps:** **Print** on a `PAID` bill; open the PDF.
**Expected:** hospital header + logo, Bill No, date, patient **name + PAT id**, itemised lines, total, amount paid, balance, payment method. **Patient identifier, not hospital identifier.**
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-009 — Print policy toggle

`HOSPITAL_A · HOSPITAL_ADMIN → REC · Billing · Medium · Print & Payment`
**Steps:** turn **Bill** print OFF (`TC-HAS-009`).
**Expected:** Print control hidden; endpoint still answers (record) — policy, not authorization.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. IPD BILL

### TC-HB-010 — IPD bill accumulates through the stay

`HOSPITAL_A · DOCTOR/REC · Billing · Critical · /ipd/:id`
**Steps:** 1. Admit P1. 2. Add IPD prescriptions; administer hospital items (`TC-HA-035`); add procedures/fees. 3. `GET /hospital/billing/ipd/{ipdId}/bill` or the IPD bill view.
**Expected:** every chargeable event appears as a line; running total correct; bed/ward charges if implemented (record).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-011 — Final bill on discharge

`HOSPITAL_A · RECEPTIONIST · Billing · Critical · /ipd/:id`
**Steps:** doctor plans discharge; reception confirms; open the final bill; take payment; print.
**Expected:** bill frozen at discharge; total = sum of lines; payment moves it to `PAID`; receipt prints. Discharge with an unpaid balance — record whether it is blocked or warned (`NEEDS_PRODUCT_CONFIRMATION`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-012 — Post-discharge additions

`HOSPITAL_A · DOCTOR · Billing · High · /ipd/:id`
**Steps:** after discharge try to administer an item / add a prescription.
**Expected:** refused (`TC-HD-026`); the final bill cannot grow silently after it was settled.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PHARMACY BILL RELATIONSHIP

### TC-HB-013 — Pharmacy sale vs hospital bill

`HOSPITAL_A · PHARMACIST → RECEPTIONIST · Billing · High · Billing`
**Steps:** = `TC-HP-037`.
**Expected:** document the actual model; **no double counting** in the admin Overview (pharmacy sales and billing collection are separate cards — verify they are not summed twice).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-014 — Pharmacy refund and hospital totals

`HOSPITAL_A · PHARMACIST · Billing · High · Reports`
**Steps:** refund a prescription sale (`TC-HP-021`); re-read the admin Overview and Reports.
**Expected:** pharmacy figures fall; hospital billing collection unaffected (or adjusted — record which, consistently).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. LIST, SEARCH, FILTERS, TOTALS

### TC-HB-015 — Billing list and columns

`HOSPITAL_A · RECEPTIONIST · Billing · High · Billing`
**Steps:** open; verify the columns Bill No, Patient Name, Date, Amount, Status; sort each; page.
**Expected:** correct values; sorting stable; amounts 2 decimals; dates IST.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-016 — Search and filters

`HOSPITAL_A · RECEPTIONIST · Billing · Medium · Billing`
**Steps:** search by bill number, patient name, `PAT` id; filter by status and date range; empty result.
**Expected:** correct matches; `PENDING`/`PARTIAL`/`PAID`/`CLOSED` filters; EmptyState.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-017 — Bills by patient

`HOSPITAL_A · RECEPTIONIST · Billing · High · Patient timeline`
**Steps:** `GET /hospital/billing/patient/{patientPublicId}`; also via View Details.
**Expected:** only that patient's bills; for the parent/child family each patient's bills are **separate** — the child's bill must never appear under the father.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-018 — Collection totals reconcile

`HOSPITAL_A · HOSPITAL_ADMIN · Billing · Critical · Overview/Reports`
**Steps:** sum every payment taken today by hand; compare with the Overview collection card and the Reports revenue figure.
**Expected:** equal to the paisa; `PARTIAL` bills contribute only the amount actually paid.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. NEGATIVE, CONCURRENCY, INTEGRITY

### TC-HB-019 — ⭐ Double-click Mark Paid / pay

`HOSPITAL_A · RECEPTIONIST · Billing · Critical · Billing`
**Steps:** double-click **Mark Paid**; double-submit a partial payment.
**Expected:** exactly one status change and **one** payment row; collection not double-counted. Duplicate payments are **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-020 — Two users paying the same bill

`HOSPITAL_A · REC + DOC · Billing · Critical · Billing`
**Steps:** two tabs open the same `PENDING` bill; both pay the full amount at once.
**Expected:** one succeeds, the other is refused or results in no extra payment; balance never negative.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-021 — Invalid status transitions

`HOSPITAL_A · RECEPTIONIST · Billing · High · API`
**Steps:** `PUT /{id}/status` with `PAID` → `PENDING`; with a nonsense value; on a nonexistent id.
**Expected:** 400/409 for illegal transitions and values; 404 for the missing id; no silent success.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-022 — No bill deletion

`HOSPITAL_A · all · Billing · Critical · API`
**Steps:** `DELETE /hospital/billing/{id}` with admin, doctor and reception tokens.
**Expected:** **403 or 405** for everyone — the matrix shows no DELETE. A deletable bill is a **Critical** financial-integrity bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-023 — Cancel / refund a hospital bill

`HOSPITAL_A · RECEPTIONIST · Billing · High · Billing`
**Steps:** look for a cancel or refund action on an OPD/IPD bill.
**Expected:** if none exists, record **PARTIAL** — hospital bills have no cancellation path (pharmacy refunds are separate). Do not invent one.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-024 — Failure mid-payment

`HOSPITAL_A · RECEPTIONIST · Billing · Critical · Billing`
**Steps:** stop the backend during a payment; restart; retry.
**Expected:** readable error; no half-recorded payment; retry produces exactly one.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. PERMISSIONS · ISOLATION · UI

### TC-HB-025 — Role matrix for billing

`HOSPITAL_A · all · Billing · Critical · API`
**Steps:** = `TC-PERM-023`. GET/POST/PUT/DELETE with all seven tokens.
**Expected:** `ADM DOC REC` allowed; NURSE, NI, OTI, PHA **403**; DELETE 403/405 for all.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-026 — Billing Handler enforcement: UI vs API

`HOSPITAL_A · DOC/REC · Billing · High · Settings`
**Steps:** = `TC-HD-032`.
**Expected:** record whether `billingHandler` is enforced server-side or only hides the button. If UI-only, raise `NEEDS_PRODUCT_CONFIRMATION`, not a bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-027 — Billing tenant isolation

`HOSPITAL_A + B · RECEPTIONIST · Billing · Critical · API`
**Steps:** = `TC-ISO-034`, `035`, `036`.
**Expected:** B cannot list, fetch, pay or download A's bills; A's balance unchanged.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-028 — BILLING module off

`HOSPITAL_M · RECEPTIONIST · Entitlement · High · sidebar/API`
**Steps:** = `TC-MOD-014`.
**Expected:** Billing and Fees tabs hidden; `/hospital/billing` **403** (this module _is_ gated).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-029 — Currency, dates, long names in bills and PDFs

`HOSPITAL_A · RECEPTIONIST · UI · Low · Billing`
**Steps:** amount 1234.50 and 1000000; 100-char patient name; blank address; print.
**Expected:** always 2 decimals, thousands separators consistent, no rounding drift; PDF wraps long names; blanks show `—`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HB-030 — Re-login persistence

`HOSPITAL_A · RECEPTIONIST · Persistence · High · Billing`
**Steps:** logout/login; re-open every bill, payment and receipt.
**Expected:** statuses, amounts and payments unchanged.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
