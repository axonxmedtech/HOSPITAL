# Form Spec — IPD Files Front Checklist (Medical Record Completeness)

| | |
|---|---|
| **Status** | Draft |
| **Source** | pasted form text — *VH/NABH/MRD/01/2026* (2026-07-01) |
| **Existing code?** | **partially exists** — archival stub only. Extends [`MrdService`](../../backend/src/main/java/com/hms/service/hospital/MrdService.java) / [`MrdController`](../../backend/src/main/java/com/hms/controller/hospital/MrdController.java) / [`MrdRecord`](../../backend/src/main/java/com/hms/entity/MrdRecord.java). **The checklist/verification layer does not exist yet.** |

> **Codebase diff — read this first.** Today `MrdService.archiveAdmission(ipdAdmissionId, rackLocation)`
> only asserts `status == "DISCHARGED"` + tenant match, generates `MRD-<seq>`, stores a rack
> location, sets `status=ARCHIVED`. **It performs zero document-completeness checking.** This form
> is that missing quality gate: a per-file checklist that must pass (all *applicable* records
> present + complete + signed) **before** archival is allowed. Implementation = a new
> `mrd_file_checklist` table + verification engine that gates the existing `archiveAdmission` call.

---

## 1. Form Overview
- **Department:** Medical Records Department (MRD)
- **Module:** MRD → **File Verification** (new sub-module; extends existing MRD archive)
- **Filled By:** MRD Officer (**role gap** — see §4). System auto-evaluates each item; officer confirms/overrides with reason.
- **Approved By:** MRD Officer / Medical Superintendent (Hospital Admin acts as MS today)
- **Verified By:** system verification engine + MRD Officer sign-off
- **Stored In:** `mrd_file_checklist` (one row per IPD admission), linked 1:1 to `mrd_records`
- **Lifecycle:** permanent — retained per MRD retention policy (India: adult 5 yr, MTP/medico-legal/minor longer)
- **NABH clause:** IMS/MOM (Management of Medical Records) — completeness, retention, retrieval of the medical record.

## 2. Purpose
- **Hospital use:** single quality gate confirming an IPD file is complete before it leaves the wards for the record room.
- **NABH requirement:** the organisation maintains a **complete and accurate** medical record for every patient (completeness audit).
- **Legal:** an incomplete file (missing consent, missing discharge summary) is indefensible in medico-legal claims/insurance queries.
- **Clinical:** guarantees the next admission/OPD visit can retrieve a full, trustworthy history.
- **Business rationale:** insurance/TPA reimbursement is denied on incomplete documentation; this catches gaps before billing closes the loop.

## 3. Trigger
`IPD status → DISCHARGED` (existing `IpdAdmission.status`) **→** admission appears in MRD **Pending Verification** queue (today it goes straight to `listPendingArchive()`) **→** MRD Officer opens the file **→** system auto-runs the checklist **→** [all mandatory items PASS] **→** file eligible for archival **→** `archiveAdmission()` (existing) assigns MRD number + rack **→** file physically stored.
**Gating state:** `checklist.status = VERIFIED` becomes a **precondition** of `archiveAdmission`. If any mandatory item FAILs → status `INCOMPLETE`, admission stays in a **Rework** queue and the responsible module is notified.

## 4. User Roles
| Actor | Capacity | HMS role |
|---|---|---|
| MRD Officer | verify / override / archive | **MISSING role → `MRD_OFFICER`** (gap). Interim: `HOSPITAL_ADMIN`. |
| Doctor | view own patients' checklist (read) | `DOCTOR` |
| Nurse | view (read) | `NURSE` |
| Medical Superintendent | audit / override-approval | `HOSPITAL_ADMIN` (no MS role today — gap) |
| Hospital Admin | full | `HOSPITAL_ADMIN` |
| Reception / Patient | none | — |

**Role gap:** `MRD_OFFICER` (distinct from admin; least-privilege on record room only). Add to README gap list.

## 5. Fields
Each checklist item is a **derived system state**, not a manual tick. `result ∈ {PASS, FAIL, N/A}` computed by the engine; `override_*` lets the officer force with a reason.

| Field | Type | Max | Mandatory | Editable rule | DB column | Validation | Searchable | Printable | Source |
|---|---|---|---|---|---|---|---|---|---|
| Patient (UHID) | FK | — | Y | read-only | `patient_id` | must belong to hospital | Y | Y | auto |
| IPD admission | FK | — | Y | read-only | `ipd_id` | status=DISCHARGED | Y | Y | auto |
| Discharge summary | enum result | — | Y | engine | `discharge_summary` | exists+signed | N | Y | auto |
| Case sheet / history | enum result | — | Y | engine | `case_sheet` | exists | N | Y | auto |
| Continuation sheet | enum result | — | N | engine | `continuation_sheet` | exists if LOS>1d | N | Y | auto |
| Consent forms (general) | enum result | — | Y | engine | `general_consent` | signed | N | Y | auto |
| Procedure/surgery consent | enum result | — | cond. | engine | `consent_forms` | if surgery→signed | N | Y | auto |
| Pre-op record | enum result | — | cond. | engine | `pre_op` | if surgery | N | Y | auto |
| Post-op record | enum result | — | cond. | engine | `post_op` | if surgery | N | Y | auto |
| TPR chart | enum result | — | Y | engine | `tpr_chart` | ≥1 entry | N | Y | auto |
| Intake/Output chart | enum result | — | cond. | engine | `io_chart` | if ICU/monitored | N | Y | auto |
| Drug/medication chart (MAR) | enum result | — | Y | engine | `drug_chart` | ≥1 entry | N | Y | auto |
| Nursing report/notes | enum result | — | Y | engine | `nurse_report` | ≥1 entry | N | Y | auto |
| Investigation reports | enum result | — | cond. | engine | `investigation_reports` | if lab/radiology ordered→all resulted | N | Y | auto |
| Blood transfusion consent | enum result | — | cond. | engine | `blood_consent` | if blood used → [Form 01](./01-blood-transfusion-consent.md) COMPLETED | N | Y | auto |
| Discharge/gate pass sheet | enum result | — | Y | engine | `discharge_sheet` | exists | N | Y | auto |
| Death summary | enum result | — | cond. | engine | `death_summary` | if outcome=DEATH | N | Y | auto |
| Remarks | text | 1000 | N | officer | `remarks` | — | N | Y | manual |
| Verified by | FK user | — | Y (on verify) | system | `verified_by` | = current user | Y | Y | auto |
| Verified date | datetime | — | Y (on verify) | system | `verified_date` | server time | Y | Y | auto |
| Status | enum | — | Y | system | `status` | PENDING/INCOMPLETE/VERIFIED/ARCHIVED | Y | Y | auto |

## 6. Business Rules
- **BR-1** `IF ipd.status != DISCHARGED THEN` checklist cannot be created/run.
- **BR-2** `IF any mandatory item.result = FAIL THEN status = INCOMPLETE AND archival is blocked.`
- **BR-3** `IF outcome = DEATH THEN death_summary is mandatory AND follow-up/discharge-med items become N/A.`
- **BR-4** `IF surgery performed (OT record exists) THEN pre_op, post_op, procedure consent are mandatory.`
- **BR-5** `IF any blood product transfused THEN Form 01 blood consent must be COMPLETED.`
- **BR-6** `IF lab or radiology orders exist THEN every ordered investigation must be resulted (no PENDING) before PASS.`
- **BR-7** `IF LOS > 1 day THEN continuation sheet + at least one nursing note per shift expected.`
- **BR-8** An officer may **override** a FAIL to PASS **only with a non-empty reason**; override is audited and flagged on print.
- **BR-9** `status = VERIFIED` is a **hard precondition** of `archiveAdmission()` (amend existing service).
- **BR-10** Once `ARCHIVED`, checklist is **immutable** (mirrors existing `validateAdmissionActive` lock). Re-open requires admin unlock + reason.
- **BR-11** All source documents referenced must belong to the **same `hospital_id` and same `ipd_id`** (no cross-encounter borrowing).
- **BR-12** Re-running the engine recomputes results but **never** deletes prior verification history (append-only snapshots).

## 7. Database Design
**New table `mrd_file_checklist`** (tenant-owned):

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| public_id | VARCHAR unique | external ref |
| hospital_id | BIGINT NOT NULL, INDEX | **tenant key — filter every query** |
| ipd_id | BIGINT NOT NULL, **UNIQUE** | 1:1 with admission (mirrors `mrd_records.ipd_admission_id`) |
| patient_id | BIGINT NOT NULL, INDEX | |
| discharge_summary … death_summary | VARCHAR(4) | each `PASS/FAIL/N_A` |
| remarks | TEXT | officer note |
| override_map | JSON/TEXT | `{item: {by, reason, at}}` for BR-8 overrides |
| status | VARCHAR(12) | PENDING/INCOMPLETE/VERIFIED/ARCHIVED |
| verified_by | BIGINT nullable | FK users |
| verified_date | TIMESTAMP nullable | |
| created_at/by, updated_at/by | audit cols | |
| is_deleted | BOOLEAN | soft-delete only |

- **FK:** `ipd_id → ipd_admissions.id`, `patient_id → patients.id`, `hospital_id → hospitals.id`.
- **Unique:** `(hospital_id, ipd_id)`.
- **Index:** `(hospital_id, status)` for queue reads.
- **Link to existing:** on archival, `mrd_records` row is created (existing flow); store `checklist_id` on `mrd_records` (**new nullable FK**) to bind gate → archive.
- **History:** append-only `mrd_checklist_snapshot` (checklist_id, computed_json, run_at, run_by) each time the engine runs — audit trail of completeness over time.
- **DMS dependency `[ASSUMPTION]`:** ideal design has each module publish completed docs into a central `document` registry (see [shared service](./shared/signature-and-document-service.md) §B/C). Until that exists, the engine reads **source tables directly** (`discharge_summaries`, `doctor_orders`, lab/radiology results, `blood_transfusion_consent`, nursing/vitals) — mapping in §15.

## 8. APIs
All under existing `@RequestMapping("/hospital/mrd")`. Every `{id}`/`{ipdId}` validates `hospital_id` ownership (audit SEC rule — existing `archiveAdmission` already does the tenant check; replicate it).

| Verb | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/hospital/mrd/pending` | ADMIN, MRD_OFFICER | **exists** — discharged, not-yet-archived queue |
| GET | `/hospital/mrd/checklist/{ipdId}` | ADMIN, MRD_OFFICER, DOCTOR, NURSE | run/return computed checklist for one admission |
| POST | `/hospital/mrd/checklist/{ipdId}/run` | ADMIN, MRD_OFFICER | recompute engine, persist snapshot |
| POST | `/hospital/mrd/checklist/{ipdId}/override` | ADMIN, MRD_OFFICER | body: `{item, reason}` → BR-8 |
| POST | `/hospital/mrd/checklist/{ipdId}/verify` | ADMIN, MRD_OFFICER | require all mandatory PASS → status=VERIFIED |
| POST | `/hospital/mrd/archive` | ADMIN, MRD_OFFICER | **exists** — now gated on status=VERIFIED (BR-9) |
| GET | `/hospital/mrd/archived` | ADMIN, MRD_OFFICER | **exists** |
| GET | `/hospital/mrd/checklist/{ipdId}/print` | ADMIN, MRD_OFFICER | PDF of the checklist (via shared service) |
| POST | `/hospital/mrd/checklist/{ipdId}/reopen` | ADMIN | unlock ARCHIVED w/ reason (BR-10) |

## 9. UI Design
- **Mental model:** an **airport pre-flight checklist / CI status page** — green ticks vs red blockers, "cannot depart until all green."
- **Desktop:** left = pending-files queue (patient, IPD no, discharge date, doctor, % complete badge); right = selected file's checklist as a grouped list (Clinical / Nursing / Consents / Investigations / Discharge). Each row: item name · PASS✓/FAIL✗/N/A · "view source" link · override button (officer only).
- Sticky action bar: **Verify & Archive** (disabled until all mandatory green), **Print Checklist**, **Notify responsible module** on each FAIL.
- Chunked to ≤7 groups (Miller). Autosave on override/remark. Inline reason modal on override.
- **Tablet/mobile:** single-column accordion by group; queue collapses to a dropdown. Read-only for DOCTOR/NURSE.

## 10. Workflow
```
(discharge) → PENDING
PENDING → [run engine / all mandatory PASS] → READY
PENDING → [run engine / any mandatory FAIL] → INCOMPLETE
INCOMPLETE → [responsible module fixes doc + re-run] → READY
INCOMPLETE → [officer override w/ reason] → READY
READY → [officer verify] → VERIFIED
VERIFIED → [archiveAdmission: assign MRD no + rack] → ARCHIVED   (immutable, existing lock)
ARCHIVED → [admin reopen w/ reason] → INCOMPLETE   (rare)
```
This is the gate that sits **between** the existing `DISCHARGED` state and the existing `ARCHIVED` state.

## 11. Validation
- `ipd_id`: exists, `status=DISCHARGED`, same hospital, not already ARCHIVED.
- Each item result ∈ `{PASS, FAIL, N_A}` only.
- `verify`: server re-runs engine; rejects if any **mandatory** item ≠ PASS (and not overridden) — never trust client-sent PASS.
- Override: `reason` non-empty, ≥10 chars; recorded with user+timestamp.
- `rack_location` (archive step): non-empty (existing rule).
- Death case: `death_summary` must resolve PASS; discharge-med items forced N/A.
- No future `verified_date`; `verified_by` = authenticated user.

## 12. Permissions
| Role | Create/Run | Read | Override | Verify | Archive | Reopen | Print |
|---|---|---|---|---|---|---|---|
| MRD_OFFICER *(gap→admin)* | Yes | Yes | Yes | Yes | Yes | No | Yes |
| HOSPITAL_ADMIN | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| DOCTOR | No | Limited (own patients) | No | No | No | No | Yes |
| NURSE | No | Limited | No | No | No | No | No |
| RECEPTIONIST / others | No | No | No | No | No | No | No |
| MRD (archived state) | — | read-only | No | No | — | — | Yes |

Must mirror §8 `@PreAuthorize`.

## 13. Print Rules
Via [shared Signature & Document service](./shared/signature-and-document-service.md) §C. Template `templates/mrd-file-checklist.html` (OpenPDF).
- A4, standard header (logo, hospital, form code **VH/NABH/MRD/01/2026**, page numbers).
- QR/barcode: UHID + IPD no + checklist public_id.
- Body: grouped item table with result column; overrides shown with reason + officer name.
- Footer signature block: **Verified by** (MRD Officer name + datetime), **Archived** (MRD no, rack, date).
- Copies: 1 file copy (front sheet of the physical file). Not given to patient.

## 14. Audit Logs
Every run/override/verify/archive/reopen → existing `AuditLogService.logAction(action, details, performedBy, hospitalId, entityType="MRD_CHECKLIST", …)`: who · when · old→new status · item-level override reason · IP · browser · hospital_id. Append-only; never deleted. Snapshot table (§7) is the completeness-over-time record.

## 15. Digital Improvements
Paper checklist → **live verification engine**. Each item maps to a real source query:
- `discharge_summary` → `discharge_summaries` by `ipd_admission_id` exists (+ signed once shared service lands).
- `drug_chart` / orders → `doctor_orders` (ACTIVE/COMPLETED) exist for admission.
- `investigation_reports` → all lab/radiology orders resulted (no PENDING).
- `blood_consent` → [Form 01](./01-blood-transfusion-consent.md) COMPLETED **iff** blood used.
- `nurse_report`, `tpr_chart`, `io_chart` → nursing/vitals tables have entries.
- **Fetch-not-type:** patient/IPD/doctor auto-filled from admission (as existing `MrdPendingDTO` already does).
- **Conditional engine:** surgery/ICU/death/blood flags auto-toggle which items are mandatory (BR-3–5) — no manual "does this apply?".
- One-click **notify responsible module** on FAIL (Nursing/Doctor/Lab) instead of phone calls.

## 16. Missing / Intelligent Features
- **Archive-Readiness Score** (% mandatory PASS) shown live on the IPD/discharge screen **before** discharge, so gaps are fixed while staff are still present — shift-left quality.
- Blocker list surfaced at **Plan Discharge** (tie into existing discharge gate) so a file is nearly complete by the time it reaches MRD.
- Duplicate/mismatch detection: doc belongs to wrong encounter/patient.
- Retention-clock automation (auto-flag files due for legal destruction).
- **[Future] Central DMS**: every module publishes completed, signed documents into one registry; MRD auto-assembles the digital file and the checklist becomes a pure read over document metadata (removes direct source-table coupling).

---

## Module & workflow placement
- **Owning module:** MRD → File Verification (extends existing MRD archive).
- **Creates:** `mrd_file_checklist`, checklist snapshots. **Updates:** `mrd_records` (adds `checklist_id`, gates archive). **Views:** discharge summary, doctor orders, lab/radiology results, consents, nursing/vitals. **Prints:** checklist front sheet. **Archives:** binds to existing `mrd_records`.
- **Feeds into:** MRD archival / retention · insurance/TPA closure. **Fed by:** Discharge · OT · Lab · Radiology · Pharmacy · Nursing · Consent (Form 01) · Billing.
- **New modules/roles this form implies:** `MRD_OFFICER` role (gap) · Medical Superintendent role (gap) · **Central Document Management System (DMS)** as the clean long-term source (gap) — add to README.
