# HMS — MASTER MANUAL QA INDEX

|                            |                                                                                                                                |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| **Product**                | AXONX HMS — multi-tenant SaaS Hospital Management System                                                                       |
| **Documentation baseline** | `origin/staging` @ **`aa143a720b9cc7fa6f275792e3acbf1269c0350f`**                                                              |
| **Baseline date**          | 2026-09-11                                                                                                                     |
| **Tranche**                | **1 + 2 + 3 + 4 — complete** — Foundation & Matrices (T1) · Hospital (T2) · Clinic & Pharmacy (T3) · End-to-end & Release (T4) |
| **Status**                 | Ready for execution                                                                                                            |

> **Baseline note.** Tranche 1 was authored against `aa143a7`, which is the merge of PR #30
> (Checkpoint 2F Phase A — patient duplicate-phone confirmation). `git diff 9319dd1..aa143a7`
> is **empty**: `aa143a7` is the merge commit only, so every figure below was re-verified against
> `aa143a7` and none was inherited from the earlier inspection without re-counting.
>
> **One Phase 1 figure was wrong and is corrected here:** the controller count is **84**, not 82
> (65 hospital, 9 pharmacy, 9 platform, 1 public).

---

## 1. READ THIS FIRST — how this product is built

**HMS is not route-navigable.** The whole application has only **18 React Router routes**, of
which 16 are real destinations. Every other screen — all ~90 of them — is an `activeTab` value
inside one of eight large dashboard components.

Three consequences you must internalise before testing:

1. **You cannot deep-link to most screens.** There is no `/hospital/admin/patients` URL. A tester
   reaches a screen by logging in and clicking a sidebar tab.
2. **"Direct URL access denied" cannot be tested from the address bar** for tab-level screens,
   because those screens have no URL. Authorization must be proven at the **API layer**. This is
   why [`05-API-TEST-TRACK.md`](05-API-TEST-TRACK.md) is mandatory and not optional.
3. **Browser refresh returns you to the dashboard's default tab.** Tab state lives in React state,
   not the URL. **This is expected behaviour, not a bug.** Do not raise it once per screen.

The single exception is **`/ipd/:id`**, the only deep-linkable record screen in the product and
therefore the only place a true UI-level ID-tampering test can be run from the browser.

---

## 2. Tranche 1 documents

| #   | Document                                                                               | Purpose                                                    | Cases |
| --- | -------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ----- |
| 00  | `00-MASTER-QA-INDEX.md`                                                                | This file. Start here.                                     | —     |
| 01  | [`01-TEST-ENVIRONMENT-SETUP.md`](01-TEST-ENVIRONMENT-SETUP.md)                         | Get a working test environment                             | —     |
| 02  | [`02-TEST-DATA-SETUP.md`](02-TEST-DATA-SETUP.md)                                       | Build the synthetic dataset every case depends on          | —     |
| 03  | [`03-GLOBAL-AUTH-AND-SECURITY.md`](03-GLOBAL-AUTH-AND-SECURITY.md)                     | Login, logout, session, JWT, tab isolation                 | 28    |
| 04  | [`04-SUPER-ADMIN.md`](04-SUPER-ADMIN.md)                                               | Every Platform Dashboard screen + downstream tenant effect | 62    |
| 05  | [`05-API-TEST-TRACK.md`](05-API-TEST-TRACK.md)                                         | How a junior tester tests the API                          | 12    |
| 06  | [`06-BUG-REPORT-TEMPLATE.md`](06-BUG-REPORT-TEMPLATE.md)                               | Bug template + severity rules                              | —     |
| 07  | [`07-EXECUTION-TRACKER.md`](07-EXECUTION-TRACKER.md)                                   | Master tracker — every case listed                         | —     |
| —   | [`cross-tenant/TENANT-ISOLATION.md`](cross-tenant/TENANT-ISOLATION.md)                 | Tenant A must never reach Tenant B                         | 58    |
| —   | [`cross-tenant/ROLE-PERMISSION-MATRIX.md`](cross-tenant/ROLE-PERMISSION-MATRIX.md)     | Full authorization pass, UI **and** API                    | 34    |
| —   | [`cross-tenant/PLAN-MODULE-RESTRICTIONS.md`](cross-tenant/PLAN-MODULE-RESTRICTIONS.md) | Module gating, revocation, drift                           | 31    |
| —   | [`cross-tenant/DATA-VISIBILITY.md`](cross-tenant/DATA-VISIBILITY.md)                   | Who sees whose data inside one tenant                      | 22    |
| —   | [`cross-tenant/SPECIAL-MODES.md`](cross-tenant/SPECIAL-MODES.md)                       | `isSingleDoctor`, `SINGLE_PHARMACIST_ADMIN`                | 18    |
| —   | [`status/IMPLEMENTATION-STATUS.md`](status/IMPLEMENTATION-STATUS.md)                   | Is it a bug, or is it just not built?                      | —     |

### Tranche 2 — Hospital tenant (`hospital/`)

| Document                                                                                       | Purpose                                                                   | Cases |
| ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- | ----- |
| [`hospital/00-HOSPITAL-MASTER-CHECKLIST.md`](hospital/00-HOSPITAL-MASTER-CHECKLIST.md)         | **Completeness proof** — every tab/modal/PDF mapped to cases and a status | —     |
| [`hospital/01-HOSPITAL-ADMIN-SETUP.md`](hospital/01-HOSPITAL-ADMIN-SETUP.md)                   | staff, master data, presets, inventory, audit                             | 46    |
| [`hospital/01b-HOSPITAL-ADMIN-CLINICAL.md`](hospital/01b-HOSPITAL-ADMIN-CLINICAL.md)           | admin clinical tabs + reports                                             | 26    |
| [`hospital/01c-HOSPITAL-ADMIN-SETTINGS.md`](hospital/01c-HOSPITAL-ADMIN-SETTINGS.md)           | all 11 settings cards, each with downstream effect                        | 44    |
| [`hospital/02-HOSPITAL-RECEPTIONIST.md`](hospital/02-HOSPITAL-RECEPTIONIST.md)                 | reception end-to-end                                                      | 58    |
| [`hospital/03-HOSPITAL-DOCTOR.md`](hospital/03-HOSPITAL-DOCTOR.md)                             | consultation, prescriptions, IPD                                          | 48    |
| [`hospital/04-HOSPITAL-NURSE.md`](hospital/04-HOSPITAL-NURSE.md)                               | staff nurse + 12 clinical panels                                          | 40    |
| [`hospital/05-HOSPITAL-NURSE-INCHARGE.md`](hospital/05-HOSPITAL-NURSE-INCHARGE.md)             | ward scope, schedule, attendance, coverage, beds                          | 32    |
| [`hospital/06-HOSPITAL-PHARMACY-DEPT.md`](hospital/06-HOSPITAL-PHARMACY-DEPT.md)               | hospital pharmacy module                                                  | 40    |
| [`hospital/07-HOSPITAL-BILLING.md`](hospital/07-HOSPITAL-BILLING.md)                           | OPD/IPD/pharmacy billing lifecycle                                        | 30    |
| [`hospital/08-HOSPITAL-OT.md`](hospital/08-HOSPITAL-OT.md)                                     | OT lifecycle + all 15 NABH forms                                          | 38    |
| [`hospital/09-HOSPITAL-ICU.md`](hospital/09-HOSPITAL-ICU.md)                                   | ICU lifecycle + the ICU-without-IPD discovery case                        | 22    |
| [`hospital/10-HOSPITAL-WARDS-BEDS.md`](hospital/10-HOSPITAL-WARDS-BEDS.md)                     | ward/bed lifecycle and occupancy integrity                                | 24    |
| [`hospital/11-HOSPITAL-CROSS-ROLE-WORKFLOWS.md`](hospital/11-HOSPITAL-CROSS-ROLE-WORKFLOWS.md) | 8 multi-user journeys H1–H8                                               | 16    |
| [`hospital/12-HOSPITAL-REGRESSION-CHECKLIST.md`](hospital/12-HOSPITAL-REGRESSION-CHECKLIST.md) | P0 (24) / P1 (34) / P2 (45) release packs                                 | —     |

### Tranche 3 — Clinic tenant (`clinic/`)

| Document                                                                               | Purpose                                                 | Cases |
| -------------------------------------------------------------------------------------- | ------------------------------------------------------- | ----- |
| [`clinic/00-CLINIC-MASTER-CHECKLIST.md`](clinic/00-CLINIC-MASTER-CHECKLIST.md)         | Completeness proof — every clinic surface classified    | —     |
| [`clinic/01-CLINIC-DELTA-FROM-HOSPITAL.md`](clinic/01-CLINIC-DELTA-FROM-HOSPITAL.md)   | **Authoritative comparison** + all negative/drift cases | 40    |
| [`clinic/02-CLINIC-ADMIN.md`](clinic/02-CLINIC-ADMIN.md)                               | admin setup, settings, reports, audit                   | 26    |
| [`clinic/03-CLINIC-RECEPTIONIST.md`](clinic/03-CLINIC-RECEPTIONIST.md)                 | patients, identity, appointments, OPD, billing          | 22    |
| [`clinic/04-CLINIC-DOCTOR.md`](clinic/04-CLINIC-DOCTOR.md)                             | consultation, prescriptions, lab, PDFs                  | 20    |
| [`clinic/05-CLINIC-PHARMACY-INTEGRATION.md`](clinic/05-CLINIC-PHARMACY-INTEGRATION.md) | clinic pharmacy module                                  | 16    |
| [`clinic/06-CLINIC-CROSS-ROLE-WORKFLOWS.md`](clinic/06-CLINIC-CROSS-ROLE-WORKFLOWS.md) | journeys C1–C8                                          | 10    |
| [`clinic/07-CLINIC-REGRESSION-CHECKLIST.md`](clinic/07-CLINIC-REGRESSION-CHECKLIST.md) | P0 (14) / P1 (18) / P2 (22)                             | —     |

### Tranche 3 — Standalone Pharmacy tenant (`pharmacy/`)

| Document                                                                                       | Purpose                                                            | Cases |
| ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ | ----- |
| [`pharmacy/00-PHARMACY-MASTER-CHECKLIST.md`](pharmacy/00-PHARMACY-MASTER-CHECKLIST.md)         | Completeness proof — every pharmacy surface + all 56 ERP endpoints | —     |
| [`pharmacy/01-PHARMACY-ADMIN.md`](pharmacy/01-PHARMACY-ADMIN.md)                               | tenant admin shell, staff, billing, analytics, audit, settings     | 24    |
| [`pharmacy/02-PHARMACIST.md`](pharmacy/02-PHARMACIST.md)                                       | pharmacist role, permissions, **clinical-drift battery**           | 20    |
| [`pharmacy/03-PHARMACY-TIERS.md`](pharmacy/03-PHARMACY-TIERS.md)                               | SOLO / SINGLE / MULTI + branch model                               | 30    |
| [`pharmacy/04-PHARMACY-INVENTORY.md`](pharmacy/04-PHARMACY-INVENTORY.md)                       | masters, batches, adjustment, **FEFO**                             | 22    |
| [`pharmacy/05-PHARMACY-PURCHASES-SUPPLIERS.md`](pharmacy/05-PHARMACY-PURCHASES-SUPPLIERS.md)   | suppliers, manufacturers, purchase lifecycle                       | 16    |
| [`pharmacy/06-PHARMACY-SALES-BILLING.md`](pharmacy/06-PHARMACY-SALES-BILLING.md)               | billing counter, invoices                                          | 20    |
| [`pharmacy/07-PHARMACY-RETURNS-REFUNDS.md`](pharmacy/07-PHARMACY-RETURNS-REFUNDS.md)           | patient refunds, supplier returns                                  | 16    |
| [`pharmacy/08-PHARMACY-EXPIRY-BATCHES.md`](pharmacy/08-PHARMACY-EXPIRY-BATCHES.md)             | buckets, block, dispose                                            | 14    |
| [`pharmacy/09-PHARMACY-REPORTS-AUDIT.md`](pharmacy/09-PHARMACY-REPORTS-AUDIT.md)               | reconciliation fixture, CSV export, audit                          | 14    |
| [`pharmacy/10-PHARMACY-CROSS-ROLE-WORKFLOWS.md`](pharmacy/10-PHARMACY-CROSS-ROLE-WORKFLOWS.md) | journeys P1–P8                                                     | 10    |
| [`pharmacy/11-PHARMACY-REGRESSION-CHECKLIST.md`](pharmacy/11-PHARMACY-REGRESSION-CHECKLIST.md) | P0 (16) / P1 (20) / P2 (26)                                        | —     |

### Tranche 4 — end-to-end journeys (`e2e/`, 127 cases)

| Document                                                                             | Journey                                     | Cases |
| ------------------------------------------------------------------------------------ | ------------------------------------------- | ----- |
| [`e2e/00-E2E-MASTER-INDEX.md`](e2e/00-E2E-MASTER-INDEX.md)                           | index, verified product states, session map | —     |
| [`e2e/01-COMPLETE-HOSPITAL-JOURNEY.md`](e2e/01-COMPLETE-HOSPITAL-JOURNEY.md)         | a full hospital day, 8 actors               | 12    |
| [`e2e/02-COMPLETE-CLINIC-JOURNEY.md`](e2e/02-COMPLETE-CLINIC-JOURNEY.md)             | clinic lifecycle + hospital-only denial     | 8     |
| [`e2e/03-COMPLETE-PHARMACY-JOURNEY.md`](e2e/03-COMPLETE-PHARMACY-JOURNEY.md)         | standalone pharmacy, three tiers            | 8     |
| [`e2e/04-OPD-TO-IPD-TO-DISCHARGE.md`](e2e/04-OPD-TO-IPD-TO-DISCHARGE.md)             | the inpatient spine                         | 12    |
| [`e2e/05-PATIENT-IDENTITY-JOURNEY.md`](e2e/05-PATIENT-IDENTITY-JOURNEY.md)           | **PI-1…PI-10 — clinical-safety core**       | 12    |
| [`e2e/06-PRESCRIPTION-TO-PHARMACY.md`](e2e/06-PRESCRIPTION-TO-PHARMACY.md)           | prescriber → dispenser continuity           | 6     |
| [`e2e/07-BILLING-PAYMENT-JOURNEY.md`](e2e/07-BILLING-PAYMENT-JOURNEY.md)             | financial integrity                         | 8     |
| [`e2e/08-OT-JOURNEY.md`](e2e/08-OT-JOURNEY.md)                                       | surgical lifecycle + NABH forms             | 5     |
| [`e2e/09-ICU-JOURNEY.md`](e2e/09-ICU-JOURNEY.md)                                     | ICU stay lifecycle                          | 4     |
| [`e2e/10-NURSING-JOURNEY.md`](e2e/10-NURSING-JOURNEY.md)                             | both nurse-login modes                      | 5     |
| [`e2e/11-STAFF-LIFECYCLE.md`](e2e/11-STAFF-LIFECYCLE.md)                             | hire → work → revoke → deactivate           | 6     |
| [`e2e/12-BED-WARD-LIFECYCLE.md`](e2e/12-BED-WARD-LIFECYCLE.md)                       | the four bed states, audited                | 5     |
| [`e2e/13-CLINICAL-DOCUMENT-LIFECYCLE.md`](e2e/13-CLINICAL-DOCUMENT-LIFECYCLE.md)     | generated PDFs + uploads                    | 4     |
| [`e2e/14-PHARMACY-STOCK-LIFECYCLE.md`](e2e/14-PHARMACY-STOCK-LIFECYCLE.md)           | **numeric stock fixture**                   | 6     |
| [`e2e/15-MULTI-BRANCH-PHARMACY-JOURNEY.md`](e2e/15-MULTI-BRANCH-PHARMACY-JOURNEY.md) | branch isolation (A=8, B=5)                 | 5     |
| [`e2e/16-TENANT-ISOLATION-E2E.md`](e2e/16-TENANT-ISOLATION-E2E.md)                   | captured-id sweep                           | 6     |
| [`e2e/17-MODULE-REVOCATION-E2E.md`](e2e/17-MODULE-REVOCATION-E2E.md)                 | live entitlement change                     | 5     |
| [`e2e/18-FAILURE-RECOVERY-JOURNEYS.md`](e2e/18-FAILURE-RECOVERY-JOURNEYS.md)         | interruption & recovery                     | 10    |

### Tranche 4 — release pack (`release/`, no new case IDs)

| Document                                                                           | Purpose                                          |
| ---------------------------------------------------------------------------------- | ------------------------------------------------ |
| [`release/00-RELEASE-QA-POLICY.md`](release/00-RELEASE-QA-POLICY.md)               | tiers, entry/exit criteria, non-negotiable rules |
| [`release/01-P0-SMOKE-SUITE.md`](release/01-P0-SMOKE-SUITE.md)                     | the 54 blocking cases, in one runnable sheet     |
| [`release/02-P1-REGRESSION-SUITE.md`](release/02-P1-REGRESSION-SUITE.md)           | 72 workflow cases + 12 E2E gates                 |
| [`release/03-P2-FULL-REGRESSION-GUIDE.md`](release/03-P2-FULL-REGRESSION-GUIDE.md) | the whole pack, with a 15-day schedule           |
| [`release/04-RELEASE-SIGN-OFF.md`](release/04-RELEASE-SIGN-OFF.md)                 | the sign-off sheet                               |
| [`release/05-QA-DAILY-EXECUTION-GUIDE.md`](release/05-QA-DAILY-EXECUTION-GUIDE.md) | a junior tester's daily guide                    |
| [`release/06-QA-BUG-TRIAGE-GUIDE.md`](release/06-QA-BUG-TRIAGE-GUIDE.md)           | severity with HMS-specific examples              |

**Grand total: 1,176 cases** — 265 (T1) + 464 (T2) + 320 (T3) + 127 (T4).
Critical 531 · High 461 · Medium 157 · Low 27.

### Release gate

Before every staging→production release run **Hospital P0 (24) + Clinic P0 (14) + Pharmacy P0 (16) = 54 cases**,
collected in [`release/01-P0-SMOKE-SUITE.md`](release/01-P0-SMOKE-SUITE.md). **Tranche 4 did not
increase P0**; the E2E gates sit in P1, with four promotion candidates listed for the release
manager to decide on.

---

## 3. ⚠️ Read `status/IMPLEMENTATION-STATUS.md` before raising any bug

Several parts of HMS are **deliberately unfinished, unreachable, or drifted from the intended
design**. If you do not read that document first you will file bugs against a placeholder screen
and against roles nobody can create.

The short version:

| Thing                                             | Reality                                                                                    |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| **Pathology tab**                                 | PLACEHOLDER — literally says "under development". Its module key can never be enabled.     |
| **Lab**                                           | Backend scaffolding only (`LabOrder` entity). No UI, no controller.                        |
| **`PHARMACY_ADMIN`, `INVENTORY_MANAGER`**         | DEAD ROLES — referenced in one `@PreAuthorize`, never assignable.                          |
| **Clinic IPD / Pharmacy OPD, IPD, Beds, Wards**   | API namespaces exist and are **ungated**. Not supported product-wise. Negative tests only. |
| **Tenant impersonation**                          | Does not exist. Out of scope.                                                              |
| **Notification bell on Nurse Incharge dashboard** | UI present, backend restricts to `NURSE` only → 403 expected.                              |

---

## 4. Test Case ID scheme

Every ID is globally unique across the whole pack.

| Prefix        | Document                   |
| ------------- | -------------------------- |
| `TC-AUTH-###` | 03 Global Auth & Security  |
| `TC-SA-###`   | 04 Super Admin             |
| `TC-API-###`  | 05 API Test Track          |
| `TC-ISO-###`  | Tenant Isolation           |
| `TC-PERM-###` | Role Permission Matrix     |
| `TC-MOD-###`  | Plan & Module Restrictions |
| `TC-VIS-###`  | Data Visibility            |
| `TC-MODE-###` | Special Modes              |

| `TC-HA-` `TC-HAC-` `TC-HAS-` `TC-HR-` `TC-HD-` `TC-HN-` `TC-HNI-` `TC-HP-` `TC-HB-` `TC-HOT-` `TC-HICU-` `TC-HWB-` `TC-HX-` | Tranche 2 — Hospital |
| `TC-CA-` `TC-CR-` `TC-CD-` `TC-CDR-` `TC-CP-` `TC-CX-` | Tranche 3 — Clinic |
| `TC-PA-` `TC-PH-` `TC-PT-` `TC-PI-` `TC-PP-` `TC-PS-` `TC-PRF-` `TC-PE-` `TC-PR-` `TC-PX-` | Tranche 3 — Pharmacy |
| **`TC-E2E-`** | **Tranche 4 — end-to-end journeys (001–127)** |

The `release/` documents define **no** new IDs — they select existing ones.

## 5. Priority definitions

| Priority     | Meaning                                                                                                  |
| ------------ | -------------------------------------------------------------------------------------------------------- |
| **Critical** | Patient safety, cross-tenant data leak, authentication bypass, data loss. A failure here blocks release. |
| **High**     | Core clinical or commercial workflow broken; authorization weaker than intended.                         |
| **Medium**   | Secondary workflow, validation, or UX correctness.                                                       |
| **Low**      | Cosmetic, non-blocking, or edge case.                                                                    |

## 6. Execution order

1. `01` environment → `02` test data (**everything depends on this**)
2. `03` auth → `05` API track (get a JWT working before isolation testing)
3. `cross-tenant/*` — highest-value security coverage
4. `04` Super Admin
5. Tranches 2 and 3 — the per-tenant packs
6. Tranche 4 `e2e/` **last**, because the journeys assume a populated system
7. `release/` whenever a build is going out
