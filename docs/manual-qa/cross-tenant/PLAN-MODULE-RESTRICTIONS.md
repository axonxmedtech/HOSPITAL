# PLAN & MODULE RESTRICTIONS

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** 31 (`TC-MOD-001` … `TC-MOD-031`)

## The entitlement rule you must apply

> **`EntitlementRegistry` is declaration-only.** Its own javadoc says: _"This class only declares.
> It enforces nothing."_ Presence or absence of a module in the registry is **not** proof that
> access is enforced. Enforcement lives in three separate places that have drifted from it:
>
> | Gate                                               | What it checks                                        | Coverage                                      |
> | -------------------------------------------------- | ----------------------------------------------------- | --------------------------------------------- |
> | `@RequireModule("X")`                              | the **live hospital row's** module list (not the JWT) | **47** handlers                               |
> | `@TenantType(HOSPITAL)`                            | the JWT's `hospitalType` claim                        | **76** handlers — **OT, ICU, dashboard only** |
> | URL aliasing (`/hospital`, `/clinic`, `/pharmacy`) | which namespace a controller is mounted on            | all                                           |
>
> **For every module case, verify BOTH the UI (tab hidden/shown) AND the backend (endpoint
> allows/denies).** Neither alone is a pass.

### What each tenant type may be sold (`EntitlementRegistry:113-121`)

| Module             | HOSPITAL | CLINIC | PHARMACY | Backend gate present?                      |
| ------------------ | -------- | ------ | -------- | ------------------------------------------ |
| OPD                | ✅       | ✅     | ❌       | ❌ **no `@RequireModule("OPD")` anywhere** |
| IPD                | ✅       | ❌     | ❌       | ⚠️ **1** handler only                      |
| APPOINTMENTS       | ✅       | ✅     | ❌       | ✅ 5                                       |
| BILLING            | ✅       | ✅     | ❌       | ✅ 3                                       |
| PHARMACY           | ✅       | ✅     | ✅       | ❌ none                                    |
| MEDICAL_INVENTORY  | ✅       | ✅     | ❌       | ✅ 6                                       |
| HOSPITAL_INVENTORY | ✅       | ❌     | ❌       | ✅ 1                                       |
| REPORTS            | ✅       | ✅     | ❌       | ✅ 1                                       |
| OT                 | ✅       | ❌     | ❌       | ✅ 10 + `@TenantType`                      |
| NURSING            | ✅       | ❌     | ❌       | ✅ 11                                      |
| ICU                | ✅       | ❌     | ❌       | ✅ 9 + `@TenantType`                       |
| Pharmacy tiers     | ❌       | ❌     | ✅       | —                                          |

The right-hand column is the finding: **OPD and PHARMACY have no backend module gate at all**,
and **IPD has one**. "Turning off OPD" in a plan therefore may hide a tab and change nothing on the
server. Cases `TC-MOD-010`…`013` prove or disprove this.

Implied (never sold): `CORE` · `WARDS`, `BEDS`, `CLINICAL_RECORDS` ← IPD · `PHARMACY_BRANCH` ← MULTI_PHARMACY.

---

## A. Plan catalogue integrity (Super Admin)

### TC-MOD-001 — Sellable modules per type match the registry

| Tenant                                                                                                                             | Role        | Module | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------- | ----------- | ------ | -------- |
| PLATFORM                                                                                                                           | SUPER_ADMIN | Plans  | **High** |
| **Steps** Open Create Plan under Hospital, Clinic and Pharmacy in turn; write down every checkbox.                                 |
| **Expected** — exactly the ✅ columns in the table above, per type. Internal keys never offered.                                   |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-002 — Server rejects an illegal module for a type (API)

| Tenant                                                                                                                                                                            | Role        | Module | Priority | Endpoint          | Method | Auth       | Expected status |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ------ | -------- | ----------------- | ------ | ---------- | --------------- |
| PLATFORM                                                                                                                                                                          | SUPER_ADMIN | Plans  | **High** | `/platform/plans` | POST   | `TOKEN_SA` | **400**         |
| **Steps** Capture a valid clinic-plan POST, add `"IPD"` (and separately `"OT"`, `"NURSING"`, `"ICU"`) to `modules`, send.                                                         |
| **Expected** — **400** each time (`validatePlanModules`). This is the one place the registry _is_ enforced. A 201 means an unsupported module can be sold to a clinic — **High**. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                |

### TC-MOD-003 — Internal keys cannot be sold directly

| Tenant                                                                                                                             | Role        | Module | Priority   | Endpoint          | Method | Auth       | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------- | ----------- | ------ | ---------- | ----------------- | ------ | ---------- | --------------- |
| PLATFORM                                                                                                                           | SUPER_ADMIN | Plans  | **Medium** | `/platform/plans` | POST   | `TOKEN_SA` | **400**         |
| **Steps** POST a hospital plan whose `modules` contains `"WARDS"`, then `"CORE"`, then `"PHARMACY_BRANCH"`.                        |
| **Expected** — rejected; these are implied, never sold.                                                                            |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-004 — IPD implies Wards, Beds and Clinical Records

| Tenant                                                                                                                                          | Role           | Module | Priority |
| ----------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | -------- |
| HOSPITAL_A                                                                                                                                      | HOSPITAL_ADMIN | IPD    | **High** |
| **Steps** With a plan that has IPD but where WARDS/BEDS were never ticked (they cannot be), open Wards & Beds and an admitted patient's Vitals. |
| **Expected** — both work. Wards, beds and clinical records arrive with IPD automatically.                                                       |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______              |

### TC-MOD-005 — APPOINTMENTS implies OPD

| Tenant                                                                                                                             | Role           | Module       | Priority   |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------------ | ---------- |
| HOSPITAL_M                                                                                                                         | HOSPITAL_ADMIN | Appointments | **Medium** |
| **Steps** Create a plan with **APPOINTMENTS only** (no OPD ticked). Assign it to Hospital M. Log in.                               |
| **Expected** — the OPD tab is present and functional (`IMPLIED_BY: APPOINTMENTS → OPD`).                                           |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-006 — ICU requires IPD

| Tenant                                                                                                                                                                                                                        | Role        | Module | Priority |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ------ | -------- |
| PLATFORM → HOSPITAL_M                                                                                                                                                                                                         | SUPER_ADMIN | ICU    | **High** |
| **Steps** Try to create a hospital plan with **ICU but not IPD**. If the UI allows it, assign it to Hospital M and open ICU Dashboard.                                                                                        |
| **Expected** — either the plan is rejected, or the ICU screen has nothing to show and its API returns a clean empty state (not 500). Record which. Mark `NEEDS_PRODUCT_CONFIRMATION` if ICU-without-IPD is accepted silently. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                            |

### TC-MOD-007 — Clinic plan never offers IPD

| Tenant                                                                                                                             | Role        | Module | Priority     |
| ---------------------------------------------------------------------------------------------------------------------------------- | ----------- | ------ | ------------ |
| PLATFORM                                                                                                                           | SUPER_ADMIN | Plans  | **Critical** |
| **Steps** Duplicate of the check in `TC-SA-009`; record it here for the module matrix.                                             |
| **Expected** — IPD is not a clinic option in the UI **and** `TC-MOD-002` proves the API rejects it.                                |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

---

## B. Tab visibility per plan (UI half)

### TC-MOD-008 — Full hospital plan shows every module tab

| Tenant                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Role           | Module | Priority |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | -------- |
| HOSPITAL_A                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | HOSPITAL_ADMIN | All    | **High** |
| **Steps** Log in; list every sidebar tab.                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **Expected** — Overview, Patients, Appointments, OPD, Follow-ups, IPD, Wards & Beds, ICU Dashboard, ICU Bed Board, OT, Pharmacy, Pharmacists, Billing, Fees, Doctors, Receptionists, OT Incharge, OT Theatres, OT Analytics, Nurses, Nurse Assignments, Nurse Tasks, Time Slots, Calendar, Reports & Analytics, Audit Logs, Settings, Support, presets. **Pathology** appears only if the plan somehow includes `PATHOLOGY` — it cannot, so it should be absent (see `TC-MOD-030`). |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                                                                                                                                                                                                                                  |

### TC-MOD-009 — OPD-only plan hides every other module tab

| Tenant                                                                                                                                                                                                                                                                                             | Role                                 | Module | Priority     |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ | ------ | ------------ |
| HOSPITAL_M                                                                                                                                                                                                                                                                                         | HOSPITAL_ADMIN, RECEPTIONIST, DOCTOR | All    | **Critical** |
| **Steps** Log in as each role of Hospital M (create a receptionist and doctor in it first). List tabs.                                                                                                                                                                                             |
| **Expected** — **present:** Overview, Patients, OPD, Doctors, Receptionists, Settings, Support, Audit Logs (CORE). **Absent:** IPD, Wards & Beds, ICU, OT, Pharmacy, Billing, Fees, Nurses, Time Slots, Calendar, Reports, Appointments, Follow-ups. Record any tab that appears despite the plan. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                                                 |

---

## C. Backend enforcement per module (API half) — run each with a HOSPITAL_M token while on the OPD-only plan

### TC-MOD-010 — ⚠️ OPD has no backend module gate

| Tenant                                                                                                                                                                                                                                                   | Role           | Module | Priority | Endpoint        | Method    | Auth       | Expected status |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | -------- | --------------- | --------- | ---------- | --------------- |
| HOSPITAL_M                                                                                                                                                                                                                                               | HOSPITAL_ADMIN | OPD    | **High** | `/hospital/opd` | GET, POST | Hospital M | record          |
| **Steps** 1. Assign a plan with **no OPD and no APPOINTMENTS** to Hospital M (e.g. BILLING only). 2. Confirm the OPD tab is hidden. 3. `GET /hospital/opd` and `POST /hospital/opd` (valid body) with M's token.                                         |
| **Expected (product intent)** — 403. **Code position:** there is **no `@RequireModule("OPD")`** anywhere, so 200 is likely → `IMPLEMENTATION_DRIFT`, **High**. If the POST **creates** an OPD case in a tenant that did not buy OPD, raise **Critical**. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                       |

### TC-MOD-011 — ⚠️ IPD backend gate coverage

| Tenant                                                                                                                                                                                                                             | Role                         | Module | Priority     | Endpoint                                             | Method    | Auth                  | Expected status  |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- | ------ | ------------ | ---------------------------------------------------- | --------- | --------------------- | ---------------- |
| HOSPITAL_M                                                                                                                                                                                                                         | HOSPITAL_ADMIN, RECEPTIONIST | IPD    | **Critical** | `/hospital/ipd`, `/hospital/wards`, `/hospital/beds` | GET, POST | Hospital M (OPD-only) | **403** expected |
| **Steps** With M on OPD-only: GET and POST each of the three; then `POST /hospital/ipd` with a valid admission body.                                                                                                               |
| **Expected (product intent)** — 403 on all. **Code position:** only **one** handler carries `@RequireModule("IPD")`; wards and beds carry none. Record each endpoint's status individually — a partial gate is the likely finding. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                 |

### TC-MOD-012 — ⚠️ PHARMACY has no backend module gate

| Tenant                                                                                                                                                                       | Role           | Module   | Priority | Endpoint                                    | Method    | Auth       | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | -------- | -------- | ------------------------------------------- | --------- | ---------- | --------------- |
| HOSPITAL_M                                                                                                                                                                   | HOSPITAL_ADMIN | Pharmacy | **High** | `/hospital/pharmacy`, `/hospital/medicines` | GET, POST | Hospital M | record          |
| **Steps** With M on OPD-only, call both. Note `/hospital/medicines` **is** gated by `MEDICAL_INVENTORY`, so compare the two results.                                         |
| **Expected (product intent)** — 403 on both. Record the split: `/hospital/medicines` likely 403 (gated), `/hospital/pharmacy` likely 200 (ungated) → `IMPLEMENTATION_DRIFT`. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                           |

### TC-MOD-013 — APPOINTMENTS gate (positive control)

| Tenant                                                                                                                                                | Role           | Module       | Priority | Endpoint                 | Method    | Auth                  | Expected status |
| ----------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------------ | -------- | ------------------------ | --------- | --------------------- | --------------- |
| HOSPITAL_M                                                                                                                                            | HOSPITAL_ADMIN | Appointments | **High** | `/hospital/appointments` | GET, POST | Hospital M (OPD-only) | **403**         |
| **Steps** Call both with M's token while the plan lacks APPOINTMENTS.                                                                                 |
| **Expected** — **403**. This module **is** gated (5 handlers); it is your reference for what a working gate looks like. Compare against `TC-MOD-010`. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                    |

### TC-MOD-014 — BILLING gate

| Tenant                                                                                                                                                                                               | Role           | Module  | Priority | Endpoint                                              | Method    | Auth       | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------- | -------- | ----------------------------------------------------- | --------- | ---------- | --------------- |
| HOSPITAL_M                                                                                                                                                                                           | HOSPITAL_ADMIN | Billing | **High** | `/hospital/billing`, `/hospital/settings/fees/custom` | GET, POST | Hospital M | **403**         |
| **Steps** Call both without BILLING on the plan.                                                                                                                                                     |
| **Expected** — 403 (3 handlers gated). Also check `/hospital/settings/fees` (on `HospitalAuthController`) — record whether **it** is gated; the base fees endpoint may be reachable without BILLING. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                   |

### TC-MOD-015 — NURSING gate

| Tenant                                                                                                                             | Role           | Module  | Priority | Endpoint                                                                                        | Method | Auth       | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------- | -------- | ----------------------------------------------------------------------------------------------- | ------ | ---------- | --------------- |
| HOSPITAL_M                                                                                                                         | HOSPITAL_ADMIN | Nursing | **High** | `/hospital/nurses`, `/hospital/nurse-assignments`, `/hospital/time-slots`, `/hospital/calendar` | GET    | Hospital M | **403**         |
| **Steps** Call each without NURSING.                                                                                               |
| **Expected** — 403 on all (11 handlers gated). Also confirm the admin cannot **create** a nurse — `POST /hospital/nurses` → 403.   |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-016 — OT gate

| Tenant                                                                                                                             | Role           | Module | Priority | Endpoint                                                                | Method | Auth       | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | -------- | ----------------------------------------------------------------------- | ------ | ---------- | --------------- |
| HOSPITAL_M                                                                                                                         | HOSPITAL_ADMIN | OT     | **High** | `/hospital/surgeries`, `/hospital/ot/rooms`, `/hospital/ot/permissions` | GET    | Hospital M | **403**         |
| **Steps** Call each without OT.                                                                                                    |
| **Expected** — 403 (10 handlers + `@TenantType`).                                                                                  |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-017 — ICU gate

| Tenant                                                                                                                             | Role           | Module | Priority | Endpoint                                          | Method | Auth       | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | -------- | ------------------------------------------------- | ------ | ---------- | --------------- |
| HOSPITAL_M                                                                                                                         | HOSPITAL_ADMIN | ICU    | **High** | `/hospital/icu`, `/hospital/icu/alert-thresholds` | GET    | Hospital M | **403**         |
| **Steps** Call each without ICU.                                                                                                   |
| **Expected** — 403 (9 handlers).                                                                                                   |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-018 — MEDICAL_INVENTORY and HOSPITAL_INVENTORY gates

| Tenant                                                                                                                             | Role           | Module    | Priority | Endpoint                                              | Method    | Auth       | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | --------- | -------- | ----------------------------------------------------- | --------- | ---------- | --------------- |
| HOSPITAL_M                                                                                                                         | HOSPITAL_ADMIN | Inventory | **High** | `/hospital/medicines`, `/hospital/hospital-inventory` | GET, POST | Hospital M | **403**         |
| **Steps** Call each without the respective module.                                                                                 |
| **Expected** — 403 on both.                                                                                                        |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-019 — REPORTS gate

| Tenant                                                                                                                                 | Role           | Module  | Priority   | Endpoint          | Method | Auth       | Expected status |
| -------------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------- | ---------- | ----------------- | ------ | ---------- | --------------- |
| HOSPITAL_M                                                                                                                             | HOSPITAL_ADMIN | Reports | **Medium** | `/hospital/stats` | GET    | Hospital M | **403**         |
| **Steps** Call without REPORTS; also open Reports & Analytics in the UI.                                                               |
| **Expected** — 403 and no tab. Note the Overview dashboard (`/hospital/dashboard`) is **not** gated by REPORTS — it should still load. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______     |

---

## D. Live revocation and restoration

### TC-MOD-020 — ⭐ Revoke a gated module mid-session (flagship)

| Tenant                                                                                                                             | Role                         | Module      | Priority     |
| ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- | ----------- | ------------ |
| PLATFORM → HOSPITAL_M                                                                                                              | SUPER_ADMIN → HOSPITAL_ADMIN | Entitlement | **Critical** |
| **Steps** Execute `TC-SA-041` exactly and record the verdict here.                                                                 |
| **Expected** — tabs vanish on refresh; the still-valid token gets **403**; data survives; restoration returns everything.          |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-021 — Revocation takes effect without re-login

| Tenant                                                                                                                                                                                                                                                                                               | Role         | Module      | Priority     |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | ----------- | ------------ |
| HOSPITAL_M                                                                                                                                                                                                                                                                                           | RECEPTIONIST | Entitlement | **Critical** |
| **Steps** 1. Receptionist logged in with APPOINTMENTS on the plan; capture `GET /hospital/appointments`. 2. Super Admin removes APPOINTMENTS. 3. **Without refresh or re-login**, click the Appointments tab.                                                                                        |
| **Expected** — the API call returns **403** on the very next request. `ModuleAccessAspect` reads the live hospital row, not the JWT. The tab itself may remain visible until refresh (it is rendered from the JWT's `modules`) — record that as expected, not a bug, but the **data must not load**. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                                                                   |

### TC-MOD-022 — Revoked-module data is preserved, not deleted

| Tenant                                                                                                                             | Role           | Module                       | Priority     |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ---------------------------- | ------------ |
| HOSPITAL_M                                                                                                                         | HOSPITAL_ADMIN | Entitlement / Data integrity | **Critical** |
| **Steps** Execute `TC-SA-043` and record here.                                                                                     |
| **Expected** — database row counts unchanged across revoke/restore.                                                                |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-023 — In-flight workflow when a module is revoked

| Tenant                                                                                                                             | Role         | Module           | Priority   |
| ---------------------------------------------------------------------------------------------------------------------------------- | ------------ | ---------------- | ---------- |
| HOSPITAL_M                                                                                                                         | RECEPTIONIST | Entitlement / UX | **Medium** |
| **Steps** 1. Open Add Appointment and half-fill it. 2. Super Admin revokes APPOINTMENTS. 3. Click Save.                            |
| **Expected** — a readable error (403 → a toast), **not** a blank screen or a React crash; no partial record.                       |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

---

## E. Tenant-type drift (product decisions — negative tests only)

### TC-MOD-024 — Clinic IPD namespace

| Tenant                                                                                                                             | Role           | Module | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------ | -------- |
| CLINIC_A                                                                                                                           | HOSPITAL_ADMIN | IPD    | **High** |
| **Steps** Execute `TC-API-009`; record here.                                                                                       |
| **Expected (intent)** 403. **Code position:** 13 ungated endpoints — `IMPLEMENTATION_DRIFT` likely.                                |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-025 — Pharmacy OPD/IPD/Beds/Wards namespaces

| Tenant                                                                                                                             | Role           | Module             | Priority |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------- | ------------------ | -------- |
| PHARMACY_A                                                                                                                         | HOSPITAL_ADMIN | OPD/IPD/Wards/Beds | **High** |
| **Steps** Execute `TC-API-010`; record here.                                                                                       |
| **Expected (intent)** 403 ×4. **Code position:** 36 ungated endpoints across the four.                                             |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______ |

### TC-MOD-026 — Clinic cannot reach nursing endpoints even if a module were granted

| Tenant                                                                                                                                                                                           | Role           | Module  | Priority | Endpoint           | Method | Auth         | Expected status |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | ------- | -------- | ------------------ | ------ | ------------ | --------------- |
| CLINIC_A                                                                                                                                                                                         | HOSPITAL_ADMIN | Nursing | **High** | `/hospital/nurses` | GET    | clinic admin | **403**         |
| **Steps** Call with the clinic admin token. (`/hospital/**` admits HOSPITAL_ADMIN regardless of tenant type — the only gate is `@RequireModule("NURSING")`, which a clinic plan cannot contain.) |
| **Expected** — 403 because the module is absent. **Then** ask Super Admin to _try_ adding NURSING to the clinic plan via API (`TC-MOD-002`) — it must be rejected, closing the loop.             |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                               |

### TC-MOD-027 — Clinic cannot reach OT / ICU (double gate)

| Tenant                                                                                                                                                                                               | Role           | Module   | Priority | Endpoint                               | Method | Auth         | Expected status |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | -------- | -------- | -------------------------------------- | ------ | ------------ | --------------- |
| CLINIC_A                                                                                                                                                                                             | HOSPITAL_ADMIN | OT / ICU | **High** | `/hospital/surgeries`, `/hospital/icu` | GET    | clinic admin | **403**         |
| **Steps** Call both.                                                                                                                                                                                 |
| **Expected** — 403 from `@TenantType(HOSPITAL)` **and** `@RequireModule`. This is the best-protected surface in the product; use it as the reference for what `TC-MOD-024`/`025` _should_ look like. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                   |

---

## F. Pharmacy tiers

### TC-MOD-028 — SINGLE_PHARMACY tier: single branch, no branch UI

| Tenant                                                                                                                                                                                                                                            | Role           | Module         | Priority |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | -------------- | -------- |
| PHARMACY_A                                                                                                                                                                                                                                        | HOSPITAL_ADMIN | Pharmacy tiers | **High** |
| **Steps** Log in; look for a **Pharmacies / Branches** tab; call `GET /pharmacy/branches`.                                                                                                                                                        |
| **Expected** — no branch management tab. Record the API result: `PharmacyBranchController` is admin-only but has no `@RequireModule` for `PHARMACY_BRANCH` — it may return 200 with one implicit branch. Mark `NEEDS_PRODUCT_CONFIRMATION` if so. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                |

### TC-MOD-029 — MULTI_PHARMACY tier: branches available

| Tenant                                                                                                                                                                                                                           | Role           | Module         | Priority |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | -------------- | -------- |
| PHARMACY (multi)                                                                                                                                                                                                                 | HOSPITAL_ADMIN | Pharmacy tiers | **High** |
| **Steps** Create `QA Pharmacy Multi` on `QA-PHARM-MULTI`; log in; open **Pharmacies**; create a second branch with its own login; log in as that branch user.                                                                    |
| **Expected** — branch CRUD works; branch user sees only its branch's sales and stock (`branch_id` scoping). Downgrade the tenant to SINGLE_PHARMACY and confirm the branch tab disappears **and existing branch data survives**. |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                               |

---

## G. Placeholder and dead gates

### TC-MOD-030 — Pathology is a placeholder and cannot be enabled

| Tenant                                                                                                                                                                                                                                                   | Role           | Module    | Priority |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | --------- | -------- |
| HOSPITAL_A                                                                                                                                                                                                                                               | HOSPITAL_ADMIN | Pathology | **Low**  |
| **Context:** `HospitalAdminDashboard.jsx:2200` gates the tab on `requiredModule: 'PATHOLOGY'`, a key absent from `ALL_MODULES` and every sellable set. If it ever rendered it would show _"Pathology Module is currently under development."_ (`:4933`). |
| **Steps** 1. Confirm no plan can include PATHOLOGY (`TC-MOD-001`). 2. Confirm the tab is absent for Hospital A. 3. If it is somehow present, click it and record the placeholder text.                                                                   |
| **Expected** — absent. **Do not create functional Pathology test cases** — there is nothing to test. `PLACEHOLDER`.                                                                                                                                      |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                       |

### TC-MOD-031 — Module claim in the JWT vs the live row (the two sources)

| Tenant                                                                                                                                                                                                                                                 | Role           | Module      | Priority   |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | ----------- | ---------- |
| HOSPITAL_M                                                                                                                                                                                                                                             | HOSPITAL_ADMIN | Entitlement | **Medium** |
| **Why:** the **UI** renders tabs from `user.modules` (a JWT claim frozen at login); the **backend** checks the hospital row live. They can disagree for the length of a session.                                                                       |
| **Steps** 1. Log in with the full plan; note the tabs. 2. Super Admin downgrades to OPD-only. 3. **Do not refresh.** Click IPD. 4. Refresh. 5. Log out and in; compare.                                                                                |
| **Expected** — step 3: tab visible, API **403**, readable error. Step 4: tab still visible (JWT unchanged) or hidden — record which. Step 5: tab hidden. Document the exact observed sequence; it is the reference for "why does the tab still show?". |
| **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______                                                                                                                     |
