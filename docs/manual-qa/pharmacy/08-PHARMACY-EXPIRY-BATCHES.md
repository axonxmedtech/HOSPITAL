# 08 — PHARMACY EXPIRY & BATCH CONTROL

**Baseline:** `aa143a7` · **Cases:** `TC-PE-001` … `TC-PE-014` · Tenant PHARMACY_A

**Endpoints:** `GET /pharmacy/inventory/expiring` · `POST /pharmacy/inventory/batches/{id}/block` · `POST /pharmacy/inventory/batches/{id}/dispose` · `POST /pharmacy/inventory/supplier-return`
**Verified UI:** buckets `ACTIVE` · `NEAR` · `CRITICAL` · `EXPIRED` · `BLOCKED` · `DISPOSED`; actions **Block**, **Freeze Batch**, **Confirm Disposal**, **Dispatch Return**; note `Safe pharmaceutical destruction program`; errors `Block failed`, `Disposal failed`, `Supplier return failed`.

---

### TC-PE-001 — Expiry buckets are computed correctly

`PHARMACY_A · PHARMACIST · Expiry · High · Expiry Management`
**Steps:** create five batches of the same medicine with expiry **−10 days**, **+10 days**, **+45 days**, **+6 months**, **+24 months**, each with a distinct quantity. Open Expiry Management.
**Expected:** each lands in exactly one bucket; record the **exact day thresholds** the product uses for `NEAR` vs `CRITICAL` (they are not documented anywhere — capture them here so future runs are repeatable). Unit counts per bucket match the quantities entered.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-002 — Boundary dates

`PHARMACY_A · PHARMACIST · Expiry · High · Expiry Management`
**Steps:** create batches expiring **today**, **yesterday** and **tomorrow**. Refresh the screen.
**Expected:** record precisely whether _today_ counts as expired or still sellable — this is the single most important boundary in the module. Then attempt to **sell** the today-expiry batch (`TC-PS-007`) and confirm the counter and the bucket agree. A batch shown as `EXPIRED` that can still be sold is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-003 — `expiring` endpoint agrees with the screen

`PHARMACY_A · PHARMACIST · Expiry · Medium · API`
**Steps:** `GET /pharmacy/inventory/expiring`; compare with the `NEAR`/`CRITICAL`/`EXPIRED` buckets and with the Dashboard's near-expiry figure.
**Expected:** all three agree.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-004 — ⭐ Block / freeze a batch stops sales

`PHARMACY_A · PHARMACIST · Expiry · Critical · Expiry Management`
**Steps:** 1. **Block** a healthy batch (`Block failed` path on error); status → `BLOCKED`. 2. Try to select it at the Billing Counter. 3. `POST /pharmacy/sales` **via API** with that batch id. 4. Check whether it still appears in `search-batches` (FEFO) and in the stock total. 5. Use **Freeze Batch** and record how it differs from **Block**, if at all.
**Expected:** blocked stock is **not sellable in the UI or the API** and is excluded from the FEFO available list. Record whether it still counts toward total stock value. If the API permits a sale of a blocked batch, that is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-005 — Unblock a batch

`PHARMACY_A · PHARMACIST · Expiry · High · Expiry Management`
**Steps:** look for an unblock/reactivate action on a `BLOCKED` batch; if present, use it and re-attempt a sale.
**Expected:** if unblocking exists the batch becomes sellable again; **if there is no unblock action, record `PARTIAL`** — blocking would then be irreversible, which is worth knowing before anyone blocks good stock by mistake.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-006 — ⭐ Dispose an expired batch

`PHARMACY_A · PHARMACIST · Expiry · Critical · Expiry Management`
**Steps:** 1. On an `EXPIRED` batch use **Confirm Disposal** (`Disposal failed` path). 2. Re-read the batch, the stock total and the ledger. 3. Attempt a sale of it via API. 4. Attempt to dispose it again. 5. Attempt to dispose a **non-expired** batch.
**Expected:** status `DISPOSED`; the quantity leaves sellable stock and **one** outward ledger row appears; sale refused; a second disposal is refused or a no-op; record whether disposing a healthy batch is permitted (it may be, for damage) — `NEEDS_PRODUCT_CONFIRMATION` if unclear.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-007 — Dispatch Return from Expiry Management

`PHARMACY_A · PHARMACIST · Expiry · Medium · Expiry Management`
**Steps:** **Dispatch Return** on a `NEAR`/`CRITICAL` batch; follow through to the supplier-return flow (`TC-PRF-011`); confirm the stock effect is applied **once**.
**Expected:** one outward movement; the batch shows the reduced quantity; `Supplier return failed` handled.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-008 — Filters, search, sort, empty state

`PHARMACY_A · PHARMACIST · Expiry · Medium · Expiry Management`
**Steps:** filter by each bucket; filter by medicine and by supplier if offered; sort by expiry date; a filter yielding nothing.
**Expected:** filters apply correctly; sorting by expiry is stable; readable empty state.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-009 — Expiry and the FEFO list interact correctly

`PHARMACY_A · PHARMACIST · Expiry · Critical · API / Billing Counter`
**Steps:** with batches at +3m, +24m and +36m: call `search-batches`; block the +3m one; call again; expire it; call again.
**Expected:** the earliest-expiry batch leads the list; once **blocked** it disappears; once **expired** it must not reappear. The available list must never offer unsellable stock.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-010 — Disposal and blocking are audited

`PHARMACY_A · PHARMACIST/ADM · Audit · High · Audit Logs`
**Steps:** after §above, open Audit Logs and filter to these actions.
**Expected:** block, freeze and dispose each recorded with batch, quantity, actor and timestamp — disposal is a stock write-off and must be traceable.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-011 — Expiry reporting reconciles

`PHARMACY_A · PHARMACIST · Reports · High · Reports & Analytics`
**Steps:** compare the expiry report's counts and value against the Expiry Management buckets and against a hand count of the batches created in `TC-PE-001`.
**Expected:** identical. Disposed and blocked quantities are reported consistently (record whether they are included in or excluded from "stock value").
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-012 — Double-submit on block and dispose

`PHARMACY_A · PHARMACIST · Expiry · Critical · Expiry Management`
**Steps:** double-click **Block**, then **Confirm Disposal**.
**Expected:** one state change and **one** ledger row each. A double disposal would write off the same stock twice — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-013 — Expiry actions across tenants and branches

`PHARMACY_A + PHARMACY_B (+ MULTI) · PHARMACIST · Isolation · Critical · API`
**Steps:** with PHARMACY_B's token: `POST /pharmacy/inventory/batches/{A batch}/block` and `/dispose`; then cross-branch inside MULTI with a branch user.
**Expected:** **403/404**; **A's (or Branch A's) batch state and quantity unchanged** — verify afterwards. Blocking or disposing another business's stock is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PE-014 — Persistence and re-login

`PHARMACY_A · PHARMACIST · Expiry · High · Expiry Management`
**Steps:** F5, then logout/login; re-read every bucket and batch status.
**Expected:** blocked, disposed and returned states all persist exactly.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
