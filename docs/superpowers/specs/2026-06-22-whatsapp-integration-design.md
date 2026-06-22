# WhatsApp Integration Design

## Goal

Add WhatsApp messaging to the HMS platform using the Meta WhatsApp Cloud API. Hospitals receive automated transactional notifications (appointment confirmations, reminders, billing and clinical documents) and a manual broadcast feature for sending messages to all registered patients.

## Architecture

Spring Application Events with `@Async` listeners. Core operations (billing, appointment creation) publish events and return immediately. WhatsApp sends happen in background threads — a WhatsApp failure never affects the patient-facing flow. Failed sends are retried twice (15-minute gap), then marked permanently failed and surfaced to hospital admin and Super Admin.

## Tech Stack

- **WhatsApp API**: Meta WhatsApp Cloud API v19.0 — free to integrate, pay per conversation (~₹0.13 utility, ~₹0.88 marketing in India)
- **PDF hosting**: Cloudinary Raw upload (already configured) — generates public URLs for document messages
- **Templates**: Pre-registered on Meta dashboard (4 templates, registered once by platform owner)
- **Scheduling**: Spring `@Scheduled` (existing pattern — `PlanExpiryScheduler` already uses it)

---

## 1. Plan Module Structure

New module strings added to the existing `plan_modules` `@ElementCollection`. No new DB table needed.

**Top-level WhatsApp mode (mutually exclusive):**

| Module String | Meaning |
|---|---|
| `WHATSAPP_PLATFORM` | Hospital uses AxonxMedtech's number; sub-options controlled in plan |
| `WHATSAPP_CUSTOM` | Hospital configures their own Meta credentials; they control all options |

**Sub-options (only meaningful when `WHATSAPP_PLATFORM` is present):**

| Module String | Trigger |
|---|---|
| `WA_APPOINTMENTS` | Appointment confirmation on creation + day-before reminder at 9 AM IST |
| `WA_BILLING` | Billing PDF when consultation is completed |
| `WA_CASE_PAPERS` | Case paper PDF when consultation is completed |
| `WA_PRESCRIPTION` | Prescription PDF when consultation is completed (only if prescription exists) |
| `WA_MEDICINE_LIST` | In-clinic dispensed medicines list when medicines are dispensed |

**`WHATSAPP_CUSTOM` behaviour:** The hospital admin controls which message types are active from their WhatsApp Settings panel. Per-type on/off toggles are stored as boolean columns on the `whatsapp_config` row (see Section 3).

**Mutual exclusivity rule:** A plan cannot have both `WHATSAPP_PLATFORM` and `WHATSAPP_CUSTOM`. The frontend enforces this with a three-way radio (None / Platform / Custom).

---

## 2. Backend Architecture

### 2a. New Service Classes

**`com.hms.service.whatsapp.WhatsAppService`**

Single entry point for all Meta API calls.

- Resolves credentials: checks `whatsapp_config` table for hospital-specific row → uses their `access_token` + `phone_number_id`. Falls back to platform env vars (`WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`).
- Calls `POST https://graph.facebook.com/{apiVersion}/{phoneNumberId}/messages`
- Writes every attempt to `whatsapp_message_log` with status `SENT` or `FAILED`
- On failure: sets `next_retry_at = now + 15 minutes`, `retry_count = 0`

**`com.hms.service.whatsapp.WhatsAppEventListener`**

`@Component` with three `@EventListener @Async` methods:

- `onAppointmentCreated(AppointmentCreatedEvent)` — checks `WA_APPOINTMENTS` (or `WHATSAPP_CUSTOM`) → sends `hms_appointment_confirmation` template
- `onConsultationCompleted(ConsultationCompletedEvent)` — checks `WA_BILLING` / `WA_CASE_PAPERS` / `WA_PRESCRIPTION` → for each enabled type: calls existing PDF service → uploads bytes to Cloudinary → sends `hms_document_ready` template with document URL
- `onMedicineDispensed(MedicineDispensedEvent)` — checks `WA_MEDICINE_LIST` (or `WHATSAPP_CUSTOM`) → generates medicine list PDF → uploads to Cloudinary → sends `hms_document_ready` template

**`com.hms.service.whatsapp.WhatsAppBroadcastService`**

- `sendBroadcast(Long hospitalId, String messageText, String imageUrl)` — fetches all patients for hospital, iterates and calls `WhatsAppService` for each `@Async`
- Returns immediately; progress tracked via `whatsapp_message_log`

### 2b. New Scheduler Classes

**`com.hms.scheduler.AppointmentReminderScheduler`**

```java
@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
```

- Queries all hospitals that have `WA_APPOINTMENTS` OR `WHATSAPP_CUSTOM` in their modules
- For each hospital: queries appointments where `appointmentDate = tomorrow` and `status = SCHEDULED`
- Sends `hms_appointment_reminder` template for each via `WhatsAppService`

**`com.hms.scheduler.WhatsAppRetryScheduler`**

```java
@Scheduled(fixedDelay = 300000) // every 5 minutes
```

- Queries `whatsapp_message_log` where `status = FAILED` and `next_retry_at <= now` and `retry_count < 2`
- Retries each via `WhatsAppService`
- On success: marks `SENT`
- On failure: if `retry_count < 2` → increment `retry_count`, set new `next_retry_at = now + 15min` / if `retry_count = 2` → mark `PERMANENTLY_FAILED`

### 2c. Spring Events (published from existing services — one line added each)

| Event Class | Published from | Payload |
|---|---|---|
| `AppointmentCreatedEvent` | `AppointmentService.createAppointment()` | `hospitalId`, `patientId`, `appointmentId` |
| `ConsultationCompletedEvent` | `BillingService.autoGenerateOpdBill()` | `hospitalId`, `patientId`, `billingId`, `opdId` |
| `MedicineDispensedEvent` | `MedicineService.addMedicinePurchase()` | `hospitalId`, `patientId`, `purchaseId` |

Each event is published with `applicationEventPublisher.publishEvent(new XxxEvent(...))` — no logic change to the publishing method, just one line at the end.

### 2d. Module Check Helper

`WhatsAppService` exposes a helper used by the event listener:

```java
boolean isEnabled(Long hospitalId, String subModule)
```

Returns true if hospital has `WHATSAPP_CUSTOM` OR (has `WHATSAPP_PLATFORM` AND has `subModule`).

---

## 3. Data Model

### New Table: `whatsapp_config`

Stores hospital-specific Meta credentials for `WHATSAPP_CUSTOM` mode only. Platform-mode hospitals have no row.

```sql
CREATE TABLE whatsapp_config (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  hospital_id          BIGINT       NOT NULL UNIQUE,
  access_token         VARCHAR(500) NOT NULL,      -- AES-256 encrypted
  phone_number_id      VARCHAR(100) NOT NULL,
  waba_id              VARCHAR(100) DEFAULT NULL,  -- optional, for reference
  is_active            TINYINT(1)   NOT NULL DEFAULT 1,
  -- Per-type toggles (only used when hospital is in WHATSAPP_CUSTOM mode)
  send_appointments    TINYINT(1)   NOT NULL DEFAULT 1,
  send_billing         TINYINT(1)   NOT NULL DEFAULT 1,
  send_case_papers     TINYINT(1)   NOT NULL DEFAULT 1,
  send_prescription    TINYINT(1)   NOT NULL DEFAULT 1,
  send_medicine_list   TINYINT(1)   NOT NULL DEFAULT 1,
  created_at           DATETIME(6)  NOT NULL,
  updated_at           DATETIME(6)  DEFAULT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);
```

### New Table: `whatsapp_message_log`

Every send attempt — automated or broadcast.

```sql
CREATE TABLE whatsapp_message_log (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  hospital_id      BIGINT       NOT NULL,
  patient_id       BIGINT       DEFAULT NULL,
  patient_phone    VARCHAR(20)  NOT NULL,
  message_type     VARCHAR(50)  NOT NULL,
  -- APPOINTMENT_CONFIRMATION | APPOINTMENT_REMINDER | BILLING |
  -- CASE_PAPER | PRESCRIPTION | MEDICINE_LIST | BROADCAST
  status           VARCHAR(25)  NOT NULL,
  -- SENT | FAILED | RETRYING | PERMANENTLY_FAILED
  error_message    VARCHAR(500) DEFAULT NULL,
  retry_count      INT          NOT NULL DEFAULT 0,
  next_retry_at    DATETIME(6)  DEFAULT NULL,
  sent_at          DATETIME(6)  DEFAULT NULL,
  created_at       DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_wml_hospital_status (hospital_id, status),
  KEY idx_wml_retry (status, next_retry_at)
);
```

### Environment Variables Added to `.env`

```
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_PHONE_NUMBER_ID=
WHATSAPP_API_VERSION=v19.0
WHATSAPP_ENCRYPTION_KEY=        # AES-256 key for encrypting custom tokens
```

---

## 4. WhatsApp Message Templates

Register once on Meta Business Manager → WhatsApp Manager → Message Templates.

### `hms_appointment_confirmation` *(Utility)*
> Hello {{1}}, your appointment at *{{2}}* is confirmed for *{{3}}* at *{{4}}*. Please arrive 10 minutes early.

`{{1}}` patient name · `{{2}}` hospital name · `{{3}}` date · `{{4}}` time

### `hms_appointment_reminder` *(Utility)*
> Hello {{1}}, reminder: your appointment at *{{2}}* is tomorrow, *{{3}}* at *{{4}}*. Please be on time.

`{{1}}` patient name · `{{2}}` hospital name · `{{3}}` date · `{{4}}` time

### `hms_document_ready` *(Utility)*
Header: DOCUMENT (Cloudinary URL set at send time)
> Hello {{1}}, your *{{2}}* from *{{3}}* is attached. Keep this for your records.

`{{1}}` patient name · `{{2}}` document type (e.g. "Billing Receipt" / "Prescription" / "Case Paper" / "Medicine List") · `{{3}}` hospital name

One template covers all document types — only `{{2}}` changes.

### `hms_broadcast` *(Marketing)*
Header: IMAGE (optional Cloudinary URL set at send time)
> {{1}}

`{{1}}` full message body typed by admin (max 1024 chars)

**Registration note:** Template names above are code constants in `WhatsAppTemplateConstants.java`. If Meta rejects a name, only the constant changes — no logic changes needed. Templates can be registered any time before go-live. During development, Meta's free test sandbox allows sending to 5 registered test numbers without any approved templates.

---

## 5. Frontend Changes

### 5a. PlansTab.jsx — WhatsApp module section

New section below existing module checkboxes. Three-way radio toggle (None / Platform / Custom). Sub-checkboxes (5 items) appear only when Platform is selected, indented beneath it. Selecting Custom hides sub-checkboxes. Selecting None clears all WhatsApp module strings.

Stored values are the module strings listed in Section 1 — no new API needed.

### 5b. Hospital Admin Dashboard — Messages tab

New sidebar tab visible only to `HOSPITAL_ADMIN`, `CLINIC_ADMIN`, `PHARMACY_ADMIN` roles. Red badge on tab when `PERMANENTLY_FAILED` count > 0 (polled via `GET /hospital/whatsapp/logs/failed-count` on tab open).

**Three sections on the page:**

**Broadcast** (top)
- Textarea with 1024-char counter
- Optional image upload (existing Cloudinary upload component)
- Patient count label: *"Will be sent to X patients"*
- "Broadcast Now" button → confirmation modal → `POST /hospital/whatsapp/broadcast`
- Recent broadcasts table: date, message preview, sent count, failed count

**Notification Log** (middle)
- Table: patient name, phone, message type, status badge, date
- Filter dropdowns: message type, status
- Shows most recent 50, paginated

**WhatsApp Settings** (bottom — visible only if plan has `WHATSAPP_CUSTOM`)
- Masked input: Access Token
- Input: Phone Number ID
- Input: WABA ID (optional)
- Per-type toggles: Appointments / Billing / Case Papers / Prescriptions / In-Clinic Medicine List (maps to `send_*` boolean columns in `whatsapp_config`)
- "Save Credentials" button → `POST /hospital/whatsapp/config`
- "Test Connection" button → `POST /hospital/whatsapp/config/test` → green tick or error message
- If no credentials saved: setup guide with steps to get credentials from Meta dashboard

### 5c. Platform Dashboard — WhatsApp failures stat card

New card in the Overview tab stats row:

```
┌──────────────────────┐
│  WhatsApp Failures   │
│          12          │
│  today · 3 hospitals │
└──────────────────────┘
```

Data from `GET /platform/whatsapp/stats`. Clicking navigates to a filtered view of the audit/log area showing permanently failed messages across all hospitals today.

---

## 6. API Endpoints

### Hospital-level (`HOSPITAL_ADMIN` / `CLINIC_ADMIN` / `PHARMACY_ADMIN`)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/hospital/whatsapp/broadcast` | Send broadcast to all patients |
| `GET` | `/hospital/whatsapp/logs` | Paginated log (params: `type`, `status`, `page`, `size`) |
| `GET` | `/hospital/whatsapp/logs/failed-count` | Count of `PERMANENTLY_FAILED` for badge |
| `GET` | `/hospital/whatsapp/config` | Get custom config (token masked) |
| `POST` | `/hospital/whatsapp/config` | Save / update custom credentials |
| `DELETE` | `/hospital/whatsapp/config` | Remove custom config (reverts to platform mode) |
| `POST` | `/hospital/whatsapp/config/test` | Test custom credentials against Meta API |

### Platform-level (Super Admin)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/platform/whatsapp/stats` | Failures today + count of affected hospitals |

---

## 7. Error Handling & Retry

| Attempt | Behaviour |
|---|---|
| 1st failure | Status → `FAILED`, `next_retry_at = now + 15 min`, `retry_count = 0` |
| 2nd attempt (retry 1) | On fail: `retry_count = 1`, `next_retry_at = now + 15 min` |
| 3rd attempt (retry 2) | On fail: `retry_count = 2`, status → `PERMANENTLY_FAILED` |
| `PERMANENTLY_FAILED` | Increments hospital admin badge + platform stats counter |

`WhatsAppRetryScheduler` runs every 5 minutes and processes all eligible `FAILED` rows.

**Access token expiry scenario** (platform token expires → all messages fail): System Admin sees platform-wide spike in the stat card → contacts Meta to refresh token → updates `.env` → redeploys. PERMANENTLY_FAILED rows are not auto-retried after token fix (hospital admin can see them in the log for awareness).

---

## 8. Phone Number Handling

Patient phones are stored as 10-digit Indian numbers (e.g. `9876543210`). WhatsApp Cloud API requires full international format. `WhatsAppService` prepends `91` before every send: `"91" + patient.getPhone()`. No DB change needed.

---

## Files Created / Modified

### New files
- `service/whatsapp/WhatsAppService.java`
- `service/whatsapp/WhatsAppEventListener.java`
- `service/whatsapp/WhatsAppBroadcastService.java`
- `scheduler/AppointmentReminderScheduler.java`
- `scheduler/WhatsAppRetryScheduler.java`
- `entity/WhatsAppConfig.java` + `repository/WhatsAppConfigRepository.java`
- `entity/WhatsAppMessageLog.java` + `repository/WhatsAppMessageLogRepository.java`
- `event/AppointmentCreatedEvent.java`
- `event/ConsultationCompletedEvent.java`
- `event/MedicineDispensedEvent.java`
- `controller/hospital/WhatsAppController.java`
- `controller/platform/PlatformWhatsAppController.java`
- `dto/WhatsAppConfigDTO.java`, `WhatsAppBroadcastRequest.java`, `WhatsAppStatsDTO.java`, `WhatsAppLogDTO.java`
- `config/WhatsAppTemplateConstants.java`
- `frontend/src/pages/hospital/MessagesTab.jsx`

### Modified files (minimal touch)
- `service/hospital/AppointmentService.java` — +1 line: publish `AppointmentCreatedEvent`
- `service/hospital/BillingService.java` — +1 line: publish `ConsultationCompletedEvent`
- `service/hospital/MedicineService.java` — +1 line in `addMedicinePurchase()`: publish `MedicineDispensedEvent`
- `frontend/src/components/PlansTab.jsx` — add WhatsApp module section
- `frontend/src/pages/hospital/HospitalAdminDashboard.jsx` — add Messages tab to sidebar
- `frontend/src/pages/platform/PlatformDashboard.jsx` — add WhatsApp failures stat card
- `setup/migrations/` — new V4 migration SQL for two new tables
- `backend/.env.example` — add four new WhatsApp env vars
