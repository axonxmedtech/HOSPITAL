# Form Spec — <FORM NAME>

> Fill one file per form from this template. File name: `NN-form-slug.md`
> (e.g. `01-general-consent.md`). Keep every section; write "N/A — reason" if
> a section truly does not apply. Ground every field/table/API in the **actual**
> pasted form + the existing codebase (link `entity/…`, `controller/…`). Mark
> anything invented-because-not-in-source as `[ASSUMPTION]`.

| | |
|---|---|
| **Status** | Draft / Reviewed / Approved |
| **Source** | pasted form image/text (date) |
| **Existing code?** | new / partially exists (`link`) / exists (`link`) |

---

## 1. Form Overview
Department · Module · Filled By · Approved By · Verified By · Stored In · Lifecycle (transient/permanent) · NABH clause (if known).

## 2. Purpose
Hospital use · NABH requirement · Legal · Clinical · Business rationale (1 line each).

## 3. Trigger
The exact event chain that makes this form appear (upstream → this form → downstream). Name the state that gates it.

## 4. User Roles
Who interacts and in what capacity (creator / approver / verifier / viewer / printer / archiver). Map each to an existing HMS role (`HOSPITAL_ADMIN`, `DOCTOR`, `RECEPTIONIST`, `NURSE`, `PHARMACIST`, `LAB_TECHNICIAN`, `RADIOLOGY_TECHNICIAN`) or flag a **missing role**.

## 5. Fields
One row per field — no exceptions.

| Field | Type | Max | Mandatory | Editable rule | DB column | Validation | Searchable | Printable | Source (auto/manual) |
|---|---|---|---|---|---|---|---|---|---|

## 6. Business Rules
Every gate: what must be true to create / sign / print / edit / delete. Express as `IF … THEN …` so they can feed a rules engine later (see product-philosophy Ch.14).

## 7. Database Design
Tables, columns, PK/FK, unique constraints, indexes, `hospital_id` (tenant), audit columns (`created_at/by`, `updated_at/by`), soft-delete flag, history strategy. **Every tenant-owned table MUST carry `hospital_id` and every query MUST filter by it** (see audit SEC-1/2/3).

## 8. APIs
Verb · path · roles (`@PreAuthorize`) · request · response · tenant-scoped query note. Include GET/POST/PUT/DELETE plus PRINT/DOWNLOAD/SIGN/VERIFY/APPROVE as needed. Every `{id}` endpoint MUST validate `hospital_id` ownership.

## 9. UI Design
Layout for desktop/tablet/mobile · sections/chunks (Miller's rule) · autosave · inline validation · sticky action bar · which real-world mental model it borrows (see product-philosophy Ch.2).

## 10. Workflow
Full state machine from creation to MRD archival. Draw it as `state → [event/guard] → state`. This is the most important section.

## 11. Validation
Every field + cross-field rule with exact bounds (age ≥ 0, temp 35–43 °C, SpO₂ 0–100, BP > 0, phone 10 digits, email RFC, no future DOB, etc.).

## 12. Permissions
Per role: Create / Read / Edit / Delete / Print / Sign / Approve (Yes / Limited / No). MRD = read-only archive. Must match §8 `@PreAuthorize`.

## 13. Print Rules
Margins · header/logo · QR/barcode (UHID/IPD no.) · page numbers · signature blocks · copies (patient/file/MRD) · PDF template (`templates/…html`, OpenPDF).

## 14. Audit Logs
What is logged on every mutation: who · when · old→new value · IP · browser · hospital_id · reason. Map to existing `AuditLogService`.

## 15. Digital Improvements
Paper → intelligent: auto-fill (name/UHID/doctor/timestamp from context), digital signature, fetch-not-type. One line per improvement.

## 16. Missing / Intelligent Features
Alerts, interaction checks, consent-expiry, duplicate warnings, risk scores, critical-value flags relevant to THIS form.

---

## Module & workflow placement
- **Owning module:** …
- **Creates / Updates / Views / Prints / Archives:** which modules do each.
- **Feeds into:** downstream forms/modules · **Fed by:** upstream.
- **New modules this form implies** (if any) — add to `README.md` gap list.
