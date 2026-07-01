# Form Spec — Consent for Blood & Blood Products Transfusion

> Form code: **VH/NABH/OT/02/2026**

| | |
|---|---|
| **Status** | Draft (spec) |
| **Source** | User-provided Form 1 (2026-07-01) |
| **Existing code?** | **New** — no consent/transfusion/blood-bank/signature entity exists today. Reuses `DoctorOrder`, `IpdAdmission`, `Patient`, `Doctor`, `AuditLogService`, `PdfService`. |
| **Prerequisite gaps** | **Blood Bank module** (blood request/issue) and **Digital Signature infrastructure** do not exist — see §16 + README missing-modules. Consent cannot be fully wired until at least a `blood_request` record exists. |

> **Reconciled with the Consent Engine (Form 05).** This is **not** a standalone table. Per
> [Form 05](./05-general-consent.md) + [shared/consent-management-engine.md](./shared/consent-management-engine.md),
> blood consent = `consent_type='BLOOD'` on the generic `patient_consent` table + a 1:1
> `blood_consent_detail` row holding the transfusion-specific fields (product, indication,
> reaction acknowledgement, etc.). Signatures/versioning/lock/print/MRD-archival/audit come from
> the shared engine — this spec's per-field detail below maps into `blood_consent_detail`, not a
> rival `blood_transfusion_consent` table.

---

## 1. Form Overview
| | |
|---|---|
| Department (primary) | Operation Theatre · Blood Bank · IPD |
| Department (secondary) | ICU · Emergency · Surgery · Nursing · MRD |
| Module | Consent Management (new) under Clinical/IPD |
| Filled by | Doctor (owner) + Nurse (draft assist) |
| Approved / signed by | Patient **or** Guardian; Doctor; 2 Witnesses; Interpreter (conditional) |
| Verified by | Blood Bank (pre-issue verification) |
| Stored in | MRD (permanent) |
| Lifecycle | **Permanent, immutable after submit**; amendments = new version |
| NABH | Consent for high-risk procedure; blood transfusion safety (AAC/COP/PSQ clauses) |

## 2. Purpose
- **Hospital use:** authorizes transfusion after risk explanation; coordinates doctor/blood-bank/nurse.
- **NABH:** informed consent for a high-risk clinical intervention is mandatory and auditable.
- **Legal:** documented, witnessed authorization protects hospital and patient; interpreter attestation covers language barrier.
- **Clinical:** ties the specific blood order to a signed, time-stamped authorization before issue.
- **Business:** blocks transfusion (and its billing) until consent is complete — reduces liability and rework.

## 3. Trigger
Never manually searched. Event-driven:

`Patient registered → Doctor consultation → Doctor raises transfusion order
(DoctorOrder, orderType=TRANSFUSION) → Blood Request created (blood_request) →
**Blood Consent auto-generated in DRAFT** → Patient/Guardian signs → Doctor signs
→ Witness(es) sign → [submit] → Blood Bank verifies → transfusion permitted`.

**Gate:** a transfusion `DoctorOrder` (and, for issue, a `blood_request`) MUST exist before a consent row can be created (§6 BR-1).

## 4. User Roles
| Actor on form | Capacity | Existing HMS role | Note |
|---|---|---|---|
| Doctor | Explains risk, creates, signs | `DOCTOR` | must belong to hospital + be attending |
| Patient | Consents/signs | — (subject, not a login) | captured as signature, not a user |
| Relative/Guardian | Signs if patient incapacitated | — | captured as signature |
| Nurse | Draft assistance, coordination | `NURSE` | cannot submit |
| Witness (patient side) | Confirms consent | — | name+signature only |
| Witness (hospital side) | Confirms consent | `NURSE`/staff | name+signature |
| Interpreter | Attests translation | **ROLE GAP** | no Interpreter role/user today |
| Blood Bank | Pre-issue verification | **ROLE GAP** | no Blood-Bank role/module today |
| MRD | Read-only archive | **ROLE GAP** | no dedicated MRD role; today MRD = admin-viewed |

> Role gaps to README: **Interpreter**, **Blood Bank**, **MRD Officer**.

## 5. Fields
Legend — Source: `auto`=fetched from context, `manual`=entered, `sig`=signature capture.

| Field | Type | Max | Mandatory | Editable rule | DB column | Validation | Search | Print | Source |
|---|---|---|---|---|---|---|---|---|---|
| UHID | string | 20 | Y | read-only | (join `patient.custom_id`) | must resolve to a patient in hospital | Y | Y | auto |
| IPD Number | string | 20 | Y | read-only | (join `ipd_admission.ipd_number`) | admission ACTIVE | Y | Y | auto |
| MLC Number | string | 30 | N | draft only | `admission.mlc_number` `[ASSUMPTION]` | — | Y | Y | auto/manual |
| Bed Number | string | 20 | Y | read-only | (join bed) | — | N | Y | auto |
| Patient Name | string | 100 | Y | read-only | `patient.name` | no digits | Y | Y | auto |
| Age | int | 3 | Y | read-only | `patient.age` | 0–120 | N | Y | auto |
| Sex | enum | — | Y | read-only | `patient.gender` | M/F/O | N | Y | auto |
| Date | date | — | Y | read-only | `consent_date` | not future | N | Y | auto |
| Time | time | — | Y | read-only | `consent_time` | auto now | N | Y | auto |
| Doctor Name | string | 100 | Y | read-only | (join `doctor.name`) | belongs to hospital + attending | Y | Y | auto |
| Explanation given | bool | — | Y | draft only | `explanation_given` | must be true to submit | N | Y | manual |
| Patient signed | bool/sig | — | Y* | draft only | `patient_signed` + sig blob | *unless incapacitated | N | Y | sig |
| Guardian name | string | 100 | cond. | draft only | `guardian_name` | required if `patient_signed=false` | N | Y | manual |
| Guardian relationship | string | 40 | cond. | draft only | `guardian_relationship` | required if guardian | N | Y | manual |
| Guardian signed | sig | — | cond. | draft only | `guardian_signed` | required if guardian | N | Y | sig |
| Witness (patient) name+sig | string+sig | 100 | Y | draft only | `witness_patient_name`, `witness_patient_signed` | — | N | Y | manual+sig |
| Witness (hospital) name+sig | string+sig | 100 | Y | draft only | `witness_hospital_name`, `witness_hospital_signed` | — | N | Y | manual+sig |
| Interpreter required | bool | — | Y | draft only | `interpreter_required` | — | N | Y | manual |
| Language | string | 40 | cond. | draft only | `interpreter_language` | required if interpreter | N | Y | manual |
| Interpreter name+sig | string+sig | 100 | cond. | draft only | `interpreter_name`, `interpreter_signed` | required if interpreter | N | Y | manual+sig |
| Remarks | text | 1000 | N | draft only | `remarks` | — | N | Y | manual |

> **Data-model note:** `Patient.age` is a stored `Integer`, not DOB-derived. The form assumes "Age calculated from DOB" — either add `patient.date_of_birth` and compute, or accept the stored age. Flag for decision.

## 6. Business Rules
- **BR-1** IF no `DoctorOrder(orderType=TRANSFUSION, status=ACTIVE)` for the admission THEN consent creation is rejected.
- **BR-2** IF `consent_status != COMPLETED` THEN blood **cannot be issued** (blocks Blood Bank).
- **BR-3** IF `consent_status = COMPLETED` THEN record is **read-only** (no PUT).
- **BR-4** Any amendment creates a **new version** (`parent_consent_id`, `version`); original preserved.
- **BR-5** Record can **never be hard-deleted** after submit (soft-delete + audit only).
- **BR-6** A patient cannot sign twice for the **same transfusion event** (unique on `blood_request_id` where status active).
- **BR-7** IF `patient_signed=false` THEN guardian fields mandatory.
- **BR-8** IF `interpreter_required=true` THEN all interpreter fields mandatory.
- **BR-9** IF `explanation_given=false` THEN submit blocked.
- **BR-10** Doctor signing must equal the current authenticated doctor of the hospital (tenant + identity check).

## 7. Database Design
Table **`blood_transfusion_consent`** (all rows carry `hospital_id`, filtered on every query — per audit SEC-1/2/3):

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| public_id | VARCHAR unique | external id |
| hospital_id | BIGINT NOT NULL, FK hospital, **INDEX** | tenant key |
| patient_id | BIGINT NOT NULL, FK patient | |
| admission_id | BIGINT NOT NULL, FK ipd_admission | |
| doctor_id | BIGINT NOT NULL, FK doctor | |
| blood_request_id | BIGINT NOT NULL, FK blood_request *(new module)* | BR-1 |
| consent_status | VARCHAR(20) | DRAFT/COMPLETED/CANCELLED |
| explanation_given | BOOLEAN | |
| patient_signed / guardian_signed | BOOLEAN / signature ref | |
| guardian_name, guardian_relationship | VARCHAR | |
| witness_patient_name/_signed, witness_hospital_name/_signed | VARCHAR/sig | |
| interpreter_required | BOOLEAN | |
| interpreter_language, interpreter_name, interpreter_signed | VARCHAR/sig | |
| consent_date, consent_time | DATE/TIME | |
| remarks | TEXT | |
| version, parent_consent_id | INT, BIGINT | BR-4 |
| is_deleted | BOOLEAN | soft-delete, BR-5 |
| created_by, created_at, updated_by, updated_at | audit | |

- **FKs:** patient, ipd_admission, doctor, blood_request all scoped to same `hospital_id` (validate on write).
- **Unique:** `(blood_request_id)` active version — BR-6.
- **Indexes:** `hospital_id`, `patient_id`, `admission_id`, `blood_request_id`.
- Signature blobs stored via Document/Signature service (new) — reference id, not inline PDF.

## 8. APIs
All under `/hospital/blood-consents`, `@PreAuthorize` per §12, **every `{id}` validates `hospital_id` ownership** (audit rule).

| Verb | Path | Roles | Notes |
|---|---|---|---|
| POST | `/hospital/blood-consents` | DOCTOR | create DRAFT; enforces BR-1 |
| GET | `/hospital/blood-consents/{id}` | DOCTOR,NURSE,ADMIN,(BLOOD_BANK,MRD) | ownership-checked |
| GET | `/hospital/patients/{patientId}/blood-consents` | DOCTOR,NURSE,ADMIN | tenant+patient scoped |
| PUT | `/hospital/blood-consents/{id}` | DOCTOR | **only while DRAFT** (BR-3) |
| POST | `/hospital/blood-consents/{id}/sign` | DOCTOR,NURSE(assist) | records a signature party |
| POST | `/hospital/blood-consents/{id}/submit` | DOCTOR | validates BR-7/8/9 → COMPLETED |
| POST | `/hospital/blood-consents/{id}/verify` | BLOOD_BANK *(new)* | pre-issue verification (BR-2) |
| GET | `/hospital/blood-consents/{id}/print` | DOCTOR,NURSE,ADMIN,MRD | PDF, only when COMPLETED |

## 9. UI Design
Collapsible section cards, read-only data visually distinct (grey) from editable (white): **Patient Info (auto)** → **Doctor Explanation** → **Consent Statement** → **Patient/Guardian Signature** (guardian block reveals when "patient cannot sign") → **Witnesses** → **Interpreter** (reveals on toggle) → **Submit**. Autosave draft; inline validation; sticky "Submit consent" bar. Mental model: **checklist + signature ceremony** (product-philosophy Ch.2). Desktop 2-col, tablet/mobile single-col stacked; signature pads finger-friendly.

## 10. Workflow
```
(no consent) --[POST, BR-1 ok]--> DRAFT
DRAFT --[PUT edits]--> DRAFT
DRAFT --[sign: patient/guardian, witnesses, doctor]--> DRAFT (signatures collected)
DRAFT --[submit, BR-7/8/9 pass]--> COMPLETED (immutable, BR-3)
COMPLETED --[verify]--> VERIFIED (blood issue unblocked, BR-2)
COMPLETED/VERIFIED --[amend]--> new version DRAFT (parent preserved, BR-4)
any --[cancel]--> CANCELLED (soft, audited; never hard-delete BR-5)
COMPLETED --> archived to MRD (permanent)
```

## 11. Validation
Consent date not future · time auto-captured · age 0–120 · sex ∈ {M,F,O} · doctor ∈ hospital & attending · guardian block required iff `patient_signed=false` · interpreter block required iff `interpreter_required=true` · `explanation_given=true` to submit · patient name no digits · one active consent per `blood_request_id`.

## 12. Permissions
| Role | Create | Edit(draft) | View | Sign | Submit | Verify | Print | Delete |
|---|---|---|---|---|---|---|---|---|
| Doctor | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| Nurse | draft assist | limited | ✅ | assist | ❌ | ❌ | ✅ | ❌ |
| Blood Bank *(new)* | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ |
| MRD *(new)* | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Hospital Admin | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ |

## 13. Print Rules
A4, hospital logo + name header, form code **VH/NABH/OT/02/2026** + page numbers; QR/barcode encoding UHID + IPD no + consent public_id; signature blocks for patient/guardian/2 witnesses/doctor/interpreter with printed name + timestamp; 3 copies (patient / case-file / MRD); rendered via `PdfService`/`ClinicalPdfService` (OpenPDF) with a new `blood-consent.html` Thymeleaf template; printable only when COMPLETED.

## 14. Audit Logs
Reuse `AuditLogService.logAction(action, details, performedBy, hospitalId, entityType="BLOOD_CONSENT", entityId, reason)`. Log CREATE/UPDATE/SIGN/SUBMIT/VERIFY/CANCEL with old→new value, user, role, timestamp, IP/browser (add if not captured), hospital_id, reason. Audit rows never deleted.

## 15. Digital Improvements
Auto-fill UHID/IPD/bed/name/age/sex/date/time/doctor from context (only signatures + consent-specifics typed) · secure digital signature capture · show linked blood-request + lab crossmatch/compatibility alongside · prevent duplicate consent for same transfusion unless explicitly renewed · auto-generate NABH-format PDF on submit · auto-timestamp everything.

## 16. Missing / Intelligent Features
Crossmatch/compatibility link (needs Lab result) · consent-expiry warning if transfusion not done within N hours · duplicate-consent warning (BR-6) · critical alert if issue attempted without VERIFIED consent · reaction-monitoring follow-on form after transfusion.

---

## Module & workflow placement
- **Owning module:** Consent Management (new) — first consumer of a reusable **Consent + Digital-Signature + Document** service usable by all future consent forms.
- **Creates:** Doctor/Clinical. **Updates:** Clinical/Nursing (draft). **Views:** Clinical, Nursing, Blood Bank, MRD, Admin. **Prints:** all viewers. **Archives:** MRD.
- **Fed by:** Patient Mgmt, IPD Admission, `DoctorOrder` (transfusion), **Blood Bank (new)**, Lab (crossmatch).
- **Feeds into:** Blood Bank issue gate, Nursing transfusion/MAR, MRD, Audit, Notifications, Print.
- **New capabilities this one form implies (reusable):** Consent service · Digital-Signature infra · Document/versioning store · **Blood Bank module** · Interpreter role · MRD Officer role.
