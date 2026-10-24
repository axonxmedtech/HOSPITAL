# 07 — PHARMACY RETURNS & REFUNDS

**Baseline:** `aa143a7` · **Cases:** `TC-PRF-001` … `TC-PRF-016` · Tenant PHARMACY_A

**Two distinct flows** (`ReturnsView.jsx` tabs `PATIENT` / `SUPPLIER`):

| Flow                | Endpoint                                   | Stock effect        | UI                                                                           |
| ------------------- | ------------------------------------------ | ------------------- | ---------------------------------------------------------------------------- |
| **Patient refund**  | `POST /pharmacy/sales/{id}/return`         | stock **increases** | `PATIENT` tab → **Search Bill** → **Process Patient Refund**                 |
| **Supplier return** | `POST /pharmacy/inventory/supplier-return` | stock **decreases** | `SUPPLIER` tab → **Dispatch Supplier Return** → **Finalize Supplier Return** |

**Verified strings:** `Search Bill`, `Sale search failed`, `Refund failed`, `Batch search failed`, `Supplier return dispatch failed`, `Walk-In`.
**History:** `GET /pharmacy/inventory/returns-history`.

---

## A. PATIENT REFUND

### TC-PRF-001 — ⭐ Partial refund

`PHARMACY_A · PHARMACIST · Returns · Critical · Returns ▸ PATIENT`
**Steps:** 1. From a sale of **5** units (`TC-PS-001`), record the batch quantity. 2. `PATIENT` tab → **Search Bill** by bill number. 3. Select the line, quantity **2**, reason if the form offers one. 4. **Process Patient Refund**. 5. Re-read the batch, the ledger, the sale record and the Dashboard.
**Expected:** stock **+2**; one ledger row of type return; the sale shows 2 returned of 5; today's net sales fall by the refunded value; the refund is audited.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-002 — Full refund

`PHARMACY_A · PHARMACIST · Returns · Critical · Returns ▸ PATIENT`
**Steps:** refund the remaining **3** of the same sale.
**Expected:** stock returns to its pre-sale figure; the sale is fully returned; the net contribution of that sale to the day's revenue is **zero**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-003 — ⭐ Over-refund is refused

`PHARMACY_A · PHARMACIST · Returns · Critical · Returns ▸ PATIENT`
**Steps:** on a sale of 5, attempt to refund **10**; then refund 5 and attempt **1** more; try via the UI and via the API.
**Expected:** each refused; **stock never exceeds what was actually sold back**. Inflating stock through refunds is a **Critical** inventory-integrity failure.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-004 — Duplicate / double-submitted refund

`PHARMACY_A · PHARMACIST · Returns · Critical · Returns ▸ PATIENT`
**Steps:** **double-click Process Patient Refund**; then replay the same `POST /pharmacy/sales/{id}/return` via API.
**Expected:** exactly **one** refund and **one** stock increment; the replay is refused or a no-op.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-005 — Refund of a multi-line sale, one line only

`PHARMACY_A · PHARMACIST · Returns · High · Returns ▸ PATIENT`
**Steps:** from the three-line sale (`TC-PS-002`), refund **only line 2**.
**Expected:** only that line's batch increments; the other two batches are untouched; the sale shows a partial return on one line.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-006 — Wrong medicine / wrong batch in a refund

`PHARMACY_A · PHARMACIST · Returns · Critical · API`
**Steps:** craft a refund body naming (a) a medicine that was **not** on the sale, (b) a **different batch** of the same medicine, (c) a batch belonging to another medicine entirely.
**Expected:** refused. **A refund must return stock to the batch it was sold from** — crediting a different batch corrupts both expiry tracking and valuation. Any success here is **Critical**; record which batch actually moved.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-007 — Refund of a walk-in sale

`PHARMACY_A · PHARMACIST · Returns · Medium · Returns ▸ PATIENT`
**Steps:** refund a `Walk-In` sale (no customer record).
**Expected:** allowed and labelled `Walk-In`; stock restored. This is the normal case for a standalone pharmacy.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-008 — Refund into a blocked / disposed / expired batch

`PHARMACY_A · PHARMACIST · Returns · High · Returns ▸ PATIENT`
**Steps:** sell from a batch; then block it (`TC-PE-004`); then attempt the refund. Repeat with a disposed batch and one that has since expired.
**Expected:** record the behaviour. Returning stock into a blocked or disposed batch would resurrect unsellable inventory — if it succeeds, check whether the returned units become **sellable again** (that would be **High**/**Critical**). `NEEDS_PRODUCT_CONFIRMATION`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-009 — Refund search guards

`PHARMACY_A · PHARMACIST · Returns · High · Returns ▸ PATIENT`
**Steps:** **Search Bill** with: a nonexistent bill number; a blank value; a **PHARMACY_B** bill number; a bill number from a **different branch** (MULTI).
**Expected:** empty result and a readable message (`Sale search failed` on a server error). **A foreign tenant's or branch's sale must not be found** — finding it is **Critical**, because the next click would refund it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-010 — Refund another tenant's sale directly

`PHARMACY_A + PHARMACY_B · PHARMACIST · Returns · Critical · API`
**Steps:** `POST /pharmacy/sales/{PHARMACY_B sale id}/return` with PHARMACY_A's token; then verify PHARMACY_B's sale and stock.
**Expected:** **403/404**; **B's sale and stock unchanged**. This moves both money and stock in another business — the single worst outcome in this document.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. SUPPLIER RETURN

### TC-PRF-011 — Dispatch and finalize a supplier return

`PHARMACY_A · PHARMACIST · Returns · High · Returns ▸ SUPPLIER`
**Steps:** 1. Record the batch quantity. 2. `SUPPLIER` tab → search the batch (`Batch search failed` path) → select quantity **4** → **Dispatch Supplier Return**. 3. Re-read the batch. 4. **Finalize Supplier Return**. 5. Re-read again and check the ledger and `returns-history`.
**Expected:** stock **decreases by 4** — record **at which step** (dispatch or finalize); the ledger shows one outward row; the return is listed in returns-history with the supplier.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-012 — Supplier-return guards

`PHARMACY_A · PHARMACIST · Returns · Critical · Returns ▸ SUPPLIER`
**Steps:** return **more** than the batch holds; return 0; return a negative quantity; **finalize twice**; dispatch a batch belonging to another tenant.
**Expected:** over-return refused (**stock must never go negative**); finalize is idempotent; a foreign batch → **403/404**. `Supplier return dispatch failed` is shown on a server error.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-013 — Supplier return from Expiry Management

`PHARMACY_A · PHARMACIST · Returns · Medium · Expiry Management`
**Steps:** = `TC-PE-007`: use **Dispatch Return** on a near-expiry batch and confirm it lands in the same supplier-return flow.
**Expected:** the two entry points produce one consistent record; no double decrement.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. RECONCILIATION · BRANCH · AUDIT

### TC-PRF-014 — ⭐ Returns arithmetic end to end

`PHARMACY_A · PHARMACIST · Returns · Critical · Inventory`
**Steps:** on a fresh batch, in order, recording the quantity after each:

| Step                                                                                                                                                                                                                                                 | Action                                   | Expected |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- | -------- |
| 1                                                                                                                                                                                                                                                    | create batch **20**                      | 20       |
| 2                                                                                                                                                                                                                                                    | sale **−6**                              | 14       |
| 3                                                                                                                                                                                                                                                    | patient refund **+2**                    | 16       |
| 4                                                                                                                                                                                                                                                    | patient refund **+4** (rest of the sale) | 20       |
| 5                                                                                                                                                                                                                                                    | supplier return **−5**                   | 15       |
| **Expected:** final on-hand **15**; `GET /pharmacy/inventory/transactions/{batch}` shows five rows ending at 15; `returns-history` lists two patient refunds and one supplier return; the Dashboard's net sales for the day reflect the full refund. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____                                                                                                                                           |

### TC-PRF-015 — Returns in a multi-branch tenant

`PHARM_MULTI · branch users + ADM · Returns · Critical · Returns`
**Steps:** 1. Sell in Branch A; refund it as the **Branch A** user → A's stock rises, **B unchanged**. 2. As the **Branch B** user, search for Branch A's bill number and attempt to refund it. 3. As the admin with `X-Branch-ID: <A>`, refund a Branch A sale.
**Expected:** (1) correct; (2) **not found / 403**, A unchanged; (3) allowed and applied to Branch A only. Verify both branches' quantities after every step.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PRF-016 — Returns are audited and reported

`PHARMACY_A · PHARMACIST/ADM · Audit · High · Audit Logs / Reports`
**Steps:** after §A–B, open Audit Logs and the Reports dashboard; export the CSV ledger.
**Expected:** every refund and supplier return appears with actor, quantity, batch and timestamp; report totals and the CSV reconcile with `TC-PRF-014`'s arithmetic; no customer phone number appears in clear text.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
