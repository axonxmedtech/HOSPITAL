# Form Spec — Re-Assessment Form (Patient Reassessment Sheet) / Clinical Progress Engine

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/IPD/09/2026* (2026-07-01) |
| **Existing code?** | **substantially exists — evolve, don't duplicate.** The doctor progress note is already [`DoctorRound`](../../backend/src/main/java/com/hms/entity/DoctorRound.java) (full SOAP: `subjective`/`objective`/`assessment`/`plan`, `round_date_time`, `next_round_at`) with [`DoctorRoundService`](../../backend/src/main/java/com/hms/service/hospital/DoctorRoundService.java) (`logRound`, `getRoundsHistory`). Vitals = [`VitalSigns`](../../backend/src/main/java/com/hms/entity/VitalSigns.java) (Form 09). Deterioration/critical-value logic = [`CdssEvaluationService`](../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java) (`calculateEws`, `evaluateLabResult`, `evaluatePrescription`, smart-summary). Medication changes = [`DoctorOrder`](../../backend/src/main/java/com/hms/entity/DoctorOrder.java) chain. Discharge readiness = existing `IpdAdmission` status `DISCHARGE_PLANNED` + [`DischargeSummary`](../../backend/src/main/java/com/hms/entity/DischargeSummary.java). |

> **Read first — this form is the recurring evolution of two things you already have.**
> **(1) `DoctorRound` IS the reassessment record — extend it, don't build a parallel `patient_reassessment`.**
> The architect's directive is "a longitudinal Clinical Progress Engine, not another standalone document."
> `DoctorRound` is already the append-only, per-visit SOAP progress note (`logRound` never overwrites;
> `getRoundsHistory` returns the timeline). Form 11 = the **structured** upgrade of `DoctorRound`: add
> `assessment_type`, structured `clinical_status`, and `clinical_impression`, and let `plan` continue to
> hold the updated plan. A second parallel table would fork the clinical timeline in two.
> **(2) It is the recurring sibling of Form 07's one-time `clinical_assessment`.**
> [Form 07](./07-admission-initial-assessment.md) is the **baseline** (one per admission). Reassessment is
> **N-per-admission**, each referencing the baseline for delta/trend. Patient-scoped history
> (`patient_allergy`, `patient_diagnosis`, …) is **not** re-collected — it is carried forward.
> **(3) Vitals / labs / meds / deterioration / discharge are all existing subsystems — this form
> *reviews and acts on* them, it does not re-enter them.** Section E reads `VitalSigns`; Section F reads
> `LabOrder`/`RadiologyOrder` (critical values via `CdssEvaluationService.evaluateLabResult`); Section G
> writes `DoctorOrder` transitions (via `evaluatePrescription`); Section H's "Ready for Discharge" flips
> `IpdAdmission → DISCHARGE_PLANNED` (existing). **Only referrals are genuinely new** (no referral entity exists).

---

## 1. Form Overview
- **Department:** Doctor / Consultant (primary owner); Nursing (structured status + vitals), ICU, MRD (secondary)
- **Module:** **Clinical Progress → Reassessment** (longitudinal timeline on the IPD chart)
- **Filled By:** Treating Doctor / Consultant (each round/review); Nurse contributes structured status + vitals
- **Reviewed By:** Consultant / ICU; feeds Discharge planning
- **Archived By:** MRD
- **Lifecycle:** repeats throughout admission (daily rounds, emergency reviews, post-op, ICU, follow-up); permanent after archival
- **NABH clause:** COP — patients are **reassessed** at appropriate intervals; care plan revised accordingly.

## 2. Purpose
- **Hospital use:** the evolving clinical narrative — how the patient responds day to day; drives every treatment change.
- **NABH requirement:** documented, timed reassessment with a revised plan (not a static admission note).
- **Legal:** the timeline of clinical impressions + plan changes is the core medico-legal record of ongoing care.
- **Clinical:** a **decision** document — continue/escalate/de-escalate, refer, or plan discharge.
- **Business rationale:** trends over snapshots; automatic change-detection (pain 8→3, new fever) surfaces deterioration before it is obvious.

## 3. Trigger
`Admission (Form 07 baseline) → scheduled round / emergency review / post-op / ICU review / follow-up → doctor reassesses → impression + updated plan → orders/referrals/discharge updated → repeat`. Each event = one new reassessment record (BR-2).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Treating Doctor / Consultant | create reassessment, set impression, update plan/orders | `DOCTOR` |
| Staff Nurse | structured clinical status + vitals contribution (read impression) | `NURSE` |
| ICU staff | frequent reassessment | `NURSE`/`DOCTOR` (ICU) |
| Ward In-charge | review compliance | `NURSE` (in-charge) |
| MRD | archive | `MRD_OFFICER` (gap) |
| Hospital Admin | read | `HOSPITAL_ADMIN` |

No new role gaps (referral **target** specialties surface Physiotherapy/Dietician/etc., already logged as Form 06 gaps).

## 5. Sections → storage (extending `DoctorRound`)
| § | Section | Capture | Storage |
|---|---|---|---|
| A | Patient info | name/UHID/bed/consultant | **auto-fill** from `IpdAdmission`/`Patient` (never typed) |
| B | Assessment info | **assessment_type** (Daily Round / Emergency Review / Post-Surgery / ICU Review / Follow-up), review_time | new `assessment_type` col + `round_date_time` *(exists)* |
| C | Current complaints | evolving symptoms | `subjective` *(exists SOAP)* |
| D | Current clinical status | consciousness, orientation, pain, mobility, appetite, hydration, sleep | new **`clinical_status`** JSON (structured) |
| E | Vitals review | latest vitals + trend | **read `VitalSigns`** (Form 09) — fetched, not re-entered; `objective` narrates |
| F | Investigation review | pending/completed labs + radiology + critical values | **read `LabOrder`/`RadiologyOrder`**; criticals via `evaluateLabResult` |
| G | Medication review | continue / stop / modify / add | **write `DoctorOrder`** transitions (§9 BR-3) via `evaluatePrescription` |
| H | Clinical impression | Improving / Stable / Deteriorating / Requires ICU / Requires Surgery / Ready for Discharge | new **`clinical_impression`** col |
| I | Updated plan | revised care plan | `plan` *(exists SOAP)* |
| — | Referrals | specialty, reason, urgency | new **`patient_referral`** table (gap) |

## 6. Longitudinal timeline (the point of the engine)
`getRoundsHistory` already returns every reassessment chronologically. The UI renders it as a **clinical timeline** (one card per reassessment: type · impression · key vitals · plan delta), not isolated pages. Trends (pain, EWS, key labs) plot across the series — the same trend infra as Form 09 vitals.

## 7. Database Design
**Extend `DoctorRound`** (rename-neutral; it stays `doctor_rounds`): add `assessment_type VARCHAR(30)`, `clinical_status JSON`, `clinical_impression VARCHAR(30)`, `baseline_assessment_id` (FK → Form 07 `clinical_assessment`, for delta), `status VARCHAR(12)` (DRAFT/FINALIZED). Keep `subjective/objective/assessment/plan`, `round_date_time`, `next_round_at`. Append-only (BR-2).
**`patient_referral`** (new): `id, public_id, hospital_id, patient_id, admission_id, reassessment_id, specialty, reason, urgency (ROUTINE/URGENT/STAT), status (REQUESTED/ACCEPTED/COMPLETED), requested_by, requested_at, responded_by, response_note, created_at`.
- FKs: `ipd_admission_id → ipd_admissions`, `doctor_id → doctors`, `hospital_id` on every row. Index `(hospital_id, ipd_admission_id, round_date_time)` for the timeline. Nurse structured-status contribution writes `clinical_status` on the current reassessment (or a linked nursing note — see [Form 08](./08-nursing-daily-progress.md) `nursing_progress_note`).

## 8. APIs
Under `/hospital/reassessment` (co-located with existing rounds); every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/reassessment` | DOCTOR | create reassessment (extends `logRound`) |
| GET | `/hospital/patients/{admissionId}/reassessments` | DOCTOR, NURSE, ADMIN | timeline (`getRoundsHistory`) |
| GET | `/hospital/reassessment/{id}` | DOCTOR, NURSE | one record |
| PATCH | `/hospital/reassessment/{id}/status` | NURSE | contribute structured status/vitals |
| POST | `/hospital/reassessment/{id}/referral` | DOCTOR | raise referral |
| GET | `/hospital/cdss/smart-summary/{admissionId}` | DOCTOR | **exists** — change-detection since last reassessment |

## 9. Business Rules
- **BR-1** Reassessment frequency by acuity/type (ICU frequent, ward daily, post-op per protocol); overdue → alert.
- **BR-2** Each reassessment is a **new immutable record** — never overwrite a prior one (append-only timeline).
- **BR-3** Medication actions **generate `DoctorOrder` transitions**: *stop*→status `STOPPED`, *modify*→supersede with new order, *add*→new order (each run through `evaluatePrescription` for interaction/allergy check). No free-text-only med change.
- **BR-4** `IF clinical_impression = Deteriorating / Requires ICU THEN` notify consultant + nurse + ICU (reuse `CdssAlertLog` + WebSocket); cross-check `calculateEws`.
- **BR-5** `IF clinical_impression = Ready for Discharge THEN` transition `IpdAdmission → DISCHARGE_PLANNED` (existing status) and open `DischargeSummary` workflow (Form 02 downstream).
- **BR-6** Critical investigation values (Section F) surface via `evaluateLabResult`; unacknowledged criticals block a "Stable/Improving" impression (guardrail).
- **BR-7** Section A/E/F are **read/derived** (patient info, vitals, orders) — not manually re-keyed; prevents divergence from source of truth.
- **BR-8** Every query filters `hospital_id`; every `{id}` validates ownership.

## 10. Dashboard
**Doctor:** patients due for reassessment · deteriorating list · pending criticals · discharge-ready · referrals awaiting response. **Nurse:** reassessment-status entry due · impression changes on assigned beds. All `WHERE hospital_id = current`.

## 11. Permissions
| Role | Create | Med/Referral action | Structured status | View |
|---|---|---|---|---|
| Doctor / Consultant | Yes | Yes | Yes | Yes |
| Nurse | No | No | Yes (§D + vitals) | Yes |
| Hospital Admin | No | No | No | Read |
| MRD | No | No | No | Archived |

Matches §8 `@PreAuthorize`.

## 12. Validation
`assessment_type` ∈ enum; `clinical_impression` ∈ enum; `review_time` not future; med changes must reference an existing `DoctorOrder` (stop/modify) or valid medicine (add); referral `urgency`/`specialty` from controlled lists; server recomputes EWS/criticals (never client). Deterioration/discharge transitions server-side only.

## 13. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/reassessment.html`: patient header, chronological reassessment entries (type · status · impression · plan), latest vitals, med changes, referrals, doctor signature, date/time, QR, version. Copy: file (MRD; Form 02 checklist `reassessment` item).

## 14. Audit Logs
Via `AuditLogService` (`entity_type="REASSESSMENT"`): created · impression set/changed · med action (old→new order) · referral raised · discharge-planned transition — user, role, timestamp, old→new, IP. CDSS alerts already log via `CdssAlertLog`.

## 15. AI & Smart Enhancements — extend CDSS/smart-summary
- **Clinical change-detection** — compare today vs previous reassessment; highlight deltas (pain 8→3, new fever, EWS ↑). Extend existing `/hospital/cdss/smart-summary` (already computes 24 h change) rather than build new.
- **Deterioration early-warning** — impression trend + EWS trend → suggest escalation. Reuses `calculateEws` + `CdssAlertLog`.
- **Discharge-readiness hint** — stable impression + resolving vitals/labs + no active criticals → *suggest* discharge planning (recommendation only).
- **Referral suggestion** — persistent problem pattern → suggest specialty referral.

---

## Module & workflow placement
- **Owning module:** Clinical Progress → Reassessment (longitudinal engine on the IPD chart).
- **Creates:** reassessment records (extended `DoctorRound`), `patient_referral`. **Updates:** `DoctorOrder` (med actions), `IpdAdmission.status` (→ `DISCHARGE_PLANNED`), EWS/alerts. **Reads/derives:** `Patient`/`IpdAdmission` (info), `VitalSigns` (Form 09), `LabOrder`/`RadiologyOrder` (Form F), Form 07 `clinical_assessment` (baseline). **Prints:** reassessment sheet. **Archives:** MRD (Form 02).
- **Feeds into:** Doctor Dashboard · ICU · Discharge workflow (Form 02) · CDSS/EWS · Alerts · MRD · Reports. **Fed by:** Initial Assessment (Form 07) · Vitals (Form 09) · Nursing progress (Form 08) · Lab/Radiology orders · Pharmacy.
- **New this form implies (add to README):** **`DoctorRound` schema evolution** (→ structured reassessment: `assessment_type`/`clinical_status`/`clinical_impression`/`baseline_assessment_id`/`status`) · **`patient_referral`** table + referral workflow (genuinely new — no referral entity exists) · reconciliation that `DoctorRound` = the Clinical Progress Engine (not a new `patient_reassessment` table).
