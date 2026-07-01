# HMS Functional & Security Audit — 2026-07-01

**Auditor role:** Architecture / QA / Security / Domain review
**Method:** Static source inspection (no code executed, no changes made).
**Scope reality check:** This is a large system — 53 controllers, 54 services, 75
entities, 71 repositories, 32 frontend pages. In a single pass I performed a
**deep** inspection of the security/multi-tenant core, IPD, pharmacy, patient,
and billing paths, and a **breadth** scan across the rest. Findings below are
tagged:

- **[CONFIRMED]** — verified against specific code (file:line given).
- **[PATTERN]** — a systemic pattern observed in sampled code, very likely to
  recur in un-sampled modules of the same shape.
- **[ASSESSMENT]** — reasoned judgement not exhaustively code-verified; treat as
  a lead to verify, not a proven defect.

> Honesty note: I did **not** hand-trace every one of the 32 screens or 54
> services. Do not read the absence of a module below as "audited and clean."

---

## TL;DR — Deployment recommendation

**Do NOT deploy to a real hospital yet.** The role model, password hashing,
pharmacy stock engine, and per-list tenant filtering are genuinely well built.
But there is a **systemic cross-tenant access-control gap**: many endpoints that
take a resource ID from the request path fetch it with a raw `findById(id)` and
never check that the row belongs to the caller's hospital. At least three are
confirmed, including a **write-side** clinical one. In a multi-tenant SaaS
handling patient data, that is a launch-blocking class of bug (and a
DPDP/data-protection exposure).

**Overall HMS Health Score: 62 / 100** — strong foundations, undermined by a
broken-access-control pattern on ID-addressed endpoints.

---

## CONFIRMED FINDINGS (with evidence)

### SEC-1 [CONFIRMED] Cross-tenant IDOR on IPD detail — read
- **Module:** IPD / Admissions
- **Severity:** Critical · **Priority:** P0
- **Where:** `IpdAdmissionService.getIpdAdmissionDetails(Long ipdId)` —
  `backend/.../service/hospital/IpdAdmissionService.java:341-342`; exposed via
  `GET /hospital/ipd/{id}` — `IpdAdmissionController.java:53-56`.
- **Root cause:** `ipdAdmissionRepository.findById(ipdId)` with no
  `ipd.getHospitalId().equals(currentHospitalId)` check. The method returns a
  full clinical DTO (patient identity, doctor, prescriptions, discharge summary,
  medical records).
- **Repro:** Log in as any hospital user. Call `GET /hospital/ipd/{id}` iterating
  `id`. Records belonging to other hospitals are returned.
- **Expected:** 403/404 for IDs outside the caller's hospital.
- **Risk:** Full cross-tenant leak of another hospital's inpatient clinical data.

### SEC-2 [CONFIRMED] Cross-tenant IDOR on IPD write ops — write
- **Module:** IPD / Clinical
- **Severity:** Critical · **Priority:** P0
- **Where:** `IpdAdmissionService.addIpdFollowup(...)` —
  `IpdAdmissionService.java:518-582`. Same pattern in the sibling
  `{id}`-addressed mutations: `planDischarge`, `confirmDischarge`,
  `administerItems`, `addPrescription`, `stopPrescription`, `changeBed`
  (`IpdAdmissionController.java:73-122`). **[PATTERN]** for the siblings —
  `addIpdFollowup` is the confirmed one.
- **Root cause:** `ipdAdmissionRepository.findById(ipdId)` (line 526) is never
  compared to the caller's `hospitalId` (fetched at line 531 but only used to
  stamp the *new* record). A doctor in Hospital A can add follow-ups, administer
  items, and **deduct stock** against Hospital B's admission.
- **Risk:** Cross-tenant data corruption + patient-safety (writing clinical
  notes / medication events onto the wrong hospital's patient).

### SEC-3 [CONFIRMED] Cross-tenant IDOR on medicine-list PDFs
- **Module:** MRD / Printing
- **Severity:** High · **Priority:** P0
- **Where:** `PatientService.getOpdMedicinesPdf(Long opdId)` and
  `getIpdMedicinesPdf(Long ipdId)` — `PatientService.java:826-910`.
- **Root cause:** `medicalRecordRepository.findByOpdId(opdId)` /
  `ipdAdmissionRepository.findById(ipdId)` with no tenant check. The hospital
  letterhead is loaded from the *caller's* hospital, so the output is Hospital
  A's letterhead over Hospital B's patient medication data — a silent leak.

### PERF/INTEG-4 [CONFIRMED] IPD stock deduction has no row lock
- **Module:** Pharmacy / IPD
- **Severity:** Medium · **Priority:** P1
- **Where:** `IpdAdmissionService.java:572-582` — `medicineRepository.findById(id)`
  → check `stockQuantity` → `setStockQuantity(old - qty)` → `save`.
- **Root cause:** Read-modify-write with no pessimistic lock (contrast the
  correct pattern in `PharmacySaleService`, which uses
  `findByIdAndHospitalIdForUpdate`, `PharmacySaleService.java:87`). Under
  concurrent administration the `stock < qty` guard can be bypassed → oversell /
  negative stock. Also `findById(medicineId)` is not hospital-filtered
  (cross-tenant medicine reference).

---

## STRENGTHS (also confirmed — credit where due)

- **Passwords:** BCrypt via `PasswordEncoder` (`SecurityConfig.java:151`). Good.
- **Role model:** Method-level `@PreAuthorize` on **50 / 53** controllers; every
  `/hospital/**` controller has it, with granular role sets. The 3 without are
  legitimately public/auth endpoints (`PlatformAuthController`, `HealthController`,
  `PublicFaqController`). This is well done.
- **Pharmacy sales engine:** Positive-quantity validation, pessimistic locking,
  tenant-scoped queries, and return-quantity guards
  (`PharmacySaleService.java:80-100, 213-278`). Production-quality.
- **Patient list/search/CRUD:** Correctly tenant-scoped via
  `findByPublicIdAndHospitalIdAndIsActiveTrue` and `.filter(hospitalId)`
  (`PatientService.java`). The IDOR pattern shows up in the *PDF/report* helpers,
  not the core CRUD.
- **Auth entrypoint:** Returns 401 (not 403) for unauthenticated; stateless JWT;
  CORS origins are configured, not wildcarded.

---

## SECURITY ISSUES beyond the IDORs

- **JWT-1 [CONFIRMED] Default secret fallback.** `application.properties:28`:
  `jwt.secret=${JWT_SECRET:YOUR_SECRET_KEY_HERE_...}`. If `JWT_SECRET` is unset in
  prod, the app boots with a *known, public* signing key → total auth bypass
  (anyone can mint admin tokens). Severity High. Fix: fail startup if the env var
  is absent; never ship a usable default.
- **JWT-2 [ASSESSMENT] No token revocation / logout invalidation.** Stateless JWT
  with 24h expiry (`jwt.expiration=86400000`) and no denylist observed. A leaked
  or post-termination token is valid until expiry. Acceptable for MVP; document it.
- **AUTHZ-1 [CONFIRMED] Orphan roles in `@PreAuthorize`.** `PHARMACY_ADMIN` and
  `INVENTORY_MANAGER` appear in method annotations but are **not** in the
  `/hospital/**` URL role list (`SecurityConfig.java:84-85`), so those users are
  blocked at the URL layer regardless. Dead/confusing config — reconcile the two
  lists.
- **FILTER [ASSESSMENT] JWT filter swallows errors silently.** On any exception
  it logs and continues unauthenticated (`JwtAuthenticationFilter.java:89-92`) —
  correct behaviour, but confirm no endpoint treats "no auth" as "public."

---

## BUSINESS-LOGIC / DOMAIN (India hospital context)

- **[CONFIRMED-GOOD]** "Cannot follow-up on a non-admitted IPD" guard exists
  (`IpdAdmissionService.java:527`); discharge status uses a state string
  (`ADMITTED` / `DISCHARGE_PLANNED`).
- **[ASSESSMENT] Discharge-before-admission / double-discharge:** verify
  `confirmDischarge` rejects an already-`DISCHARGED` admission and frees the bed
  atomically. Not fully traced.
- **[ASSESSMENT] GST/tax on billing & pharmacy:** tax correctness, rounding, and
  MRP-vs-batch pricing were **not** verified — high-value for Indian billing
  compliance; audit `BillingService` + `PharmacySaleService` totals explicitly.
- **[ASSESSMENT] Duplicate medicine master / duplicate patient (same phone):**
  uniqueness constraints not verified end-to-end.
- **[ASSESSMENT] Negative/oversell:** pharmacy counter path is safe; the **IPD
  administration path is not** (INTEG-4). Inconsistent guarantees across two
  code paths that both move stock — a red flag for a unified inventory ledger.

---

## MULTI-TENANCY VERDICT

The design *intends* tenant isolation (JWT carries `hospitalId`,
`SecurityContextHelper.getCurrentHospitalId()`, and ~89 tenant-scoped repo calls).
But isolation is enforced **per-method by convention, not structurally.** There
are ~127 raw `Repository.findById(...)` calls in hospital services; most operate
on IDs derived from an already-validated parent, but the sampled exceptions
(SEC-1/2/3) prove the convention is not applied uniformly, including on
request-supplied path IDs. **This is the single most important thing to fix
before go-live.** Recommended structural fixes:
1. Hibernate `@Filter` / tenant interceptor auto-applying `hospital_id` to every
   query, **or**
2. A mandated `findByIdAndHospitalId` repository convention with a lint/ArchUnit
   rule banning bare `findById` on tenant-owned entities in service code.

---

## AREAS NOT DEEPLY AUDITED (explicit gaps in THIS pass)

Lab result entry, Radiology, Appointments/calendar, Nurse vitals & tasks, OT,
Bed transfer atomicity, MRD document upload (Cloudinary file-type/size/auth),
WebSocket per-tenant topic scoping, Purchase/Supplier, Platform admin, Audit-log
completeness, and all frontend state/loading/empty/error states. Each should get
the same `{id}`-endpoint IDOR check applied in SEC-1/2. **[PATTERN]** strongly
suggests the same IDOR exists in other `{id}`-addressed detail/mutation
endpoints (lab orders, radiology reports, billing by id, bed by id).

---

## SUGGESTED FIX ORDER

1. **P0** — Add hospital-ownership checks to every `{id}`-addressed endpoint
   (start: IPD, then sweep all controllers). Structural fix preferred (tenant
   filter/interceptor) over hand-patching 100+ call sites.
2. **P0** — Remove the JWT default-secret fallback; fail fast if unset.
3. **P1** — Pessimistic lock + hospital filter on the IPD stock-deduction path
   (mirror `PharmacySaleService`). Unify the two inventory-movement code paths.
4. **P1** — Reconcile role lists (`SecurityConfig` vs `@PreAuthorize`).
5. **P2** — Verify billing/tax math, discharge state machine, uniqueness
   constraints; then work through the un-audited modules with the SEC-1 checklist.

---

# END-TO-END TRACE PASS (added 2026-07-01, second pass)

Follow-up to the request "trace and test all screens, services, controllers,
pages, end to end." What was actually executed vs. traced:

## 1. Automated tests — EXECUTED ✅
- `mvn test` ran to completion on an embedded DB (no external MySQL needed).
- **17 test classes, ~71 tests, 0 failures / 0 errors.** All green.
- **BUT coverage is shallow and single-tenant:** only 17 of 54 services have
  tests, and **none assert cross-tenant isolation** — every test operates inside
  one hospital's happy path. The SEC-1/2/3 IDORs would pass all current tests.
  Green suite ≠ safe. `HospitalAuthControllerIT` is an IT (failsafe) and does
  **not** run under `mvn test`; use `mvn verify` to include it.

## 2. Frontend build — EXECUTED ✅ (with warnings)
- `npm run build` (`tsc && vite build`) **succeeded**, 1992 modules, no
  TypeScript/import errors. The deleted `DataGrid.jsx/.css` (git status) is
  **clean** — zero references remain.
- **PERF-FE-1 [CONFIRMED] No code-splitting.** Output is a single
  **1.9 MB** JS chunk (`gzip 483 kB`). Vite explicitly warns. On the slow
  networks common in Indian hospitals this is a real first-paint problem. Fix
  with route-level `import()` / `manualChunks`.
- **[CONFIRMED-minor] axios is both statically and dynamically imported**
  (`ProfileModal.jsx` vs `apiService.js`) — defeats the dynamic split; harmless
  but sloppy.

## 3. Auth entry path (every screen) — TRACED ✅
- `apiService.js` is solid: Bearer injection from `sessionStorage`, 401 →
  clear token + redirect to role-specific login, 30 s timeout, 5 MB response cap.
- **[ASSESSMENT] XSS → token theft:** JWT in `sessionStorage` is script-readable;
  combined with no server-side token revocation (JWT-2) and a 24 h expiry, one
  XSS = a stolen, un-revocable session. Standard SPA tradeoff, but worth a CSP.

## 4. Controller → service → repo IDOR sweep — TRACED (mechanical, all 40 services)
Ratio of ownership-guards to bare `Repository.findById(...)` per service (a
bare `findById` on a request-supplied, tenant-owned id = candidate IDOR):

| Service | guards | bare findById | Verdict |
|---|---:|---:|---|
| IpdAdmissionService | 5 | 46 | **Confirmed IDOR (read+write)** — SEC-1/2 |
| OpdService | 0 | 7 | **Confirmed IDOR** — `getOpdById(id)` unfiltered (OpdService.java:144); `createOpd` links patient by bare id (:56) |
| MrdService | 1 | 8 | **High-risk, likely IDOR** — verify every `{id}` method |
| NurseDashboardService | 0 | 4 | High-risk — verify |
| PatientService | 6 | 12 | Mixed — CRUD safe, PDF helpers leak (SEC-3) |
| BillingService | 1 | 12 | **Mostly OK** — user-facing `updateStatus(id)` IS guarded (BillingService.java:171); most bare calls are internal on already-tenant-stamped rows |
| PharmacySaleService | 5 | 1 | **Safe** — locks + `findByIdAndHospitalId` |
| AppointmentService | 21 | 3 | Looks well-guarded |
| MasterData/Nurse/Lab/Radiology/Purchase/Supplier | ≥1 | 0 | No bare-id fetches — low risk |

**Conclusion of the sweep:** the IDOR is **systemic, not isolated** — it is a
missing convention, present wherever a service exposes an `{id}` detail/mutation
method (IPD, OPD confirmed; MRD, NurseDashboard flagged). Billing's money path
is, reassuringly, guarded. This confirms the structural-fix recommendation:
a tenant filter/interceptor is the only reliable remedy across ~40 services.

## 5. What was NOT runtime-tested (honest gap)
No live backend run (needs MySQL + Redis), so no real HTTP request/response,
WebSocket, Cloudinary upload, or PDF-render was exercised. The IDOR findings are
proven by code path, not by a live cross-tenant request. A focused integration
test that logs in as Hospital A and requests Hospital B's `ipd/{id}` would
convert SEC-1/2 from [CONFIRMED-by-code] to [CONFIRMED-by-execution] and belongs
in the regression suite.

---

## Production readiness by area (audited only)

| Area | Score | Note |
|---|---|---|
| Auth (password/JWT signing) | 75 | BCrypt good; default-secret fallback bad |
| Role authorization | 85 | Granular `@PreAuthorize`, near-complete |
| Multi-tenant isolation | 45 | Intent good, enforcement leaky (SEC-1/2/3) |
| Pharmacy counter | 88 | Locking + validation done right |
| IPD clinical/stock | 40 | Write-IDOR + unlocked stock deduction |
| Patient CRUD | 80 | Tenant-scoped; PDF helpers leak |

*Scores cover only what was inspected this pass; un-audited modules are unscored.*
