# 09 — PHARMACY REPORTS & AUDIT

**Baseline:** `aa143a7` · **Cases:** `TC-PR-001` … `TC-PR-014` · Tenant PHARMACY_A

**Endpoints:** `GET /pharmacy/reports/dashboard` · **`GET /pharmacy/reports/export`** (`exportLedgerCsv`) · `GET /pharmacy/sales/stats` · `GET /pharmacy/inventory` · `/low-stock` · `/expiring` · `/returns-history` · `/transactions/{batchId}` · `GET /pharmacy/audit-logs` (tenant audit, **admin only**)

> **"The chart rendered" is never a pass.** Every figure below must be reconciled against records
> you created by hand.

---

## A. THE RECONCILIATION FIXTURE

### TC-PR-001 — ⭐ Build a known ledger, then verify every report against it

`PHARMACY_A · PHARMACIST · Reports · Critical · all reports`
**Steps** — on a **fresh** medicine `QA Report Med` with a single batch, perform exactly this and record the timestamp of each:

| #   | Action                      | Qty delta | Value            |
| --- | --------------------------- | --------- | ---------------- |
| 1   | create batch, opening stock | **+10**   | cost 10 × ₹20    |
| 2   | purchase posted             | **+5**    | cost 5 × ₹20     |
| 3   | sale                        | **−3**    | sold 3 × ₹30     |
| 4   | patient refund              | **+1**    | refunded 1 × ₹30 |
| 5   | supplier return             | **−2**    | —                |
| 6   | stock adjustment (loss)     | **−1**    | —                |

**Expected — compute by hand first, then compare each screen:**

- **On-hand = 10 + 5 − 3 + 1 − 2 − 1 = 10**
- **Units sold today = 3**, **units refunded = 1**, **net units sold = 2**
- **Gross sales = 3 × 30 = ₹90**, **refunds = ₹30**, **net sales = ₹60**
- **Batch ledger** (`/transactions/{batchId}`) shows **six** rows whose running balance ends at **10**.

Now check: Inventory list · Dashboard · Reports dashboard · sales report · purchase report · expiry report · the CSV export.
**Any screen that disagrees with this arithmetic is a defect** — record which screen and by how much. A stock figure that disagrees with its own ledger is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-002 — Sales report

`PHARMACY_A · PHARMACIST · Reports · Critical · Reports & Analytics`
**Steps:** open the sales report for today; compare count, gross, refunds and net against `TC-PR-001`; drill into a sale if the report allows it.
**Expected:** every figure matches; **refunds are netted, not ignored**; a drill-through opens the correct sale.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-003 — Inventory / stock report

`PHARMACY_A · PHARMACIST · Reports · Critical · Reports & Analytics`
**Steps:** compare on-hand quantity and stock value against the Inventory list and the ledger; check how **blocked** and **disposed** stock are treated.
**Expected:** on-hand = 10 for the fixture medicine; record explicitly whether blocked/disposed units are included in stock value (`TC-PE-011`) — and whether that treatment is consistent across the Dashboard and the report.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-004 — Purchase report

`PHARMACY_A · PHARMACIST · Reports · High · Reports & Analytics`
**Steps:** compare against the purchases posted in `05`; include a `DRAFT` purchase and check whether it is counted.
**Expected:** only **POSTED** (and PAID) purchases contribute — a DRAFT that moved no stock must not appear in purchase value. If drafts are counted, that is **High** (it overstates cost).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-005 — Expiry report

`PHARMACY_A · PHARMACIST · Reports · High · Reports & Analytics`
**Steps:** = `TC-PE-011`.
**Expected:** bucket counts and values match Expiry Management.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-006 — Returns report / history

`PHARMACY_A · PHARMACIST · Reports · High · Reports / API`
**Steps:** `GET /pharmacy/inventory/returns-history`; compare with the report and with `TC-PRF-014`.
**Expected:** patient refunds and supplier returns listed separately with the correct quantities and batches.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. FILTERS, EXPORT, DASHBOARD

### TC-PR-007 — Date filters and boundaries

`PHARMACY_A · PHARMACIST · Reports · High · Reports & Analytics`
**Steps:** run each report for: today; yesterday (should exclude today's fixture); a range spanning both; a future range (empty); an inverted range (end before start).
**Expected:** today's figures appear only in ranges containing today; the empty range shows an empty state; the inverted range is refused or normalised — never a 500. Boundaries are **IST business days**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-008 — ⭐ CSV ledger export

`PHARMACY_A · PHARMACIST · Reports · High · API / Reports`
**Steps:** `GET /pharmacy/reports/export` (or the export control); open the CSV in a spreadsheet.
**Expected:** the file downloads with a sensible filename; the header row is readable; **the rows reconcile with `TC-PR-001`'s six movements**; amounts are plain numbers (not locale-formatted text); **only this tenant's rows** are present. Check for a UTF-8 BOM/encoding issue with any non-ASCII medicine name.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-009 — Dashboard vs reports vs admin Analytics

`PHARMACY_A · PHARMACIST + ADM · Reports · High · Dashboard / Reports / Analytics`
**Steps:** compare the pharmacist Dashboard (`TC-PH-003`), the Reports dashboard, and the admin **Analytics** tab (`TC-PA-011`) for the same day.
**Expected:** all three agree exactly. Three different numbers for one day is a **High** defect even if each is individually plausible.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-010 — Reports UI states

`PHARMACY_A · PHARMACIST · Reports · Medium · Reports & Analytics`
**Steps:** a brand-new tenant with no data; slow network; backend down; a very long medicine name in a chart label; a huge value (₹10,00,000).
**Expected:** empty state rather than a blank chart; readable error; labels truncate; currency always two decimals with consistent separators.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. AUDIT

### TC-PR-011 — ⭐ Every material action is audited

`PHARMACY_A · PHARMACIST + ADM · Audit · High · Audit Logs`
**Steps:** perform each action and then confirm its entry:

| Action                                | Expected audit entry |
| ------------------------------------- | -------------------- |
| create medicine                       | ____                 |
| create/toggle manufacturer, category  | ____                 |
| create supplier / edit / delete       | ____                 |
| create purchase (DRAFT)               | ____                 |
| **post purchase**                     | ____                 |
| **stock adjustment** (with reason)    | ____                 |
| **sale**                              | ____                 |
| **patient refund**                    | ____                 |
| **supplier return**                   | ____                 |
| block / freeze batch                  | ____                 |
| **dispose batch**                     | ____                 |
| create pharmacist / reset password    | ____                 |
| create / edit / delete branch (MULTI) | ____                 |
| settings change                       | ____                 |

**Expected:** each has an action name, the acting user's email, an entity reference and a timestamp. **Mark any row with no entry as a gap** — a stock adjustment or disposal with no audit trail is **High**, because it is an untraceable write-off.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-012 — Audit entries carry the correct tenant and branch

`PHARMACY_A + PHARMACY_B + PHARM_MULTI · ADM · Audit · Critical · Audit Logs`
**Steps:** 1. Perform an audited action in PHARMACY_A and one in PHARMACY_B; open each tenant's Audit Logs and search for the other's action, user email and medicine name. 2. In MULTI, act in Branch A and Branch B; check the branch column and each branch user's view.
**Expected:** **no cross-tenant entries**; branch entries carry `branch_id` and a branch user sees only their own (`TC-PT-029`). An audit log is otherwise a back door into another business's activity.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-013 — Audit privacy and access

`PHARMACY_A · PHARMACIST + ADM · Audit · High · Audit Logs / API`
**Steps:** search the audit log for passwords, tokens and full customer phone numbers; then `GET /pharmacy/audit-logs` with the **pharmacist** token and with the **admin** token.
**Expected:** no secrets; phone numbers masked where recorded at all; pharmacist **403**, admin **200** (`TC-PH-013` — and if the pharmacist's Audit Logs tab 403s, that is the `UI_WITHOUT_BACKEND` finding).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PR-014 — Reports and audit tenant isolation

`PHARMACY_A + PHARMACY_B + HOSPITAL_A + CLINIC_A · PHARMACIST · Isolation · Critical · API`
**Steps:** with PHARMACY_B's token call `/pharmacy/reports/dashboard`, `/pharmacy/reports/export`, `/pharmacy/sales/stats`, `/pharmacy/inventory`, `/pharmacy/inventory/returns-history`; compare every figure against PHARMACY_B's own hand count. Then open the exported CSV and search for PHARMACY_A's medicine names.
**Expected:** every figure is B's alone; **no A row in the CSV**. All four tenant types share the `/pharmacy/**` namespace, so an aggregate that silently includes another tenant is a **Critical** leak even though no name is displayed on screen.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
