# Shared Capability — Clinical Risk Management Engine

The reusable framework behind **every** structured risk assessment. Vulnerability
Assessment ([Form 06](../06-vulnerability-assessment.md)) is its first/canonical
instance; future scored assessments (Braden pressure-ulcer, Morse fall scale,
nutrition screening/MUST, pain, VTE, and **EWS/Early Warning Score — consumed by
Form 08 Nursing**) are **scales on the same engine** — nurses
record structured findings once, the engine computes standardized scores, raises
alerts, generates tasks/referrals, and **broadcasts the risk to every module**.
**New build.** Reuses existing [`WebSocketConfig`](../../../backend/src/main/java/com/hms/config/WebSocketConfig.java)
for live badges and [`AuditLogService`](../../../backend/src/main/java/com/hms/service/AuditLogService.java).

## Why it exists
The architect's directive (Form 06): *"your HMS should include a dedicated Clinical
Risk Management Engine, not just individual assessment forms."* Every assessment
repeats the same shape: structured inputs → scored risk level → badge + alert +
auto-task + referral, shared across dashboards. Build once; each new scale plugs in.

## A. Core table `patient_risk_assessment`
| Column | Type | Notes |
|---|---|---|
| id / public_id | BIGINT / VARCHAR | |
| hospital_id | BIGINT NOT NULL, INDEX | tenant key — filter every query |
| patient_id | BIGINT NOT NULL | FK |
| admission_id | BIGINT NOT NULL | FK ipd_admissions |
| scale_type | VARCHAR(30) | VULNERABILITY / BRADEN / MORSE / NUTRITION / PAIN … |
| inputs | JSON | structured findings (the ticked items) |
| computed_scores | JSON | per-dimension level: fall/pressure/nutrition/mobility/mental/infection |
| overall_risk | VARCHAR(10) | LOW / MEDIUM / HIGH |
| version | INT | reassessments fork a version |
| status | VARCHAR(12) | DRAFT / COMPLETED / REVIEWED / SUPERSEDED / ARCHIVED |
| assessed_by / assessed_at | BIGINT / TS | nurse |
| reviewed_by / reviewed_at | BIGINT / TS | doctor (high-risk review) |
| remarks | TEXT | |
| created_at / updated_at | audit cols | |

- **Index:** `(hospital_id, admission_id)`, `(hospital_id, overall_risk, status)` for the high-risk dashboard.
- Reassessment = new version (append-only history); latest COMPLETED is authoritative.

> **EWS already exists.** The `EWS` scale is **not** a new build — [`CdssEvaluationService.calculateEws(ipdAdmissionId)`](../../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java)
> already scores pulse/BP/temp/RR/SpO₂ (exposed at `/hospital/cdss/ews/{id}`), plus
> `evaluatePrescription`, `evaluateLabResult`, and smart-summary. The engine's job for EWS is to
> **wrap** that calculator with badge/notify propagation (§C) — not re-implement scoring. See
> [Form 09](../09-tpr-vitals-chart.md). (Its accuracy improves once `VitalSigns` BP is split into
> INT columns — today it string-parses `"120/80"`.)

## B. Scoring engine (deterministic, server-side)
Each `scale_type` registers a rule set: `inputs → per-dimension level + overall`.
Example (Vulnerability fall dimension): `age>75 + previous_fall + walking_aid +
sedation ⇒ HIGH`. **Never** computed client-side; server recomputes on every
submit so the level is trustworthy and auditable. Rules expressed as `IF…THEN…`
so they feed a config-driven rules table later.

## C. Risk propagation (the point of the engine)
On COMPLETED, the computed levels publish to **every relevant surface** — one
source of truth, many consumers:
- **Badge broadcast** via WebSocket → Doctor dashboard, Nurse dashboard, Ward board, **patient header**, medication screen (e.g. 🔴 HIGH FALL RISK everywhere).
- **Auto-tasks** (see §D) → nurse task list.
- **Auto-referrals** → Physiotherapy / Dietician / Infection Control (gap modules).
- **High-Risk Dashboard** membership.

## D. Auto-task generation — ⚠ prerequisite
Risk-driven safety tasks (bed rails, fall band, hourly rounding, 2-hourly turning,
assisted ambulation) must land on the nurse task list. **Blocker:** existing
[`NurseTask`](../../../backend/src/main/java/com/hms/entity/NurseTask.java) has
`doctor_order_id NOT NULL` — every task today requires a doctor order. Safety
tasks have **none**. Fix required: make `doctor_order_id` **nullable** + add a
`source`/`task_type` column (`DOCTOR_ORDER` | `RISK_PROTOCOL` | `NURSING`). Until
then, auto-task generation cannot be wired. Log as a foundational gap.

## E. Alerts / notifications
Assessment pending → ward nurse; overdue → ward in-charge; HIGH risk → doctor;
isolation → infection-control/housekeeping. Reuse existing event/WebSocket infra.

## F. Print + archival + audit
Via [Signature & Document service](./signature-and-document-service.md): nurse
signature, doctor-review block, QR, version. Archived to MRD on discharge (Form 02
checklist can add risk-assessment as an item). All mutations via `AuditLogService`
(`entity_type="RISK_ASSESSMENT"`, `scale_type` in details).

## G. How a form references this
A risk-assessment form spec supplies only: its **scale_type**, its **input fields**,
its **scoring rules (§B)**, and which **auto-tasks/referrals/badges** its levels
trigger. Storage, versioning, propagation, task-gen, print, archival, audit → *"via
Clinical Risk Engine"*.
