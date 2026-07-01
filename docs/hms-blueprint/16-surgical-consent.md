# Form Spec — Surgical Consent (Operation Consent) — a *type* on the Consent Engine

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form analysis — *VH/NABH/OT/02/2026* (2026-07-01) |
| **Existing code?** | **No new module — this is `consent_type='SURGERY'` on the shared [Consent Management Engine](./shared/consent-management-engine.md).** Same pattern as [Form 01 Blood Consent](./01-blood-transfusion-consent.md) (`consent_type='BLOOD'`). The engine itself is **spec-only today** (no `patient_consent` entity in code yet — Forms 05/01/16 all depend on it being built once). Completes the **Surgical Readiness gate** from [Form 15 PAC](./15-pre-anaesthesia-assessment.md). |

> **Read first — this is a reconciliation, not a build.**
> The architect is explicit: *"Do NOT create another standalone module. Reuse the same Consent Engine."*
> So the form's proposed `surgical_consent` table is **not** a new table — it is
> **`patient_consent` (consent_type='SURGERY') + `surgery_consent_detail`**, exactly as Blood consent is
> `consent_type='BLOOD'` + `blood_consent_detail`. Lifecycle (DRAFT→SIGNED→SUBMITTED→LOCKED→ARCHIVED),
> versioning, partial-unique (one active per admission per type), signatures, print, audit → **all "via
> Consent Engine"**, not re-specified here. This doc supplies only what is *surgery-specific*: the detail
> fields, the surgery-change invalidation rule (BR-4), and its slot in the Surgical Readiness gate.
> **Prerequisites (already logged):** the Consent Engine must be built (Form 05), and `Patient` needs
> `date_of_birth`/`guardian_*`/`preferred_language` for guardian (Section E) + interpreter (Section G).

---

## 1. Form Overview
- **Department:** OT (primary); Surgeon, Anaesthesia, Nursing, MRD, Admin (secondary)
- **Module:** **Consent Management → Surgery Consent** (`consent_type='SURGERY'`)
- **Filled By:** Surgeon explains; Patient/Guardian signs; Witness + Interpreter as needed
- **Reviewed By:** OT Nurse verifies before shift-to-OT
- **Archived By:** MRD
- **Lifecycle:** via Consent Engine; gates OT scheduling + shift-to-OT
- **NABH clause:** PRE/PSQ — informed consent for invasive procedures.

## 2. Purpose
- **Hospital use:** legal authorization for the specific surgery — no valid consent, no OT.
- **NABH requirement:** documented informed consent (nature, risks, alternatives, blood, anaesthesia).
- **Legal:** protects patient, surgeon, hospital, anaesthesiologist; first document in medico-legal review.
- **Clinical:** binds the authorization to the correct patient/procedure/surgeon (wrong-site/patient prevention).
- **Business rationale:** one Consent Engine → every consent type consistent, versioned, auditable.

## 3. Trigger
`Surgery recommended → scheduled → PAC → **Surgical Consent** → Blood Consent (if needed) → WHO checklist → shift to OT → operation`. **Never enter OT without a completed surgical consent** (BR-1).

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| Surgeon | explain, create draft | `DOCTOR` |
| Patient | informed consent (sign) | — (patient signature via engine) |
| Guardian | signs if patient cannot | — (guardian, requires `Patient.guardian_*`) |
| Witness | patient-side + hospital-side | — |
| OT Nurse | verify before OT | `NURSE` (OT) |
| Anaesthesiologist | view | `DOCTOR` (anaesthetist flag) |
| MRD | archive | `MRD_OFFICER` (gap) |

Role gaps: **Interpreter** (already logged, Form 05).

## 5. Sections → storage (surgery-specific fields on `surgery_consent_detail`)
| § | Section | Capture | Storage |
|---|---|---|---|
| A | Patient info | UHID/name/age/gender/address/contact | **auto** from `Patient` (engine) |
| B | Surgery details | proposed surgery, diagnosis, surgeon, dept, **elective/emergency**, date/time | `surgery_consent_detail` (linked to `ot_booking_id`) |
| C | Explanation given | nature/purpose/benefits/risks/complications/alternatives/additional-procedures/blood/anaesthesia | **structured checkboxes** + remarks on `surgery_consent_detail` |
| D | Patient consent | name, signature, date/time | engine signatures |
| E | Guardian consent | name, relationship, signature (if minor/unconscious/incapacitated) | engine + `Patient.guardian_*` (gap) — mandatory when patient can't consent |
| F | Witness | patient-side + hospital-side name/signature/date/time | engine signatures (two witness slots) |
| G | Interpreter | name, language, signature (if language assistance) | engine + `Patient.preferred_language` (gap) |

## 6. Database Design
**No new top-level table.** `patient_consent` row with `consent_type='SURGERY'` (via Consent Engine) + **`surgery_consent_detail`** (new detail table): `id, hospital_id, consent_id (→ patient_consent), ot_booking_id (→ ot_bookings), surgeon_id, proposed_surgery, diagnosis, department, surgery_class (ELECTIVE/EMERGENCY), surgery_date, explanation_flags (JSON of the Section C checkboxes), additional_procedure_authorized, blood_authorized, remarks`.
- `hospital_id` on every row; ownership via engine. `ot_booking_id` binds consent to the exact surgery (§14 completeness check).

## 7. Business Rules
- **BR-1** Consent must exist (SUBMITTED) before **OT scheduling is finalized** — a Surgical Readiness check (§9), gating `OtService.scheduleBooking` (Form 15).
- **BR-2** Read-only after submission (engine LOCKED).
- **BR-3** Every revision → new **version**; never overwrite signed consent (engine versioning).
- **BR-4** **If surgery type changes → existing consent invalid → new consent mandatory** (surgery-specific: compare `surgery_consent_detail.proposed_surgery`/`ot_booking_id` to the booking; mismatch invalidates).
- **BR-5** If surgeon changes → hospital policy may require re-verification (configurable).
- **BR-6** If surgery cancelled → consent archived, marked **UNUSED** (engine status).
- **BR-7** Guardian mandatory when patient cannot legally consent (minor via `date_of_birth`/unconscious/incapacitated) — engine rule shared with General consent (Form 05).
- **BR-8** Every query filters `hospital_id`; every `{id}` validates ownership (engine).

## 8. (Signatures / lifecycle / print / audit) — via Consent Engine
Not re-specified. Surgical consent inherits DRAFT→SIGNED→SUBMITTED→LOCKED→ARCHIVED, partial-unique (one active SURGERY consent per admission), digital signatures, version history, print, and audit from the [Consent Engine](./shared/consent-management-engine.md) + [Signature & Document service](./shared/signature-and-document-service.md).

## 9. Surgical Readiness Validation (completes Form 15's gate)
Before shift-to-OT, validate: ✓ **Surgery Consent** · ✓ General Consent (Form 05) · ✓ Blood Consent if required (Form 01) · ✓ PAC complete + FIT (Form 15) · ✓ investigations complete · ✓ blood available (Blood Bank) · ✓ OT scheduled. Any missing → **block OT transfer**. This is the **same Surgical Readiness Score** engine from Form 15 §13 — Form 16 adds the three consent checks to it. The readiness panel becomes the **OT control panel**.

## 10. APIs — via Consent Engine (type=SURGERY)
Under `/hospital/consents?type=SURGERY` (engine endpoints), every `{id}` validates `hospital_id`.
| Verb | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/hospital/consents` (type=SURGERY) | SURGEON | create draft |
| POST | `/hospital/consents/{id}/sign` | PATIENT/GUARDIAN/WITNESS | capture signature |
| POST | `/hospital/consents/{id}/submit` | SURGEON/OT NURSE | finalize (LOCKED) |
| GET | `/hospital/patients/{id}/consents?type=SURGERY` | care team | list |
| GET | `/hospital/consents/{id}/print` | all | PDF |
| GET | `/hospital/ot/readiness/{bookingId}` | OT team | readiness panel (§9) |

## 11. Permissions
| Role | Create | Edit | Sign | View |
|---|---|---|---|---|
| Surgeon | Yes | Draft | No | Yes |
| Patient | No | No | Own | Own |
| Guardian | No | No | Applicable | Applicable |
| OT Nurse | Verify | No | No | Yes |
| Anaesthesiologist | No | No | No | Yes |
| MRD | No | No | No | Archived |

Matches §10 `@PreAuthorize` (engine).

## 12. Notifications
Surgery consent pending · patient signed · guardian signature required · interpreter required · consent completed · OT informed · MRD archive pending. Reuse `WebSocketConfig`.

## 13. Print Rules
Via Consent Engine → [Signature & Document service](./shared/signature-and-document-service.md). `templates/surgical-consent.html`: logo, patient info, surgery details, consent statements, surgeon, patient/guardian/witness signatures, QR, version, digital verification ID. NABH paper-faithful.

## 14. AI & Smart Enhancements
- **Consent completeness check** — all mandatory signatures collected **and** surgery details match the OT schedule: `surgery_consent_detail` vs `OtBooking` (procedure, surgeon, date). Mismatch → block (wrong-patient/procedure guard).
- **Language assistance** — render consent in `Patient.preferred_language`; if translated, require interpreter documentation (Section G).
- **Consent expiry** — surgery postponed beyond configurable validity → "may require renewal."
- **Smart OT readiness** — §9 panel; the OT control panel.

## 15. Validation
`consent_type='SURGERY'`; `surgery_class` ∈ {ELECTIVE,EMERGENCY}; guardian block enforced when patient cannot consent (BR-7); `ot_booking_id` must match an active booking; surgery-detail mismatch invalidates (BR-4); submitted consent immutable (amend=new version). Server-side (engine).

## 16. Audit Logs
Via Consent Engine + `AuditLogService` (`entity_type="CONSENT"`, `consent_type="SURGERY"`): created · signed (patient/guardian/witness) · submitted · invalidated (surgery change) · cancelled-unused · readiness pass/block — user, role, timestamp, version, IP.

---

## Module & workflow placement
- **Owning module:** Consent Management → Surgery Consent (`consent_type='SURGERY'`) — **reuses the engine**.
- **Creates:** `patient_consent` (SURGERY) + `surgery_consent_detail`. **Gates:** OT scheduling + shift-to-OT (Surgical Readiness). **Binds to:** `OtBooking` (procedure/surgeon/date match). **Reads:** `Patient` (guardian/language gaps). **Prints:** surgical consent. **Archives:** MRD.
- **Feeds into:** Surgical Readiness / OT control panel (Form 15) · WHO checklist (`OtChecklist`) · OT · MRD. **Fed by:** Surgery scheduling (`OtBooking`) · Consent Engine · PAC (Form 15).
- **New this form implies (add to README):** **nothing structurally new** — it validates the Consent Engine (Form 05) as multi-type (GENERAL/BLOOD/SURGERY all one engine) and the Surgical Readiness gate (Form 15) as consent-aware. Adds only **`surgery_consent_detail`** (a detail table, like `blood_consent_detail`) and the **surgery-change invalidation rule** (BR-4). Reconfirms the Consent-Engine + Patient-guardian/language prerequisites.
