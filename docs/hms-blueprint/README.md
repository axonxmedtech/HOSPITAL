# HMS Blueprint — NABH Form-Driven Specification

Living blueprint that turns each real hospital form into UI + DB + APIs + rules +
permissions + workflow + print + audit. **One form per file** using
[`_TEMPLATE.md`](./_TEMPLATE.md). Built form-by-form as forms are provided —
we do not fabricate forms we haven't seen.

## How we work
1. You paste one form (image/text).
2. I produce `NN-form-slug.md` from the 16-section template, grounded in that
   form **and** the existing codebase (linking real `entity/`, `controller/`,
   `service/` where they already exist, flagging `[ASSUMPTION]` otherwise).
3. I update the trackers below and the module/workflow maps.
4. You review; we move to the next form.

Design principles come from [`../superpowers/product-philosophy.md`](../superpowers/product-philosophy.md).
Every spec must respect the tenant-isolation rules the audit
([`../superpowers/audits/2026-07-01-hms-functional-audit.md`](../superpowers/audits/2026-07-01-hms-functional-audit.md))
found broken: `hospital_id` on every tenant table, ownership check on every
`{id}` endpoint.

## Form tracker
| # | Form | Module | Filled by | Status | Spec |
|---|------|--------|-----------|--------|------|
| 01 | Consent for Blood & Blood Products Transfusion (VH/NABH/OT/02/2026) | Consent Mgmt (new) | Doctor + Nurse | Draft spec | [01-blood-transfusion-consent.md](./01-blood-transfusion-consent.md) |
| 02 | IPD Files Front Checklist / Medical Record Completeness (VH/NABH/MRD/01/2026) | MRD → File Verification (extends existing MRD) | MRD Officer (system-verified) | Draft spec | [02-ipd-files-front-checklist.md](./02-ipd-files-front-checklist.md) |
| 03 | Patient Feedback / Quality Intelligence (VH/NABH/GEN/03/2026) | Patient Experience / Quality (new) | Patient / Relative (public token) | Draft spec | [03-patient-feedback.md](./03-patient-feedback.md) |
| 04 | HR Training Attendance / Learning Management (VH/NABH/HR/01/2026) | HR → Training Management / LMS (new) | HR Executive / Trainer / Employees | Draft spec | [04-hr-training-attendance.md](./04-hr-training-attendance.md) |
| 05 | General Consent — root of Consent Management (VH/NABH/GEN/02/2026) | Consent Management (new; defines shared engine) | Receptionist + Patient/Guardian | Draft spec | [05-general-consent.md](./05-general-consent.md) |
| 06 | Vulnerability / Risk Assessment (VH/NABH/IPD/08/2026) | Nursing → Risk Assessment (defines Clinical Risk Engine) | Admitting Nurse | Draft spec | [06-vulnerability-assessment.md](./06-vulnerability-assessment.md) |
| 07 | Admission History & Initial Assessment (VH/NABH/IPD/07/2026) | Doctor → Initial Clinical Assessment (EMR backbone) | Admitting Doctor / Consultant | Draft spec | [07-admission-initial-assessment.md](./07-admission-initial-assessment.md) |
| 08 | Nurses Assessment & Daily Progress Record + MAR (VH/NABH/IPD/05/2026) | Nursing → Daily Care (Nursing Workflow Engine) | Staff / Duty / Head Nurse | Draft spec | [08-nursing-daily-progress.md](./08-nursing-daily-progress.md) |
| 09 | TPR Chart & Vitals Monitoring (VH/NABH/IPD/02/2026) | Nursing → Vitals Monitoring (extends `VitalSigns`; EWS exists) | Staff Nurse / ICU | Draft spec | [09-tpr-vitals-chart.md](./09-tpr-vitals-chart.md) |
| 10 | Intake & Output (I/O) Chart (VH/NABH/IPD/06/2026) | Nursing → Fluid Balance (Fluid Management Engine, new) | Staff Nurse / ICU | Draft spec | [10-intake-output-chart.md](./10-intake-output-chart.md) |
| 11 | Re-Assessment / Patient Reassessment Sheet (VH/NABH/IPD/09/2026) | Clinical Progress → Reassessment (evolves `DoctorRound` into Clinical Progress Engine) | Treating Doctor + Nurse | Draft spec | [11-patient-reassessment.md](./11-patient-reassessment.md) |
| 12 | Emergency Initial Assessment (VH/NABH/ER/04/2026) | Emergency Dept → Triage → Emergency Assessment (Emergency Information System, new) | ER Nurse (triage) + ER Doctor | Draft spec | [12-emergency-initial-assessment.md](./12-emergency-initial-assessment.md) |
| 13 | Doctor's Daily Progress Notes / SOAP Continuation (VH/NABH/IPD/13/2026) | Doctor → Progress Notes → Clinical Timeline (**same `DoctorRound` engine as Form 11**) | Treating Doctor / Resident / Consultant | Draft spec | [13-doctor-progress-notes.md](./13-doctor-progress-notes.md) |
| 14 | Discharge Summary (VH/NABH/IPD/20/2026) | Doctor → Discharge Management (extends thin existing `DischargeSummary` into auto-gen Discharge Engine) | Treating Doctor + Consultant | Draft spec | [14-discharge-summary.md](./14-discharge-summary.md) |
| 15 | Pre-Anaesthesia Assessment / PAC (VH/NABH/OT/01/2026) | OT → Pre-operative Assessment (front of Surgical Workflow / Pre-op Safety Engine) | Anaesthesiologist | Draft spec | [15-pre-anaesthesia-assessment.md](./15-pre-anaesthesia-assessment.md) |
| 16 | Surgical Consent / Operation Consent (VH/NABH/OT/02/2026) | Consent Management → Surgery Consent (`consent_type='SURGERY'` — **reuses Consent Engine**) | Surgeon + Patient/Guardian | Draft spec | [16-surgical-consent.md](./16-surgical-consent.md) |
| 17 | WHO Surgical Safety Checklist (VH/NABH/OT/03/2026) | OT → WHO Checklist (**extends existing `OtChecklist`; phase gates already built**) | OT Nurse (leads) + team | Draft spec | [17-who-surgical-safety-checklist.md](./17-who-surgical-safety-checklist.md) |
| 18 | Operation Record / Intraoperative Record (VH/NABH/OT/04/2026) | OT → Intraoperative Documentation (OT Execution Engine — hub; reuses inventory/pathology/fluid/order chains) | Primary Surgeon + OT nurses | Draft spec | [18-operation-record.md](./18-operation-record.md) |
| 19 | Anaesthesia Record / AIMS (VH/NABH/OT/~05/2026) | OT → Anaesthesia Module (real-time AIMS; reuses PAC/`Medicine`/fluid/EWS rails) | Anaesthesiologist + OT nurse | Draft spec | [19-anaesthesia-record.md](./19-anaesthesia-record.md) |
| 20 | Recovery Room / PACU Record (VH/NABH/OT/08/2026) | OT → PACU Module (Post-Anaesthesia Recovery Management; reuses patient/surgery/meds; owns Aldrete score) | Recovery Nurse + Anaesthesiologist | Draft spec | [20-pacu-recovery-record.md](./20-pacu-recovery-record.md) |
| 21 | Post-Operative Orders & Assessment (VH/NABH/OT/09/2026) | OT → Post-operative Management (Handover & Ward Orders Engine; reuses patient/surgery/meds; schedules vitals/tasks) | Surgeon + Anaesthesiologist | Draft spec | [21-post-operative-orders.md](./21-post-operative-orders.md) |
| 22 | OT to Ward/ICU Handover (VH/NABH/OT/09/2026) | OT → Clinical Handover (Patient Handover Engine; registers tubes/devices, tracks transport, accepts handover) | Recovery Nurse + Ward/ICU Nurse | Draft spec | [22-clinical-handover.md](./22-clinical-handover.md) |
| 23 | OT Instrument & Sponge Count (VH/NABH/OT/10/2026) | OT → Instrument Count (Instrument Safety Engine; tracks initial/final counts, handles discrepancies & overrides) | Scrub Nurse + Circulating Nurse + Surgeon | Draft spec | [23-instrument-count.md](./23-instrument-count.md) |
| 24 | Implant / Device Record (VH/NABH/OT/11/2026) | OT → Implant Management (Implant Lifecycle Engine; handles barcode scanning, stock deduction, and patient cards) | OT Nurse + Surgeon + Inventory Manager | Draft spec | [24-implant-record.md](./24-implant-record.md) |
| 25 | Operation Theatre Register (VH/NABH/OT/12/2026) | OT → OT Register (OT Master Register / Operational Intelligence Engine; tracks daily summaries, case details, timings, and rooms) | OT Coordinator + OT In-charge | Draft spec | [25-ot-register.md](./25-ot-register.md) |
| 26 | OT Cleaning & Sterility Checklist (VH/NABH/OT/15/2026) | OT → OT Readiness (Sterility and Readiness Gating Engine; tracks housekeeping cleaning, CSSD validation, bio-med safety, and environment parameters) | OT Nurse + Housekeeping + CSSD + Bio-med | Draft spec | [26-ot-readiness.md](./26-ot-readiness.md) |
| 27 | Laboratory Information System (LIS) (VH/NABH/LIS/01/2026) | Laboratory → LIS (Lab Order & Results Engine; tracks sample collection, reception scanning, result parameters, delta checks, and pathologist verification) | Lab Technician + Pathologist + Nurse | Draft spec | [27-lab-information-system.md](./27-lab-information-system.md) |
| 28 | Radiology Information System (RIS) & PACS (VH/NABH/RIS/01/2026) | Radiology → RIS (Imaging Order, Scheduling & PACS Image Engine; tracks appointments, scan dose metadata, DICOM uploads, and pathologist verification) | Radiology Tech + Radiologist + Receptionist | Draft spec | [28-radiology-information-system.md](./28-radiology-information-system.md) |
| 29 | Pharmacy Management System (PMS) (VH/NABH/PMS/01/2026) | Pharmacy → PMS (Medication Management & Dispensing Engine; tracks prescriptions, FEFO batch selection, controlled substances double-signature, and returns) | Pharmacist + Doctor + Nurse | Draft spec | [29-pharmacy-management-system.md](./29-pharmacy-management-system.md) |
| 30 | Billing & Revenue Cycle (RCM) (VH/NABH/RCM/01/2026) | Billing → Billing Master (Revenue Cycle Engine; handles automated charge captures, split payments, advances, and TPA pre-authorizations) | Billing Executive + Cashier + TPA Executive | Draft spec | [30-billing-rcm.md](./30-billing-rcm.md) |
| 31 | Medical Records & EMR (VH/NABH/MRD/01/2026) | Medical Records → EMR (Document Management & Timeline Engine; tracks longitudinal patient timeline, clinical note signing, ICD-10 coding, and folder archives) | MRD Officer + Medical Coder + Doctor | Draft spec | [31-mrd-emr.md](./31-mrd-emr.md) |
| 32 | Hospital Admin & MIS Dashboard (VH/NABH/MIS/01/2026) | Administration → Executive Dashboard (Operational & Financial Analytics Engine; consolidates metrics, tracks KPIs, and dispatches threshold alerts) | Chairman + CEO + Medical Director + Administrator | Draft spec | [32-admin-dashboard.md](./32-admin-dashboard.md) |
| 33 | Inventory & Store Management (VH/NABH/ISMS/01/2026) | Inventory → ISMS (Inventory & Supply Chain Platform; handles item master, multi-store stock, FEFO issues, department indents, and audit adjustments) | Store Keeper + Store Manager + Dept Head | Draft spec | [33-inventory-store.md](./33-inventory-store.md) |
| 34 | Purchase & Procurement (VH/NABH/PPMS/01/2026) | Purchase → PPMS (Procurement & Vendor Platform; handles requisitions, RFQs, POs, GRN verification, and three-way invoice matching) | Purchase Officer + Purchase Manager + Accountant | Draft spec | [34-purchase-procurement.md](./34-purchase-procurement.md) |
| 35 | CSSD Sterilization (VH/NABH/CSSD/01/2026) | CSSD → Central Sterile Supply (Sterilization Lifecycle Engine; tracks instruments, tray packaging, autoclave cycles, parameters, biological indicators, and checkouts) | CSSD Technician + CSSD Supervisor + OT Nurse | Draft spec | [35-cssd-sterilization.md](./35-cssd-sterilization.md) |
| 36 | Biomedical Engineering (VH/NABH/BEMS/01/2026) | Biomedical → BEMS (Medical Equipment Lifecycle Platform; handles asset QR codes, scheduled preventive maintenance, breakdown tickets, and calibration registers) | Biomedical Engineer + Biomedical Manager + Dept Head | Draft spec | [36-biomedical-equipment.md](./36-biomedical-equipment.md) |
| 37 | Housekeeping & Facility (VH/NABH/HFMS/01/2026) | Housekeeping → HFMS (Facility Operations Platform; handles bed cleaning turnover gating, biomedical waste manifests, linen tracking, and helpdesk complaints) | Housekeeper + Housekeeping Supervisor + Ward Nurse | Draft spec | [37-housekeeping-facility.md](./37-housekeeping-facility.md) |
| 38 | Blood Bank & Transfusion (VH/NABH/BBTMS/01/2026) | Blood Bank → BBTMS (Transfusion Lifecycle Platform; handles donor records, screening tests, component separation, cross-matching compatibility, and transfusion reaction monitoring) | Blood Bank Tech + Blood Bank Officer + Nurse | Draft spec | [38-blood-bank.md](./38-blood-bank.md) |
| 39 | HR, Roster & Payroll (VH/NABH/HR/01/2026) | Human Resources → HCM (Workforce & Payroll Platform; handles employee master records, council license renewals, shift duty rosters, biometric attendance, and salary slips) | HR Executive + HR Manager + Department Head | Draft spec | [39-hr-workforce.md](./39-hr-workforce.md) |
| 40 | Patient Portal & Engagement (VH/NABH/PT/01/2026) | Patient Portal → Engagement (Patient Experience Platform; handles patient accounts, online appointment scheduling, released lab/rad reports, gateway billing, and telemedicine) | Patient + Guardian + Doctor | Draft spec | [40-patient-portal.md](./40-patient-portal.md) |
| 41 | Integration & Interoperability (VH/NABH/INT/01/2026) | Integration → API Gateway (Healthcare Interoperability Layer; handles secure client registrations, HL7 ADT/ORM/ORU logs, FHIR API resources, and DICOM connectivity) | Integration Engineer + IT Admin + Developer | Draft spec | [41-integration-interoperability.md](./41-integration-interoperability.md) |

## Existing roles (map form actors to these)
`SUPER_ADMIN` · `HOSPITAL_ADMIN` · `DOCTOR` · `RECEPTIONIST` · `NURSE` ·
`PHARMACIST` · `LAB_TECHNICIAN` · `RADIOLOGY_TECHNICIAN`.
Actors with no matching role (Consultant, Witness, Interpreter, Relative,
Blood-bank, MRD Officer, Accountant, Cashier, Housekeeping, Biomedical) →
tracked as **role gaps** below.

## Canonical IPD workflow (from AXONX_IPD_FLOW PDF — the one real source doc)
`OPD consult → [Admit to IPD] → carry forward (dx, vitals, Rx, orders) →
IPD Registration (Admission Form: IPD no, datetime, admitting doctor,
type=Emergency|Elective) → Bed Management (ward type, live availability,
charges auto-link) → Doctor Orders (meds/lab/radiology/procedure/diet auto-forward
to Pharmacy/Lab/Radiology) → Nurse Dashboard (pending meds, IV schedule, vitals,
intake-output, pain score, notes, MAR + missed-dose alert) → [Plan Discharge]
→ gate on (pending bills, lab reports, pharmacy clearance) → generate (discharge
summary, final bill, prescription, follow-up) → bed auto-freed, IPD closed →
MRD archive`.

Priority order per the source doc: **Admission → Bed → Orders → Billing →
Discharge first; scale after.**

## Full hospital operational chain (target)
`Reception → Registration → Doctor consult → Admission → Consent → Vitals →
Assessment → Lab → Radiology → OT → Nursing → Pharmacy → Billing → Discharge →
MRD → Reports`.

## Module ownership map (updated as forms arrive)
Reception · Doctor/Clinical · Nursing · OT · Lab · Radiology · Pharmacy ·
Billing/Accounts · MRD · Platform/Admin. _(populated per form in each spec's
"Module & workflow placement".)_

**Role gaps discovered:** Interpreter · Blood Bank · MRD Officer (Form 01) ·
`MRD_OFFICER` (distinct least-privilege record-room role) · Medical Superintendent (Form 02) ·
`QUALITY_OFFICER` (QMS / complaint review — Form 03) ·
`HR_EXECUTIVE` · `DEPARTMENT_HEAD` · trainer flag · `HOUSEKEEPING` (Form 04) ·
`PHYSIOTHERAPIST` · `DIETICIAN` · `INFECTION_CONTROL` (Form 06) ·
**CMO** (Casualty Medical Officer — supervisory capacity flag on `DOCTOR`, Form 12) ·
**Anaesthesiologist** (capacity flag on `DOCTOR`; also convert `OtBooking.anesthetist_name` String → `anaesthesiologist_id` FK, Form 15).

**New reusable capabilities surfaced by forms:** **Consent Management Engine →
[shared/consent-management-engine.md](./shared/consent-management-engine.md)**
(Form 05 defines it; Form 01 Blood + Form 16 Surgery both reconciled as *types* on it —
**three proven types now** (`GENERAL`/`BLOOD`/`SURGERY`), each just a `consent_type` + a
`*_consent_detail` table; one engine for General/Blood/Surgery/Anaesthesia/Procedure/High-Risk/ICU/DAMA) · **Digital-
Signature + Document/versioning service → [shared/signature-and-document-service.md](./shared/signature-and-document-service.md)** ·
**Blood Bank module** (Form 01) · **Clinical Risk Engine →
[shared/clinical-risk-engine.md](./shared/clinical-risk-engine.md)** (Form 06
defines it; future Braden/Morse/nutrition scales reuse it — record findings once,
engine computes scores + badges + auto-tasks/referrals, broadcasts risk to every
dashboard via WebSocket). Build these once; every consent/clinical
document reuses them. Forms link to the shared services rather than re-specifying
signatures/print/audit.

**MRD verification engine + Central Document Management System (DMS)** (Form 02):
MRD gate exists only as a thin archival stub today
([`MrdService`](../../backend/src/main/java/com/hms/service/hospital/MrdService.java) —
checks `DISCHARGED` + rack only, **no completeness check**). Form 02 adds the
verification layer; the clean long-term source is a central DMS every module
publishes completed/signed docs into. Until DMS exists the engine reads source
tables directly.

**Public tokenized-link infrastructure + Complaint Management** (Form 03): the
first *patient-facing, no-login* form. Submission runs on `/api/public/**` with a
single-use, expiring, tenant-scoped `feedback_token` as the sole authorization
(the token resolves hospital/patient server-side — payload never carries
`hospital_id`). Reuses existing [`WhatsAppService`](../../backend/src/main/java/com/hms/service/whatsapp/WhatsAppService.java)
for delivery. Build the token infra once — reusable for any future patient-facing
form (home surveys, remote consent). Auto-generated `quality_complaint` seeds a
Complaint Management sub-module.

**⚠ Foundational: canonical staff/employee identity** (surfaced by Form 04, brushed
by MRD/feedback). Staff identity is **fragmented** — [`User`](../../backend/src/main/java/com/hms/entity/User.java)
(login + `role` String + `hospital_id`, **no department/designation**),
[`Doctor`](../../backend/src/main/java/com/hms/entity/Doctor.java) and
[`Nurse`](../../backend/src/main/java/com/hms/entity/Nurse.java) as **standalone**
tables with **no `user_id` link** and duplicated emails. Any HR/LMS/competency/
department-compliance feature needs one canonical employee record (unify
`User`/`Doctor`/`Nurse`, add `department`, `designation`, `is_trainer`, `user_id`
FKs). This is a **cross-cutting prerequisite**, not a per-form gap — resolve before
building HR-facing modules on top.

**⚠ Foundational: Patient model additions** (surfaced by Form 05, echoed by Form 01
& the audit). [`Patient`](../../backend/src/main/java/com/hms/entity/Patient.java)
stores `age:Integer` (not DOB-derived) and has **no** `guardian_name`,
`guardian_relationship`, or `preferred_language`. These block consent minor/
unconscious detection (BR-3/4), guardian signing, and language/interpreter routing.
Add `date_of_birth`, `guardian_name`, `guardian_relationship`, `preferred_language`
to `Patient` before wiring the Consent Engine. **Also (Form 38):** `Patient` has **no
`blood_group`** — the Blood Bank/transfusion cross-match (Form 38) needs it; add it here
too (the Form 38 header wrongly implied it already exists — corrected in that spec).

**⚠ Foundational: `NurseTask` coupling to doctor orders** (surfaced by Form 06).
[`NurseTask.doctor_order_id`](../../backend/src/main/java/com/hms/entity/NurseTask.java)
is `NOT NULL` — every nursing task must originate from a doctor order. Risk-driven
**safety tasks** (bed rails, hourly rounding, 2-hourly turning) and other nursing-
initiated tasks have no doctor order and **cannot be created** under this constraint.
Make `doctor_order_id` nullable and add a `source`/`task_type` column
(`DOCTOR_ORDER` | `RISK_PROTOCOL` | `NURSING`) before wiring auto-task generation
from the Clinical Risk Engine. **Also (Form 08):** `NurseTask` *is* the Medication
Administration Record (MAR) — it already carries `administered_quantity`, `route`,
`injection_site`, `pre_vitals`, `executeTask(status,…)`. Do **not** build a separate
`medication_administration` table. Add a structured **`missed_reason`** column so
missed/held doses capture a mandatory reason (today it would land in free-text
`notes`).

**⚠ Foundational: `VitalSigns` schema is under-modeled** (surfaced by Form 09).
[`VitalSigns`](../../backend/src/main/java/com/hms/entity/VitalSigns.java) stores
`blood_pressure` as a **single VARCHAR string** and lacks `bp_systolic`/`bp_diastolic`,
`pain_score`, `weight`, `oxygen_support`, `remarks`, and method/rhythm/pattern fields.
The string-BP is already a **live hack** — [`CdssEvaluationService.calculateEws`](../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java)
does `parseSystolic(v.getBloodPressure())` to score it. Split BP into INT columns +
add the missing fields (and backfill-migrate the string) before wiring trend graphs
and accurate EWS. This is the canonical vitals source Forms 07/08 already consume.

**⚠ Foundational: normalized patient-scoped clinical history (EMR backbone)**
(surfaced by Form 07). The initial assessment must write **cross-admission**
history — allergies *([`PatientAllergy`](../../backend/src/main/java/com/hms/entity/PatientAllergy.java)
exists)*, medical/surgical/medication/family/social history, and diagnosis history
— keyed by `patient_id` and reused in every future admission. Today
[`Patient.medical_history`](../../backend/src/main/java/com/hms/entity/Patient.java#L116)
is a **single 1000-char string** — the crude precursor. Add `patient_medical_history`,
`patient_surgical_history`, `patient_medication_history`, `patient_family_history`,
`patient_social_history`, `patient_diagnosis` (coded via existing `DiagnosisMaster`/
`AllergyMaster`) and migrate the string in. This is the longitudinal EMR spine —
separate from admission-scoped `clinical_assessment`.

**Clinical Progress Engine = evolve `DoctorRound` — ONE engine behind Forms 11 & 13** (surfaced by
Forms 11 + 13). [`DoctorRound`](../../backend/src/main/java/com/hms/entity/DoctorRound.java)
(append-only SOAP: `subjective`/`objective`/`assessment`/`plan`, `round_date_time`,
`next_round_at`; `logRound`/`getRoundsHistory`) is **the single table** behind **both** the daily
progress note (Form 13, `assessment_type=DAILY_ROUND`) **and** the reassessment (Form 11,
`assessment_type ∈ EMERGENCY_REVIEW/POST_SURGERY/ICU_REVIEW/FOLLOW_UP`). **Do not build
`progress_note` *or* `patient_reassessment` as separate tables** — that would fork one clinical
timeline into three. Evolve `DoctorRound` with: `assessment_type`, structured `clinical_status`
(JSON), `clinical_impression`, `baseline_assessment_id` (→ Form 07 `clinical_assessment`), and a
**sign/amend lifecycle** (`status` DRAFT/SIGNED/AMENDED, `version`, `amended_from_id`, `signed_by`/
`signed_at` — none exist today; `logRound` just inserts). It reviews/acts on existing subsystems —
vitals (`VitalSigns`, Form 09), labs/radiology (`evaluateLabResult`), meds (`DoctorOrder` transitions
via `evaluatePrescription`), discharge (`IpdAdmission → DISCHARGE_PLANNED`, existing) — rather than
re-entering them. **Genuinely new:** a **`patient_referral`** table + workflow (**no referral entity
exists**, only `DischargeSummary`); a **`progress_order`** junction linking each note to the orders it
generates; and a **smart-linking Plan parser** ("Repeat CBC"→LabOrder, "Discharge tomorrow"→discharge
planning) that extends the order chain/CDSS, **confirm-before-commit**. Change-detection + multi-note
clinical summary extend the existing `/hospital/cdss/smart-summary`.

**⚠ Foundational: admission is hard-wired to an OPD origin** (surfaced by Form 12). The only
admission entry point is [`IpdAdmissionService.admitFromOpd(opdId, …)`](../../backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java#L103)
— it **requires an `Opd` row**. `IpdAdmission.admissionType` already carries the value `EMERGENCY`,
but nothing can reach it because an ER patient has no OPD encounter. Add an
**`admitFromEmergency(emergencyVisitId, …)`** path and, longer-term, **decouple admission from its
OPD origin** so any source (ER, direct, transfer) can admit. Prerequisite for ER→ward/ICU disposition.

**⚠ Foundational: patient identity cannot represent an unknown/unregistered arrival** (surfaced by
Form 12). [`Patient`](../../backend/src/main/java/com/hms/entity/Patient.java) has `publicId`/`name`/
`phone` but **no UHID, no emergency number, no `is_temporary`/`is_unknown` flag, and no record-merge**.
Emergency care **inverts the normal Patient-first flow**: Red-triage patients are treated *before*
registration (BR-2), and unknown patients get a temporary identifier ("Unknown Male 01") later merged
to a permanent UHID (BR-3). Add temporary-identity + a **patient record-merge** capability before the
EIS can register-after-treat. (Patient-side sibling of the canonical staff-identity gap above.)

**⚠ Foundational: `DischargeSummary` lacks `hospital_id` (tenant-isolation gap) and is under-modeled**
(surfaced by Form 14). [`DischargeSummary`](../../backend/src/main/java/com/hms/entity/DischargeSummary.java)
is keyed **only** by `ipd_admission_id` (unique) — it has **no `hospital_id`, `patient_id`, or
`doctor_id`**. Any direct `findByIpdAdmissionId` that doesn't join back to the admission's tenant risks
**cross-tenant read** — precisely the audit-class isolation bug. Add `hospital_id`/`patient_id`/
`doctor_id` and enforce ownership. Separately, the record is thin (only `final_diagnosis`/
`treatment_given`/`discharge_notes`/`follow_up_date`) — expand with `admission_diagnosis`/
`hospital_course`/`condition_at_discharge`/sign-amend lifecycle + auto-populated
`discharge_medications`/`investigations`/`procedures`/`instructions`. The `/plan-discharge` →
`/confirm-discharge` → PDF flow and `DISCHARGE_PLANNED` status **already exist** — extend, don't replace.

**Surgical Workflow / Pre-operative Safety Engine — more built than expected** (Forms 15, 16, 17). The
OT spine already has: [`OtBooking`](../../backend/src/main/java/com/hms/entity/OtBooking.java) (surgery
schedule/request — Form 15's `surgery_request_id` = `ot_booking_id`) and
[`OtChecklist`](../../backend/src/main/java/com/hms/entity/OtChecklist.java) = the **WHO Surgical Safety
Checklist** whose [phase gating is already implemented](../../backend/src/main/java/com/hms/service/hospital/OtService.java#L157)
(`signChecklist` blocks Time Out before Sign In, blocks Sign Out before Time Out, auto-advances booking to
`IN_PROGRESS` on time-out — Form 17 BR-4/5/6 **done**). **What's actually missing across the three OT forms:**
(a) the pre-op **PAC/fitness** front half (Form 15, new); (b) **consent** is a Consent-Engine type, not new
(Form 16); (c) `OtChecklist` needs a **granular `ot_checklist_items`** layer, an **auto-verification
precondition on sign-in** (today `setSignInCompleted(true)` blindly trusts the caller — wire it to the
Surgical Readiness gate), and **instrument/needle/swab count safety** (block sign-out on mismatch, Form 17
BR-7). Critical gap: [`OtService.scheduleBooking`](../../backend/src/main/java/com/hms/service/hospital/OtService.java)
is **ungated** — books surgery with no PAC/consent/blood/investigation precondition; wire the **Surgical
Readiness** gate (PAC-fit + consent + blood + investigations) so only READY patients reach the schedule and
sign-in. Also: `OtBooking.anesthetist_name` free String → `anaesthesiologist_id` FK; patient-scoped
**`anaesthesia_history`** joins the EMR-history family. **Feeder gaps:** CSSD (sterilization) + Implant Inventory.
(d) The **Operation Record** (Form 18, new `operation_record` hub) is the OT execution record — it reuses
existing `InventoryItem`/`HospitalInventory` for implant/consumable auto-deduct + traceability, `LabOrder`
for specimen→pathology, Fluid Mgmt (Form 10) for fluids, and `DoctorOrder`/`NurseTask` for post-op orders;
only **Incident Reporting** (complication → quality review) is genuinely new.
(e) The **Anaesthesia Record** (Form 19, new `anaesthesia_record` hub) is the OT's real-time **AIMS** — the
anaesthetist's physiological counterpart to the surgeon's Operation Record. It **reuses** `pre_anaesthesia_assessment`
(Form 15 — ASA/plan auto-fill), deducts `Medicine` stock for anaesthetic drugs (BR-3, same pattern as Form 18 implants),
reuses Fluid Mgmt (Form 10) and **shares EBL** with the Operation Record (Form 18), and its real-time early-warning
**extends `calculateEws`** (add ETCO₂ + trend velocity) rather than building a new scorer. Intraop vitals are a **new,
high-frequency, device-fed `anaesthesia_vitals`** channel of the Vitals & Monitoring Engine (distinct from ward
`VitalSigns` — adds ETCO₂/ventilation, recorded every few minutes). **Genuinely new / gaps:** **Biomedical Device
Integration** (AIMS auto-import from OT monitors/ventilators/pumps — BR-2); **OT-status granularity** — an *"Anaesthesia
Started"* sub-state to trigger BR-1 auto-start (today `OtBooking` jumps `SCHEDULED → IN_PROGRESS` only at time-out, but
anaesthesia begins *before* incision); and **Aldrete/recovery scoring** (`recovery_scores`) that **gates PACU start**
(BR-5) and feeds the next (Recovery/PACU) form.

## Reusable engine catalog (the architecture these forms converge on)
The architect's Form 14 recommendation — *build reusable engines, not per-form modules* — is the pattern
this blueprint has been driving toward. Status of the 10 engines against specced forms:
| Engine | Status | Where |
|---|---|---|
| Consent Engine | specced (3 proven types) | [shared/consent-management-engine.md](./shared/consent-management-engine.md) (Forms 01 BLOOD, 05 GENERAL, 16 SURGERY) |
| Clinical Documentation Engine | specced (= `DoctorRound`) | Forms 11, 13 — one timeline for progress notes + reassessment |
| Assessment Engine | specced | Form 07 (`clinical_assessment` baseline) + patient-scoped history |
| Risk Assessment Engine | specced | [shared/clinical-risk-engine.md](./shared/clinical-risk-engine.md) (Form 06; EWS = existing CDSS) |
| Vitals & Monitoring Engine | partially exists | `VitalSigns` + CDSS (Form 09) — needs BP split; **`anaesthesia_vitals`** = high-frequency device-fed channel (Form 19) |
| Medication Administration Engine (MAR) | exists | `NurseTask` (Form 08) — needs `missed_reason` |
| Discharge Engine | exists-thin → extend | `DischargeSummary` (Form 14) |
| Document Generation Engine | exists | `PdfService` + Thymeleaf; per-form templates |
| Digital Signature Engine | specced | [shared/signature-and-document-service.md](./shared/signature-and-document-service.md) |
| MRD Document Archive Engine | specced | Form 02 (verification) + central DMS target |
Plus emergent: **Fluid Management** (Form 10), **EIS** (Form 12), **Patient-facing tokenized** (Form 03),
**HR/LMS** (Form 04), **Surgical Workflow / Pre-op Safety Engine** (Form 15 — `OtBooking` + `OtChecklist`/WHO
exist, PAC + readiness gate to add), **AIMS / Anaesthesia Record** (Form 19 — real-time intraop record; reuses
PAC/`Medicine`/fluid/EWS; needs device integration + Aldrete scoring). Forms should *reference* these engines, not re-specify them.

## Candidate missing modules (NABH — confirm/prioritise as forms reveal them)
**Incident Reporting** *(genuinely new — Form 18: intraop complication → auto Quality Incident review, BR-6; also risk/quality feeder)* ·
Fall Assessment · Pressure-Ulcer (Braden) · Nutrition
Screening · Pain Scale · Code Blue · Blood Bank · CSSD *(OT sterilization feeder — Form 17)* ·
**Implant Inventory** *(OT implant tracking feeder — Form 17)* · Mortuary · Biomedical
Waste (BMW) · Infection Control (HAI) · NABH Audit · Housekeeping · Maintenance ·
Dietary · Laundry · Ambulance · **Biomedical Device Integration** *(AIMS auto-import from OT
monitors/ventilators/pumps — genuinely new, Form 19)* · Biomedical Equipment · **PACU / Recovery + Aldrete scoring**
*(`recovery_scores` gate PACU start — Form 19, feeds the next Recovery form)* · Quality Indicators ·
Patient Feedback *(now specced — Form 03)* · Complaint Management *(Form 03)* ·
**HR & Learning Management (LMS)** *(now specced — Form 04)* ·
**Fluid Management Engine (I/O)** *(now specced — Form 10)* ·
Clinical Decision Support *(**partially built** — [`CdssEvaluationService`](../../backend/src/main/java/com/hms/service/hospital/CdssEvaluationService.java)
already does prescription/allergy check, critical-lab alert, **EWS/NEWS scoring**,
and 24 h smart-summary; Forms 07/08 `[Future]` drug-safety flags are really "extend
this", not "build new" — surfaced Form 09)* ·
**Emergency Information System (EIS)** *(now specced — Form 12: `emergency_visit`/`assessment`/
`triage`/`injury`/`event`; speed-optimized triage + one-click disposition to ward/ICU/OT)* ·
**Medico-Legal Case (MLC) workflow** *(Form 12 — `medico_legal_case`, triggered by police cases)* ·
Interpreter Services. _(Not built until a form/requirement
justifies it — problem-first, per philosophy.)_
