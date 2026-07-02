# Patient Portal — Login & Record Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a patient log into a self-service portal via mobile+OTP and view their own appointments, released lab/radiology reports, prescriptions, and billing status — all read-only.

**Architecture:** A new `patient_portal_user` identity (not the staff `users` table) authenticates via OTP against MSG91, reusing the existing `JwtUtil`/`JwtAuthenticationFilter` unchanged (confirmed claim-only, no DB lookup) by minting tokens with `role=PATIENT` and `userId=patient_portal_user.id`. A new `/hospital/portal/**` namespace (mirroring the existing `/platform/**` vs `/hospital/**` split) exposes OTP endpoints (public) and dashboard/record endpoints (`hasRole('PATIENT')`), every one scoped to the patient resolved from the JWT.

**Tech Stack:** Spring Boot, Spring Security, JPA/Hibernate, MySQL, JUnit 5 + Mockito + AssertJ, React 18, React Router 7, Axios.

---

## Plan-time refinements vs the design doc

Two adjustments made while grounding the spec against the actual codebase (both are simplifications, not scope changes):

1. **No custom JWT claim needed.** The design doc mentioned a `patientId` custom claim. Investigation confirmed `JwtAuthenticationFilter` builds its security context purely from existing claims (`userId`, `role`, `hospitalId`, `modules`) with no database re-lookup. So `JwtUtil` and `JwtAuthenticationFilter` need **zero changes** — tokens use the existing `generateToken(userId, email, role, hospitalId, modules)` signature with `userId = patient_portal_user.id`. Services resolve `patientId` with one extra repository lookup (`PatientPortalUserRepository.findById(currentUserId)`), which is no different in cost from any other service's existing patterns.
2. **No new PDF template for reports.** `LabResult`/`RadiologyResult` already have an optional `resultFileUrl` (uploaded PDF/image link) but no server-generated watermarked PDF pipeline exists anywhere in the codebase yet. Building one from scratch is out of scope for this pass. The `/reports` endpoint returns structured JSON (parameters/findings/impression + `resultFileUrl` if present); the frontend opens `resultFileUrl` in a new tab when present, otherwise renders the structured fields. A watermarked PDF generator is a good follow-up, not a blocker for read access.
3. **No separate `/reports/{id}/download` endpoint.** Since there's no server-generated PDF (see #2), "download" is just the frontend opening the stored `resultFileUrl` directly — there's no backend request to intercept. BR-4's audit trail is satisfied by logging the `GET /hospital/portal/reports` list access itself (which is what exposes `resultFileUrl` to the client) rather than a separate download event.

---

### Task 1: Database schema + entities + repositories

**Files:**
- Modify: `setup/schema-full.sql`
- Create: `backend/src/main/java/com/hms/entity/PatientPortalUser.java`
- Create: `backend/src/main/java/com/hms/entity/PortalOtp.java`
- Create: `backend/src/main/java/com/hms/repository/PatientPortalUserRepository.java`
- Create: `backend/src/main/java/com/hms/repository/PortalOtpRepository.java`

- [ ] **Step 1: Add the two new tables to `setup/schema-full.sql`**

Find the most recently added table block (search for `CREATE TABLE IF NOT EXISTS executive_alert`) and insert immediately after its closing `) ENGINE=InnoDB;` / index line, before the next `--` comment block:

```sql
CREATE TABLE IF NOT EXISTS patient_portal_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    mobile VARCHAR(15) NOT NULL,
    email VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,
    lock_until DATETIME NULL,
    last_login DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portal_user_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_portal_user_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT uq_portal_user_hospital_patient UNIQUE (hospital_id, patient_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS portal_otp (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    mobile VARCHAR(15) NOT NULL,
    otp_hash VARCHAR(100) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portal_otp_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_portal_otp_mobile ON portal_otp(hospital_id, mobile, consumed_at);
```

- [ ] **Step 2: Create `PatientPortalUser.java`**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Patient portal login identity (Form 40 phase 1) — one row per (hospital, patient),
 * created lazily on first OTP request. Deliberately separate from the staff {@link User}
 * table: patients authenticate via OTP only and have no password.
 */
@Entity
@Table(name = "patient_portal_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientPortalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "mobile", nullable = false, length = 15)
    private String mobile;

    @Column(name = "email", length = 100)
    private String email;

    // ACTIVE / LOCKED / SUSPENDED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Create `PortalOtp.java`**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Single-use, hashed, expiring OTP for patient portal login (Form 40 phase 1). */
@Entity
@Table(name = "portal_otp")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "mobile", nullable = false, length = 15)
    private String mobile;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    // LOGIN (only value used in this pass)
    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Create `PatientPortalUserRepository.java`**

```java
package com.hms.repository;

import com.hms.entity.PatientPortalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientPortalUserRepository extends JpaRepository<PatientPortalUser, Long> {
    Optional<PatientPortalUser> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);
}
```

- [ ] **Step 5: Create `PortalOtpRepository.java`**

```java
package com.hms.repository;

import com.hms.entity.PortalOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortalOtpRepository extends JpaRepository<PortalOtp, Long> {
    Optional<PortalOtp> findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(Long hospitalId, String mobile);
}
```

- [ ] **Step 6: Verify compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: exits 0, no errors.

- [ ] **Step 7: Commit**

```bash
git add setup/schema-full.sql backend/src/main/java/com/hms/entity/PatientPortalUser.java backend/src/main/java/com/hms/entity/PortalOtp.java backend/src/main/java/com/hms/repository/PatientPortalUserRepository.java backend/src/main/java/com/hms/repository/PortalOtpRepository.java
git commit -m "feat(portal): patient portal user + OTP schema and entities (Form 40 phase 1)"
```

---

### Task 2: Security wiring — PATIENT role, SecurityConfig matchers, rate limiting

**Files:**
- Modify: `backend/src/main/java/com/hms/security/UserRole.java`
- Modify: `backend/src/main/java/com/hms/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/hms/filter/RateLimitFilter.java`

- [ ] **Step 1: Add the `PATIENT` role constant**

In `backend/src/main/java/com/hms/security/UserRole.java`, add this line inside the class body, after the `RADIOLOGY_TECHNICIAN` constant:

```java
    public static final String PATIENT = "PATIENT";
```

- [ ] **Step 2: Add the portal request matchers to `SecurityConfig.java`**

In `backend/src/main/java/com/hms/config/SecurityConfig.java`, inside `authorizeHttpRequests`, immediately after the existing `.requestMatchers("/api/public/feedback/**").permitAll()` line, add:

```java
                        // Form 40 phase 1: patient portal — OTP request/verify are public,
                        // everything else under /hospital/portal/** requires role PATIENT.
                        .requestMatchers("/hospital/portal/otp/**").permitAll()
                        .requestMatchers("/hospital/portal/**").hasRole("PATIENT")
```

Place these two lines **before** the existing `.requestMatchers("/hospital/**", "/api/pharmacy/**")` rule (Spring Security evaluates matchers in order, and `/hospital/portal/**` is a more specific prefix than `/hospital/**` — it must be declared first or the broader staff-role rule would shadow it).

- [ ] **Step 3: Extend `RateLimitFilter` to cover the OTP endpoints**

In `backend/src/main/java/com/hms/filter/RateLimitFilter.java`, replace:

```java
        String uri = request.getRequestURI();
        if (!uri.endsWith("/login")) {
            chain.doFilter(request, response);
            return;
        }
```

with:

```java
        String uri = request.getRequestURI();
        boolean limited = uri.endsWith("/login") || uri.contains("/portal/otp/");
        if (!limited) {
            chain.doFilter(request, response);
            return;
        }
```

- [ ] **Step 4: Verify compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: exits 0, no errors.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/security/UserRole.java backend/src/main/java/com/hms/config/SecurityConfig.java backend/src/main/java/com/hms/filter/RateLimitFilter.java
git commit -m "feat(portal): PATIENT role, /hospital/portal/** security matchers, OTP rate limiting"
```

---

### Task 3: SMS gateway (MSG91)

**Files:**
- Create: `backend/src/main/java/com/hms/service/portal/SmsGateway.java`
- Create: `backend/src/main/java/com/hms/service/portal/Msg91SmsGateway.java`
- Modify: `backend/src/main/resources/application.properties`

- [ ] **Step 1: Add MSG91 configuration properties**

In `backend/src/main/resources/application.properties`, immediately after the existing `jwt.expiration=86400000` line, add:

```properties
msg91.auth.key=${MSG91_AUTH_KEY:}
msg91.sender.id=${MSG91_SENDER_ID:HMSOTP}
msg91.otp.template.id=${MSG91_OTP_TEMPLATE_ID:}
msg91.api.url=${MSG91_API_URL:https://control.msg91.com/api/v5/otp}
```

- [ ] **Step 2: Create the `SmsGateway` interface**

```java
package com.hms.service.portal;

/** Delivers a one-time OTP code to a mobile number. One method, one responsibility. */
public interface SmsGateway {
    void send(String mobile, String otp);
}
```

- [ ] **Step 3: Create `Msg91SmsGateway.java`**

```java
package com.hms.service.portal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * MSG91 OTP delivery. If {@code msg91.auth.key} is unconfigured (blank), falls back to
 * logging the OTP to the server console instead of calling the external API — this only
 * activates when the credential is absent, so production behavior once configured is a
 * real SMS send.
 */
@Component
public class Msg91SmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsGateway.class);

    @Value("${msg91.auth.key}")
    private String authKey;

    @Value("${msg91.sender.id}")
    private String senderId;

    @Value("${msg91.otp.template.id}")
    private String templateId;

    @Value("${msg91.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void send(String mobile, String otp) {
        if (authKey == null || authKey.isBlank()) {
            log.warn("[DEV-OTP] MSG91_AUTH_KEY not configured — OTP for {} is: {}", mobile, otp);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authkey", authKey);

            Map<String, Object> body = new HashMap<>();
            body.put("template_id", templateId);
            body.put("mobile", mobile);
            body.put("otp", otp);
            body.put("sender", senderId);

            restTemplate.exchange(apiUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.error("MSG91 OTP dispatch failed for {}: {}", mobile, e.getMessage());
            throw new RuntimeException("Failed to send OTP. Please try again.");
        }
    }
}
```

**Note for whoever configures production:** verify the exact MSG91 request shape (`template_id`/`mobile`/`otp` field names, auth header) against your MSG91 account's API version before enabling — MSG91's OTP API has had minor variations across account tiers. This is why `SmsGateway` is an interface: the implementation can be adjusted without touching any calling code.

- [ ] **Step 4: Verify compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: exits 0, no errors.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application.properties backend/src/main/java/com/hms/service/portal/SmsGateway.java backend/src/main/java/com/hms/service/portal/Msg91SmsGateway.java
git commit -m "feat(portal): MSG91 SMS gateway for OTP delivery with dev-mode fallback"
```

---

### Task 4: PatientPortalAuthService (TDD)

**Files:**
- Create: `backend/src/main/java/com/hms/dto/PortalOtpRequestRequest.java`
- Create: `backend/src/main/java/com/hms/dto/PortalOtpVerifyRequest.java`
- Create: `backend/src/main/java/com/hms/dto/PortalLoginResponse.java`
- Create: `backend/src/main/java/com/hms/service/portal/PatientPortalAuthService.java`
- Test: `backend/src/test/java/com/hms/service/PatientPortalAuthServiceTest.java`

- [ ] **Step 1: Create the request/response DTOs**

`backend/src/main/java/com/hms/dto/PortalOtpRequestRequest.java`:
```java
package com.hms.dto;

import lombok.Data;

@Data
public class PortalOtpRequestRequest {
    private String mobile;
    private String uhid;
}
```

`backend/src/main/java/com/hms/dto/PortalOtpVerifyRequest.java`:
```java
package com.hms.dto;

import lombok.Data;

@Data
public class PortalOtpVerifyRequest {
    private String mobile;
    private String otp;
}
```

`backend/src/main/java/com/hms/dto/PortalLoginResponse.java`:
```java
package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalLoginResponse {
    private String token;
    private Long patientId;
    private String patientName;
    private String uhid;
}
```

- [ ] **Step 2: Write the failing test file**

Create `backend/src/test/java/com/hms/service/PatientPortalAuthServiceTest.java`:

```java
package com.hms.service;

import com.hms.dto.PortalLoginResponse;
import com.hms.dto.PortalOtpRequestRequest;
import com.hms.dto.PortalOtpVerifyRequest;
import com.hms.entity.Patient;
import com.hms.entity.PatientPortalUser;
import com.hms.entity.PortalOtp;
import com.hms.repository.PatientPortalUserRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.PortalOtpRepository;
import com.hms.security.JwtUtil;
import com.hms.service.portal.PatientPortalAuthService;
import com.hms.service.portal.SmsGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientPortalAuthServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private PatientPortalUserRepository portalUserRepository;
    @Mock private PortalOtpRepository otpRepository;
    @Mock private SmsGateway smsGateway;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private PatientPortalAuthService service;

    private static final Long HOSPITAL_ID = 1L;

    private Patient patient(Long id, String customId) {
        Patient p = new Patient();
        p.setId(id);
        p.setHospitalId(HOSPITAL_ID);
        p.setName("Jane Doe");
        p.setPhone("9876543210");
        p.setCustomId(customId);
        return p;
    }

    // ===== OTP request =====

    @Test
    void requestOtp_noMatchingPatient_throws() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of());

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");

        assertThatThrownBy(() -> service.requestOtp(HOSPITAL_ID, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No patient record found");
        verify(smsGateway, never()).send(any(), any());
    }

    @Test
    void requestOtp_ambiguousMatchWithoutUhid_throws() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(patient(1L, "UHID-1"), patient(2L, "UHID-2")));

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");

        assertThatThrownBy(() -> service.requestOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple records found");
    }

    @Test
    void requestOtp_ambiguousMatchWithUhid_resolvesAndSendsOtp() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(patient(1L, "UHID-1"), patient(2L, "UHID-2")));
        when(portalUserRepository.findByHospitalIdAndPatientId(HOSPITAL_ID, 2L)).thenReturn(Optional.empty());
        when(portalUserRepository.save(any(PatientPortalUser.class))).thenAnswer(i -> i.getArgument(0));
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");
        req.setUhid("UHID-2");

        service.requestOtp(HOSPITAL_ID, req);

        verify(smsGateway).send(eq("9876543210"), any());
        verify(otpRepository).save(any(PortalOtp.class));
    }

    @Test
    void requestOtp_singleMatch_createsPortalUserAndSendsOtp() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(patient(1L, "UHID-1")));
        when(portalUserRepository.findByHospitalIdAndPatientId(HOSPITAL_ID, 1L)).thenReturn(Optional.empty());
        when(portalUserRepository.save(any(PatientPortalUser.class))).thenAnswer(i -> i.getArgument(0));
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");

        service.requestOtp(HOSPITAL_ID, req);

        verify(portalUserRepository).save(any(PatientPortalUser.class));
        verify(smsGateway).send(eq("9876543210"), any());
    }

    @Test
    void requestOtp_lockedAccountWithinCooldown_throws() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(patient(1L, "UHID-1")));
        PatientPortalUser locked = new PatientPortalUser();
        locked.setId(5L);
        locked.setStatus("LOCKED");
        locked.setLockUntil(LocalDateTime.now().plusMinutes(10));
        when(portalUserRepository.findByHospitalIdAndPatientId(HOSPITAL_ID, 1L)).thenReturn(Optional.of(locked));

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");

        assertThatThrownBy(() -> service.requestOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
        verify(smsGateway, never()).send(any(), any());
    }

    // ===== OTP verify =====

    @Test
    void verifyOtp_noOtpOnFile_throws() {
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.empty());

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("123456");

        assertThatThrownBy(() -> service.verifyOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyOtp_expiredOtp_throws() {
        PortalOtp otp = new PortalOtp();
        otp.setId(9L);
        otp.setMobile("9876543210");
        otp.setOtpHash(service.hashOtp("123456"));
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.of(otp));

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("123456");

        assertThatThrownBy(() -> service.verifyOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyOtp_wrongCode_incrementsAttemptsAndLocksAfterFive() {
        PortalOtp otp = new PortalOtp();
        otp.setId(9L);
        otp.setMobile("9876543210");
        otp.setOtpHash(service.hashOtp("123456"));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setAttemptCount(4);
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.of(otp));
        PatientPortalUser portalUser = new PatientPortalUser();
        portalUser.setId(5L);
        portalUser.setPatientId(1L);
        portalUser.setStatus("ACTIVE");
        when(portalUserRepository.findByHospitalIdAndPatientId(eq(HOSPITAL_ID), any())).thenReturn(Optional.empty());
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("000000");

        assertThatThrownBy(() -> service.verifyOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(otp.getAttemptCount()).isEqualTo(5);
    }

    @Test
    void verifyOtp_correctCode_mintsTokenAndConsumesOtp() {
        PortalOtp otp = new PortalOtp();
        otp.setId(9L);
        otp.setHospitalId(HOSPITAL_ID);
        otp.setMobile("9876543210");
        otp.setOtpHash(service.hashOtp("123456"));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setAttemptCount(0);
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.of(otp));
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        Patient p = patient(1L, "UHID-1");
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(p));

        PatientPortalUser portalUser = new PatientPortalUser();
        portalUser.setId(5L);
        portalUser.setHospitalId(HOSPITAL_ID);
        portalUser.setPatientId(1L);
        portalUser.setStatus("ACTIVE");
        when(portalUserRepository.findByHospitalIdAndPatientId(HOSPITAL_ID, 1L)).thenReturn(Optional.of(portalUser));
        when(portalUserRepository.save(any(PatientPortalUser.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(5L, "9876543210", "PATIENT", HOSPITAL_ID, List.of())).thenReturn("fake-jwt");

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("123456");

        PortalLoginResponse resp = service.verifyOtp(HOSPITAL_ID, req);

        assertThat(resp.getToken()).isEqualTo("fake-jwt");
        assertThat(resp.getPatientId()).isEqualTo(1L);
        assertThat(otp.getConsumedAt()).isNotNull();
        assertThat(portalUser.getLastLogin()).isNotNull();
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd backend && mvn -q -Dtest=PatientPortalAuthServiceTest test`
Expected: FAIL — compile error, `PatientPortalAuthService` does not exist yet.

- [ ] **Step 4: Implement `PatientPortalAuthService`**

Create `backend/src/main/java/com/hms/service/portal/PatientPortalAuthService.java`:

```java
package com.hms.service.portal;

import com.hms.dto.PortalLoginResponse;
import com.hms.dto.PortalOtpRequestRequest;
import com.hms.dto.PortalOtpVerifyRequest;
import com.hms.entity.Patient;
import com.hms.entity.PatientPortalUser;
import com.hms.entity.PortalOtp;
import com.hms.repository.PatientPortalUserRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.PortalOtpRepository;
import com.hms.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Patient portal OTP login (Form 40 phase 1). Two endpoints cover both first-time
 * registration and every subsequent login — there is no password step.
 */
@Service
public class PatientPortalAuthService {

    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final String OTP_HMAC_KEY = "patient-portal-otp-hmac";

    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientPortalUserRepository portalUserRepository;
    @Autowired private PortalOtpRepository otpRepository;
    @Autowired private SmsGateway smsGateway;
    @Autowired private JwtUtil jwtUtil;

    @Transactional
    public void requestOtp(Long hospitalId, PortalOtpRequestRequest request) {
        Patient patient = resolvePatient(hospitalId, request.getMobile(), request.getUhid());

        PatientPortalUser portalUser = portalUserRepository
                .findByHospitalIdAndPatientId(hospitalId, patient.getId())
                .orElseGet(() -> {
                    PatientPortalUser u = new PatientPortalUser();
                    u.setHospitalId(hospitalId);
                    u.setPatientId(patient.getId());
                    u.setMobile(request.getMobile());
                    u.setStatus("ACTIVE");
                    return portalUserRepository.save(u);
                });

        if ("LOCKED".equals(portalUser.getStatus()) && portalUser.getLockUntil() != null
                && portalUser.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("This account is temporarily locked due to repeated failed attempts. Try again later.");
        }

        String otp = generateOtp();
        PortalOtp record = new PortalOtp();
        record.setHospitalId(hospitalId);
        record.setMobile(request.getMobile());
        record.setOtpHash(hashOtp(otp));
        record.setPurpose("LOGIN");
        record.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        record.setAttemptCount(0);
        otpRepository.save(record);

        smsGateway.send(request.getMobile(), otp);
    }

    @Transactional
    public PortalLoginResponse verifyOtp(Long hospitalId, PortalOtpVerifyRequest request) {
        PortalOtp otp = otpRepository
                .findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(hospitalId, request.getMobile())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP."));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired OTP.");
        }

        if (!otp.getOtpHash().equals(hashOtp(request.getOtp()))) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            otpRepository.save(otp);
            if (otp.getAttemptCount() >= MAX_ATTEMPTS) {
                Patient patient = resolvePatient(hospitalId, request.getMobile(), null);
                portalUserRepository.findByHospitalIdAndPatientId(hospitalId, patient.getId()).ifPresent(u -> {
                    u.setStatus("LOCKED");
                    u.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                    portalUserRepository.save(u);
                });
            }
            throw new IllegalArgumentException("Invalid or expired OTP.");
        }

        otp.setConsumedAt(LocalDateTime.now());
        otp.setAttemptCount(0);
        otpRepository.save(otp);

        Patient patient = resolvePatient(hospitalId, request.getMobile(), null);
        PatientPortalUser portalUser = portalUserRepository
                .findByHospitalIdAndPatientId(hospitalId, patient.getId())
                .orElseThrow(() -> new IllegalStateException("Portal account not found."));
        portalUser.setStatus("ACTIVE");
        portalUser.setLockUntil(null);
        portalUser.setLastLogin(LocalDateTime.now());
        portalUserRepository.save(portalUser);

        String token = jwtUtil.generateToken(portalUser.getId(), request.getMobile(), "PATIENT", hospitalId, List.of());
        return new PortalLoginResponse(token, patient.getId(), patient.getName(), patient.getCustomId());
    }

    private Patient resolvePatient(Long hospitalId, String mobile, String uhid) {
        List<Patient> matches = patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue(mobile, hospitalId);
        if (matches.isEmpty()) {
            throw new RuntimeException("No patient record found with this number. Please visit reception to register.");
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (uhid == null || uhid.isBlank()) {
            throw new IllegalStateException("Multiple records found. Please also provide your patient ID.");
        }
        return matches.stream()
                .filter(p -> uhid.equalsIgnoreCase(p.getCustomId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No patient record found with this number and ID."));
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public String hashOtp(String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(OTP_HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash OTP", e);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && mvn -q -Dtest=PatientPortalAuthServiceTest test`
Expected: PASS, all 9 tests green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/dto/PortalOtpRequestRequest.java backend/src/main/java/com/hms/dto/PortalOtpVerifyRequest.java backend/src/main/java/com/hms/dto/PortalLoginResponse.java backend/src/main/java/com/hms/service/portal/PatientPortalAuthService.java backend/src/test/java/com/hms/service/PatientPortalAuthServiceTest.java
git commit -m "feat(portal): OTP request/verify auth service with lockout (Form 40 phase 1)"
```

---

### Task 5: PatientPortalAuthController

**Files:**
- Create: `backend/src/main/java/com/hms/controller/portal/PatientPortalAuthController.java`

- [ ] **Step 1: Add `hospitalId` to the OTP request DTOs**

The OTP endpoints are unauthenticated, so there's no JWT to pull `hospitalId` from yet — unlike `PublicFeedbackController`'s single-use-token pattern (which resolves hospital/patient from the token server-side), there's no token yet at this stage, so the client must supply which hospital's portal it's logging into. The frontend already knows this from the URL (`/portal/:hospitalId/login`, added in Task 9). Add a `hospitalId` field to both DTOs:

In `backend/src/main/java/com/hms/dto/PortalOtpRequestRequest.java`, add:
```java
    private Long hospitalId;
```

In `backend/src/main/java/com/hms/dto/PortalOtpVerifyRequest.java`, add:
```java
    private Long hospitalId;
```

- [ ] **Step 2: Update `PatientPortalAuthService` call sites to use the request's `hospitalId`**

The service methods already take `hospitalId` as a separate first parameter — no service change needed. The controller will pass `request.getHospitalId()`.

- [ ] **Step 3: Create the controller**

```java
package com.hms.controller.portal;

import com.hms.dto.PortalOtpRequestRequest;
import com.hms.dto.PortalOtpVerifyRequest;
import com.hms.service.portal.PatientPortalAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Patient portal OTP login (Form 40 phase 1) — public, unauthenticated by design. */
@RestController
@RequestMapping("/hospital/portal/otp")
public class PatientPortalAuthController {

    @Autowired
    private PatientPortalAuthService authService;

    @PostMapping("/request")
    public ResponseEntity<?> requestOtp(@RequestBody PortalOtpRequestRequest request) {
        try {
            if (request.getHospitalId() == null) {
                return ResponseEntity.badRequest().body("hospitalId is required");
            }
            authService.requestOtp(request.getHospitalId(), request);
            return ResponseEntity.ok(java.util.Map.of("message", "OTP sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody PortalOtpVerifyRequest request) {
        try {
            if (request.getHospitalId() == null) {
                return ResponseEntity.badRequest().body("hospitalId is required");
            }
            return ResponseEntity.ok(authService.verifyOtp(request.getHospitalId(), request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Verify compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: exits 0, no errors.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/controller/portal/PatientPortalAuthController.java backend/src/main/java/com/hms/dto/PortalOtpRequestRequest.java backend/src/main/java/com/hms/dto/PortalOtpVerifyRequest.java
git commit -m "feat(portal): public OTP request/verify endpoints"
```

---

### Task 6: PatientPortalDashboardService (TDD)

**Files:**
- Create: `backend/src/main/java/com/hms/dto/PortalReportResponse.java`
- Create: `backend/src/main/java/com/hms/dto/PortalDashboardResponse.java`
- Create: `backend/src/main/java/com/hms/service/portal/PatientPortalDashboardService.java`
- Test: `backend/src/test/java/com/hms/service/PatientPortalDashboardServiceTest.java`

- [ ] **Step 1: Create the response DTOs**

`backend/src/main/java/com/hms/dto/PortalReportResponse.java`:
```java
package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalReportResponse {
    private Long orderId;
    private String category; // LAB / RADIOLOGY
    private String testName;
    private LocalDateTime releasedAt;
    private String summary;   // resultSummary for lab, impression for radiology
    private String parameters; // JSON parameters (lab) or findings (radiology)
    private Boolean isAbnormal;
    private String resultFileUrl;
}
```

`backend/src/main/java/com/hms/dto/PortalDashboardResponse.java`:
```java
package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalDashboardResponse {
    private long upcomingAppointments;
    private long releasedReports;
    private long activePrescriptions;
    private BigDecimal outstandingBalance;
}
```

- [ ] **Step 2: Write the failing test file**

Create `backend/src/test/java/com/hms/service/PatientPortalDashboardServiceTest.java`:

```java
package com.hms.service;

import com.hms.dto.PortalDashboardResponse;
import com.hms.dto.PortalReportResponse;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.service.portal.PatientPortalDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientPortalDashboardServiceTest {

    @Mock private PatientPortalUserRepository portalUserRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private LabOrderRepository labOrderRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private RadiologyOrderRepository radiologyOrderRepository;
    @Mock private RadiologyResultRepository radiologyResultRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private BillingRepository billingRepository;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private PatientPortalDashboardService service;

    private static final Long HOSPITAL_ID = 1L;
    private static final Long PORTAL_USER_ID = 5L;
    private static final Long PATIENT_ID = 1L;

    private PatientPortalUser portalUser() {
        PatientPortalUser u = new PatientPortalUser();
        u.setId(PORTAL_USER_ID);
        u.setHospitalId(HOSPITAL_ID);
        u.setPatientId(PATIENT_ID);
        return u;
    }

    // ===== BR-2: only RELEASED reports are ever returned =====

    @Test
    void getReports_excludesUnreleasedOrders() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));

        LabOrder released = new LabOrder();
        released.setId(10L);
        released.setStatus("RELEASED");
        released.setTestName("CBC");
        LabOrder verified = new LabOrder();
        verified.setId(11L);
        verified.setStatus("VERIFIED");
        verified.setTestName("LFT");
        when(labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of(released, verified));
        when(radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());

        LabResult result = new LabResult();
        result.setResultSummary("Normal");
        result.setIsAbnormal(false);
        result.setReleasedAt(java.time.LocalDateTime.now());
        when(labResultRepository.findByLabOrderId(10L)).thenReturn(Optional.of(result));

        List<PortalReportResponse> reports = service.getReports(HOSPITAL_ID, PORTAL_USER_ID);

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getOrderId()).isEqualTo(10L);
        assertThat(reports.get(0).getTestName()).isEqualTo("CBC");
    }

    // ===== dashboard summary =====

    @Test
    void getDashboard_summarizesCountsAndBalance() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));
        Appointment upcoming = new Appointment();
        upcoming.setStatus("SCHEDULED");
        upcoming.setAppointmentDate(LocalDate.now().plusDays(2));
        when(appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(List.of(upcoming));

        LabOrder released = new LabOrder();
        released.setId(10L);
        released.setStatus("RELEASED");
        when(labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of(released));
        when(radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());

        when(medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(List.of());
        when(prescriptionRepository.findByMedicalRecordIdIn(List.of())).thenReturn(List.of());

        Billing pending = new Billing();
        pending.setAmount(new BigDecimal("1500"));
        pending.setPaymentStatus("PENDING");
        when(billingRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(List.of(pending));

        PortalDashboardResponse dashboard = service.getDashboard(HOSPITAL_ID, PORTAL_USER_ID);

        assertThat(dashboard.getUpcomingAppointments()).isEqualTo(1);
        assertThat(dashboard.getReleasedReports()).isEqualTo(1);
        assertThat(dashboard.getOutstandingBalance()).isEqualByComparingTo("1500");
    }

    // ===== tenant/patient scoping =====

    @Test
    void getAppointments_scopesToResolvedPatientId() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));
        when(appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(List.of());

        service.getAppointments(HOSPITAL_ID, PORTAL_USER_ID);

        org.mockito.Mockito.verify(appointmentRepository)
                .findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(PATIENT_ID, HOSPITAL_ID);
    }

    // ===== BR-4: every read is audit-logged =====

    @Test
    void getReports_writesAuditLogEntry() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));
        when(labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());
        when(radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());

        service.getReports(HOSPITAL_ID, PORTAL_USER_ID);

        org.mockito.Mockito.verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(
                entry -> entry.getAction().equals("PATIENT_PORTAL_REPORTS_ACCESSED")));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd backend && mvn -q -Dtest=PatientPortalDashboardServiceTest test`
Expected: FAIL — compile error, `PatientPortalDashboardService` does not exist yet.

- [ ] **Step 4: Implement `PatientPortalDashboardService`**

Create `backend/src/main/java/com/hms/service/portal/PatientPortalDashboardService.java`:

```java
package com.hms.service.portal;

import com.hms.dto.PortalDashboardResponse;
import com.hms.dto.PortalReportResponse;
import com.hms.entity.*;
import com.hms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Patient portal read-only dashboard (Form 40 phase 1). Every method resolves the caller's
 * own {@code patientId} from their {@code patient_portal_user} row — never a client-supplied
 * ID — and the reports query enforces BR-2 (RELEASED only) server-side. BR-4: every read
 * writes an {@link AuditLog} entry (patient portal actions are audited per-access, unlike
 * internal staff endpoints).
 */
@Service
public class PatientPortalDashboardService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PatientPortalDashboardService.class);

    @Autowired private PatientPortalUserRepository portalUserRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private LabOrderRepository labOrderRepository;
    @Autowired private LabResultRepository labResultRepository;
    @Autowired private RadiologyOrderRepository radiologyOrderRepository;
    @Autowired private RadiologyResultRepository radiologyResultRepository;
    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private BillingRepository billingRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    public PortalDashboardResponse getDashboard(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_DASHBOARD_ACCESSED", "Dashboard viewed");

        long upcoming = appointmentRepository
                .findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(patientId, hospitalId)
                .stream()
                .filter(a -> !a.getAppointmentDate().isBefore(LocalDate.now()))
                .count();

        List<PortalReportResponse> reports = getReports(hospitalId, portalUserId);

        List<MedicalRecord> records = medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        List<Long> recordIds = records.stream().map(MedicalRecord::getId).collect(Collectors.toList());
        long prescriptions = prescriptionRepository.findByMedicalRecordIdIn(recordIds).size();

        BigDecimal outstanding = billingRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .filter(b -> "PENDING".equalsIgnoreCase(b.getPaymentStatus()))
                .map(Billing::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortalDashboardResponse(upcoming, reports.size(), prescriptions, outstanding);
    }

    public List<Appointment> getAppointments(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_APPOINTMENTS_ACCESSED", "Appointments viewed");
        return appointmentRepository
                .findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(patientId, hospitalId);
    }

    /** BR-2: only RELEASED lab/radiology orders are ever visible to the patient. */
    public List<PortalReportResponse> getReports(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_REPORTS_ACCESSED", "Released reports list viewed");
        List<PortalReportResponse> reports = new ArrayList<>();

        for (LabOrder order : labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId)) {
            if (!"RELEASED".equals(order.getStatus())) continue;
            labResultRepository.findByLabOrderId(order.getId()).ifPresent(result ->
                    reports.add(new PortalReportResponse(order.getId(), "LAB", order.getTestName(),
                            result.getReleasedAt(), result.getResultSummary(), result.getParameters(),
                            result.getIsAbnormal(), result.getResultFileUrl())));
        }

        for (RadiologyOrder order : radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId)) {
            if (!"RELEASED".equals(order.getStatus())) continue;
            radiologyResultRepository.findByRadiologyOrderId(order.getId()).ifPresent(result ->
                    reports.add(new PortalReportResponse(order.getId(), "RADIOLOGY", order.getTestName(),
                            result.getReleasedAt(), result.getImpression(), result.getFindings(),
                            result.getIsAbnormal(), result.getResultFileUrl())));
        }

        return reports;
    }

    public List<Prescription> getPrescriptions(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_PRESCRIPTIONS_ACCESSED", "Prescriptions viewed");
        List<MedicalRecord> records = medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        List<Long> recordIds = records.stream().map(MedicalRecord::getId).collect(Collectors.toList());
        return prescriptionRepository.findByMedicalRecordIdIn(recordIds);
    }

    public List<Billing> getBilling(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_BILLING_ACCESSED", "Billing viewed");
        return billingRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    private Long resolvePatientId(Long hospitalId, Long portalUserId) {
        PatientPortalUser portalUser = portalUserRepository.findById(portalUserId)
                .orElseThrow(() -> new RuntimeException("Portal account not found"));
        if (!portalUser.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Portal account not found");
        }
        return portalUser.getPatientId();
    }

    /** BR-4: writes one AuditLog entry per portal record access, tagged with the patient ID. */
    private void audit(Long hospitalId, Long patientId, String action, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setDetails(details + " (patientId=" + patientId + ")");
            entry.setPerformedBy("patient-portal");
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }
}
```

Note: `LabOrder.getTestName()` requires `testName` to exist as a getter — it already exists per the entity. Also confirm `LabResult` has a `getReleasedAt()` — check `LabResult.java`; if the field is instead only implicitly tracked via `releasedAt` on the order, adjust the reference accordingly (grep `releasedAt` in `LabResult.java` before finalizing — it's listed in the entity as `released_at` / `releasedAt`, confirmed present).

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && mvn -q -Dtest=PatientPortalDashboardServiceTest test`
Expected: PASS, all 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/dto/PortalReportResponse.java backend/src/main/java/com/hms/dto/PortalDashboardResponse.java backend/src/main/java/com/hms/service/portal/PatientPortalDashboardService.java backend/src/test/java/com/hms/service/PatientPortalDashboardServiceTest.java
git commit -m "feat(portal): read-only dashboard/appointments/reports/prescriptions/billing (Form 40 phase 1)"
```

---

### Task 7: PatientPortalController

**Files:**
- Create: `backend/src/main/java/com/hms/controller/portal/PatientPortalController.java`

- [ ] **Step 1: Create the controller**

```java
package com.hms.controller.portal;

import com.hms.security.SecurityContextHelper;
import com.hms.service.portal.PatientPortalDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Patient portal dashboard/record endpoints (Form 40 phase 1). Every method resolves the
 * caller's identity via {@link SecurityContextHelper} — the JWT's {@code userId} claim is
 * the {@code patient_portal_user.id}, never a client-supplied patient ID.
 */
@RestController
@RequestMapping("/hospital/portal")
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalController {

    @Autowired private PatientPortalDashboardService dashboardService;
    @Autowired private SecurityContextHelper securityHelper;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }

    @GetMapping("/appointments")
    public ResponseEntity<?> getAppointments() {
        return ResponseEntity.ok(dashboardService.getAppointments(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }

    @GetMapping("/reports")
    public ResponseEntity<?> getReports() {
        return ResponseEntity.ok(dashboardService.getReports(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }

    @GetMapping("/prescriptions")
    public ResponseEntity<?> getPrescriptions() {
        return ResponseEntity.ok(dashboardService.getPrescriptions(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }

    @GetMapping("/billing")
    public ResponseEntity<?> getBilling() {
        return ResponseEntity.ok(dashboardService.getBilling(securityHelper.getCurrentHospitalId(), securityHelper.getCurrentUserId()));
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: exits 0, no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/controller/portal/PatientPortalController.java
git commit -m "feat(portal): authenticated dashboard/record endpoints"
```

---

### Task 8: Backend green gate

- [ ] **Step 1: Run the full backend test suite**

Run: `cd backend && mvn -q test`
Expected: BUILD SUCCESS, all tests pass (including every test from prior sessions — this must not regress anything).

- [ ] **Step 2: If anything fails, fix and re-run before proceeding**

Do not proceed to the frontend tasks until this is green.

---

### Task 9: Frontend — service, login page, dashboard page, routing

**Files:**
- Create: `frontend/src/services/patientPortalService.js`
- Create: `frontend/src/pages/portal/PatientPortalLogin.jsx`
- Create: `frontend/src/pages/portal/PatientPortalDashboard.jsx`
- Modify: `frontend/src/App.jsx`

- [ ] **Step 1: Create the service wrapper**

```javascript
// frontend/src/services/patientPortalService.js
import apiService from './apiService';

const patientPortalService = {
  requestOtp: (hospitalId, mobile, uhid) =>
    apiService.post('/hospital/portal/otp/request', { hospitalId, mobile, uhid }),
  verifyOtp: (hospitalId, mobile, otp) =>
    apiService.post('/hospital/portal/otp/verify', { hospitalId, mobile, otp }),

  getDashboard: () => apiService.get('/hospital/portal/dashboard'),
  getAppointments: () => apiService.get('/hospital/portal/appointments'),
  getReports: () => apiService.get('/hospital/portal/reports'),
  getPrescriptions: () => apiService.get('/hospital/portal/prescriptions'),
  getBilling: () => apiService.get('/hospital/portal/billing'),
};

export default patientPortalService;
```

- [ ] **Step 2: Create the login page**

```jsx
// frontend/src/pages/portal/PatientPortalLogin.jsx
import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import patientPortalService from '../../services/patientPortalService';

export default function PatientPortalLogin() {
  const { hospitalId } = useParams();
  const navigate = useNavigate();
  const [step, setStep] = useState('mobile'); // mobile | otp
  const [mobile, setMobile] = useState('');
  const [uhid, setUhid] = useState('');
  const [otp, setOtp] = useState('');
  const [error, setError] = useState('');
  const [needsUhid, setNeedsUhid] = useState(false);
  const [loading, setLoading] = useState(false);

  const err = (e, fallback) => e?.response?.data?.error || e?.response?.data?.message || e?.response?.data || fallback;

  const requestOtp = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await patientPortalService.requestOtp(Number(hospitalId), mobile, uhid || undefined);
      setStep('otp');
    } catch (ex) {
      const message = err(ex, 'Failed to send OTP');
      if (typeof message === 'string' && message.includes('Multiple records')) {
        setNeedsUhid(true);
      }
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const verifyOtp = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await patientPortalService.verifyOtp(Number(hospitalId), mobile, otp);
      const data = res?.data || res;
      sessionStorage.setItem('token', data.token);
      sessionStorage.setItem('user', JSON.stringify({ role: 'PATIENT', patientId: data.patientId, name: data.patientName }));
      navigate('/portal/dashboard');
    } catch (ex) {
      setError(err(ex, 'Invalid OTP'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 max-w-md w-full space-y-5">
        <h1 className="text-xl font-bold text-gray-900">Patient Portal</h1>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2">{error}</div>}

        {step === 'mobile' && (
          <form onSubmit={requestOtp} className="space-y-3">
            <input required placeholder="Mobile Number" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
              value={mobile} onChange={e => setMobile(e.target.value)} />
            {needsUhid && (
              <input required placeholder="Patient ID (UHID)" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                value={uhid} onChange={e => setUhid(e.target.value)} />
            )}
            <button disabled={loading} className="w-full bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-semibold py-2.5 rounded-lg">
              {loading ? 'Sending…' : 'Send OTP'}
            </button>
          </form>
        )}

        {step === 'otp' && (
          <form onSubmit={verifyOtp} className="space-y-3">
            <p className="text-sm text-gray-600">Enter the OTP sent to {mobile}</p>
            <input required placeholder="6-digit OTP" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
              value={otp} onChange={e => setOtp(e.target.value)} />
            <button disabled={loading} className="w-full bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-semibold py-2.5 rounded-lg">
              {loading ? 'Verifying…' : 'Verify & Login'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Create the dashboard page**

```jsx
// frontend/src/pages/portal/PatientPortalDashboard.jsx
import React, { useState, useEffect, useCallback } from 'react';
import patientPortalService from '../../services/patientPortalService';

export default function PatientPortalDashboard() {
  const [dashboard, setDashboard] = useState(null);
  const [appointments, setAppointments] = useState([]);
  const [reports, setReports] = useState([]);
  const [prescriptions, setPrescriptions] = useState([]);
  const [billing, setBilling] = useState([]);

  const load = useCallback(async () => {
    try {
      const [d, a, r, p, b] = await Promise.all([
        patientPortalService.getDashboard(),
        patientPortalService.getAppointments(),
        patientPortalService.getReports(),
        patientPortalService.getPrescriptions(),
        patientPortalService.getBilling(),
      ]);
      setDashboard(d?.data || d);
      setAppointments(Array.isArray(a?.data) ? a.data : (Array.isArray(a) ? a : []));
      setReports(Array.isArray(r?.data) ? r.data : (Array.isArray(r) ? r : []));
      setPrescriptions(Array.isArray(p?.data) ? p.data : (Array.isArray(p) ? p : []));
      setBilling(Array.isArray(b?.data) ? b.data : (Array.isArray(b) ? b : []));
    } catch (e) { /* best-effort */ }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="min-h-screen bg-gray-50 p-6 space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">My Health Portal</h1>

      {dashboard && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-500">Upcoming Appointments</p>
            <p className="text-2xl font-bold mt-1">{dashboard.upcomingAppointments}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-500">Released Reports</p>
            <p className="text-2xl font-bold mt-1">{dashboard.releasedReports}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-500">Active Prescriptions</p>
            <p className="text-2xl font-bold mt-1">{dashboard.activePrescriptions}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-500">Outstanding Balance</p>
            <p className="text-2xl font-bold mt-1 text-amber-600">₹{dashboard.outstandingBalance}</p>
          </div>
        </div>
      )}

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-800 text-sm mb-2">Appointments</h2>
        <div className="space-y-1 text-xs">
          {appointments.map(a => (
            <div key={a.id} className="flex justify-between border-b border-gray-100 py-1.5">
              <span>{a.doctorName} — {a.appointmentDate} {a.appointmentTime}</span>
              <span className="text-gray-400">{a.status}</span>
            </div>
          ))}
          {appointments.length === 0 && <p className="text-gray-400 italic">No appointments.</p>}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-800 text-sm mb-2">Released Reports</h2>
        <div className="space-y-2 text-xs">
          {reports.map(r => (
            <div key={`${r.category}-${r.orderId}`} className="border border-gray-100 rounded-lg p-2">
              <div className="flex justify-between items-center">
                <span className="font-semibold">{r.testName} ({r.category})</span>
                {r.isAbnormal && <span className="text-red-600 font-semibold">Abnormal</span>}
              </div>
              <p className="text-gray-600 mt-1">{r.summary}</p>
              {r.resultFileUrl && (
                <a href={r.resultFileUrl} target="_blank" rel="noreferrer" className="text-indigo-600 underline">Download</a>
              )}
            </div>
          ))}
          {reports.length === 0 && <p className="text-gray-400 italic">No released reports yet.</p>}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-800 text-sm mb-2">Prescriptions</h2>
        <div className="space-y-1 text-xs">
          {prescriptions.map(p => (
            <div key={p.id} className="flex justify-between border-b border-gray-100 py-1.5">
              <span>{p.medicineName} — {p.dosage} ({p.frequency})</span>
              <span className="text-gray-400">{p.status}</span>
            </div>
          ))}
          {prescriptions.length === 0 && <p className="text-gray-400 italic">No prescriptions.</p>}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-800 text-sm mb-2">Billing</h2>
        <div className="space-y-1 text-xs">
          {billing.map(b => (
            <div key={b.id} className="flex justify-between border-b border-gray-100 py-1.5">
              <span>Bill #{b.id}</span>
              <span className={b.paymentStatus === 'PAID' ? 'text-green-600' : 'text-amber-600'}>₹{b.amount} — {b.paymentStatus}</span>
            </div>
          ))}
          {billing.length === 0 && <p className="text-gray-400 italic">No bills.</p>}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Wire routes into `App.jsx`**

Add the import near the other page imports:

```javascript
import PatientPortalLogin from './pages/portal/PatientPortalLogin';
import PatientPortalDashboard from './pages/portal/PatientPortalDashboard';
```

Add two new `<Route>` entries — an unauthenticated one for login (following the `/login/hospital` precedent) and a `PATIENT`-guarded one for the dashboard (following the existing `<ProtectedRoute allowedRoles={[...]}>` precedent), placed alongside the other public routes and protected routes respectively:

```jsx
                    <Route
                        path="/portal/:hospitalId/login"
                        element={
                            <PageMeta title="HMS - Patient Portal">
                                <PatientPortalLogin />
                            </PageMeta>
                        }
                    />

                    <Route
                        path="/portal/dashboard"
                        element={
                            <ProtectedRoute allowedRoles={['PATIENT']}>
                                <PageMeta title="HMS - My Health Portal">
                                    <PatientPortalDashboard />
                                </PageMeta>
                            </ProtectedRoute>
                        }
                    />
```

- [ ] **Step 5: Run the frontend build**

Run: `cd frontend && npm run build`
Expected: `tsc` and `vite build` both succeed, no type errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/services/patientPortalService.js frontend/src/pages/portal/PatientPortalLogin.jsx frontend/src/pages/portal/PatientPortalDashboard.jsx frontend/src/App.jsx
git commit -m "feat(portal): patient login and read-only dashboard UI (Form 40 phase 1)"
```

---

### Task 10: Manual verification

- [ ] **Step 1: Start the backend and frontend dev servers**

```bash
cd backend && mvn spring-boot:run
```
```bash
cd frontend && npm run dev
```

- [ ] **Step 2: Manually exercise the flow**

1. Pick a hospital ID and an existing patient's phone number from your test data (e.g. from `setup/test-data-doctors.sql` or any patient created via the admin UI).
2. Visit `http://localhost:5173/portal/<hospitalId>/login`, enter the mobile number, submit.
3. Check the backend console for a `[DEV-OTP]` log line (since `MSG91_AUTH_KEY` won't be configured yet) — copy the 6-digit code.
4. Enter it on the OTP screen — expect redirect to `/portal/dashboard` showing the patient's own appointments/reports/prescriptions/billing.
5. Confirm a lab/radiology order that is `VERIFIED` but not `RELEASED` does **not** appear under Released Reports (create one via the existing staff LIS/RIS flow if needed to check this negative case).
6. Confirm entering the wrong OTP 5 times locks the account (subsequent OTP requests return the "temporarily locked" error).

- [ ] **Step 3: Report results**

Note any discrepancies from the expected flow before considering this plan complete.
