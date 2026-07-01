# HMS Implementation Roadmap — From 41 Specs to a Production NABH System

| | |
|---|---|
| **Status** | Draft roadmap (program-level) |
| **Date** | 2026-07-01 |
| **Owner** | kartiklomte |
| **Source of truth** | [`docs/hms-blueprint/`](../../hms-blueprint/) — 41 grounded form specs + [`README.md`](../../hms-blueprint/README.md) (gaps, engine catalog, role gaps) |
| **Design guardrails** | [`docs/superpowers/product-philosophy.md`](../product-philosophy.md) · tenant-isolation audit ([`audits/2026-07-01-hms-functional-audit.md`](../audits/2026-07-01-hms-functional-audit.md)) |
| **Decisions locked** | (1) **Clinical spine first**, support modules later. (2) **Parallel-per-module agents, gated**; foundations sequential. (3) This doc is roadmap-only; each phase spawns its own plan→build cycle. |

> **What this document is.** A dependency-ordered program plan that turns the 41 blueprint
> specs + the foundational/role gaps into buildable **phases**. It is **not** an implementation
> plan — each phase (or module) gets its own `writing-plans` plan and TDD build cycle when we
> reach it. This roadmap defines *what order*, *who builds it (which agents, in parallel or
> sequential)*, *what "done" means (acceptance gates)*, and *the guardrails that keep the
> existing codebase safe*.

---

## 0. Governing principles (apply to every phase)

### 0.1 Do-no-harm guardrails (non-negotiable)
1. **Additive, reversible migrations only.** New columns land **nullable**, are backfilled, and only then made `NOT NULL` in a follow-up migration. Never drop/rename a live column in the same change that adds its replacement. Every schema change ships as a numbered SQL migration **and** the matching JPA entity edit (per `CLAUDE.md`: `setup/schema-full.sql` is canonical).
2. **No breaking changes to live endpoints.** Existing routes keep their contract. New behaviour goes behind **new** endpoints or **additive** fields. Deprecate, don't delete.
3. **Tenant isolation is a test, not a hope.** Every new tenant table carries `hospital_id`; every `{id}` endpoint gets an ownership-check test that proves a cross-tenant read returns 403/404. This directly closes the audit-class bug (e.g. `DischargeSummary`).
4. **Feature-flag risky surfaces.** New modules mount behind a per-hospital module toggle (extend `HospitalSetting`) so a half-built module never blocks a live hospital.
5. **Green build gate.** No module merges unless `mvn test` (backend) and `npm run build` (frontend) pass, plus the module's own tests. Foundations additionally require the **full** regression suite green.
6. **One reviewer between agent and `main`.** Every module branch is reviewed (`requesting-code-review` / `receiving-code-review`) before merge. No agent self-merges to `staging`/`main`.

### 0.2 Usability principles (from `product-philosophy.md`)
- **Tasks over modules.** Each role sees a *worklist* ("what do I do next"), not a menu of tables. Reuse the existing per-role dashboards (`DoctorDashboard`, `NurseDashboard`, etc.) as the home surface; new modules add cards/queues to them rather than new top-level nav where possible.
- **One clinical timeline.** Progress notes, reassessments, OT events, PACU, handover all render on a single patient timeline (the `DoctorRound`-centred Clinical Documentation Engine), never as disconnected screens.
- **Confirm-before-commit for smart features.** Every AI/auto action (smart-linking Plan parser, auto-orders, early-warning) proposes; a human commits. No silent writes.
- **Cognitive-load rules.** Auto-populate anything the system already knows (patient/surgery/ASA/vitals). Red-banner allergies and critical alerts. Gate dangerous actions (incision, transfer, discharge) behind readiness checks the user can see.

### 0.3 Agent orchestration model (decision #2)
```
Per phase:
  1. LEAD (architect agent, Plan type) — reads the phase's specs + this roadmap,
     writes the phase implementation plan via `writing-plans`, identifies the
     parallelizable module set + shared-code touch points.  [sequential, 1 agent]
  2. FOUNDATION work inside the phase (shared entities/security/migrations)
     — done SEQUENTIALLY by one agent, merged first, because it touches shared code.
  3. MODULE agents — one per independent module, each in its OWN git worktree
     (`using-git-worktrees`), each doing `test-driven-development`, running in
     parallel (`dispatching-parallel-agents` / `subagent-driven-development`).
  4. REVIEW gate — reviewer agent per module branch (`requesting-code-review`);
     author agent applies fixes (`receiving-code-review`).
  5. INTEGRATE — merge reviewed branches one at a time into the phase branch;
     run full regression; `verification-before-completion` before closing the phase.
```
**Parallelism rule:** two module agents may run concurrently **only if** their spec's
"Creates" tables and touched services are disjoint. Anything that edits a shared entity
(`Patient`, `IpdAdmission`, `User`, security, `OtBooking`, `OtChecklist`, `NurseTask`)
is serialized through the phase's foundation step. This is why foundations are sequential.

### 0.4 Definition of Done (every module)
Spec's 16 sections satisfied · migrations additive + entity updated · tenant-isolation test green ·
unit + integration tests green · role-permission tests green · frontend wired to a role dashboard ·
PDF/print template if the spec has one · audit-log calls present · reviewed + merged · demoable end-to-end.

---

## 1. Role & identity model (resolved once, consumed everywhere)

This is the "role gaps" resolution the whole system depends on. Built in **Phase 0**, referenced by every later phase.

### 1.1 Three identity mechanisms
| Mechanism | Use for | Implementation |
|---|---|---|
| **First-class role** (JWT `role`) | Distinct least-privilege job functions | Add to the role enum + `@PreAuthorize` |
| **Capacity flag** (boolean on staff record) | Specializations *within* a role | Column on the canonical employee record |
| **Portal identity** (separate) | Patients/guardians (non-staff) | Portal account table, not a staff `User` |

### 1.2 New first-class roles (least-privilege)
Existing: `SUPER_ADMIN`, `HOSPITAL_ADMIN`, `DOCTOR`, `RECEPTIONIST`, `NURSE`, `PHARMACIST`, `LAB_TECHNICIAN`, `RADIOLOGY_TECHNICIAN`.
Add, as clinical/support phases require them (not all at once):
`MRD_OFFICER` · `QUALITY_OFFICER` · `HR_EXECUTIVE` · `DEPARTMENT_HEAD` · `STORE_KEEPER` · `PURCHASE_OFFICER` · `BIOMEDICAL_ENGINEER` · `CSSD_TECHNICIAN` · `BLOOD_BANK_TECHNICIAN` · `HOUSEKEEPING` · `ACCOUNTANT` (cashier = capacity) · `IT_ADMIN` (integration).

### 1.3 Capacity flags (on the canonical employee record)
On `DOCTOR`: `is_anaesthetist`, `is_surgeon`, `is_pathologist`, `is_radiologist`, `is_intensivist` (ICU), `is_cmo` (casualty), `is_trainer`, `is_single_doctor` (exists).
On `NURSE`: `is_scrub`, `is_ot`, `is_pacu`, `is_icu`.
Rationale: a hospital's one anaesthesiologist is a `DOCTOR` with `is_anaesthetist=true`, not a separate login class — keeps auth simple, matches specs 15/19/20.

### 1.4 Non-staff actors
Patient, Guardian, Witness, Interpreter, Relative are **not** staff roles. Guardian/witness/interpreter are captured as **signature slots** by the Digital Signature Engine (Phase 1). Patient/guardian portal logins arrive only in the Patient Portal phase (Form 40).

---

## 2. Shared engines (build once, in dependency order)

The architecture converges on ~14 reusable engines. Building these *before* the forms that consume them is the core sequencing insight. Status from the blueprint engine catalog:

| Engine | Built in | Notes |
|---|---|---|
| Digital Signature + Document/versioning service | Phase 0 | Underpins every consent/clinical doc/print |
| Notification service (WebSocket + WhatsApp) | Phase 0 (wire) | `WebSocketConfig` + `WhatsAppService` exist — formalize a `NotificationService` facade |
| Document Generation (PDF) | exists | `PdfService` + Thymeleaf — add templates per form |
| Consent Engine | Phase 1 | `patient_consent` + `*_consent_detail`; types GENERAL/BLOOD/SURGERY (Forms 05/01/16) |
| Assessment Engine + EMR history backbone | Phase 1 | `clinical_assessment` + patient-scoped history tables (Form 07) |
| Risk Assessment Engine | Phase 1 | `clinical-risk-engine.md` (Form 06); EWS already in CDSS |
| Vitals & Monitoring Engine | Phase 1 | `VitalSigns` (BP split) + unified `monitoring_vitals` (INTRAOP/PACU) |
| Medication Administration (MAR) | Phase 1 | `NurseTask` (+ `missed_reason`, nullable order link) — Form 08 |
| Clinical Documentation Engine | Phase 2 | evolve `DoctorRound` (sign/amend lifecycle) — Forms 11/13 |
| Discharge Engine | Phase 2 | extend `DischargeSummary` (+ isolation fix) — Form 14 |
| OT Safety Orchestration (Surgical Readiness) | Phase 3 | gates `OtService.scheduleBooking`/sign-in — Forms 15/16/17 |
| OT Execution + AIMS + PACU | Phase 3 | `operation_record`, `anaesthesia_record`, `pacu_record` — Forms 18/19/20 |
| Clinical Handover Engine | Phase 3 | reuse existing nurse shift-handover pattern — Form 22 |
| Incident Reporting / Quality Engine | Phase 3 | complication → review — Form 18 BR-6; feeds Form 03 |
| MRD verification + DMS | Phase 4 | `MrdService` + completeness layer — Form 02 |
| Public tokenized-link infra | Phase 6 | `/api/public` single-use tokens — Form 03; reused by Portal |

---

## 3. Phases

Legend: **⛓ sequential** (shared code, one agent) · **⇉ parallel** (independent module agents) · each phase ends at an **acceptance gate**.

### Phase 0 — Foundations ⛓ (no new user-facing modules; everything downstream depends on this)
**Objective:** fix the data-model, identity, and security gaps the blueprint surfaced, and scaffold the two lowest engines. All sequential — this is shared code.

| Work item | What | Guardrail |
|---|---|---|
| **Patient model** | add `date_of_birth`, `guardian_name`, `guardian_relationship`, `preferred_language`, `blood_group`, `uhid`, `is_temporary`/`is_unknown` + **record-merge** capability | nullable→backfill `age`↔`dob`; merge is additive |
| **Canonical staff identity** | unify `User`/`Doctor`/`Nurse` via `user_id` FKs; add `department`, `designation`, `is_trainer` + the §1.3 capacity flags | keep existing tables; add links, don't delete |
| **Role framework** | extend role enum with the §1.2 roles *as needed per phase*; central `@PreAuthorize` matrix + tests | additive |
| **VitalSigns** | split `blood_pressure` String → `bp_systolic`/`bp_diastolic` INT; add `pain_score`, `weight`, `oxygen_support`, `remarks`; migrate `calculateEws` off `parseSystolic` | backfill string→ints; keep string readable one release |
| **DischargeSummary isolation fix** | add `hospital_id`/`patient_id`/`doctor_id` + ownership checks (**IDOR fix**) | ships with a cross-tenant test |
| **NurseTask decoupling** | `doctor_order_id` nullable + `source`/`task_type` (DOCTOR_ORDER/RISK_PROTOCOL/NURSING) + `missed_reason` | nullable migration |
| **Admission decoupling** | `admitFromEmergency(...)` path; begin decoupling `admitFromOpd` hard-wire | new method, old path intact |
| **Monitoring vitals table** | one `monitoring_vitals` (context INTRAOP/PACU) for Forms 19/20 to share | new table |
| **Signature + Notification scaffolds** | `signature-and-document-service` + `NotificationService` facade over existing WS/WhatsApp | new services |

**Acceptance gate:** full regression green · every changed entity has a migration + isolation test · `calculateEws` still scores correctly on migrated data · no live endpoint contract changed. **Nothing else starts until this merges.**

---

### Phase 1 — Admission Clinical Core ⇉ (the "front half" of an inpatient stay)
**Prereqs:** Phase 0. **Consumes:** Consent, Assessment, Risk, Vitals, MAR engines.
**Modules (parallel groups):**
- **Group A (engine-defining, some ordering):** Form 05 General Consent → *defines* Consent Engine → then Form 01 Blood Consent (type) in parallel with Form 16 later. ⛓ within group.
- **Group B ⇉:** Form 07 Admission Initial Assessment (+ EMR history backbone) · Form 06 Vulnerability/Risk Assessment · Form 09 TPR/Vitals · Form 10 I/O Chart · Form 08 Nursing Daily + MAR.
  These touch different tables (assessment / risk / vitals / fluid / nurse-task) → run concurrently.
**Acceptance gate:** a patient can be admitted, consented, assessed, risk-scored, and have vitals/fluids/MAR recorded — end-to-end on the Doctor + Nurse dashboards. Consent Engine proven with ≥2 types.

---

### Phase 2 — Clinical Progress & Discharge ⇉→⛓
**Prereqs:** Phase 1 (assessment + vitals feed these). **Consumes:** Clinical Documentation Engine, Discharge Engine.
**Modules:**
- ⛓ Evolve `DoctorRound` → Clinical Documentation Engine (sign/amend lifecycle, `assessment_type`) — this is **one** engine behind **both** Form 11 (reassessment) and Form 13 (progress notes). Build once.
- ⇉ then: `progress_order` smart-linking parser (confirm-before-commit) · `patient_referral` table/workflow.
- ⛓ Form 14 Discharge Summary — extend the thin entity into the auto-gen Discharge Engine (uses existing `/plan-discharge`→`/confirm-discharge` flow).
**Acceptance gate:** unified patient timeline renders rounds+reassessments+progress; discharge auto-assembles from orders/meds/investigations and prints; isolation test on the fixed `DischargeSummary`.

---

### Phase 3 — Operation Theatre Suite ⇉ (the largest phase; 12 forms, tightly coupled)
**Prereqs:** Phases 1–2 (consent, assessment, vitals, meds). **Consumes/builds:** OT Safety Orchestration, OT Execution, AIMS, PACU, Handover, Instrument Safety, Implant Lifecycle, Incident Reporting.
**Sub-sequencing (OT is a pipeline, so partial ordering):**
1. ⛓ **Pre-op gate:** Form 15 PAC + Form 16 Surgical Consent (Consent type) → build the **Surgical Readiness** gate that finally gates the currently-**ungated** `OtService.scheduleBooking`.
2. ⛓ **Safety gate:** Form 17 WHO checklist — extend `OtChecklist` with `ot_checklist_items` + sign-in auto-verification + count safety. (Phase gates already exist — extend, don't rebuild.)
3. ⇉ **Execution set** (once a case can reach the table): Form 18 Operation Record (hub) · Form 19 Anaesthesia Record/AIMS · Form 23 Instrument Count · Form 24 Implant Record — parallel where table-disjoint (op record vs anaesthesia vs count vs implant).
4. ⛓ **Recovery gate:** Form 20 PACU (owns Aldrete; gated by Form 19 completion) → Form 21 Post-op Orders → Form 22 Handover.
5. ⇉ **Registers/readiness:** Form 25 OT Register · Form 26 OT Readiness (gates `updateStatus`).
   **Genuinely-new sub-modules born here:** Incident Reporting (Form 18 BR-6), Implant traceability, "Anaesthesia Started" OT sub-state, Aldrete scoring.
**Acceptance gate:** a full surgical journey — schedule (only if READY) → WHO sign-in/time-out/sign-out → operation record → anaesthesia chart → PACU (transfer only if Aldrete≥min) → handover — runs end-to-end with every gate enforced and audited. Instrument-count mismatch blocks sign-out.

---

### Phase 4 — Emergency & Medical Records ⇉
**Prereqs:** Phase 0 (admission decoupling, temporary-patient identity), Phase 2 (docs).
**Modules ⇉:** Form 12 Emergency (EIS: `emergency_visit`/`triage`/`assessment`/`injury`/`event` + MLC) using `admitFromEmergency` · Form 02 MRD verification (completeness layer on `MrdService`) · Form 31 MRD/EMR timeline & coding.
**Acceptance gate:** an unknown ER arrival can be treated-before-registered, triaged, assessed, and admitted to a ward/ICU; a discharged file is completeness-checked before archival; longitudinal EMR timeline renders.

---

### Phase 5 — Diagnostics & Revenue hardening ⇉ (mostly *extend* existing code)
**Prereqs:** Phase 1. **Note:** LIS/RIS/PMS/Billing **already exist and are integrated** — this phase hardens them to the specs, it does not build from scratch.
**Modules ⇉:** Form 27 LIS (add `VERIFIED`/`RELEASED` states + pathologist gate + `sample_tracker`) · Form 28 RIS/PACS · Form 29 PMS (add `narcotic_log` + double-sign + FEFO enforcement) · Form 30 Billing/RCM (TPA, advances, split-pay).
**Acceptance gate:** lab result requires pathologist verification before release; controlled-drug dispense requires dual signature; billing captures OT/implant/pharmacy charges without leakage.

---

### Phase 6 — Supply chain & Blood ⇉
**Prereqs:** Phase 3 (OT consumes CSSD/implants/blood), Phase 5 (inventory/purchase).
**Modules ⇉:** Form 33 Inventory/Store · Form 34 Purchase/Procurement · Form 35 CSSD (gates OT tray readiness) · Form 36 Biomedical (gates OT device calibration) · Form 38 Blood Bank (BBTMS; needs `Patient.blood_group` from Phase 0) · Form 03 Patient Feedback (public tokenized infra).
**Acceptance gate:** OT readiness (Form 26) can actually verify CSSD sterility + biomedical calibration + blood availability from live modules, not stubs.

---

### Phase 7 — Workforce, Facility, Experience & Interop ⇉ (support layer)
**Prereqs:** Phase 0 (staff identity), most clinical phases.
**Modules ⇉:** Form 04/39 HR & LMS & roster/payroll · Form 37 Housekeeping/Facility (bed-turnover gating) · Form 32 Admin/MIS dashboard (consumes all module metrics) · Form 40 Patient Portal (public identity) · Form 41 Integration/Interoperability (HL7/FHIR/DICOM gateway).
**Acceptance gate:** admin sees cross-module KPIs; patients can self-serve appointments + released reports; external systems can exchange ADT/ORU.

---

## 4. Dependency summary (why this order)
```
Phase 0 Foundations ──────────────────────────────────────────────┐ (everything)
   │
   ├─► Phase 1 Admission Core (Consent/Assessment/Risk/Vitals/MAR)
   │        │
   │        ├─► Phase 2 Progress + Discharge (DoctorRound engine)
   │        │        │
   │        │        └─► Phase 3 OT Suite (readiness→WHO→exec→PACU→handover)
   │        │                 │
   │        │                 └─► Phase 6 Supply/Blood (CSSD/implant/blood feed OT)
   │        │
   │        └─► Phase 5 Diagnostics/Revenue (extend LIS/RIS/PMS/Billing)
   │
   ├─► Phase 4 Emergency + MRD (needs admission decoupling)
   │
   └─► Phase 7 Support (HR/Facility/Admin/Portal/Interop)
```

## 5. Cross-cutting workstreams (run alongside all phases)
- **Testing:** TDD per module (`test-driven-development`); a growing regression suite is the phase gate.
- **Design/UX:** each user-facing module uses the design skills for its dashboard cards/wizards; keep to the existing component library (`DataTable`, `ActionMenu`, `PageHeader`, `useWebSocket`) so the app stays visually coherent and easy to use.
- **Security:** tenant-isolation test template applied to every new `{id}` endpoint; periodic audit re-run.
- **Docs:** each module updates its blueprint spec status Draft→Built; migrations logged in `setup/`.

## 6. What happens next (immediate)
1. **This roadmap is reviewed by you.** 2. On approval, we start **Phase 0** by invoking `writing-plans` to produce the detailed, task-by-task Phase 0 implementation plan (migrations, entity edits, tests, order). 3. Phase 0 is built sequentially (shared code), merged behind green regression. 4. Then Phase 1 opens with a LEAD architect agent + parallel module agents per §0.3.

> **Nothing in this roadmap writes code.** It sequences the work and sets the gates. Each phase
> is a separate plan→TDD→review→merge cycle so the live codebase is never left broken.
