# 00 — PHARMACY MASTER CHECKLIST (completeness proof)

**Baseline:** `origin/staging` @ `aa143a720b9cc7fa6f275792e3acbf1269c0350f` (re-fetched at Tranche 3; **0 files changed**).
**Pharmacy cases:** 152 (`TC-PA` 24 · `TC-PH` 20 · `TC-PT` 30 · `TC-PI` 22 · `TC-PP` 16 · `TC-PS` 20 · `TC-PRF` 16 · `TC-PE` 14 · `TC-PR` 14 · `TC-PX` 10 — minus none; see the table below).

**Status key:** `TESTED` · `SHARED_REFERENCE` · `PARTIAL` · `PLACEHOLDER` · `DEAD_UNREACHABLE` · `NOT_SUPPORTED` · `IMPLEMENTATION_DRIFT` · `NEEDS_PRODUCT_CONFIRMATION`

## Documents

| Doc                                   | Scope                                                           | Cases | Prefix    |
| ------------------------------------- | --------------------------------------------------------------- | ----- | --------- |
| `01-PHARMACY-ADMIN.md`                | tenant admin: shell, staff, billing, analytics, audit, settings | 24    | `TC-PA-`  |
| `02-PHARMACIST.md`                    | pharmacist role, permissions, **clinical-drift battery**        | 20    | `TC-PH-`  |
| `03-PHARMACY-TIERS.md`                | SOLO / SINGLE / MULTI + branches                                | 30    | `TC-PT-`  |
| `04-PHARMACY-INVENTORY.md`            | masters, batches, adjustment, **FEFO**                          | 22    | `TC-PI-`  |
| `05-PHARMACY-PURCHASES-SUPPLIERS.md`  | suppliers, manufacturers, purchase lifecycle                    | 16    | `TC-PP-`  |
| `06-PHARMACY-SALES-BILLING.md`        | billing counter, invoices                                       | 20    | `TC-PS-`  |
| `07-PHARMACY-RETURNS-REFUNDS.md`      | patient refunds, supplier returns                               | 16    | `TC-PRF-` |
| `08-PHARMACY-EXPIRY-BATCHES.md`       | buckets, block, dispose                                         | 14    | `TC-PE-`  |
| `09-PHARMACY-REPORTS-AUDIT.md`        | reconciliation fixture, CSV export, audit                       | 14    | `TC-PR-`  |
| `10-PHARMACY-CROSS-ROLE-WORKFLOWS.md` | journeys P1–P8                                                  | 10    | `TC-PX-`  |
| `11-PHARMACY-REGRESSION-CHECKLIST.md` | P0 (16) / P1 (20) / P2 (26)                                     | —     | —         |

---

## 1. Admin dashboard — tabs per tier (all classified)

| Tab                             | SOLO | SINGLE | MULTI | Status          | Cases                    |
| ------------------------------- | ---- | ------ | ----- | --------------- | ------------------------ |
| Overview                        | ✅   | ✅     | ✅    | `TESTED`        | `TC-PA-004`              |
| **Pharmacists**                 | ❌   | ✅     | ❌    | `TESTED`        | `TC-PA-006`, `TC-PT-012` |
| **Pharmacies** (branches)       | ❌   | ❌     | ✅    | `TESTED`        | `TC-PT-016`…`020`        |
| **Suppliers** (admin-level)     | ❌   | ❌     | ✅    | `TESTED`        | `TC-PT-016`, `TC-PP-001` |
| Billing                         | ✅   | ✅     | ✅    | `TESTED`        | `TC-PA-010`              |
| Analytics                       | ✅   | ✅     | ✅    | `TESTED`        | `TC-PA-011`              |
| Audit Logs                      | ✅   | ✅     | ✅    | `TESTED`        | `TC-PA-012`, `TC-PR-011` |
| Settings                        | ✅   | ✅     | ✅    | `TESTED`        | `TC-PA-013`…`015`        |
| Support                         | ✅   | ✅     | ✅    | `TESTED`        | `TC-PA-017`              |
| _(every hospital clinical tab)_ | —    | —      | —     | `NOT_SUPPORTED` | `TC-PA-003`, `TC-PA-022` |

## 2. Pharmacist dashboard — all 13 tabs

| Tab                 | Status                                                                                     | Cases                    |
| ------------------- | ------------------------------------------------------------------------------------------ | ------------------------ |
| Dashboard           | `TESTED`                                                                                   | `TC-PH-003`              |
| Billing Counter     | `TESTED`                                                                                   | `TC-PS-001`…`012`        |
| Billing (history)   | `TESTED`                                                                                   | `TC-PS-013`…`016`        |
| **Prescriptions**   | `PARTIAL` / `NEEDS_PRODUCT_CONFIRMATION` — no internal prescriber in a standalone pharmacy | `TC-PH-004`              |
| Inventory           | `TESTED`                                                                                   | `TC-PI-001`…`022`        |
| Purchase Management | `TESTED`                                                                                   | `TC-PP-008`…`015`        |
| Suppliers           | `TESTED`                                                                                   | `TC-PP-001`…`005`        |
| Manufacturers       | `TESTED`                                                                                   | `TC-PP-006`, `TC-PI-005` |
| Returns & Refunds   | `TESTED`                                                                                   | `TC-PRF-001`…`013`       |
| Expiry Management   | `TESTED`                                                                                   | `TC-PE-001`…`014`        |
| Reports & Analytics | `TESTED`                                                                                   | `TC-PR-001`…`010`        |
| Audit Logs          | `TESTED` (with a role caveat)                                                              | `TC-PH-013`, `TC-PR-013` |
| Settings            | `TESTED`                                                                                   | `TC-PH-005`, `TC-PA-014` |

## 3. ERP endpoints — every one mapped (56 across 9 controllers)

| Controller                                                                                                                                                                                                          | Endpoints | Status                                                 | Cases                              |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | ------------------------------------------------------ | ---------------------------------- |
| `InventoryController` — list, `/expiring`, `/low-stock`, `/returns-history`, **`/search-batches` (FEFO)**, `/transactions/{batchId}`, `/adjust`, `/batches/{id}/block`, `/batches/{id}/dispose`, `/supplier-return` | 10        | `TESTED`                                               | `TC-PI-*`, `TC-PE-*`, `TC-PRF-011` |
| `MedicineMasterController` — medicines CRUD + status, `/catalog/search`, `/autocomplete/medicines`, `/search/medicines`                                                                                             | 8         | `TESTED`                                               | `TC-PI-001`…`004`                  |
| `ManufacturerController`                                                                                                                                                                                            | 5         | `TESTED`                                               | `TC-PP-006`                        |
| `MedicineCategoryController`                                                                                                                                                                                        | 5         | `TESTED` (UI presence to confirm)                      | `TC-PI-005`                        |
| `SupplierController`                                                                                                                                                                                                | 5         | `TESTED`                                               | `TC-PP-001`…`005`                  |
| `PurchaseController` — list, get, create, **post**                                                                                                                                                                  | 4         | `TESTED` (no edit/cancel → `PARTIAL`)                  | `TC-PP-008`…`015`                  |
| `PharmacySaleController` — create, list, `/search`, get, **`/{id}/pdf`**, `/stats`, `/{id}/return`                                                                                                                  | 7         | `TESTED`                                               | `TC-PS-*`, `TC-PRF-001`            |
| `PharmacyBranchController` — list, create, update, delete, reset-password                                                                                                                                           | 5         | `TESTED` + `NEEDS_PRODUCT_CONFIRMATION` on tier gating | `TC-PT-011`, `017`…`020`           |
| `PharmacyReportsController` — `/dashboard`, **`/export`**                                                                                                                                                           | 2         | `TESTED`                                               | `TC-PR-001`, `TC-PR-008`           |

## 4. Tier coverage

| Tier                             | Landing              | Distinguishing feature                                   | Cases                                |
| -------------------------------- | -------------------- | -------------------------------------------------------- | ------------------------------------ |
| `SINGLE_PHARMACIST_ADMIN` (SOLO) | `/pharmacy/pharmacy` | dual-role switcher; no staff tab                         | `TC-PT-004`…`010`, `TC-PX-008`       |
| `SINGLE_PHARMACY`                | `/pharmacy/admin`    | Pharmacists tab; no switcher                             | `TC-PT-011`…`015`                    |
| `MULTI_PHARMACY`                 | `/pharmacy/admin`    | Pharmacies + Suppliers tabs; branch model; `X-Branch-ID` | `TC-PT-016`…`029`, `TC-PX-006`/`007` |
| Tier change                      | —                    | requires re-login (JWT claim)                            | `TC-PT-030`, `TC-PA-020`             |

## 5. Non-`TESTED` register

| Item                                                                                                                                                        | Status                                                       | Evidence                                                                                             | Case                           |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------- | ------------------------------ |
| `/pharmacy/opd` 11 · `/pharmacy/ipd` 13 · `/pharmacy/beds` 5 · `/pharmacy/wards` 7                                                                          | **`IMPLEMENTATION_DRIFT`**                                   | aliased, **no** `@TenantType`/`@RequireModule`                                                       | `TC-PH-019`, `TC-PX-010`       |
| `/pharmacy/doctors` 11 · `/pharmacy/receptionists` 6                                                                                                        | **`IMPLEMENTATION_DRIFT`**                                   | aliased + `SecurityConfig` admits both roles; `LandingRedirect` sends a DOCTOR to `/hospital/doctor` | `TC-PA-008`, `TC-PH-016`/`017` |
| `/pharmacy/patients` 14 · `/pharmacy/appointments` 11 · `/pharmacy/billing` 7 · `/pharmacy/hospital-inventory` 7 · `/pharmacy/fees` 4 · `/pharmacy/stats` 4 | `IMPLEMENTATION_DRIFT` / gated                               | aliased; four carry a module gate a pharmacy plan cannot satisfy                                     | `TC-PH-019`, `TC-PA-022`       |
| Tenant settings endpoints (`/pharmacy/settings/*`, `/pharmacy/subscription`)                                                                                | `IMPLEMENTATION_DRIFT`                                       | no `@PreAuthorize` on 10 endpoints                                                                   | `TC-PA-015`, `TC-PH-014`       |
| `/pharmacy/branches` on SINGLE / SOLO                                                                                                                       | **`NEEDS_PRODUCT_CONFIRMATION`**                             | `hasRole('HOSPITAL_ADMIN')`, no `@RequireModule`                                                     | `TC-PT-011`                    |
| Prescriptions tab on a standalone pharmacy                                                                                                                  | `NEEDS_PRODUCT_CONFIRMATION`                                 | no internal prescriber                                                                               | `TC-PH-004`                    |
| Pharmacist's Audit Logs tab vs admin-only endpoint                                                                                                          | `NEEDS_PRODUCT_CONFIRMATION` / possible `UI_WITHOUT_BACKEND` | `HospitalAuditController` = `ADM`                                                                    | `TC-PH-013`                    |
| Purchase edit / cancel / delete                                                                                                                             | **`PARTIAL`**                                                | only create + post exist                                                                             | `TC-PP-014`                    |
| Unblock a blocked batch                                                                                                                                     | `PARTIAL` (to confirm)                                       | no obvious unblock endpoint                                                                          | `TC-PE-005`                    |
| Quantity spanning multiple batches in one line                                                                                                              | `NEEDS_PRODUCT_CONFIRMATION`                                 | auto-split not evidenced                                                                             | `TC-PI-014`                    |
| Medicine Categories screen                                                                                                                                  | `NEEDS_PRODUCT_CONFIRMATION` / possible `BACKEND_WITHOUT_UI` | API exists; UI presence unverified                                                                   | `TC-PI-005`                    |
| Duplicate medicine / supplier names                                                                                                                         | `NEEDS_PRODUCT_CONFIRMATION`                                 | no uniqueness evidenced                                                                              | `TC-PI-002`, `TC-PP-002`       |
| Tax / discount / rounding controls                                                                                                                          | record-as-found                                              | not evidenced in the counter code read                                                               | `TC-PS-011`                    |
| Refund into a blocked/disposed batch                                                                                                                        | `NEEDS_PRODUCT_CONFIRMATION`                                 | behaviour unknown                                                                                    | `TC-PRF-008`                   |
| Sale into a deactivated branch                                                                                                                              | `NEEDS_PRODUCT_CONFIRMATION`                                 | behaviour unknown                                                                                    | `TC-PT-026`                    |
| `PHARMACY_ADMIN` / `INVENTORY_MANAGER` in `MedicineMasterController`                                                                                        | `DEAD_UNREACHABLE`                                           | never assignable                                                                                     | `TC-PERM-008`                  |
| NURSE / NURSE_INCHARGE / OT_INCHARGE on `/pharmacy/**`                                                                                                      | `NOT_SUPPORTED` (correctly closed)                           | `SecurityConfig:104-105`                                                                             | `TC-PH-018`                    |

**Unmapped surfaces: 0.**
