# 05 — PHARMACY PURCHASES & SUPPLIERS

**Baseline:** `aa143a7` · **Cases:** `TC-PP-001` … `TC-PP-016` · Tenant PHARMACY_A

**Endpoints:** `/pharmacy/purchases` — `GET /`, `GET /{id}`, `POST /`, **`POST /{id}/post`** (both roles) · `/pharmacy/suppliers` — `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` · `/pharmacy/manufacturers` — `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `PATCH /{id}/status`
**Purchase statuses (verified in the UI):** `DRAFT` · `POSTED` · `PAID`; action **Post & Inward**.

> There is **no** purchase edit, cancel or delete endpoint. A purchase can be created and posted,
> nothing more. Record that as `PARTIAL` rather than raising "cannot edit a purchase" as a bug.

---

## A. SUPPLIERS

### TC-PP-001 — Create a supplier

`PHARMACY_A · PHARMACIST · Suppliers · High · Suppliers`
**Steps:** create `QA Pharm Supplier One` with contact `9900000001`, address, GST/registration fields if present; save; reload.
**Expected:** persists; offered in the Purchase form and in the supplier-return flow.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-002 — Supplier validation and duplicates

`PHARMACY_A · PHARMACIST · Suppliers · Medium · Suppliers`
**Steps:** blank name; a 300-char name; an invalid phone; **the same supplier name twice**.
**Expected:** field messages; record whether a duplicate name is refused (`NEEDS_PRODUCT_CONFIRMATION` if allowed).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-003 — Edit a supplier and propagate

`PHARMACY_A · PHARMACIST · Suppliers · Medium · Suppliers`
**Steps:** rename the supplier; check the Purchase form picker and an **existing posted purchase**.
**Expected:** the picker shows the new name; the historical purchase shows the current name (or the name captured at the time) — record which, it matters for reprints.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-004 — Delete a supplier, including one with purchases

`PHARMACY_A · PHARMACIST · Suppliers · High · Suppliers`
**Steps:** (a) delete an unused supplier; (b) delete a supplier that has a **posted purchase**; then open that purchase.
**Expected:** (a) removed after confirmation. (b) record: refused, soft-deleted, or hard-deleted. **The posted purchase and its stock must survive** — if deleting a supplier breaks a purchase record, that is **High**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-005 — Supplier search, sort, pagination, empty state

`PHARMACY_A · PHARMACIST · Suppliers · Low · Suppliers`
**Steps:** search by name and phone; sort; page; search something absent.
**Expected:** partial case-insensitive match; readable empty state.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. MANUFACTURERS

### TC-PP-006 — Manufacturer CRUD and status toggle

`PHARMACY_A · PHARMACIST · Manufacturers · Medium · Manufacturers`
**Steps:** create, edit, `PATCH /{id}/status` to inactive, then re-activate; use it on a medicine; blank name; duplicate name.
**Expected:** CRUD persists; an inactive manufacturer drops out of the medicine form but **remains on existing medicines**; validation messages on blank/duplicate.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-007 — Free-text manufacturer from a purchase

`PHARMACY_A · PHARMACIST · Manufacturers · Medium · Purchase`
**Steps:** on the purchase form, enter a manufacturer name that does not exist in the master list; post the purchase; then open Manufacturers.
**Expected:** the schema supports a free-text manufacturer name on the medicine master — record whether the purchase creates a manufacturer record, stores the text only, or refuses. `NEEDS_PRODUCT_CONFIRMATION`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PURCHASE LIFECYCLE

### TC-PP-008 — ⭐ Create DRAFT → stock unchanged

`PHARMACY_A · PHARMACIST · Purchase · Critical · Purchase Management`
**Steps:** 1. Record the current quantity of `QP-B1`. 2. New purchase: supplier `QA Pharm Supplier One`; line 1 = `QA Pharm Paracetamol 500`, batch `QP-B4`, qty **50**, expiry +18 months, cost and MRP. 3. Save as **DRAFT**. 4. Re-read Inventory and the batch ledger.
**Expected:** the purchase is listed as `DRAFT`; **no stock movement at all** — `QP-B4` does not yet exist as sellable stock and no ledger row appears. Stock that moves on draft is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-009 — ⭐ Post & Inward → stock increases

`PHARMACY_A · PHARMACIST · Purchase · Critical · Purchase Management`
**Steps:** open the DRAFT → **Post & Inward** (`POST /pharmacy/purchases/{id}/post`); re-read Inventory, the batch and the ledger.
**Expected:** status `DRAFT` → **`POSTED`**; batch `QP-B4` now holds **50**; the ledger shows exactly **one** inward row referencing the purchase; the medicine's total stock rises by 50.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-010 — ⭐ Posting is idempotent

`PHARMACY_A · PHARMACIST · Purchase · Critical · Purchase Management`
**Steps:** 1. **Double-click Post & Inward** on a fresh DRAFT. 2. Then call `POST /pharmacy/purchases/{id}/post` again via API on an already-`POSTED` purchase. 3. Re-read the batch quantity and the ledger.
**Expected:** stock increases **exactly once**; the second attempt is refused (400/409) or is a no-op. **A double inward is a Critical inventory-integrity bug** — it silently inflates stock and every downstream report.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-011 — Purchase validation

`PHARMACY_A · PHARMACIST · Purchase · High · Purchase Management`
**Steps:** save/post with: no supplier; no line items; **negative quantity**; quantity 0; **past expiry**; negative cost; cost greater than MRP; a 2-line purchase where one line is invalid.
**Expected:** each refused with a field-level message; **no partial purchase and no partial stock movement**; never a 500. Record the cost>MRP behaviour — it may be allowed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-012 — Multi-line purchase

`PHARMACY_A · PHARMACIST · Purchase · High · Purchase Management`
**Steps:** create a purchase with **three** lines across two medicines (one medicine getting two different batches); post it; check all three batches and the purchase total.
**Expected:** all three batches created/incremented in one posting; the purchase total equals the sum of the line costs; three ledger rows, one per batch.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-013 — Purchase into an existing batch number

`PHARMACY_A · PHARMACIST · Purchase · High · Purchase Management`
**Steps:** post a purchase using batch number **`QP-B1`** (which already exists with a different expiry), then with the **same** expiry.
**Expected:** record whether it merges into the existing batch (quantity adds) or creates a second batch row. A merge with a _different_ expiry would be wrong — the expiry of existing stock must not change. Flag as **High** if the expiry is overwritten.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-014 — Payment status and the absence of edit/cancel

`PHARMACY_A · PHARMACIST · Purchase · Medium · Purchase Management`
**Steps:** mark a posted purchase **PAID**; then look for Edit, Cancel and Delete actions; check the API for such endpoints.
**Expected:** `POSTED` → `PAID` works. **No edit, cancel or delete exists** (only `GET`, `GET /{id}`, `POST`, `POST /{id}/post`) — record as **`PARTIAL`**, not a bug. A mistaken purchase can only be corrected by a stock adjustment or a supplier return; note that as the workaround.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PP-015 — Purchase list, detail, search, filter, empty state

`PHARMACY_A · PHARMACIST · Purchase · Medium · Purchase Management`
**Steps:** list; filter by status `DRAFT`/`POSTED`/`PAID`; search by supplier or purchase number; open a detail (`GET /{id}`); empty filter.
**Expected:** correct rows and totals; the detail shows every line, batch, quantity and cost; readable empty state.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. ISOLATION & BRANCH

### TC-PP-016 — Purchases and suppliers: tenant and branch scope

`PHARMACY_A + PHARMACY_B (+ MULTI) · PHARMACIST/ADM · Isolation · Critical · API`
**Steps:**

1. With PHARMACY_B's token: list suppliers and purchases (A's absent); `GET /pharmacy/purchases/{A id}`; `GET /pharmacy/suppliers/{A id}`; `PUT`/`DELETE` A's supplier; `POST /pharmacy/purchases/{A id}/post`.
2. In MULTI: post a purchase into **Branch A** and confirm **Branch B stock is unchanged** (`TC-PT-027`); then attempt to post a Branch A purchase with the Branch B user's token.
   **Expected:** **403/404** on every cross-tenant call; **A's supplier, purchase and stock unchanged**; stock lands only in the purchasing branch. Posting another tenant's purchase would create stock in their inventory — **Critical**.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
