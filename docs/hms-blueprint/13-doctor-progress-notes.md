# Form Spec — Doctor's Daily Progress Notes / SOAP Continuation Sheet (Clinical Documentation Engine)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/IPD/13/2026* (2026-07-01) |
| **Existing code?** | **already exists — this IS `DoctorRound`.** [`DoctorRound`](../../backend/src/main/java/com/hms/entity/DoctorRound.java) is the SOAP note (`subjective`/`objective`/`assessment`/`plan`, `round_date_time`, `next_round_at`) with [`DoctorRoundService`](../../backend/src/main/java/com/hms/service/hospital/DoctorRoundService.java) (`logRound`/`getRoundsHistory`). **Same engine as [Form 11 Reassessment](./11-patient-reassessment.md).** Pulls `VitalSigns` (Form 09) + labs for the O; writes `DoctorOrder`→Lab/Radiology for the P; `DISCHARGE_PLANNED` transition for discharge; extends `/hospital/cdss/smart-summary` for AI summary. |

> **Read first — do NOT create a `progress_note` table. This is the same Clinical Progress Engine as Form 11.**
> The daily progress note and the reassessment ([Form 11](./11-patient-reassessment.md)) are **the same
> object viewed two ways**, both already modelled by [`DoctorRound`](../../backend/src/main/java/com/hms/entity/DoctorRound.java):
> - **Daily Progress Note** = a `DoctorRound` with `assessment_type = DAILY_ROUND` (the routine journal entry).
> - **Reassessment** = a `DoctorRound` with `assessment_type ∈ {EMERGENCY_REVIEW, POST_SURGERY, ICU_REVIEW, FOLLOW_UP}`.
>
> Building a separate `progress_note` **and** `patient_reassessment` **and** keeping `DoctorRound` would
> fork one clinical timeline into three. **They are one engine.** This spec adds to that engine only what
> Form 11 didn't: (a) the **sign/amend lifecycle** (`DoctorRound` has no `status`/`signed`/`version` today —
> `logRound` just inserts), (b) the **`progress_order` link table** (note ↔ generated orders), and (c) the
> **smart-linking "command center"** — parsing the Plan into orders/discharge/transfer actions.

---

## 1. Form Overview
- **Department:** Doctor (primary); Consultant, Resident, Nursing, ICU, MRD (secondary)
- **Module:** **Doctor → Progress Notes → Clinical Timeline** (live EMR, not PDF)
- **Filled By:** Treating Doctor / Resident (every day, often multiple times)
- **Reviewed By:** Consultant (amendment/verification); Nurse reads instructions
- **Archived By:** MRD
- **Lifecycle:** many per admission; append-only; permanent after archival
- **NABH clause:** COP/MOI — the chronological medical record; core medico-legal EMR document.

## 2. Purpose
- **Hospital use:** the doctor's daily journal — the complete story of the hospitalization.
- **NABH requirement:** timed, signed, chronological progress documentation for every admission day.
- **Legal:** the primary record courts / insurers / medical councils rely on — append-only integrity is essential.
- **Clinical:** SOAP drives continuity across shifts and consultants.
- **Business rationale:** the **command center** of clinical care — each note *does* things (orders, discharge), not just narrates.

## 3. Trigger
`Admission → Initial Assessment (Form 07) → treatment → progress note → modification → progress note → recovery → Discharge Summary`. One entry per round/review; repeats the whole admission. Same append-only timeline as Form 11.

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Treating Doctor | write daily notes, sign | `DOCTOR` |
| Consultant | review, amend/verify | `DOCTOR` (consultant) |
| Resident Doctor | add updates | `DOCTOR` |
| Nurse | read instructions | `NURSE` |
| MRD | archive | `MRD_OFFICER` (gap) |
| Hospital Admin | read | `HOSPITAL_ADMIN` |

No new role gaps.

## 5. Sections → storage (on the extended `DoctorRound`)
| Section | Capture | Storage |
|---|---|---|
| Patient info | UHID, IPD no, name, consultant, ward, bed, admission date | **auto-fill** from `IpdAdmission`/`Patient`/`IpdBedHistory` |
| Note date/time/doctor | auto | `round_date_time` *(exists)* + `doctor_id`/`doctor_name` *(exists)* |
| **S**ubjective | patient-reported (pain reduced, fever continues…) | `subjective` *(exists)* |
| **O**bjective | latest BP/pulse/temp/RR/SpO₂ + labs **auto-pulled**, plus exam findings | `objective` *(exists)* + read `VitalSigns` (Form 09) + `LabOrder` results |
| **A**ssessment | improving / stable / sepsis improving… | `assessment` *(exists)* |
| **P**lan | continue abx / repeat CBC / CXR tomorrow / shift to oral / discharge | `plan` *(exists)* → **smart-linked to orders** (§9) |

## 6. Database Design
**Extend `DoctorRound`** (the shared Clinical Progress Engine table — same extension Form 11 opened): add
`assessment_type VARCHAR(30)` (DAILY_ROUND default), `status VARCHAR(12)` (DRAFT/SIGNED/AMENDED),
`version INT`, `amended_from_id` (self-FK — amendments chain, never overwrite), `signed_by`/`signed_at`,
`clinical_status JSON`/`clinical_impression` (Form 11). Keep `subjective/objective/assessment/plan`.
**`progress_order`** (new junction — the note→action link the form asks for): `id, hospital_id,
progress_note_id (→ doctor_rounds.id), order_type (LAB/RADIOLOGY/MEDICATION/DISCHARGE/TRANSFER),
reference_id (→ DoctorOrder/LabOrder/RadiologyOrder/DischargeSummary), created_at`.
- Append-only; index `(hospital_id, ipd_admission_id, round_date_time)` for the timeline. `hospital_id` on every row.

## 7. Business Rules
- **BR-1** Progress notes are **append-only** — never overwrite (extends `logRound`, which already only inserts).
- **BR-2** Every note carries **version + timestamp + doctor** (add `version`/`signed_by`/`signed_at`).
- **BR-3** A **signed** note cannot be deleted — corrections are **amendments** (`status=AMENDED`, new row via `amended_from_id`).
- **BR-4** `Plan` mentions Lab → auto-generate **`LabOrder`** (via `DoctorOrder` chain); link in `progress_order`.
- **BR-5** `Plan` mentions Radiology → auto-create **`RadiologyOrder`**; link in `progress_order`.
- **BR-6** `Plan` mentions Discharge → **start Discharge workflow** (`IpdAdmission → DISCHARGE_PLANNED` + `DischargeSummary`, Form 02 downstream).
- **BR-7** Medication change ("stop Ceftriaxone", "shift to oral") → **`DoctorOrder` transition after confirmation** (via `evaluatePrescription`), linked in `progress_order`.
- **BR-8** Every query filters `hospital_id`; every `{id}` validates ownership.

## 8. Clinical Timeline
`getRoundsHistory` already returns notes chronologically — render as one timeline interleaving progress notes, initial assessment (Form 07), reassessments (Form 11), labs, radiology, discharge. **Same timeline** as Form 11 §6 (not a second one). Everything chronological, one source of truth.

## 9. Smart Linking — the "command center" (extend CDSS/order chain)
Parse the **Plan** into structured actions (clinician confirms before commit):
| Doctor writes | System does |
|---|---|
| "Repeat CBC" | create CBC `LabOrder` (BR-4) |
| "Chest X-ray tomorrow" | create scheduled `RadiologyOrder` (BR-5) |
| "Shift to oral antibiotic" | update medication `DoctorOrder` (BR-7) |
| "Stop Ceftriaxone" | set that `DoctorOrder.status=STOPPED` after confirm (BR-7) |
| "Transfer to ICU" | initiate ICU transfer (admission workflow) |
| "Fit for discharge tomorrow" | begin discharge planning (BR-6) |
Implemented as a suggestion layer (NLP/keyword → candidate order); **never auto-commits** — confirmation required, consistent with existing CDSS "recommendation, not action" framing.

## 10. APIs
Under `/hospital/progress-notes` (co-located with rounds; same engine as Form 11's `/reassessment`). Every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/progress-notes` | DOCTOR | create note (extends `logRound`) |
| GET | `/hospital/progress-notes/{admissionId}` | DOCTOR, NURSE, ADMIN | notes for patient |
| POST | `/hospital/progress-notes/{id}/sign` | DOCTOR | sign (DRAFT→SIGNED) |
| PUT | `/hospital/progress-notes/{id}/amend` | DOCTOR, CONSULTANT | amendment (new versioned row) |
| GET | `/hospital/progress-notes/timeline/{admissionId}` | DOCTOR, NURSE | unified clinical timeline |
| GET | `/hospital/cdss/smart-summary/{admissionId}` | DOCTOR | **exists** — AI multi-note summary |

## 11. Permissions
| Role | Create | Edit | View |
|---|---|---|---|
| Doctor | Yes | Draft only (then amend) | Yes |
| Consultant | Yes | Amendment | Yes |
| Nurse | No | No | Read |
| Hospital Admin | Read | No | Read |
| MRD | No | No | Archived |

Matches §10 `@PreAuthorize`.

## 12. Notifications
Note added → Nurse · treatment changed → Pharmacy · lab ordered → Lab · radiology ordered → Radiology · discharge planned → Billing. Reuse `WebSocketConfig` + order-chain events (already fire on `DoctorOrder`).

## 13. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/progress-notes.html`: chronological SOAP entries with date/time/doctor, signature, QR, version. Paper-faithful but cleaner. Copy: file (MRD; Form 02 checklist `progress_notes` item).

## 14. AI Enhancements — extend existing smart-summary
- **Clinical summary** — condense N notes into a 5-day narrative (fever improved → abx changed → culture negative → discharge planned). **Extend** `/hospital/cdss/smart-summary` (already summarizes 24 h) to the full admission.
- **Trend detection** — temperature 39.5→38.8→37.9 → "improving" (reuses `VitalSigns` trend infra, Form 09).
- **Missing-documentation warning** — no progress note today → nudge doctor.
- **Duplicate-note detection** — near-identical copied notes → warn (quality guardrail).
All recommendations only.

## 15. Enterprise — Clinical Documentation Engine
Structured, not a text area: specialty SOAP templates (Medicine/Surgery/Peds/Ortho/ICU) · voice-to-text dictation · terminology autocomplete (via `DiagnosisMaster`) · **problem-oriented notes** (each active `patient_diagnosis` gets its own progress section) · automatic note↔investigation/prescription/procedure/referral linkage (`progress_order` + Form 11 `patient_referral`) · full amendment version history · e-signatures with consultant verification. These are the maturity roadmap on top of the reconciled engine.

## 16. Audit Logs
Via `AuditLogService` (`entity_type="PROGRESS_NOTE"`): created · signed · amended (old→new, `amended_from_id`) · order auto-generated (`progress_order`) · discharge-planned — user, role, timestamp, IP. Append-only integrity is the audit centerpiece.

---

## Module & workflow placement
- **Owning module:** Doctor → Progress Notes (the Clinical Progress / Documentation Engine — **shared with Form 11**).
- **Creates:** `DoctorRound` rows (extended), `progress_order` links. **Updates:** `DoctorOrder`/`LabOrder`/`RadiologyOrder` (smart-linking), `IpdAdmission.status` (discharge/ICU), amendments. **Reads:** `VitalSigns`, lab results, `Patient`/`IpdAdmission`. **Prints:** progress notes. **Archives:** MRD.
- **Feeds into:** Nursing · Pharmacy · Lab · Radiology · Discharge Planning · Billing · CDSS · MRD · Reports. **Fed by:** Initial Assessment (Form 07) · Vitals (Form 09) · Reassessment (Form 11) · orders.
- **New this form implies (add to README):** **Consolidation** — `DoctorRound` = the single Clinical Progress Engine behind **both** daily progress notes (Form 13) **and** reassessment (Form 11); do not build `progress_note`/`patient_reassessment` as separate tables · **sign/amend lifecycle** on `DoctorRound` (`status`/`version`/`amended_from_id`/`signed_by`) · **`progress_order`** note↔action link table · **smart-linking Plan parser** (extends CDSS/order chain, confirm-before-commit).
