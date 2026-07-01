# Form Spec — Pre-Anaesthesia Assessment (PAC) / Pre-operative Safety Engine

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/OT/01/2026* (2026-07-01) |
| **Existing code?** | **PAC itself is new; the surgical spine around it is half-built.** [`OtBooking`](../../backend/src/main/java/com/hms/entity/OtBooking.java) already = the surgery-scheduling record; [`OtChecklist`](../../backend/src/main/java/com/hms/entity/OtChecklist.java) already = the **WHO Surgical Safety Checklist** (`sign_in`/`time_out`/`sign_out`); [`OtService`](../../backend/src/main/java/com/hms/service/hospital/OtService.java) has `scheduleBooking`/`updateStatus`/`signChecklist`. PAC plugs in **before** scheduling. Reuses `PatientAllergy` (banner), Form 07 patient-scoped history, `LabOrder`/`RadiologyOrder` (investigations), `CdssEvaluationService.evaluateLabResult` (abnormal flags), Consent Engine (Form 05), Blood Bank (Form 01 gap). |

> **Read first — three grounding facts that change the design.**
> **(1) `surgery_request_id` already exists as `OtBooking`.** Don't invent a surgery-request table —
> the OT booking *is* the surgical request/schedule. PAC references `ot_booking_id`.
> **(2) The WHO Surgical Safety Checklist is already built** as [`OtChecklist`](../../backend/src/main/java/com/hms/entity/OtChecklist.java)
> (three WHO phases: sign-in / time-out / sign-out). The workflow's "PAC → OT Scheduling → WHO Checklist
> → Surgery" therefore maps to **`pre_anaesthesia_assessment` (new) → `OtBooking` (exists) → `OtChecklist`
> (exists)**. PAC is the *missing front half* of an otherwise partly-built Surgical Workflow Engine.
> **(3) `OtService.scheduleBooking` has NO pre-op gate today** — it creates a booking directly. PAC's core
> job (BR-1/BR-3) is to become the **fitness precondition** on scheduling: only a `FIT`/`FIT_WITH_PRECAUTIONS`
> PAC (plus consent + blood + investigations = **Surgical Readiness**, §13) may reach the OT schedule.

---

## 1. Form Overview
- **Department:** Anaesthesia (primary); OT, Surgeon, Nursing, ICU, Lab, MRD (secondary)
- **Module:** **Operation Theatre → Pre-operative Assessment → Pre-Anaesthesia Evaluation**
- **Filled By:** Anaesthesiologist (full assessment)
- **Reviewed By:** Surgeon (fitness), OT Nurse (checklist), ICU (high-risk)
- **Archived By:** MRD
- **Lifecycle:** created when surgery planned; gates OT scheduling; permanent after archival
- **NABH clause:** COP/PSQ — anaesthesia risk assessment; no elective surgery without documented fitness.

## 2. Purpose
- **Hospital use:** determines whether the patient is medically fit for the planned anaesthesia (GA/spinal/epidural/regional/local/sedation).
- **NABH requirement:** documented pre-anaesthetic evaluation before elective surgery.
- **Legal:** a clinical-safety **and** medico-legal record — fitness decision is accountable.
- **Clinical:** airway + ASA + systemic review drive the anaesthesia plan and prevent intra-op crises.
- **Business rationale:** the front of a **Pre-operative Safety Engine** — only fully-cleared patients reach OT.

## 3. Trigger
`Admission → diagnosis → surgery planned → pre-op investigations → PAC → fitness decision → (FIT) OT scheduling → WHO checklist → surgery`. No PAC → no elective surgery (life-threatening emergency exception per policy).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Anaesthesiologist | full assessment, ASA, fitness, approve | `DOCTOR` + **`is_anaesthetist`** flag (gap) |
| Surgeon | review fitness (read) | `DOCTOR` |
| OT Nurse | checklist confirmation | `NURSE` (OT) |
| OT Coordinator | scheduling | `NURSE`/coordinator capacity |
| ICU Doctor | high-risk notes | `DOCTOR` (ICU) |
| MRD | archive | `MRD_OFFICER` (gap) |
| Hospital Admin | read | `HOSPITAL_ADMIN` |

Role gap: **Anaesthesiologist** — a capacity flag on `DOCTOR` (like CMO, Form 12). Note `OtBooking.anesthetist_name` is a free **String** today — should become `anaesthesiologist_id` FK.

## 5. Sections → storage
| § | Section | Capture | Storage / source |
|---|---|---|---|
| A | Patient info + BMI | UHID/IPD/name/age/gender/height/weight/**BMI**/surgeon/surgery/date | **auto** from `Patient`/`OtBooking`; height/weight from `NurseAssessment`/`VitalSigns` (Form 09) |
| B | Medical history | DM/HTN/asthma/COPD/cardiac/renal/hepatic/thyroid… | **auto-import** Form 07 patient-scoped history (persists across admissions) |
| C | Previous anaesthesia history | prior surgery/anaesthesia, complications, difficult intubation, MH, PONV | new **`anaesthesia_history`** (patient-scoped — persists across admissions, EMR-history family) |
| D | Drug & allergy history | drug/food/latex allergy | **read `PatientAllergy`** → permanent allergy banner (reuse Form 06/07) |
| E | Airway assessment | mouth opening, Mallampati, neck movement, dentition, facial abnormality, difficult-airway prediction | new **`airway_assessment`** (structured) |
| F | Systemic examination | CVS/Resp/CNS/Renal/Hepatic | structured + notes on `pre_anaesthesia_assessment` |
| G | Investigations | CBC/sugar/LFT/KFT/ECG/CXR/coag/blood group | **auto-fetch `LabOrder`/`RadiologyOrder`**; abnormal via `evaluateLabResult` |
| H | ASA classification | ASA I–VI | `asa_class` (mandatory, BR-2) |
| I | Anaesthesia plan | GA/spinal/epidural/local/regional/sedation + airway/monitoring/post-op pain | `planned_anaesthesia` |
| J | Fitness decision | Fit / Fit-with-precautions / Further-evaluation / Deferred | `fitness_status` (only FIT* allows scheduling, BR-3) |

## 6. Database Design
**`pre_anaesthesia_assessment`** (new): `id, public_id, hospital_id, patient_id, admission_id, ot_booking_id (→ ot_bookings), anaesthesiologist_id, asa_class, airway_assessment (or FK), systemic_assessment, fitness_status, planned_anaesthesia, remarks, status (DRAFT/APPROVED), assessment_date, created_at`.
**Related (new):** `airway_assessment` (Mallampati/mouth-opening/neck/dentition…), **`anaesthesia_history`** (patient-scoped, cross-admission), `preop_investigations` (links to `LabOrder`/`RadiologyOrder` results + abnormal flag), `pac_risk_factors`.
- FK `patient_id → patients`, `admission_id → ipd_admissions`, `ot_booking_id → ot_bookings`. `hospital_id` on every row; index `(hospital_id, admission_id)` and `(hospital_id, fitness_status)`.

## 7. Business Rules
- **BR-1** PAC **mandatory before elective surgery** — gate `OtService.scheduleBooking` (currently ungated).
- **BR-2** **ASA classification mandatory** before surgery.
- **BR-3** `fitness_status ∈ {Further-evaluation, Deferred}` → **surgery cannot be scheduled** (only FIT/FIT-with-precautions pass).
- **BR-4** Abnormal investigations → **alert before** fitness approval (`evaluateLabResult`).
- **BR-5** Allergy present → **banner on OT dashboard + anaesthesia screen + medication screen** (reuse `PatientAllergy` propagation, like the risk-badge broadcast).
- **BR-6** Blood anticipated → **verify blood availability + blood consent (Form 01, `consent_type=BLOOD`) + cross-match** before surgery (Blood Bank gap; Consent Engine exists).
- **BR-7** Every query filters `hospital_id`; every `{id}` validates ownership.

## 8. Pre-operative Dashboard
Today's scheduled surgeries · PAC pending · PAC completed · **Not Fit** · high-ASA (III+) · blood-required · ICU-backup-required · pending investigations. All `WHERE hospital_id = current`; live via `WebSocketConfig`.

## 9. APIs
Under `/hospital/ot/pre-anaesthesia`; every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/ot/pre-anaesthesia` | ANAESTHETIST | create PAC |
| GET | `/hospital/ot/pre-anaesthesia/{admissionId}` | ANAESTHETIST, SURGEON, OT NURSE | view |
| PUT | `/hospital/ot/pre-anaesthesia/{id}` | ANAESTHETIST | edit |
| POST | `/hospital/ot/pre-anaesthesia/{id}/approve` | ANAESTHETIST | set fitness (gates scheduling) |
| GET | `/hospital/ot/pre-anaesthesia/dashboard` | ANAESTHETIST, SURGEON, OT NURSE | pre-op dashboard |

## 10. Permissions
| Role | Create | Edit | Approve | View |
|---|---|---|---|---|
| Anaesthesiologist | Yes | Yes | Yes | Yes |
| Surgeon | No | No | No | Yes |
| OT Nurse | Checklist only | No | No | Yes |
| ICU Doctor | No | Notes | No | Relevant |
| MRD | No | No | No | Archived |

Matches §9 `@PreAuthorize`.

## 11. Notifications
PAC pending · investigations incomplete · declared fit · **not fit** · blood arrangement required · ICU bed required post-op · ready for scheduling. Reuse `WebSocketConfig` + `CdssAlertLog`.

## 12. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/pre-anaesthesia.html`: patient details, medical history, airway assessment, ASA, investigation summary, anaesthesia plan, fitness decision, anaesthesiologist signature, QR, version. Copy: file (MRD).

## 13. AI & Smart Enhancements — the Surgical Readiness engine
- **Difficult-airway prediction** — Mallampati + mouth-opening + neck mobility + BMI → "potential difficult airway; prepare advanced equipment." Rule set (fits CDSS pattern).
- **Investigation review** — auto-highlight abnormal pre-op results (`evaluateLabResult`).
- **Surgical Readiness Score** — aggregate PAC-fit + consent (Form 05) + blood available (Form 01) + investigations complete + fitness approved → `Surgical Readiness 94% — READY FOR OT`. **This is the Pre-operative Safety Engine.**
- **Smart OT Scheduler** — only patients whose readiness passes all mandatory checks appear as *Ready for Surgery* on the OT schedule (feeds `OtService`).

## 14. Validation
`asa_class` ∈ {I–VI} mandatory; `fitness_status` ∈ enum; airway fields structured; BMI computed server-side from height/weight; abnormal-investigation acknowledgement required before approve; blood/consent verified when blood anticipated; scheduling blocked unless readiness passes. Server-side only.

## 15. Audit Logs
Via `AuditLogService` (`entity_type="PRE_ANAESTHESIA"`): created · edited · ASA set · fitness decided · abnormal-investigation acknowledged · readiness computed · scheduling gate pass/block — user, role, timestamp, old→new, IP.

---

## Module & workflow placement
- **Owning module:** Operation Theatre → Pre-operative Assessment (front of the Surgical Workflow / Pre-operative Safety Engine).
- **Creates:** `pre_anaesthesia_assessment`, `airway_assessment`, `anaesthesia_history` (patient-scoped), `preop_investigations`, `pac_risk_factors`. **Gates:** `OtService.scheduleBooking` (fitness precondition). **Reads:** `Patient`/`OtBooking`, Form 07 history, `PatientAllergy`, `LabOrder`/`RadiologyOrder`, `NurseAssessment`/`VitalSigns` (height/weight), Consent (Form 05), Blood Bank (Form 01). **Feeds:** `OtChecklist` (WHO, exists). **Prints:** PAC. **Archives:** MRD.
- **Feeds into:** OT Scheduling · WHO Checklist (`OtChecklist`) · Surgeon · ICU · Anaesthesia · Blood Bank · Consent · MRD. **Fed by:** Admission · Assessment (Form 07) · Lab/Radiology · Vitals.
- **New this form implies (add to README):** **Surgical Workflow / Pre-operative Safety Engine** — note `OtBooking` (schedule) + `OtChecklist` (**WHO checklist already built**) exist; PAC is the missing pre-op gate · **`OtService.scheduleBooking` must become PAC/readiness-gated** (currently ungated) · **patient-scoped `anaesthesia_history`** (EMR-history family, persists across admissions) · **Anaesthesiologist** capacity flag on `DOCTOR` + convert `OtBooking.anesthetist_name` String → `anaesthesiologist_id` FK · **Surgical Readiness Score** aggregator.
