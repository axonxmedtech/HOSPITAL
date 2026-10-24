# 02 — PHARMACIST (standalone pharmacy tenant)

**Baseline:** `aa143a7` · **Cases:** `TC-PH-001` … `TC-PH-020` · Tenant PHARMACY_A · `pharm.pharma@qa.test` at `/login/pharmacy` → `/pharmacy/pharmacy`

**13 tabs:** Dashboard · Billing Counter · Billing · Prescriptions · Inventory · Purchase Management · Suppliers · Manufacturers · Returns & Refunds · Expiry Management · Reports & Analytics · Audit Logs · Settings

This document covers the role: access, dashboard, permissions and the negative boundary. The
functional depth lives in `04`–`09`.

---

## A. ACCESS

### TC-PH-001 — Pharmacist login and landing

`PHARMACY_A · PHARMACIST · Auth · Critical · /login/pharmacy`
**Steps:** log in; record the URL and tab set; `GET /auth/me`; watch the Network namespace.
**Expected:** lands **`/pharmacy/pharmacy`** (not `/hospital/pharmacy` — that is the hospital/clinic pharmacy department); `hospitalType: PHARMACY`; ERP calls go to `/pharmacy/...`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-002 — All 13 tabs load without error

`PHARMACY_A · PHARMACIST · UI · High · all tabs`
**Steps:** with DevTools open, click each of the 13 tabs in turn.
**Expected:** none returns **403** or **500**. A visible tab whose API refuses it is `UI_WITHOUT_BACKEND` — record the tab and endpoint.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-003 — Dashboard figures

`PHARMACY_A · PHARMACIST · Dashboard · High · Dashboard`
**Steps:** record today's sales count and value, low stock and near-expiry; make one sale of known value; refresh.
**Expected:** figures move by exactly that sale (`GET /pharmacy/sales/stats`); they match the admin Analytics tab (`TC-PA-011`).
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-004 — Prescriptions tab in a standalone pharmacy

`PHARMACY_A · PHARMACIST · Prescriptions · High · Prescriptions`
**Steps:** open the Prescriptions tab on a tenant that has **no doctors and no OPD**.
**Expected:** an **empty state** — a standalone pharmacy has no internal prescriber, so there is nothing to list. Record what renders. If it errors or shows another tenant's prescriptions, that is a defect (the latter **Critical**). `NEEDS_PRODUCT_CONFIRMATION`: should this tab be hidden for a pharmacy tenant?
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-005 — Pharmacist Settings tab

`PHARMACY_A · PHARMACIST · Settings · Medium · Settings`
**Steps:** open; record every option; change one; F5; verify downstream; restore.
**Expected:** persists and affects the counter (e.g. barcode). Record the exact list — do not assume it matches the hospital pharmacy department's.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## B. CORE OPERATIONS (pointers into the detail documents)

### TC-PH-006 — Inventory

`PHARMACY_A · PHARMACIST · Inventory · High · Inventory`
**Steps:** = `TC-PI-001`…`TC-PI-020` in `04-PHARMACY-INVENTORY.md`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-007 — Purchases and suppliers

`PHARMACY_A · PHARMACIST · Purchase · High · Purchase Management / Suppliers / Manufacturers`
**Steps:** = `05-PHARMACY-PURCHASES-SUPPLIERS.md`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-008 — Sales and billing

`PHARMACY_A · PHARMACIST · Sales · Critical · Billing Counter / Billing`
**Steps:** = `06-PHARMACY-SALES-BILLING.md`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-009 — Returns and refunds

`PHARMACY_A · PHARMACIST · Returns · Critical · Returns & Refunds`
**Steps:** = `07-PHARMACY-RETURNS-REFUNDS.md`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-010 — Expiry and batches

`PHARMACY_A · PHARMACIST · Expiry · Critical · Expiry Management`
**Steps:** = `08-PHARMACY-EXPIRY-BATCHES.md`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-011 — Reports and audit

`PHARMACY_A · PHARMACIST · Reports · High · Reports & Analytics / Audit Logs`
**Steps:** = `09-PHARMACY-REPORTS-AUDIT.md`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## C. PERMISSIONS INSIDE THE TENANT

### TC-PH-012 — Pharmacist cannot manage branches or staff

`PHARMACY_A · PHARMACIST · Authorization · Critical · API`
**Steps:** with the pharmacist token: `GET`/`POST`/`PUT`/`DELETE /pharmacy/branches`; `POST /pharmacy/branches/{id}/reset-password`; `POST /pharmacy/pharmacists`.
**Expected:** **403** on all branch endpoints (`hasRole('HOSPITAL_ADMIN')`); record the pharmacists result.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-013 — Pharmacist cannot read the tenant audit log as admin

`PHARMACY_A · PHARMACIST · Audit · High · API`
**Steps:** `GET /pharmacy/audit-logs` with the pharmacist token, then with the admin token.
**Expected:** **403** then **200** (`HospitalAuditController` = `ADM`). But the pharmacist **has** an Audit Logs tab in the dashboard — if that tab calls a different, permitted endpoint, record which; if it calls this one and 403s, that is `UI_WITHOUT_BACKEND`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-014 — ⚠️ Pharmacist and the tenant settings endpoints

`PHARMACY_A · PHARMACIST · Settings · Critical · API`
**Steps:** = `TC-PA-015` with the pharmacist token.
**Expected (intent):** 403. **Code:** no `@PreAuthorize` → 200 likely → **Critical drift**; verify whether a value actually changes and restore it.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-015 — Branch user is a scoped pharmacist (MULTI)

`PHARM_MULTI · branch user · Authorization · Critical · API`
**Steps:** = `TC-PT-021`, `023`, `024`.
**Expected:** the branch token is scoped by `branchId`; `X-Branch-ID` is **ignored** for a non-admin; cross-branch ids are refused.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## D. UNSUPPORTED ROLES IN A PHARMACY TENANT

### TC-PH-016 — ⚠️ DOCTOR is not a supported pharmacy role

`PHARMACY_A · DOCTOR · Authorization · High · UI/API`
**Steps:** 1. Try to create a doctor (`TC-PA-008`). 2. If created, log in and record the **landing URL**. 3. With that token call `/pharmacy/patients`, `/pharmacy/appointments`, `/pharmacy/opd`, `/pharmacy/sales`.
**Expected (policy):** the role should not exist here. **Code:** `SecurityConfig:104-105` admits `DOCTOR` to `/pharmacy/**`, and `LandingRedirect` sends any DOCTOR to **`/hospital/doctor`** — so a pharmacy doctor would land on a _hospital_ URL showing clinical tabs backed by a pharmacy tenant. Record exactly what renders. **`IMPLEMENTATION_DRIFT`, HIGH.**
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-017 — ⚠️ RECEPTIONIST is not a supported pharmacy role

`PHARMACY_A · RECEPTIONIST · Authorization · High · UI/API`
**Steps:** as above with a receptionist; record the landing URL (`/hospital/receptionist` expected) and what the dashboard shows.
**Expected (policy):** not supported. Record actual as `IMPLEMENTATION_DRIFT`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-018 — NURSE / NURSE_INCHARGE / OT_INCHARGE are properly closed

`HOSPITAL_A → PHARMACY · NURSE/NI/OTI · Authorization · High · API`
**Steps:** 1. `POST /pharmacy/nurses`, `/pharmacy/ot-incharges` with the pharmacy admin token. 2. With a **hospital** nurse, nurse-incharge and OT-incharge token call `/pharmacy/patients`, `/pharmacy/sales`, `/pharmacy/inventory`.
**Expected:** step 1 → **404** (never aliased). Step 2 → **403** ×9 (`SecurityConfig:104-105` omits these roles). This is the correctly closed boundary; contrast with `TC-PH-016`/`017`.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

---

## E. ⚠️ CLINICAL-DOMAIN DRIFT — the pharmacy alias battery

### TC-PH-019 — `/pharmacy/opd`, `/ipd`, `/beds`, `/wards` — full method matrix

`PHARMACY_A · HOSPITAL_ADMIN + PHARMACIST · Authorization · Critical · API`

> **Product expectation: a standalone PHARMACY cannot operate these clinical domains.**
> **Code:** `/pharmacy/opd` 11, `/pharmacy/ipd` 13, `/pharmacy/beds` 5, `/pharmacy/wards` 7 endpoints — **none** carrying `@TenantType` or `@RequireModule`.

Run each row with the **admin** token, then the **pharmacist** token:

| Domain       | Endpoint                                      | Method     | Expected | Actual (ADM) | Actual (PHA) | Data?     | Row created? |
| ------------ | --------------------------------------------- | ---------- | -------- | ------------ | ------------ | --------- | ------------ |
| OPD          | `/pharmacy/opd`                               | GET        | 403      | ____         | ____         | ____      | —            |
| OPD          | `/pharmacy/opd`                               | POST       | 403      | ____         | ____         | —         | ____         |
| OPD          | `/pharmacy/opd/{HOSP_A id}`                   | GET        | 403/404  | ____         | ____         | ____      | —            |
| OPD          | `/pharmacy/opd/{id}/pdf`                      | GET        | 403/404  | ____         | ____         | **file?** | —            |
| IPD          | `/pharmacy/ipd`                               | GET        | 403      | ____         | ____         | ____      | —            |
| IPD          | `/pharmacy/ipd/admit`                         | POST       | 403      | ____         | ____         | —         | ____         |
| IPD          | `/pharmacy/ipd/{HOSP_A id}/confirm-discharge` | POST       | 403      | ____         | ____         | —         | ____         |
| Wards        | `/pharmacy/wards`                             | GET / POST | 403      | ____         | ____         | ____      | ____         |
| Beds         | `/pharmacy/beds`                              | GET / POST | 403      | ____         | ____         | ____      | ____         |
| Patients     | `/pharmacy/patients`                          | GET / POST | 403      | ____         | ____         | ____      | ____         |
| Appointments | `/pharmacy/appointments`                      | GET        | 403      | ____         | ____         | ____      | —            |

**Expected:** every row 403. **Any 200 → `IMPLEMENTATION_DRIFT`, HIGH.** **Any 200 carrying another tenant's data, or any created row → Critical.** For the PDF row, if bytes are returned, **open the file** — a pharmacy downloading a hospital's case paper is the worst outcome. Record row ids; do not clean up.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-PH-020 — Never-aliased clinical namespaces are closed

`PHARMACY_A · HOSPITAL_ADMIN · Authorization · High · API`
**Steps:** `/pharmacy/icu`, `/pharmacy/surgeries`, `/pharmacy/nurse/vitals`, `/pharmacy/nurses`, `/pharmacy/time-slots`, `/pharmacy/notifications`, `/pharmacy/form-access`, `/pharmacy/vitals`; then the `/hospital/...` forms of each.
**Expected:** `/pharmacy/...` → **404** (no mapping); `/hospital/...` → **403** (module and/or tenant gate). Also `GET /hospital/dashboard` → **403** (`@TenantType(HOSPITAL)`). Record which code each returns — 404 vs 403 tells you _why_ it is closed.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
