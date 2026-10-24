# Client-facing tasks — completion record

**Branch with everything:** `integration/client-tasks` (local, not pushed)
**Base:** `origin/staging` @ `f93671c` (Merge PR #27)
**Date:** 2026-09-12

This branch carries four task commits and one merge, on top of staging:

| Commit    | What                                                                  |
| --------- | --------------------------------------------------------------------- |
| `c2580b8` | Task 1 — Medication frequency quick presets                           |
| `ee3d6b6` | Task 2 — Injection food-timing UI rule                                |
| `0029b4d` | Task 4 — Prescription language selector (Before/After Food, EN/MR/HI) |
| `aafccb1` | Settings change — `BOTH` reception mode                               |
| `9d1821c` | Merge of the Task 1 branch into the integration branch                |

Task 3 is **blocked** and has no commit (see §3).

Every commit was made with the repository's pre-commit hook active (secret scan, `eslint --fix`, `prettier --write`). Where the hook reformatted pre-existing lines in a touched file, that is noted in the commit message rather than hidden.

Verification on the merged branch, after the final merge:

| Check                                                                                                                                                                                                                                                    | Result                  |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| Frontend unit suite (`vitest`)                                                                                                                                                                                                                           | **498 / 498**, 73 files |
| Frontend type-check (`tsc --noEmit`)                                                                                                                                                                                                                     | clean                   |
| Frontend production build (`tsc && vite build`)                                                                                                                                                                                                          | clean                   |
| Backend compile                                                                                                                                                                                                                                          | clean                   |
| Backend targeted suites: `PatientApiTest`, `CrossTenantIsolationTest`, `AuthBoundarySmokeTest`, `OpdTenancyTest`, `OpdIdempotencyTest`, `HospitalAuthServiceTest`, `NursingClinicalFieldsTest`, `ClinicalPdfServiceLanguageTest`, `FoodTimingLabelsTest` | **79 / 79**             |

The full backend suite additionally contains 33 tests in `LocalVpsClinicalDocumentStorageTest`, `PatientDocumentApiTest` and `PatientDocumentJourneyTest` that fail on Windows only (POSIX permissions and symlink privilege). They are unrelated to this work and pass in CI on Linux.

---

## Method

Each task followed the same lifecycle before any code was written:

1. **Audit** — locate the existing implementation, contracts, validation, tests and workflow. Never assume something is missing until the repository has been checked.
2. **Fit & impact** — classify as frontend-only / existing contract / backend dependency / schema dependency / Sagar decision.
3. **Plan** — exact files, exact tests, scope check.
4. **Approval gate** — stop; nothing implemented until the plan was approved.
5. **Implement → test → verify diff → commit → checkpoint report.**

Two tasks stopped at the gate on their first pass because the audit showed the existing contract could not carry the request as first stated (Tasks 3 and 4). That is the intended outcome of the method, not a failure of it.

---

## 1. Medication Dose / Frequency Quick Presets

**Commit:** `c2580b8` · **Branch of origin:** `feat/medication-quick-presets`

### Requirement

Clickable presets `1-0-1 / BD`, `1-1-1 / TDS`, `1-1-1-1 / QID`, `1-0-0 / OD`, `0-0-1 / HS` that populate the dose/frequency; manual entry must keep working.

### Audit finding

Frequency entry was already a structured component, `FrequencyInput.jsx`, with three dose boxes (Morning-Afternoon-Night) and an "As Per Required" checkbox, emitting a plain string (`"1-0-1"`) consumed by six call sites. Validation is the single frontend rule `isFrequencyValid` (`^\d-\d-\d$`, at least one non-zero dose). The backend stores frequency as a free `varchar(50)` with only `@NotBlank`; nothing anywhere parses the string into slots — it is printed and displayed verbatim.

### What was built

- A row of four one-click chips inside `FrequencyInput`, above the boxes: **`1-0-0 OD`, `1-0-1 BD`, `1-1-1 TDS`, `0-0-1 HS`**. Each shows the pattern _and_ the abbreviation.
- A preset emits the same string the boxes already produce — no new format, no new validation. `isFrequencyValid`, `parse` and `compose` are untouched.
- The chip matching the current value is marked selected (`aria-pressed`), whether clicked or typed by hand.
- Choosing a preset clears "As Per Required". Chips are `type="button"` and honour `disabled`.
- Because the presets live in the shared component, **all six frequency-entry surfaces** (OPD prescribing, in-clinic administered row, IPD medicine modal, catalogue default, both preset managers) got them with **no change to any parent file**.

### QID — deliberately not implemented

`1-1-1-1` needs a fourth dose slot. The frequency contract is three slots, and widening it would change what the third digit means (Night today, Evening in a four-slot value) for every record already written. That is a clinical-format decision. **Raised for Sagar; not settled in code.** A `1-1-1-1` chip is explicitly asserted absent in the tests.

### Files

| File                                                            | Change                                                                                   |
| --------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `frontend/src/components/FrequencyInput.jsx`                    | `FREQUENCY_PRESETS` export + chip row                                                    |
| `frontend/src/components/FrequencyInput.test.jsx`               | +11 tests                                                                                |
| `frontend/src/components/ConsultationModal.submission.test.jsx` | +1 integration test (prescribe via a preset click; payload carries `frequency: '1-0-1'`) |

### Scope

Frontend-only. No backend, API, DTO, schema or parent-component change.

---

## 2. Injection Food-Timing UI Rule

**Commit:** `ee3d6b6` · **Branch of origin:** `feat/injection-food-timing-rule`

### Requirement

Keep Before/After Food for applicable medicines; hide or disable it for injections; ensure stale food-timing data is never submitted when a medicine changes to an injection.

### Audit finding

Food timing is a structured field (`utils/foodTiming.js`, vocabulary `BEFORE_FOOD / AFTER_FOOD / WITH_FOOD / NOT_SPECIFIED`, mirroring `entity/FoodTiming.java`), entered in exactly one place — the IPD "Add Medicine" modal in `IpdDetails.jsx` — and displayed read-only on the nurse MAR. The backend validates the _value_ but has **no cross-field rule** tying it to type or route. The defect had two entry paths: changing the Type dropdown, and — more likely — picking an injection from the catalogue, which sets `type` without the doctor touching the dropdown.

### What was built

- `isFoodTimingApplicable(type, route)` in `utils/foodTiming.js`, beside the vocabulary it governs. **Not applicable** for types `INJECTION` / `IV_FLUID` and routes `IV` / `IM` / `SUBCUTANEOUS` — the server's full vocabulary, not just the subset the form offers. Case-insensitive; type and route judged independently (a catalogue injection sets type but leaves route on `ORAL`). Unknown/null stay applicable.
- The Food Timing select is **disabled, not hidden**, with the message _"Not applicable for injections."_ wired via `aria-describedby`.
- `foodTiming` is **cleared on every path** that can make an order parenteral: Type change, Route change, catalogue pick.
- **Authoritative payload guard**: `foodTiming` is decided at submit from the order itself, so no present or future path can put a meal time on an injection. The server already reads `null` as "not stated".

### Not touched (by decision)

`describeFoodTiming`, the nurse MAR, historical stored values (permissive display preserved), OPD `instructions` free text, the entry vocabulary.

### Files

| File                                                         | Change                                                  |
| ------------------------------------------------------------ | ------------------------------------------------------- |
| `frontend/src/utils/foodTiming.js`                           | the predicate                                           |
| `frontend/src/pages/hospital/IpdDetails.jsx`                 | disable + message, three clearing paths, payload guard  |
| `frontend/src/utils/foodTiming.test.js`                      | +5 tests                                                |
| `frontend/src/pages/hospital/IpdDetails.foodTiming.test.jsx` | **new**, 9 tests — first tests for this 2,200-line page |

### Scope

Frontend-only. Zero backend files changed. No schema or contract change.

---

## 3. Medicine Composition Display — BLOCKED

**Status: BLOCKED — requires product/backend/domain decision from Sagar.** No code, no commit.

### Requirement

When a medicine is selected, show its generic/composition (e.g. _"XYZ Tablet — Paracetamol 500 mg"_); handle missing composition cleanly.

### Why it is blocked — two independent blockers

There are **three separate medicine catalogues**:

| Entity                      | Used by                                   | Composition fields                            |
| --------------------------- | ----------------------------------------- | --------------------------------------------- |
| `MedicineList`              | **the doctor's prescribing autocomplete** | `id, name, type, hospitalType` — nothing else |
| `Medicine`                  | hospital clinical inventory               | none                                          |
| `MedicineMaster` (pharmacy) | pharmacy ERP                              | `genericName`, `strength`, `dosageForm`       |

**Blocker 1 — the field does not exist where the task points.** The prescribing surfaces are backed by `MedicineList`, which has no composition column. Adding one is a schema + API + migration change, explicitly out of scope.

**Blocker 2 — nothing can author the data, even where the column exists.** On the pharmacy side `genericName`/`strength` exist and reach the browser, but **no screen writes them**: the Medicine Master tab was removed (a code comment says so), `medicinesApi.create/update` has zero frontend callers, and the purchase flow that actually creates `MedicineMaster` rows (`PurchaseService:166`) never sets `genericName` or `strength`. A display-only change would render "N/A" for every medicine in every tenant. Fabricating or deriving composition data was ruled out.

### Decisions required from Sagar

1. Which medicine surface is intended for composition display (prescribing vs. pharmacy)?
2. Where should generic/composition data be authored and maintained?
3. Should the clinical catalogue use `genericName + strength` (matching the pharmacy model) or a single dedicated composition field?
4. Are schema/API changes approved if required?

---

## 4. Prescription Language Selector — English / Marathi / Hindi

**Commit:** `0029b4d` · Implemented on `feat/injection-food-timing-rule` after approval

### Requirement (final, after two rounds of clarification with Sagar)

A language selector on the **printed prescription** that governs **exactly two labels** — _Before Food_ and _After Food_ — with hardcoded translations:

|         | Before Food  | After Food  |
| ------- | ------------ | ----------- |
| English | Before Food  | After Food  |
| Marathi | जेवणापूर्वी  | जेवणानंतर   |
| Hindi   | भोजन से पहले | भोजन के बाद |

Nothing else is translated. Doctor notes, diagnosis, symptoms, instructions, medicine names, dosage and frequency print **exactly as entered**. No translation API, no i18n framework.

### Audit findings that shaped the design

- The application has **no i18n layer** at all.
- The primary print is a **server-generated combined PDF** (`/hospital/opd/{id}/documents/pdf`), built by `ClinicalPdfService` with base-14 `HELVETICA` at Cp1252 and **no embedded font** — it physically could not draw Devanagari. A Marathi or Hindi doctor note **was already printing as blanks/boxes** before this task.
- The OPD prescription **never carried a structured food timing**; the doctor typed "after food" into free text. The `prescriptions.food_timing` column already existed (filled only by the IPD path), so making it available needed a DTO field and one service line — **no migration**.

### What was built

**Backend**

- `ConsultationRequest.PrescriptionItem` accepts an optional `foodTiming`, saved via the existing `FoodTiming.normalize()` (`DoctorService`).
- The four prescription/case-paper PDF endpoints accept `?lang=en|mr|hi`, defaulting to `en`, threaded through `PdfService` into `ClinicalPdfService`. **Every existing signature is preserved as an overload**, so current callers are unchanged.
- `FoodTimingLabels` holds the six strings, byte-exact. `WITH_FOOD` and `NOT_SPECIFIED` are untouched; unknown/historical values print as stored.
- The label is rendered **inside the existing Instructions cell** (`जेवणानंतर · with water`), not a new column, so the table layout is unchanged.
- **Font:** Noto Sans Devanagari v2.007 is embedded via `IDENTITY_H`, used **only** for user-entered text and the food label; headers and fixed labels keep Helvetica, so the English document is unchanged. Loading is guarded with a Helvetica fallback.

**Frontend**

- Food-timing select in the OPD Add Medicine form, reusing `FOOD_TIMING_OPTIONS`.
- Language choice beside _Complete Consultation_, held as **temporary state** and passed through the existing `onSuccess` callback to the combined print as `?lang=`. **Nothing is persisted.**
- Client-side mirror of the label map in `utils/foodTiming.js`.

### Verification of the font, since it was added deliberately, not blindly

The file's own name table was read directly: **Noto Sans Devanagari, Version 2.007, © The Noto Project Authors, SIL Open Font License 1.1** — free to embed and redistribute. It carries `GSUB`/`GPOS` tables for conjunct shaping. `ClinicalPdfServiceLanguageTest` renders the exact supplied strings and a Devanagari note and extracts them back from the PDF.

**Open caveat:** the file is the _variable_ build (`fvar`/`gvar`/`avar` tables). OpenPDF 1.3.30 predates variable fonts; it normally embeds the default instance correctly, but **the rendered PDF should be inspected visually for malformed conjuncts** before this is considered final. If any appear, the static (non-variable) Noto Sans Devanagari build is a drop-in replacement.

### Accepted limitations

- Re-print buttons default to English for now.
- `WITH_FOOD` / `NOT_SPECIFIED` have no supplied translations and stay as they were.

### Files

| Area           | Files                                                                                                                                                                                                                    |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Backend        | `DoctorController`, `OpdController`, `ConsultationRequest`, `PdfService`, `DoctorService`, `ClinicalPdfService`, `PdfLayoutHelper`, **new** `FoodTimingLabels`, **new** `resources/fonts/NotoSansDevanagari-Regular.ttf` |
| Backend tests  | **new** `ClinicalPdfServiceLanguageTest` (6), **new** `FoodTimingLabelsTest` (8)                                                                                                                                         |
| Frontend       | `ConsultationModal.jsx`, `DoctorDashboard.jsx`, `utils/foodTiming.js`                                                                                                                                                    |
| Frontend tests | `ConsultationModal.submission.test.jsx`, `foodTiming.test.js`                                                                                                                                                            |

### Scope

Backend/API change **approved by Sagar** for this task specifically. No migration. No schema change. No new domain model. No translation service.

---

## Settings change — `BOTH` reception mode

**Commit:** `aafccb1`

### What it is

Reception Mode previously had two values. `HAS_RECEPTIONIST` gives the front desk to reception and hides it from the doctor; `SOLO` removes reception entirely, blocks receptionist logins and forces billing to the doctor. Neither fits a clinic that has a receptionist but also wants the doctor able to register a patient or open an OPD when the desk is busy.

**`BOTH`** is that mode: a receptionist exists and can log in, **and** the doctor has the front-desk actions too.

### Behaviour matrix

|                                                                                        | `HAS_RECEPTIONIST` | `SOLO`               | `BOTH`         |
| -------------------------------------------------------------------------------------- | ------------------ | -------------------- | -------------- |
| Receptionist can log in                                                                | yes                | **no**               | yes            |
| Doctor sees front-desk actions (Add Patient, OPD intake, Admit to IPD, inventory tabs) | no                 | yes                  | **yes**        |
| Doctor can call patient/appointment write APIs                                         | no (403)           | yes                  | **yes**        |
| Doctor sees full live OPD list                                                         | no (queued only)   | yes                  | **yes**        |
| Billing handler                                                                        | admin's choice     | **forced to DOCTOR** | admin's choice |
| Billing dropdown in Settings                                                           | enabled            | locked               | enabled        |

The `SOLO` billing invariant was kept on purpose: who answers the desk and who takes the money are separate questions, and `BOTH` keeps them separate.

### Where it changed

- **Validation:** `HospitalSettingDTO` `@Pattern` and the `HospitalAuthService` guard both accept `BOTH`.
- **Settings UI:** three-way select with a distinct badge ("Both (Shared)").
- **Doctor dashboard:** every `SOLO`-only branch now reads "SOLO or BOTH" — overview patient list, live OPD list, front-desk buttons, IPD admit action, inventory tabs. `IpdDetails` tab gating likewise. `IpdAdmissionService` likewise.
- **API boundary:** `PatientController` (`POST`, `PUT`) and `AppointmentController` (`POST`) had `DOCTOR` added to their role lists. On review this had opened them to **every** doctor in a `HAS_RECEPTIONIST` hospital — the dashboard hid the buttons, but the API did not. They now check the hospital's reception mode server-side (`requireFrontDeskAccess`), mirroring `BillingController`, and refuse a doctor whose hospital keeps the desk with reception.

### Defects found in review of the first draft, and fixed in the same commit

1. The live OPD filter had been missed — under `BOTH` the doctor saw a receptionist-style queue beside a button that said otherwise.
2. `DoctorOpdTable` (a separate component receiving `user` as a prop) referenced the dashboard's `canDoctorManageReception` const, which is not in its scope — a `ReferenceError` the moment a completed case's row menu opened with the IPD module on. A test now opens that menu in all three modes and was confirmed to fail with exactly that error when the bug is reintroduced.
3. The API permission widening described above.
4. Dead code: a two-state `toggleReceptionMode` with no callers, and an unused `isSolo` flag in `IpdDetails`.
5. `HospitalAdminDashboard.operationsMode.test.jsx` read the JSX file **as text** and asserted substrings — it would pass with the code commented out and fail on any reformat. Replaced with a test that renders the Settings tab, changes the select and asserts what reaches the service.

### No migration

`reception_mode` is `varchar(20)` with no CHECK constraint; `BOTH` fits.

### Files

| Area           | Files                                                                                                                                                |
| -------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Backend        | `AppointmentController`, `PatientController`, `HospitalSettingDTO`, `LoginResponse`, `HospitalSetting`, `HospitalAuthService`, `IpdAdmissionService` |
| Backend tests  | `PatientApiTest` (+3: doctor under each mode), `HospitalAuthServiceTest` (+2)                                                                        |
| Frontend       | `DoctorDashboard.jsx`, `HospitalAdminDashboard.jsx`, `IpdDetails.jsx`                                                                                |
| Frontend tests | **new** `DoctorDashboard.receptionMode.test.jsx` (6), **new** `HospitalAdminDashboard.operationsMode.test.jsx` (3)                                   |

---

## Open items for Sagar

| #   | Task   | Decision needed                                                                                              |
| --- | ------ | ------------------------------------------------------------------------------------------------------------ |
| 1   | Task 1 | Whether to widen the frequency contract to support `1-1-1-1 / QID` (a 4th dose slot).                        |
| 2   | Task 3 | The four questions in §3 — surface, authoring, field shape, schema approval. Task is blocked until answered. |
| 3   | Task 4 | Translations for `WITH_FOOD` and `NOT_SPECIFIED`, or confirmation they stay English.                         |
| 4   | Task 4 | Whether re-print buttons should also get the language selector.                                              |
| 5   | Task 4 | Visual sign-off on Devanagari conjunct rendering in the PDF (variable-font caveat above).                    |

---

## Operational notes encountered along the way

These are environment issues, not code changes, recorded so they are not rediscovered.

- **Stale columns in the local database.** The shared dev database carried `patients.manually_edited` (`NOT NULL`, no default) and `patients.preferred_language`, left behind by other branches while Hibernate `ddl-auto=update` was on. Hibernate creates `nullable=false` entity fields as `NOT NULL` with no default, and `DatabaseMigrationRunner` (which would have supplied the default) runs later and skips columns that already exist. This made every patient `INSERT` fail with `Field 'manually_edited' doesn't have a default value`. Repaired locally with `ALTER TABLE patients MODIFY manually_edited BIT(1) NOT NULL DEFAULT b'0'`. Any branch with a new non-null entity field can leave the same residue.
- **IDE-compiled classes poisoning `target/classes`.** A "Network Error" at login turned out to be the backend failing to start with `java.lang.Error: Unresolved compilation problems: PdfService cannot be resolved` in `BillingController`. That message is produced only by the Eclipse compiler: the IDE compiled a dependent class while `PdfService.java` was mid-edit, and Maven's incremental compile kept the poisoned class because its source had not changed. `mvn clean compile` is the fix.
- **Pre-existing flaky frontend tests** (`PlatformDashboardStats`, `PatientDocumentsPanel`, `WardModal`, `DoctorDashboard.queue`) were fixed in `bd2f814` on `create-patient-feature`; that commit is not on this branch. Two others (`ReceptionistDashboard.appointments`, `DoctorDashboard.appointments`) flake occasionally under full-suite load on `staging` itself and are unrelated to these tasks.
