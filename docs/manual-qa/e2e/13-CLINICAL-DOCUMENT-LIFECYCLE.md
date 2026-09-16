# 13 — CLINICAL DOCUMENT LIFECYCLE

**Cases:** `TC-E2E-098` … `TC-E2E-101` · Tenants HOSPITAL_A, CLINIC_A

Documents are the product's legal output. This journey covers **generated PDFs** (prescription,
case paper, receipt, reports — OpenPDF, built programmatically, **no HTML templates**) and
**uploaded patient documents**.

> The backend document-storage tests are a **known-failing macOS baseline** (`LocalVpsClinicalDocumentStorageTest`,
> `PatientDocumentApiTest`, `PatientDocumentJourneyTest`). That is an automated-test environment
> issue, **not** a licence to skip these manual cases — upload/download must be verified by hand.

### TC-E2E-098 — ⭐ Every generated PDF is correct, complete and correctly attributed

`HOSPITAL_A + CLINIC_A · DOC + REC + PHA · Documents · Critical`
**Steps:** `TC-HD-017` (prescription) · `TC-HD-018` (case paper) · `TC-HB-008` (receipt) · `TC-HAC-012` (reports) · `TC-PS-015` (pharmacy invoice).
**E2E assertion:** generate all five for the **same** patient and check each for: the **correct hospital name and logo**, the **correct patient identity** (name, `custom_id`, age/gender), the **correct clinician**, the right date, every line item present, and totals matching the screen. Then download the **same** documents from CLINIC_A and confirm they carry the **clinic's** branding — a leaked logo or header from another tenant is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-099 — The case paper's VITAL SIGNS table follows the hospital's vitals settings

`HOSPITAL_A · ADM → REC → DOC · Documents · High`
**Steps:** `TC-HAS-010` · `TC-HAS-011` · `TC-HAS-012` · `TC-HAS-036` (vitals settings) · `TC-HD-018`.
**E2E assertion:** the case paper's VITAL SIGNS table is **built from the enabled vitals**, so: disable a built-in (e.g. `SPO2`) → it disappears from the OPD entry forms **and from the PDF**; add a **custom** vital, record a value, and confirm it appears in both. Built-ins keep their validation (BP in `120/80` form, numerics ranged); **custom vitals have no validation** — confirm that rather than reporting it. Built-ins can be disabled but **never deleted**; deleting a custom keeps historical values on existing records, so **re-open an older case paper and confirm its previously recorded value is still printed**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-100 — Upload → list → download → delete, by hand

`HOSPITAL_A · REC + DOC + NUR · Documents · Critical`
**Steps:** `TC-HD-021` · `TC-HN-019` · `TC-CDR-016`.
**E2E assertion:** upload a PDF and an image to a patient; both appear in the list with the correct name, size, type, uploader and time; **download and open each — the bytes must be the file you uploaded**, not a placeholder or a different patient's file. Then test the guards: an oversized file, a disallowed type, an empty file and a filename containing `../` or unusual characters must each be refused **without storing anything**. Delete one document and confirm it disappears from the list and can no longer be downloaded by its direct URL.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-101 — ⭐ No document crosses a tenant, a role scope, or an expired session

`HOSPITAL_A + B + CLINIC_A · ADM + NUR · Isolation · Critical`
**Steps:** `TC-ISO-049` … `TC-ISO-052` · `TC-PERM-025` · `TC-PERM-006` · `TC-AUTH-018`.
**E2E assertion:** copy the **exact download URL** of a HOSPITAL_A patient document and a generated PDF, then request each:

1. as HOSPITAL_B's admin → must be refused;
2. as CLINIC_A → must be refused;
3. with **no token at all** → must be refused;
4. with an **expired/revoked** token → must be refused;
5. as a HOSPITAL_A **staff nurse whose scope excludes that patient** → must be refused.
   Also confirm the **two `DoctorController` prescription-PDF endpoints with no role check** behave as recorded in `status/IMPLEMENTATION-STATUS.md`: any authenticated **same-tenant** role may fetch them (log against the existing entry), but a **cross-tenant** fetch must still fail. A cross-tenant success is **Critical**.
   **Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
