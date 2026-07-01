# Form Spec — Discharge Summary / Discharge Engine (auto-generated)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/IPD/20/2026* (2026-07-01) |
| **Existing code?** | **exists but thin — extend into an auto-generation engine.** [`DischargeSummary`](../../backend/src/main/java/com/hms/entity/DischargeSummary.java) already exists (1:1 per admission) with a live flow: [`IpdAdmissionController`](../../backend/src/main/java/com/hms/controller/hospital/IpdAdmissionController.java) `/plan-discharge` → `/confirm-discharge` → `/{id}/discharge-summary/pdf`, `DISCHARGE_PLANNED` status, and billing wired through [`IpdAdmissionService`](../../backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java). Auto-generation reads existing modules: progress notes (`DoctorRound`, Forms 11/13), labs/radiology (`LabOrder`/`RadiologyOrder`), procedures (`OtBooking`), meds (`DoctorOrder`/Pharmacy), vitals (`VitalSigns`). |

> **Read first — extend the existing `DischargeSummary`; do not build a new table. And it has a tenant-isolation bug.**
> **(1) `DischargeSummary` is real but under-modeled.** It stores only `ipd_admission_id` (unique),
> `final_diagnosis`, `treatment_given`, `discharge_notes`, `follow_up_date`, `created_at`. Form 14 needs
> `admission_diagnosis` (provisional), a distinct `hospital_course`, `condition_at_discharge`, `status`,
> `signed_by`/`signed_at`, and version/amendment. **Extend it.**
> **(2) ⚠ It has NO `hospital_id` (and no `patient_id`/`doctor_id`).** It is keyed only by
> `ipd_admission_id` — a **tenant-isolation gap** exactly of the kind the
> [audit](../../superpowers/audits/2026-07-01-hms-functional-audit.md) flagged. Any direct
> `findByIpdAdmissionId` without joining back to the admission's `hospital_id` risks cross-tenant read.
> **Add `hospital_id`, `patient_id`, `doctor_id`** as part of this work (foundational fix).
> **(3) The flow already exists — reuse it.** `/plan-discharge`, `/confirm-discharge`, and the PDF
> endpoint are built. This form turns the mostly-blank summary into a **95% auto-generated draft** and
> adds the sign/amend lifecycle (same pattern as [Form 13](./13-doctor-progress-notes.md)) — it does not
> replace the plan→confirm spine.

---

## 1. Form Overview
- **Department:** Treating Doctor (primary); Nursing, Pharmacy, Lab, Billing, MRD, Admin (secondary)
- **Module:** **Doctor → Discharge Management → Discharge Summary** (auto-generated, not a blank form)
- **Filled By:** Treating Doctor (reviews the draft), Consultant approves
- **Reviewed By:** Consultant (approve), Pharmacy (meds), Billing (clearance), Nurse (checklist)
- **Archived By:** MRD
- **Lifecycle:** one per admission; append-only after sign; permanent after archival
- **NABH clause:** MOI/COP — the final, complete episode-of-care record.

## 2. Purpose
- **Hospital use:** the single document representing the entire stay — used by patient, future doctors, TPA/insurers, MRD, courts, other hospitals.
- **NABH requirement:** a complete, signed discharge summary for every inpatient episode.
- **Legal:** the authoritative summary of diagnosis, course, treatment, and discharge condition.
- **Clinical:** carries the longitudinal record forward to the next encounter.
- **Business rationale:** **auto-generated from structured data** — doctor verifies, doesn't retype; cuts documentation time and transcription error.

## 3. Trigger
`… Progress Notes → Reassessment → Discharge decision (Plan="discharge", Form 13 BR-6 → DISCHARGE_PLANNED) → generate draft → doctor review → sign → billing clearance → patient leaves → MRD archive`. Reuses the existing `/plan-discharge` entry.

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Treating Doctor | generate, review, sign | `DOCTOR` |
| Consultant | review, approve, amend | `DOCTOR` (consultant) |
| Nurse | discharge checklist | `NURSE` |
| Pharmacy | discharge-med review | `PHARMACIST` |
| Billing | financial clearance | `RECEPTIONIST`/billing capacity |
| MRD | archive | `MRD_OFFICER` (gap) |
| Patient | receives copy | — (public/print) |
| Hospital Admin | read | `HOSPITAL_ADMIN` |

No new role gaps.

## 5. Sections → storage (auto-populated where marked)
| § | Section | Source | Storage |
|---|---|---|---|
| A | Patient info | `Patient`/`IpdAdmission`/`IpdBedHistory` | **auto, read-only** |
| B | Admission diagnosis (provisional) | Form 07 `clinical_assessment` | **auto-import** → `admission_diagnosis` (new); doctor may evolve |
| C | Chief complaints | Form 07 | **auto-import**; edit via amendment |
| D | **Hospital course** | `DoctorRound` progress notes + reassessments + procedures + labs | **AI draft** (§8) → `hospital_course` (new); doctor edits/approves |
| E | Investigations | `LabOrder`/`RadiologyOrder` results | **auto-list** → `discharge_investigations` (new) |
| F | Procedures | `OtBooking`, dialysis, transfusion (Blood Bank), ICU stay | **auto-import** → `discharge_procedures` (new) |
| G | Treatment given | IV fluids (Form 10), meds (`DoctorOrder`), O₂, nursing | **auto-compile** → `treatment_given` *(exists)* |
| H | Final diagnosis | doctor confirms (ICD-ready via `DiagnosisMaster`) | `final_diagnosis` *(exists)* + secondary/comorbid |
| I | Discharge medication | Pharmacy + `DoctorOrder`; doctor reviews | **auto-fetch** → `discharge_medications` (new) |
| J | Follow-up advice | doctor | `follow_up_date` *(exists)* + `discharge_instructions` (new) |
| K | Condition at discharge | Stable/Improved/Referred/Critical/Deceased | `condition_at_discharge` (new) |
| L | Doctor signature | digital sig + timestamp + reg no | `signed_by`/`signed_at` (new) via Signature service |

## 6. Database Design
**Extend `DischargeSummary`:** add **`hospital_id`, `patient_id`, `doctor_id`** (Read-first-2), `admission_diagnosis`, `hospital_course`, `condition_at_discharge`, `status` (DRAFT/SIGNED/AMENDED), `version`, `amended_from_id`, `signed_by`, `signed_at`. Keep `final_diagnosis`, `treatment_given`, `follow_up_date`.
**Related (new, all `hospital_id`-scoped, auto-populated — no manual re-entry):**
- `discharge_medications` (`id, hospital_id, discharge_summary_id, medicine, dose, frequency, duration, instructions, source_order_id`)
- `discharge_investigations` (`id, hospital_id, discharge_summary_id, type LAB/RADIOLOGY/ECG, name, key_finding, source_id`)
- `discharge_procedures` (`id, hospital_id, discharge_summary_id, procedure, date, source_id`)
- `discharge_instructions` (`id, hospital_id, discharge_summary_id, category, text`)
- FK `patient_id → patients`, `admission_id → ipd_admissions`. Index `(hospital_id, admission_id)`.

## 7. Business Rules
- **BR-1** Cannot create without an **active admission** (`ADMITTED`/`DISCHARGE_PLANNED`).
- **BR-2** Cannot **finalize/sign** until: progress notes complete · pending investigations reviewed · final diagnosis entered · discharge meds reviewed.
- **BR-3** **Billing must confirm financial clearance** before the patient physically leaves — but the **clinical summary remains available regardless of payment** (clinical ≠ financial gate; reuses existing `Billing` wiring).
- **BR-4** After signing → **read-only**; corrections require an **amendment**.
- **BR-5** Every amendment → new **version**, previous archived, audit entry (same pattern as Form 13).
- **BR-6** Every query filters `hospital_id`; every `{id}` validates ownership *(fixes the current `hospital_id`-less keying)*.

## 8. Auto-generation Engine
Collects structured data across the stay — Registration · Admission · Assessment (Form 07) · Progress Notes (Forms 11/13) · Lab · Radiology · OT · Pharmacy · Vitals · Nursing · Fluids (Form 10) — and assembles a **draft**. The **Hospital Course** narrative extends the existing `/hospital/cdss/smart-summary` (already summarizes notes; Form 13 extends it to full-admission) into a discharge narrative. **Doctor verifies, doesn't retype** — the 95%-auto target.

## 9. APIs
Under `/hospital/discharge-summary` (reuse existing plan/confirm/pdf on `IpdAdmissionController`). Every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/discharge-summary/generate/{admissionId}` | DOCTOR | build auto-draft (§8) |
| GET | `/hospital/discharge-summary/{admissionId}` | DOCTOR, NURSE, PHARMACIST, ADMIN | view |
| PUT | `/hospital/discharge-summary/{id}` | DOCTOR | edit draft |
| POST | `/hospital/discharge-summary/{id}/sign` | DOCTOR, CONSULTANT | sign (BR-2 gate) |
| POST | `/hospital/discharge-summary/{id}/amend` | DOCTOR, CONSULTANT | versioned amendment |
| GET | `/hospital/discharge-summary/{id}/pdf` | all (patient copy) | **exists** — PDF |

## 10. Permissions
| Role | Create | Edit | Sign | View |
|---|---|---|---|---|
| Treating Doctor | Yes | Draft | Yes | Yes |
| Consultant | Yes | Amendment | Yes | Yes |
| Nurse | Checklist only | No | No | Read |
| Pharmacy | Med review | No | No | Relevant section |
| Billing | Financial clearance | No | No | Limited |
| MRD | No | No | No | Full (archived) |

Matches §9 `@PreAuthorize`.

## 11. Notifications
Discharge initiated → team · pharmacy review pending · billing clearance pending · nurse checklist pending · summary signed · patient ready · MRD archive pending. Reuse `WebSocketConfig`; billing/pharmacy events already fire.

## 12. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/discharge-summary.html` *(the existing PDF endpoint's template)*: logo, UHID/IPD, admission/discharge dates, diagnoses, hospital course, investigations, procedures, meds, follow-up, doctor signature + reg no, QR, version. NABH-standard yet patient-friendly.

## 13. AI & Smart Enhancements
- **Automatic draft generation** — §8; extend smart-summary. Doctor reviews, not retypes.
- **Medication reconciliation** — compare admission vs current vs discharge meds; flag omissions/duplications/conflicts. **Extend `CdssEvaluationService`** (`evaluatePrescription` already has the drug context) — don't build a parallel checker.
- **Follow-up reminder engine** — schedule follow-up appointment + investigation/med reminders via existing [`WhatsAppService`](../../backend/src/main/java/com/hms/service/whatsapp/WhatsAppService.java)/SMS/email (same delivery as Form 03).
- **Patient-friendly summary** — generate a plain-language second version alongside the clinical one.

## 14. Validation
Requires active admission (BR-1); sign blocked until BR-2 checklist passes (server-enforced); `condition_at_discharge` ∈ enum; discharge date ≥ admission date; signed summary immutable (amend only); auto-imported sections read-only at source. Server-side gates only.

## 15. Audit Logs
Via `AuditLogService` (`entity_type="DISCHARGE_SUMMARY"`): generated · edited · signed · amended (version, old→new) · billing-clearance state · archived — user, role, timestamp, IP. Amendment version chain is the audit centerpiece (BR-5).

---

## Module & workflow placement
- **Owning module:** Doctor → Discharge Management (the Discharge Engine — final intelligence layer).
- **Creates/extends:** `DischargeSummary` (+`hospital_id`/`patient_id`/`doctor_id` fix), `discharge_medications`/`investigations`/`procedures`/`instructions`. **Reads:** Form 07 assessment, `DoctorRound` notes (Forms 11/13), `LabOrder`/`RadiologyOrder`, `OtBooking`, `DoctorOrder`/Pharmacy, `VitalSigns`, `Billing`. **Updates:** `IpdAdmission.status` (→ DISCHARGED on confirm). **Prints:** discharge summary. **Archives:** MRD (Form 02 checklist `discharge_summary` — likely already an item).
- **Feeds into:** Patient · MRD · Billing/TPA · Follow-up Mgmt · Reports · next admission (longitudinal record). **Fed by:** every clinical module of the stay.
- **New this form implies (add to README):** **`DischargeSummary` schema fix + expansion** — add `hospital_id`/`patient_id`/`doctor_id` (**tenant-isolation gap**), plus `admission_diagnosis`/`hospital_course`/`condition_at_discharge`/sign-amend lifecycle · **`discharge_medications`/`investigations`/`procedures`/`instructions`** auto-populated tables · **Discharge auto-generation engine** (extends smart-summary) · **medication reconciliation** (extends CDSS) · **follow-up reminder engine** (reuses WhatsAppService).
