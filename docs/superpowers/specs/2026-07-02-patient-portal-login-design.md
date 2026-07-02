# Patient Portal — Login & Record Access (Form 40, Phase 1) Design

## Status

Approved for planning. This is a deliberately scoped first slice of the full Form 40 blueprint
(`docs/hms-blueprint/40-patient-portal.md`). Online payment and teleconsultation are separate,
later specs — both depend on choosing an external vendor (payment gateway, video/WebRTC
provider) that hasn't been picked yet. Delegate/caregiver access (BR-1, `patient_consent`) is
also deferred to keep this pass to a single-patient-per-account identity.

## Goal

Let a patient log into a self-service portal using their mobile number and an OTP, and view
their own appointments, released lab/radiology reports, prescriptions, and billing status —
all read-only, all scoped strictly to their own record.

## Scope

**In scope:**
- Mobile + OTP registration/login, issuing a JWT with a new `PATIENT` role
- Read-only dashboard: appointments, released reports (with PDF download), prescriptions, billing summary
- Server-side enforcement of the "released reports only" gate (BR-2)
- Per-access audit logging (BR-4)
- Tenant + patient isolation on every endpoint (BR-7)

**Explicitly out of scope for this spec** (separate future specs):
- Online bill payment (needs a payment gateway vendor decision)
- Teleconsultation / video (needs a WebRTC/video vendor decision)
- Delegate/caregiver access (`patient_consent`, BR-1)
- Self-service appointment booking (view-only in this pass)
- `portal_notification` push/SMS/WhatsApp event triggers (BR-6) — deferred, not core to login/viewing
- `dashboard_widget`-style customization — not applicable here (this is the patient dashboard admin dashboard MIS work already done under Form 32 is separate)

## Architecture & Identity

Patient identity is **not** modeled as a row in the existing `users` table. That table holds
password-based staff accounts; patients authenticate via OTP only and have no password. Mixing
the two would force synthetic email addresses and password hashes onto a fundamentally
different identity type.

Instead:
- **New table `patient_portal_user`**: one row per `(hospital_id, patient_id)`, created lazily
  on first successful OTP request. Fields: `mobile`, `email` (nullable), `status`
  (ACTIVE/LOCKED/SUSPENDED), `last_login`, `created_at`.
- **New role constant `PATIENT`** in `UserRole.java`, recognized only under a new
  `/hospital/portal/**` request-matcher namespace in `SecurityConfig` — this mirrors the
  existing `/platform/**` (Super Admin) vs `/hospital/**` (tenant staff) split; a third
  top-level namespace for a third distinct identity class follows the established pattern
  rather than overloading `/hospital/**`'s existing `hasAnyRole(...)` staff-role list.
- **JWT issuance** reuses the existing `JwtUtil.generateToken(userId, email, role, hospitalId,
  modules)`. For a patient token: `userId` = the `patient_portal_user.id`, `email` = the
  patient's email if on file else a placeholder never used for lookup, `role` = `"PATIENT"`,
  `hospitalId` = the patient's hospital, `modules` = empty list. The token additionally carries
  a `patientId` custom claim (added alongside the existing claims) so downstream endpoints can
  scope every query without a database round-trip.

**Implementation verification item (first task of the plan):** confirm
`JwtAuthenticationFilter` builds its `UserAuthenticationDetails` purely from JWT claims and does
not re-query the `users` table by `userId`. If it does, add a narrow branch: when
`role == PATIENT`, skip the `users` lookup (or look up `PatientPortalUserRepository` instead).
This is a small, isolated change either way — it does not affect the overall design.

## OTP Flow & Data Model

No password step exists, so registration and login collapse into the same two-endpoint flow:

### `POST /hospital/portal/otp/request`
Request: `{ "mobile": "9876543210", "uhid": "UHID-1234" }` (`uhid` optional, only required on
ambiguous match)

1. Query `Patient` where `hospital_id = :currentHospital AND phone = :mobile`.
   - **0 matches** → 404 `"No patient record found with this number. Please visit reception to register."`
   - **1 match** → proceed.
   - **2+ matches** → if `uhid` is absent, 409 `"Multiple records found. Please also provide your patient ID."`; if present, filter matches by `custom_id = uhid`; 0 results after filtering → 404; else proceed with the filtered match.
2. Find-or-create `patient_portal_user` for that `(hospital_id, patient_id)`.
3. If `patient_portal_user.status == LOCKED` and lock cooldown hasn't elapsed → 423 with retry-after context.
4. Generate a 6-digit numeric OTP. Store a hash (HMAC-SHA256, not reversible) in `portal_otp`
   with `purpose = LOGIN`, `expires_at = now + 5 minutes`, `attempt_count = 0`.
5. Dispatch via `SmsGateway.send(mobile, otp)`.
6. Response: `{ "message": "OTP sent" }` — never echoes the OTP or confirms which specific patient matched beyond what's needed.

### `POST /hospital/portal/otp/verify`
Request: `{ "mobile": "9876543210", "otp": "123456" }`

1. Load the most recent non-consumed `portal_otp` row for this `(hospital_id, mobile)`.
   - Missing, expired, or already consumed → 400 `"Invalid or expired OTP."`
2. Compare hash. Mismatch → increment `attempt_count`; if it reaches 5, set
   `patient_portal_user.status = LOCKED` with a 15-minute cooldown timestamp; return 400.
3. Match → mark OTP `consumed_at = now`, reset `attempt_count`, update
   `patient_portal_user.last_login`, mint the JWT as described above.
4. Response: `{ "token": "...", "patient": { "id", "name", "uhid" } }`

**New tables:**

```sql
CREATE TABLE patient_portal_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    mobile VARCHAR(15) NOT NULL,
    email VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,       -- ACTIVE / LOCKED / SUSPENDED
    lock_until DATETIME NULL,
    last_login DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portal_user_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_portal_user_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT uq_portal_user_hospital_patient UNIQUE (hospital_id, patient_id)
);

CREATE TABLE portal_otp (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    mobile VARCHAR(15) NOT NULL,
    otp_hash VARCHAR(100) NOT NULL,
    purpose VARCHAR(20) NOT NULL,      -- LOGIN (only value used in this pass)
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portal_otp_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);
CREATE INDEX idx_portal_otp_mobile ON portal_otp(hospital_id, mobile, consumed_at);
```

## SMS Gateway (MSG91)

- `SmsGateway` interface: `void send(String mobile, String otp)`.
- `Msg91SmsGateway` implementation calls MSG91's OTP API using `MSG91_AUTH_KEY`,
  `MSG91_SENDER_ID`, `MSG91_OTP_TEMPLATE_ID` from environment config (same pattern as
  `JWT_SECRET`, `SPRING_REDIS_HOST`, etc. in `.env`).
- **Dev/unconfigured fallback:** if `MSG91_AUTH_KEY` is blank, `Msg91SmsGateway` logs the OTP to
  the server console with a `[DEV-OTP]` prefix instead of calling the external API, and still
  returns success. This only activates when the credential is absent — production behavior once
  configured is a real SMS, matching what was chosen.
- Unit tests use a mock `SmsGateway`, never the real MSG91 client.

## Dashboard & Record APIs

All endpoints below live under `/hospital/portal/**`, require `hasRole('PATIENT')`, and resolve
`patientId`/`hospitalId` **only** from the JWT claims set at login — never from a path/query
parameter — closing the obvious IDOR path (one patient requesting another's data).

| Endpoint | Purpose |
|---|---|
| `GET /hospital/portal/dashboard` | Summary counts: upcoming appointment count, released report count, prescription count, outstanding balance |
| `GET /hospital/portal/appointments` | This patient's appointments (upcoming + past), read-only |
| `GET /hospital/portal/reports` | Lab + radiology orders **where `status = 'RELEASED'` only** (BR-2, enforced server-side — a `VERIFIED`-but-unreleased order is invisible, not just hidden client-side) |
| `GET /hospital/portal/reports/{id}/download` | Streams the existing PDF report (reuses `PdfService`), watermarked "PATIENT COPY - DOWNLOADED VIA PORTAL"; re-validates ownership + `RELEASED` status before streaming |
| `GET /hospital/portal/prescriptions` | Read-only prescription list (BR-3 — no write endpoint exists on this entire surface) |
| `GET /hospital/portal/billing` | Bill amounts + payment status, no payment action (payment gateway is a separate future spec) |

## Security & Audit

- **BR-4 (audit trail):** every GET above writes an `AuditLog` entry
  (`entity_type = "PATIENT_PORTAL"`, action e.g. `PORTAL_RECORD_ACCESSED`, details including the
  accessed document/report ID). This is new behavior relative to internal staff endpoints, which
  don't audit every read — required explicitly by the blueprint's NABH/privacy rule.
- **Rate limiting:** `otp/request` and `otp/verify` go through the existing `RateLimitFilter`
  (SMS costs money per send; both endpoints are unauthenticated by nature and are the most
  abuse-prone surface in this spec).
- **OTP hashing:** OTPs are stored as HMAC-SHA256 hashes, never plaintext, single-use, 5-minute
  expiry.
- **Lockout:** 5 failed verify attempts locks the `patient_portal_user` for a 15-minute
  cooldown — a soft, timestamp-based lock (no admin unlock endpoint needed for this pass).
- **Tenant isolation (BR-7):** every table and query includes `hospital_id`.

## Frontend

- New unauthenticated routes (added to `App.jsx`'s existing `<Routes>`, following the
  `/login/hospital`, `/login/clinic` precedent): `/portal/login` — a two-step mobile-entry →
  OTP-entry form.
- New `PATIENT`-role-guarded route `/portal/dashboard`, reusing the existing `ProtectedRoute`
  component (add `PATIENT` to its role-matching logic) — bento-grid cards for appointments,
  reports (with download buttons), prescriptions, and billing status.
- New `frontend/src/services/patientPortalService.js` following the existing `apiService.js`
  axios-wrapper pattern.
- Token storage reuses the existing `sessionStorage` `token`/`user` keys via the existing
  `apiService` interceptor — no new storage mechanism needed (tab-isolated sessionStorage
  already prevents collision with a staff session in another tab).

## Testing

- `PatientPortalServiceTest` (Mockito/AssertJ, same pattern as every service test this session):
  OTP request finds the correct patient by mobile; ambiguous multi-match requires `uhid`; OTP
  verify success path mints token info and marks the OTP consumed; expired/wrong OTP rejected;
  lockout after 5 failed attempts; dashboard/reports/appointments/billing endpoints scope
  strictly to the JWT's `patientId`; a `VERIFIED`-but-not-`RELEASED` lab/radiology order is
  excluded from `/reports`.
- Green gates: `mvn -q test` (backend), `npm run build` (frontend), as done for every prior
  increment this session.

## Open Dependency

Real OTP delivery requires `MSG91_AUTH_KEY` / sender ID / DLT template ID, which you'll need to
obtain from MSG91 and add to `.env`. The feature is fully buildable and testable without those
credentials (dev fallback logs the OTP instead of sending it); only live SMS delivery is
blocked until you supply them.
