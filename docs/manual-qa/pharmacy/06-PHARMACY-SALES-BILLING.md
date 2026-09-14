# 06 — PHARMACY SALES & BILLING COUNTER

**Baseline:** `aa143a7` · **Cases:** `TC-PS-001` … `TC-PS-020` · Tenant PHARMACY_A

**Endpoints:** `POST /pharmacy/sales` · `GET /pharmacy/sales` · `GET /pharmacy/sales/search` (by bill number) · `GET /pharmacy/sales/{id}` · **`GET /pharmacy/sales/{id}/pdf`** · `GET /pharmacy/sales/stats` · `POST /pharmacy/sales/{id}/return`
**Verified UI strings:** `Walk-in Mode` / `Hospital Rx Mode`, `Walk-in Customer`, `PRESCRIPTION`, `PHARMACY`, `OUT OF STOCK`, `CASH`, `Please select a medicine batch first`, `Invalid quantity or insufficient stock`.

> A standalone pharmacy has **no internal prescriber**. `Hospital Rx Mode` will find no local
> prescription (`TC-PH-004`), so the everyday flow here is **Walk-in Mode**. Record how the mode
> toggle behaves on a pharmacy tenant.

---

## A. WALK-IN SALE — the core flow

### TC-PS-001 — ⭐ Complete a walk-in sale

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter`
**Steps:** 1. Record `QP-B1`'s quantity. 2. **Walk-in Mode** → `Walk-in Customer` (add a name/phone if the form offers it). 3. Search `QA Pharm Paracetamol` → select batch `QP-B1` → qty **5**. 4. Payment `CASH`. 5. Complete. 6. Re-read Inventory and the batch ledger. 7. Print the invoice.
**Expected:** one sale created with type `PHARMACY`/walk-in and a bill number; stock falls by exactly 5; **one** ledger row; the invoice shows the pharmacy's header, the bill number, line items, quantity, rate and total.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-002 — Multi-line, multi-batch sale

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter`
**Steps:** one sale with three lines: medicine X from `QP-B1`, medicine X from `QP-B3`, medicine Y from its batch. Record every batch quantity before and after.
**Expected:** each line decrements **its own** batch by its own quantity; the invoice lists three lines; the total equals the sum of the lines; three ledger rows.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-003 — Batch selection respects FEFO ordering

`PHARMACY_A · PHARMACIST · Sales · High · Billing Counter`
**Steps:** = `TC-PI-012`/`013` from the counter.
**Expected:** the batch list is earliest-expiry first; the batch actually chosen is the one that decrements.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-004 — Hospital Rx Mode on a standalone pharmacy

`PHARMACY_A · PHARMACIST · Sales · Medium · Billing Counter`
**Steps:** switch to **Hospital Rx Mode**; search for a patient by name/phone.
**Expected:** no patients exist in this tenant → an empty result and a readable message. Record whether a sale can still be completed in this mode without a patient, and whether the mode toggle should be hidden for a pharmacy tenant (`NEEDS_PRODUCT_CONFIRMATION`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. SALE GUARDS

### TC-PS-005 — Insufficient stock

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter`
**Steps:** batch holds 5 → enter qty **10**; then qty **0**; then **−1**; then sell from a zero-quantity batch.
**Expected:** `Invalid quantity or insufficient stock`; **no sale created and no stock change** — verify Inventory and the ledger afterwards. `OUT OF STOCK` batches are not selectable.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-006 — No batch selected

`PHARMACY_A · PHARMACIST · Sales · High · Billing Counter`
**Steps:** enter a quantity without choosing a batch; press Enter/Add.
**Expected:** `Please select a medicine batch first`; **no request sent**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-007 — ⭐ Expired and blocked batches cannot be sold

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter / API`
**Steps:** 1. Take a batch already past expiry (or set one). 2. Try to select it at the counter. 3. `POST /pharmacy/sales` **via API** with that batch id. 4. Block a batch (`TC-PE-004`) and repeat both.
**Expected:** not offered in the picker **and** the API refuses. Selling expired or blocked medicine is a **Critical** patient-safety failure. If the UI hides it but the API allows it, that is still **Critical** — record clearly which layer failed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-008 — ⭐ Double-click Complete

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter`
**Steps:** prepare a sale; **double-click Complete**; then check the sales list, the stock and the ledger.
**Expected:** exactly **one** sale, **one** decrement, **one** ledger row. Two sales for one transaction is **Critical** — it double-charges the customer and double-depletes stock.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-009 — Backend failure during a sale

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter`
**Steps:** stop the backend mid-sale; complete; restart; retry the same sale.
**Expected:** a readable error; **stock not decremented by the failed attempt**; the retry creates exactly one sale. A decrement without a sale record is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-010 — ⭐ Two counters selling the last unit

`PHARMACY_A · PHARMACIST ×2 · Sales · Critical · Billing Counter`
**Steps:** set a batch to qty **1**; open two browser tabs (two pharmacists, or the admin and the pharmacist); prepare a sale of 1 in each; submit as close together as possible.
**Expected:** one succeeds, the other is refused; **stock never goes negative**. Record the exact message the loser sees. A negative quantity is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PRICING, TAX, DISCOUNT, ROUNDING

### TC-PS-011 — Totals, tax and discount

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter`
**Steps:** 1. Record which pricing controls the counter actually offers — unit rate, discount (% or amount), tax/GST, round-off. **Do not assume any of them exist.** 2. For each that exists: sell 3 × 33.33; apply a 10% discount; apply tax; observe the total.
**Expected:** line total = qty × rate; discount and tax applied in the order shown on screen; **the printed invoice total equals the on-screen total to the paisa**; rounding is consistent (record the rule). Mark any absent control `NOT_IMPLEMENTED` rather than failing the case.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-012 — Payment method and change

`PHARMACY_A · PHARMACIST · Sales · High · Billing Counter`
**Steps:** complete with `CASH`; then with each other method the form offers; record whether a reference is required for non-cash and whether tendered/change is captured.
**Expected:** the method appears on the invoice; a non-cash reference is required if the form demands it. Record the exact option list.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. SALES HISTORY & INVOICE

### TC-PS-013 — Billing history list

`PHARMACY_A · PHARMACIST · Billing · High · Billing`
**Steps:** open **Billing**; verify every sale from §A–C appears with bill number, date, type, amount; sort and page; compare the day's total with the Dashboard (`TC-PH-003`) and the admin Billing tab (`TC-PA-010`).
**Expected:** all three views agree exactly.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-014 — Search a sale by bill number

`PHARMACY_A · PHARMACIST · Billing · High · API / Returns`
**Steps:** `GET /pharmacy/sales/search?...` with a valid bill number, a nonexistent one, and a bill number belonging to **PHARMACY_B**.
**Expected:** the valid one returns that sale; the nonexistent one an empty result; **the foreign one must return nothing** — returning B's sale is **Critical** (this endpoint is also what the Returns screen's **Search Bill** uses).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-015 — Sale detail and invoice PDF

`PHARMACY_A · PHARMACIST · Billing · High · Billing`
**Steps:** `GET /pharmacy/sales/{id}`; then `GET /pharmacy/sales/{id}/pdf` and open the file; reprint the same invoice twice.
**Expected:** the detail shows every line with batch, quantity, rate and total; the PDF carries the **pharmacy's** name and logo, the bill number, date, lines, total and payment method; reprints are identical and do **not** create a second sale.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-016 — Sale timestamps and "today"

`PHARMACY_A · PHARMACIST · Reports · Medium · Dashboard / Billing`
**Steps:** make a sale; compare its time on the invoice, in the list and in the report; confirm it lands in today's IST business day.
**Expected:** consistent IST wall-clock. `pharmacy_sales.created_at` is **second precision** — a sub-second difference is not a bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. BRANCH · ISOLATION · UI

### TC-PS-017 — Sale in the wrong branch

`PHARM_MULTI · branch user + ADM · Sales · Critical · Billing Counter / API`
**Steps:** 1. As the Branch A user, sell from a **Branch B** batch id via API. 2. As the **admin** with `X-Branch-ID: <B>`, sell from a Branch A batch id. 3. As the admin with a **foreign tenant's** branch id.
**Expected:** (1) **403/404**, Branch B unchanged. (2) the sale must be rejected or land in the branch that owns the batch — never split. (3) must not touch the other tenant. Verify quantities in both branches afterwards.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-018 — Sales tenant isolation

`PHARMACY_A + PHARMACY_B (+ HOSPITAL_A, CLINIC_A) · PHARMACIST · Isolation · Critical · API`
**Steps:** = `TC-ISO-038`/`039`/`043` across all four tenants sharing the `/pharmacy/**` ERP namespace: list sales; `GET /pharmacy/sales/{A id}`; download `{A id}/pdf`; `POST /pharmacy/sales` using A's batch id; `POST /pharmacy/sales/{A id}/return`.
**Expected:** **403/404** throughout; **no PDF bytes**; **A's stock and sales unchanged** — verify in A. Because all four tenant types use the same ERP namespace, tenant scoping is the only separation here.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-019 — Counter UI states

`PHARMACY_A · PHARMACIST · UI · Medium · Billing Counter`
**Steps:** empty inventory; a medicine with no available batch; slow network; backend down; a 100-char medicine name; amounts 1234.50 and 1000000; keyboard `Enter` to add a line; remove a line; clear the whole cart.
**Expected:** readable empty and error states; no crash; amounts always two decimals; removing a line recalculates the total; clearing the cart sends nothing.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PS-020 — Refresh and re-login persistence

`PHARMACY_A · PHARMACIST · Sales · High · Billing Counter / Billing`
**Steps:** build a cart then **F5** (the cart is expected to be lost); complete a real sale; logout/login; re-open the sale and reprint.
**Expected:** an unsubmitted cart does not survive a refresh (expected — record it once); completed sales, amounts and invoices persist exactly.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
