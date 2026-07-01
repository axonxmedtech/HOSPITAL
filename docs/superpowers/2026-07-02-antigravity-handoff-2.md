# HMS Implementation — Handoff #2 to Antigravity (2026-07-02)

> **Purpose.** Cold-start handoff so Antigravity can continue the phased NABH implementation
> with zero conversation context. Read §1 (state), §2 (rules & lessons — includes bugs already
> found and fixed, do not reintroduce them), §3 (the exact per-form recipe with file names),
> then start at §4 (task queue, in order).

**Repo:** `e:\Projects\HOSPITAL` · **Working branch:** `phase-0-01-discharge-isolation` (stay on it)
**Do NOT touch `main`. Do NOT push anything to any remote** — the owner will push when their work is done.
Local `staging` was fast-forward-merged up to commit `57d7fa1` and is now BEHIND this branch — that is fine; leave it. When asked to "merge to staging", use `git switch staging && git merge --ff-only phase-0-01-discharge-isolation && git switch phase-0-01-discharge-isolation` (it is always a clean fast-forward), and still do not push.

---

## 1. Current state (verified 2026-07-02)

### Completed and committed on this branch
| Phase | What | Key commits |
|---|---|---|
| 0.1 | DischargeSummary tenant-isolation IDOR fix | `b961b43`,`b196464`,`f5a6bc1` |
| 0.2 | VitalSigns structured BP split + EWS migration | `6cbb62f`,`575ed5e`,`db33256`,`a156547`,`bb69a9e` |
| 2 | Clinical forms suite (Antigravity's earlier work: consent panel, risk, fluid, nursing progress, clinical assessment) | up to `c7dd5dd` |
| 3 | NABH Discharge Summary (11 fields, upsert draft/finalize, JSON endpoint, enriched PDF) + Vitals timeline panel + qualitative-vitals fix + doctor-can-record-vitals fix | `b10bd9f`,`a2a0764`,`a42c717` |
| 4.01 | **Operation Record** (Form 18 core) — WHO time-out-gated create, finalize gate | `64f3d4c`,`7096bc8` |
| 4.02 | **Anaesthesia Record** (Form 19 core) — sign-in-gated, complete+sign | `d761902`,`a59081a` |
| 4.03 | **PACU/Recovery** (Form 20 core) — anaesthesia-complete gate, server-computed Aldrete, transfer gate ≥9 | `39e20c8`,`737012d` |
| 4.04 | **Clinical Handover** (Form 22 core) — PACU-ready gate, accept-locks | `8bdcabb`,`936a7aa` |
| — | **fix: `Fields()` call pattern** (focus-loss bug) — see §2.4 | `57d7fa1` |
| 4.05 | **Post-op Orders** (Form 21 core) — draft→sign lock | `0ce85a4`,`ac70bc1` |
| 4.06 | **Instrument Count** (Form 23 core) — **WHO sign-out safety gate** | `1acbc5d`,`970b4a3` |

The full OT surgical journey is gated end-to-end: schedule → sign-in → anaesthesia → time-out → operation record → count (gates sign-out) → post-op orders → sign-out → finalize → PACU (Aldrete) → transfer → handover accept.

### Untracked WIP in the working tree (do not lose, do not commit blindly)
- `backend/src/main/java/com/hms/controller/hospital/ConsentController.java`, `dto/ConsentCreateRequest.java`, `dto/ConsentSignRequest.java`, `service/hospital/ConsentService.java` — **half-finished Consent Engine backend**. The frontend `frontend/src/components/ipd/ConsentPanel.jsx` is ALREADY COMMITTED and calls these — finishing/committing this is task §4.3.
- `docs/superpowers/plans/2026-07-01-phase-0-03…0-08-*.md` and `2026-07-01-phase-1-01-consent-engine.md` — written but unexecuted plan docs (task §4.4).

### Verification state
Backend `mvn -q test` exit 0 (OtServiceTest = 36 tests) · frontend `npm run build` exit 0, at HEAD `970b4a3`. **No live run against MySQL has been done** for the new OT tables (Hibernate ddl-auto will create them on first boot).

---

## 2. Rules & lessons (non-negotiable; several are bugs already found+fixed once)

### 2.1 Owner's standing constraints
1. Do not harm the codebase — additive, reversible changes only. 2. Make no bug — TDD, suites stay green. 3. Maintain integrity/security — tenant isolation is tested, not assumed. 4. Keep it easy to use. 5. **Never push; never touch `main`.**

### 2.2 Build/verify (trust exit codes; `-q` hides surefire summary)
Backend from `backend/`: `mvn -q test` (full), `mvn -q -Dtest=OtServiceTest test` (single), `mvn -q -DskipTests compile`. Frontend from `frontend/`: `npm run build`. Windows/PowerShell environment; git CRLF warnings are harmless.

### 2.3 Migration pattern (no Flyway! there is no Flyway in this project)
Nullable `@Column` fields on the entity → Hibernate `ddl-auto=update` creates columns/tables at boot → mirror every change in `setup/schema-full.sql` (the canonical schema) → idempotent backfills only via `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java` (information_schema-guarded, try/catch, `jdbcTemplate`). Never write `V*__.sql` Flyway files — `backend/src/main/resources/db/migration/` is legacy reference only.

### 2.4 Frontend lesson — the focus-loss bug (fixed once in `57d7fa1`; DO NOT reintroduce)
Never define a form sub-component inside a component body and render it as JSX (`<Fields />`) — React remounts it every render and inputs drop focus per keystroke. Either define sub-components at module scope, or define `const Fields = () => (...)` inside and render it as a **function call**: `{Fields()}`. All existing OT sections use `{Fields()}` — copy that.

### 2.5 Role-alignment lesson (bug fixed once in `a42c717`)
Every UI write button's role check must match the endpoint's `@PreAuthorize` exactly, or users get 403s. When adding a section, verify the controller roles and mirror them in the component's `canWrite`.

### 2.6 Tenant isolation (mandatory in every service method)
```java
Long hospitalId = securityHelper.getCurrentHospitalId();
if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
OtBooking booking = bookingRepository.findById(bookingId).orElseThrow(...);
if (!booking.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied: Tenant mismatch");
```
Every repo finder is `findBy…AndHospitalId`. Every new lifecycle method gets a cross-tenant rejection test.

### 2.7 Do-no-harm gating lesson
When adding a gate to an existing flow (like the count gate in sign-out), the gate applies **only when the new record exists** — absent record = legacy behavior unchanged. Never make a new feature block an existing hospital's current workflow.

### 2.8 Commit hygiene
Two commits per increment: backend (entity/repo/dto/service/controller/test/schema) then frontend (service/section/panel). Conventional messages. Use your own Co-Authored-By signature.

---

## 3. The proven per-form recipe (used 6× — follow it exactly)

Each OT form increment touches exactly these files. **Read the three most recent examples first**: entity `backend/src/main/java/com/hms/entity/OtInstrumentCount.java`, service methods + gates in `backend/src/main/java/com/hms/service/hospital/OtService.java` (search `"===== Instrument"`), tests in `backend/src/test/java/com/hms/service/OtServiceTest.java`, UI in `frontend/src/components/ot/InstrumentCountSection.jsx`.

1. **Entity** — create `backend/src/main/java/com/hms/entity/<Name>.java`: Lombok `@Data @NoArgsConstructor @AllArgsConstructor`, `hospital_id NOT NULL`, `ot_booking_id NOT NULL UNIQUE` (1:1 per booking), nullable business columns, `status`, `signed_by/signed_at` if it has a sign lifecycle, `@CreationTimestamp created_at`.
2. **Repository** — `backend/src/main/java/com/hms/repository/<Name>Repository.java` with `Optional<T> findByOtBookingIdAndHospitalId(Long, Long)`.
3. **DTO** — `backend/src/main/java/com/hms/dto/<Name>Request.java`, Lombok `@Data`, editable fields only.
4. **Service** — add to `backend/src/main/java/com/hms/service/hospital/OtService.java`: import DTO, `@Autowired` repo, then `get…` / `save…` (upsert draft; reject if terminal status) / terminal action (`sign`/`finalize`/`accept`) with its business-rule gates, tenant checks (§2.6), `mrdService.validateAdmissionActive(booking.getIpdAdmissionId())` on writes, `audit(...)` + `broadcast(hospitalId)` after saves.
5. **Controller** — add to `backend/src/main/java/com/hms/controller/hospital/OtController.java` (base `/api/ipd/{admissionId}/ot/bookings`): GET/POST/PUT `/{bookingId}/<kebab-name>` + POST terminal action; try/catch → `badRequest(e.getMessage())`; `@PreAuthorize` — reads usually `'DOCTOR','NURSE','HOSPITAL_ADMIN'`, writes per clinical role.
6. **Schema mirror** — `setup/schema-full.sql`: insert a `CREATE TABLE IF NOT EXISTS … ENGINE=InnoDB;` + composite index block **immediately before the `-- Table structure for table `mrd_records`` line** (~line 2200; the previous OT blocks 4.01–4.06 are right above it — match their style).
7. **Tests** — add to `backend/src/test/java/com/hms/service/OtServiceTest.java`: `@Mock` the new repo; use the existing `tenantBooking(bookingId, hospitalId, admissionId)` helper; test each gate (blocked + allowed), signature/lock stamping, terminal-state immutability, cross-tenant rejection (`verify(repo, never()).save(any())`). Run `mvn -q -Dtest=OtServiceTest test`.
8. **Frontend service** — append to `frontend/src/services/otService.js` (paths mirror the controller).
9. **Frontend section** — create `frontend/src/components/ot/<Name>Section.jsx` copying `InstrumentCountSection.jsx`'s shape (load/hydrate/editing/saving states, `{Fields()}` call pattern §2.4, role-gated buttons §2.5, status badge, locked read view). Mount it in `frontend/src/components/ot/OtWorkflowPanel.jsx` inside the `activeBooking` block, in pipeline order (current order: Anaesthesia → OperationRecord → InstrumentCount → PostopOrders → Pacu → ClinicalHandover). Props: `admissionId`, `bookingId={activeBooking.id}`, `bookingStatus={activeBooking.status}`, `isLocked={isLocked}`.
10. **Gates + commit** — `mvn -q test` AND `npm run build` both exit 0 → two commits (§2.8).

---

## 4. Task queue (do in this order)

### 4.1 Form 24 — Implant Record (finishes the OT execution set)
- **Read:** `docs/hms-blueprint/24-implant-record.md` (§5 fields, §6/7 rules+DB). Existing inventory rails: `backend/src/main/java/com/hms/entity/InventoryItem.java`, `HospitalInventory.java` (read before wiring deduction).
- **Build (recipe §3):** `ImplantRecord` entity — note: implants can be **multiple per booking**, so use `List<T> findByOtBookingIdAndHospitalId` (NOT unique 1:1 — the one deviation from the recipe): implant name/type, batch/serial no, manufacturer, expiry, quantity, site, `inventory_item_id` (nullable link), recorded_by/at. Endpoints: GET list / POST add / DELETE `{implantId}` (draft-window only). v1: traceability record only; **stock deduction is a follow-up** — do NOT wire inventory writes yet unless the plan below is done first.
- **Gate:** can only add implants while an operation record exists and is not FINALIZED.
- **UI:** `ImplantRecordSection.jsx` — table of implants + add row; mount after InstrumentCountSection.

### 4.2 Forms 25/26 — OT Register + OT Readiness (light)
- **Read:** `docs/hms-blueprint/25-ot-register.md`, `26-ot-readiness.md`.
- Form 25 = a hospital-wide OT register view: backend GET (paginated list of bookings joined with operation/anaesthesia/PACU statuses — extend `OtService.getHospitalBookings`) + a simple register table UI (new tab or admin view). No new tables needed for v1.
- Form 26 = readiness checklist gating `scheduleBooking` — v1: a `ot_readiness` record per booking date/room with boolean checks; **gate scheduleBooking only when a readiness record exists and is not READY** (§2.7 pattern). Full CSSD/biomedical integration is Phase 6 — stub the check fields as manual booleans.

### 4.3 Finish the Consent Engine backend (untracked WIP — currently a latent broken state)
- **Read first:** the untracked files listed in §1, the committed `frontend/src/components/ipd/ConsentPanel.jsx` (defines the API contract the backend must satisfy — match its request/response shapes exactly), and the plan `docs/superpowers/plans/2026-07-01-phase-1-01-consent-engine.md`, blueprint `docs/hms-blueprint/05-general-consent.md`.
- Ground what exists in the WIP files, finish gaps (entity/repo may be missing — check `entity/` for a Consent entity; `SignatureSlot.java` exists and may be the signature mechanism), add tenant guards + tests (`ConsentServiceTest`), mirror schema, get gates green, commit.

### 4.4 Phase 0 foundation sub-plans 0.3 → 0.8 (plan docs already written, untracked)
Execute in order, each is a self-contained plan with exact file paths and code:
- `docs/superpowers/plans/2026-07-01-phase-0-03-nursetask-decoupling.md` → `entity/NurseTask.java`, `service/hospital/NurseTaskService.java`
- `…phase-0-04-patient-model.md` → `entity/Patient.java` + registration flow
- `…phase-0-05-staff-identity.md` → `entity/User/Doctor/Nurse.java` (note: schema-full.sql already has Phase 0.5 comment stubs near the `doctors` table)
- `…phase-0-06-role-framework.md` → role enum + `config/SecurityConfig.java`
- `…phase-0-07-admission-monitoring.md` → `service/hospital/IpdAdmissionService.java` (`admitFromEmergency`; also add the deferred tenant guard on `addIpdFollowup` ~line 526) + new `monitoring_vitals`
- `…phase-0-08-signature-notification.md` → note `SignatureSlot.java` + `NotificationService.java` already exist — ground first, plans may be partially done.
Commit each plan doc together with its implementation.

### 4.5 Deferred fan-outs (after the above; each is its own increment)
1. Post-op meds → Pharmacy queue + `NurseTask` MAR schedules (Form 21 BR-2/3; read `entity/NurseTask.java`, `service/hospital/NurseTaskService.java`).
2. Operation-record specimens → auto-`LabOrder` (Form 18 BR-4; read `entity/LabOrder.java` + its service).
3. Implant/anaesthesia-drug stock deduction → `InventoryItem`/`Medicine` (Forms 24/19 BR-3).
4. Incident Reporting entity (Form 18 BR-6 / count-discrepancy auto-incident).
5. Structured child tables for post-op meds/monitoring/investigations and count items.

---

## 5. Quick-start checklist
1. `git status` — confirm branch `phase-0-01-discharge-isolation`; the untracked files of §1 should be present.
2. `cd backend && mvn -q test` and `cd frontend && npm run build` — confirm the inherited green baseline.
3. Read the §3 recipe examples (OtInstrumentCount entity/service section/tests/UI section).
4. Start §4.1 (Implant Record). Work the queue in order. Two commits per increment, gates green before each commit, never push, never touch `main`.
