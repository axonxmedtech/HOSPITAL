# Form Spec — Admission History & Initial Assessment (Clinical Foundation / EMR backbone)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/IPD/07/2026* (2026-07-01) |
| **Existing code?** | **partially exists.** Reuses [`PatientAllergy`](../../backend/src/main/java/com/hms/entity/PatientAllergy.java) + [`AllergyMaster`](../../backend/src/main/java/com/hms/entity/AllergyMaster.java), [`DiagnosisMaster`](../../backend/src/main/java/com/hms/entity/DiagnosisMaster.java), [`VitalSigns`](../../backend/src/main/java/com/hms/entity/VitalSigns.java)/[`NurseAssessment`](../../backend/src/main/java/com/hms/entity/NurseAssessment.java), [`DoctorOrder`](../../backend/src/main/java/com/hms/entity/DoctorOrder.java) → [`LabOrder`](../../backend/src/main/java/com/hms/entity/LabOrder.java)/[`RadiologyOrder`](../../backend/src/main/java/com/hms/entity/RadiologyOrder.java). **New:** `clinical_assessment` + normalized history tables. Distinct from [`DoctorRound`](../../backend/src/main/java/com/hms/entity/DoctorRound.java) (ongoing SOAP progress notes). |

> **Read first — the EMR spine + one reconciliation.**
> This is the **Clinical Foundation Module**, not a form. The architect's directive: build it as a
> *structured clinical data model* that becomes the backbone of the longitudinal EMR — history,
> allergies, exam, diagnosis, treatment plan become **reusable data**, not scanned text.
> **Two scopes, don't conflate them:**
> - **Admission-scoped snapshot** → `clinical_assessment` (this admission's chief complaint, HPI,
>   provisional dx, plan). One per admission, finalized read-only.
> - **Patient-scoped longitudinal history** → `patient_allergy` *(exists)*, `patient_medical_history`,
>   `patient_surgical_history`, `patient_family_history`, `patient_social_history`. Keyed by
>   `patient_id`, **reused across every future admission** — the EMR backbone. The assessment
>   *references/updates* these; it doesn't re-store them.
> **Reconciliation:** [`Patient.medical_history`](../../backend/src/main/java/com/hms/entity/Patient.java#L116)
> is a single 1000-char string today — the crude precursor of these structured tables. Migrate it
> into `patient_medical_history` (and keep the string as a deprecated free-text fallback, or drop
> after backfill). Flag, don't silently duplicate.

---

## 1. Form Overview
- **Department:** Doctor (primary); Nursing, IPD, Emergency, ICU, MRD (secondary)
- **Module:** **Doctor → Initial Clinical Assessment** (Clinical Foundation)
- **Filled By:** Admitting Doctor / Resident
- **Approved By:** Consultant (finalize)
- **Referenced By:** Nurse (care instructions)
- **Archived By:** MRD
- **Lifecycle:** permanent; read-only after finalize; amendments versioned
- **NABH clause:** AAC/COP — initial medical assessment within defined timeframe; documented, dated, signed.

## 2. Purpose
- **Hospital use:** first complete medical assessment; drives every downstream module.
- **NABH requirement:** documented initial assessment (history, exam, provisional dx, care plan) by a qualified doctor within policy time.
- **Legal:** the clinical basis for all subsequent orders; incomplete = indefensible care.
- **Clinical:** structured data powers decision support, drug-safety, referrals, discharge summary, future admissions.
- **Business rationale:** eliminates duplicate documentation; becomes the EMR backbone.

## 3. Trigger
`Registration → IPD Admission → Nurse Initial Assessment → **Doctor Initial Assessment** → History → Examination → Diagnosis → Orders → Treatment Plan`.
**Gates (BR):** without this assessment, lab orders should not proceed (except emergency), medication orders limited, surgery planning blocked (§7 Rule pre-conditions).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Admitting Doctor / Resident | create, edit | `DOCTOR` |
| Consultant | create, edit, **finalize** | `DOCTOR` (consultant flag / assigned doctor) |
| Nurse | reference care instructions (view) | `NURSE` |
| MRD | archive | `MRD_OFFICER` (gap) |
| Hospital Admin | oversight (view) | `HOSPITAL_ADMIN` |
| Patient | none | — |

No new role gaps.

## 5. Fields (sections → storage)
| Section | Content | Storage | Notes |
|---|---|---|---|
| A. Patient info | UHID, IPD no, name, age, gender, consultant, ward, bed, admit date/time | auto-fill (read-only) | from patient + admission |
| B. Chief complaint | free text + terminology | `clinical_assessment.chief_complaint` | min/max length; voice-to-text; ICD suggest `[Future]` |
| C. HPI (onset/duration/progression/severity/assoc/prev tx) | structured template + free text | `clinical_assessment.history_present_illness` | templated |
| D. Past medical history | diabetes, HTN, asthma, CKD, thyroid, TB, heart… | **`patient_medical_history`** (patient-scoped, coded via `DiagnosisMaster`) | reused across admissions |
| E. Past surgical history | surgery, date, hospital, surgeon, complications | **`patient_surgical_history`** (patient-scoped) | reusable |
| F. Drug history | current meds, dose, freq, compliance, OTC, herbal | **`patient_medication_history`** (patient-scoped) | integrate Pharmacy |
| G. Allergy history | drug/food/latex allergies | **`patient_allergy`** *(EXISTS)* + `AllergyMaster` | **red banner if any (BR-4)** |
| H. Family history | DM, HTN, cancer, stroke, heart | **`patient_family_history`** | risk profiling |
| I. Social history | smoking, alcohol, tobacco, occupation, diet, sleep, exercise | **`patient_social_history`** | influences plan |
| J. Physical exam | appearance, temp, pulse, BP, RR, SpO₂, wt, ht, BMI | **`VitalSigns` / `NurseAssessment`** *(EXISTS)* — integrate, don't re-enter | pull latest vitals |
| K. Systemic exam | CVS, RS, CNS, GI, MSK, skin | `systemic_examination` (per-system rows or JSON) | templates + narrative |
| L. Provisional diagnosis | initial dx, coded | `patient_diagnosis` (history) + `clinical_assessment.provisional_diagnosis`, via `DiagnosisMaster` | ICD suggest `[Future]`; **diagnosis history, not overwrite (BR-6)** |
| M. Treatment plan | investigations, meds, procedures, referrals, obs, diet, follow-up | `clinical_assessment.treatment_plan` **+ spawns `DoctorOrder`** | auto-creates downstream orders |

## 6. Business Rules
- **BR-1** Assessment mandatory for every IPD admission (one active per admission).
- **BR-2** Only the assigned doctor / authorized consultant may **finalize**.
- **BR-3** After finalize → **read-only**; changes require an **amendment** (new version + audit).
- **BR-4** `IF any patient_allergy exists THEN` red allergy banner + alert in Pharmacy, Medication Orders, Nursing Station, Doctor Dashboard (patient-level, broadcast).
- **BR-5** `IF treatment plan orders investigations THEN` auto-create `DoctorOrder` → `LabOrder`/`RadiologyOrder` (existing auto-forward chain).
- **BR-6** `IF diagnosis changes THEN` append to `patient_diagnosis` history — never overwrite.
- **BR-7** History sections (D–I) write to **patient-scoped** tables → available in future admissions (fetch-not-retype).
- **BR-8** Vitals (J) sourced from `VitalSigns`/`NurseAssessment`, not re-keyed.
- **BR-9** Every query filters `hospital_id`; every `{id}` validates ownership.

## 7. Database Design
**`clinical_assessment`** (admission-scoped, new): `id, public_id, hospital_id, patient_id, admission_id, doctor_id, chief_complaint, history_present_illness, provisional_diagnosis, treatment_plan, status(DRAFT/FINALIZED/AMENDED/ARCHIVED), version, parent_id, finalized_by, finalized_at, created_at/by, updated_at`. Unique `(hospital_id, admission_id)` active.
**Patient-scoped history (EMR backbone):** `patient_allergy` *(exists)*, `patient_medical_history`, `patient_surgical_history`, `patient_medication_history`, `patient_family_history`, `patient_social_history`, `patient_diagnosis` — all `(id, hospital_id, patient_id, …, source_assessment_id, recorded_at, is_active)`. Coded rows reference `DiagnosisMaster`/`AllergyMaster`.
**`systemic_examination`** (admission-scoped): per-system findings.
- FK: `patient_id → patients`, `admission_id → ipd_admissions`, `doctor_id → doctors`. Tenant + audit + soft-delete throughout.
- **Reconcile:** migrate `Patient.medical_history` string → `patient_medical_history` (§Read-first).

## 8. Workflow
```
admission → DRAFT (doctor fills sections) → [consultant finalize] → FINALIZED (read-only)
FINALIZED → [amendment] → new version (old = AMENDED/superseded), audit trail
FINALIZED → [treatment plan] → spawn DoctorOrders → Lab/Radiology/Pharmacy
FINALIZED → [discharge] → ARCHIVED (MRD; Form 02 checklist: case_sheet item)
```
One assessment drives Vitals · Lab · Radiology · Pharmacy · Nursing plan · Dietician/Physio referral (Form 06 engine) · OT · Billing · MRD.

## 9. APIs
Under `/hospital/clinical-assessments`; every `{id}`/`{patient}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/clinical-assessments` | DOCTOR | create draft |
| GET | `/hospital/clinical-assessments/{id}` | DOCTOR, NURSE, ADMIN | detail |
| PUT | `/hospital/clinical-assessments/{id}` | DOCTOR (draft/amend) | update |
| POST | `/hospital/clinical-assessments/{id}/finalize` | DOCTOR (assigned/consultant) | lock + spawn orders |
| GET | `/hospital/patients/{id}/clinical-history` | DOCTOR, NURSE | longitudinal EMR view |

## 10. Permissions
| Role | Create | Edit | Finalize | View |
|---|---|---|---|---|
| Doctor / Consultant | Yes | Yes | Yes | Yes |
| Nurse | No | No | No | Yes |
| MRD | No | No | No | Archived |
| Hospital Admin | No | No | No | Yes |

Matches §9 `@PreAuthorize`.

## 11. Validation
Chief complaint length bounds; vitals in physiological ranges (temp 35–43 °C, SpO₂ 0–100, BP > 0, pulse 0–300, RR > 0) — reuse vitals module validation; BMI derived (not entered); diagnosis references `DiagnosisMaster`; allergy references `AllergyMaster`; finalize allowed only by assigned doctor of this hospital; server re-validates before finalize.

## 12. Notifications
Assessment pending after admission · consultant review pending · **critical allergy entered** · new diagnosis added · investigation orders generated · assessment finalized. Reuse event/WebSocket infra.

## 13. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/initial-assessment.html`: branding, patient identifiers, date/time, doctor details, structured history, exam findings, diagnosis, treatment plan, digital signature, QR (UHID+admission+assessment id), version. Copy: file (MRD).

## 14. Audit Logs
Via `AuditLogService` (`entity_type="CLINICAL_ASSESSMENT"`): created · opened · reviewed · critical allergy entered · new diagnosis · orders generated · finalized · amended (old→new) · archived — user, role, timestamp, old→new, IP. Amendments preserve prior version (BR-3).

## 15. AI & Smart Enhancements `[Future]`
- **Clinical Decision Support** — e.g. fever + cough + low SpO₂ → suggest respiratory workup.
- **Drug-interaction check** — cross-check current meds × new prescriptions × allergies before allowing medication orders (ties to Pharmacy + `patient_allergy`).
- **ICD-10/11 suggestions** from narrative for doctor review.
- **Smart clinical timeline** — admission → assessment → labs → progress notes (`DoctorRound`) → treatment changes → discharge summary, chronological.

## 16. Missing / Intelligent Features
- Longitudinal EMR view aggregating all admissions' assessments + history.
- Allergy-driven hard stop in medication ordering (patient-safety gate).
- Structured-history reuse: prior admission's medical/surgical/family history pre-loads (editable).
- Diagnosis-history trend (how provisional → final dx evolved).

---

## Module & workflow placement
- **Owning module:** Doctor → Initial Clinical Assessment (Clinical Foundation / EMR backbone).
- **Creates:** `clinical_assessment`, patient-scoped history rows, `patient_diagnosis`, `systemic_examination`; spawns `DoctorOrder`. **Updates:** patient allergy/history tables, allergy banner. **Views:** integrates `VitalSigns`. **Prints:** assessment PDF. **Archives:** MRD (Form 02 `case_sheet`).
- **Feeds into:** Vitals · Lab · Radiology · Pharmacy · Nursing plan · Dietician/Physio referral (Form 06) · OT · Billing · MRD · Clinical notes (`DoctorRound`). **Fed by:** Registration · IPD Admission · Nurse assessment · prior admissions' history.
- **New modules/roles this form implies:** normalized **patient-scoped clinical history tables** (EMR backbone — reconcile `Patient.medical_history` string) · `patient_diagnosis` history · Clinical Decision Support (`[Future]`) — add to README.
