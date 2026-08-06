# Legacy Patient Data Import — Design

**Date:** 2026-08-06 · **Scope:** HOSPITAL + CLINIC tenants · **Actor:** `HOSPITAL_ADMIN`
**Status:** Approved design, pending implementation plan

When a hospital is acquired or adopts this software, they arrive with years of patient records in
their old system. This feature migrates that data in with **zero data loss**: everything that can be
mapped is mapped, everything else is preserved, and anything that cannot be imported is reported
rather than silently dropped.

This is one of three related features. The other two — per-hospital data export, and patient document
upload — get their own specs. Ordering is **Import → Documents → Export**, so export ships last and
can cover both documents and imported custom fields.

---

## 1. Requirements

| #   | Requirement                                                                        | Source   |
| --- | ---------------------------------------------------------------------------------- | -------- |
| R1  | Import patients, visits, prescriptions and bills from a legacy system              | User     |
| R2  | Blank source values stay blank — never substituted, never rejected for being empty | User     |
| R3  | Columns the schema does not model are preserved and readable                       | User     |
| R4  | No silent data loss: every row either lands or appears in an error report          | User     |
| R5  | Accept `.xlsx` (sheet per entity) and `.csv`                                       | Decision |
| R6  | Dry-run preview before any write; full undo of a committed batch                   | Decision |
| R7  | Re-running the same file updates matched records instead of duplicating            | Decision |
| R8  | Imported financial history must not silently move existing revenue figures         | Decision |

### Out of scope

Scheduled/recurring imports · direct DB-to-DB migration · record-by-record review UI ·
promoting legacy columns to first-class patient fields · importing documents or attachments
(that is feature 2).

---

## 2. Key constraints discovered in the codebase

These drove the design and are the reason it is not a thin CSV reader.

1. **Validation lives on the entity.** `Patient` carries `@NotBlank` on `name`/`gender`/`phone` and
   `@Pattern("^[0-9]{10}$")` on `phone` ([Patient.java:104-115]). `PatientController.addPatient`
   binds `@Valid @RequestBody Patient` directly — there is no request DTO. Because JPA runs Bean
   Validation on persist, these rules fire on **every** save, so an importer writing `Patient`
   objects is blocked by Hibernate even though it never calls the controller. R2 is therefore
   impossible without moving validation.
2. **`customId` is derived, not supplied.** It is assigned as `PAT<auto-increment-id>` _after_ the
   first save ([PatientService.java:155]), so it cannot hold a hospital's existing MRN.
3. **Upload cap is 5 MB** (`spring.servlet.multipart.max-file-size`) and it is global.
4. **Prior art exists and should be followed:** `CsvUploads` + `MedicineService.importCatalogCsv`
   for upload validation and `{imported, updated, errors}` reporting; `opd.custom_vitals`
   ([Opd.java:52]) for storing hospital-defined keys the schema does not know; `FormRegistry` /
   `VitalRegistry` for canonical-list-by-key.
5. **No backend file storage exists.** Logo uploads go browser → Cloudinary with an _unsigned_
   preset ([ProfileModal.jsx:182]); only a URL is persisted. Nothing in the backend accepts and
   stores a file today.

---

## 3. Architecture

A **generic import engine** owns everything identical across entities — upload, parse, column
mapping, dry-run, batch commit, progress, undo, error reporting. Each entity contributes a small
`EntityImporter`.

Rejected alternatives: four independent import services (duplicates dry-run/mapping/undo four times
and makes cross-entity undo very hard), and staging tables (most of the cost of the review workflow
that was explicitly declined).

| Component                 | Responsibility                                                                                               |
| ------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `ImportEngine`            | Orchestrates upload → map → dry-run → commit → undo. Entity-agnostic.                                        |
| `WorkbookParser`          | Streams `.xlsx` via the POI **event** API (never DOM) and `.csv`. Yields headers + a row iterator per sheet. |
| `ImportFieldRegistry`     | Per entity: canonical target fields and header synonyms for auto-mapping. Same shape as `FormRegistry`.      |
| `EntityImporter`          | Interface: `targetFields()`, `validateRow()`, `dedupeKey()`, `resolveParent()`, `apply()`.                   |
| `ImportBatchService`      | Batch lifecycle, counts, undo including the clinical-activity guard.                                         |
| `ImportProgressPublisher` | Progress events over the existing `HospitalWebSocketHandler`.                                                |

Controller: `/hospital/imports/**` and `/clinic/imports/**`, `HOSPITAL_ADMIN` only. UI entry point is
a Settings card beside `VitalsSettingsCard` and `FilesAndAccessCard`.

**Not module-gated.** Migration is needed by any tenant regardless of plan, matching how Files &
Access and OPD vitals settings are treated.

---

## 4. Data model

### 4.1 Two-tier validation

Strict field rules move **off `Patient` and onto a new `PatientRequest` DTO** bound by the create and
update endpoints. The entity retains only what protects the database — column lengths and
nullability. Manual creation stays exactly as strict as today, with identical messages and an
unchanged JSON contract, so the frontend needs no change. The importer constructs entities directly
and is bound only by DB constraints.

The cheaper alternative — keep the annotations and disable JPA validation globally — was rejected: it
removes write-time validation for every entity in the system to solve a problem in one.

`Patient.gender` and `Patient.phone` become nullable.

### 4.2 New tables

**`import_batch`** — one row per import attempt.

| Column                                                            | Notes                                                         |
| ----------------------------------------------------------------- | ------------------------------------------------------------- |
| `id`, `public_id`, `hospital_id`                                  | tenancy, existing convention                                  |
| `entity_type`                                                     | `PATIENT` \| `VISIT` \| `PRESCRIPTION` \| `BILL`              |
| `status`                                                          | `DRAFT → PREVIEWED → RUNNING → COMPLETED \| FAILED \| UNDONE` |
| `source_filename`, `sheet_name`                                   | provenance                                                    |
| `mapping_json`                                                    | the mapping used — enables identical re-run                   |
| `created_count`, `updated_count`, `skipped_count`, `failed_count` |                                                               |
| `created_by`, `created_at`, `committed_at`, `undone_at`           | audit                                                         |

**`import_row_error`** — `batch_id`, `row_num`, `column_name`, `message`, `raw_row_json`.
(`row_num`, not `row_number`: the latter is reserved in MySQL 8.0+ for the window function, and an
identifier that is only legal when quoted is a trap for JPA mappings. Found during implementation.)
A table rather than a JSON blob so 8,000 errors can be paginated and streamed to CSV.

### 4.3 Columns added to `Patient` (mirrored on history entities in later phases)

| Column            | Purpose                                                                                                                                                                                    |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `legacy_id`       | The hospital's old MRN. **Indexed, unique per `hospital_id`.** Serves as dedupe key, join key for child rows, and a searchable value so staff can find a patient by their old card number. |
| `source`          | `MANUAL` \| `IMPORTED`                                                                                                                                                                     |
| `import_batch_id` | Nullable FK. Lineage — this is what makes undo possible.                                                                                                                                   |
| `custom_fields`   | TEXT holding JSON of unrecognised columns, following `opd.custom_vitals`.                                                                                                                  |

### 4.4 Migration

Idempotent `ensureXxx()` methods in `DatabaseMigrationRunner`, mirrored into `setup/schema-full.sql`,
per the existing convention. **The migration must backfill `source = MANUAL` on all existing
patients**, otherwise every pre-existing record reads as ambiguous.

---

## 5. Behaviour

### 5.1 Admin flow

```
1  Upload .xlsx/.csv   dedicated streaming endpoint with its own size limit;
                       the global 5 MB cap is left untouched.
                       Returns detected sheets, headers, 10 sample rows.
                       Batch created as DRAFT.

2  Map columns         Auto-suggested from header synonyms
                       ("Patient Name"→name, "Mob No"→phone, "MRN"→legacy_id).
                       Admin corrects. Unmapped columns are listed explicitly:
                       "7 columns will be preserved as Imported information: …"

3  Preview (dry-run)   Parses the whole file, writes nothing.
                       "6,431 new · 12 update · 1,557 duplicate · 43 warnings"
                       plus sample resulting records and the full error list.

4  Commit              Background job; live progress via WebSocket.
                       Chunked commits of 500 rows per transaction.

5  Result              Downloadable errors.csv (§5.6). Undo offered, subject to §5.4.
```

Step 3 writing nothing is the core safety property: the admin sees the real outcome against the real
file before a single row is persisted.

### 5.2 Matching

- **Dedupe:** `legacy_id` scoped to `hospital_id`. Deterministic, and it satisfies R7.
- **No legacy ID column:** fall back to `name + phone`. If `phone` is blank the fallback key degrades
  to name alone, which is **not** a sufficient identity — in that case the row is treated as new, and
  flagged as a possible duplicate in the report rather than matched. Where the fallback matches more
  than one existing patient, **skip and report — never merge.** A wrongly merged patient record is
  worse than a duplicate.
- **Parent linking:** child rows resolve to a patient by legacy patient ID. Unresolvable rows become
  error rows, never silent drops (R4).
- **Unmappable references are kept as text, not rejected.** A legacy doctor absent from the `Doctor`
  table is stored as a name on the imported visit; a prescription medicine absent from the catalogue
  is kept as a free-text line. Both are flagged for later reconciliation.

### 5.3 Unknown columns

Stored as JSON in `custom_fields` and rendered as a read-only **"Imported information"** panel on the
patient profile. Included in exports and printouts. Not searchable, not editable, never lost (R3).

### 5.4 Undo

Undo **soft-deletes** the rows created by a batch — `Patient.isActive` already exists — and never
issues a hard `DELETE`.

**A batch can only be reversed while its records remain clinically untouched.** If an imported patient
has since gained a real OPD visit, prescription, or bill created in this system, undo is refused and
the offending records are named. Cascading would destroy live clinical data.

**Undo followed by re-import.** Soft-deleted rows keep their `legacy_id`, which still occupies the
unique `(hospital_id, legacy_id)` index — so a naive re-import of the corrected file would collide.
Re-import therefore **matches soft-deleted rows too, and reactivates them** (`isActive = true`, new
`import_batch_id`) rather than inserting. This is the expected recovery path after a mis-mapped
import and must be covered by a test.

### 5.5 Imported bills and reporting

Imported bills are visible on the patient record and in their billing history, but **financial reports
exclude them by default**, behind an explicit "include imported history" toggle (R8). Importing three
years of history must not silently move a hospital's revenue figures.

### 5.6 The correction loop — errors.csv round-trips

The error download is **shaped for re-upload**: the original header row, only the rows that failed,
plus a trailing `_error` column explaining each one. The admin fixes those rows in place and uploads
that small file as a new batch.

This matters because it is the only correction path that is safe when the source file has **no MRN
column**. Without `legacy_id`, dedupe degrades (§5.2) and re-uploading the full file after a commit
would insert duplicates. Re-uploading only the failed rows touches nothing that already landed.

The mapping step therefore **warns when no `legacy_id` is mapped**, stating that re-uploads of the
full file will not be able to match existing records, and that corrections should be made either at
preview (before committing) or via the errors.csv round-trip.

A correction that changes the MRN itself creates a new record rather than fixing the old one —
identity keys cannot self-correct. Undo is the clean recovery for that case.

---

## 6. Failure handling

Two classes:

- **Fatal** — unreadable file, no header row, no mappable target field. Nothing is written; the batch
  is rejected.
- **Row-level** — bad date, unresolvable parent, duplicate. Recorded in `import_row_error` with the
  raw row; the run continues.

One malformed row out of 8,000 must never cost the other 7,999.

**Mid-commit crash:** chunks commit in separate transactions, each row carrying `import_batch_id`. A
crash leaves the batch `FAILED` with accurate counts, and undo reverses exactly what landed.

**Concurrency:** one running import per hospital, enforced by a status check on `import_batch`.
Concurrent overlapping imports would race on the dedupe key.

---

## 7. Security

**Tenant isolation is the critical control.** `hospital_id` comes from the JWT via
`SecurityHelper.getCurrentHospitalId()` and is stamped on every row. A `hospital_id` column present in
the uploaded file is **ignored, never honoured** — otherwise a crafted spreadsheet becomes a
cross-tenant write.

- `HOSPITAL_ADMIN` only.
- POI configured with zip-bomb ratio limits and external entity resolution disabled. `.xlsx` is a zip
  of XML and is a real attack surface.
- **The uploaded file is PHI.** Non-web-served directory, `0600`, deleted on batch completion or
  expiry, never logged. Directory ownership must be an explicit deployment step, not an assumption —
  see the `/var/backups` permission failure documented in `docs/database/BACKUP_AND_RESTORE.md`.
- Error CSV downloads are escaped against formula injection; a legacy field containing `=cmd|…` must
  not execute when opened in Excel.
- Batch commit and undo are recorded via `AuditLogService` with row counts and the acting admin.

---

## 8. Testing

| Area                  | Cases                                                                                                                                                    |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Parser                | multi-sheet, blank rows, merged cells, dates stored as text, UTF-8 names, CSV with BOM                                                                   |
| Importer (per entity) | mapping, dedupe hit/miss, blank preservation, custom-field capture                                                                                       |
| Engine                | dry-run writes nothing (assert row counts unchanged); commit idempotent on re-run; undo restores prior state; undo refused when clinical activity exists |
| Tenant isolation      | a file carrying a foreign `hospital_id` writes to the caller's hospital — non-negotiable                                                                 |
| Realistic fixture     | deliberately messy acquisition data: missing genders, `+91` phones, duplicate MRNs, three unknown columns                                                |

Lands in the existing backend suite (`mvn test`, JaCoCo coverage floor in CI).

---

## 9. Phases

Each phase gets its own implementation plan.

| Phase | Delivers                                                                                 | Size  | Notes                                                              |
| ----- | ---------------------------------------------------------------------------------------- | ----- | ------------------------------------------------------------------ |
| **1** | Engine, `PatientImporter`, two-tier validation refactor, schema + migration, Settings UI | **L** | ~70% of total work; independently shippable and immediately useful |
| **2** | `VisitImporter`                                                                          | M     | First child entity — proves parent linking                         |
| **3** | `PrescriptionImporter`                                                                   | M     | Line items + catalogue matching                                    |
| **4** | `BillImporter`                                                                           | M     | Money; reporting toggle from §5.5                                  |

Phase 1 carries essentially all the risk. Phases 2–4 are largely repetitions of an interface once it
exists.

---

## 10. Limits and retention

| Setting                                       | Value                     | Rationale                                                                                                                       |
| --------------------------------------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Import upload cap                             | **50 MB**                 | Well above a realistic full-history workbook; enforced on the dedicated endpoint only, so the global 5 MB cap is unaffected.    |
| Rows per batch                                | **100,000**               | A ceiling that keeps preview responsive and bounds worst-case job duration. Larger migrations are split by the admin.           |
| Uploaded file retention                       | **30 days**, then deleted | It is PHI. Undo does not need the file — batch lineage is in the database — so deletion does not remove the ability to reverse. |
| `import_batch` / `import_row_error` retention | Indefinite                | Migration provenance is an audit record; the rows are small.                                                                    |

**`legacy_id` collisions.** Two legacy systems merged into one hospital can produce the same MRN for
different people. Silently namespacing would corrupt the join key that child rows depend on, so the
importer instead **rejects the colliding rows and reports them**, naming both the existing and the
incoming record. The admin resolves it in the source file — for example by prefixing one system's
IDs — and re-imports. Guessing here risks attaching one patient's history to another.
