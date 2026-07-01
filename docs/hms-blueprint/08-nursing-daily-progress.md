# Form Spec — Nurses Assessment & Daily Progress Record (Nursing Workflow Engine + MAR)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/IPD/05/2026* (2026-07-01) |
| **Existing code?** | **substantially exists — reconcile, don't rebuild.** MAR = existing [`NurseTask`](../../backend/src/main/java/com/hms/entity/NurseTask.java) (+[`NurseTaskService`](../../backend/src/main/java/com/hms/service/hospital/NurseTaskService.java)); vitals = [`VitalSigns`](../../backend/src/main/java/com/hms/entity/VitalSigns.java); initial assessment = [`NurseAssessment`](../../backend/src/main/java/com/hms/entity/NurseAssessment.java); orders = [`DoctorOrder`](../../backend/src/main/java/com/hms/entity/DoctorOrder.java). **New:** recurring `nursing_progress_note`, `nursing_procedure`, shift-handover. EWS = a scale on the [Clinical Risk Engine](./shared/clinical-risk-engine.md). |

> **Read first — three reconciliations against existing code.**
> **(1) The MAR already exists.** [`NurseTask`](../../backend/src/main/java/com/hms/entity/NurseTask.java)
> is effectively the Medication Administration Record: `doctor_order_id` (NOT NULL), `scheduled_at`/
> `executed_at`/`executed_by`, `status`, `administered_quantity`, `route`, `injection_site`,
> `pre_vitals`, and `NurseTaskService.executeTask(status, notes, qty, route, site, preVitals)`. **Do
> not create the form's `medication_administration` table** — it *is* `NurseTask`. Only gap for
> BR-3: add a structured **`missed_reason`** column (today the reason would land in free-text
> `notes`). This is the *same* `NurseTask` whose `doctor_order_id NOT NULL` constraint Form 06
> flagged — the nullable+`source` fix lets non-medication nursing procedures share the table too.
> **(2) Two different "nursing assessments".** [`NurseAssessment`](../../backend/src/main/java/com/hms/entity/NurseAssessment.java)
> is the **one-time initial** assessment (unique per admission). This form is the **recurring
> per-shift** progress note → new `nursing_progress_note` (many per admission). Distinct records;
> don't overload `NurseAssessment`.
> **(3) EWS is not a new calculator.** Early Warning Score = `scale_type='EWS'` on the
> [Clinical Risk Engine](./shared/clinical-risk-engine.md) (Form 06) — vitals+consciousness →
> score → alert. Reuse the engine's scoring + propagation, don't build a parallel one.

---

## 1. Form Overview
- **Department:** Nursing Station (primary); Doctors, ICU, IPD, Pharmacy, Dietician, Physio, Infection Control, MRD (secondary)
- **Module:** **Nursing Station → Daily Nursing Care → Progress Notes** (live module, 24×7)
- **Filled By:** Staff/Duty Nurse (every shift)
- **Reviewed By:** Head Nurse; Doctor reads observations
- **Audited By:** Quality
- **Archived By:** MRD
- **Lifecycle:** active throughout admission; permanent after archival
- **NABH clause:** COP/NURS — continuous nursing assessment, MAR, shift documentation.

## 2. Purpose
- **Hospital use:** the daily operational nursing record — observations, medications, procedures, communication, handover.
- **NABH requirement:** documented nursing care each shift + a medication administration record.
- **Legal:** doctor-communication + medication logs are key medico-legal evidence.
- **Clinical:** structured nursing data powers alerts, MAR safety, EWS, dashboards.
- **Business rationale:** real-time Nursing Workflow Engine, not a note — the "heartbeat" to the doctor module's "brain".

## 3. Trigger
`Admission → Initial Nursing Assessment (NurseAssessment) → [each shift: Medication Admin → Vital Monitoring → Observation → Doctor Rounds → Shift Handover → Daily Progress] → Discharge`. Active for the whole admission; one progress note **per shift** (BR-1).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Staff Nurse | create, edit current shift | `NURSE` |
| Head Nurse | create, edit, approve | `NURSE` (in-charge flag) |
| Doctor | read observations, review | `DOCTOR` |
| Quality | audit | `QUALITY_OFFICER` (gap) |
| MRD | archive | `MRD_OFFICER` (gap) |
| Hospital Admin | read | `HOSPITAL_ADMIN` |

No new role gaps.

## 5. Fields (sections → storage)
| Section | Content | Storage |
|---|---|---|
| A. Patient info | UHID, IPD no, name, consultant, ward, bed, admit date | auto-fill (read-only) |
| B. Shift info | shift (MORNING/EVENING/NIGHT), start/end, assigned nurse (from login) | `nursing_progress_note.shift`, `nurse_id` |
| C. General condition | stable/critical/conscious/drowsy/unconscious/pain/comfortable/restless | `general_condition`, `pain_score` |
| D. Vital signs | temp, pulse, BP, RR, SpO₂, weight | **`VitalSigns`** *(EXISTS)* — fetch latest; nurse may record new |
| E. Medication administration | medicine, dose, time, route, status (completed/missed/held/delayed) | **`NurseTask`** *(EXISTS = MAR)* via `executeTask` |
| F. Nursing procedures | dressing, catheter/IV care, wound clean, suction, nebulization, O₂, position change, oral care (+time, nurse, remarks) | **`nursing_procedure`** (new) |
| G. Patient complaints | pain, vomiting, fever, breathlessness, bleeding, weakness | `nursing_progress_note.remarks` (structured + free text) |
| H. Doctor communication | doctor name, time, reason, advice, action taken | `nursing_progress_note.doctor_notified/name/advice` |
| I. Patient response | improved/stable/deteriorated/transferred/referred | `nursing_progress_note.patient_response` |
| J. Shift handover | outgoing/incoming nurse, pending tasks, critical alerts, meds due, investigations pending, doctor review pending | **`shift_handover`** (new) |

## 6. Business Rules
- **BR-1** Every admitted patient must have ≥1 nursing progress entry **per shift**.
- **BR-2** Medication administration **must** reference a valid `DoctorOrder` — **never free-text medicine** (already enforced by `NurseTask.doctor_order_id`).
- **BR-3** `IF medication missed/held THEN` **`missed_reason` mandatory** (patient refused / doctor withheld / unavailable / asleep / contraindication) — add column.
- **BR-4** `IF pain_score > threshold (e.g. 7/10) THEN` notify doctor.
- **BR-5** `IF abnormal vital signs THEN` generate alert (via EWS scale, §8).
- **BR-6** `IF patient deterioration (response=DETERIORATED / EWS high) THEN` escalate to doctor immediately.
- **BR-7** Staff nurse may edit only the **current shift**'s note; prior shifts locked (Head Nurse override, audited).
- **BR-8** Every query filters `hospital_id`; every `{id}` validates ownership.

## 7. Database Design
**`nursing_progress_note`** (new, recurring): `id, public_id, hospital_id, patient_id, admission_id, shift, nurse_id, general_condition, pain_score, remarks, doctor_notified, doctor_name, doctor_advice, patient_response, status, created_at/updated_at`. Index `(hospital_id, admission_id, shift)`.
**`nursing_procedure`** (new): `id, progress_note_id, hospital_id, procedure_name, performed_by, performed_time, remarks`.
**MAR = `NurseTask`** *(exists)* — add `missed_reason` (BR-3) + the Form-06 `doctor_order_id`-nullable/`source` fix so procedures/safety tasks coexist. No new `medication_administration` table.
**`shift_handover`** (new): `id, hospital_id, admission_id, shift, outgoing_nurse_id, incoming_nurse_id, pending_tasks, critical_alerts, meds_due, investigations_pending, doctor_review_pending, created_at`. *(Nascent shift-handover work already exists in frontend/service — align to it.)*
- FK: `patient_id → patients`, `admission_id → ipd_admissions`, `nurse_id → users/nurses`. Tenant + audit + soft-delete.

## 8. Medication Safety (MAR) + EWS
- **MAR flow:** Doctor prescribes (`DoctorOrder`) → Pharmacy dispenses → nurse administers via `NurseTask.executeTask` → barcode scan `[Future]` → recorded. **5-rights check** before recording (wrong dose/patient/time, allergy conflict via `PatientAllergy` from Form 07, duplicate) — `[Future]`, ties to Clinical Decision Support.
- **EWS:** `scale_type='EWS'` on [Clinical Risk Engine](./shared/clinical-risk-engine.md) — pulse+BP+temp+RR+SpO₂+consciousness → score; threshold breach → 🔴 notify doctor (engine propagation §C).

## 9. Nursing Dashboard
Patients assigned · medications due · missed medications · critical patients · pain alerts · high fall risk (Form 06) · isolation patients (Form 06) · pending procedures · pending doctor review · discharge today. All `WHERE hospital_id = current`. Reuses risk badges from the Clinical Risk Engine.

## 10. APIs
Under `/hospital/nursing`; every `{id}`/`{patient}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/nursing/progress` | NURSE | create shift progress note |
| GET | `/hospital/nursing/progress/{patient}` | NURSE, DOCTOR, ADMIN | notes for patient |
| PUT | `/hospital/nursing/progress/{id}` | NURSE (current shift) | update |
| POST | `/hospital/nursing/procedure` | NURSE | record a procedure |
| POST | `/hospital/medication-administration` | NURSE | = `NurseTask.executeTask` (MAR) |
| POST | `/hospital/nursing/handover` | NURSE | shift handover |
| GET | `/hospital/nursing/dashboard` | NURSE, DOCTOR, ADMIN | dashboard |

## 11. Permissions
| Role | Create | Edit | View | Approve |
|---|---|---|---|---|
| Staff Nurse | Yes | Current shift | Yes | No |
| Head Nurse | Yes | Yes | Yes | Yes |
| Doctor | No | No | Yes | Review |
| MRD | No | No | Archived | No |
| Hospital Admin | Read | No | Yes | No |

Matches §10 `@PreAuthorize`.

## 12. Validation
Shift ∈ {MORNING,EVENING,NIGHT}; pain_score 0–10; vitals in physiological ranges (reuse vitals module); medication admin references a valid `DoctorOrder` of the same admission; `missed_reason` required when status∈{MISSED,HELD}; assigned nurse = authenticated nurse of hospital; one note per (admission, shift, nurse) unless correction.

## 13. Notifications
Medication due → nurse · medication missed → head nurse · critical vital/EWS → doctor · shift handover pending → incoming nurse · pain > threshold → doctor · deterioration → doctor. Reuse event/WebSocket infra.

## 14. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/nursing-progress.html`: patient details, shift, nursing notes, procedures, MAR, nurse signature, date/time, QR. Copy: file (MRD; Form 02 `nurse_report`, `drug_chart`, `tpr_chart` items).

## 15. Audit Logs
Via `AuditLogService` (`entity_type="NURSING_PROGRESS"`/`"MEDICATION_ADMIN"`): note created/edited · procedure recorded · medication administered/missed (with reason) · doctor notified · handover · deterioration escalation — user, role, timestamp, old→new, IP. MAR entries are medico-legal — never silently edited.

## 16. AI & Smart Enhancements `[Future]`
- **Smart nursing task list** — auto-generated per patient/time (BP, meds, dressing, I/O, pain assessment) — needs the `NurseTask` source-decoupling fix.
- **EWS auto-calc** (via risk engine) with immediate doctor alert on breach.
- **Medication-safety 5-rights + allergy/duplicate check** before recording.
- **Auto shift summary** — procedures done, meds administered, pending tasks, outstanding investigations, patients needing urgent review — cuts handover errors.

---

## Module & workflow placement
- **Owning module:** Nursing Station → Daily Nursing Care (Nursing Workflow Engine).
- **Creates:** `nursing_progress_note`, `nursing_procedure`, `shift_handover`; records into `NurseTask` (MAR) + `VitalSigns`. **Updates:** MAR status, risk/EWS badges. **Views:** doctor orders, vitals, allergies (Form 07), risk (Form 06). **Prints:** shift record. **Archives:** MRD (Form 02 nurse_report/drug_chart/tpr_chart).
- **Feeds into:** Doctor Dashboard · Pharmacy · Lab/Radiology (pending) · Dietician/Physio · Bed Mgmt · Alerts · MRD. **Fed by:** IPD Admission · Nurse initial assessment · DoctorOrders · Vitals · Risk Engine.
- **New modules/roles this form implies:** `nursing_progress_note`/`nursing_procedure`/`shift_handover` tables · `NurseTask.missed_reason` + source-decoupling (foundational, shared with Form 06) · **EWS scale** on Clinical Risk Engine · Medication-safety 5-rights check (`[Future]`, CDS) — add to README.
