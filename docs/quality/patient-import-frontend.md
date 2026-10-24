# Patient import frontend and explicit column exclusions

Worktree: `/private/tmp/hms-import-v2`
Branch: `feature/legacy-patient-import-v2`
Base HEAD: `22fc0ad5a6c273e84757798021a522b13a27bbcb`

No commit, push, merge or deployment was performed. Existing test-isolation fixes remain intact.

## HTTP contract

The existing Hospital Admin-only endpoints remain:

- `POST /hospital/patients/import/preview`
- `POST /hospital/patients/import/commit`
- `GET /hospital/patients/import/{batchPublicId}`

All three retain `/clinic/patients/import` aliases. There is no pharmacy alias. Tenant and actor come from the authenticated server context. The frontend uses the existing Axios client, JWT/session interceptor and clinic namespace rewrite.

Preview and commit accept multipart parts:

| Part              | Value                                                                |
| ----------------- | -------------------------------------------------------------------- |
| `file`            | Original CSV/XLSX file; at most 50 MiB                               |
| `mapping`         | JSON object of source header to writable field key                   |
| `sheetName`       | Optional selected XLSX worksheet                                     |
| `excludedColumns` | **New, optional** JSON array of source headers to explicitly exclude |

Example: `mapping={"Patient Name":"name","Mobile":"phone"}`, `excludedColumns=["Old Notes","Unused Export Field"]`.

Writable fields remain `name`, `legacyId`, `gender`, `phone`, `email`, `dateOfBirth`, `address`, and `medicalHistory`. Name mapping remains required. This does not imply a name alone is sufficient to create a patient: backend row validation remains authoritative.

Exclusions use the existing header normalization (strip, collapse whitespace, case-insensitive). Blank, non-string, duplicate, unknown, oversized, or mapped-and-excluded entries are rejected with 400. Exclusions are validated against the selected sheet's actual headers before creating a commit batch. The list is bounded to 100 columns and its JSON to 64 KiB.

Absent `excludedColumns` or `[]` takes the original engine path. Unmapped columns are **not** implicitly excluded; legacy clients still preserve them as metadata. The frontend requires each column to have an explicit field choice or **Do not import**, and sends exclusions separately from mappings.

A shared projection removes excluded source headers and values before evaluation and review-row persistence. It preserves physical row numbers and parser problems. `PatientImporter` and the writer remain unchanged. Excluded values cannot produce metadata-only updates. Existing metadata and patient fields are not cleared, including on real updates of other mapped fields. Parser resource limits still apply to the original upload; exclusion does not bypass validation of oversized or malformed data.

Fingerprinting still hashes the original uploaded bytes. Changing mapping, exclusions or worksheet does not bypass same-file RUNNING/COMPLETED/PARTIAL blocking. No schema, migrations, transaction boundaries, identity matching, duplicate-phone rules, concurrency protections, authentication rules or upload limits changed.

Response envelopes remain `{success, data, ...}`:

- Preview: `sheetName`, `headers`, `sheetNames`, `counts`, bounded `samples`, `samplesTruncated`, `previousImport`. Samples expose row number, state, reason code, column, sanitized message and masked phone.
- Commit: `batchPublicId`, `status`, `counts`, `committedAt`. Commit is synchronous.
- Status: `publicId`, `status`, filename/sheet/actor/timestamps, `counts`, fixed failure reason. The UI displays status/counts without exposing internal IDs.
- Counts: `total`, `created`, `updated`, `skipped`, `needsReview`, `failed`.
- Row outcomes: CREATED, UPDATED, SKIPPED, NEEDS_REVIEW, FAILED.
- Batch states: RUNNING, COMPLETED, PARTIAL, FAILED, UNDONE.

## Frontend

Route: `/hospital/patients/import`, reached from **Patients → Import Patients** in the Hospital Admin dashboard. Clinic users use this same UI route and the shared API client rewrites requests to `/clinic`. Pharmacy and non-admin access are denied at both page/service boundaries; server authorization remains authoritative.

The workflow provides CSV/XLSX browsing/drop, file name/type/size/removal, worksheet selection, editable conservative mapping suggestions, explicit exclusions, required/duplicate mapping checks, zero-write preview, real counts, bounded masked issue samples, a confirmation dialog, synchronous commit and final batch status. Successful commits put only the public batch identifier in the URL so a page refresh can reload its server-side status. A missing session follows the existing login route.

CSV/XLSX headers are read in a short-lived worker using pinned Papa Parse 5.7.0 and read-excel-file 9.3.10. Only headers/sheet names leave the worker. The original File is sent unchanged. Workers are terminated on completion, replacement, unmount, or a 60-second read timeout. XLSX parsing still requires transient workbook memory; very large/complex workbooks need staging performance validation. The page does not transform dates or implement patient matching.

Mapping/file/sheet changes invalidate previews. Unselected columns never become exclusions implicitly. Known previous imports block confirmation. Files with no proposed creates/updates explain why confirmation is unavailable. Mutation buttons and an immediate submission lock prevent duplicate calls. POST failures are never automatically retried. If a commit response is lost, the page warns that some writes may have completed, offers status when an error supplies a public batch ID, and requires a fresh preview before another confirmation. Arbitrary error response bodies are not rendered or logged. The existing client handles 401 session clearing.

No patient files/rows, preview counts or fake history are saved to browser storage. Unsubmitted file choices are lost on navigation/refresh. During commit, closing/reloading the page prompts the browser's leave warning; leaving a page does not cancel server-side work.

## Remaining API limitations and rollout

- There is no list/history endpoint; no persistent Import History screen was invented.
- There is no row-result retrieval/export or review-resolution endpoint. Preview issue samples are labeled as preview data; final results use authoritative batch counts. No shared-phone acknowledgement or client-side resolution is offered.
- A network-lost commit without a public batch ID must be recovered by previewing the same original file to discover its live batch.
- Exclusion is not erasure of existing records or metadata.
- The backend extension must be available before this frontend is enabled: older backend versions reject the new multipart part. Existing clients remain supported by the new backend.
- No staging or production mutation tests were executed. A successful local build is not a production-readiness claim.

## Verification commands

Commands ran in this worktree (frontend commands under `frontend/`). Maven used:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Java: OpenJDK **17.0.20.1**, Homebrew, build `17.0.20.1+0`.

```sh
mvn -q -f backend/pom.xml -DskipTests test-compile
mvn -q -f backend/pom.xml -Dtest=ImportColumnExclusionsTest,ImportMappingValidatorTest,ImportEngineTest,PatientImporterTest,ImportedPatientWriterTest,ImportControllerBoundaryTest,PatientImportApiTest test
mvn -q -f backend/pom.xml -Dtest=ImportEngineTest,ClinicPharmacyIsolationTest,ImportEngineIT '-Dhms.it.mysql.url=jdbc:mysql://127.0.0.1:53860/pr32_s2184_it?connectionTimeZone=Asia/Kolkata&forceConnectionTimeZoneToSession=true' -Dhms.it.mysql.username=root -Dhms.it.mysql.password="${TEST_DB_PASSWORD}" -Duser.timezone=UTC test
mvn -q -f backend/pom.xml -Dtest=PatientImportApiTest test
mvn -q -f backend/pom.xml -Dtest=UploadBoundaryTest test
npm install --ignore-scripts --save-exact papaparse@5.7.0 read-excel-file@9.3.10
npm test -- src/pages/hospital/PatientImportPage.test.jsx src/pages/hospital/patient-import/importFields.test.js src/services/patientImportService.test.js
npm test -- src/services/patientImportTransport.test.js
npm test
npm run typecheck
npm run build
npm run lint # repository root
node node_modules/eslint/bin/eslint.js frontend/src/pages/hospital/PatientImportPage.jsx frontend/src/pages/hospital/PatientImportPage.test.jsx frontend/src/pages/hospital/patient-import frontend/src/services/patientImportService.js frontend/src/services/patientImportService.test.js frontend/src/services/patientImportTransport.test.js
npm audit --json # frontend
node /tmp/patient-import-browser-smoke.cjs
node /tmp/patient-import-browser-flow.cjs
git diff --check
git status --short
```

The MySQL URL/password above belong only to the disposable local test database, never staging or production. Test launchers needed sandbox escalation for local sockets/JVM attachment. Root lint/format commands reused the already installed shared checkout tooling through a temporary `node_modules` symlink; that task-created symlink was removed after checks. Frontend dependencies are installed in this worktree. Both task-owned local Vite servers were stopped after browser verification. One automated approval review rejected an insertion script as potentially removing tests; an explicit additive patch resolved that concern. No tests were removed or weakened.

Scoped backend results: **130 distinct tests passed, 0 failures/errors/skips**:

| Class                                  | Tests |
| -------------------------------------- | ----: |
| ImportColumnExclusionsTest             |     4 |
| ImportMappingValidatorTest             |     5 |
| ImportEngineTest                       |    17 |
| PatientImporterTest                    |    38 |
| ImportedPatientWriterTest              |     9 |
| ImportControllerBoundaryTest           |     3 |
| PatientImportApiTest                   |    15 |
| UploadBoundaryTest                     |    24 |
| ClinicPharmacyIsolationTest            |     4 |
| ImportEngineIT (real disposable MySQL) |    11 |

The API regressions cover absent/single/multiple exclusions, metadata preservation and metadata-only skip decisions, actual-field preservation, blank/duplicate/unknown/conflicting exclusions, name requirement, identical preview/commit counts, both aliases with real clinic JWTs, duplicate-phone review, tenant-scoped status and admin-only access. Existing boundary tests prove no pharmacy alias; XLSX engine assertions prove removed values cannot enter stored review rows, and the original SHA is unchanged. All original tests and three isolation fixes remain intact. The complete backend Surefire suite and coverage gate subsequently passed; see the full-suite follow-up below.

Final frontend results: **556 tests passed in 81 files**, zero failures/skips; this includes **44 new import tests**. Typecheck and production build passed. Lint on all new import modules/tests passed with zero warnings/errors. Whole-repository lint reports 6 errors and 277 warnings, all outside the new import files; error locations are listed below. `git diff --check` passed.

Local Chromium smoke tests passed for real CSV and XLSX workers. A second browser test verified the full flow against intercepted synthetic API responses: original multipart file, explicit exclusions, clinic namespace/JWT, preview issue rendering, confirmation, exactly one commit request, status after refresh, no patient data in browser storage, and no overflow at 390px width. Both reported zero browser page errors. These HTTP responses were mocked; real server semantics were tested separately in the backend API/engine suites. Screenshots are `/tmp/patient-import-browser.png` and `/tmp/patient-import-results.png`.

Repository-wide lint has six pre-existing errors in untouched files: `FollowUpPanel.jsx` (107, 108, 242), `FollowUpPanel.test.jsx` (37, 102), and `DoctorDashboard.jsx` (1727). Their contents match HEAD. They were not changed as part of import work. Existing build output also warns about dashboard chunks over 500 KiB. Dependency audit reports three moderate development-tool findings in the existing Vitest dependency chain; none are in the newly added reader dependencies. No audit-fix or unrelated dependency upgrades were applied.

## Staging QA checklist (still required)

Use synthetic data in an approved staging tenant; get approval before commits that write patients.

- [ ] A. Small valid CSV: correct headers/counts, original file received, expected create after explicit confirmation.
- [ ] B. Small valid XLSX: worksheet selection, cached/formatted headers, same preview/commit semantics.
- [ ] C. Unsupported extensions and corrupt files fail clearly without writes.
- [ ] D. Missing name, duplicate targets and unchosen columns block preview. Single/multiple **Do not import** choices reach `excludedColumns`; blank/duplicate/unknown/conflicting exclusions receive 400.
- [ ] E. Invalid DOB appears as a failed row with the backend reason.
- [ ] F. Missing/malformed phone appears with the backend reason and masked values.
- [ ] G. Existing MRN/lineage update changes only intended fields. Excluding old metadata creates no metadata-only update and never clears existing metadata/fields.
- [ ] H. Shared/duplicate phone stays NEEDS_REVIEW; no client acknowledgement or automatic patient merge.
- [ ] I. Mixed valid/review/failed rows retain successful writes and show final PARTIAL counts; preview sample truncation is clear.
- [ ] J. Receptionist/doctor/pharmacy cannot import; hospital/clinic admins can. Cross-tenant batch lookup reveals nothing.
- [ ] K. Above 50 MiB is refused locally; test near-limit CSV and representative XLSX performance, reverse-proxy upload limits and 411/413 handling without changing global limits.
- [ ] L. Compare patient/link/batch/result tables before/after preview to confirm zero writes.
- [ ] M. Explicit confirmation creates/updates expected records. Double clicks produce one commit. Same original file remains blocked after COMPLETED/PARTIAL even if exclusions change.
- [ ] N. Refresh a known batch URL and verify status. Refresh before commit loses the file; navigate away during a commit and recover via original-file preview/status without automatic retry. Test 401/403/409/5xx/network interruptions.
- [ ] O. Verify no patient file/rows or raw error payloads in console, browser storage or telemetry. Excluded values must be absent from metadata and stored review rows; phone samples remain masked.

## Exact files changed

- `backend/src/main/java/com/hms/controller/hospital/PatientImportController.java`
- `backend/src/main/java/com/hms/service/import_/ImportColumnExclusions.java`
- `backend/src/main/java/com/hms/service/import_/ImportCommitRequest.java`
- `backend/src/main/java/com/hms/service/import_/ImportEngine.java`
- `backend/src/test/java/com/hms/api/PatientImportApiTest.java`
- `backend/src/test/java/com/hms/api/UploadBoundaryTest.java`
- `backend/src/test/java/com/hms/service/import_/ImportColumnExclusionsTest.java`
- `backend/src/test/java/com/hms/service/import_/ImportEngineTest.java`
- `docs/quality/patient-import-frontend.md`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/src/App.jsx`
- `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`
- `frontend/src/pages/hospital/PatientImportPage.jsx`
- `frontend/src/pages/hospital/PatientImportPage.test.jsx`
- `frontend/src/pages/hospital/patient-import/ImportSummary.jsx`
- `frontend/src/pages/hospital/patient-import/importFields.js`
- `frontend/src/pages/hospital/patient-import/importFields.test.js`
- `frontend/src/pages/hospital/patient-import/importHeaders.worker.js`
- `frontend/src/pages/hospital/patient-import/readImportHeaders.js`
- `frontend/src/services/patientImportService.js`
- `frontend/src/services/patientImportService.test.js`
- `frontend/src/services/patientImportTransport.test.js`

## Full Surefire follow-up

A full Linux Java 17 run initially completed 1,597 tests with one failure: `UploadBoundaryTest.theClinicAliasGetsTheSameImportLimitAndGetStatusGetsNoUploadAllowance` expected HTTP 200 but received 500. Its mock still stubbed the old five-argument `ImportEngine.preview`; the controller now invokes the overload carrying exclusions. Mockito returned null and response conversion threw a NullPointerException. This was a deterministic stale test fixture, not an order/isolation dependency or production engine failure.

The fixture and invocation verification now use the new overload and explicitly require `eq(List.of())` for absent exclusions. All original upload-limit/security assertions remain intact. The class passes 24/24 individually under Java 17.0.20.1. No additional production change was made in this follow-up.

The full rerun uses an isolated snapshot of HEAD plus all eight changed/new backend files, with SHA-256 checks against the worktree, no `.env`, and the cached Maven repository copied into the disposable container. Runtime: Linux Eclipse Temurin **17.0.19+10**. Command inside `maven:3.9-eclipse-temurin-17`, working directory `/workspace/backend`, with CI's dummy JWT secret:

```sh
mvn -o -B --no-transfer-progress -Dmaven.repo.local=/tmp/m2 clean test jacoco:check@jacoco-check
```

This exercises the complete default Surefire phase and explicitly executes the repository's JaCoCo floor check. It is not the entire CI `verify` lifecycle: Failsafe and packaging are separate; the 11 MySQL `ImportEngineIT` tests passed in the prior scoped run.

Evidence directory: `/private/tmp/hms-import-exclusions-full-verification/`. The first failure log/XML and final-run log are preserved there. Final full-suite result: **1,597 tests across 178 classes, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. JaCoCo enforcement passed: line coverage **56.728%** (11,438 covered / 20,163 total; required 20%), branch coverage **44.475%** (4,138 covered / 9,304 total; required 10%). The full command completed in 4 minutes 30 seconds. All eight backend overlay SHA-256 hashes still matched the worktree after the run. `git diff --check` passed. HEAD and branch remain unchanged; no commit, push, merge or deployment occurred.

Final log: `/private/tmp/hms-import-exclusions-full-verification/surefire-linux-final.log`. JUnit reports and JaCoCo XML are under `source/backend/target/` in that evidence directory. The exact file list above now contains 23 files, including the narrowly updated `UploadBoundaryTest` fixture. Existing frontend verification remains 556 tests passed, typecheck/build passed, with the same pre-existing whole-repository lint failures.
