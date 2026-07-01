# Shared Capability — Digital Signature & Document/Versioning Service

Reusable infrastructure referenced by **every** form that needs signatures,
immutability, versioning, or MRD archival. Spec once; forms link here instead of
re-specifying signature/print/audit plumbing. **New build.** First consumer:
[Form 01 – Blood Transfusion Consent](../01-blood-transfusion-consent.md).

## Why it exists
NABH forms repeat the same needs: capture signatures from multiple parties,
lock a document as immutable once submitted, keep an amendment history, render a
compliant PDF, and archive to MRD. Building this per-form would duplicate logic
50+ times and drift. One service, consistent behaviour, one audit trail.

## A. Signature capture (`signature`)
| Column | Type | Notes |
|---|---|---|
| id / public_id | BIGINT / VARCHAR | |
| hospital_id | BIGINT NOT NULL, INDEX | tenant key — filter every query |
| entity_type | VARCHAR(40) | e.g. `BLOOD_CONSENT` |
| entity_id | BIGINT | FK to the owning document row |
| party_role | VARCHAR(30) | PATIENT / GUARDIAN / DOCTOR / WITNESS_PATIENT / WITNESS_HOSPITAL / INTERPRETER |
| signer_name | VARCHAR(100) | printed name |
| signer_user_id | BIGINT nullable | set when signer is a logged-in user (doctor/nurse) |
| signature_ref | VARCHAR | pointer to signature image/vector blob in Document store (not inline) |
| signed_at | TIMESTAMP | auto |
| ip / user_agent | VARCHAR | captured at sign time |

Rules: a party signs at most once per document version (unique
`entity_type,entity_id,party_role,version`); signatures are append-only; the
`signer_user_id` for DOCTOR must equal the authenticated doctor of the hospital.

## B. Document versioning & immutability
- Any submittable clinical/consent document gets: `version`, `parent_id`,
  `status` (DRAFT→COMPLETED→[amended]→new DRAFT), `is_deleted` (soft only).
- **COMPLETED = immutable.** Edits after completion fork a new version; original
  preserved forever. Hard-delete is never allowed on completed documents.

## C. PDF render + MRD archival
- One entry point wrapping `PdfService` (OpenPDF + Thymeleaf). Each form supplies
  its own template (`templates/<form>.html`) + metadata (form code, copies).
- Standard header (logo, hospital, form code, page numbers), QR/barcode (UHID +
  encounter no + document public_id), signature blocks with printed name + time.
- On COMPLETED, document is flagged archived → visible to MRD (read-only).

## D. Audit
All create/update/sign/submit/verify/cancel go through
`AuditLogService.logAction(...)` with `entity_type`, old→new, user, role, time,
IP, `hospital_id`, reason. Never deleted.

## API surface (generic, reused)
| Verb | Path | Purpose |
|---|---|---|
| POST | `/hospital/{entity}/{id}/sign` | add a signature party (body: party_role, signer_name) |
| POST | `/hospital/{entity}/{id}/submit` | validate + lock → COMPLETED |
| GET | `/hospital/{entity}/{id}/print` | render PDF (COMPLETED only) |

Each form binds these to its own `@PreAuthorize` roles and its own submit
validations; the signing/locking/print/audit behaviour is shared. Every `{id}`
call validates `hospital_id` ownership (audit SEC rule).

## How a form references this
In a form spec, sections **§5 (signature fields)**, **§7 (versioning columns)**,
**§13 (print)**, **§14 (audit)** may say *"via Signature & Document service"*
and list only form-specific parties/validations — not re-specify the plumbing.
