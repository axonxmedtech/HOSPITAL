# Phase 4.01 — Operation Record (OT Execution hub, Form 18 core)

> Next increment of the Operation Theatre Suite. Builds on the existing OtBooking + WHO
> OtChecklist (sign-in/time-out/sign-out). Ships the **core `operation_record`** — the central
> operative note — fully additive. Fan-out pieces (implant inventory deduction, specimen→LabOrder,
> incident reporting, team/consumables tables) are deferred to follow-up increments.

**Goal:** Capture a structured intra-operative record per surgery, gated by the WHO checklist, with a finalize+sign lifecycle and strict tenant isolation.

**Architecture:** New `operation_record` table (1:1 with an `ot_bookings` row). Nullable columns, Hibernate `ddl-auto` creates them, mirrored in `setup/schema-full.sql`. Endpoints nest under the existing per-booking OT controller. No change to scheduling/checklist safety logic.

**Blueprint:** `docs/hms-blueprint/18-operation-record.md` (§6 core table, §7 BR-1/BR-2, §13 validation).

## Business rules (server-enforced)
- **BR-1 (create gate):** an operation record can be created only after the WHO **time-out** is completed (booking `IN_PROGRESS` — patient inside OT).
- **BR-2 (finalize gate):** finalize/sign requires: `actualProcedure` present · `postOpPlan` present · WHO **sign-out** completed (instrument-count confirmed proxy). Finalize stamps `signedBy`/`signedAt`, sets `status=FINALIZED`.
- **Timings monotonic:** `operationStart ≤ operationEnd` when both present.
- **Tenant:** every method resolves `hospitalId` from `securityHelper` and validates booking + record ownership (fail-closed).
- **Edit:** only `DRAFT` records are editable; a `FINALIZED` record is read-only.

## Tasks
1. `entity/OperationRecord.java` — table `operation_record`; fields: id, hospitalId, patientId, admissionId, otBookingId (unique), surgeonId, procedureName, actualProcedure, operativeFindings, estimatedBloodLoss, complicationsSummary, postOpPlan, operationStart, operationEnd, status, signedBy, signedAt, createdAt.
2. `repository/OperationRecordRepository.java` — `findByOtBookingIdAndHospitalId`.
3. `dto/OperationRecordRequest.java` — editable fields.
4. `service/hospital/OtService.java` — inject repo; `getOperationRecord`, `createOperationRecord` (BR-1), `updateOperationRecord` (draft only), `finalizeOperationRecord` (BR-2). Audit + broadcast like siblings.
5. `controller/hospital/OtController.java` — GET/POST/PUT `/{bookingId}/operation-record` + POST `/{bookingId}/operation-record/finalize`. Roles: DOCTOR/HOSPITAL_ADMIN write, +NURSE read.
6. `setup/schema-full.sql` — mirror table.
7. `OtServiceTest` — add `@Mock OperationRecordRepository`; tests: create rejected before time-out; create ok after time-out; finalize rejected without procedure/post-op plan; finalize rejected before sign-out; finalize happy path stamps signature; cross-tenant rejected.
8. `services/otService.js` + `components/ot/OtWorkflowPanel.jsx` — Operation Record section (view/draft/finalize) on the active booking.
9. Green gates: `mvn -q test` + `npm run build`; commit.
