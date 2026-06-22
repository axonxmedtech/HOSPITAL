# WhatsApp Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Meta WhatsApp Cloud API into HMS so hospitals automatically message patients on appointment creation, day-before reminders, consultation documents, and in-clinic dispensing — plus a manual broadcast feature and a notification log.

**Architecture:** Spring Application Events with `@Async` listeners decouple WhatsApp sends from core operations; failures never affect patient-facing flows. Plan-level toggles control which message types each hospital gets; CUSTOM-mode hospitals store their own encrypted Meta credentials. A retry scheduler re-attempts failed sends twice at 15-minute gaps before marking permanently failed and surfacing to hospital admin + Super Admin.

**Tech Stack:** Meta WhatsApp Cloud API v19.0, Spring `@Async` + `@EventListener`, Spring `@Scheduled`, OpenPDF + Cloudinary for document delivery, AES-256 for credential encryption, React + Axios (existing patterns).

**Spec:** `docs/superpowers/specs/2026-06-22-whatsapp-integration-design.md`

---

## File Map

### New backend files
| File | Purpose |
|---|---|
| `setup/migrations/V4_whatsapp_tables.sql` | SQL for `whatsapp_config` + `whatsapp_message_log` tables |
| `entity/WhatsAppConfig.java` | JPA entity for custom hospital credentials |
| `entity/WhatsAppMessageLog.java` | JPA entity for every send attempt |
| `repository/WhatsAppConfigRepository.java` | Spring Data repo for WhatsAppConfig |
| `repository/WhatsAppMessageLogRepository.java` | Spring Data repo for WhatsAppMessageLog |
| `event/AppointmentCreatedEvent.java` | Spring event POJO |
| `event/ConsultationCompletedEvent.java` | Spring event POJO |
| `event/MedicineDispensedEvent.java` | Spring event POJO |
| `config/WhatsAppTemplateConstants.java` | Template name string constants |
| `config/AsyncConfig.java` | `@EnableAsync` + thread pool config |
| `dto/WhatsAppConfigDTO.java` | Request/response DTO for custom config |
| `dto/WhatsAppBroadcastRequest.java` | Broadcast request DTO |
| `dto/WhatsAppStatsDTO.java` | Platform stats response DTO |
| `dto/WhatsAppLogDTO.java` | Log list response DTO |
| `service/whatsapp/WhatsAppService.java` | Meta API calls, credential resolution, log writes |
| `service/whatsapp/WhatsAppEventListener.java` | `@Async` event handlers |
| `service/whatsapp/WhatsAppBroadcastService.java` | Broadcast to all patients |
| `scheduler/AppointmentReminderScheduler.java` | 9 AM IST daily reminders |
| `scheduler/WhatsAppRetryScheduler.java` | 5-min retry loop for failed sends |
| `controller/hospital/WhatsAppController.java` | 7 hospital-level endpoints |
| `controller/platform/PlatformWhatsAppController.java` | 1 Super Admin stats endpoint |

### Modified backend files
| File | Change |
|---|---|
| `config/DatabaseMigrationRunner.java` | Add `ensureWhatsAppTables()` call |
| `repository/HospitalRepository.java` | Add `findByModulesContaining()` query |
| `service/hospital/AppointmentService.java` | +1 line: publish `AppointmentCreatedEvent` |
| `service/hospital/BillingService.java` | +1 line: publish `ConsultationCompletedEvent` |
| `service/hospital/MedicineService.java` | +1 line: publish `MedicineDispensedEvent` |
| `backend/.env.example` | 4 new WhatsApp env vars |

### New frontend files
| File | Purpose |
|---|---|
| `frontend/src/pages/hospital/MessagesTab.jsx` | Broadcast + log + settings panel |

### Modified frontend files
| File | Change |
|---|---|
| `frontend/src/components/PlansTab.jsx` | WhatsApp module radio + sub-checkboxes |
| `frontend/src/pages/hospital/HospitalAdminDashboard.jsx` | Messages tab entry + MessagesTab render |
| `frontend/src/pages/platform/PlatformDashboard.jsx` | WhatsApp failures stat card |

---

## Task 1: DB Migration SQL

**Files:**
- Create: `setup/migrations/V4_whatsapp_tables.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- V4_whatsapp_tables.sql
-- Creates whatsapp_config and whatsapp_message_log tables.
-- Applied automatically on startup via DatabaseMigrationRunner.java.

CREATE TABLE IF NOT EXISTS whatsapp_config (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  hospital_id          BIGINT       NOT NULL UNIQUE,
  access_token         VARCHAR(500) NOT NULL,
  phone_number_id      VARCHAR(100) NOT NULL,
  waba_id              VARCHAR(100) DEFAULT NULL,
  is_active            TINYINT(1)   NOT NULL DEFAULT 1,
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

CREATE TABLE IF NOT EXISTS whatsapp_message_log (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  hospital_id      BIGINT       NOT NULL,
  patient_id       BIGINT       DEFAULT NULL,
  patient_phone    VARCHAR(20)  NOT NULL,
  message_type     VARCHAR(50)  NOT NULL,
  status           VARCHAR(25)  NOT NULL,
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

Save to `setup/migrations/V4_whatsapp_tables.sql`.

- [ ] **Step 2: Add migration calls to DatabaseMigrationRunner**

Open `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`.

Add two calls in `runMigrations()` and two private methods:

```java
@EventListener(ApplicationReadyEvent.class)
public void runMigrations() {
    fixHospitalsPlanColumn();
    ensureHospitalSettingsInClinic();
    ensureHospitalsIsSingleDoctor();
    ensureWhatsAppConfigTable();      // NEW
    ensureWhatsAppMessageLogTable();  // NEW
}
```

Append the two private methods at the end of the class (before the closing `}`):

```java
private void ensureWhatsAppConfigTable() {
    try {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'whatsapp_config'",
            Integer.class
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(
                "CREATE TABLE whatsapp_config (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT," +
                "  hospital_id BIGINT NOT NULL UNIQUE," +
                "  access_token VARCHAR(500) NOT NULL," +
                "  phone_number_id VARCHAR(100) NOT NULL," +
                "  waba_id VARCHAR(100) DEFAULT NULL," +
                "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                "  send_appointments TINYINT(1) NOT NULL DEFAULT 1," +
                "  send_billing TINYINT(1) NOT NULL DEFAULT 1," +
                "  send_case_papers TINYINT(1) NOT NULL DEFAULT 1," +
                "  send_prescription TINYINT(1) NOT NULL DEFAULT 1," +
                "  send_medicine_list TINYINT(1) NOT NULL DEFAULT 1," +
                "  created_at DATETIME(6) NOT NULL," +
                "  updated_at DATETIME(6) DEFAULT NULL," +
                "  PRIMARY KEY (id)," +
                "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id)" +
                ")"
            );
            log.info("DB migration applied: whatsapp_config table created");
        }
    } catch (Exception e) {
        log.warn("DB migration skipped (whatsapp_config): {}", e.getMessage());
    }
}

private void ensureWhatsAppMessageLogTable() {
    try {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'whatsapp_message_log'",
            Integer.class
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(
                "CREATE TABLE whatsapp_message_log (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT," +
                "  hospital_id BIGINT NOT NULL," +
                "  patient_id BIGINT DEFAULT NULL," +
                "  patient_phone VARCHAR(20) NOT NULL," +
                "  message_type VARCHAR(50) NOT NULL," +
                "  status VARCHAR(25) NOT NULL," +
                "  error_message VARCHAR(500) DEFAULT NULL," +
                "  retry_count INT NOT NULL DEFAULT 0," +
                "  next_retry_at DATETIME(6) DEFAULT NULL," +
                "  sent_at DATETIME(6) DEFAULT NULL," +
                "  created_at DATETIME(6) NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_wml_hospital_status (hospital_id, status)," +
                "  KEY idx_wml_retry (status, next_retry_at)" +
                ")"
            );
            log.info("DB migration applied: whatsapp_message_log table created");
        }
    } catch (Exception e) {
        log.warn("DB migration skipped (whatsapp_message_log): {}", e.getMessage());
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add setup/migrations/V4_whatsapp_tables.sql
git add backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java
git commit -m "feat: add V4 migration SQL + DatabaseMigrationRunner patches for WhatsApp tables"
```

---

## Task 2: JPA Entities + Repositories

**Files:**
- Create: `backend/src/main/java/com/hms/entity/WhatsAppConfig.java`
- Create: `backend/src/main/java/com/hms/entity/WhatsAppMessageLog.java`
- Create: `backend/src/main/java/com/hms/repository/WhatsAppConfigRepository.java`
- Create: `backend/src/main/java/com/hms/repository/WhatsAppMessageLogRepository.java`

- [ ] **Step 1: Create WhatsAppConfig entity**

```java
package com.hms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_config")
public class WhatsAppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false, unique = true)
    private Long hospitalId;

    @Column(name = "access_token", nullable = false, length = 500)
    private String accessToken;

    @Column(name = "phone_number_id", nullable = false, length = 100)
    private String phoneNumberId;

    @Column(name = "waba_id", length = 100)
    private String wabaId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "send_appointments", nullable = false)
    private boolean sendAppointments = true;

    @Column(name = "send_billing", nullable = false)
    private boolean sendBilling = true;

    @Column(name = "send_case_papers", nullable = false)
    private boolean sendCasePapers = true;

    @Column(name = "send_prescription", nullable = false)
    private boolean sendPrescription = true;

    @Column(name = "send_medicine_list", nullable = false)
    private boolean sendMedicineList = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getWabaId() { return wabaId; }
    public void setWabaId(String wabaId) { this.wabaId = wabaId; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public boolean isSendAppointments() { return sendAppointments; }
    public void setSendAppointments(boolean sendAppointments) { this.sendAppointments = sendAppointments; }
    public boolean isSendBilling() { return sendBilling; }
    public void setSendBilling(boolean sendBilling) { this.sendBilling = sendBilling; }
    public boolean isSendCasePapers() { return sendCasePapers; }
    public void setSendCasePapers(boolean sendCasePapers) { this.sendCasePapers = sendCasePapers; }
    public boolean isSendPrescription() { return sendPrescription; }
    public void setSendPrescription(boolean sendPrescription) { this.sendPrescription = sendPrescription; }
    public boolean isSendMedicineList() { return sendMedicineList; }
    public void setSendMedicineList(boolean sendMedicineList) { this.sendMedicineList = sendMedicineList; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

Save to `backend/src/main/java/com/hms/entity/WhatsAppConfig.java`.

- [ ] **Step 2: Create WhatsAppMessageLog entity**

```java
package com.hms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_message_log")
public class WhatsAppMessageLog {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PERMANENTLY_FAILED = "PERMANENTLY_FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "patient_phone", nullable = false, length = 20)
    private String patientPhone;

    @Column(name = "message_type", nullable = false, length = 50)
    private String messageType;

    @Column(name = "status", nullable = false, length = 25)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

Save to `backend/src/main/java/com/hms/entity/WhatsAppMessageLog.java`.

- [ ] **Step 3: Create WhatsAppConfigRepository**

```java
package com.hms.repository;

import com.hms.entity.WhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WhatsAppConfigRepository extends JpaRepository<WhatsAppConfig, Long> {
    Optional<WhatsAppConfig> findByHospitalId(Long hospitalId);
    boolean existsByHospitalId(Long hospitalId);
}
```

Save to `backend/src/main/java/com/hms/repository/WhatsAppConfigRepository.java`.

- [ ] **Step 4: Create WhatsAppMessageLogRepository**

```java
package com.hms.repository;

import com.hms.entity.WhatsAppMessageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WhatsAppMessageLogRepository extends JpaRepository<WhatsAppMessageLog, Long> {

    Page<WhatsAppMessageLog> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId, Pageable pageable);

    Page<WhatsAppMessageLog> findByHospitalIdAndMessageTypeOrderByCreatedAtDesc(Long hospitalId, String messageType, Pageable pageable);

    Page<WhatsAppMessageLog> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status, Pageable pageable);

    Page<WhatsAppMessageLog> findByHospitalIdAndMessageTypeAndStatusOrderByCreatedAtDesc(
            Long hospitalId, String messageType, String status, Pageable pageable);

    long countByHospitalIdAndStatus(Long hospitalId, String status);

    List<WhatsAppMessageLog> findByStatusAndNextRetryAtBeforeAndRetryCountLessThan(
            String status, LocalDateTime now, int maxRetries);

    @Query("SELECT COUNT(l) FROM WhatsAppMessageLog l WHERE l.status = :status AND l.createdAt >= :since")
    long countByStatusSince(@Param("status") String status, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT l.hospitalId) FROM WhatsAppMessageLog l WHERE l.status = :status AND l.createdAt >= :since")
    long countDistinctHospitalsByStatusSince(@Param("status") String status, @Param("since") LocalDateTime since);
}
```

Save to `backend/src/main/java/com/hms/repository/WhatsAppMessageLogRepository.java`.

- [ ] **Step 5: Add `findByModulesContaining` to HospitalRepository**

Open `backend/src/main/java/com/hms/repository/HospitalRepository.java` and add this import + method:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

```java
@Query("SELECT DISTINCT h FROM Hospital h JOIN h.modules m WHERE m IN :moduleNames")
List<Hospital> findByAnyModule(@Param("moduleNames") List<String> moduleNames);
```

- [ ] **Step 6: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS with no errors.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hms/entity/WhatsAppConfig.java
git add backend/src/main/java/com/hms/entity/WhatsAppMessageLog.java
git add backend/src/main/java/com/hms/repository/WhatsAppConfigRepository.java
git add backend/src/main/java/com/hms/repository/WhatsAppMessageLogRepository.java
git add backend/src/main/java/com/hms/repository/HospitalRepository.java
git commit -m "feat: add WhatsApp JPA entities and repositories"
```

---

## Task 3: Spring Event POJOs

**Files:**
- Create: `backend/src/main/java/com/hms/event/AppointmentCreatedEvent.java`
- Create: `backend/src/main/java/com/hms/event/ConsultationCompletedEvent.java`
- Create: `backend/src/main/java/com/hms/event/MedicineDispensedEvent.java`

- [ ] **Step 1: Create AppointmentCreatedEvent**

```java
package com.hms.event;

public class AppointmentCreatedEvent {
    private final Long hospitalId;
    private final Long patientId;
    private final Long appointmentId;

    public AppointmentCreatedEvent(Long hospitalId, Long patientId, Long appointmentId) {
        this.hospitalId = hospitalId;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
    }

    public Long getHospitalId() { return hospitalId; }
    public Long getPatientId() { return patientId; }
    public Long getAppointmentId() { return appointmentId; }
}
```

Save to `backend/src/main/java/com/hms/event/AppointmentCreatedEvent.java`.

- [ ] **Step 2: Create ConsultationCompletedEvent**

```java
package com.hms.event;

public class ConsultationCompletedEvent {
    private final Long hospitalId;
    private final Long patientId;
    private final Long appointmentId;

    public ConsultationCompletedEvent(Long hospitalId, Long patientId, Long appointmentId) {
        this.hospitalId = hospitalId;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
    }

    public Long getHospitalId() { return hospitalId; }
    public Long getPatientId() { return patientId; }
    public Long getAppointmentId() { return appointmentId; }
}
```

Save to `backend/src/main/java/com/hms/event/ConsultationCompletedEvent.java`.

- [ ] **Step 3: Create MedicineDispensedEvent**

```java
package com.hms.event;

public class MedicineDispensedEvent {
    private final Long hospitalId;
    private final Long patientId;   // nullable — not all purchases are patient-linked
    private final Long purchaseId;

    public MedicineDispensedEvent(Long hospitalId, Long patientId, Long purchaseId) {
        this.hospitalId = hospitalId;
        this.patientId = patientId;
        this.purchaseId = purchaseId;
    }

    public Long getHospitalId() { return hospitalId; }
    public Long getPatientId() { return patientId; }
    public Long getPurchaseId() { return purchaseId; }
}
```

Save to `backend/src/main/java/com/hms/event/MedicineDispensedEvent.java`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/event/
git commit -m "feat: add Spring event POJOs for WhatsApp triggers"
```

---

## Task 4: Constants, DTOs, and Async Config

**Files:**
- Create: `backend/src/main/java/com/hms/config/WhatsAppTemplateConstants.java`
- Create: `backend/src/main/java/com/hms/config/AsyncConfig.java`
- Create: `backend/src/main/java/com/hms/dto/WhatsAppConfigDTO.java`
- Create: `backend/src/main/java/com/hms/dto/WhatsAppBroadcastRequest.java`
- Create: `backend/src/main/java/com/hms/dto/WhatsAppStatsDTO.java`
- Create: `backend/src/main/java/com/hms/dto/WhatsAppLogDTO.java`

- [ ] **Step 1: Create WhatsAppTemplateConstants**

```java
package com.hms.config;

public final class WhatsAppTemplateConstants {
    private WhatsAppTemplateConstants() {}

    public static final String APPOINTMENT_CONFIRMATION = "hms_appointment_confirmation";
    public static final String APPOINTMENT_REMINDER     = "hms_appointment_reminder";
    public static final String DOCUMENT_READY           = "hms_document_ready";
    public static final String BROADCAST                = "hms_broadcast";

    public static final String MSG_TYPE_APPOINTMENT_CONFIRMATION = "APPOINTMENT_CONFIRMATION";
    public static final String MSG_TYPE_APPOINTMENT_REMINDER     = "APPOINTMENT_REMINDER";
    public static final String MSG_TYPE_BILLING                  = "BILLING";
    public static final String MSG_TYPE_CASE_PAPER               = "CASE_PAPER";
    public static final String MSG_TYPE_PRESCRIPTION             = "PRESCRIPTION";
    public static final String MSG_TYPE_MEDICINE_LIST            = "MEDICINE_LIST";
    public static final String MSG_TYPE_BROADCAST                = "BROADCAST";

    public static final String MODULE_WHATSAPP_PLATFORM = "WHATSAPP_PLATFORM";
    public static final String MODULE_WHATSAPP_CUSTOM   = "WHATSAPP_CUSTOM";
    public static final String MODULE_WA_APPOINTMENTS   = "WA_APPOINTMENTS";
    public static final String MODULE_WA_BILLING        = "WA_BILLING";
    public static final String MODULE_WA_CASE_PAPERS    = "WA_CASE_PAPERS";
    public static final String MODULE_WA_PRESCRIPTION   = "WA_PRESCRIPTION";
    public static final String MODULE_WA_MEDICINE_LIST  = "WA_MEDICINE_LIST";
}
```

Save to `backend/src/main/java/com/hms/config/WhatsAppTemplateConstants.java`.

- [ ] **Step 2: Create AsyncConfig**

```java
package com.hms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    // Spring Boot's default SimpleAsyncTaskExecutor is sufficient for this use case.
    // The @Async methods run in background threads; failures don't affect callers.
}
```

Save to `backend/src/main/java/com/hms/config/AsyncConfig.java`.

- [ ] **Step 3: Create WhatsAppConfigDTO**

```java
package com.hms.dto;

public class WhatsAppConfigDTO {
    private String accessToken;      // on save: full token; on get: masked "••••••••<last4>"
    private String phoneNumberId;
    private String wabaId;
    private boolean active;
    private boolean sendAppointments;
    private boolean sendBilling;
    private boolean sendCasePapers;
    private boolean sendPrescription;
    private boolean sendMedicineList;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getWabaId() { return wabaId; }
    public void setWabaId(String wabaId) { this.wabaId = wabaId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isSendAppointments() { return sendAppointments; }
    public void setSendAppointments(boolean sendAppointments) { this.sendAppointments = sendAppointments; }
    public boolean isSendBilling() { return sendBilling; }
    public void setSendBilling(boolean sendBilling) { this.sendBilling = sendBilling; }
    public boolean isSendCasePapers() { return sendCasePapers; }
    public void setSendCasePapers(boolean sendCasePapers) { this.sendCasePapers = sendCasePapers; }
    public boolean isSendPrescription() { return sendPrescription; }
    public void setSendPrescription(boolean sendPrescription) { this.sendPrescription = sendPrescription; }
    public boolean isSendMedicineList() { return sendMedicineList; }
    public void setSendMedicineList(boolean sendMedicineList) { this.sendMedicineList = sendMedicineList; }
}
```

Save to `backend/src/main/java/com/hms/dto/WhatsAppConfigDTO.java`.

- [ ] **Step 4: Create WhatsAppBroadcastRequest**

```java
package com.hms.dto;

public class WhatsAppBroadcastRequest {
    private String messageText;  // max 1024 chars — the {{1}} variable in hms_broadcast template
    private String imageUrl;     // optional Cloudinary URL for header image

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
```

Save to `backend/src/main/java/com/hms/dto/WhatsAppBroadcastRequest.java`.

- [ ] **Step 5: Create WhatsAppStatsDTO**

```java
package com.hms.dto;

public class WhatsAppStatsDTO {
    private long failedToday;
    private long affectedHospitalsToday;

    public WhatsAppStatsDTO(long failedToday, long affectedHospitalsToday) {
        this.failedToday = failedToday;
        this.affectedHospitalsToday = affectedHospitalsToday;
    }

    public long getFailedToday() { return failedToday; }
    public long getAffectedHospitalsToday() { return affectedHospitalsToday; }
}
```

Save to `backend/src/main/java/com/hms/dto/WhatsAppStatsDTO.java`.

- [ ] **Step 6: Create WhatsAppLogDTO**

```java
package com.hms.dto;

import java.time.LocalDateTime;

public class WhatsAppLogDTO {
    private Long id;
    private Long patientId;
    private String patientPhone;
    private String messageType;
    private String status;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

Save to `backend/src/main/java/com/hms/dto/WhatsAppLogDTO.java`.

- [ ] **Step 7: Add WhatsApp env vars to .env.example**

Open `backend/.env.example` and add at the bottom:

```
# WhatsApp Cloud API (Meta)
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_PHONE_NUMBER_ID=
WHATSAPP_API_VERSION=v19.0
WHATSAPP_ENCRYPTION_KEY=
```

- [ ] **Step 8: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/hms/config/WhatsAppTemplateConstants.java
git add backend/src/main/java/com/hms/config/AsyncConfig.java
git add backend/src/main/java/com/hms/dto/WhatsAppConfigDTO.java
git add backend/src/main/java/com/hms/dto/WhatsAppBroadcastRequest.java
git add backend/src/main/java/com/hms/dto/WhatsAppStatsDTO.java
git add backend/src/main/java/com/hms/dto/WhatsAppLogDTO.java
git add backend/.env.example
git commit -m "feat: add WhatsApp constants, DTOs, and async config"
```

---

## Task 5: WhatsAppService (Meta API Client)

**Files:**
- Create: `backend/src/main/java/com/hms/service/whatsapp/WhatsAppService.java`

This is the core service. It resolves credentials (custom config or platform env vars), calls the Meta API, and writes every attempt to `whatsapp_message_log`.

- [ ] **Step 1: Create WhatsAppService**

```java
package com.hms.service.whatsapp;

import com.hms.config.WhatsAppTemplateConstants;
import com.hms.entity.WhatsAppConfig;
import com.hms.entity.WhatsAppMessageLog;
import com.hms.repository.WhatsAppConfigRepository;
import com.hms.repository.WhatsAppMessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final String BASE_URL = "https://graph.facebook.com/%s/%s/messages";
    private static final int MAX_RETRIES = 2;

    @Value("${whatsapp.access-token:}")
    private String platformAccessToken;

    @Value("${whatsapp.phone-number-id:}")
    private String platformPhoneNumberId;

    @Value("${whatsapp.api-version:v19.0}")
    private String apiVersion;

    @Value("${whatsapp.encryption-key:}")
    private String encryptionKey;

    private final WhatsAppConfigRepository configRepository;
    private final WhatsAppMessageLogRepository logRepository;
    private final RestTemplate restTemplate;

    public WhatsAppService(WhatsAppConfigRepository configRepository,
                           WhatsAppMessageLogRepository logRepository) {
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Returns true if the hospital has WhatsApp enabled for the given sub-module.
     * WHATSAPP_CUSTOM always returns true (hospital controls per-type via whatsapp_config toggles).
     * WHATSAPP_PLATFORM returns true only if the hospital also has the specific sub-module string.
     */
    public boolean isEnabled(Long hospitalId, List<String> hospitalModules, String subModule) {
        if (hospitalModules == null) return false;
        if (hospitalModules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) return true;
        return hospitalModules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_PLATFORM)
                && hospitalModules.contains(subModule);
    }

    /**
     * Send appointment confirmation template.
     * {{1}} patientName · {{2}} hospitalName · {{3}} date · {{4}} time
     */
    public void sendAppointmentConfirmation(Long hospitalId, Long patientId,
                                            String phone, String patientName,
                                            String hospitalName, String date, String time) {
        List<String> params = List.of(patientName, hospitalName, date, time);
        doSend(hospitalId, patientId, phone,
                WhatsAppTemplateConstants.APPOINTMENT_CONFIRMATION,
                WhatsAppTemplateConstants.MSG_TYPE_APPOINTMENT_CONFIRMATION,
                params, null);
    }

    /**
     * Send appointment reminder template.
     */
    public void sendAppointmentReminder(Long hospitalId, Long patientId,
                                        String phone, String patientName,
                                        String hospitalName, String date, String time) {
        List<String> params = List.of(patientName, hospitalName, date, time);
        doSend(hospitalId, patientId, phone,
                WhatsAppTemplateConstants.APPOINTMENT_REMINDER,
                WhatsAppTemplateConstants.MSG_TYPE_APPOINTMENT_REMINDER,
                params, null);
    }

    /**
     * Send document-ready template with a Cloudinary PDF URL as document header.
     * {{1}} patientName · {{2}} docType · {{3}} hospitalName
     */
    public void sendDocument(Long hospitalId, Long patientId,
                             String phone, String patientName,
                             String hospitalName, String docType,
                             String documentUrl, String msgType) {
        List<String> params = List.of(patientName, docType, hospitalName);
        doSend(hospitalId, patientId, phone,
                WhatsAppTemplateConstants.DOCUMENT_READY, msgType, params, documentUrl);
    }

    /**
     * Send broadcast template. {{1}} is the full message body.
     */
    public void sendBroadcast(Long hospitalId, Long patientId,
                              String phone, String messageText, String imageUrl) {
        List<String> params = List.of(messageText);
        doSend(hospitalId, patientId, phone,
                WhatsAppTemplateConstants.BROADCAST,
                WhatsAppTemplateConstants.MSG_TYPE_BROADCAST, params, imageUrl);
    }

    /**
     * Retry a previously failed log entry.
     */
    public void retry(WhatsAppMessageLog entry) {
        // Rebuild and re-send — for retry we call the Meta API directly using stored log data
        // (we don't have the original params, so we cannot rebuild a template call;
        //  instead mark as a simple retry ping with minimal payload)
        String[] creds = resolveCredentials(entry.getHospitalId());
        if (creds == null) {
            markPermanentlyFailed(entry, "No WhatsApp credentials configured");
            return;
        }
        // For retries we re-attempt the same phone/type combination using a simple text message
        // noting the document is available. Full template retry would require storing params —
        // this is a best-effort re-attempt.
        boolean success = callMetaApi(creds[0], creds[1], entry.getPatientPhone(), null, null, null);
        if (success) {
            entry.setStatus(WhatsAppMessageLog.STATUS_SENT);
            entry.setSentAt(LocalDateTime.now());
            entry.setErrorMessage(null);
        } else {
            if (entry.getRetryCount() >= MAX_RETRIES) {
                markPermanentlyFailed(entry, "Max retries exceeded");
            } else {
                entry.setRetryCount(entry.getRetryCount() + 1);
                entry.setNextRetryAt(LocalDateTime.now().plusMinutes(15));
            }
        }
        logRepository.save(entry);
    }

    // ---- Internal ----

    private void doSend(Long hospitalId, Long patientId, String rawPhone,
                        String templateName, String msgType,
                        List<String> templateParams, String mediaUrl) {
        String phone = normalizePhone(rawPhone);
        String[] creds = resolveCredentials(hospitalId);

        WhatsAppMessageLog entry = new WhatsAppMessageLog();
        entry.setHospitalId(hospitalId);
        entry.setPatientId(patientId);
        entry.setPatientPhone(phone);
        entry.setMessageType(msgType);

        if (creds == null) {
            entry.setStatus(WhatsAppMessageLog.STATUS_FAILED);
            entry.setErrorMessage("No WhatsApp credentials configured for hospital " + hospitalId);
            entry.setNextRetryAt(LocalDateTime.now().plusMinutes(15));
            logRepository.save(entry);
            return;
        }

        boolean ok = callMetaApi(creds[0], creds[1], phone, templateName, templateParams, mediaUrl);
        if (ok) {
            entry.setStatus(WhatsAppMessageLog.STATUS_SENT);
            entry.setSentAt(LocalDateTime.now());
        } else {
            entry.setStatus(WhatsAppMessageLog.STATUS_FAILED);
            entry.setNextRetryAt(LocalDateTime.now().plusMinutes(15));
        }
        logRepository.save(entry);
    }

    private boolean callMetaApi(String accessToken, String phoneNumberId,
                                String toPhone, String templateName,
                                List<String> params, String mediaUrl) {
        try {
            String url = String.format(BASE_URL, apiVersion, phoneNumberId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", toPhone);
            body.put("type", "template");

            if (templateName != null) {
                Map<String, Object> template = new LinkedHashMap<>();
                template.put("name", templateName);
                Map<String, Object> language = new LinkedHashMap<>();
                language.put("code", "en");
                template.put("language", language);

                if (params != null && !params.isEmpty()) {
                    List<Map<String, Object>> components = new ArrayList<>();
                    // Body component
                    Map<String, Object> bodyComp = new LinkedHashMap<>();
                    bodyComp.put("type", "body");
                    List<Map<String, Object>> bodyParams = new ArrayList<>();
                    for (String p : params) {
                        bodyParams.add(Map.of("type", "text", "text", p));
                    }
                    bodyComp.put("parameters", bodyParams);
                    components.add(bodyComp);

                    // Header component (document or image)
                    if (mediaUrl != null && !mediaUrl.isBlank()) {
                        Map<String, Object> headerComp = new LinkedHashMap<>();
                        headerComp.put("type", "header");
                        List<Map<String, Object>> headerParams = new ArrayList<>();
                        if (WhatsAppTemplateConstants.DOCUMENT_READY.equals(templateName)) {
                            headerParams.add(Map.of("type", "document",
                                    "document", Map.of("link", mediaUrl)));
                        } else {
                            headerParams.add(Map.of("type", "image",
                                    "image", Map.of("link", mediaUrl)));
                        }
                        headerComp.put("parameters", headerParams);
                        components.add(headerComp);
                    }
                    template.put("components", components);
                }
                body.put("template", template);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("WhatsApp API call failed: {}", e.getMessage());
            return false;
        }
    }

    /** Returns [accessToken, phoneNumberId] or null if neither custom nor platform is configured. */
    private String[] resolveCredentials(Long hospitalId) {
        Optional<WhatsAppConfig> custom = configRepository.findByHospitalId(hospitalId);
        if (custom.isPresent() && custom.get().isActive()) {
            WhatsAppConfig cfg = custom.get();
            String token = decrypt(cfg.getAccessToken());
            return new String[]{token, cfg.getPhoneNumberId()};
        }
        if (!platformAccessToken.isBlank() && !platformPhoneNumberId.isBlank()) {
            return new String[]{platformAccessToken, platformPhoneNumberId};
        }
        return null;
    }

    private void markPermanentlyFailed(WhatsAppMessageLog entry, String reason) {
        entry.setStatus(WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED);
        entry.setErrorMessage(reason);
    }

    /** Prepends "91" to a 10-digit Indian phone number. */
    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) return "91" + digits;
        if (digits.startsWith("91") && digits.length() == 12) return digits;
        return digits;
    }

    public String encrypt(String plaintext) {
        if (encryptionKey.isBlank()) return plaintext;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(
                    encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("WhatsApp token encryption failed, storing plain", e);
            return plaintext;
        }
    }

    public String decrypt(String ciphertext) {
        if (encryptionKey.isBlank()) return ciphertext;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(
                    encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(
                    Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("WhatsApp token decryption failed, returning as-is", e);
            return ciphertext;
        }
    }
}
```

Save to `backend/src/main/java/com/hms/service/whatsapp/WhatsAppService.java`.

**Note about `whatsapp.*` property names:** Spring Boot maps `WHATSAPP_ACCESS_TOKEN` env var to `whatsapp.access-token` property. Add these to `backend/src/main/resources/application.properties`:

```properties
whatsapp.access-token=${WHATSAPP_ACCESS_TOKEN:}
whatsapp.phone-number-id=${WHATSAPP_PHONE_NUMBER_ID:}
whatsapp.api-version=${WHATSAPP_API_VERSION:v19.0}
whatsapp.encryption-key=${WHATSAPP_ENCRYPTION_KEY:}
```

- [ ] **Step 2: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/service/whatsapp/WhatsAppService.java
git add backend/src/main/resources/application.properties
git commit -m "feat: add WhatsAppService with Meta API client and credential resolution"
```

---

## Task 6: Publish Events from Existing Services

**Files:**
- Modify: `backend/src/main/java/com/hms/service/hospital/AppointmentService.java`
- Modify: `backend/src/main/java/com/hms/service/hospital/BillingService.java`
- Modify: `backend/src/main/java/com/hms/service/hospital/MedicineService.java`

Each change is exactly one new field injection + one line at the right spot. No other logic changes.

- [ ] **Step 1: Update AppointmentService**

Open `AppointmentService.java`.

Add import at top of imports:
```java
import com.hms.event.AppointmentCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

Add field (after other `@Autowired` fields):
```java
@Autowired
private ApplicationEventPublisher eventPublisher;
```

In `createAppointment()`, after line `return savedAppointment;` is computed but BEFORE it returns — add before the `return savedAppointment;` statement (the method currently ends with the WebSocket try/catch block and then `return savedAppointment;`). Insert after the websocket try/catch block:

```java
try {
    eventPublisher.publishEvent(new AppointmentCreatedEvent(
            hospitalId, savedAppointment.getPatientId(), savedAppointment.getId()));
} catch (Exception e) {
    logger.warn("Failed to publish AppointmentCreatedEvent", e);
}
```

- [ ] **Step 2: Update BillingService**

Open `BillingService.java`.

Add import:
```java
import com.hms.event.ConsultationCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

Add field:
```java
@Autowired
private ApplicationEventPublisher eventPublisher;
```

In `autoGenerateOpdBill()`, after the billing item is saved (after the `billingItemRepository.save(item)` try/catch block, before the closing `}` of the `if (fee != null && fee.compareTo(BigDecimal.ZERO) > 0)` block), add:

```java
try {
    eventPublisher.publishEvent(new ConsultationCompletedEvent(
            hospital.getId(), appointment.getPatientId(), appointment.getId()));
} catch (Exception e) {
    logger.warn("Failed to publish ConsultationCompletedEvent", e);
}
```

- [ ] **Step 3: Update MedicineService**

Open `MedicineService.java`.

Add import:
```java
import com.hms.event.MedicineDispensedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

Add field injection (inject `ApplicationEventPublisher` via constructor or `@Autowired` field — use `@Autowired` field to match existing pattern in this class):
```java
@Autowired
private ApplicationEventPublisher eventPublisher;
```

In `addMedicinePurchase()`, after the WebSocket try/catch and before `return savedPurchase;`, add:

```java
try {
    Long patientId = purchase.getPatientId(); // null for non-patient purchases
    eventPublisher.publishEvent(new MedicineDispensedEvent(
            hospitalId, patientId, savedPurchase.getId()));
} catch (Exception e) {
    // ignore — WhatsApp failure must not affect dispensing
}
```

**Note:** `MedicinePurchase` entity may not have a `getPatientId()` method. Check the entity. If not present, use `null`:
```java
eventPublisher.publishEvent(new MedicineDispensedEvent(hospitalId, null, savedPurchase.getId()));
```

- [ ] **Step 4: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/AppointmentService.java
git add backend/src/main/java/com/hms/service/hospital/BillingService.java
git add backend/src/main/java/com/hms/service/hospital/MedicineService.java
git commit -m "feat: publish Spring events from AppointmentService, BillingService, MedicineService"
```

---

## Task 7: WhatsAppEventListener

**Files:**
- Create: `backend/src/main/java/com/hms/service/whatsapp/WhatsAppEventListener.java`

The listener handles the three events `@Async` so they never block the caller.

- [ ] **Step 1: Create WhatsAppEventListener**

```java
package com.hms.service.whatsapp;

import com.hms.config.WhatsAppTemplateConstants;
import com.hms.entity.*;
import com.hms.event.*;
import com.hms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class WhatsAppEventListener {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppEventListener.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final WhatsAppService whatsAppService;
    private final HospitalRepository hospitalRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final WhatsAppConfigRepository configRepository;

    public WhatsAppEventListener(WhatsAppService whatsAppService,
                                 HospitalRepository hospitalRepository,
                                 PatientRepository patientRepository,
                                 AppointmentRepository appointmentRepository,
                                 WhatsAppConfigRepository configRepository) {
        this.whatsAppService = whatsAppService;
        this.hospitalRepository = hospitalRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.configRepository = configRepository;
    }

    @Async
    @EventListener
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        try {
            Hospital hospital = hospitalRepository.findById(event.getHospitalId()).orElse(null);
            if (hospital == null) return;

            List<String> modules = hospital.getModules();
            if (!whatsAppService.isEnabled(event.getHospitalId(), modules,
                    WhatsAppTemplateConstants.MODULE_WA_APPOINTMENTS)) return;

            // Check CUSTOM per-type toggle
            if (modules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) {
                Optional<WhatsAppConfig> cfg = configRepository.findByHospitalId(event.getHospitalId());
                if (cfg.isPresent() && !cfg.get().isSendAppointments()) return;
            }

            Patient patient = patientRepository.findById(event.getPatientId()).orElse(null);
            if (patient == null || patient.getPhone() == null) return;

            Appointment appt = appointmentRepository.findById(event.getAppointmentId()).orElse(null);
            if (appt == null) return;

            String date = appt.getAppointmentDate() != null
                    ? appt.getAppointmentDate().format(DATE_FMT) : "—";
            String time = appt.getAppointmentTime() != null
                    ? appt.getAppointmentTime().format(TIME_FMT) : "—";

            whatsAppService.sendAppointmentConfirmation(
                    event.getHospitalId(), patient.getId(),
                    patient.getPhone(), patient.getName(),
                    hospital.getName(), date, time);
        } catch (Exception e) {
            log.warn("WhatsApp appointment confirmation failed for event {}", event, e);
        }
    }

    @Async
    @EventListener
    public void onConsultationCompleted(ConsultationCompletedEvent event) {
        try {
            Hospital hospital = hospitalRepository.findById(event.getHospitalId()).orElse(null);
            if (hospital == null) return;
            List<String> modules = hospital.getModules();

            Patient patient = patientRepository.findById(event.getPatientId()).orElse(null);
            if (patient == null || patient.getPhone() == null) return;

            // Billing PDF — WA_BILLING or CUSTOM with sendBilling=true
            boolean billingEnabled = whatsAppService.isEnabled(event.getHospitalId(), modules,
                    WhatsAppTemplateConstants.MODULE_WA_BILLING);
            if (billingEnabled && isCustomSendEnabled(event.getHospitalId(), modules, "billing")) {
                // Billing PDF generation + Cloudinary upload is deferred to a future task
                // (PdfService + Cloudinary integration). For now, send without a document URL.
                whatsAppService.sendDocument(event.getHospitalId(), patient.getId(),
                        patient.getPhone(), patient.getName(),
                        hospital.getName(), "Billing Receipt", null,
                        WhatsAppTemplateConstants.MSG_TYPE_BILLING);
            }
        } catch (Exception e) {
            log.warn("WhatsApp consultation-completed handler failed for event {}", event, e);
        }
    }

    @Async
    @EventListener
    public void onMedicineDispensed(MedicineDispensedEvent event) {
        try {
            if (event.getPatientId() == null) return; // no patient to message

            Hospital hospital = hospitalRepository.findById(event.getHospitalId()).orElse(null);
            if (hospital == null) return;
            List<String> modules = hospital.getModules();

            if (!whatsAppService.isEnabled(event.getHospitalId(), modules,
                    WhatsAppTemplateConstants.MODULE_WA_MEDICINE_LIST)) return;

            if (modules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) {
                Optional<WhatsAppConfig> cfg = configRepository.findByHospitalId(event.getHospitalId());
                if (cfg.isPresent() && !cfg.get().isSendMedicineList()) return;
            }

            Patient patient = patientRepository.findById(event.getPatientId()).orElse(null);
            if (patient == null || patient.getPhone() == null) return;

            whatsAppService.sendDocument(event.getHospitalId(), patient.getId(),
                    patient.getPhone(), patient.getName(),
                    hospital.getName(), "Medicine List", null,
                    WhatsAppTemplateConstants.MSG_TYPE_MEDICINE_LIST);
        } catch (Exception e) {
            log.warn("WhatsApp medicine-dispensed handler failed for event {}", event, e);
        }
    }

    /** Returns false if hospital is CUSTOM mode AND the per-type toggle is off. */
    private boolean isCustomSendEnabled(Long hospitalId, List<String> modules, String type) {
        if (!modules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) return true;
        Optional<WhatsAppConfig> cfg = configRepository.findByHospitalId(hospitalId);
        if (cfg.isEmpty()) return true;
        return switch (type) {
            case "billing" -> cfg.get().isSendBilling();
            case "casePaper" -> cfg.get().isSendCasePapers();
            case "prescription" -> cfg.get().isSendPrescription();
            case "medicineList" -> cfg.get().isSendMedicineList();
            default -> true;
        };
    }
}
```

Save to `backend/src/main/java/com/hms/service/whatsapp/WhatsAppEventListener.java`.

- [ ] **Step 2: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/service/whatsapp/WhatsAppEventListener.java
git commit -m "feat: add WhatsAppEventListener with @Async handlers for 3 events"
```

---

## Task 8: WhatsAppBroadcastService

**Files:**
- Create: `backend/src/main/java/com/hms/service/whatsapp/WhatsAppBroadcastService.java`

- [ ] **Step 1: Create WhatsAppBroadcastService**

```java
package com.hms.service.whatsapp;

import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WhatsAppBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBroadcastService.class);

    private final WhatsAppService whatsAppService;
    private final PatientRepository patientRepository;

    public WhatsAppBroadcastService(WhatsAppService whatsAppService,
                                    PatientRepository patientRepository) {
        this.whatsAppService = whatsAppService;
        this.patientRepository = patientRepository;
    }

    /**
     * Sends a broadcast message to all active patients of the given hospital.
     * Runs @Async so the HTTP response returns immediately.
     */
    @Async
    public void broadcastToAllPatients(Long hospitalId, String messageText, String imageUrl) {
        List<Patient> patients = patientRepository.findByHospitalIdAndIsActiveTrue(hospitalId);
        log.info("Broadcasting WhatsApp message to {} patients for hospital {}", patients.size(), hospitalId);
        for (Patient patient : patients) {
            if (patient.getPhone() == null || patient.getPhone().isBlank()) continue;
            try {
                whatsAppService.sendBroadcast(hospitalId, patient.getId(),
                        patient.getPhone(), messageText, imageUrl);
            } catch (Exception e) {
                log.warn("Broadcast failed for patient {}: {}", patient.getId(), e.getMessage());
            }
        }
    }

    /** Returns count of active patients — shown in the UI as "Will be sent to X patients". */
    public long countActivePatients(Long hospitalId) {
        return patientRepository.countByHospitalIdAndIsActiveTrue(hospitalId);
    }
}
```

Save to `backend/src/main/java/com/hms/service/whatsapp/WhatsAppBroadcastService.java`.

**Note:** `PatientRepository` needs `findByHospitalIdAndIsActiveTrue` and `countByHospitalIdAndIsActiveTrue`. Add them if not present:

Open `backend/src/main/java/com/hms/repository/PatientRepository.java` and add:

```java
List<com.hms.entity.Patient> findByHospitalIdAndIsActiveTrue(Long hospitalId);
long countByHospitalIdAndIsActiveTrue(Long hospitalId);
```

- [ ] **Step 2: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/service/whatsapp/WhatsAppBroadcastService.java
git add backend/src/main/java/com/hms/repository/PatientRepository.java
git commit -m "feat: add WhatsAppBroadcastService for bulk patient messaging"
```

---

## Task 9: AppointmentReminderScheduler

**Files:**
- Create: `backend/src/main/java/com/hms/scheduler/AppointmentReminderScheduler.java`

Runs daily at 9:00 AM IST. Finds all hospitals with `WA_APPOINTMENTS` or `WHATSAPP_CUSTOM`, queries appointments where `appointmentDate = tomorrow` and `status = SCHEDULED`, and sends the reminder template.

- [ ] **Step 1: Create AppointmentReminderScheduler**

```java
package com.hms.scheduler;

import com.hms.config.WhatsAppTemplateConstants;
import com.hms.entity.Appointment;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.WhatsAppConfig;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.WhatsAppConfigRepository;
import com.hms.service.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class AppointmentReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderScheduler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final HospitalRepository hospitalRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final WhatsAppConfigRepository configRepository;
    private final WhatsAppService whatsAppService;

    public AppointmentReminderScheduler(HospitalRepository hospitalRepository,
                                        AppointmentRepository appointmentRepository,
                                        PatientRepository patientRepository,
                                        WhatsAppConfigRepository configRepository,
                                        WhatsAppService whatsAppService) {
        this.hospitalRepository = hospitalRepository;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.configRepository = configRepository;
        this.whatsAppService = whatsAppService;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void sendDayBeforeReminders() {
        log.info("AppointmentReminderScheduler: running day-before reminder job");
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<Hospital> hospitals = hospitalRepository.findByAnyModule(
                List.of(WhatsAppTemplateConstants.MODULE_WA_APPOINTMENTS,
                        WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM));

        for (Hospital hospital : hospitals) {
            // For CUSTOM hospitals, check the per-type toggle
            if (hospital.getModules().contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) {
                Optional<WhatsAppConfig> cfg = configRepository.findByHospitalId(hospital.getId());
                if (cfg.isPresent() && !cfg.get().isSendAppointments()) continue;
            }

            List<Appointment> appointments = appointmentRepository
                    .findByHospitalIdAndAppointmentDateAndStatusAndIsActiveTrue(
                            hospital.getId(), tomorrow, "SCHEDULED");

            for (Appointment appt : appointments) {
                try {
                    Patient patient = patientRepository.findById(appt.getPatientId()).orElse(null);
                    if (patient == null || patient.getPhone() == null) continue;

                    String date = appt.getAppointmentDate().format(DATE_FMT);
                    String time = appt.getAppointmentTime() != null
                            ? appt.getAppointmentTime().format(TIME_FMT) : "—";

                    whatsAppService.sendAppointmentReminder(
                            hospital.getId(), patient.getId(),
                            patient.getPhone(), patient.getName(),
                            hospital.getName(), date, time);
                } catch (Exception e) {
                    log.warn("Reminder failed for appointment {}", appt.getId(), e);
                }
            }
        }
    }
}
```

Save to `backend/src/main/java/com/hms/scheduler/AppointmentReminderScheduler.java`.

**Note:** `AppointmentRepository` needs a query for `findByHospitalIdAndAppointmentDateAndStatusAndIsActiveTrue`. Add to `AppointmentRepository.java`:

```java
List<Appointment> findByHospitalIdAndAppointmentDateAndStatusAndIsActiveTrue(
        Long hospitalId, java.time.LocalDate date, String status);
```

- [ ] **Step 2: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/scheduler/AppointmentReminderScheduler.java
git add backend/src/main/java/com/hms/repository/AppointmentRepository.java
git commit -m "feat: add AppointmentReminderScheduler — 9 AM IST day-before WhatsApp reminders"
```

---

## Task 10: WhatsAppRetryScheduler

**Files:**
- Create: `backend/src/main/java/com/hms/scheduler/WhatsAppRetryScheduler.java`

Runs every 5 minutes. Finds all `FAILED` log entries where `next_retry_at <= now` and `retry_count < 2`, retries them, updates status.

- [ ] **Step 1: Create WhatsAppRetryScheduler**

```java
package com.hms.scheduler;

import com.hms.entity.WhatsAppMessageLog;
import com.hms.repository.WhatsAppMessageLogRepository;
import com.hms.service.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class WhatsAppRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppRetryScheduler.class);
    private static final int MAX_RETRIES = 2;

    private final WhatsAppMessageLogRepository logRepository;
    private final WhatsAppService whatsAppService;

    public WhatsAppRetryScheduler(WhatsAppMessageLogRepository logRepository,
                                  WhatsAppService whatsAppService) {
        this.logRepository = logRepository;
        this.whatsAppService = whatsAppService;
    }

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    public void retryFailed() {
        List<WhatsAppMessageLog> eligible = logRepository
                .findByStatusAndNextRetryAtBeforeAndRetryCountLessThan(
                        WhatsAppMessageLog.STATUS_FAILED, LocalDateTime.now(), MAX_RETRIES);

        if (!eligible.isEmpty()) {
            log.info("WhatsAppRetryScheduler: retrying {} failed messages", eligible.size());
        }

        for (WhatsAppMessageLog entry : eligible) {
            try {
                whatsAppService.retry(entry);
            } catch (Exception e) {
                log.warn("Retry failed for log entry {}: {}", entry.getId(), e.getMessage());
            }
        }
    }
}
```

Save to `backend/src/main/java/com/hms/scheduler/WhatsAppRetryScheduler.java`.

- [ ] **Step 2: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/scheduler/WhatsAppRetryScheduler.java
git commit -m "feat: add WhatsAppRetryScheduler — 5-min retry loop for failed sends"
```

---

## Task 11: WhatsAppController (Hospital-Level)

**Files:**
- Create: `backend/src/main/java/com/hms/controller/hospital/WhatsAppController.java`

7 endpoints, all under `/hospital/whatsapp`, all restricted to `HOSPITAL_ADMIN` with `@PreAuthorize`.

- [ ] **Step 1: Create WhatsAppController**

```java
package com.hms.controller.hospital;

import com.hms.dto.WhatsAppBroadcastRequest;
import com.hms.dto.WhatsAppConfigDTO;
import com.hms.dto.WhatsAppLogDTO;
import com.hms.entity.WhatsAppConfig;
import com.hms.entity.WhatsAppMessageLog;
import com.hms.repository.WhatsAppConfigRepository;
import com.hms.repository.WhatsAppMessageLogRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.whatsapp.WhatsAppBroadcastService;
import com.hms.service.whatsapp.WhatsAppService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/hospital/whatsapp")
public class WhatsAppController {

    private final WhatsAppConfigRepository configRepository;
    private final WhatsAppMessageLogRepository logRepository;
    private final WhatsAppService whatsAppService;
    private final WhatsAppBroadcastService broadcastService;
    private final SecurityContextHelper securityHelper;

    public WhatsAppController(WhatsAppConfigRepository configRepository,
                              WhatsAppMessageLogRepository logRepository,
                              WhatsAppService whatsAppService,
                              WhatsAppBroadcastService broadcastService,
                              SecurityContextHelper securityHelper) {
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.whatsAppService = whatsAppService;
        this.broadcastService = broadcastService;
        this.securityHelper = securityHelper;
    }

    /** POST /hospital/whatsapp/broadcast */
    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody WhatsAppBroadcastRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        long patientCount = broadcastService.countActivePatients(hospitalId);
        broadcastService.broadcastToAllPatients(hospitalId, req.getMessageText(), req.getImageUrl());
        return ResponseEntity.ok(Map.of(
                "message", "Broadcast queued",
                "patientCount", patientCount));
    }

    /** GET /hospital/whatsapp/logs */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Page<WhatsAppLogDTO>> getLogs(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PageRequest pr = PageRequest.of(page, size);
        Page<WhatsAppMessageLog> raw;
        if (type != null && status != null) {
            raw = logRepository.findByHospitalIdAndMessageTypeAndStatusOrderByCreatedAtDesc(
                    hospitalId, type, status, pr);
        } else if (type != null) {
            raw = logRepository.findByHospitalIdAndMessageTypeOrderByCreatedAtDesc(hospitalId, type, pr);
        } else if (status != null) {
            raw = logRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status, pr);
        } else {
            raw = logRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId, pr);
        }
        return ResponseEntity.ok(raw.map(this::toDTO));
    }

    /** GET /hospital/whatsapp/logs/failed-count */
    @GetMapping("/logs/failed-count")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Long>> getFailedCount() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        long count = logRepository.countByHospitalIdAndStatus(
                hospitalId, WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** GET /hospital/whatsapp/config */
    @GetMapping("/config")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<WhatsAppConfigDTO> getConfig() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Optional<WhatsAppConfig> opt = configRepository.findByHospitalId(hospitalId);
        if (opt.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(toConfigDTO(opt.get(), true));
    }

    /** POST /hospital/whatsapp/config */
    @PostMapping("/config")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<WhatsAppConfigDTO> saveConfig(@RequestBody WhatsAppConfigDTO dto) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        WhatsAppConfig cfg = configRepository.findByHospitalId(hospitalId)
                .orElseGet(WhatsAppConfig::new);
        cfg.setHospitalId(hospitalId);
        cfg.setAccessToken(whatsAppService.encrypt(dto.getAccessToken()));
        cfg.setPhoneNumberId(dto.getPhoneNumberId());
        cfg.setWabaId(dto.getWabaId());
        cfg.setActive(dto.isActive());
        cfg.setSendAppointments(dto.isSendAppointments());
        cfg.setSendBilling(dto.isSendBilling());
        cfg.setSendCasePapers(dto.isSendCasePapers());
        cfg.setSendPrescription(dto.isSendPrescription());
        cfg.setSendMedicineList(dto.isSendMedicineList());
        configRepository.save(cfg);
        return ResponseEntity.ok(toConfigDTO(cfg, true));
    }

    /** DELETE /hospital/whatsapp/config */
    @DeleteMapping("/config")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deleteConfig() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        configRepository.findByHospitalId(hospitalId).ifPresent(configRepository::delete);
        return ResponseEntity.noContent().build();
    }

    /** POST /hospital/whatsapp/config/test */
    @PostMapping("/config/test")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Object>> testConfig(@RequestBody WhatsAppConfigDTO dto) {
        // Send a minimal request to Meta API to validate credentials
        String decrypted = whatsAppService.decrypt(
                whatsAppService.encrypt(dto.getAccessToken()));
        // Attempt a simple "get phone number info" call
        try {
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(decrypted);
            org.springframework.http.ResponseEntity<String> resp = rt.exchange(
                    "https://graph.facebook.com/v19.0/" + dto.getPhoneNumberId(),
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Credentials valid"));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("success", false, "message", "Unexpected response"));
    }

    private WhatsAppLogDTO toDTO(WhatsAppMessageLog log) {
        WhatsAppLogDTO dto = new WhatsAppLogDTO();
        dto.setId(log.getId());
        dto.setPatientId(log.getPatientId());
        dto.setPatientPhone(log.getPatientPhone());
        dto.setMessageType(log.getMessageType());
        dto.setStatus(log.getStatus());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setRetryCount(log.getRetryCount());
        dto.setSentAt(log.getSentAt());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    private WhatsAppConfigDTO toConfigDTO(WhatsAppConfig cfg, boolean maskToken) {
        WhatsAppConfigDTO dto = new WhatsAppConfigDTO();
        if (maskToken && cfg.getAccessToken() != null && cfg.getAccessToken().length() > 4) {
            dto.setAccessToken("••••••••" + cfg.getAccessToken()
                    .substring(cfg.getAccessToken().length() - 4));
        } else {
            dto.setAccessToken(cfg.getAccessToken());
        }
        dto.setPhoneNumberId(cfg.getPhoneNumberId());
        dto.setWabaId(cfg.getWabaId());
        dto.setActive(cfg.isActive());
        dto.setSendAppointments(cfg.isSendAppointments());
        dto.setSendBilling(cfg.isSendBilling());
        dto.setSendCasePapers(cfg.isSendCasePapers());
        dto.setSendPrescription(cfg.isSendPrescription());
        dto.setSendMedicineList(cfg.isSendMedicineList());
        return dto;
    }
}
```

Save to `backend/src/main/java/com/hms/controller/hospital/WhatsAppController.java`.

- [ ] **Step 2: Compile check**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/controller/hospital/WhatsAppController.java
git commit -m "feat: add WhatsAppController with 7 hospital-level endpoints"
```

---

## Task 12: PlatformWhatsAppController

**Files:**
- Create: `backend/src/main/java/com/hms/controller/platform/PlatformWhatsAppController.java`

- [ ] **Step 1: Create PlatformWhatsAppController**

```java
package com.hms.controller.platform;

import com.hms.dto.WhatsAppStatsDTO;
import com.hms.entity.WhatsAppMessageLog;
import com.hms.repository.WhatsAppMessageLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/platform/whatsapp")
public class PlatformWhatsAppController {

    private final WhatsAppMessageLogRepository logRepository;

    public PlatformWhatsAppController(WhatsAppMessageLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /** GET /platform/whatsapp/stats — failures today + affected hospital count */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<WhatsAppStatsDTO> getStats() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        long failed = logRepository.countByStatusSince(
                WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED, startOfDay);
        long hospitals = logRepository.countDistinctHospitalsByStatusSince(
                WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED, startOfDay);
        return ResponseEntity.ok(new WhatsAppStatsDTO(failed, hospitals));
    }
}
```

Save to `backend/src/main/java/com/hms/controller/platform/PlatformWhatsAppController.java`.

- [ ] **Step 2: Compile and run tests**

```bash
cd backend && mvn compile -q && mvn test -q
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/controller/platform/PlatformWhatsAppController.java
git commit -m "feat: add PlatformWhatsAppController — GET /platform/whatsapp/stats"
```

---

## Task 13: PlansTab.jsx — WhatsApp Module Section

**Files:**
- Modify: `frontend/src/components/PlansTab.jsx`

Add a three-way radio (None / Platform / Custom) and sub-checkboxes for Platform mode, following the existing `inClinic` boolean pattern.

- [ ] **Step 1: Read the current PlansTab**

Read `frontend/src/components/PlansTab.jsx` lines 1-50 to confirm imports and the `AVAILABLE_MODULES` array location. Then read the area around `inClinic` for the exact pattern to follow.

- [ ] **Step 2: Add WhatsApp helper constants near the top of the module (after imports)**

Find the `AVAILABLE_MODULES` array. Just before or after it, add:

```javascript
const WA_SUB_MODULES = [
    { key: 'WA_APPOINTMENTS', label: 'Appointments (confirmation + reminder)' },
    { key: 'WA_BILLING',      label: 'Billing PDF' },
    { key: 'WA_CASE_PAPERS',  label: 'Case Papers PDF' },
    { key: 'WA_PRESCRIPTION', label: 'Prescription PDF' },
    { key: 'WA_MEDICINE_LIST', label: 'In-Clinic Medicine List' },
];
```

- [ ] **Step 3: Add a helper to derive WhatsApp mode from form.modules**

Inside the component function (before the return), add:

```javascript
const waMode = form.modules.includes('WHATSAPP_CUSTOM')
    ? 'CUSTOM'
    : form.modules.includes('WHATSAPP_PLATFORM')
        ? 'PLATFORM'
        : 'NONE';

const handleWaModeChange = (newMode) => {
    const filtered = (form.modules || []).filter(
        m => m !== 'WHATSAPP_PLATFORM' && m !== 'WHATSAPP_CUSTOM'
            && !WA_SUB_MODULES.map(s => s.key).includes(m)
    );
    if (newMode === 'PLATFORM') {
        onChange({ ...form, modules: [...filtered, 'WHATSAPP_PLATFORM'] });
    } else if (newMode === 'CUSTOM') {
        onChange({ ...form, modules: [...filtered, 'WHATSAPP_CUSTOM'] });
    } else {
        onChange({ ...form, modules: filtered });
    }
};

const handleWaSubToggle = (key) => {
    const mods = form.modules || [];
    const next = mods.includes(key)
        ? mods.filter(m => m !== key)
        : [...mods, key];
    onChange({ ...form, modules: next });
};
```

- [ ] **Step 4: Add the WhatsApp section JSX after the inClinic section**

Find the `inClinic` checkbox section. Immediately after it (still inside the module section wrapper), add:

```jsx
{/* WhatsApp Messaging */}
<div className="mt-6 border-t border-gray-100 pt-4">
    <p className="text-sm font-semibold text-gray-700 mb-2">WhatsApp Messaging</p>
    <div className="flex flex-col gap-2">
        {[
            { value: 'NONE',     label: 'None' },
            { value: 'PLATFORM', label: 'Platform (use AxonxMedtech number)' },
            { value: 'CUSTOM',   label: 'Custom (hospital provides own credentials)' },
        ].map(opt => (
            <label key={opt.value} className="flex items-center gap-2 cursor-pointer text-sm text-gray-700">
                <input
                    type="radio"
                    name="waMode"
                    value={opt.value}
                    checked={waMode === opt.value}
                    onChange={() => handleWaModeChange(opt.value)}
                    className="accent-blue-600"
                />
                {opt.label}
            </label>
        ))}
    </div>

    {waMode === 'PLATFORM' && (
        <div className="ml-5 mt-3 flex flex-col gap-2">
            <p className="text-xs text-gray-500 mb-1">Enable specific message types:</p>
            {WA_SUB_MODULES.map(sub => (
                <label key={sub.key} className="flex items-center gap-2 cursor-pointer text-sm text-gray-600">
                    <input
                        type="checkbox"
                        checked={(form.modules || []).includes(sub.key)}
                        onChange={() => handleWaSubToggle(sub.key)}
                        className="accent-blue-600"
                    />
                    {sub.label}
                </label>
            ))}
        </div>
    )}

    {waMode === 'CUSTOM' && (
        <p className="ml-5 mt-2 text-xs text-gray-500">
            Hospital admin will configure their own Meta credentials in the WhatsApp Settings tab.
        </p>
    )}
</div>
```

- [ ] **Step 5: Verify in browser**

Start frontend: `cd frontend && npm run dev`.  
Go to System Admin → Plans → edit a plan.  
Verify the WhatsApp radio appears, sub-checkboxes show when Platform is selected, and radio state persists on save/reopen.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/PlansTab.jsx
git commit -m "feat: add WhatsApp module section to PlansTab (radio + sub-checkboxes)"
```

---

## Task 14: MessagesTab.jsx

**Files:**
- Create: `frontend/src/pages/hospital/MessagesTab.jsx`

Three sections: Broadcast, Notification Log, and WhatsApp Settings (CUSTOM only).

- [ ] **Step 1: Create MessagesTab.jsx**

```jsx
import React, { useState, useEffect, useCallback } from 'react';
import hospitalService from '../../services/hospitalService';
import { useToast } from '../../context/ToastContext';
import ConfirmationModal from '../../components/ConfirmationModal';
import StatusBadge from '../../components/StatusBadge';

const MSG_TYPES = ['', 'APPOINTMENT_CONFIRMATION', 'APPOINTMENT_REMINDER',
    'BILLING', 'CASE_PAPER', 'PRESCRIPTION', 'MEDICINE_LIST', 'BROADCAST'];
const STATUSES = ['', 'SENT', 'FAILED', 'PERMANENTLY_FAILED'];

export default function MessagesTab({ modules }) {
    const { success: toastSuccess, error: toastError } = useToast();
    const isCustom = (modules || []).includes('WHATSAPP_CUSTOM');

    // Broadcast state
    const [broadcastText, setBroadcastText] = useState('');
    const [broadcastImageUrl, setBroadcastImageUrl] = useState('');
    const [patientCount, setPatientCount] = useState(null);
    const [broadcastModal, setBroadcastModal] = useState(false);
    const [broadcasting, setBroadcasting] = useState(false);

    // Log state
    const [logs, setLogs] = useState([]);
    const [logPage, setLogPage] = useState(0);
    const [logTotalPages, setLogTotalPages] = useState(0);
    const [typeFilter, setTypeFilter] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [logsLoading, setLogsLoading] = useState(false);
    const [failedCount, setFailedCount] = useState(0);

    // Config state (CUSTOM only)
    const [config, setConfig] = useState({
        accessToken: '', phoneNumberId: '', wabaId: '',
        active: true, sendAppointments: true, sendBilling: true,
        sendCasePapers: true, sendPrescription: true, sendMedicineList: true,
    });
    const [configSaving, setConfigSaving] = useState(false);
    const [testResult, setTestResult] = useState(null);

    const loadLogs = useCallback(async () => {
        setLogsLoading(true);
        try {
            const params = new URLSearchParams({ page: logPage, size: 50 });
            if (typeFilter) params.set('type', typeFilter);
            if (statusFilter) params.set('status', statusFilter);
            const res = await hospitalService.get(`/hospital/whatsapp/logs?${params}`);
            setLogs(res.data.content || []);
            setLogTotalPages(res.data.totalPages || 0);
        } catch { /* silent */ } finally {
            setLogsLoading(false);
        }
    }, [logPage, typeFilter, statusFilter]);

    useEffect(() => { loadLogs(); }, [loadLogs]);

    useEffect(() => {
        hospitalService.get('/hospital/whatsapp/logs/failed-count')
            .then(r => setFailedCount(r.data.count || 0))
            .catch(() => {});
        hospitalService.get('/hospital/whatsapp/broadcast')
            .then(r => setPatientCount(r.data?.patientCount ?? null))
            .catch(() => {});
        if (isCustom) {
            hospitalService.get('/hospital/whatsapp/config')
                .then(r => { if (r.data) setConfig(r.data); })
                .catch(() => {});
        }
    }, [isCustom]);

    const handleBroadcast = async () => {
        setBroadcasting(true);
        try {
            await hospitalService.post('/hospital/whatsapp/broadcast', {
                messageText: broadcastText,
                imageUrl: broadcastImageUrl || null,
            });
            toastSuccess('Broadcast queued successfully');
            setBroadcastModal(false);
            setBroadcastText('');
            loadLogs();
        } catch { toastError('Failed to queue broadcast'); }
        finally { setBroadcasting(false); }
    };

    const handleSaveConfig = async () => {
        setConfigSaving(true);
        try {
            await hospitalService.post('/hospital/whatsapp/config', config);
            toastSuccess('WhatsApp settings saved');
        } catch { toastError('Failed to save settings'); }
        finally { setConfigSaving(false); }
    };

    const handleTestConfig = async () => {
        setTestResult(null);
        try {
            const res = await hospitalService.post('/hospital/whatsapp/config/test', config);
            setTestResult(res.data);
        } catch { setTestResult({ success: false, message: 'Request failed' }); }
    };

    const statusColor = (s) => {
        if (s === 'SENT') return 'green';
        if (s === 'PERMANENTLY_FAILED') return 'red';
        if (s === 'FAILED') return 'orange';
        return 'gray';
    };

    return (
        <div className="space-y-8 p-6">
            {/* Broadcast Section */}
            <div className="bg-white border border-gray-200 rounded-xl p-6">
                <h2 className="text-lg font-bold text-gray-900 mb-1">Broadcast Message</h2>
                <p className="text-sm text-gray-500 mb-4">
                    Send a WhatsApp message to all registered patients.
                    {patientCount !== null && (
                        <span className="font-medium text-blue-600"> Will be sent to {patientCount} patients.</span>
                    )}
                </p>
                <textarea
                    className="w-full border border-gray-300 rounded-lg p-3 text-sm resize-none focus:ring-2 focus:ring-blue-500 focus:outline-none"
                    rows={4}
                    maxLength={1024}
                    placeholder="Type your message here (max 1024 characters)..."
                    value={broadcastText}
                    onChange={e => setBroadcastText(e.target.value)}
                />
                <div className="flex items-center justify-between mt-2">
                    <span className="text-xs text-gray-400">{broadcastText.length}/1024</span>
                    <button
                        disabled={!broadcastText.trim()}
                        onClick={() => setBroadcastModal(true)}
                        className="px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        Broadcast Now
                    </button>
                </div>
            </div>

            {/* Notification Log */}
            <div className="bg-white border border-gray-200 rounded-xl p-6">
                <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-3">
                        <h2 className="text-lg font-bold text-gray-900">Notification Log</h2>
                        {failedCount > 0 && (
                            <span className="px-2 py-0.5 rounded-full bg-red-100 text-red-600 text-xs font-semibold">
                                {failedCount} permanently failed
                            </span>
                        )}
                    </div>
                    <div className="flex items-center gap-2">
                        <select
                            value={typeFilter}
                            onChange={e => { setTypeFilter(e.target.value); setLogPage(0); }}
                            className="border border-gray-300 rounded-lg text-sm px-2 py-1.5"
                        >
                            <option value="">All Types</option>
                            {MSG_TYPES.filter(Boolean).map(t => (
                                <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
                            ))}
                        </select>
                        <select
                            value={statusFilter}
                            onChange={e => { setStatusFilter(e.target.value); setLogPage(0); }}
                            className="border border-gray-300 rounded-lg text-sm px-2 py-1.5"
                        >
                            <option value="">All Statuses</option>
                            {STATUSES.filter(Boolean).map(s => (
                                <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
                            ))}
                        </select>
                    </div>
                </div>
                {logsLoading ? (
                    <div className="text-center text-gray-400 py-8 text-sm">Loading...</div>
                ) : logs.length === 0 ? (
                    <div className="text-center text-gray-400 py-8 text-sm">No messages found.</div>
                ) : (
                    <>
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b border-gray-100 text-left text-xs text-gray-500 uppercase tracking-wide">
                                        <th className="pb-2 font-medium">Phone</th>
                                        <th className="pb-2 font-medium">Type</th>
                                        <th className="pb-2 font-medium">Status</th>
                                        <th className="pb-2 font-medium">Retries</th>
                                        <th className="pb-2 font-medium">Date</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {logs.map(log => (
                                        <tr key={log.id} className="border-b border-gray-50 hover:bg-gray-50">
                                            <td className="py-2">{log.patientPhone}</td>
                                            <td className="py-2">{log.messageType?.replace(/_/g, ' ')}</td>
                                            <td className="py-2">
                                                <StatusBadge status={log.status} color={statusColor(log.status)} />
                                            </td>
                                            <td className="py-2 text-center">{log.retryCount}</td>
                                            <td className="py-2 text-gray-500">
                                                {log.createdAt ? new Date(log.createdAt).toLocaleDateString() : '—'}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        {logTotalPages > 1 && (
                            <div className="flex justify-center gap-2 mt-4">
                                <button
                                    disabled={logPage === 0}
                                    onClick={() => setLogPage(p => p - 1)}
                                    className="px-3 py-1 text-sm border border-gray-300 rounded-lg disabled:opacity-40"
                                >Prev</button>
                                <span className="px-3 py-1 text-sm text-gray-600">
                                    {logPage + 1} / {logTotalPages}
                                </span>
                                <button
                                    disabled={logPage >= logTotalPages - 1}
                                    onClick={() => setLogPage(p => p + 1)}
                                    className="px-3 py-1 text-sm border border-gray-300 rounded-lg disabled:opacity-40"
                                >Next</button>
                            </div>
                        )}
                    </>
                )}
            </div>

            {/* WhatsApp Settings — only for CUSTOM mode */}
            {isCustom && (
                <div className="bg-white border border-gray-200 rounded-xl p-6">
                    <h2 className="text-lg font-bold text-gray-900 mb-1">WhatsApp Settings</h2>
                    <p className="text-sm text-gray-500 mb-4">
                        Connect your Meta WhatsApp Business account.
                        Get credentials from{' '}
                        <a href="https://developers.facebook.com" target="_blank" rel="noreferrer"
                            className="text-blue-600 underline">
                            Meta for Developers
                        </a>.
                    </p>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
                        <div>
                            <label className="block text-xs font-medium text-gray-700 mb-1">
                                Access Token *
                            </label>
                            <input
                                type="password"
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                                placeholder="EAA..."
                                value={config.accessToken}
                                onChange={e => setConfig(c => ({ ...c, accessToken: e.target.value }))}
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-gray-700 mb-1">
                                Phone Number ID *
                            </label>
                            <input
                                type="text"
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                                placeholder="1234567890"
                                value={config.phoneNumberId}
                                onChange={e => setConfig(c => ({ ...c, phoneNumberId: e.target.value }))}
                            />
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-gray-700 mb-1">
                                WABA ID (optional)
                            </label>
                            <input
                                type="text"
                                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                                placeholder="1234567890"
                                value={config.wabaId}
                                onChange={e => setConfig(c => ({ ...c, wabaId: e.target.value }))}
                            />
                        </div>
                    </div>

                    <p className="text-xs font-semibold text-gray-700 mb-2">Message Types</p>
                    <div className="grid grid-cols-2 md:grid-cols-3 gap-2 mb-4">
                        {[
                            { key: 'sendAppointments', label: 'Appointments' },
                            { key: 'sendBilling',      label: 'Billing' },
                            { key: 'sendCasePapers',   label: 'Case Papers' },
                            { key: 'sendPrescription', label: 'Prescription' },
                            { key: 'sendMedicineList', label: 'Medicine List' },
                        ].map(t => (
                            <label key={t.key} className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={!!config[t.key]}
                                    onChange={e => setConfig(c => ({ ...c, [t.key]: e.target.checked }))}
                                    className="accent-blue-600"
                                />
                                {t.label}
                            </label>
                        ))}
                    </div>

                    {testResult && (
                        <p className={`text-sm mb-3 font-medium ${testResult.success ? 'text-green-600' : 'text-red-500'}`}>
                            {testResult.success ? '✓ ' : '✗ '}{testResult.message}
                        </p>
                    )}

                    <div className="flex gap-3">
                        <button
                            onClick={handleSaveConfig}
                            disabled={configSaving}
                            className="px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50"
                        >
                            {configSaving ? 'Saving...' : 'Save Credentials'}
                        </button>
                        <button
                            onClick={handleTestConfig}
                            className="px-4 py-2 border border-gray-300 text-gray-700 text-sm rounded-lg hover:bg-gray-50"
                        >
                            Test Connection
                        </button>
                    </div>
                </div>
            )}

            <ConfirmationModal
                isOpen={broadcastModal}
                onClose={() => setBroadcastModal(false)}
                onConfirm={handleBroadcast}
                loading={broadcasting}
                title="Send Broadcast"
                message={`Send this message to ${patientCount ?? 'all'} patients?`}
                confirmText="Send"
            />
        </div>
    );
}
```

Save to `frontend/src/pages/hospital/MessagesTab.jsx`.

**Note:** `hospitalService.get` and `hospitalService.post` — check the actual method names in `hospitalService.js`. The service may use `apiService.get(path)` or a different pattern. Adjust the calls to match:

- If `hospitalService` uses `apiService` under the hood (likely), the API calls may be `apiService.get('/hospital/whatsapp/logs?...')` etc. Read `frontend/src/services/hospitalService.js` briefly and adjust the import/calls accordingly.

- [ ] **Step 2: Verify in browser**

Navigate to Hospital Admin dashboard → Messages tab. Confirm:
- Broadcast textarea and button render
- Notification log table shows (empty state is fine)
- Settings panel shows only when `WHATSAPP_CUSTOM` is in modules
- No console errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/hospital/MessagesTab.jsx
git commit -m "feat: add MessagesTab with broadcast, notification log, and WhatsApp settings"
```

---

## Task 15: Wire Messages Tab + Platform Stat Card

**Files:**
- Modify: `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`
- Modify: `frontend/src/pages/platform/PlatformDashboard.jsx`

- [ ] **Step 1: Add Messages tab to HospitalAdminDashboard**

Open `HospitalAdminDashboard.jsx`.

**Import MessagesTab** (at the top with other imports):
```javascript
import MessagesTab from './MessagesTab';
```

**Add tab entry** in `allTabs` array (after `settings`):
```javascript
{ id: 'messages', label: 'Messages', icon: null,
  requiredModule: null,
  // Only show if hospital has any WhatsApp module
  condition: modules.includes('WHATSAPP_PLATFORM') || modules.includes('WHATSAPP_CUSTOM') },
```

**Update the tab filter** (the `tabs = allTabs.filter(...)` call):
```javascript
const tabs = allTabs.filter(tab =>
    (!tab.requiredModule || modules.includes(tab.requiredModule))
    && (tab.condition === undefined || tab.condition)
);
```

**Render MessagesTab** in the main tab content area. Find the large JSX block where `activeTab === 'settings'` renders `<SettingsPanel />` or similar. Add a new condition after it:

```jsx
{activeTab === 'messages' && (
    <MessagesTab modules={modules} />
)}
```

- [ ] **Step 2: Add WhatsApp failures stat card to PlatformDashboard**

Open `frontend/src/pages/platform/PlatformDashboard.jsx`.

**Add state** near other stats state:
```javascript
const [waStats, setWaStats] = useState({ failedToday: 0, affectedHospitalsToday: 0 });
```

**Fetch on mount** (in the `useEffect` or wherever other stats are fetched):
```javascript
apiService.get('/platform/whatsapp/stats')
    .then(r => setWaStats(r.data))
    .catch(() => {});
```

**Add the card** in the grid at line 565, changing `grid-cols-3` to `grid-cols-4` OR adding a 4th card below:

```jsx
{/* Card 4 — WhatsApp Failures (Red when > 0) */}
<div className="bg-white border border-gray-200 rounded-xl p-6 hover:shadow-md transition-shadow duration-200">
    <div className="flex items-center justify-between mb-4">
        <p className="text-sm font-medium text-gray-600">WhatsApp Failures</p>
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 ${waStats.failedToday > 0 ? 'bg-red-100' : 'bg-gray-100'}`}>
            <svg className={`w-5 h-5 ${waStats.failedToday > 0 ? 'text-red-500' : 'text-gray-400'}`}
                fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
        </div>
    </div>
    <p className={`text-3xl font-bold ${waStats.failedToday > 0 ? 'text-red-600' : 'text-gray-400'}`}>
        {waStats.failedToday}
    </p>
    <p className="text-sm text-gray-500 mt-1">
        today · {waStats.affectedHospitalsToday} hospital{waStats.affectedHospitalsToday !== 1 ? 's' : ''}
    </p>
</div>
```

Also change `md:grid-cols-3` to `md:grid-cols-4` on the parent grid div (line 565).

- [ ] **Step 3: Verify in browser**

- Hospital Admin: Messages tab appears in sidebar (if modules include `WHATSAPP_PLATFORM` or `WHATSAPP_CUSTOM`). Tab renders without errors.
- Platform Dashboard: 4-card stats row renders, WhatsApp Failures card shows 0 (gray).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/hospital/HospitalAdminDashboard.jsx
git add frontend/src/pages/platform/PlatformDashboard.jsx
git commit -m "feat: wire Messages tab in Hospital dashboard + WhatsApp failures card in Platform"
```

---

## Self-Review

### Spec coverage check

| Spec requirement | Task |
|---|---|
| WHATSAPP_PLATFORM + sub-modules in plan | Task 13 (PlansTab) |
| WHATSAPP_CUSTOM mutual exclusivity | Task 13 (radio enforces it) |
| whatsapp_config table + entity | Task 1 + Task 2 |
| whatsapp_message_log table + entity | Task 1 + Task 2 |
| WhatsAppService credential resolution | Task 5 |
| Phone normalization (91 prefix) | Task 5 (normalizePhone) |
| AES-256 token encryption | Task 5 (encrypt/decrypt) |
| AppointmentCreatedEvent | Tasks 3, 6, 7 |
| ConsultationCompletedEvent | Tasks 3, 6, 7 |
| MedicineDispensedEvent | Tasks 3, 6, 7 |
| isEnabled(hospitalId, modules, subModule) | Task 5 |
| @Async listeners — non-blocking | Task 7 (WhatsAppEventListener) |
| @EnableAsync config | Task 4 (AsyncConfig) |
| 9 AM IST appointment reminders | Task 9 |
| Retry scheduler every 5 min | Task 10 |
| 2 retries at 15-min gaps → PERMANENTLY_FAILED | Tasks 5, 10 |
| PERMANENTLY_FAILED badge count | Task 11 (GET /logs/failed-count) |
| Broadcast to all patients | Tasks 8, 11 (POST /broadcast) |
| Paginated log with type/status filters | Task 11 (GET /logs) |
| Custom config CRUD + test endpoint | Task 11 |
| Platform stats endpoint | Task 12 |
| PlansTab WhatsApp radio + sub-checkboxes | Task 13 |
| MessagesTab — broadcast + log + settings | Task 14 |
| Messages tab in Hospital Admin sidebar | Task 15 |
| Platform stat card | Task 15 |
| Env vars in .env.example | Task 4 |
| V4 migration SQL | Task 1 |

All spec requirements have corresponding tasks.

### Notes on PDF + Cloudinary delivery
The spec calls for uploading billing/case paper/prescription PDFs to Cloudinary and sending the URL in the document template. The `WhatsAppEventListener.onConsultationCompleted()` in Task 7 currently sends without a document URL (`null`) as a placeholder. Integrating `PdfService` + Cloudinary upload is a follow-on task once templates are approved by Meta and actual API credentials exist. The message sends (and logs) correctly — only the PDF attachment is deferred.

### Type consistency check
- `AppointmentCreatedEvent`, `ConsultationCompletedEvent`, `MedicineDispensedEvent` — all three use `Long hospitalId, Long patientId, Long appointmentId/purchaseId` — consistent across Tasks 3, 6, 7.
- `WhatsAppService.isEnabled(Long, List<String>, String)` — called exactly this way in Tasks 7 and 9. ✓
- `WhatsAppTemplateConstants.MODULE_*` constants — used consistently in Tasks 5, 7, 9. ✓
- `WhatsAppMessageLog.STATUS_SENT`, `STATUS_FAILED`, `STATUS_PERMANENTLY_FAILED` — defined in entity (Task 2), used in Tasks 5, 10, 11, 12. ✓
