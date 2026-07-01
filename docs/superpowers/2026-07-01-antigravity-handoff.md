# HMS Implementation — Handoff to Antigravity

> **Purpose of this file.** A cold-start handoff so a new agent ("Antigravity") can continue
> the phased, NABH-compliant implementation of this Hospital Management System without any
> prior conversation context. It tells you: what's already done, the exact rules you must
> follow, the proven implementation pattern to copy, the detailed remaining Phase 0 sub-plans,
> and the Phase 1–7 program map. **Read Section 1 (Required Reading) first, then Section 4
> (Working Rules), then start at Section 5.1 (sub-plan 0.3).**

**Repo:** `e:\Projects\HOSPITAL` · **Working branch:** `phase-0-01-discharge-isolation`
(Phase 0 work is accumulating on this one branch; do not create new branches per sub-plan.)
**Main branch:** `main` · **Staging:** `staging`.
**Date of handoff:** 2026-07-01.

---

## 0. Standing user constraints (NON-NEGOTIABLE — apply to every task)

These were stated repeatedly by the product owner and override any convenience:

1. **"Do not harm our code base — work like a professional."** Additive, reversible changes only.
2. **"Make no bug."** Every change ships with tests; the full suite must stay green.
3. **"Maintain the integrity and security of this product."** Tenant isolation is mandatory and tested (see §4.3). This is multi-tenant clinical software — a cross-tenant leak or a wrong Early Warning Score is a safety incident, not a cosmetic bug.
4. **"Make this HMS easy to use."** Follow the product philosophy (tasks-over-modules, one clinical timeline, confirm-before-commit, auto-populate known data). See `docs/superpowers/product-philosophy.md`.

---

## 1. Required reading (read these files, in this order, before writing code)

| # | File | Why |
|---|---|---|
| 1 | `CLAUDE.md` (repo root) | Architecture, package map, API namespaces, roles, build commands, schema source-of-truth rule. |
| 2 | `docs/superpowers/product-philosophy.md` | The UX guardrails (Strynkix principles) every screen must honor. |
| 3 | `docs/superpowers/specs/2026-07-01-hms-implementation-roadmap.md` | **The master program plan.** Phases, dependency order, agent-orchestration model, engine catalog, acceptance gates. This handoff summarizes it, but the roadmap is the source of truth. |
| 4 | `docs/superpowers/audits/2026-07-01-hms-functional-audit.md` | The tenant-isolation / functional gaps audit that motivates Phase 0. |
| 5 | `docs/hms-blueprint/README.md` + the 41 form specs `docs/hms-blueprint/01-*.md … 41-*.md` | The reverse-engineered NABH form requirements. **Read only the specific forms a phase consumes** (each phase below names them) — do not read all 41 up front. |
| 6 | `docs/superpowers/plans/2026-07-01-phase-0-01-discharge-tenant-isolation.md` | **Worked example** of a completed sub-plan (the IDOR fix). Copy its structure and rigor. |
| 7 | `docs/superpowers/plans/2026-07-01-phase-0-02-vitalsigns-bp-split.md` | **Worked example #2** (dual-write + backfill migration). Copy its do-no-harm migration pattern. |

**For any specific sub-plan below, also read the actual source files it names before editing them** — file paths in this doc are accurate as of the handoff date but always ground against reality first.

---

## 2. System map (where things live)

Monorepo: Spring Boot backend + React/Vite frontend. Multi-tenant SaaS.

### Backend — `backend/src/main/java/com/hms/`
| Path | Contents |
|---|---|
| `entity/` | JPA entities. Key ones for Phase 0: `Patient.java`, `Doctor.java`, `Nurse.java`, `User.java`, `IpdAdmission.java`, `VitalSigns.java` (done), `DischargeSummary.java` (done), `NurseTask.java`, `DoctorOrder.java`, `NurseAssessment.java`. |
| `repository/` | Spring Data JPA repos (one per entity). |
| `service/hospital/` | Hospital-scoped business logic. Key: `IpdAdmissionService.java`, `NurseAssessmentService.java` (done), `NurseTaskService.java`, `CdssEvaluationService.java` (done — holds NEWS2 EWS), `MrdService.java`, `OtService.java`, `NurseService.java`. |
| `service/platform/` | Super-admin/platform logic. |
| `controller/hospital/` | REST under `/hospital/**`. |
| `controller/platform/` | REST under `/platform/**`. |
| `controller/publicapi/` | Unauthenticated `/api/public/**`. |
| `security/` | `JwtAuthenticationFilter`, `JwtUtil`, `SecurityContextHelper` (injected as field `securityHelper`; `getCurrentHospitalId()`, `getCurrentUserEmail()` are your tenant/identity accessors). |
| `config/` | `SecurityConfig` (role allowlists + `@PreAuthorize`), `DatabaseMigrationRunner.java` (**all startup migrations live here**), `WebSocketConfig`, Redis/CORS config. |
| `dto/` | Request/response DTOs, e.g. `EwsResultDTO.java`. |

### Frontend — `frontend/src/`
| Path | Contents |
|---|---|
| `pages/hospital/` | Per-role dashboards: `DoctorDashboard`, `NurseDashboard`, `ReceptionistDashboard`, `HospitalAdminDashboard`, `PharmacistDashboard`, `IpdDetails.jsx` (the patient clinical workspace), technician dashboards. |
| `components/` | Shared UI: `DataTable`, `ActionMenu`, `StatusBadge`, `ConfirmationModal`, `PageHeader`, nurse/`VitalsForm.jsx`, `NurseAssessmentForm.jsx`, `PatientClinicalRecord.jsx`, lab/radiology panels. |
| `services/` | Axios wrappers: `apiService.js` (interceptors, JWT injection, 401 redirect), `hospitalService.js`, `nurseService.js`, `authService.js`, `cdssService.js`. |
| `context/` | `ToastContext`. `hooks/` | `useWebSocket`, `useModule`. |

### Database
- **Canonical schema:** `setup/schema-full.sql` — every schema change MUST be mirrored here (per `CLAUDE.md`).
- **Runtime migrations:** `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`. Hibernate `ddl-auto=update` auto-creates new **nullable** columns; the runner handles idempotent backfills and guarded ALTERs.

---

## 3. What is already done (Phase 0.1 + 0.2, on the working branch)

Commits on `phase-0-01-discharge-isolation` (newest first, above the `ec28c07` WIP baseline):

**Sub-plan 0.1 — DischargeSummary tenant isolation (IDOR fix):**
- `b961b43` fix(security): reject cross-tenant plan/confirm discharge (IDOR)
- `b196464` feat(discharge): stamp hospital/patient/doctor on discharge summaries
- `f5a6bc1` chore(db): backfill discharge_summary tenant columns + mirror canonical schema
- **What it did:** Added fail-closed tenant guards in `IpdAdmissionService.planDischarge` and `confirmDischarge`; added nullable `hospital_id`/`patient_id`/`doctor_id` to `DischargeSummary`; idempotent backfill; schema mirror. All 7 TDD tasks green.

**Sub-plan 0.2 — VitalSigns structured BP split:**
- `6cbb62f` feat(vitals): add `bp_systolic`/`bp_diastolic` + `pain_score`/`weight`/`oxygen_support`/`remarks` to `VitalSigns` (nullable; kept legacy `blood_pressure` string)
- `575ed5e` feat(cdss): EWS scores from structured `bp_systolic`, falls back to parsing the legacy string for old rows
- `db33256` feat(vitals): `recordVitals` dual-writes structured BP + persists previously-dropped `respiratoryRate` (latent EWS bug fix) + new fields
- `a156547` chore(db): idempotent backfill of `vital_signs` structured BP + schema mirror
- `bb69a9e` fix(vitals): coerce blank numeric form fields to null in `recordVitals` helpers (prevents HTTP 500 on blank Resp. Rate/Temp — found in review)
- **Status:** Full backend suite green (`mvn test` exit 0), frontend build green, independent review verdict **APPROVE**.

**Both sub-plan design docs** are in `docs/superpowers/plans/` (see §1, rows 6–7) — study them; they are the template.

---

## 4. Working rules (how to build here)

### 4.1 Build & test commands
- Backend (run from `backend/`): full suite `mvn -q test`; single class `mvn -q -Dtest=ClassName test`; single method `mvn -q -Dtest=ClassName#method test`; compile only `mvn -q -DskipTests compile`. **Exit 0 = green** (the `-q` flag hides the surefire summary; trust the exit code).
- Frontend (run from `frontend/`): `npm run build` (runs `tsc` then `vite build`). Install with `npm install` if needed.
- Platform note: primary shell is **PowerShell** on Windows; a Bash tool is also available. Watch CRLF/LF — git will warn; harmless.

### 4.2 Do-no-harm migration pattern (proven in 0.1 and 0.2 — copy it exactly)
1. **Add columns as NULLABLE** on the JPA entity (`@Column(name=...)`, no `nullable=false`). `ddl-auto=update` creates them at startup. Never drop/rename a live column in the same change that adds its replacement.
2. **Dual-write / backfill, don't break readers.** If replacing a representation (e.g. String → INT), keep the old column populated and readable for at least one release; add a fallback read path for legacy rows.
3. **Backfill in `DatabaseMigrationRunner`**, following the existing method style: wrap in `try/catch`; guard with an `information_schema.COLUMNS` existence check; early-return when there's nothing to do (idempotent — safe to run every boot); `log.info` the row count on apply, `log.warn` on skip. Use the injected `jdbcTemplate` and `log`.
4. **Mirror every schema change in `setup/schema-full.sql`** — add the columns to the `CREATE TABLE` and include the equivalent `ALTER TABLE` as a comment block. Match the file's existing indent/backtick style (verify by reading it).
5. **Only enforce `NOT NULL` in a later, separate migration** once data is backfilled — never in the additive change.

### 4.3 Tenant isolation is a TEST, not a hope (mandatory)
- Every hospital-scoped entity carries `hospital_id`. Get the caller's tenant via `securityHelper.getCurrentHospitalId()`.
- **IDOR guard pattern** (fail-closed) immediately after any `repository.findById(...)` that will be acted on:
  ```java
  Long currentHospitalId = securityHelper.getCurrentHospitalId();
  if (currentHospitalId == null) {
      throw new UnauthorizedException("Hospital ID not found in context");
  }
  if (entity.getHospitalId() == null || !entity.getHospitalId().equals(currentHospitalId)) {
      throw new org.springframework.security.access.AccessDeniedException("Access denied: Tenant mismatch");
  }
  ```
- Every new `{id}` endpoint gets a test proving a cross-tenant read is rejected (see `IpdAdmissionServiceTest` cross-tenant tests added in 0.1 for the template).

### 4.4 Test conventions
JUnit 5 (`@ExtendWith(MockitoExtension.class)`), Mockito (`@Mock`, `@InjectMocks`, `ArgumentCaptor`, `verify(..., never())`, `lenient()` for stubs not asserted), AssertJ (`assertThat`, `assertThatThrownBy`). Tests live in `backend/src/test/java/com/hms/...`. Write the failing test first (TDD), watch it fail, implement minimally, watch it pass, commit.

### 4.5 Commit hygiene
Small, frequent, conventional commits (`feat(scope):`, `fix(scope):`, `chore(db):`, `docs(plan):`). One logical change per commit. End commit messages with:
```
Co-Authored-By: <your agent signature>
```
Do NOT push or open PRs unless the user asks. Do not merge to `staging`/`main` yourself — one human/reviewer gate before merge (roadmap §0.1 rule 6).

### 4.6 The proven execution pattern (recommended)
For each sub-plan: (1) **ground** — read the real source files it touches; (2) **write the plan doc** to `docs/superpowers/plans/YYYY-MM-DD-phase-0-0N-<slug>.md` with bite-sized TDD tasks and complete code (no placeholders); (3) **execute task-by-task** (TDD: red → green → commit); (4) run the **green-build gate** (`mvn -q test` + `npm run build`); (5) do an **independent review** pass (fresh eyes over the whole diff) before declaring done. This is exactly how 0.1 and 0.2 were built.

---

## 5. Remaining Phase 0 sub-plans (DO THESE NEXT, in this order)

Phase 0 is **sequential** (shared code — no parallelism). Each sub-plan below is one plan doc + one build cycle on the current branch. Filenames follow the established convention:
`docs/superpowers/plans/2026-07-01-phase-0-0N-<slug>.md`.

> For each: the roadmap work-item is in §3 "Phase 0" table of the roadmap. Ground against the named source files first — verify field names, injected dependencies, and current shape before writing the plan.

### 5.1 Sub-plan 0.3 — NurseTask decoupling
- **Plan file:** `docs/superpowers/plans/2026-07-01-phase-0-03-nursetask-decoupling.md`
- **Target files:** `entity/NurseTask.java`, `service/hospital/NurseTaskService.java` (+ its test `backend/src/test/java/com/hms/service/NurseTaskServiceTest.java`), `config/DatabaseMigrationRunner.java`, `setup/schema-full.sql`. Read `NurseTask.java` and `NurseTaskService.java` first.
- **Goal:** Decouple nurse tasks from doctor orders so tasks can also originate from risk protocols and nursing judgment, and capture why a task was missed.
- **Changes (all additive/nullable):**
  - Make `doctor_order_id` **nullable** on `NurseTask` (today a task is likely hard-wired to a `DoctorOrder`). A task no longer requires a doctor order.
  - Add `source` (enum/string: `DOCTOR_ORDER` | `RISK_PROTOCOL` | `NURSING`) — where the task came from. Backfill existing rows to `DOCTOR_ORDER` (they all have a `doctor_order_id` today).
  - Add `task_type` (string; free-form clinical category, e.g. MEDICATION / OBSERVATION / MOBILITY — read the blueprint Form 08 for the vocabulary).
  - Add `missed_reason` (nullable text) — populated when a task is marked not-done (the MAR "Not Given" reason already exists in the UI, see commit `c814930`; wire it through if not persisted).
- **Blueprint to read:** `docs/hms-blueprint/08-nursing-daily-progress.md` (MAR/nursing tasks).
- **Do-no-harm:** nullable migration; backfill `source='DOCTOR_ORDER'` where `doctor_order_id IS NOT NULL`; keep every existing task-creation call working (they set a doctor order → source defaults correctly).
- **Tests:** creating a task with a null `doctor_order_id` + `source=NURSING` succeeds; existing doctor-order task path unchanged; tenant isolation on any `{id}` task mutation still enforced; `missed_reason` persists.

### 5.2 Sub-plan 0.4 — Patient model additions + record-merge
- **Plan file:** `docs/superpowers/plans/2026-07-01-phase-0-04-patient-model.md`
- **Target files:** `entity/Patient.java`, `repository/PatientRepository.java`, the patient service/controller (find via grep), `config/DatabaseMigrationRunner.java`, `setup/schema-full.sql`. Frontend registration form (`pages/hospital/` + patient registration components) — additive fields only, keep the 4-required-field progressive-disclosure UX (commit `8d07e1f`).
- **Goal:** Bring the patient record up to NABH identity requirements and support merging duplicate/temporary records.
- **Changes (all nullable, backfilled):**
  - `date_of_birth` (DATE) — backfill both directions with existing `age` where possible (`dob`↔`age`); keep `age` readable.
  - `guardian_name`, `guardian_relationship`, `preferred_language`, `blood_group`, `uhid` (unique hospital patient ID — generate for existing rows in a guarded backfill), `is_temporary`, `is_unknown` (booleans, default false — for ER "unknown" arrivals; consumed by Phase 4 emergency).
  - **Record-merge capability:** an additive service method to merge a temporary/duplicate patient into a canonical one (re-point child FKs: appointments, OPD, IPD, bills, orders → survivor; mark loser merged). Additive only — do not delete data destructively; soft-mark the merged-away record.
- **Blueprint to read:** `docs/hms-blueprint/07-admission-initial-assessment.md` (identity fields) and `12-emergency-initial-assessment.md` (unknown/temporary patient). `38-blood-bank` needs `blood_group` later.
- **Tests:** new fields persist; `dob`/`age` backfill is consistent; `uhid` is unique per hospital; merge re-points FKs and is tenant-scoped (cannot merge across hospitals); merge is idempotent.

### 5.3 Sub-plan 0.5 — Canonical staff identity
- **Plan file:** `docs/superpowers/plans/2026-07-01-phase-0-05-staff-identity.md`
- **Target files:** `entity/User.java`, `entity/Doctor.java`, `entity/Nurse.java` (+ repos), migration runner, schema. Read all three entities first to see how they currently relate (likely email-linked, not FK-linked).
- **Goal:** Unify the three staff representations under one identity via `user_id` FKs, and add the capacity flags the clinical phases need — **without deleting the existing tables** (roadmap §1.1–1.3).
- **Changes (additive):**
  - Add nullable `user_id` FK on `Doctor` and `Nurse` linking to `User`; backfill by matching email. Keep existing tables and their current columns.
  - Add `department`, `designation`, `is_trainer` to the canonical staff record.
  - **Capacity flags on `Doctor`:** `is_anaesthetist`, `is_surgeon`, `is_pathologist`, `is_radiologist`, `is_intensivist`, `is_cmo` (`is_single_doctor` already exists). On `Nurse`: `is_scrub`, `is_ot`, `is_pacu`, `is_icu`. All boolean, default false.
  - Rationale (roadmap §1.3): a hospital's one anaesthesiologist is a `DOCTOR` with `is_anaesthetist=true`, not a new login class — keeps auth simple; consumed by Phases 3/5.
- **Tests:** FK backfill matches the right user; flags default false and persist; no existing staff query breaks (the old email-based lookups still work).

### 5.4 Sub-plan 0.6 — Role framework
- **Plan file:** `docs/superpowers/plans/2026-07-01-phase-0-06-role-framework.md`
- **Target files:** the role enum (grep for the existing enum containing `SUPER_ADMIN`, `HOSPITAL_ADMIN`, `DOCTOR`, `RECEPTIONIST`, `NURSE`, `PHARMACIST`, `LAB_TECHNICIAN`, `RADIOLOGY_TECHNICIAN`), `config/SecurityConfig.java`, and a central `@PreAuthorize` matrix. Read `SecurityConfig` first.
- **Goal:** Establish the extensible least-privilege role framework and a **central, tested authorization matrix** — but add roles **only as later phases need them** (do not add all at once; YAGNI).
- **Changes (additive):**
  - Extend the role enum with the roles the roadmap lists (§1.2): `MRD_OFFICER`, `QUALITY_OFFICER`, `DEPARTMENT_HEAD`, `STORE_KEEPER`, `PURCHASE_OFFICER`, `BIOMEDICAL_ENGINEER`, `CSSD_TECHNICIAN`, `BLOOD_BANK_TECHNICIAN`, `HOUSEKEEPING`, `ACCOUNTANT`, `HR_EXECUTIVE`, `IT_ADMIN` — **but for Phase 0 add only the framework + enum plumbing; wire specific roles when their phase arrives.** At minimum add `MRD_OFFICER` (Phase 4) and `QUALITY_OFFICER` (Phase 3) plumbing since they're near.
  - A documented `@PreAuthorize` role matrix + tests proving each namespace (`/platform/**`, `/hospital/**`) rejects the wrong role.
- **Tests:** role-permission tests (right role allowed, wrong role 403); existing roles unaffected.

### 5.5 Sub-plan 0.7 — Admission decoupling + monitoring_vitals table
- **Plan file:** `docs/superpowers/plans/2026-07-01-phase-0-07-admission-monitoring.md`
- **Target files:** `service/hospital/IpdAdmissionService.java` (has `admitFromOpd`; add `admitFromEmergency`), a new `entity/MonitoringVitals.java` + repo, migration runner, schema.
- **Goal:** (a) allow admitting a patient who did NOT come through OPD (needed by Phase 4 emergency), without breaking the existing OPD path; (b) create the shared intra-op/PACU vitals table Forms 19/20 will use.
- **Changes (additive):**
  - New `admitFromEmergency(...)` method on `IpdAdmissionService` — a parallel entry path that does not require an OPD record. **Leave `admitFromOpd` fully intact.** Begin decoupling the hard-wired OPD dependency only additively.
  - New `monitoring_vitals` table/entity with a `context` discriminator (`INTRAOP` | `PACU`) so Forms 19 (Anaesthesia) and 20 (PACU) share one structure. New table — no impact on existing data.
  - **Carry forward the deferred IDOR guard:** `IpdAdmissionService.addIpdFollowup` (~line 526) has the same unguarded `findById`-then-act shape flagged in 0.1; add the §4.3 tenant guard + a cross-tenant test here.
- **Blueprint to read:** `docs/hms-blueprint/12-emergency-initial-assessment.md`, `19-anaesthesia-record.md`, `20-pacu-recovery-record.md`.
- **Tests:** emergency admission creates a valid IPD without an OPD; OPD admission unchanged; `monitoring_vitals` persists both contexts; `addIpdFollowup` rejects cross-tenant.

### 5.6 Sub-plan 0.8 — Signature + Notification scaffolds
- **Plan file:** `docs/superpowers/plans/2026-07-01-phase-0-08-signature-notification.md`
- **Target files:** new `service/.../SignatureAndDocumentService.java`, new `service/.../NotificationService.java` facade, tables for signatures/document versions, migration + schema. Read existing `WebSocketConfig`, any `WhatsAppService`, and `PdfService` first.
- **Goal:** Scaffold the two lowest shared engines every later consent/clinical-doc/print feature depends on (roadmap §2).
- **Changes (new, additive):**
  - `SignatureAndDocumentService` + a `signature` table with polymorphic slots (signer role/name/relationship, timestamp, document ref, signature image/hash) — supports staff AND non-staff signers (guardian/witness/interpreter as signature slots, roadmap §1.4). Document versioning.
  - `NotificationService` facade over the existing WebSocket + WhatsApp infrastructure (formalize, don't rebuild — `WebSocketConfig` and WhatsApp services already exist; see commits around `baba960`).
- **Tests:** a signature slot persists and is tenant-scoped; document version increments; notification facade routes to WS/WhatsApp (mock the transports).

### 5.7 Phase 0 acceptance gate (before declaring Phase 0 done)
Full regression green (`mvn -q test` + `npm run build`) · every changed entity has a migration + a tenant-isolation test · `calculateEws` still scores correctly on migrated data · **no live endpoint contract changed** · all sub-plan docs committed. **Nothing in Phase 1 starts until Phase 0 merges** (roadmap §Phase 0 gate). At that point, get the human review gate, then merge the branch.

### 5.8 Deferred items to carry forward (don't lose these)
- **Dual-write BP conflict:** if a client sends both structured `bpSystolic/bpDiastolic` AND a conflicting legacy `bloodPressure` string, structured wins for scoring/display but the typed string is stored verbatim. Undocumented + untested — add a comment stating structured is authoritative and a test for the conflict case (fold into 0.3 or a cleanup commit).
- **Two BP parsers exist:** `NurseAssessmentService.parseBloodPressure` (int[]/-1 sentinel) and `CdssEvaluationService.parseSystolic` (Integer/null). Consider a shared util in a later cleanup (low priority).
- **Frontend structured BP input:** the nurse `VitalsForm.jsx` still posts BP as free text; the backend accepts both, so adding a two-field systolic/diastolic input later is non-breaking. Deferred to a UX task.

---

## 6. Phases 1–7 program map (after Phase 0 merges)

This is the summary; the authoritative detail (modules, parallel groups, per-phase acceptance gates) is in the roadmap §3. **Order is dependency-driven — do not reorder.** From Phase 1 on, modules within a phase may run in **parallel** but only if their tables/services are disjoint; anything touching a shared entity is serialized through the phase's "foundation" step (roadmap §0.3). Each module = its own plan→TDD→review→merge cycle.

**Golden rule for every module (roadmap §0.4 Definition of Done):** spec's sections satisfied · additive migration + entity updated · tenant-isolation test green · unit+integration+role-permission tests green · frontend wired to the relevant role dashboard (reuse `DataTable`/`ActionMenu`/`PageHeader`/`useWebSocket`) · PDF/print template if the form has one · audit-log calls present · reviewed + merged · demoable end-to-end.

### Phase 1 — Admission Clinical Core (⇉ parallel)
- **Prereqs:** Phase 0. **Consumes engines:** Consent, Assessment, Risk, Vitals (done in 0.2), MAR.
- **Group A (ordered):** Form 05 General Consent → *defines* the Consent Engine → then Form 01 Blood Consent as a consent type.
- **Group B (parallel, table-disjoint):** Form 07 Admission Initial Assessment (+ EMR history backbone) · Form 06 Vulnerability/Risk Assessment · Form 09 TPR/Vitals · Form 10 Intake/Output Chart · Form 08 Nursing Daily + MAR.
- **Blueprint to read:** `docs/hms-blueprint/05,01,07,06,09,10,08-*.md`.
- **Gate:** a patient can be admitted, consented, assessed, risk-scored, and have vitals/fluids/MAR recorded end-to-end on the Doctor + Nurse dashboards; Consent Engine proven with ≥2 types.

### Phase 2 — Clinical Progress & Discharge (⇉→⛓)
- **Prereqs:** Phase 1. **Consumes:** Clinical Documentation Engine, Discharge Engine.
- ⛓ Evolve `DoctorRound` into the Clinical Documentation Engine (sign/amend lifecycle, `assessment_type`) — **one** engine behind both Form 11 (reassessment) and Form 13 (progress notes). Build once.
- ⇉ then: `progress_order` smart-linking parser (confirm-before-commit) · `patient_referral` workflow.
- ⛓ Form 14 Discharge Summary — extend the (now tenant-isolated) `DischargeSummary` into the auto-gen Discharge Engine over the existing `/plan-discharge`→`/confirm-discharge` flow.
- **Blueprint:** `11,13,14-*.md`. **Gate:** unified patient timeline renders rounds+reassessments+progress; discharge auto-assembles + prints; isolation test on `DischargeSummary`.

### Phase 3 — Operation Theatre Suite (⇉, largest — 12 forms)
- **Prereqs:** Phases 1–2. **Builds:** OT Safety Orchestration, OT Execution, AIMS, PACU, Handover, Instrument/Implant safety, Incident Reporting.
- Pipeline sub-order: (1) ⛓ Pre-op gate: Form 15 PAC + Form 16 Surgical Consent → Surgical Readiness gate that finally gates the currently-ungated `OtService.scheduleBooking`; (2) ⛓ Form 17 WHO checklist (extend `OtChecklist`); (3) ⇉ Execution: Form 18 Operation Record · 19 Anaesthesia/AIMS · 23 Instrument Count · 24 Implant; (4) ⛓ Recovery: Form 20 PACU (Aldrete-gated) → 21 Post-op Orders → 22 Handover; (5) ⇉ Form 25 OT Register · 26 OT Readiness.
- **Blueprint:** `15–26-*.md`. **Gate:** full surgical journey with every gate enforced/audited; instrument-count mismatch blocks sign-out.

### Phase 4 — Emergency & Medical Records (⇉)
- **Prereqs:** Phase 0 (admission decoupling + temporary-patient identity from 0.4/0.7), Phase 2.
- Form 12 Emergency (EIS + MLC) using `admitFromEmergency` · Form 02 MRD verification (completeness layer on `MrdService`) · Form 31 MRD/EMR timeline & coding.
- **Blueprint:** `12,02,31-*.md`. **Gate:** unknown ER arrival treated-before-registered, triaged, assessed, admitted; discharged file completeness-checked before archival.

### Phase 5 — Diagnostics & Revenue hardening (⇉, mostly EXTEND existing code)
- **Prereqs:** Phase 1. **Note:** LIS/RIS/PMS/Billing already exist — this HARDENS them.
- Form 27 LIS (add `VERIFIED`/`RELEASED` + pathologist gate + `sample_tracker`) · Form 28 RIS/PACS · Form 29 PMS (add `narcotic_log` + double-sign + FEFO) · Form 30 Billing/RCM (TPA, advances, split-pay).
- **Blueprint:** `27,28,29,30-*.md`. **Gate:** lab result needs pathologist verification before release; controlled-drug dispense needs dual signature; billing captures OT/implant/pharmacy charges.

### Phase 6 — Supply chain & Blood (⇉)
- **Prereqs:** Phase 3, Phase 5.
- Form 33 Inventory/Store · 34 Purchase · 35 CSSD (gates OT tray readiness) · 36 Biomedical (gates OT device calibration) · 38 Blood Bank (needs `Patient.blood_group` from 0.4) · Form 03 Patient Feedback (public tokenized infra).
- **Blueprint:** `33,34,35,36,38,03-*.md`. **Gate:** OT readiness verifies CSSD sterility + biomedical calibration + blood availability from live modules.

### Phase 7 — Workforce, Facility, Experience & Interop (⇉, support layer)
- **Prereqs:** Phase 0 (staff identity), most clinical phases.
- Form 04/39 HR & LMS & roster/payroll · Form 37 Housekeeping/Facility · Form 32 Admin/MIS dashboard · Form 40 Patient Portal (public identity) · Form 41 Integration (HL7/FHIR/DICOM).
- **Blueprint:** `04,39,37,32,40,41-*.md`. **Gate:** admin sees cross-module KPIs; patients self-serve appointments + released reports; external systems exchange ADT/ORU.

### Dependency picture (roadmap §4)
```
Phase 0 ──► Phase 1 ──► Phase 2 ──► Phase 3 ──► Phase 6
   │            └──────► Phase 5
   ├──► Phase 4
   └──► Phase 7
```

---

## 7. Quick-start checklist for Antigravity

1. Read the §1 required-reading files (roadmap + product philosophy + `CLAUDE.md` + the two worked-example plans).
2. Confirm you're on branch `phase-0-01-discharge-isolation` with a clean tree (`git status`).
3. Run `cd backend && mvn -q test` and `cd frontend && npm run build` to confirm you inherit a green baseline.
4. Start **sub-plan 0.3** (§5.1): read `NurseTask.java` + `NurseTaskService.java`, write the plan doc, execute TDD task-by-task, run the green-build gate, review, commit.
5. Proceed through 0.4 → 0.5 → 0.6 → 0.7 → 0.8 in order. Honor §0 constraints and §4 rules on every task.
6. At the Phase 0 gate (§5.7), stop for the human review before merging; then Phase 1 opens with a LEAD/architect pass per roadmap §0.3.

**When in doubt:** additive over destructive, test over hope, ground against real source before editing, and never break a live endpoint or leak across tenants.
