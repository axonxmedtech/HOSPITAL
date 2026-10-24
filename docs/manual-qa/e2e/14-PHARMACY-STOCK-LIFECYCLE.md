# 14 — PHARMACY STOCK LIFECYCLE (numeric fixture)

**Cases:** `TC-E2E-067` … `TC-E2E-072` · Tenant **PHARMACY_A** (repeat in HOSPITAL_A's pharmacy dept and CLINIC_A where noted)

Stock is the pharmacy's money. This journey proves that **every movement is accounted for and the
ledger never disagrees with the on-hand figure**.

## The fixture — run it exactly, in order, on a **fresh** medicine and batch

| Step | Action                            | Case         | Delta | **Expected on-hand**         | Actual |
| ---- | --------------------------------- | ------------ | ----- | ---------------------------- | ------ |
| 0    | create batch, opening stock       | `TC-PI-006`  | —     | **10**                       | ____   |
| 1    | purchase `DRAFT` (20 units)       | `TC-PP-008`  | **0** | **10** (draft moves nothing) | ____   |
| 2    | **Post & Inward**                 | `TC-PP-009`  | +20   | **30**                       | ____   |
| 3    | sale of 4                         | `TC-PS-001`  | −4    | **26**                       | ____   |
| 4    | patient refund of 2               | `TC-PRF-001` | +2    | **28**                       | ____   |
| 5    | stock adjustment −3 (with reason) | `TC-PI-010`  | −3    | **25**                       | ____   |
| 6    | supplier return of 5              | `TC-PRF-011` | −5    | **20**                       | ____   |

**Then verify the same number — 20 — in every one of these:**

| Surface                                                                                | Expected                             | Actual |
| -------------------------------------------------------------------------------------- | ------------------------------------ | ------ |
| Inventory list (on-hand)                                                               | 20                                   | ____   |
| `GET /pharmacy/inventory/transactions/{batchId}` — **running balance of the last row** | 20                                   | ____   |
| — and the ledger has **6 rows** (steps 0,2,3,4,5,6 — **not** step 1)                   | 6 rows                               | ____   |
| Pharmacist Dashboard stock figure                                                      | consistent                           | ____   |
| Reports ▸ inventory/stock report                                                       | 20                                   | ____   |
| Sales report (units sold today)                                                        | 4, refunded 1×2 → net 2              | ____   |
| `GET /pharmacy/inventory/returns-history`                                              | 1 patient refund + 1 supplier return | ____   |
| **CSV export** (`/pharmacy/reports/export`)                                            | rows reconcile to 20                 | ____   |
| Admin Analytics tab                                                                    | same as pharmacist Dashboard         | ____   |

> **If any surface disagrees with the ledger, that is Critical** — stock and its own audit trail
> must never diverge. Record which surface and by how much.

---

### TC-E2E-067 — ⭐ Run the fixture and reconcile all nine surfaces

`PHARMACY_A · PHARMACIST · Inventory · Critical`
**Steps:** the table above, then `TC-PI-019` · `TC-PI-009` · `TC-PR-001` · `TC-PR-008`.
**E2E assertion:** final on-hand **20**, ledger ends at **20** with **6** rows, and all nine surfaces agree. Note especially that **step 1 must produce no row at all** — a `DRAFT` purchase that moves stock is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-068 — FEFO across three batches

`PHARMACY_A · PHARMACIST · Inventory · Critical`
**Steps:** `TC-PI-008` (three batches: +3m/30, +24m/100, +36m/50) · `TC-PI-012` · `TC-PI-013` · `TC-PS-003`.
**E2E assertion:** `search-batches` returns **earliest expiry first**; the medicine's total equals the sum of its batches; selling explicitly from the **latest**-expiry batch decrements **only that batch** — FEFO orders the suggestion, it does not substitute silently. Then block the earliest batch and confirm it disappears from the available list.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-069 — Unsellable stock stays unsellable

`PHARMACY_A · PHARMACIST · Expiry · Critical`
**Steps:** `TC-PE-001` · `TC-PE-002` (today-expiry boundary) · `TC-PE-004` (block) · `TC-PE-006` (dispose) · `TC-PS-007` (sell via API).
**E2E assertion:** expired, blocked and disposed batches are **not offered in the UI and are refused by the API**. If the UI hides one but the API sells it, that is still **Critical** — record which layer failed. Disposal removes the quantity from sellable stock with **one** ledger row and is audited. Capture the exact `NEAR`/`CRITICAL` day thresholds and the today-expiry verdict — neither is documented anywhere else.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-070 — Refusals never move stock

`PHARMACY_A · PHARMACIST · Inventory · Critical`
**Steps:** `TC-PS-005` (insufficient) · `TC-PS-006` (no batch) · `TC-PS-008` (double-click) · `TC-PS-009` (backend failure) · `TC-PS-010` (two counters, last unit) · `TC-PI-011` (adjustment guards) · `TC-PRF-003` (over-refund) · `TC-PP-010` (double post).
**E2E assertion:** after **every** refusal, the batch quantity and the ledger row count are **unchanged**. Stock may **never go negative**, and it may never be inflated by a repeated inward or refund. This is the single most important invariant in the pharmacy module.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-071 — Stock isolation: tenant and branch

`PHARMACY_A + B (+ MULTI) · PHARMACIST · Isolation · Critical`
**Steps:** `TC-PI-021` · `TC-PE-013` · `TC-PS-018` · `TC-PRF-010` · `TC-PT-023`.
**E2E assertion:** another tenant (or another branch) cannot read the ledger, adjust, block, dispose, sell from, or refund against this batch. After every attempt, **re-read this tenant's quantity and confirm it is unchanged**. A cross-tenant or cross-branch stock mutation is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-072 — The same fixture inside a hospital and a clinic

`HOSPITAL_A + CLINIC_A · PHARMACIST · Inventory · High`
**Steps:** repeat the fixture (steps 0–6) in HOSPITAL_A's pharmacy department and in CLINIC_A.
**E2E assertion:** identical arithmetic and identical reconciliation. All four tenant types share the `/pharmacy/**` ERP namespace, so the same code is under test — confirm the three tenants' stocks remain completely independent (`TC-CP-016`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
