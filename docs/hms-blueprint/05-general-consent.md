# Form Spec — General Consent (root of Consent Management System)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/GEN/02/2026* (2026-07-01) |
| **Existing code?** | **new module.** No consent tables exist. **Defines the shared [Consent Management Engine](./shared/consent-management-engine.md)** (built on [Signature & Document service](./shared/signature-and-document-service.md)). [Form 01 – Blood](./01-blood-transfusion-consent.md) is reconciled as a *type* on this engine. Grounds on [`IpdAdmission`](../../backend/src/main/java/com/hms/entity/IpdAdmission.java) (`admissionType` EMERGENCY/ELECTIVE) and [`Patient`](../../backend/src/main/java/com/hms/entity/Patient.java). |

> **Keystone form — read first.** This is the architect's intended **entry point of a
> centralized Consent Management System**, not a standalone PDF. General Consent is the
> *root/canonical* consent; Blood ([Form 01](./01-blood-transfusion-consent.md)), Surgery,
> Anaesthesia, Procedure, High-Risk, ICU, DAMA all become **types on one engine**. To avoid
> re-specifying signatures/versioning/print/audit for 9+ consents, that engine is spec'd once in
> [`shared/consent-management-engine.md`](./shared/consent-management-engine.md); this form
> defines only the General-consent *type*. **Reconciliation of Form 01:** `blood_transfusion_consent`
> is not a competing table — it is `consent_type='BLOOD'` on `patient_consent` + a type-specific
> detail row. **Prerequisite:** `Patient` lacks `guardian_name/relationship`, `date_of_birth`
> (needed for minor detection — only `age:Integer` exists), and `preferred_language`; these must be
> added for BR-3/BR-4/§17 to work. Flagged, not silently assumed.

---

## 1. Form Overview
- **Department:** Reception + IPD Admission (primary); OPD, Emergency, Nursing, MRD, Billing, Admin (secondary). **Hospital-level, not owned by one clinical dept.**
- **Module:** **Consent Management → General Consent** (new; defines the engine)
- **Filled By:** Receptionist / Admission Executive (initiates)
- **Signed By:** Patient (or Guardian if patient cannot)
- **Verified By:** Nurse (before care), Admission Executive (identity)
- **Archived By:** MRD
- **Lifecycle:** permanent; **immutable once LOCKED**
- **NABH clause:** PRE/AAC — informed general consent for treatment on admission.

## 2. Purpose
- **Hospital use:** primary legal authorization for routine care, exams, investigations, nursing, and hospital policies.
- **NABH requirement:** documented general/informed consent before non-emergency treatment.
- **Legal:** the baseline authorization; its absence blocks elective treatment and creates liability.
- **Clinical:** all procedure-specific consents (blood, surgery…) link back to this master.
- **Business rationale:** one reusable consent engine vs. 9 disconnected forms → less code, consistent audits.

## 3. Trigger
`Patient Registration → Doctor Consultation → Admission Decision → Create IPD Admission` **→ General Consent auto-generated** → `Patient/Guardian Signature → Admission Confirmed → Room Allocation → Clinical Care Begins`.
**Gating state (BR):** no **elective** (`admissionType='ELECTIVE'`) IPD admission is *confirmed* until General Consent is `LOCKED`. **Emergency** (`admissionType='EMERGENCY'`) may proceed first; consent obtained per policy afterward.

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Receptionist / Admission Exec | create, edit draft | `RECEPTIONIST` (admission exec = same or `HOSPITAL_ADMIN`) |
| Patient | sign own consent | none — patient (in-person, staff-supervised device) |
| Guardian | sign if patient cannot | none — captured as GUARDIAN party |
| Nurse | verify documentation before care | `NURSE` (view) |
| Doctor | reference when planning care | `DOCTOR` (view) |
| MRD | archive | `MRD_OFFICER` (gap → admin) |
| Hospital Admin | oversight | `HOSPITAL_ADMIN` |

No new role gaps beyond those already logged.

## 5. Fields
Core columns = [Consent Engine §A](./shared/consent-management-engine.md). General-consent specifics:

| Field | Type | Mand. | Editable rule | DB column | Validation | Source |
|---|---|---|---|---|---|---|
| Consent type | enum | Y | fixed = `GENERAL` | `consent_type` | = GENERAL | auto |
| Patient (UHID) | FK | Y | read-only | `patient_id` | hospital's patient | auto |
| Admission | FK | Y | read-only | `admission_id` | ADMITTED, same hospital | auto |
| Patient signed | bool | Y* | patient | `patient_signed` | true unless guardian path | signature |
| Guardian signed | bool | cond. | guardian | `guardian_signed` | required if minor/unconscious | signature |
| Relationship | enum | cond. | receptionist | `relationship` | required if guardian signs | manual |
| Signature type | enum | Y | system | `signature_type` | FINGER/STYLUS/OTP/DIGITAL_CERT | manual |
| Witness | FK | Y | receptionist | `witness_id` | staff of hospital | manual |
| Language | enum | Y | receptionist | `language` | supported list | manual |
| Interpreter | FK | cond. | receptionist | `interpreter_id` | if patient can't read language | manual |
| Declaration acks | bool set | Y | patient | (see §7) | all required acks true | manual |
| Remarks | text | N | receptionist | `remarks` | — | manual |

Auto-filled (never typed): UHID, name, age, gender, address, mobile, admission no, ward, bed, consultant, date/time, hospital name — [Engine §E](./shared/consent-management-engine.md).

## 6. Business Rules
- **BR-1** General Consent is **mandatory for every IPD admission**.
- **BR-2** Only **one active** General Consent per admission (Engine partial-unique §A).
- **BR-3** `IF patient unconscious THEN` guardian details + relationship mandatory.
- **BR-4** `IF patient is a minor (DOB-derived age < 18) THEN` guardian consent mandatory. *(Needs `Patient.date_of_birth` — prerequisite; today only `age:Integer`.)*
- **BR-5** Any modification after signing forks a **new version**; original never edited (Engine §D).
- **BR-6** Elective admission cannot be **confirmed** until this consent is `LOCKED`; emergency may defer (§3).
- **BR-7** Once `LOCKED`, read-only; MRD archives on discharge (ties to [Form 02](./02-ipd-files-front-checklist.md) `general_consent` checklist item).
- **BR-8** Every query filters `hospital_id`; every `{id}` validates ownership (audit SEC rule).

## 7. Patient Declaration (acks shown before signature)
Displayed clearly, each a required acknowledgement: authorizes routine treatment · understands outcomes not guaranteed · investigations/treatment may be needed · accepts hospital rules/policies · understands confidentiality policy · agrees to routine nursing procedures · understands additional procedure-specific consents may be required later.

## 8. Database Design
Uses **`patient_consent`** ([Engine §A](./shared/consent-management-engine.md)) with `consent_type='GENERAL'`. No type-specific detail table needed (General is simple). FK: `patient_id → patients`, `admission_id → ipd_admissions`, `witness_id`/`interpreter_id → users`. Tenant + audit + soft-delete per engine. Links to MRD checklist ([Form 02](./02-ipd-files-front-checklist.md)) via admission.

## 9. Workflow
Shared lifecycle — [Engine §D](./shared/consent-management-engine.md):
`DRAFT → (patient review) → SIGNED → (witness + submit) → SUBMITTED → LOCKED → ARCHIVED(MRD)`. Post-lock change → new version, old `SUPERSEDED`.

## 10. APIs
Via [Engine §F](./shared/consent-management-engine.md), type = GENERAL:
`POST /hospital/consents` · `GET /hospital/consents/{id}` · `GET /hospital/patients/{id}/consents` · `POST /hospital/consents/{id}/sign` · `POST /hospital/consents/{id}/submit` · `GET /hospital/consents/{id}/print`. `@PreAuthorize`: create/edit = RECEPTIONIST/HOSPITAL_ADMIN; sign = patient/guardian in-person; view = NURSE/DOCTOR/MRD/ADMIN. Every `{id}` tenant-checked.

## 11. Validation
Patient/admission belong to hospital; admission status ADMITTED; guardian block complete when minor/unconscious; witness is staff of hospital; language ∈ supported; signature_type ∈ enum; all declaration acks true before submit; server re-validates on submit (never trust client "signed").

## 12. Permissions
| Role | Create | Edit draft | Sign | View |
|---|---|---|---|---|
| Receptionist | Yes | Yes | No | Yes |
| Patient | No | No | Yes | Own |
| Guardian | No | No | Yes | Applicable |
| Nurse | No | No | No | Yes |
| Doctor | No | No | No | Yes |
| MRD | No | No | No | Yes (archive) |
| Hospital Admin | View | Limited | No | Yes |

Matches §10 `@PreAuthorize`.

## 13. Print Rules
Via [Signature & Document service §C](./shared/signature-and-document-service.md). `templates/general-consent.html`: hospital logo, consent version, patient details, full consent/declaration text, signature blocks (patient/guardian/witness/interpreter), QR (UHID + admission + consent public_id), document ID, page numbers, digital verification ID. Must closely match the NABH-approved paper layout. Copies: patient + file (MRD).

## 14. Audit Trail
Via [Engine §G](./shared/consent-management-engine.md) / `AuditLogService`: created · opened · reviewed · signature captured · witness added · submitted · printed · archived — each with user, role, timestamp, action, old→new status, IP. `entity_type="PATIENT_CONSENT"`, `consent_type="GENERAL"`. Never deleted.

## 15. Digital Improvements
- Fetch-not-type auto-fill of all demographics/encounter data.
- Multi-method digital signature (finger/stylus/OTP).
- Consent status surfaced on the **admission dashboard** ("General Consent: signed/pending").
- All future procedure-specific consents **link back** to this master via patient/admission.

## 16. Missing / Intelligent Features
- **Auto-detect patient language** → present consent in that language; trigger **interpreter workflow** if patient can't understand default (needs Interpreter role — gap from Form 01).
- **Block treatment if mandatory consent missing** — hard gate on elective care start.
- Minor/unconscious detection auto-forces guardian path (needs DOB).
- Master→child consent linkage view (all consents for an admission in one place).

## 17. Prerequisite — Patient model additions
For BR-3/BR-4/§16 to function, [`Patient`](../../backend/src/main/java/com/hms/entity/Patient.java) needs: `date_of_birth` (derive age + minor status; today only `age:Integer` — same gap flagged in audit/Form 01), `guardian_name`, `guardian_relationship`, `preferred_language`. Add to README foundational gaps.

---

## Module & workflow placement
- **Owning module:** Consent Management (new) — General Consent is its root; defines the shared engine.
- **Creates:** `patient_consent` (GENERAL). **Updates:** versions on amendment. **Views:** Nurse/Doctor/MRD/Admin. **Prints:** NABH-format PDF. **Archives:** MRD (Form 02 checklist item).
- **Feeds into:** every procedure-specific consent (Blood/Surgery/Anaesthesia/ICU/DAMA…) · Admission confirmation gate · Nursing/Doctor dashboards · MRD. **Fed by:** Registration · IPD Admission.
- **New modules/roles this form implies:** **Consent Management Engine** (shared — now spec'd) · Interpreter role/workflow (gap) · **Patient model additions** (DOB, guardian, language) — foundational gap; add to README.
