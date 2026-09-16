# 03 — COMPLETE STANDALONE PHARMACY JOURNEY

**Cases:** `TC-E2E-051` … `TC-E2E-058` · Tenants **PHARMACY_A** (SINGLE), **PHARM_SOLO**, **PHARM_MULTI**

A standalone pharmacy is a retail business, not a clinical one. The journey is
procure → stock → sell → return → reconcile, and it ends by proving the clinical domains stay shut.

## State table (SINGLE tier)

| #   | Stage       | Actor         | Entity                             | Identifier | Before              | Action        | After                 | Evidence    |
| --- | ----------- | ------------- | ---------------------------------- | ---------- | ------------------- | ------------- | --------------------- | ----------- |
| 1   | Tenant      | S-SA          | Pharmacy + tier                    | `id=____`  | —                   | create        | Active                | screenshot  |
| 2   | Staff       | S-PADM        | Pharmacist                         | `____`     | —                   | create        | Active                | screenshot  |
| 3   | Masters     | S-PPHA        | Manufacturer / Category / Supplier | `____`     | —                   | create        | Active                | screenshot  |
| 4   | Catalogue   | S-PPHA        | Medicine                           | `____`     | —                   | create        | Active                | screenshot  |
| 5   | Purchase    | S-PPHA        | Purchase                           | `____`     | —                   | create        | **`DRAFT`**           | screenshot  |
| 6   | Stock check | system        | batch `QP-B4`                      | —          | **not yet stocked** | —             | unchanged             | screenshot  |
| 7   | Inward      | S-PPHA        | Purchase                           | same       | `DRAFT`             | Post & Inward | **`POSTED`**          | screenshot  |
| 8   | Stock       | system        | `QP-B4`                            | `____`     | 0                   | inward        | **50**                | screenshot  |
| 9   | FEFO        | S-PPHA        | batch list                         | —          | —                   | search        | earliest-expiry first | screenshot  |
| 10  | Sale        | S-PPHA        | Sale                               | `____`     | —                   | sell 8        | created               | invoice PDF |
| 11  | Stock       | system        | `QP-B4`                            | same       | 50                  | sale          | **42**                | screenshot  |
| 12  | Payment     | S-PPHA        | Purchase                           | same       | `POSTED`            | mark paid     | **`PAID`**            | screenshot  |
| 13  | Refund      | S-PPHA        | Return                             | `____`     | —                   | refund 2      | processed             | screenshot  |
| 14  | Stock       | system        | `QP-B4`                            | same       | 42                  | refund        | **44**                | screenshot  |
| 15  | Reports     | S-PPHA/S-PADM | dashboards + CSV                   | —          | —                   | read          | reconcile             | CSV file    |
| 16  | Audit       | S-PADM        | Audit log                          | —          | —                   | read          | all actions           | screenshot  |
| 17  | **Denial**  | S-PADM/S-PPHA | OPD · IPD · Beds · Wards           | —          | —                   | attempt       | **denied**            | API log     |

---

### TC-E2E-051 — Stages 1–4: tenant, tier, staff and catalogue

`PHARMACY_A · SA + ADM + PHA · Setup · Critical`
**Steps:** `TC-SA-020` · `TC-SA-010` · `TC-PA-001` · `TC-PA-003` · `TC-PT-012` · `TC-PA-006` · `TC-PP-001` · `TC-PP-006` · `TC-PI-001`.
**E2E assertion:** the pharmacy plan offers **only** the PHARMACY base module and the three tiers; the admin lands on **`/pharmacy/admin`** (not `/hospital/admin`) with a tab set matching the tier; the pharmacist lands on **`/pharmacy/pharmacy`** with 13 tabs; the platform medicine catalogue is searchable (shared by design) while **stock is per tenant**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-052 — ⭐ Stages 5–8: stock moves **only** on posting

`PHARMACY_A · PHARMACIST · Purchase · Critical`
**Steps:** `TC-PP-008` · `TC-PP-009` · `TC-PP-010`.
**E2E assertion:** a `DRAFT` purchase moves **nothing** — no sellable batch, no ledger row. **Post & Inward** creates the batch at exactly the purchased quantity and **one** ledger row. Posting twice (double-click **and** API replay) must **not** inward twice; a double inward silently inflates stock and every downstream report — **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-053 — Stages 9–11: FEFO ordering and the sale

`PHARMACY_A · PHARMACIST · Sales · Critical`
**Steps:** `TC-PI-012` · `TC-PI-013` · `TC-PS-001` · `TC-PS-002`.
**E2E assertion:** `GET /pharmacy/inventory/search-batches` returns batches **earliest-expiry first** (FEFO **is** implemented — `searchAvailableBatchesFEFO`); blocked and zero-quantity batches are excluded; the batch the pharmacist actually **chooses** is the one that decrements, by exactly the quantity sold. FEFO orders the suggestion; it does not silently substitute a batch.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-054 — Stages 12–14: refund restores stock and nets revenue

`PHARMACY_A · PHARMACIST · Returns · Critical`
**Steps:** `TC-PRF-001` · `TC-PRF-003` · `TC-PRF-004`.
**E2E assertion:** the refunded quantity returns to **the batch it was sold from**; the day's net sales fall by the refunded value; over-refund and a double-submitted refund are both refused; stock is never inflated beyond what was sold.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-055 — Stages 15–16: every report reconciles with the ledger

`PHARMACY_A · PHA + ADM · Reports/Audit · Critical`
**Steps:** `TC-PR-001` (the reconciliation fixture) · `TC-PR-008` (CSV) · `TC-PR-009` · `TC-PR-011`.
**E2E assertion:** the Inventory list, batch ledger, pharmacist Dashboard, Reports dashboard, admin Analytics tab and the exported CSV **all agree with the hand arithmetic**. Three different numbers for one day is a **High** defect even if each looks plausible. Every material action (post, sale, refund, adjustment, block, dispose) has an audit entry with an actor.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-056 — The SOLO tier: one login, two dashboards

`PHARM_SOLO · HOSPITAL_ADMIN · Tiers · Critical`
**Steps:** `TC-PX-008` (P6) in full.
**E2E assertion:** the SOLO admin lands on `/pharmacy/pharmacy`, runs the entire pharmacist day with the **admin** token, switches to `/pharmacy/admin` and sees **those same sales** in Billing/Analytics/Audit; the switcher and `sessionStorage.activeDashboard` behave per `TC-PT-004`/`008`; a password reset revokes **both** surfaces at once; **no third role is granted** (nurse/OT/ICU endpoints 403).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-057 — The MULTI tier: branches

`PHARM_MULTI · ADM + branch users · Branches · Critical`
**Steps:** `TC-E2E-079`…`TC-E2E-083` in `15-MULTI-BRANCH-PHARMACY-JOURNEY.md`.
**E2E assertion:** see that document — the headline is **Branch A 10 → sell 2 → 8, Branch B stays 5**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-058 — ⭐ Stage 17: clinical domains must stay shut

`PHARMACY_A · ADM + PHA · Authorization · Critical`
**Steps:** `TC-PX-010` (P8) in full — the method matrix over `/pharmacy/opd`, `/ipd`, `/beds`, `/wards`, `/patients`, `/appointments`, plus `TC-PH-020` for the never-aliased namespaces and `TC-PA-008` / `TC-PH-016` for the unsupported roles.
**E2E assertion:** ICU, OT and nursing are genuinely closed (404 on `/pharmacy/...`, 403 on `/hospital/...`), and `/hospital/dashboard` is 403.
**Known drift — 36 ungated endpoints across opd/ipd/beds/wards and 17 across doctors/receptionists.** Product policy is that a standalone pharmacy operates **none** of these. Therefore a **200 is a FAIL / IMPLEMENTATION_DRIFT** at **HIGH**; a created row, another tenant's data, or a downloadable case-paper PDF raises it to **Critical**. Also record the landing URL a pharmacy-tenant DOCTOR receives (`/hospital/doctor` is expected from `LandingRedirect` — itself part of the finding).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
