# HMS Implementation — Handoff #3 to Antigravity (2026-07-02)

> **Purpose.** Cold-start handoff so Antigravity can continue the phased NABH implementation
> with zero conversation context. Read §1 (verified current state), §2 (non-negotiable rules —
> condensed from Handoff #1/#2, read those two docs in full if anything here is unclear), then
> work the phase queue in §3, in order. Each phase is scoped to be independently shippable
> (its own two commits, its own green gates) — do not blend phases into one commit.

**Repo:** `e:\Projects\HOSPITAL` · **Working branch:** `phase-0-01-discharge-isolation` (stay on it)
**Do NOT touch `main`. Do NOT push anything to any remote** — the owner pushes when their work is done.

---

## 1. Verified current state (2026-07-02, audited against actual code — not assumed)

### Fully done, committed, tested (do not redo)
Forms 02, 03, 04, 12, 15, 16 (Surgical Consent), 18–24 (full OT execution set incl. Implant Record),
27, 28, 29, 31, 32, 35, 36, 37, 38, 39. Form 30 partially (see §3, Phase D). Form 40 phase 1 only
(OTP login + read-only dashboard — payment/telemedicine/delegate access are explicitly out of
scope until Phase H below). Form 01 (Blood Transfusion Consent) is fully done inside
`ConsentService`/`BloodConsentDetail`. Form 08 (Nursing Daily Progress), Form 10 (Intake/Output),
Form 14 (Discharge Summary), Form 17 (WHO Safety Checklist, embedded as `OtChecklist` gate logic
in `OtService.signChecklist()`) are all fully done.

**Full backend test suite green (`mvn -q test`, 343+ tests), frontend green (`npm run build`), at
HEAD as of this handoff.**

### Partially done — needs completion, not a fresh build (see §3 Phase A)
**Form 05 (General Consent)**: `ConsentService`/`PatientConsent` supports a `"GENERAL"` type
end-to-end (create/sign/lock), but two real gaps:
1. Minor-detection (`ConsentService.java` ~line 237-244) uses `patient.getAge()` (a coarse
   Integer) instead of the precise `patient.getDateOfBirth()` field, which **already exists** on
   `Patient` (`backend/src/main/java/com/hms/entity/Patient.java` line 133) along with
   `guardianName` (line 136), `guardianRelationship` (line 139), and `preferredLanguage` (line
   142) — those fields exist but are not read anywhere in the GENERAL consent flow. Do not add
   new Patient fields; wire the existing ones in.
2. No BR-6-equivalent hard gate blocking elective IPD admission confirmation until a GENERAL
   consent is `LOCKED` for that patient (check `IpdAdmissionService` admission-confirm path).

### Not started — needs a fresh build (see §3 for phase assignment)
- Form 06 — Vulnerability Assessment (no trace anywhere in the repo)
- Forms 25/26 — OT Register + OT Readiness (queued in Handoff #2 §4.2, still not done)
- Form 30 BR-1 (proper tenant-partitioned unique bill sequence — currently `Billing.customId` is
  `"BIL" + random 4-digit int`, `backend/src/main/java/com/hms/entity/Billing.java` line ~59, no
  collision retry), BR-3 (Charge Master pricing catalog — no `ChargeMaster` entity exists at all),
  BR-6 (insurance/cashless claim freeze — no insurance/preauth logic anywhere in `BillingService`)
- Forms 33/34 — Inventory/Store + Purchase/Procurement as dedicated NABH-compliant modules
  (pharmacy-adjacent inventory commits exist from earlier "Phase D/E/G1" work, but were never
  checked against these two blueprint specs — audit first, see Phase E)
- Deferred fan-outs from Handoff #2 §4.5 (still pending, unchanged — see §3 Phase F)
- Form 40 phases 2–3 — online payment gateway, teleconsultation, delegate/caregiver access
  (deliberately deferred; needs the owner's vendor decisions before any code — see §3 Phase H)
- Form 41 — HL7/FHIR/DICOM interoperability gateway (architecturally distinct — see §3 Phase I)

### Needs verification before extending (audited but ambiguous — ground truth before building)
These have SOME implementation under a different name than the blueprint form, built before the
blueprint docs existed. Read the actual blueprint spec and the actual code side-by-side before
deciding whether to extend or rebuild:
- **Form 07 (Admission Initial Assessment)** ≈ `ClinicalAssessment` entity/service — currently
  only chief complaint, HPI, provisional diagnosis, treatment plan, draft/finalize/versioning. No
  systemic examination, nutritional/functional/pain screening fields. Likely needs field
  expansion, not a rebuild.
- **Form 09 (TPR/Vitals Chart)** ≈ `VitalSigns` entity — solid per-reading capture (temp, pulse,
  RR, BP, SpO2, pain, weight, O2 support) but no trend-chart/graph aggregation view, no
  intake-output cross-reference. Likely needs a reporting/view layer on top of existing data, not
  new capture fields.
- **Form 11 (Patient Reassessment)** ≈ `PatientRiskAssessment`/`RiskAssessmentService` — this is
  fall-risk/pressure-sore **scoring**, which may or may not be what Form 11 actually asks for
  (periodic time-bound clinical reassessment notes). Read `docs/hms-blueprint/11-patient-reassessment.md`
  closely before assuming this counts as done — it may need a separate, new entity.
- **Form 13 (Doctor Progress Notes)** ≈ `DoctorRound` entity/service — captures rounds, but no
  confirmed SOAP (Subjective/Objective/Assessment/Plan) structured fields. Compare against
  `docs/hms-blueprint/13-doctor-progress-notes.md`'s field list before assuming coverage.

---

## 2. Non-negotiable rules (condensed — full detail in Handoff #1 and #2)

1. **Never push to any remote. Never touch `main`.** Owner pushes when their work is done.
2. **Tenant isolation in every service method, no exceptions:**
   ```java
   Long hospitalId = securityHelper.getCurrentHospitalId();
   if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
   Foo foo = fooRepository.findById(id).orElseThrow(...);
   if (!foo.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied: Tenant mismatch");
   ```
   Every repository finder should be `findBy…AndHospitalId`. Every new lifecycle method gets a
   cross-tenant-rejection test (`verify(repo, never()).save(any())` after asserting the exception).
3. **No Flyway.** Nullable `@Column` fields → Hibernate `ddl-auto=update` creates
   columns/tables at boot → mirror every schema change in `setup/schema-full.sql` (the canonical
   schema, `CREATE TABLE IF NOT EXISTS ... ENGINE=InnoDB;` blocks) → idempotent backfills only via
   `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`. Never write new `V*__.sql`
   files.
4. **Frontend focus-loss bug (fixed once, do not reintroduce):** never render a form
   sub-component defined inside a parent component as JSX (`<Fields />`) — React remounts it every
   keystroke and inputs lose focus. Define `const Fields = () => (...)` and render as a **function
   call**: `{Fields()}`. Every existing section in this codebase already does this — copy the
   pattern.
5. **Role alignment:** every UI write-button's role check must exactly match its endpoint's
   `@PreAuthorize`, or users get silent 403s.
6. **Do-no-harm gating:** when adding a gate to an existing flow, the gate applies only when the
   new record type is actually in play — absence of the new record must never change behavior for
   a hospital that hasn't started using the new feature yet.
7. **Green gates before every commit:** `cd backend && mvn -q test` AND `cd frontend && npm run
   build` both exit 0. Two commits per increment (backend: entity/repo/dto/service/controller/
   test/schema; then frontend: service/component). Conventional commit messages
   (`feat(module): description (Form N BR-X)`), your own `Co-Authored-By` signature.
8. **Write real tests, not placeholders.** Mockito + AssertJ unit tests on services (see any
   `*ServiceTest.java` in `backend/src/test/java/com/hms/service/` for the established pattern:
   `@ExtendWith(MockitoExtension.class)`, `@Mock` every repository dependency, `@InjectMocks` the
   service, test each business rule as its own `@Test` method — the gate blocked, the gate passed,
   tenant rejection).
9. **Scope discipline:** when a blueprint form's full business-rule set is large, it is
   acceptable and expected to scope down to the load-bearing safety/compliance rules for a first
   pass and explicitly document (in the commit message and/or a code comment) which BRs were
   deferred and why — this repo's history is full of exactly that pattern (e.g. Form 30 was scoped
   from 7 BRs to 2 in one pass; Forms 35/36/37 each explicitly deferred 1-3 BRs). Don't silently
   under-build; state the cut.
10. **Don't guess at file contents.** Before writing code against an existing entity/service/
    repository, read it. Several bugs this session were caused by assuming a field existed (e.g.
    assuming `Appointment.doctorName` was a persisted column when it's `@Transient`) — always
    verify with a real `Read`/`Grep` first.

---

## 3. Phase queue (work in this order — each phase is independently shippable)

### Phase A — Consent Engine correctness (Form 05 completion)
**Read first:** `docs/hms-blueprint/05-general-consent.md`, `backend/src/main/java/com/hms/service/hospital/ConsentService.java` (the `"GENERAL"` branch and the minor-detection block ~line 237), `backend/src/main/java/com/hms/entity/Patient.java` (confirm `dateOfBirth`/`guardianName`/`guardianRelationship`/`preferredLanguage` fields — they already exist, don't re-add them).

**Build:**
1. Replace the `patient.getAge() < 18` minor check with a precise age-from-`dateOfBirth` calculation (`Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears() < 18`), falling back to `age` only if `dateOfBirth` is null (legacy patients).
2. When a GENERAL consent requires a guardian signature (minor path), pre-fill/validate against `patient.getGuardianName()`/`getGuardianRelationship()` instead of accepting arbitrary free text with no cross-check.
3. Add a BR-6-equivalent gate: find where `IpdAdmissionService` confirms an elective (non-emergency) admission, and block confirmation until a GENERAL `PatientConsent` exists for that patient with `status = "LOCKED"`. Follow the do-no-harm pattern (§2.6): only gate when GENERAL consent tracking has actually started for that hospital's patient — i.e. don't retroactively block admissions for patients who predate this feature. (Practically: only enforce the gate going forward from a config flag or simply document that this is a soft warning, not a hard block, if retroactive enforcement risk seems too high — use judgment, but document the choice.)
4. Tests in `ConsentServiceTest.java` (create if it doesn't exist yet, per the untracked-WIP note in Handoff #2 §4.3 — check whether it was already created and committed since; if so, extend it): minor-by-DOB detection, guardian cross-check, admission-confirm gate blocked/passed.

---

### Phase B — Vulnerability Assessment (Form 06, new)
**Read first:** `docs/hms-blueprint/06-vulnerability-assessment.md` in full (fields, business rules, DB design, API list) — this is a completely new form, no prior art to build from.

**Build:** follow the standard recipe (see Handoff #2 §3 for the exact 10-step file-by-file recipe: entity → repository → DTO → service → controller → schema mirror → tests → frontend service → frontend section → gates+commit). Use `docs/hms-blueprint/13-doctor-progress-notes.md`'s sibling forms (`ClinicalAssessment`, `NursingProgressNote`) as the closest existing analogues for a per-admission clinical documentation entity with draft/finalize lifecycle.

---

### Phase C — OT Register + OT Readiness (Forms 25/26)
**Read first:** `docs/hms-blueprint/25-ot-register.md`, `docs/hms-blueprint/26-ot-readiness.md`, and Handoff #2 §4.2 (this phase was scoped there already, just never executed — the scoping guidance there is still valid):
- Form 25 = hospital-wide OT register view: backend GET (paginated list of bookings joined with operation/anaesthesia/PACU statuses — extend `OtService.getHospitalBookings` or add a new read method) + a register table UI (new tab or admin view). No new tables needed.
- Form 26 = readiness checklist gating `OtService.scheduleBooking`: a `ot_readiness` record per booking date/room with boolean checks (equipment calibrated, CSSD trays confirmed, etc. — v1 can be manual booleans, not live-integrated with CSSD/Biomedical inventories even though those modules now exist as of this session; wiring readiness checks to live CSSD tray status / Biomedical equipment status is a good v2 fan-out, not required for v1). Gate `scheduleBooking` only when a readiness record exists for that date/room and is not `READY` (do-no-harm §2.6 — hospitals with no readiness record yet are unaffected).

---

### Phase D — Billing/RCM hardening (Form 30 remaining BRs)
**Read first:** `docs/hms-blueprint/30-billing-rcm.md`, `backend/src/main/java/com/hms/service/hospital/BillingService.java`, `backend/src/main/java/com/hms/entity/Billing.java`.

**Build, in order (each is its own commit pair):**
1. **BR-1 (unique bill sequence):** replace the random-4-digit `customId` generation
   (`Billing.java` `@PrePersist`, ~line 57-59) with a per-hospital sequential, human-readable
   format matching the blueprint (e.g. `BIL-2026-00045`). Use a `countByHospitalId`-style query
   (same pattern already used in `EmployeeRepository.countByHospitalId`,
   `MedicalEquipmentRepository.countByHospitalId` from this session's Forms 36/39 work) plus a
   save-retry loop for the rare concurrent-insert collision case (or a DB-level unique constraint
   with a caught-and-retried `DataIntegrityViolationException` — don't just add the constraint and
   hope, actually handle the collision).
2. **BR-3 (Charge Master):** new `ChargeMaster` entity (tenant-owned: service code, name,
   category, active price, effective-from date, `is_active`), repository, service CRUD, and
   integrate it as the pricing source for new `BillingItem` rows instead of ad hoc manual entry —
   don't rip out manual entry entirely (do-no-harm), but prefer Charge Master lookups when a
   matching active entry exists.
3. **BR-6 (insurance/cashless claim freeze):** new fields or a small `InsuranceClaim` entity
   (claim number, payer name, status: `PENDING`/`APPROVED`/`REJECTED`/`SETTLED`, pre-auth amount)
   linked to a `Billing` record; while a claim is `PENDING`/`APPROVED`-awaiting-settlement, block
   direct patient billing finalization for the claimed amount portion. Read the blueprint's exact
   BR-6 wording before designing the state machine — don't improvise a claims workflow from
   scratch without checking what the spec actually asks for.

---

### Phase E — Inventory & Procurement (Forms 33/34)
**Read first:** `docs/hms-blueprint/33-inventory-store.md`, `docs/hms-blueprint/34-purchase-procurement.md`. Then **audit** the existing pharmacy-adjacent inventory code from earlier session work (commits tagged `feat(pharmacy): ...` — Manufacturer, Category, Suppliers, opening stock/inventory transactions, `HospitalInventory`/`InventoryItem` entities) against both blueprint specs before writing anything new. It's likely a meaningful fraction of Form 33/34's scope is already covered by the pharmacy module (which manages medicine stock) but Forms 33/34 are about **general hospital inventory** (non-pharmaceutical: linens, equipment consumables, office supplies) and **procurement workflow** (purchase requisition → PO → GRN → payment) — these may be a materially different scope than pharmacy stock. Do not assume overlap; verify field-by-field.

**Build:** whatever gap the audit reveals, following the standard recipe.

---

### Phase F — Deferred fan-outs (carried over from Handoff #2 §4.5, unchanged, still pending)
Each is its own increment:
1. Post-op meds → Pharmacy queue + `NurseTask` MAR schedules (Form 21 BR-2/3; read `entity/NurseTask.java`, `service/hospital/NurseTaskService.java`).
2. Operation-record specimens → auto-`LabOrder` (Form 18 BR-4; read `entity/LabOrder.java` + its service).
3. Implant/anaesthesia-drug stock deduction → `InventoryItem`/`Medicine` (Forms 24/19 BR-3) — this now has a natural home given Phase E's inventory work; sequence Phase F item 3 after Phase E if possible.
4. Incident Reporting entity (Form 18 BR-6 / instrument-count-discrepancy auto-incident — note: this session's CSSD work (Form 35) also deferred a BR-4 incident-report concept; consider whether a single shared `IncidentReport` entity should serve both OT and CSSD rather than building two parallel ones — check `docs/hms-blueprint/35-cssd-sterilization.md` BR-4 and `18-operation-record.md` BR-6 together before deciding).
5. Structured child tables for post-op meds/monitoring/investigations and count items (currently likely free-text or JSON blobs — read the current `OtPostopOrder`/`OtInstrumentCount` entities to confirm before restructuring).

---

### Phase G — Clinical documentation depth pass (Forms 07/09/11/13 — verify then extend)
For each of the four "needs verification" forms in §1: read the blueprint spec in full, read the current entity/service field-by-field, write a short comparison note (even just a code comment or a scratch doc), then extend the existing entity/service with whatever's missing. Do NOT create parallel/duplicate entities if the existing one can be extended with additive nullable fields (§2.3 migration pattern). If a form turns out to need a genuinely different shape (e.g. Form 11 needing time-bound reassessment notes distinct from risk scoring), build it as a new entity but keep `PatientRiskAssessment` as-is (it's real, working code for a real purpose — don't repurpose it).

---

### Phase H — Patient Portal, phases 2–3 (Form 40 remainder) — **do not start without the owner**
This phase is **gated on decisions only the owner can make** — do not implement blindly:
- **Phase H.1 — Online payment.** Needs the owner to pick a payment gateway (Razorpay, Stripe,
  PayU, CCAvenue, etc.) and supply API credentials. Read
  `docs/superpowers/specs/2026-07-02-patient-portal-login-design.md` for the identity/JWT pattern
  already established (reuse it — patient JWTs already exist from Phase 1) before designing
  anything. **Brainstorm with the owner first** (this repo's convention: use a
  brainstorming/design-then-plan workflow before writing code for anything architecturally new —
  see how Form 40 phase 1 itself was scoped in the design doc above for the expected process).
- **Phase H.2 — Teleconsultation.** Needs the owner to pick a video/WebRTC vendor (Twilio Video,
  Daily.co, Zoom SDK, etc.). Same brainstorm-first requirement.
- **Phase H.3 — Delegate/caregiver access.** This one does NOT need a vendor decision — it was
  deferred purely for scope, not for a missing dependency. `docs/hms-blueprint/40-patient-portal.md`
  BR-1 and the `patient_consent` table design describe it. This can be picked up directly via the
  standard recipe once H.1/H.2 (or independently of them) are scheduled — it's the one Form 40
  remainder that doesn't need to wait on the owner.

---

### Phase I — Interoperability Gateway (Form 41) — **do not start without the owner**
Architecturally distinct from every other form in this codebase (external HL7/FHIR/DICOM protocol
adapters, not an internal CRUD-safety-chain module). Read `docs/hms-blueprint/41-integration-interoperability.md`
in full, then **brainstorm the design with the owner before writing any code** — this needs
explicit scoping decisions (which standards/versions, which external systems to integrate with
first, authentication model for external callers) that only the owner can make.

---

## 4. Quick-start checklist
1. `git status` — confirm branch `phase-0-01-discharge-isolation`, working tree clean (or only
   containing files you're mid-editing).
2. `cd backend && mvn -q test` and `cd frontend && npm run build` — confirm the inherited green
   baseline before touching anything.
3. Re-read §1 of this doc once more — it is the ground truth as of 2026-07-02, verified against
   actual code, not assumed from commit messages alone.
4. Start Phase A. Work the queue in order unless the owner redirects you. Two commits per
   increment, gates green before each commit, never push, never touch `main`.
5. For Phases H and I specifically: **stop and get the owner's input before writing code** — these
   are explicitly marked "do not start without the owner" for a reason (external vendor
   dependencies / architecturally novel scope that needs a design conversation, not a blind build).
