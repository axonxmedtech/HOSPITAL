# IMPLEMENTATION STATUS REGISTER

**Baseline:** `origin/staging` @ `aa143a7` · Read this **before** raising any bug.

Every entry answers: _if this workflow fails, is it a bug, unfinished work, an unsupported
combination, or a deliberate restriction?_ All evidence is a file and line in the baseline commit.

| Status                       | Meaning for a tester                                                                     |
| ---------------------------- | ---------------------------------------------------------------------------------------- |
| `IMPLEMENTED`                | Test it fully. Failures are bugs.                                                        |
| `PARTIAL`                    | Test what exists; the listed gap is known — do not raise it.                             |
| `PLACEHOLDER`                | Nothing to test. Do not create functional cases.                                         |
| `DEAD_UNREACHABLE`           | Cannot be reached by any user. Do not test positively.                                   |
| `BACKEND_WITHOUT_UI`         | API exists, no screen. Test only via API if a case says so.                              |
| `UI_WITHOUT_BACKEND`         | Screen exists, API refuses. Expect the failure; raise as Medium referencing this row.    |
| `IMPLEMENTATION_DRIFT`       | Code permits what product forbids. **Negative tests only**; a "success" is a defect.     |
| `NEEDS_PRODUCT_CONFIRMATION` | Behaviour unclear. Record what happens; do not classify pass/fail until product answers. |

---

## 1. Tenant types & roles

| Item                                                       | Status                                                      | Evidence                                                                                                         | Notes                                                                                             |
| ---------------------------------------------------------- | ----------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| HOSPITAL, CLINIC, PHARMACY tenant types                    | `IMPLEMENTED`                                               | `entity/HospitalType.java`                                                                                       |                                                                                                   |
| Clinic sharing Hospital frontend/routes                    | `IMPLEMENTED` (by design)                                   | `App.jsx` — no `/clinic/*` routes; `LandingRedirect` sends clinic admins to `/hospital/admin`                    | Not a bug. See `TC-AUTH-003`.                                                                     |
| 8 assignable roles                                         | `IMPLEMENTED`                                               | `setRole(...)` call sites                                                                                        | SUPER_ADMIN, HOSPITAL_ADMIN, DOCTOR, RECEPTIONIST, PHARMACIST, NURSE, NURSE_INCHARGE, OT_INCHARGE |
| `PHARMACY_ADMIN` role                                      | `DEAD_UNREACHABLE`                                          | `MedicineMasterController.java:45,75` only; never assigned; absent from `SecurityConfig`                         | `TC-PERM-008`                                                                                     |
| `INVENTORY_MANAGER` role                                   | `DEAD_UNREACHABLE`                                          | same                                                                                                             | `TC-PERM-008`                                                                                     |
| DOCTOR / RECEPTIONIST on a PHARMACY tenant                 | `IMPLEMENTATION_DRIFT` / **POTENTIAL_AUTHORIZATION_DEFECT** | `SecurityConfig:104-105` admits both to `/pharmacy/**`; `LandingRedirect` sends any DOCTOR to `/hospital/doctor` | Product: **not supported**. `TC-API-011`, `TC-PERM-017`                                           |
| NURSE / NURSE_INCHARGE / OT_INCHARGE on clinic or pharmacy | `IMPLEMENTED` restriction                                   | `SecurityConfig:98-105` excludes them from `/clinic/**`, `/pharmacy/**`                                          | Correct behaviour = 403. `TC-API-008`                                                             |
| Tenant impersonation / switching                           | **NOT IMPLEMENTED**                                         | no code                                                                                                          | Out of scope. No positive cases.                                                                  |

## 2. Modules & entitlement

| Item                                                                                          | Status                                             | Evidence                                                                                                                                                                                                               | Notes                                                   |
| --------------------------------------------------------------------------------------------- | -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| `EntitlementRegistry` as enforcement                                                          | **DECLARATION-ONLY**                               | `EntitlementRegistry.java` javadoc: _"This class only declares. It enforces nothing."_                                                                                                                                 | Only `validatePlanModules` (plan creation) enforces it. |
| `@RequireModule` gating                                                                       | `IMPLEMENTED`                                      | 47 handlers; `ModuleAccessAspect` reads the live hospital row                                                                                                                                                          | Revocation applies without re-login. `TC-SA-041`        |
| `@TenantType` gating                                                                          | `PARTIAL`                                          | 76 handlers — OT, ICU, `HospitalDashboard` **only**                                                                                                                                                                    | Everything else has no tenant-type gate.                |
| OPD module gate                                                                               | **NONE**                                           | no `@RequireModule("OPD")` in the codebase                                                                                                                                                                             | `IMPLEMENTATION_DRIFT`. `TC-MOD-010`                    |
| PHARMACY module gate                                                                          | **NONE**                                           | no `@RequireModule("PHARMACY")`                                                                                                                                                                                        | `TC-MOD-012`                                            |
| IPD module gate                                                                               | `PARTIAL`                                          | exactly **1** `@RequireModule("IPD")`                                                                                                                                                                                  | Wards/beds ungated. `TC-MOD-011`                        |
| Clinic reaching `/clinic/ipd`                                                                 | `IMPLEMENTATION_DRIFT`                             | 13 endpoints, no `@TenantType`, no `@RequireModule`                                                                                                                                                                    | Product: IPD not supported for Clinic. `TC-API-009`     |
| Pharmacy reaching `/pharmacy/opd`, `/ipd`, `/beds`, `/wards`                                  | `IMPLEMENTATION_DRIFT`                             | 11 + 13 + 5 + 7 endpoints, all ungated                                                                                                                                                                                 | Product: not supported. `TC-API-010`                    |
| APPOINTMENTS, BILLING, NURSING, OT, ICU, MEDICAL_INVENTORY, HOSPITAL_INVENTORY, REPORTS gates | `IMPLEMENTED`                                      | 5 / 3 / 11 / 10 / 9 / 6 / 1 / 1 handlers                                                                                                                                                                               | Reference behaviour. `TC-MOD-013`…`019`                 |
| Pathology                                                                                     | `PLACEHOLDER`                                      | `HospitalAdminDashboard.jsx:2200` gate `PATHOLOGY` (not in `ALL_MODULES`); `:4933` _"currently under development"_                                                                                                     | **No functional QA.** `TC-MOD-030`                      |
| Lab — **ordering**                                                                            | **`PARTIAL`** ⟵ _corrected at Tranche 2_           | `ConsultationModal` has a lab-required toggle and an 8-test picker (`CBC, LFT, RFT, RBS, Lipid Profile, TSH, HbA1c, Urine Routine`); `DoctorService:621-631` **creates `LabOrder` rows**; they print on the case paper | Test ordering: `TC-HD-008`.                             |
| Lab — **results / management**                                                                | `BACKEND_WITHOUT_UI` → in fact **NOT IMPLEMENTED** | no lab controller, no results screen, no lab dashboard                                                                                                                                                                 | Do not invent a results workflow.                       |

## 3. Authorization drift (settings & PDFs)

| Item                                               | Status                                                      | Evidence                                                                                                                                                                                           | Notes                                                                         |
| -------------------------------------------------- | ----------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| Tenant settings endpoints with no `@PreAuthorize`  | `IMPLEMENTATION_DRIFT` / **POTENTIAL_AUTHORIZATION_DEFECT** | `HospitalAuthController.java:79-151` — 10 endpoints (`/settings/fees`, `/operations`, `/print-payment`, `/barcode`, `/nurse-login`, `/ot-incharge`, `/subscription`); class has no `@PreAuthorize` | Falls back to `SecurityConfig` → all 7 roles. **`TC-API-006`, `TC-PERM-005`** |
| Prescription PDF endpoints with no `@PreAuthorize` | `IMPLEMENTATION_DRIFT`                                      | `DoctorController.java:228, 276`                                                                                                                                                                   | Every neighbouring method is gated. `TC-API-007`, `TC-PERM-006`               |
| Receptionist may write clinical records            | `NEEDS_PRODUCT_CONFIRMATION`                                | `Vitals`, `NursingNote`, `SugarChart`, `InitialAssessment`, `VulnerabilityAssessment`, `MedicationAdmin` controllers admit `RECEPTIONIST`                                                          | `TC-PERM-009`                                                                 |
| Pharmacist may list wards                          | `NEEDS_PRODUCT_CONFIRMATION`                                | `WardController` GET admits `PHARMACIST`                                                                                                                                                           | `TC-PERM-004`                                                                 |
| Doctor may create doctors                          | `NEEDS_PRODUCT_CONFIRMATION`                                | `DoctorController` POST = `ADM DOC` per derived matrix                                                                                                                                             | `TC-PERM-002`                                                                 |

## 4. UI ↔ backend mismatches

| Item                                          | Status                                       | Evidence                                                                                                                          | Notes                                               |
| --------------------------------------------- | -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Notification bell on Nurse Incharge dashboard | `UI_WITHOUT_BACKEND`                         | `NurseInchargeDashboard.jsx:147` renders `<NotificationBell/>`; `NotificationController` is `hasRole('NURSE')` on all 4 endpoints | NI will get 403. `TC-PERM-007`                      |
| Platform Users screen                         | `BACKEND_WITHOUT_UI`                         | `GET /platform/users` exists; `platformService.getUsers` has **no caller**                                                        | `TC-SA-037` may be N/A                              |
| Edit FAQ                                      | `BACKEND_WITHOUT_UI`                         | `PUT /platform/faqs/{id}` exists; `platformService.updateFaq` has **no caller**                                                   | Delete + recreate. `TC-SA-058`                      |
| OT Incharge dashboard                         | `PARTIAL` / **BACKEND_UI_COVERAGE_MISMATCH** | `OtInchargeDashboard.jsx` — 2 tabs; OT backend has 10 controllers                                                                 | Two-tab UI is the supported baseline. `TC-PERM-012` |
| Pharmacy Inventory Items (platform)           | absent **by design**                         | `PlatformDashboard.jsx:742-748` — Pharmacy group has 4 sub-items                                                                  | Not a bug.                                          |

## 5. Navigation model

| Item                                       | Status                    | Evidence                                                   | Notes                                                            |
| ------------------------------------------ | ------------------------- | ---------------------------------------------------------- | ---------------------------------------------------------------- |
| Tab-based navigation (no URLs per screen)  | `IMPLEMENTED` (by design) | `App.jsx` — 18 `<Route path=>`; dashboards use `activeTab` | Refresh returns to the default tab. **Not a bug.** `TC-AUTH-015` |
| `/ipd/:id` deep link                       | `IMPLEMENTED`             | `App.jsx:296`                                              | The **only** deep-linkable record. `TC-ISO-020`                  |
| Session in `sessionStorage`                | `IMPLEMENTED`             | `apiService.js`                                            | Per-tab sessions. `TC-AUTH-013`                                  |
| Session revocation on password/role change | `IMPLEMENTED`             | `User.java:178-192` (`tokenVersion`)                       | `TC-AUTH-019/020`                                                |
| JWT lifetime 12 h                          | `IMPLEMENTED`             | `application.properties:42`                                | `TC-AUTH-018`                                                    |

## 6. Patient identity (Checkpoint 2F Phase A)

| Item                                           | Status                                                | Evidence                                                                                                             | Notes                                                                                                 |
| ---------------------------------------------- | ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Duplicate-phone warn → confirm workflow        | `IMPLEMENTED`                                         | `PatientService.addPatient/updatePatient`, `AppointmentService.createAppointment`, `DuplicatePhoneConflictModal.jsx` | `TC-VIS-005`…`008`                                                                                    |
| Value-bound acknowledgement                    | `IMPLEMENTED`                                         | `Patient.duplicatePhoneAckFor/At/By`; READ_ONLY to clients                                                           |                                                                                                       |
| **Database uniqueness / concurrency backstop** | **NOT YET IMPLEMENTED** (Phase D)                     | `DatabaseMigrationRunner.ensurePatientDuplicatePhoneAckColumns` — columns only; no unique index                      | Two simultaneous submissions **can** still both succeed. Do **not** write a case asserting otherwise. |
| Patient reactivation                           | **NOT IMPLEMENTED**                                   | no `setIsActive(true)` path for Patient                                                                              | Deleted patients cannot be restored.                                                                  |
| Editing an inactive patient                    | `NEEDS_PRODUCT_CONFIRMATION` (known defect, deferred) | `PatientService.getPatientById` filters `hospitalId` but not `isActive`                                              | Recorded as follow-up D4.                                                                             |

## 7. Open `NEEDS_PRODUCT_CONFIRMATION` list

1. Lab / `LabOrder` — real feature, scaffolding, or Pathology seed?
2. Receptionist writing clinical records (§3).
3. Pharmacist listing wards (§3).
4. Doctor creating doctors (§3).
5. Same-tenant prescription PDF access by pharmacist/nurse (`TC-PERM-006`).
6. ICU without IPD on a plan (`TC-MOD-006`).
7. SINGLE_PHARMACY tenant reaching `/pharmacy/branches` (`TC-MOD-028`).
8. Second doctor on a single-doctor tenant (`TC-MODE-006`).
9. Additional pharmacist on a solo-pharmacist tenant (`TC-MODE-015`).
10. `isSingleDoctor` editable after creation / settable on a pharmacy tenant (`TC-MODE-009`, `018`).
11. Hard vs soft tenant deletion (`TC-SA-038`).
12. Platform medicine delete vs existing tenant stock (`TC-SA-047`).
13. Doctors viewing colleagues' OPD cases (`TC-VIS-010`).

---

## 8. Added at Tranche 2 (Hospital inspection)

| Item                                                                                                                    | Status                                        | Evidence                                                                                                                        | Test                                           |
| ----------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| **Lab ordering**                                                                                                        | `PARTIAL`                                     | see §2 above — ordering implemented, results not                                                                                | `TC-HD-008`                                    |
| **Pharmacy-tenant-only admin tabs** (Pharmacies, Suppliers, pharmacy Billing, pharmacy Analytics, pharmacy Pharmacists) | render only for `hospitalType === 'PHARMACY'` | `HospitalAdminDashboard.jsx:2251` rebuilds the tab list for pharmacy tenants                                                    | out of scope for Hospital; Tranche 3           |
| **Patients / Doctors / Receptionists tabs gated on `OPD`**                                                              | by design                                     | `requiredModule: 'OPD'` on all three                                                                                            | `TC-HAC-022` — API is ungated, so record drift |
| **In-Clinic Presets tab driven by a live setting**                                                                      | `IMPLEMENTED`                                 | `user.inClinic` refreshed by the `SETTINGS_UPDATED` WebSocket message                                                           | `TC-HA-031`, `TC-HAS-004`                      |
| **`hospital-inventory` tab label is tenant-worded**                                                                     | `IMPLEMENTED`                                 | `label: \`${tenantWord} Inventory\``                                                                                            | `TC-HA-035`                                    |
| **Hospital bill cancellation / refund**                                                                                 | `PARTIAL`                                     | no cancel or refund action found on OPD/IPD bills (pharmacy refunds are separate)                                               | `TC-HB-023`                                    |
| **OT policy engine**                                                                                                    | `IMPLEMENTED`                                 | 8 policy keys × 3 scopes (`ANY`/`ELECTIVE`/`EMERGENCY`) + 4 presets (Small/Medium/Large/Corporate-NABH); `ADVISORY` mode exists | `TC-HAS-024`, `TC-HOT-010`                     |
| **Surgery state machine**                                                                                               | `IMPLEMENTED`                                 | `SurgeryStatus` 9 states; `SurgeryStateMachine` rejects illegal transitions; `lifecycle_version` detects stale commands         | `TC-HOT-021`, `TC-HOT-022`                     |
| **Bed status audit**                                                                                                    | `IMPLEMENTED`                                 | every write via `BedStatusService` into `bed_status_audits`                                                                     | `TC-HWB-016`                                   |
| **ICU stay backfill on startup**                                                                                        | `IMPLEMENTED`                                 | `backfillIcuStaysForCurrentOccupants`                                                                                           | `TC-HICU-003`                                  |
| **ICU without IPD**                                                                                                     | `NEEDS_PRODUCT_CONFIRMATION`                  | runtime discovery case written with an observation form                                                                         | `TC-HICU-021`                                  |

---

## 9. Added at Tranche 3 (Clinic & standalone Pharmacy inspection)

### Confirmed drift (negative tests only — a success is a defect)

| Item                                                                                               | Status                 | Evidence                                                                                                                                       | Test                                  |
| -------------------------------------------------------------------------------------------------- | ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| **`/clinic/ipd`** — 13 endpoints                                                                   | `IMPLEMENTATION_DRIFT` | aliased; **no** `@TenantType`, **no** `@RequireModule`                                                                                         | `TC-CD-010`…`015`, `TC-CX-010`        |
| **`/clinic/wards`** 7 · **`/clinic/beds`** 5                                                       | `IMPLEMENTATION_DRIFT` | same                                                                                                                                           | `TC-CD-016`, `TC-CD-017`              |
| **`/pharmacy/opd`** 11 · **`/pharmacy/ipd`** 13 · **`/pharmacy/beds`** 5 · **`/pharmacy/wards`** 7 | `IMPLEMENTATION_DRIFT` | same                                                                                                                                           | `TC-PH-019`, `TC-PX-010`              |
| **`/pharmacy/doctors`** 11 · **`/pharmacy/receptionists`** 6                                       | `IMPLEMENTATION_DRIFT` | aliased **and** `SecurityConfig:104-105` admits both roles; `LandingRedirect` sends any DOCTOR to `/hospital/doctor` regardless of tenant type | `TC-PA-008`, `TC-PH-016`/`017`        |
| Tenant settings endpoints on `/clinic/**` and `/pharmacy/**`                                       | `IMPLEMENTATION_DRIFT` | the same 10 un-`@PreAuthorize`d `HospitalAuthController` endpoints, aliased to all three namespaces                                            | `TC-PA-015`, `TC-PH-014`, `TC-CD-030` |

### Correctly closed (reference behaviour)

| Item                                                              | Why it is closed                                                                                    | Test                     |
| ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ------------------------ |
| ICU, OT, nursing on `/clinic` and `/pharmacy`                     | **never aliased** → 404; the `/hospital` forms are `@TenantType(HOSPITAL)` + `@RequireModule` → 403 | `TC-CD-018`, `TC-PH-020` |
| `/hospital/dashboard` for clinic and pharmacy                     | `@TenantType(HOSPITAL)`                                                                             | `TC-CD-009`              |
| `HOSPITAL_INVENTORY` for clinic                                   | `@RequireModule` on a module a clinic cannot buy                                                    | `TC-CD-019`              |
| NURSE / NURSE_INCHARGE / OT_INCHARGE on `/clinic` and `/pharmacy` | `SecurityConfig:104-105` omits them                                                                 | `TC-CD-008`, `TC-PH-018` |
| APPOINTMENTS · BILLING · MEDICAL_INVENTORY · REPORTS in clinic    | real `@RequireModule` gates                                                                         | `TC-CD-036`              |

### Newly discovered implementation facts

| Item                                       | Status                    | Evidence                                                                                                         | Test                      |
| ------------------------------------------ | ------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------------------------- |
| **FEFO batch selection is implemented**    | `IMPLEMENTED`             | `GET /pharmacy/inventory/search-batches` → `searchAvailableBatchesFEFO`                                          | `TC-PI-012`, `TC-PS-003`  |
| **Stock adjustment endpoint**              | `IMPLEMENTED`             | `POST /pharmacy/inventory/adjust`                                                                                | `TC-PI-010`/`011`         |
| **Batch transaction ledger**               | `IMPLEMENTED`             | `GET /pharmacy/inventory/transactions/{batchId}`                                                                 | `TC-PI-009`, `TC-PR-001`  |
| **CSV ledger export**                      | `IMPLEMENTED`             | `GET /pharmacy/reports/export` → `exportLedgerCsv`                                                               | `TC-PR-008`               |
| **Returns history endpoint**               | `IMPLEMENTED`             | `GET /pharmacy/inventory/returns-history`                                                                        | `TC-PI-017`               |
| **`X-Branch-ID` branch impersonation**     | `IMPLEMENTED`, admin-only | `JwtAuthenticationFilter:168-175` honours the header **only** when the token's role is `HOSPITAL_ADMIN`          | `TC-PT-024`, `TC-PX-007`  |
| **Pharmacy admin tab set is tier-driven**  | `IMPLEMENTED`             | `HospitalAdminDashboard.jsx:2251-2272` — SOLO no staff tab, SINGLE + Pharmacists, MULTI + Pharmacies & Suppliers | `TC-PT-005`, `012`, `016` |
| **Purchase has no edit / cancel / delete** | **`PARTIAL`**             | only `GET`, `GET /{id}`, `POST`, `POST /{id}/post` exist                                                         | `TC-PP-014`               |
| Clinic has no Time Slots / Calendar        | by design                 | both are `NURSING`-gated                                                                                         | `TC-CX-001`               |

### New `NEEDS_PRODUCT_CONFIRMATION` (Tranche 3)

1. `/pharmacy/branches` reachable on **SINGLE** and **SOLO** tiers — `PharmacyBranchController` has no `@RequireModule` (`TC-PT-011`).
2. Prescriptions tab on a standalone pharmacy, which has no internal prescriber (`TC-PH-004`).
3. Pharmacist's Audit Logs tab vs the admin-only `/pharmacy/audit-logs` endpoint (`TC-PH-013`).
4. Quantity spanning multiple batches in one sale line — auto-split not evidenced (`TC-PI-014`).
5. Duplicate medicine / supplier names — no uniqueness evidenced (`TC-PI-002`, `TC-PP-002`).
6. Tax / discount / rounding controls at the counter — not evidenced in code; record as found (`TC-PS-011`).
7. Refund into a blocked or disposed batch (`TC-PRF-008`).
8. Sale into a deactivated branch (`TC-PT-026`).
9. Unblocking a blocked batch — no obvious endpoint (`TC-PE-005`).
10. Medicine **Categories** screen — API exists, UI presence unverified (`TC-PI-005`).
11. Today-expiry boundary: sellable or not (`TC-PE-002`).
12. Clinic Files & Access offering IPD/OT form keys (`TC-CD-031`).
13. Clinic Operations showing nurse-login / OT-incharge toggles (`TC-CD-030`).
14. Second pharmacist on a SOLO tenant (`TC-PT-010`).
15. A plan carrying two pharmacy tiers (`TC-PT-003`).

---

## 10. Added at Tranche 4 (end-to-end inspection)

Tranche 4 composed the existing cases into 127 journeys. It **inspected no new code paths**, so it
added **no new implementation findings**. What it did add is the _place where each known item gets
exercised end to end_, which is the thing that was previously missing.

### 10.1 Where each known item is now proven under real load

| Known item (section above)                                         | E2E case that exercises it                            |
| ------------------------------------------------------------------ | ----------------------------------------------------- |
| settings endpoints with no `@PreAuthorize` (§3)                    | `TC-E2E-115`                                          |
| prescription-PDF endpoints with no role check (§3)                 | `TC-E2E-101`                                          |
| notification bell on the Nurse Incharge dashboard (§4)             | `TC-E2E-086`                                          |
| **OPD / PHARMACY have no `@RequireModule`; IPD has one** (§2)      | `TC-E2E-115`                                          |
| ungated `/clinic/**` and `/pharmacy/**` aliases (§2)               | `TC-E2E-097`, `TC-E2E-111`                            |
| tab-state navigation; F5 returns to the default tab (§5)           | `TC-E2E-120`                                          |
| **no DB backstop for duplicate phones — Phase D not shipped** (§6) | `TC-E2E-123` (records behaviour; **asserts nothing**) |
| value-bound acknowledgement semantics (§6)                         | `TC-E2E-124`                                          |
| `@TenantType` present only on OT, ICU and HospitalDashboard (§2)   | `TC-E2E-077`, `TC-E2E-081`, `TC-E2E-111`              |
| `EntitlementRegistry` declares but enforces nothing (§2)           | `TC-E2E-116`, `TC-E2E-117`                            |
| `X-Branch-ID` honoured only for `HOSPITAL_ADMIN`                   | `TC-E2E-103`                                          |

### 10.2 One documentation error found and corrected during Tranche 4

Two Tranche-2 documents described an OPD status of **"WAITING"**, which does not exist. The enum is
`Opd.Status { QUEUED, CONSULTED, COMPLETED, IN_IPD }`. Corrected in
`hospital/03-HOSPITAL-DOCTOR.md` and `hospital/01b-HOSPITAL-ADMIN-CLINICAL.md`. All 127 E2E state
tables use the verified enum values listed in `e2e/00-E2E-MASTER-INDEX.md`.

### 10.3 Standing instruction to testers

Everything in this document is **known, accepted and open**. Attach evidence to the entry above;
do **not** open a new bug. Open a new bug only when the behaviour **differs from what is written
here** — including when it is now _better_ than documented, because then this document is wrong.
