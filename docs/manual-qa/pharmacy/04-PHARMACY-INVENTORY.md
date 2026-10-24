# 04 — PHARMACY INVENTORY

**Baseline:** `aa143a7` · **Cases:** `TC-PI-001` … `TC-PI-022` · Tenant PHARMACY_A

**Endpoints (`/pharmacy/inventory`, all `PHARMACIST` + `HOSPITAL_ADMIN`):**
`GET /` · `GET /expiring` · `GET /low-stock` · `GET /returns-history` · **`GET /search-batches` → `searchAvailableBatchesFEFO`** · `GET /transactions/{batchId}` · `POST /adjust` · `POST /batches/{id}/block` · `POST /batches/{id}/dispose` · `POST /supplier-return`

**Masters:** `/pharmacy/medicines` (+ `PATCH /{id}/status`) · `/pharmacy/manufacturers` (+ status) · `/pharmacy/categories` (+ status) · `/pharmacy/catalog/search` · `/pharmacy/autocomplete/medicines`

> **FEFO is implemented** — the batch search endpoint is literally `searchAvailableBatchesFEFO`.
> `TC-PI-012` tests it. Do **not** invent FIFO behaviour that is not there; record what the
> endpoint actually orders by.

---

## A. MEDICINE MASTER

### TC-PI-001 — Create a medicine

`PHARMACY_A · PHARMACIST · Inventory · Critical · Inventory`
**Steps:** create `QA Pharm Paracetamol 500` with manufacturer, category, unit and pricing; save; reload.
**Expected:** persists; searchable via `/pharmacy/medicines` and the autocomplete.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-002 — Medicine validation and duplicates

`PHARMACY_A · PHARMACIST · Inventory · High · Inventory`
**Steps:** blank name; negative price; a 300-char name; **the same name twice**; the same name with different casing.
**Expected:** validation messages; record whether a duplicate medicine name is refused, allowed, or merged — `NEEDS_PRODUCT_CONFIRMATION` if allowed silently (two identical medicines split stock and confuse the counter).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-003 — Toggle a medicine inactive

`PHARMACY_A · PHARMACIST · Inventory · High · Inventory`
**Steps:** `PATCH /pharmacy/medicines/{id}/status` (or the UI toggle); then open the Billing Counter and search for it; re-activate.
**Expected:** an inactive medicine drops out of the sale picker; **existing stock and historical sales are untouched**; re-activating restores it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-004 — Platform catalogue vs tenant stock

`PHARMACY_A + PHARMACY_B · PHARMACIST · Inventory · Critical · Inventory`
**Steps:** search a Super-Admin medicine via `/pharmacy/catalog/search` in both tenants; then set PHARMACY_A stock 100 and PHARMACY_B stock 7 for the same medicine.
**Expected:** the **catalogue entry is shared** (expected, not a leak); **stock is per tenant** — A shows 100, B shows 7.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-005 — Manufacturers and categories

`PHARMACY_A · PHARMACIST · Masters · Medium · Manufacturers / (Categories)`
**Steps:** create, edit, and `PATCH /{id}/status` a manufacturer and a category; try blank names and duplicates; use each in a medicine; then deactivate one and re-open the medicine form.
**Expected:** CRUD persists; deactivated masters drop out of pickers but remain on existing medicines. Record whether a **Categories** screen exists in the UI — the API does (`/pharmacy/categories`); if there is no screen, mark `BACKEND_WITHOUT_UI`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. BATCHES

### TC-PI-006 — Create a batch with quantity and expiry

`PHARMACY_A · PHARMACIST · Inventory · Critical · Inventory`
**Steps:** add batch `QP-B1` to `QA Pharm Paracetamol 500`: qty **100**, expiry +24 months, MRP and cost. Record the batch id.
**Expected:** appears in Inventory with qty 100; `GET /pharmacy/inventory` reflects it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-007 — Batch validation

`PHARMACY_A · PHARMACIST · Inventory · High · Inventory`
**Steps:** blank batch number; **negative quantity**; quantity 0; expiry in the **past**; expiry 50 years out; a duplicate batch number for the **same** medicine; the same batch number for a **different** medicine.
**Expected:** negative quantity and past expiry refused with clear messages. Record the outcomes for zero quantity (may be legitimate as an empty batch), duplicate-same-medicine (should be refused) and duplicate-different-medicine (probably allowed).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-008 — Multiple batches of one medicine

`PHARMACY_A · PHARMACIST · Inventory · High · Inventory`
**Steps:** add `QP-B2` (qty 30, expiry **+3 months**) and `QP-B3` (qty 50, expiry +36 months) to the same medicine.
**Expected:** all three batches listed separately with their own quantity and expiry; the medicine's total stock = 100 + 30 + 50 = **180**. Record where that total is displayed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-009 — Batch transaction ledger

`PHARMACY_A · PHARMACIST · Inventory · High · API / Inventory`
**Steps:** `GET /pharmacy/inventory/transactions/{QP-B1 id}` after: creation, a purchase inward, a sale, a refund and an adjustment.
**Expected:** one row per movement with type, quantity delta, resulting balance, actor and timestamp; the running balance equals the current on-hand quantity. This ledger is the reconciliation tool for every other case in this document.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. STOCK ADJUSTMENT

### TC-PI-010 — Manual stock adjustment

`PHARMACY_A · PHARMACIST · Inventory · Critical · API / Inventory`
**Steps:** `POST /pharmacy/inventory/adjust` on `QP-B1`: **+5** with a reason, then **−3** with a reason. Check the ledger and the Inventory list after each.
**Expected:** 100 → 105 → 102; each adjustment appears in the ledger with its reason and actor; it is audited (`TC-PR-011`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-011 — Adjustment guards

`PHARMACY_A · PHARMACIST · Inventory · Critical · API`
**Steps:** adjust **−200** on a batch holding 102; adjust **0**; adjust with no reason; adjust a **blocked** batch; adjust a **disposed** batch; adjust another tenant's batch id.
**Expected:** stock **must never go negative**; record whether a reason is mandatory; blocked/disposed batches should refuse; a foreign batch id → **403/404**. A negative on-hand quantity is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. ⭐ FEFO BATCH SELECTION

### TC-PI-012 — FEFO ordering on batch search

`PHARMACY_A · PHARMACIST · Inventory · Critical · API / Billing Counter`
**Why:** `GET /pharmacy/inventory/search-batches` maps to `searchAvailableBatchesFEFO` — **First Expiry, First Out is implemented**.
**Steps:**

1. With `QP-B1` (exp +24m, 100), `QP-B2` (exp **+3m**, 30) and `QP-B3` (exp +36m, 50) all available, call `GET /pharmacy/inventory/search-batches` for that medicine.
2. Record the **order** of the returned batches.
3. Open the Billing Counter and search the medicine; record the order shown and which batch is pre-selected, if any.
4. Block `QP-B2` (`TC-PE-004`) and repeat.
5. Set `QP-B2`'s quantity to 0 and repeat.
   **Expected:** batches come back **earliest-expiry first** — `QP-B2`, `QP-B1`, `QP-B3`. Blocked and zero-quantity batches are **excluded** from the available list. Record whether the counter merely _orders_ them or actually _auto-selects_ the earliest — the endpoint name implies ordering; **do not assume auto-selection**.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-013 — Sale draws from the batch actually chosen

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter`
**Steps:** sell 5 units explicitly choosing `QP-B3` (the latest expiry). Check all three batch quantities and the ledgers.
**Expected:** **only `QP-B3` decrements** (50 → 45); `QP-B1` and `QP-B2` unchanged. FEFO orders the _suggestion_; the chosen batch is what moves. If a different batch decrements, that is **Critical** — it would mean the invoice and the stock disagree.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-014 — Quantity spanning more than one batch

`PHARMACY_A · PHARMACIST · Sales · High · Billing Counter`
**Steps:** attempt to sell **120** units of a medicine whose largest single batch holds 100 (total across batches 180).
**Expected:** record the behaviour: refused with "insufficient stock in batch", or split across batches automatically. **Do not assume auto-splitting exists.** If it splits, verify each batch's decrement and each ledger row. `NEEDS_PRODUCT_CONFIRMATION`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. STOCK VIEWS

### TC-PI-015 — Low-stock view

`PHARMACY_A · PHARMACIST · Inventory · High · Dashboard / API`
**Steps:** create a batch with a very low quantity; `GET /pharmacy/inventory/low-stock`; check the dashboard's low-stock figure and the `LowStockBanner`.
**Expected:** the low-stock list and count agree; record the threshold used (fixed, configurable, or per medicine) — `NEEDS_PRODUCT_CONFIRMATION` if not visible anywhere.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-016 — Expiring view

`PHARMACY_A · PHARMACIST · Inventory · High · API / Expiry Management`
**Steps:** `GET /pharmacy/inventory/expiring`; compare with the Expiry Management buckets (`08`).
**Expected:** `QP-B2` (+3 months) appears; a +36-month batch does not; the two screens agree.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-017 — Returns history

`PHARMACY_A · PHARMACIST · Inventory · Medium · API / Returns`
**Steps:** after a patient refund and a supplier return (`07`), `GET /pharmacy/inventory/returns-history`.
**Expected:** both appear with type, quantity, batch and date; totals match the Returns screen.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-018 — Inventory search, filters, sort, pagination, empty state

`PHARMACY_A · PHARMACIST · Inventory · Medium · Inventory`
**Steps:** search by medicine name, batch number and manufacturer; filter by stock state; sort columns; page through >25 rows; search something absent.
**Expected:** case-insensitive partial match; stable sort; paging neither duplicates nor skips; readable empty state.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. INTEGRITY · ISOLATION · PERSISTENCE

### TC-PI-019 — ⭐ Full lifecycle arithmetic

`PHARMACY_A · PHARMACIST · Inventory · Critical · Inventory`
**Steps:** on a **fresh** batch, perform in order and record the on-hand quantity after each step:

| Step                                                                                                                                                                                                                                                                   | Action                  | Expected qty |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- | ------------ |
| 1                                                                                                                                                                                                                                                                      | create batch qty **10** | 10           |
| 2                                                                                                                                                                                                                                                                      | purchase posted **+5**  | 15           |
| 3                                                                                                                                                                                                                                                                      | sale **−3**             | 12           |
| 4                                                                                                                                                                                                                                                                      | patient refund **+1**   | 13           |
| 5                                                                                                                                                                                                                                                                      | adjustment **−2**       | 11           |
| 6                                                                                                                                                                                                                                                                      | supplier return **−4**  | 7            |
| **Expected:** the final on-hand is **7**, and `GET /pharmacy/inventory/transactions/{batch}` shows **six** rows whose running balance ends at 7. Any mismatch between the ledger and the on-hand figure is **Critical** — it means stock and its audit trail disagree. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____                                                                                                                                                             |

### TC-PI-020 — Double-submit on inventory writes

`PHARMACY_A · PHARMACIST · Inventory · Critical · Inventory`
**Steps:** double-click Save on: add medicine, add batch, adjust stock, block batch.
**Expected:** exactly one medicine, one batch, **one** adjustment and one block. A double adjustment silently doubles stock — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-021 — Inventory tenant and branch isolation

`PHARMACY_A + PHARMACY_B (+ MULTI branches) · PHARMACIST · Isolation · Critical · API`
**Steps:** with PHARMACY_B's token: list inventory (A's batches absent); `GET`/`PUT` A's batch id; `POST /pharmacy/inventory/adjust` on A's batch; `GET /pharmacy/inventory/transactions/{A batch}`; block and dispose A's batch. Then repeat cross-**branch** inside MULTI (`TC-PT-023`).
**Expected:** **403/404** throughout; **A's quantities unchanged** — verify in A afterwards. A cross-tenant or cross-branch adjustment is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PI-022 — Refresh and re-login persistence

`PHARMACY_A · PHARMACIST · Inventory · High · Inventory`
**Steps:** after A–F, F5 and then logout/login; re-read every batch quantity and ledger.
**Expected:** identical figures; nothing depends on client state.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
