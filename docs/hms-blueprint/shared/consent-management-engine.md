# Shared Capability — Consent Management Engine

The single engine behind **every** consent in the HMS. General Consent
([Form 05](../05-general-consent.md)) is its canonical/root instance; Blood
([Form 01](../01-blood-transfusion-consent.md)), Surgery, Anaesthesia, Procedure,
High-Risk, ICU, DAMA, and future consents are **types** on the same engine — not
separate forms. Builds on the [Signature & Document service](./signature-and-document-service.md)
(signatures, versioning, immutability, PDF, MRD archival); this doc adds the
**consent-specific** model on top. **New build.**

## Why it exists
The architect's directive (Form 05): *"The General Consent Form should not exist as
an isolated PDF. It should be the entry point of a centralized Consent Management
System."* Nine+ consent types share identical needs — auto-fill patient/encounter,
patient/guardian/witness signatures, versioning, lock-on-submit, print, MRD
archival, audit. Build once; every type reuses it. This also **reconciles Form 01**:
`blood_transfusion_consent` is not a rival table — it is `consent_type='BLOOD'` on
this engine plus a type-specific detail record.

## A. Core table `patient_consent` (generic, all types)
| Column | Type | Notes |
|---|---|---|
| id / public_id | BIGINT / VARCHAR | |
| hospital_id | BIGINT NOT NULL, INDEX | tenant key — filter every query |
| patient_id | BIGINT NOT NULL | FK |
| admission_id | BIGINT nullable | FK (null for OPD consents) |
| encounter_type | VARCHAR | OPD / IPD / EMERGENCY |
| consent_type | VARCHAR(30) | GENERAL / BLOOD / SURGERY / ANAESTHESIA / PROCEDURE / HIGH_RISK / ICU / DAMA / SPECIAL |
| version | INT | starts 1; new version on any post-lock change |
| parent_id | BIGINT nullable | previous version |
| status | VARCHAR(12) | DRAFT / SIGNED / SUBMITTED / LOCKED / ARCHIVED / SUPERSEDED |
| patient_signed | BOOLEAN | |
| guardian_signed | BOOLEAN | |
| relationship | VARCHAR(40) | guardian relationship (if guardian signs) |
| signature_type | VARCHAR | FINGER / STYLUS / OTP / DIGITAL_CERT |
| witness_id | BIGINT nullable | staff witness |
| language | VARCHAR(20) | language consent was presented in |
| interpreter_id | BIGINT nullable | if interpreter used |
| signed_at | TIMESTAMP nullable | |
| remarks | TEXT | |
| created_by / created_at / updated_at | audit cols | |
| is_deleted | BOOLEAN | soft only; LOCKED never hard-deleted |

- **Unique (partial):** at most **one active** (`status NOT IN (SUPERSEDED,ARCHIVED)`) row per `(hospital_id, admission_id, consent_type)` → enforces "one active consent per type per admission".
- **Index:** `(hospital_id, admission_id)`, `(hospital_id, patient_id, consent_type)`.

## B. Type-specific detail (`consent_detail`)
Generic table stays lean; each `consent_type` stores its extra fields as a typed
detail row / JSON keyed by `consent_id`. Example: Blood ([Form 01](../01-blood-transfusion-consent.md))
keeps its product/reaction/indication fields here (or in its own `blood_consent_detail`
table 1:1 with `patient_consent`) — **not** by duplicating the signature/version/status
plumbing. Recommended: **core `patient_consent` + one 1:1 detail table per rich type**;
simple types (General, ICU) need no detail.

## C. Signatures — via [Signature & Document service](./signature-and-document-service.md)
Parties: PATIENT, GUARDIAN, WITNESS, INTERPRETER, (type-specific: DOCTOR for
procedure/blood). Signature methods: finger/stylus/OTP/digital-cert (store image ref,
timestamp, user id, device info, IP). A party signs at most once per version.

## D. Lifecycle (shared state machine)
```
DRAFT → [patient/guardian review]
      → [signature captured] → SIGNED
      → [witness + submit; validations pass] → SUBMITTED → LOCKED (immutable)
LOCKED → [any change] → new version DRAFT (old = SUPERSEDED)
LOCKED → [encounter archived] → ARCHIVED (MRD read-only)
```
LOCKED = read-only. Edits fork a new version (never edit original).

## E. Auto-fill (fetch-not-type)
UHID, name, age, gender, address, mobile, admission no, ward, bed, consultant
doctor, date/time, hospital name — all pulled from patient + admission context.
Only signatures/acknowledgements require interaction.

## F. API surface (generic; each type binds its own roles + validations)
| Verb | Path | Purpose |
|---|---|---|
| POST | `/hospital/consents` | create (type, patient, admission) → DRAFT |
| GET | `/hospital/consents/{id}` | detail (tenant-checked) |
| GET | `/hospital/patients/{id}/consents` | all consents for a patient |
| POST | `/hospital/consents/{id}/sign` | capture a party signature |
| POST | `/hospital/consents/{id}/submit` | validate + lock → LOCKED |
| GET | `/hospital/consents/{id}/print` | PDF (LOCKED only) |
| GET | `/hospital/consents/{id}/versions` | version history |

Every `{id}` validates `hospital_id` ownership (audit SEC rule). Each consent type
supplies its own `@PreAuthorize` roles, mandatory-party rules, and print template.

## G. Audit — via `AuditLogService`
created/opened/reviewed/signed/witness-added/submitted/printed/archived, each with
user, role, timestamp, old→new status, IP. `entity_type="PATIENT_CONSENT"`,
`consent_type` in details. Never deleted.

## H. How a form references this
A consent form spec (General, Blood, Surgery, …) specifies only: its **type**, its
**mandatory parties**, its **type-specific detail fields (§B)**, its **declaration
text**, its **print template**, and its **role permissions**. Signature capture,
versioning, lock, print plumbing, MRD archival, audit → *"via Consent Management
Engine"*. **Prerequisite data:** `Patient` needs `guardian_name`, `guardian_relationship`,
`date_of_birth` (for minor detection), `preferred_language`; these don't exist today
(see Form 05 §Prerequisite).
