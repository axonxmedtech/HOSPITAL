# 06 — HOSPITAL PHARMACY DEPARTMENT

**Baseline:** `aa143a7` · **Cases:** `TC-HP-001` … `TC-HP-040` · Tenant **HOSPITAL_A** (not the standalone PHARMACY tenant) · login `pharm.hospa@qa.test` → `/hospital/pharmacy`

**Tabs:** Dashboard · Billing Counter · Billing · Prescriptions · Inventory · Purchase Management · Suppliers · Manufacturers · Returns & Refunds · Expiry Management · Reports & Analytics · Audit Logs · Settings

**Verified strings:** Billing Counter — `Walk-in Mode` / `Hospital Rx Mode`, `Walk-in Customer`, `PRESCRIPTION`, `OUT OF STOCK`, `CASH`, `Please select a medicine batch first`, `Invalid quantity or insufficient stock`. Returns — `PATIENT` / `SUPPLIER`, `Search Bill`, **Process Patient Refund**, **Dispatch Supplier Return**, **Finalize Supplier Return**, `Refund failed`, `Sale search failed`. Expiry — `ACTIVE`/`NEAR`/`CRITICAL`/`EXPIRED`/`BLOCKED`/`DISPOSED`, **Block**, **Freeze Batch**, **Confirm Disposal**, **Dispatch Return**. Purchase — `DRAFT`/`POSTED`/`PAID`, **Post & Inward**. Prescriptions — `ACTIVE`, `Pending`, `UNLINKED`, **See Consultation**, `Date Not Recorded`. Dispense — **Dispense** (`Dispensing…`), `Select a medicine`, `No dosage recorded`.

> Scope: this is the **hospital's** pharmacy module. The standalone PHARMACY tenant pack is Tranche 3.

---

## A. ACCESS & DASHBOARD

### TC-HP-001 — Pharmacist login and tab set

`HOSPITAL_A · PHARMACIST · Pharmacy · Critical · /hospital/pharmacy`
**Steps:** log in; list tabs; note the URL.
**Expected:** `/hospital/pharmacy` (not `/pharmacy/pharmacy` — that is the pharmacy tenant); the 13 tabs above; PHARMACY module required.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-002 — Admin can open the pharmacy dashboard

`HOSPITAL_A · HOSPITAL_ADMIN · Pharmacy · Medium · /hospital/pharmacy`
**Steps:** as admin navigate to `/hospital/pharmacy`.
**Expected:** allowed (`ProtectedRoute` admits `PHARMACIST, HOSPITAL_ADMIN`); same data as the pharmacist sees.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-003 — Pharmacy Dashboard figures

`HOSPITAL_A · PHARMACIST · Dashboard · High · Dashboard`
**Steps:** record today's sales count/value, low stock, near-expiry; make one sale; refresh.
**Expected:** figures move by exactly that sale; match the admin Pharmacy tab (`TC-HA-037`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. INVENTORY & MASTERS

### TC-HP-004 — Inventory list, search, batch detail

`HOSPITAL_A · PHARMACIST · Inventory · High · Inventory`
**Steps:** open; search `QA Paracetamol`; open the batch; check qty/expiry/MRP.
**Expected:** matches what admin added (`TC-HA-034`); `OUT OF STOCK` badge on `QA Zero Stock`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-005 — Suppliers CRUD

`HOSPITAL_A · PHARMACIST · Suppliers · Medium · Suppliers`
**Steps:** add `QA Supplier One` (contact `9900000001`); edit; delete; blank name.
**Expected:** CRUD persists (`Supplier` = `ADM PHA` all verbs); validation messages; supplier offered in Purchase.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-006 — Manufacturers and Categories

`HOSPITAL_A · PHARMACIST · Masters · Medium · Manufacturers`
**Steps:** add/edit a manufacturer; toggle active; add a medicine category; use both when adding a medicine.
**Expected:** persist and appear in the medicine form; inactive ones drop out of pickers but stay on historical records.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-007 — Platform medicine catalogue is shared (expected)

`HOSPITAL_A · PHARMACIST · Inventory · Medium · Inventory`
**Steps:** = `TC-SA-046`. Search for a Super-Admin-created medicine.
**Expected:** found. **This is the one deliberately global list — not a tenant leak.** Stock quantities remain per tenant (`TC-ISO-041`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PURCHASE → STOCK

### TC-HP-008 — Purchase lifecycle DRAFT → Post & Inward

`HOSPITAL_A · PHARMACIST · Purchase · Critical · Purchase Management`
**Steps:** 1. New purchase (`PurchaseForm`): supplier, medicine, batch `QA-B5`, qty 50, expiry, rates. 2. Save as `DRAFT`. 3. Check inventory (**unchanged**). 4. **Post & Inward**. 5. Re-check inventory. 6. Mark `PAID`.
**Expected:** stock increases **only on posting** (`DRAFT` does not affect stock); status `DRAFT` → `POSTED` → `PAID`; audit entries.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-009 — Purchase validation & duplicate posting

`HOSPITAL_A · PHARMACIST · Purchase · High · Purchase Management`
**Steps:** negative qty; past expiry; no supplier; post the same purchase twice (double-click **Post & Inward**).
**Expected:** validation blocks; posting is idempotent — stock must **not** increase twice. Double-inward is a **Critical** inventory-integrity bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. ⭐ PRESCRIPTION → SALE → INVENTORY → BILLING

### TC-HP-010 — Doctor prescription appears in Prescriptions

`HOSPITAL_A · DOCTOR → PHARMACIST · Prescriptions · Critical · Prescriptions`
**Steps:** 1. Doctor completes a consultation for Rahul with `QA Paracetamol 500` and `QA Amoxicillin 250`. 2. Pharmacist → **Prescriptions**.
**Expected:** row appears with patient name, **PAT id**, doctor, date, `ACTIVE`/`Pending`; **See Consultation** opens the source; `Date Not Recorded` only when the date is genuinely absent; `UNLINKED` marks prescriptions with no appointment/OPD link.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-011 — Dispense against a prescription

`HOSPITAL_A · PHARMACIST · Prescriptions · Critical · DispenseModal`
**Steps:** open the prescription → **Dispense**; select the medicine batch; quantity; confirm (`Dispensing…`); leave the medicine unselected (`Select a medicine`); a prescription line with no dosage (`No dosage recorded`).
**Expected:** dispensing creates a sale linked to the patient and reduces the batch; validation messages as listed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-012 — Billing Counter — Hospital Rx Mode

`HOSPITAL_A · PHARMACIST · Billing Counter · Critical · Billing Counter`
**Steps:** 1. Switch to **Hospital Rx Mode**. 2. Search the patient (name/`PAT` id/phone) and load their prescription. 3. Pick a batch; qty 5; add. 4. Payment `CASH`. 5. Complete the sale. 6. Print the invoice.
**Expected:** sale created with `PRESCRIPTION` type and linked patient; stock 100 → 95; invoice shows the patient's **PAT id**; total = qty × rate (2 decimals).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-013 — Billing Counter — Walk-in Mode

`HOSPITAL_A · PHARMACIST · Billing Counter · High · Billing Counter`
**Steps:** **Walk-in Mode** → `Walk-in Customer`; add a medicine; complete; print.
**Expected:** sale type `PHARMACY`/walk-in with no patient link; stock decremented; invoice shows `Walk-in Customer`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-014 — Insufficient stock

`HOSPITAL_A · PHARMACIST · Billing Counter · Critical · Billing Counter`
**Steps:** batch has 5; enter qty 10; also try qty 0 and a negative; also sell from `QA Zero Stock`.
**Expected:** `Invalid quantity or insufficient stock`; **no sale created and no stock change** — verify inventory afterwards; `OUT OF STOCK` batches are not selectable.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-015 — No batch selected

`HOSPITAL_A · PHARMACIST · Billing Counter · High · Billing Counter`
**Steps:** enter a quantity without choosing a batch.
**Expected:** `Please select a medicine batch first`; no request sent.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-016 — Expired / blocked batch cannot be sold

`HOSPITAL_A · PHARMACIST · Billing Counter · Critical · Billing Counter`
**Steps:** set a batch expiry in the past (or use `QA Expiring Soon` after blocking it in Expiry Management); attempt to sell it; attempt via API with that batch id.
**Expected:** not offered in the picker; API refuses. Selling an expired batch is a **Critical** patient-safety failure.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-017 — ⭐ Double-submit a sale

`HOSPITAL_A · PHARMACIST · Billing Counter · Critical · Billing Counter`
**Steps:** double-click **Complete sale**; check sales list and stock.
**Expected:** exactly **one** sale and **one** decrement. Two sales for one transaction is a **Critical** financial + inventory bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-018 — Sale during a backend failure

`HOSPITAL_A · PHARMACIST · Billing Counter · Critical · Billing Counter`
**Steps:** stop the backend mid-sale; complete; restart; retry.
**Expected:** readable error; **stock not decremented by the failed attempt**; retry creates exactly one sale.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-019 — Two pharmacists selling the last unit (manual concurrency)

`HOSPITAL_A · PHARMACIST ×2 · Billing Counter · Critical · Billing Counter`
**Steps:** batch qty = 1; two browser tabs with the same/two pharmacists; both prepare a sale of 1; submit as close together as possible.
**Expected:** one succeeds, the other is refused; stock never goes negative. Record the exact message. (A negative stock value is **Critical**.)
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-020 — Billing (sales history)

`HOSPITAL_A · PHARMACIST · Billing · High · Billing`
**Steps:** open **Billing** (history); search by invoice/patient/date; reprint an invoice; check totals.
**Expected:** all sales listed with type and amount; totals equal the Dashboard; reprint identical to the original.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. RETURNS & REFUNDS

### TC-HP-021 — Patient refund

`HOSPITAL_A · PHARMACIST · Returns · Critical · Returns & Refunds ▸ PATIENT`
**Steps:** 1. `PATIENT` tab → **Search Bill** by invoice. 2. Select lines and quantity. 3. **Process Patient Refund**. 4. Check stock, the sale record and the Dashboard totals.
**Expected:** refunded quantity returns to the batch; the sale shows the return; today's net sales fall by the refunded amount; `Sale search failed` / `Refund failed` handled.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-022 — Over-refund and duplicate refund

`HOSPITAL_A · PHARMACIST · Returns · Critical · Returns & Refunds`
**Steps:** refund more than sold; refund the same line twice.
**Expected:** refused; stock never inflated beyond what was sold.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-023 — Supplier return

`HOSPITAL_A · PHARMACIST · Returns · High · Returns & Refunds ▸ SUPPLIER`
**Steps:** `SUPPLIER` tab → search batch (`Batch search failed` path) → **Dispatch Supplier Return** → **Finalize Supplier Return**.
**Expected:** stock reduces on dispatch (or on finalize — record which); supplier return recorded; `Supplier return dispatch failed` handled; a finalized return cannot be re-dispatched.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-024 — Refund for a walk-in sale

`HOSPITAL_A · PHARMACIST · Returns · Medium · Returns & Refunds`
**Steps:** refund a `Walk-In` sale.
**Expected:** allowed without a patient link; labelled `Walk-In`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## F. EXPIRY MANAGEMENT

### TC-HP-025 — Expiry buckets

`HOSPITAL_A · PHARMACIST · Expiry · High · Expiry Management`
**Steps:** with `QA Expiring Soon` (+20 days) and a long-dated batch, open Expiry Management.
**Expected:** buckets `ACTIVE`, `NEAR`, `CRITICAL`, `EXPIRED` computed from expiry dates; counts and units correct.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-026 — Block / freeze a batch

`HOSPITAL_A · PHARMACIST · Expiry · Critical · Expiry Management`
**Steps:** **Block** a batch (`Block failed` path); **Freeze Batch**; then try to sell it at the counter and via API.
**Expected:** status `BLOCKED`; not sellable in UI or API. This is the safety gate for expiring stock.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-027 — Dispose an expired batch

`HOSPITAL_A · PHARMACIST · Expiry · High · Expiry Management`
**Steps:** **Confirm Disposal** on an `EXPIRED` batch (`Disposal failed` path); check stock and reports.
**Expected:** status `DISPOSED`; quantity removed from sellable stock; disposal recorded (`Safe pharmaceutical destruction program` note) and auditable.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-028 — Dispatch Return from Expiry

`HOSPITAL_A · PHARMACIST · Expiry · Medium · Expiry Management`
**Steps:** **Dispatch Return** for a near-expiry batch to its supplier.
**Expected:** links to the supplier-return flow; `Supplier return failed` handled.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## G. REPORTS · AUDIT · SETTINGS

### TC-HP-029 — Pharmacy Reports & Analytics

`HOSPITAL_A · PHARMACIST · Reports · High · Reports & Analytics`
**Steps:** run each report (sales, stock, expiry, purchase) for today; compare against the sales made in §D–F; empty date range; export if present.
**Expected:** every total reconciles with the hand count; empty state; tenant-scoped (`TC-ISO-055`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-030 — Pharmacy Audit Logs

`HOSPITAL_A · PHARMACIST · Audit · Medium · Audit Logs`
**Steps:** after sales, refunds, blocks and disposals, open Audit Logs.
**Expected:** entries with actor and entity; branch column where applicable; no secrets.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-031 — Pharmacy Settings tab

`HOSPITAL_A · PHARMACIST · Settings · Medium · Settings`
**Steps:** open; record every option; change one; F5; check the downstream screen; restore.
**Expected:** whatever the tab exposes (barcode workflow, print options) persists and affects the counter. Record the exact list — do not assume.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-032 — Barcode workflow toggle downstream

`HOSPITAL_A · HOSPITAL_ADMIN → PHARMACIST · Settings · Medium · Billing Counter`
**Steps:** = `TC-HAS-005`.
**Expected:** scan field appears/disappears; manual selection always available.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## H. PERMISSIONS · ISOLATION · UI

### TC-HP-033 — Pharmacist cannot reach clinical areas

`HOSPITAL_A · PHARMACIST · Authorization · Critical · API`
**Steps:** = `TC-PERM-004`: `GET/POST /hospital/opd`, `/hospital/nurse/vitals`, `/hospital/nurse/notes`, `/hospital/patients`.
**Expected:** 403 each. **Also test `GET /hospital/wards`** — matrix says `PHA` is admitted; record (`NEEDS_PRODUCT_CONFIRMATION`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-034 — ⚠️ Pharmacist and prescription PDFs / settings (drift)

`HOSPITAL_A · PHARMACIST · Documents/Settings · High · API`
**Steps:** = `TC-API-007` and `TC-API-006` with the pharmacist token.
**Expected:** record both. Prescription PDF access by a pharmacist is `NEEDS_PRODUCT_CONFIRMATION`; settings write is **Critical** drift if 200.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-035 — Pharmacy tenant isolation

`HOSPITAL_A + B · PHARMACIST · all · Critical · API`
**Steps:** = `TC-ISO-038`…`044` between HOSPITAL_A and HOSPITAL_B pharmacists.
**Expected:** sales, batches, suppliers, purchases all scoped; selling from B's batch refused and B's stock unchanged.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-036 — PHARMACY module off

`HOSPITAL_M · PHARMACIST/ADMIN · Entitlement · High · sidebar/API`
**Steps:** = `TC-MOD-012`. Also: can a pharmacist even be created in Hospital M (`TC-HA-015`)?
**Expected:** tabs hidden; API result recorded (no `@RequireModule("PHARMACY")` → drift likely).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-037 — Sale ↔ hospital billing relationship

`HOSPITAL_A · PHARMACIST → RECEPTIONIST · Billing · High · Billing`
**Steps:** after a prescription sale, open the patient's hospital Billing and the patient timeline.
**Expected:** record whether the pharmacy sale appears on the hospital bill or stays a separate pharmacy invoice — document the actual model; `NEEDS_PRODUCT_CONFIRMATION` if ambiguous. Totals must not be double-counted in the admin Overview.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-038 — Sale timestamps and "today"

`HOSPITAL_A · PHARMACIST · Reports · Medium · Dashboard/Reports`
**Steps:** make a sale; compare its time on the invoice, the list and the report; check it lands in **today's** IST business day.
**Expected:** consistent IST wall-clock; `pharmacy_sales.created_at` is second-precision — sub-second differences are not a bug.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-039 — Pharmacy UI states

`HOSPITAL_A · PHARMACIST · UI · Medium · all tabs`
**Steps:** empty inventory, no sales, backend down, slow network; long medicine names; 2-decimal currency; modals close via ✕/ESC/backdrop.
**Expected:** EmptyState per tab; readable errors; no crash; amounts always 2 decimals.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-HP-040 — Re-login persistence

`HOSPITAL_A · PHARMACIST · Persistence · High · all`
**Steps:** logout/login; re-check stock, sales, returns, blocked/disposed batches.
**Expected:** all persist exactly.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
