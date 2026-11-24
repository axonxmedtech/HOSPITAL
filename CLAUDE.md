# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Non-negotiable Engineering Principles

These apply to **every** request in this repository and override default behavior.

- **Reuse before creating.** Inspect the existing Patient, IPD Admission, Ward, Bed, Doctor, Nurse, Pharmacy, Lab, Billing, Inventory, Medical Record, Notification, WebSocket and Audit components before adding new entities (this is the standing rule for ICU work in particular).
- **One coherent patient workspace.** The ICU Patient Chart is the central clinical workspace. Do not create 15 disconnected pages for routine ICU documentation.
- **Tenant/facility isolation.** Every ICU-owned resource is scoped by the authenticated facility/tenant. Foreign resources must never be discoverable through IDs.
- **Clinical history is append/audit oriented.** Vitals, MAR, infusion changes, ventilator settings, notes, procedures and handovers preserve history and identity — never silently overwrite prior values.
- **Clinical decision support is limited.** Document values and configurable alerts only. No treatment recommendations, ventilator recommendations, or AI-generated medical diagnosis without separate clinical validation.
- **Incremental delivery.** Each phase is audited, designed, implemented, tested, diff-reviewed, locally committed and reported before the next phase begins.
- **Scope discipline.** If a feature requires modifying security, tenancy, OT concurrency, IPD sequencing architecture, global error handling, CI/CD, multi-clinic architecture, pharmacy redesign or billing redesign — **stop that dependency and escalate**. Do not change it independently.

## Standard Development Format

```
AUDIT → DESIGN → IMPLEMENT → FOCUSED TEST → REGRESSION TEST → FULL BUILD → DIFF REVIEW → LOCAL COMMIT → CHECKPOINT REPORT
```

- Never jump directly from requirement to code.
- Each checkpoint starts with a codebase inspection and a **written proposal** covering: reused components, new components, risks, and the exact files to be touched.
- Only approved scope is implemented. Unrelated refactoring is reported separately, never done silently.
- Checkpoint code is committed **locally only** after all required tests and diff checks pass.
- **No checkpoint is ever pushed automatically.**

## Commands

### Backend (Spring Boot / Maven)

```bash
cd backend
mvn spring-boot:run          # Start dev server (port 8080)
mvn clean package -DskipTests # Build JAR
mvn test                     # Run tests
```

### Frontend (React / Vite)

```bash
cd frontend
npm install                  # Install dependencies
npm run dev                  # Start dev server (port 5173)
npm run build                # Production build (runs tsc then vite build)
```

### Database

```bash
# Initial setup
CREATE DATABASE hospital_management;
# Then run: setup/setup-super-admin.sql
# Optional test data: setup/test-data-doctors.sql
```

## Architecture

This is a **multi-tenant SaaS Hospital Management System** — a monorepo with a Spring Boot backend and a React frontend.

### Tenant Model

Two tiers:

- **Platform level** — Super Admin manages hospitals (onboarding, billing)
- **Hospital level** — Hospital Admin manages their own staff and patients

All hospital-scoped entities carry a `hospital_id` foreign key. The backend extracts `hospitalId` from the JWT claims via `SecurityHelper.getCurrentHospitalId()` and enforces tenant isolation in every service method.

### Backend (`backend/src/main/java/com/hms/`)

| Package                 | Purpose                                                                                   |
| ----------------------- | ----------------------------------------------------------------------------------------- |
| `entity/`               | JPA entities (Patient, Doctor, Appointment, Opd, Ipd, Prescription, Medicine, Billing, …) |
| `repository/`           | Spring Data JPA repositories                                                              |
| `service/hospital/`     | Business logic for hospital-level operations                                              |
| `service/platform/`     | Platform (Super Admin) operations                                                         |
| `controller/hospital/`  | REST handlers under `/hospital/**`                                                        |
| `controller/platform/`  | REST handlers under `/platform/**`                                                        |
| `controller/publicapi/` | Unauthenticated endpoints (`/api/public/**`)                                              |
| `security/`             | JWT filter (`JwtAuthenticationFilter`), `JwtUtil`, `SecurityHelper`                       |
| `config/`               | Spring Security config, Redis config, WebSocket config, CORS                              |
| `dto/`                  | Request/response DTOs                                                                     |

**API URL namespaces:**

- `/platform/**` — Super Admin only
- `/hospital/**` — All hospital roles (ADMIN, DOCTOR, RECEPTIONIST, PHARMACIST, NURSE, NURSE_INCHARGE, OT_INCHARGE)
- `/clinic/**`, `/pharmacy/**` — clinic and pharmacy tenants (ADMIN, DOCTOR, RECEPTIONIST, PHARMACIST). Shared endpoints are aliased across tenants by listing every path on one handler, e.g. `@GetMapping({"/hospital/settings/fees", "/clinic/settings/fees", "/pharmacy/settings/fees"})`.
- `/api/public/health` — unauthenticated health check
- `/ws/**` — WebSocket

**Roles:** `SUPER_ADMIN`, `HOSPITAL_ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PHARMACIST`, `NURSE` (staff nurse), `NURSE_INCHARGE`, `OT_INCHARGE`. Roles are plain strings on `User.role`; `JwtAuthenticationFilter` maps them to `ROLE_<role>` authorities (so a new role works in `@PreAuthorize` once added to the relevant `hasAnyRole(...)` lists in `SecurityConfig` for `/hospital/**` and `/ws/**`). There is no central role enum/whitelist.

Special flag: `isSingleDoctor` on a `HOSPITAL_ADMIN` user lets that admin also act as the sole doctor (single-doctor clinic mode).

### Plan modules & migrations (used constantly)

- **Module gating:** a hospital's plan enables modules (`OPD`, `IPD`, `PHARMACY`, `BILLING`, `NURSING`, `OT`, …). Backend gates controllers/methods with `@RequireModule("NURSING")` (enforced by `ModuleAccessAspect`); the frontend reads `user.modules` and conditionally renders tabs. The module list lives in `frontend/src/components/PlansTab.jsx`.
- **Migrations:** `config/DatabaseMigrationRunner` runs idempotent `ensureXxx()` methods on `ApplicationReadyEvent` (each checks `information_schema` then `CREATE TABLE` / `ALTER TABLE`, wrapped in try/catch). Add a new `ensureXxxTable()`/`addColumnIfMissing(...)`, call it from `runMigrations()`, **and** mirror the DDL in `setup/schema-full.sql`. Hibernate `ddl-auto=update` also applies entity changes, so migrations are a safety net + the canonical schema record.
- **Errors:** `GlobalExceptionHandler` maps `IllegalArgumentException`→400, `UnauthorizedException`→401, `AccessDeniedException`→403. `ApiResponse.error(msg)` puts the message in the **`error`** field — the frontend reads `err.response.data.error`.
- **Audit:** `AuditLogService.logAction(action, details, performedByEmail, hospitalId, entityType, entityId, reason)` — best-effort, wrap calls in try/catch.

### OT (Operation Theatre) module — `OT`-gated

`Surgery` (own lifecycle REQUESTED→SCHEDULED→IN_PROGRESS→COMPLETED, linked to an IPD admission) + a generic `surgery_forms` JSON store backing 15 NABH forms. Doctor requests a surgery from the IPD case; reception schedules (any doctor or free-text operator + optional anaesthetist + a single-bed OT ward) and runs Start/Complete. OT wards are identified by name containing "OT". Frontend: `pages/hospital/ot/` (`SurgeryFormFrame` is the shared fill→save→print shell; `surgeryFormsRegistry.jsx` lists the forms). Nurse fills the forms from the surgery-patient "Consent Forms" tab.

### Nursing Management module — `NURSING`-gated (built in phases A–D)

- **Hierarchy:** `HOSPITAL_ADMIN` → `NURSE_INCHARGE` (manages assigned wards) → `NURSE` (staff). An incharge is a `NurseProfile` with `is_incharge=true` + a `User` of role `NURSE_INCHARGE`; staff nurses may have no login. `Ward.incharge_nurse_id` links a ward to its incharge (one incharge, many wards). Ward-scope RBAC lives in `security/NurseInchargeGuard` (admin bypasses; incharge limited to their `myWardIds()`); staff-nurse "only your patients" is `security/NurseAccessGuard`; `security/NurseWriteAccess` picks the right guard per role for nursing writes.
- **Separate Nurse Login setting** (`hospital_settings.separate_nurse_login`, default OFF): OFF → staff nurses have no login and the incharge records care, choosing a "Performed By Nurse" (`security/PerformingNurseResolver` + `performed_by_nurse_id` on nursing records); ON → the logged-in nurse is recorded.
- **Patient assignment** (`PatientAssignmentService`, replaces least-loaded auto-assign): reception admits to a ward (which must have an incharge and an Available bed); OFF → incharge handles; ON + exactly one ward staff nurse → auto-assign; ON + many → incharge assigns.
- **Time Slots / scheduling:** `shift_templates` + `appointment_slots` (admin CRUD, `TimeSlotController`); `nurse_shift_schedules` snapshot the template's times so editing a template only rewrites **future** schedules. "On shift now" is derived from today's schedule (no manual toggle).
- **Beds:** four states `available/occupied/cleaning/maintenance` (`entity/BedStatus`); **all** bed status writes go through `BedStatusService` (audited into `bed_status_audits`). Vacating a bed (discharge / transfer / OT complete) marks it `cleaning`; the incharge marks it cleaned → available.
- **Attendance:** `nurse_attendance` (one row per nurse/date, upserted, shift window snapshotted).
- Frontend: `pages/hospital/NurseDashboard.jsx` (staff), `NurseInchargeDashboard.jsx` + `pages/hospital/nurse-incharge/*` (incharge), `pages/hospital/nurse/*` (shared nurse views). Remaining phases (E incharge dashboard, F substitution, G calendar) are speced in `docs/superpowers/`.
- **Due Now worklist** (`GET /hospital/nurse/worklist`, `NurseWorklistService`, frontend `pages/hospital/nurse/DueNowView`). It is the staff nurse's landing tab and the in-charge's second tab.
  - It is read-only. Every dose, vitals round and sample due today across the nurse's My Patients scope. One-tap actions go through the existing endpoints (`/hospital/nurse/medication`, `/vitals`, `/ipd/investigations/{id}/sample`), so their rules still apply.
  - Doses come from the M-A-N frequency at 08:00 / 14:00 / 20:00 (`SLOT_TIMES`). A dose is DUE within ±60 min and OVERDUE after. A record carrying the dose's `scheduledTime` closes it; a record without one closes the earliest open dose.
  - Dose times before the order was written are not owed. "As Per Required" is listed as AS_NEEDED, and an unrecognised frequency as UNSCHEDULED.
  - Vitals are due 6 h after the last reading. A sample is due while its lab order is `ORDERED`.
  - With a shared nurse login, the in-charge picks "Recording for" once at the top.

### Files & Access (per-hospital form availability + edit permission)

Hospital-scoped config controlling which clinical forms are active and who may edit each (`DOCTOR` / `NURSE` / `BOTH`). **Not module-gated** — shared by hospital and clinic tenants (excluded only for pharmacy).

- **Canonical list:** `service/hospital/FormRegistry` (20 forms: 4 nursing records — `VITALS`, `NOTES` (the Re-Assessment Sheet), `INITIAL_ASSESSMENT`, `VULNERABILITY_ASSESSMENT`, `SUGAR_CHART` — + 15 OT/NABH forms whose keys match `frontend/.../ot/surgeryFormsRegistry.jsx` `type` values). `NURSE_INCHARGE` normalizes to `NURSE`; `HOSPITAL_ADMIN` sees everything editable.
- **Storage & defaults:** `hospital_form_access` stores only _overrides_; a form with **no row is enabled + BOTH** (lazy default, no seeding). `FormAccessService`: `list()`, `update()`, `effectiveForRole(role)` → `HIDDEN` / `READ_ONLY` / `EDITABLE`, and **`assertCanEdit(formKey)`** (throws `AccessDeniedException`) called in the write paths of the vitals/assessment/vulnerability/sugar/notes services — **server-side enforcement, not just UI**. API: `/hospital/form-access` (admin) + `/effective` (any staff role reads their verdict map).
- **Enforcement rule:** Off ⇒ tab hidden for everyone; On ⇒ the access role(s) edit, the other role is read-only (the entry form is _hidden_, records stay visible/printable). Applied in `NursePatientDetail` (nurse) and `IpdDetails` (doctor's IPD case, which mirrors the nurse sub-tabs). Medication and the OPD Medication tab reuse `MedicationPanel` with a `readOnly` prop; nurse form panels take `readOnly` and hide their nurse-only "Performed By Nurse" UI for non-nurses. UI card: `pages/hospital/FilesAndAccessCard.jsx` in Settings.

### OPD vitals settings (per-hospital + custom vitals)

Each hospital picks which vitals are captured at OPD entry and can define its own. **Not module-gated** (hospital + clinic).

- **Built-ins:** `service/hospital/VitalRegistry` (`BP`, `TEMPERATURE`, `PULSE`, `HEIGHT`, `WEIGHT`, `SPO2`), each mapped to a typed `opd` column. **Custom** vitals live in `hospital_vitals` (`is_custom=true`) and their values in the **`opd.custom_vitals` JSON** column (built-ins keep their typed columns).
- **Defaults/rules:** lazy — a built-in with no `hospital_vitals` row is enabled; toggling writes an override. Built-ins can be turned off but **never deleted**; only customs are deletable (deleting keeps historical values). Custom vitals have **no validation**; built-ins keep theirs (BP `120/80` format, numerics ranged). `VitalSettingsService`: `list`/`enabledVitals`/`toggle`/`addCustom`/`deleteCustom` + `enabledBuiltInKeys`/`enabledCustomKeys` (used by `OpdService` to drop disabled vitals server-side) + `enabledVitalsFor(hospitalId)` (used by `ClinicalPdfService` to build the case-paper VITAL SIGNS table dynamically). API `/hospital/vitals`. Frontend: `VitalsSettingsCard.jsx` (Settings) + `hooks/useEnabledVitals.js` (drives the 3 OPD entry forms and the doctor's `ConsultationModal` vitals strip).

### Patient records & history (nothing is lost)

- **Never hard-delete patient, clinical or billing rows** — cancel, archive, stop, undo or mark inactive. `security/NoHardDeleteOfPatientDataTest` fails the build on a new delete call/method/raw `DELETE FROM` on those repositories/tables (a short reviewed allowlist covers editing an open bill's draft lines). The platform's permanent facility delete (`PlatformHospitalService.deleteHospitalPermanently`) requires the facility blocked first + its exact name typed + a reason, and writes a mandatory audit row before purging.
- **Patient File** — `GET /hospital/patients/{publicId}/file` (`PatientFileService`): every OPD visit, IPD stay, ICU stay, surgery, bill and document. Admin/doctor everything; receptionist no clinical detail; nurse / nurse in-charge only a patient currently in their care (`NurseAccessGuard`); bills follow `security/BillingAccessPolicy` (shared with `BillingController`). Admin can open a deleted patient's file. Frontend `pages/hospital/PatientFile.jsx` at `/patients/:publicId/file`; patient names link there via `components/records/PatientNameLink`.
- **History lists** (server-filtered, paged, `view` + `from`/`to` ISO dates + `search`): `GET /hospital/ipd?view=CURRENT|DISCHARGED|CANCELLED|ALL` (doctor = own admissions), `GET /hospital/icu/history?view=CURRENT|CLOSED|ALL` (not for staff nurses), `GET /hospital/surgeries/history?view=ACTIVE|DONE|CANCELLED|ALL`. Frontend: `components/RecordsFilterBar` (view tabs, date quick-picks, search) + `components/records/{Ipd,Icu,Ot}HistoryView`.
- **Access log (who opened which record)** — annotate every controller method that opens, prints or downloads a patient's record with `@PatientRecordAccess(record = Kind.X, action = …, label = "…")` (`com.hms.audit`); `PatientAccessAspect` writes a `patient_access_logs` row after a 2xx answer, tracing the method's **first `@PathVariable`** to the patient (hospital-scoped lookups in `PatientAccessLogRepository`). Same user + record + action within 10 min = one row. Append-only, retained on facility purge, kept apart from `audit_logs`. Admin reads it at `GET /hospital/patients/{publicId}/access-log` → Patient File "Access log" tab (`components/records/PatientAccessLog`).
- **Backups** — nightly `db-backup.yml` → `scripts/db/backup.sh` (dump stays on the VPS) → `offsite-copy.sh` (age-encrypted copy to S3, `daily/` + `monthly/`; `OFFSITE=required` fails the run if it can't). Setup and restore: `docs/database/OFFSITE_BACKUPS.md`; disk encryption: `docs/security/DATA_AT_REST.md`.

### Pharmacy money and stock rules

- **Prices include GST (MRP style).** A batch's selling price (≤ MRP) is what the customer pays; GST is extracted from it. `service/pharmacy/PharmacyBillCalculator` is the only place a counter bill is priced — `PharmacySaleService.createSale` ignores every amount the browser sends and refuses expired / blocked / disposed batches, empty bills, discounts above the bill and unknown payment methods. `utils/pharmacyMoney.js` mirrors it for display only.
- **Invoices:** consecutive serials per facility (or branch) per financial year from `PharmacyInvoiceNumberService` (`PH/26-27/00001`, `B3/…`, return notes `SR/…`); create the series row _before_ taking the lock (MySQL gap lock — see `PharmacyInvoiceNumberIT`). GSTIN / drug licence live on `hospitals` (Settings → Invoice details) and print on the tax invoice.
- **Refunds** are recorded in `pharmacy_sale_returns` (amount + GST in it) and are based on what was paid per unit, never the shelf price. Reports read refunds from there; revenue and profit exclude GST.
- **Purchases** are totalled on the server (rate excl. GST + GST − discount). Drafts can be edited / discarded; a posted purchase can only be cancelled (with a reason) while all its stock is still on the shelf. A re-used batch number must keep its expiry and cannot revive a blocked / disposed batch. Supplier payments go to `purchase_payments`.
- **Branches:** every pharmacy query takes `securityHelper.getCurrentBranchId()` (null = whole pharmacy); new queries must too. Money / stock changes outside a sale go through `PharmacyAuditTrail` (shown in the pharmacy Audit Logs).
- A `PHARMACY` facility cannot reach doctor / receptionist / patient endpoints (`FacilityAccessAspect.NOT_FOR_PHARMACY`).

### IPD rules (admission → discharge)

- **Numbers & cancel:** IPD numbers run per hospital (`uk_ipd_admission_hospital_ipd_number`; the admission locks the hospital row). A mistaken admission is **cancelled** (`POST /hospital/ipd/{id}/cancel`, status `CANCELLED`), never discharged. Cancelling frees the bed, voids the bill and reopens the doctor's request. It is refused once a stock dose was given, a surgery is booked, or a discharge is planned.
- **Bed charges:** all bed, nursing and visit days come from `IpdChargePeriods` (`CALENDAR_DAY` or `HOURS_24` + grace).
  - The rule is snapshotted on `ipd_admission` at admission and set in `ipd_billing_settings`.
  - Line descriptions name the day, which makes them idempotent and lets packages find them.
  - `BillingSchedulerService` is the only place that adds day 2+ charges.
- **Packages:** `ipd_packages` cover bed, nursing and visits for N days. Applying one later credits the covered lines with a negative adjustment line, and removal is reversed by opposite lines; nothing is deleted.
- **Money:**
  - **Advances** are `billing_payments` of kind `ADVANCE` and may exceed the balance. The unused part is refunded at `confirm-discharge` (`refundMode`).
  - **Insurance:** `ipd_insurance_claims` follow a state machine in `IpdInsuranceService`. The approved amount is the insurer's share at discharge, and the bill stays open for it. The settlement is posted as kind/mode `INSURANCE`.
- **Discharge summary:**
  - Structured (`DischargeSummaryRules`: diagnosis, course and condition required) and editable while `DISCHARGE_PLANNED`.
  - PDF via `ClinicalPdfService.generateDischargeSummaryPdf`.
  - A NORMAL discharge books the follow-up appointment (best effort, next free 15-minute slot).
- **Care orders:** `IpdCareAccess` decides who may read and write.
  - **Rounds:** one timeline (`/rounds`) that nurses can read.
  - **Investigations:** stored on `lab_orders` with `ipd_admission_id` (ORDERED → SAMPLE_COLLECTED → COMPLETED/CANCELLED). A result may be recorded after discharge.
  - **Diet:** `diet_orders` (history kept), plus a diet sheet PDF.
- **Bed reservations:** `bed_reservations` hold a free bed until `reserved_until`.
  - `BedReservationService.claimFor` is called by admit and change-bed. Only the reserved patient may enter, matched by `patientId`, or by `reservationId` for a hold made by name.
  - Expired holds are ignored.
- **Desk role:** reception, the admin, or a doctor in Solo/Both mode (`FrontDeskAccess`).
- **Desk shortcuts (fewer screens):**
  - The current-patients list is `components/ipd/CurrentAdmissionsTable`, shared by reception and the admin. Its row actions are checkout, admission form, advance/package, print bill and change bed. "Ready to go" shows `DISCHARGE_PLANNED`.
  - Cases open in-app via `useOpenIpdCase`, and the list's sub-tab and filter live in the URL (`ipdView`, `ipdFilter`), so Back returns to the same list. Never use `window.location.href` for a case.
  - `DischargeCheckoutModal` is the only discharge UI. It pays every outstanding bill (insurer share taken off the IPD bill), confirms, then prints `/{id}/discharge-pack/pdf` (summary + bill, merged by `PdfService.mergePdfs`) and offers "Mark bed cleaned".
  - After a discharge the case page stays open.
  - The admission form opens straight after Admit. "Save & Print" prints the form and the consent as one job (`combinePrintDocuments`).
- **Doctor's round mode** (`/ipd/round/:id`, `pages/hospital/IpdRoundMode`; `first` = the first patient in ward/bed order):
  - One screen per patient: latest vitals, running medicines (Stop), `InvestigationsPanel`, `DietCard`, the round note and any number of new medicines (`components/ipd/MedicineDraftRow`, prescription presets add several at once), then "Save & next".
  - It saves through the existing `addIpdFollowup` and one `addIpdPrescription` per order. There is no batch endpoint: a refused order stays on screen to fix.
  - Entry points: "Start ward round" and a per-row "Round" on the doctor's IPD list, plus "Round mode" on the case.
- **ICU = IPD in a critical-care bed:**
  - A stay opens and closes automatically when the admission's bed moves into or out of an `icu_wards` ward (`IcuStayService.onWardSettled`). There is no separate ICU admission or discharge.
  - The IPD admitted list (`/hospital/ipd/admissions`) includes ICU patients, flagged `inIcu` + `icuUnitLabel`.
  - The frontend has one **ICU** tab per role (`pages/hospital/icu/IcuWorkspace`) with Beds (`IcuBedBoard`: open case / Mark cleaned / Admit here), Patients (the shared `CurrentAdmissionsTable`, ICU rows via `toAdmissionRow`), Housekeeping (`IcuHousekeeping`: tasks tied to a real `bedId`) and History.
  - The old `icu-dashboard` / `icu-beds` tab ids redirect via `LEGACY_ICU_TABS`. "ICU Wards & Beds" stays as admin setup under Rooms.
  - An ICU patient leaves the hospital through the same `DischargeCheckoutModal`, after the doctor plans it. "Move to ICU" / "Move out of ICU" are bed moves (`changeBed`), offered to the desk roles only.

### Design docs

Specs and phase plans live under `docs/superpowers/specs/` and `docs/superpowers/plans/` (dated markdown). Consult the latest `nursing-mgmt-phase*` docs before extending the nurse module.

### Frontend (`frontend/src/`)

| Directory         | Purpose                                                                                                          |
| ----------------- | ---------------------------------------------------------------------------------------------------------------- |
| `pages/hospital/` | Per-role dashboards: `HospitalAdminDashboard`, `DoctorDashboard`, `ReceptionistDashboard`, `PharmacistDashboard` |
| `pages/platform/` | Super Admin UI                                                                                                   |
| `components/`     | Shared UI: `DataTable`, `ActionMenu`, `StatusBadge`, `ConfirmationModal`, `Sidebar`, `Navbar`, `PageHeader`, …   |
| `services/`       | Axios wrappers — `apiService.js` (interceptors), `hospitalService.js`, `authService.js`                          |
| `context/`        | `ToastContext` (global toast notifications)                                                                      |
| `hooks/`          | `useWebSocket`, `useModule`                                                                                      |

**Routing:** React Router 7. `ProtectedRoute` guards all non-login pages. After login the user is redirected to their role's dashboard.

**Auth flow:** JWT stored in `sessionStorage` (tab-isolated). Axios request interceptor injects `Authorization: Bearer <token>`. The 401 response interceptor clears the token and redirects to `/login`.

**Table components pattern:** Large dashboard files contain co-located table components at the bottom (`PatientsTable`, `DoctorsTable`, `AppointmentsTable`, etc.) using `@tanstack/react-table` with a `createColumnHelper`. Actions use the shared `ActionMenu` component (three-dot dropdown). The `isAdmin` prop controls whether the Actions column is rendered.

### Database schema source of truth

`setup/schema-full.sql` is the canonical schema. Entity classes in `entity/` mirror it. When adding columns, update both the SQL (provide the ALTER query) and the JPA entity.

### PDF generation

`PdfService` is a thin facade that delegates to `service/pdf/`: `ClinicalPdfService` (prescription / case paper), `BillingPdfService` (receipts), and `ReportPdfService` (OPD reports), with shared drawing in `PdfLayoutHelper`. PDFs are built programmatically with **OpenPDF** (`PdfPTable`, `document.add(...)`) — there are no HTML/Thymeleaf templates. The case paper's VITAL SIGNS table is built from the hospital's enabled vitals (see OPD vitals settings).

### WebSocket

Real-time dashboard updates. Config in `com.hms.config.WebSocketConfig`. Secured with JWT. Frontend hook: `useWebSocket`.

## Environment

**Backend** reads from `.env` (loaded via Spring's property source):

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `FRONTEND_URL` (CORS origin)
- `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`

**Frontend** reads from `.env`:

- `VITE_API_BASE_URL` (defaults to `http://localhost:8080`)
- `VITE_CLOUDINARY_CLOUD_NAME`, `VITE_CLOUDINARY_UPLOAD_PRESET` (logo uploads)
