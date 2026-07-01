# Form Spec — Emergency Initial Assessment / Emergency Information System (EIS)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/ER/04/2026* (2026-07-01) |
| **Existing code?** | **new module (EIS).** No emergency/triage/MLC entity exists. **Reuses** [`VitalSigns`](../../backend/src/main/java/com/hms/entity/VitalSigns.java) (Form 09), the [`DoctorOrder`](../../backend/src/main/java/com/hms/entity/DoctorOrder.java)→Lab/Radiology auto-forward chain, [`CdssEvaluationService`](../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java) (`calculateEws`/`evaluateLabResult`/`evaluatePrescription`) for smart triage & pathways, [`OtBooking`](../../backend/src/main/java/com/hms/entity/OtBooking.java)/[`OtChecklist`](../../backend/src/main/java/com/hms/entity/OtChecklist.java) for OT transfer, and `IpdAdmission` for admit. **Two blockers surfaced** — see Read-first. |

> **Read first — two foundational blockers this form exposes.**
> **(1) There is no admission path that isn't OPD-originated.** The only entry point is
> [`IpdAdmissionService.admitFromOpd(opdId, wardId, bedId, admissionType, …)`](../../backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java#L103)
> — it **requires an `Opd` row**. `IpdAdmission.admissionType` already carries the value `EMERGENCY`,
> but **nothing can reach it**: an ER patient has no OPD encounter. Disposition "Admit / ICU" (BR-4)
> needs a new **`admitFromEmergency(emergencyVisitId, …)`** path (and, cleaner, decoupling admission
> from its OPD origin). Log as a foundational gap.
> **(2) `Patient` cannot represent an unknown/unregistered arrival.** [`Patient`](../../backend/src/main/java/com/hms/entity/Patient.java)
> has `publicId`/`name`/`phone` but **no UHID, no emergency number, no `is_temporary`/`is_unknown`
> flag, and no merge mechanism.** Section A ("Unknown Male 01", later merged to permanent UHID) and
> BR-2/BR-3 (Red patients treated *before* registration) **invert the normal Patient-first flow** and
> require a temporary-identity + record-merge capability. Foundational gap.
>
> **What this form does NOT rebuild:** vitals (`VitalSigns`, Form 09), lab/radiology ordering
> (`DoctorOrder` chain), EWS/pathway logic (`CdssEvaluationService`), OT booking (`OtBooking`).
> The EIS **orchestrates** these existing subsystems at emergency speed.

---

## 1. Form Overview
- **Department:** Emergency (ER) — primary; ER Doctor, ER Nurse, CMO, ICU, Lab, Radiology, Billing, MRD — secondary
- **Module:** **Emergency Department → Triage → Emergency Assessment** (speed-optimized, minimal clicks)
- **Filled By:** ER Nurse (triage + nursing sections), ER Doctor (medical assessment)
- **Reviewed By:** CMO / Specialist on consult
- **Archived By:** MRD
- **Lifecycle:** created at arrival; closed at disposition (discharge/admit/ICU/OT/referral/LAMA/death); permanent after archival
- **NABH clause:** AAC — triage, timely emergency assessment, and safe transitions of care.

## 2. Purpose
- **Hospital use:** first clinical record of an ER arrival; drives immediate treatment and disposition.
- **NABH requirement:** documented triage + emergency assessment even when identity/consent are deferred.
- **Legal:** arrival time, triage, ABCDE, orders, disposition = core medico-legal ER record (esp. MLC).
- **Clinical:** structured primary survey + smart triage catch time-critical conditions (MI/stroke/sepsis) fast.
- **Business rationale:** a time-driven **Emergency Information System**, not a form — reduces treatment delay, gives full traceability.

## 3. Trigger
`Arrival → Emergency Registration (deferrable for Red) → Triage → Emergency Initial Assessment → Emergency Orders → Treatment → Observation → Disposition (Discharge | Admit | ICU | OT | Referral | LAMA | Death)`. Assessment and treatment run **near-simultaneously** — unlike OPD/IPD.

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Emergency Nurse | triage + nursing sections + vitals | `NURSE` (ER) |
| Emergency Doctor | full assessment, orders, disposition | `DOCTOR` (ER) |
| CMO | review emergency management | `DOCTOR` (flag `is_cmo`?) |
| Specialist | takes over on consult | `DOCTOR` |
| Reception | registration only (deferred) | `RECEPTIONIST` |
| MRD | archive | `MRD_OFFICER` (gap) |
| Hospital Admin | read | `HOSPITAL_ADMIN` |

Role gaps: **CMO** (Casualty Medical Officer — a supervisory ER doctor capacity; a flag on `DOCTOR` rather than a new role).

## 5. Sections → storage
| § | Section | Capture | Storage |
|---|---|---|---|
| A | Patient identification | emergency no, UHID (if known), name/age/gender/mobile/relative, arrival time; **or** temp "Unknown Male 01" | `emergency_visit` + **temporary `Patient`** (Read-first-2); mergeable |
| B | Arrival details | arrival time, **arrival_mode** (Walk-in/Ambulance/Police/Referral/Relative) | `emergency_visit` |
| C | Triage | **triage_level** 🔴Red/🟠Orange/🟡Yellow/🟢Green | `emergency_visit.triage_level` (+ triage history) |
| D | Chief complaint | chest pain/trauma/poisoning/RTA/stroke… | `emergency_assessment.chief_complaint` |
| E | Primary survey (ABCDE) | Airway / Breathing / Circulation / Disability(GCS,pupils) / Exposure | structured cols on `emergency_assessment` (mandatory for trauma/critical) |
| F | Vitals | temp/pulse/BP/RR/SpO₂/pain | **read/write `VitalSigns`** (Form 09) — integrated, not duplicated |
| G | Emergency orders | Lab/X-ray/CT/MRI/ECG/meds/O₂/IV | **`DoctorOrder`** chain → auto-route to Lab/Radiology/Pharmacy |
| H | Initial diagnosis | Acute MI/Stroke/Sepsis/Polytrauma… | `emergency_assessment.initial_diagnosis` (ICD-ready via `DiagnosisMaster`) |
| I | Disposition | Discharge/Admit/ICU/OT/Referral/LAMA/Death | `emergency_assessment.disposition` → triggers workflow (§7) |

## 6. Database Design
**`emergency_visit`** (new — the ER encounter, analogous to `Opd`): `id, public_id, hospital_id, patient_id (nullable until merged), emergency_number, arrival_time, arrival_mode, triage_level, is_mlc, status (ACTIVE/OBSERVATION/DISPOSED), created_at`.
**`emergency_assessment`** (new): `id, public_id, hospital_id, patient_id, emergency_visit_id, chief_complaint, airway_status, breathing_status, circulation_status, gcs_score, pupils, exposure_findings, initial_diagnosis, disposition, doctor_id, assessment_time, created_at`.
**`emergency_triage`** (new — triage history/re-triage): `id, hospital_id, emergency_visit_id, triage_level, criteria, triaged_by, triaged_at`.
**`emergency_injury`** (new — trauma exposure detail): `id, hospital_id, assessment_id, region, type (injury/burn/fracture/rash), description`.
**`emergency_event`** (new — the ER timeline): `id, hospital_id, emergency_visit_id, event_type, event_time, detail, actor_id` (arrival/triage/assessment/order/med/CT/admission…).
- Orders reuse `DoctorOrder`; vitals reuse `VitalSigns`; MLC reuse `medico_legal_case` (new, §7 BR-6). FK `patient_id → patients`, `hospital_id` on every row. Index `(hospital_id, status, triage_level)` for the priority dashboard.

## 7. Business Rules
- **BR-1** Every ER patient must be **triaged before routine treatment** (triage record required).
- **BR-2** **Red bypasses administrative delay** — treatment first; registration completed later per policy (temporary identity created immediately, Read-first-2).
- **BR-3** Unknown patient → **temporary emergency identifier** (`Unknown Male 01`); later **merged** to permanent UHID (record-merge).
- **BR-4** `disposition = ICU / Admit` → **initiate admission** via new `admitFromEmergency` (Read-first-1) with `admissionType=EMERGENCY`.
- **BR-5** `disposition = OT` → **notify OT + generate pre-op tasks** (`OtBooking`/`OtChecklist`; pre-op `NurseTask`).
- **BR-6** `is_mlc = true` → **trigger Medico-Legal Case workflow** (`medico_legal_case` — new).
- **BR-7** Deterioration during observation → EWS (`calculateEws`) breach alert to ER doctor + CMO.
- **BR-8** Every query filters `hospital_id`; every `{id}` validates ownership (temp patients still tenant-scoped).

## 8. Emergency Dashboard (priority board)
Current ER patients · Red-triage · waiting-for-doctor · waiting-for-bed · waiting-for-lab · waiting-for-CT · waiting-for-admission · waiting-for-ICU — sorted so doctors see the **highest priority first**. All `WHERE hospital_id = current`. Live via existing `WebSocketConfig`.

## 9. APIs
Under `/hospital/emergency`; every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/emergency/assessment` | ER DOCTOR, ER NURSE(sections) | create/append assessment |
| GET | `/hospital/emergency/patients` | ER DOCTOR, NURSE, ADMIN | priority board |
| GET | `/hospital/emergency/{id}` | ER DOCTOR, NURSE | one visit (+ timeline) |
| POST | `/hospital/emergency/{id}/triage` | ER NURSE | (re)triage |
| POST | `/hospital/emergency/orders` | ER DOCTOR | emergency orders (→ `DoctorOrder`) |
| POST | `/hospital/emergency/{id}/admit` | ER DOCTOR | admit/ICU (→ `admitFromEmergency`) |
| POST | `/hospital/emergency/{id}/discharge` | ER DOCTOR | discharge/LAMA |
| POST | `/hospital/emergency/{id}/transfer` | ER DOCTOR | OT / referral |
| GET | `/hospital/cdss/ews/{visit}` | ER DOCTOR, NURSE | **exists** — deterioration |

## 10. Permissions
| Role | Create | Edit | View |
|---|---|---|---|
| ER Nurse | Yes | Nursing/triage sections | Yes |
| ER Doctor | Yes | Yes | Yes |
| Consultant | Yes | Yes | Yes |
| Reception | Registration only | No | Limited |
| Hospital Admin | Read | No | Read |
| MRD | No | No | Archived |

Matches §9 `@PreAuthorize`.

## 11. Notifications
🔴 Red arrival · CT ordered · ICU bed requested · surgeon requested · blood requested · MLC initiated · patient admitted · patient discharged. Reuse `WebSocketConfig` + `CdssAlertLog`; blood ties to Blood Bank (Form 01 gap).

## 12. Print Rules
Via [Signature & Document service](./shared/signature-and-document-service.md). `templates/emergency-assessment.html`: emergency number, arrival time, triage level, ABCDE assessment, orders, diagnosis, disposition, doctor signature, QR, version. Preserves official ER format. Copy: file (MRD).

## 13. AI & Smart Enhancements — extend CDSS (recommendation only)
- **Smart Triage** — BP + pulse + SpO₂ + GCS + chief complaint → *suggest* "Recommended Triage: Red" (clinician decides). Reuses `calculateEws` + a triage rule set on `CdssEvaluationService`.
- **Stroke detection** — facial weakness + slurred speech + limb weakness → *suggest* "Consider Stroke Protocol."
- **Chest-pain pathway** — chest pain + sweating + low BP → *suggest* ECG + Troponin + Cardiology.
- **ER Timeline** — chronological journey (arrival→triage→assessment→ECG→med→CT→ICU) from `emergency_event`; invaluable for quality + medico-legal review.
All are recommendations, never auto-diagnoses — consistent with existing CDSS framing.

## 14. Validation
`triage_level` ∈ {RED,ORANGE,YELLOW,GREEN}; `arrival_mode`/`disposition` ∈ enums; `gcs_score` 3–15; ABCDE mandatory when triage ∈ {RED,ORANGE} or complaint=trauma; `arrival_time` not future; vitals validated by Form 09 rules; temp-patient requires gender for identifier; server-side EWS/pathway recompute.

## 15. Audit Logs
Via `AuditLogService` (`entity_type="EMERGENCY"`): triage set/changed · assessment · order · disposition · MLC initiated · patient-merge — user, role, timestamp, old→new, IP. `emergency_event` doubles as the clinical timeline; CDSS alerts via `CdssAlertLog`.

---

## Module & workflow placement
- **Owning module:** Emergency Department (EIS) → Triage → Emergency Assessment.
- **Creates:** `emergency_visit`, `emergency_assessment`, `emergency_triage`, `emergency_injury`, `emergency_event`, `medico_legal_case`; temporary `Patient`. **Updates:** `IpdAdmission` (admit/ICU via `admitFromEmergency`), `OtBooking` (OT), `DoctorOrder` (orders), `Patient` (UHID merge). **Reads/writes:** `VitalSigns` (Form 09). **Prints:** ER assessment. **Archives:** MRD.
- **Feeds into:** Doctor Dashboard · Nursing · Lab · Radiology · Pharmacy · Blood Bank · ICU · OT · Bed Mgmt · Admission · Billing · MRD · Notifications · Audit. **Fed by:** Emergency Registration · Patient Management · Triage · Vitals · CDSS.
- **New this form implies (add to README):** **Emergency Information System module** (`emergency_visit`/`assessment`/`triage`/`injury`/`event` tables) · **`admitFromEmergency` admission path** (decouple admission from OPD origin — foundational) · **temporary/unknown patient identity + UHID record-merge** (foundational, patient-side) · **Medico-Legal Case (MLC) workflow** (`medico_legal_case`) · **CMO** capacity flag on `DOCTOR` · smart-triage/stroke/chest-pain rule sets extend existing CDSS.
