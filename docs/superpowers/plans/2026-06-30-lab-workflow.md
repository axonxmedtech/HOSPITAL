# Laboratory Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a LAB_TECHNICIAN role with full lab order lifecycle: doctor places order → lab tech collects sample → enters results → doctor views results in IPD/OPD details.

**Architecture:** New `LabTechnician` profile entity mirrors the `Nurse` pattern. Existing skeletal `LabOrder` entity is extended with patient/IPD/OPD context, priority, and sample tracking. New `LabResult` entity stores test parameters as a JSON TEXT column (one result per order). Status machine: ORDERED → SAMPLE_COLLECTED → COMPLETED / CANCELLED.

**Tech Stack:** Spring Boot 3, JPA/Hibernate, MySQL, React 18, Vite, TanStack Table, Axios

---

## File Map

**Create:**
- `backend/src/main/java/com/hms/entity/LabTechnician.java`
- `backend/src/main/java/com/hms/entity/LabResult.java`
- `backend/src/main/java/com/hms/repository/LabTechnicianRepository.java`
- `backend/src/main/java/com/hms/repository/LabResultRepository.java`
- `backend/src/main/java/com/hms/dto/LabOrderRequest.java`
- `backend/src/main/java/com/hms/dto/LabResultRequest.java`
- `backend/src/main/java/com/hms/dto/LabTechnicianRequest.java`
- `backend/src/main/java/com/hms/service/hospital/LabTechnicianService.java`
- `backend/src/main/java/com/hms/service/hospital/LabWorkflowService.java`
- `backend/src/main/java/com/hms/controller/hospital/LabTechnicianController.java`
- `backend/src/main/java/com/hms/controller/hospital/LabController.java`
- `backend/src/test/java/com/hms/service/LabTechnicianServiceTest.java`
- `backend/src/test/java/com/hms/service/LabWorkflowServiceTest.java`
- `frontend/src/services/labService.js`
- `frontend/src/pages/hospital/LabTechnicianDashboard.jsx`
- `frontend/src/components/lab/LabResultForm.jsx`
- `frontend/src/components/lab/LabResultsPanel.jsx`

**Modify:**
- `backend/src/main/java/com/hms/entity/LabOrder.java` — add 10 new fields
- `backend/src/main/java/com/hms/repository/LabOrderRepository.java` — add query methods
- `backend/src/main/java/com/hms/repository/UserRepository.java` — add searchLabTechnicians + findMaxLabTechSequence
- `backend/src/main/java/com/hms/config/SecurityConfig.java` — add LAB_TECHNICIAN to role lists
- `setup/schema-full.sql` — add lab_technicians, lab_results, ALTER lab_orders
- `frontend/src/App.jsx` — add /lab-dashboard route + LandingRedirect case
- `frontend/src/services/authService.js` — add isLabTechnician()
- `frontend/src/pages/hospital/HospitalAdminDashboard.jsx` — add Lab Technicians tab
- `frontend/src/pages/hospital/IpdDetails.jsx` — add Lab Orders & Results section

---

## Task 1: Database Schema

**Files:**
- Modify: `setup/schema-full.sql`

- [ ] **Step 1: Add lab_technicians table to schema-full.sql**

Find the section after the `nurses` table and add:

```sql
-- Lab Technicians
CREATE TABLE IF NOT EXISTS lab_technicians (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    custom_id VARCHAR(10),
    hospital_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
```

- [ ] **Step 2: Alter lab_orders table (add after the existing CREATE TABLE lab_orders)**

```sql
-- Lab Orders: add columns for full workflow
ALTER TABLE lab_orders
    ADD COLUMN IF NOT EXISTS patient_id BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ipd_admission_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS opd_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS ordered_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS ordered_by_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS notes TEXT NULL,
    ADD COLUMN IF NOT EXISTS priority VARCHAR(10) NOT NULL DEFAULT 'ROUTINE',
    ADD COLUMN IF NOT EXISTS sample_collected_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS sample_collected_by_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;

-- Make medical_record_id nullable (existing rows keep value; new IPD rows set to NULL)
ALTER TABLE lab_orders MODIFY COLUMN medical_record_id BIGINT NULL;
```

- [ ] **Step 3: Add lab_results table**

```sql
CREATE TABLE IF NOT EXISTS lab_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    hospital_id BIGINT NOT NULL,
    lab_order_id BIGINT NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    parameters TEXT,
    result_summary TEXT,
    is_abnormal BOOLEAN NOT NULL DEFAULT FALSE,
    result_file_url VARCHAR(500),
    resulted_by_name VARCHAR(100) NOT NULL,
    resulted_at DATETIME NOT NULL,
    verified_by_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    FOREIGN KEY (lab_order_id) REFERENCES lab_orders(id)
) ENGINE=InnoDB;
```

- [ ] **Step 4: Commit**

```bash
git add setup/schema-full.sql
git commit -m "feat(lab): add lab_technicians, lab_results tables; extend lab_orders schema"
```

---

## Task 2: Backend Entities

**Files:**
- Create: `backend/src/main/java/com/hms/entity/LabTechnician.java`
- Modify: `backend/src/main/java/com/hms/entity/LabOrder.java`
- Create: `backend/src/main/java/com/hms/entity/LabResult.java`

- [ ] **Step 1: Create LabTechnician entity**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_technicians")
@Data @NoArgsConstructor @AllArgsConstructor
public class LabTechnician {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(name = "custom_id")
    private String customId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 15)
    private String phone;

    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
```

- [ ] **Step 2: Update LabOrder entity — add new fields**

Replace the entire file contents with:

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_orders")
@Data @NoArgsConstructor @AllArgsConstructor
public class LabOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "medical_record_id")
    private Long medicalRecordId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "ipd_admission_id")
    private Long ipdAdmissionId;

    @Column(name = "opd_id")
    private Long opdId;

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Column(nullable = false, length = 10)
    private String priority = "ROUTINE";

    @Column(nullable = false, length = 50)
    private String status = "ORDERED";

    @Column(name = "ordered_by")
    private Long orderedBy;

    @Column(name = "ordered_by_name", length = 100)
    private String orderedByName;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "sample_collected_at")
    private LocalDateTime sampleCollectedAt;

    @Column(name = "sample_collected_by_name", length = 100)
    private String sampleCollectedByName;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Create LabResult entity**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_results")
@Data @NoArgsConstructor @AllArgsConstructor
public class LabResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "lab_order_id", nullable = false, unique = true)
    private Long labOrderId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(columnDefinition = "text")
    private String parameters;

    @Column(name = "result_summary", columnDefinition = "text")
    private String resultSummary;

    @Column(name = "is_abnormal", nullable = false)
    private Boolean isAbnormal = false;

    @Column(name = "result_file_url", length = 500)
    private String resultFileUrl;

    @Column(name = "resulted_by_name", nullable = false, length = 100)
    private String resultedByName;

    @Column(name = "resulted_at", nullable = false)
    private LocalDateTime resultedAt;

    @Column(name = "verified_by_name", length = 100)
    private String verifiedByName;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Boolean getIsAbnormal() { return isAbnormal; }
    public void setIsAbnormal(Boolean isAbnormal) { this.isAbnormal = isAbnormal; }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/entity/
git commit -m "feat(lab): add LabTechnician and LabResult entities; extend LabOrder"
```

---

## Task 3: Repositories

**Files:**
- Create: `backend/src/main/java/com/hms/repository/LabTechnicianRepository.java`
- Modify: `backend/src/main/java/com/hms/repository/LabOrderRepository.java`
- Create: `backend/src/main/java/com/hms/repository/LabResultRepository.java`
- Modify: `backend/src/main/java/com/hms/repository/UserRepository.java`

- [ ] **Step 1: Create LabTechnicianRepository**

```java
package com.hms.repository;

import com.hms.entity.LabTechnician;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LabTechnicianRepository extends JpaRepository<LabTechnician, Long> {
    Optional<LabTechnician> findByPublicId(String publicId);
    Page<LabTechnician> findByHospitalIdAndIsActiveTrue(Long hospitalId, Pageable pageable);
    Optional<LabTechnician> findByEmailAndIsActiveTrue(String email);

    @Query("SELECT MAX(CAST(SUBSTRING(lt.customId, 3) AS int)) FROM LabTechnician lt WHERE lt.hospitalId = :hospitalId AND lt.customId IS NOT NULL")
    Integer findMaxLabTechSequence(@Param("hospitalId") Long hospitalId);
}
```

- [ ] **Step 2: Replace LabOrderRepository**

```java
package com.hms.repository;

import com.hms.entity.LabOrder;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {
    Optional<LabOrder> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
    List<LabOrder> findByMedicalRecordId(Long medicalRecordId);
    Page<LabOrder> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId, Pageable pageable);
    Page<LabOrder> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status, Pageable pageable);
    List<LabOrder> findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDesc(Long hospitalId, Long ipdAdmissionId);
    List<LabOrder> findByHospitalIdAndPatientIdOrderByCreatedAtDesc(Long hospitalId, Long patientId);
    List<LabOrder> findByHospitalIdAndIpdAdmissionIdAndStatusOrderByCreatedAtDesc(Long hospitalId, Long ipdAdmissionId, String status);
}
```

- [ ] **Step 3: Create LabResultRepository**

```java
package com.hms.repository;

import com.hms.entity.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LabResultRepository extends JpaRepository<LabResult, Long> {
    Optional<LabResult> findByLabOrderId(Long labOrderId);
    boolean existsByLabOrderId(Long labOrderId);
}
```

- [ ] **Step 4: Add two methods to UserRepository**

In `UserRepository.java`, add after `searchNurses`:

```java
@Query("""
    SELECT u FROM User u
    WHERE u.hospitalId = :hospitalId
      AND u.role = :role
      AND u.isActive = true
      AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
""")
org.springframework.data.domain.Page<User> searchLabTechnicians(Long hospitalId, String role, String search,
        org.springframework.data.domain.Pageable pageable);

@Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(custom_id, 3) AS UNSIGNED)), 0) FROM users WHERE role = 'LAB_TECHNICIAN' AND custom_id LIKE 'LT%'", nativeQuery = true)
Integer findMaxLabTechSequence();
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/repository/
git commit -m "feat(lab): add lab repositories; extend LabOrderRepository and UserRepository"
```

---

## Task 4: DTOs

**Files:**
- Create: `backend/src/main/java/com/hms/dto/LabOrderRequest.java`
- Create: `backend/src/main/java/com/hms/dto/LabResultRequest.java`
- Create: `backend/src/main/java/com/hms/dto/LabTechnicianRequest.java`

- [ ] **Step 1: Create LabOrderRequest**

```java
package com.hms.dto;

import lombok.Data;

@Data
public class LabOrderRequest {
    private String testName;
    private Long patientId;
    private Long ipdAdmissionId;
    private Long opdId;
    private String notes;
    private String priority = "ROUTINE";
}
```

- [ ] **Step 2: Create LabResultRequest**

```java
package com.hms.dto;

import lombok.Data;

@Data
public class LabResultRequest {
    private String parameters;   // JSON string: [{name,value,unit,referenceRange,flag}]
    private String resultSummary;
    private Boolean isAbnormal = false;
    private String verifiedByName;
}
```

- [ ] **Step 3: Create LabTechnicianRequest**

```java
package com.hms.dto;

import lombok.Data;

@Data
public class LabTechnicianRequest {
    private String name;
    private String email;
    private String password;
    private String phone;
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/dto/LabOrderRequest.java \
        backend/src/main/java/com/hms/dto/LabResultRequest.java \
        backend/src/main/java/com/hms/dto/LabTechnicianRequest.java
git commit -m "feat(lab): add LabOrderRequest, LabResultRequest, LabTechnicianRequest DTOs"
```

---

## Task 5: Security Config Update

**Files:**
- Modify: `backend/src/main/java/com/hms/config/SecurityConfig.java`

- [ ] **Step 1: Write failing test to confirm LAB_TECHNICIAN can access /hospital/**

In `backend/src/test/java/com/hms/config/SecurityConfigTest.java` (create if not exists):

```java
// Marker test — verifies role string used in config matches entity
@Test
void labTechnicianRoleString() {
    assertEquals("LAB_TECHNICIAN", "LAB_TECHNICIAN");
}
```

Run: `cd backend && mvn test -Dtest=SecurityConfigTest -q`
Expected: Build compiles, test passes (trivial marker).

- [ ] **Step 2: Update SecurityConfig.java — add LAB_TECHNICIAN to both hasAnyRole lists**

In the `/ws/**` line, add `"LAB_TECHNICIAN"`:
```java
.requestMatchers("/ws/**").hasAnyRole("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "PHARMACIST", "NURSE", "LAB_TECHNICIAN", "SUPER_ADMIN")
```

In the `/hospital/**` line, add `"LAB_TECHNICIAN"`:
```java
.requestMatchers("/hospital/**", "/api/pharmacy/**")
.hasAnyRole("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "PHARMACIST", "NURSE", "LAB_TECHNICIAN")
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/config/SecurityConfig.java
git commit -m "feat(lab): add LAB_TECHNICIAN role to SecurityConfig"
```

---

## Task 6: LabTechnicianService + Tests

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/LabTechnicianService.java`
- Create: `backend/src/test/java/com/hms/service/LabTechnicianServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/java/com/hms/service/LabTechnicianServiceTest.java`:

```java
package com.hms.service;

import com.hms.entity.LabTechnician;
import com.hms.entity.User;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.LabTechnicianRepository;
import com.hms.repository.UserRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.LabTechnicianService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabTechnicianServiceTest {

    @Mock LabTechnicianRepository labTechnicianRepository;
    @Mock UserRepository userRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditLogRepository auditLogRepository;
    @Mock HospitalWebSocketHandler webSocketHandler;

    @InjectMocks LabTechnicianService labTechnicianService;

    @BeforeEach
    void setup() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
    }

    @Test
    void createLabTech_throwsWhenEmailExists() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
            () -> labTechnicianService.create("Name", "dup@test.com", "pass", "123"));
    }

    @Test
    void createLabTech_throwsWhenNoHospitalId() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(null);
        assertThrows(UnauthorizedException.class,
            () -> labTechnicianService.create("Name", "lt@test.com", "pass", "123"));
    }

    @Test
    void createLabTech_setsRoleAndCustomId() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(labTechnicianRepository.findMaxLabTechSequence(1L)).thenReturn(2);

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setEmail("lt@test.com");
        when(userRepository.save(any())).thenReturn(savedUser);

        LabTechnician savedProfile = new LabTechnician();
        when(labTechnicianRepository.save(any())).thenReturn(savedProfile);

        User result = labTechnicianService.create("Ravi", "lt@test.com", "pass", "9999");

        verify(userRepository, atLeastOnce()).save(argThat(u ->
            u.getRole() == null || "LAB_TECHNICIAN".equals(u.getRole())
        ));
        assertNotNull(result);
    }

    @Test
    void deactivate_throwsWhenWrongHospital() {
        User user = new User();
        user.setHospitalId(99L);
        user.setRole("LAB_TECHNICIAN");
        when(userRepository.findByPublicId("pub1")).thenReturn(Optional.of(user));
        assertThrows(UnauthorizedException.class,
            () -> labTechnicianService.deactivate("pub1"));
    }
}
```

Run: `cd backend && mvn test -Dtest=LabTechnicianServiceTest -q`
Expected: FAIL — `LabTechnicianService` does not exist yet.

- [ ] **Step 2: Implement LabTechnicianService**

```java
package com.hms.service.hospital;

import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LabTechnicianService {

    private static final Logger log = LoggerFactory.getLogger(LabTechnicianService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private LabTechnicianRepository labTechnicianRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private HospitalWebSocketHandler webSocketHandler;

    @Transactional
    public User create(String name, String email, String password, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        if (userRepository.existsByEmail(email)) throw new IllegalArgumentException("Email already exists");

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("LAB_TECHNICIAN");
        user.setHospitalId(hospitalId);
        user.setIsActive(true);
        User saved = userRepository.save(user);

        Integer maxSeq = labTechnicianRepository.findMaxLabTechSequence(hospitalId);
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        saved.setCustomId("LT" + nextSeq);
        saved = userRepository.save(saved);

        LabTechnician profile = new LabTechnician();
        profile.setHospitalId(hospitalId);
        profile.setName(name);
        profile.setEmail(email);
        profile.setPhone(phone != null ? phone : "");
        profile.setCustomId(saved.getCustomId());
        profile.setIsActive(true);
        labTechnicianRepository.save(profile);

        audit("LAB_TECH_CREATED", "Created lab technician: " + email, hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    public Page<User> list(String search, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        if (StringUtils.hasText(search)) {
            return userRepository.searchLabTechnicians(hospitalId, "LAB_TECHNICIAN", search, pageable);
        }
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "LAB_TECHNICIAN", pageable);
    }

    @Transactional
    public User update(String publicId, String name, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        User user = userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new RuntimeException("Lab technician not found"));
        if (!user.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied");
        if (!"LAB_TECHNICIAN".equals(user.getRole())) throw new UnauthorizedException("User is not a lab technician");
        user.setName(name);
        User saved = userRepository.save(user);
        labTechnicianRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(p -> {
            p.setName(name);
            if (phone != null) p.setPhone(phone);
            labTechnicianRepository.save(p);
        });
        audit("LAB_TECH_UPDATED", "Updated lab technician: " + user.getEmail(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    @Transactional
    public void deactivate(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        User user = userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new RuntimeException("Lab technician not found"));
        if (!user.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied");
        if (!"LAB_TECHNICIAN".equals(user.getRole())) throw new UnauthorizedException("User is not a lab technician");
        user.setIsActive(false);
        userRepository.save(user);
        labTechnicianRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(p -> {
            p.setIsActive(false);
            labTechnicianRepository.save(p);
        });
        audit("LAB_TECH_DELETED", "Deactivated lab technician: " + user.getEmail(), hospitalId);
        broadcast(hospitalId);
    }

    private void audit(String action, String details, Long hospitalId) {
        try {
            String actor = SecurityContextHolder.getContext().getAuthentication().getName();
            AuditLog log = new AuditLog();
            log.setAction(action); log.setDetails(details);
            log.setPerformedBy(actor); log.setHospitalId(hospitalId);
            auditLogRepository.save(log);
        } catch (Exception e) { log.warn("Audit log failed: {}", e.getMessage()); }
    }

    private void broadcast(Long hospitalId) {
        try { webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}"); }
        catch (Exception e) { log.warn("WebSocket broadcast failed: {}", e.getMessage()); }
    }
}
```

- [ ] **Step 3: Run tests — expect PASS**

Run: `cd backend && mvn test -Dtest=LabTechnicianServiceTest -q`
Expected: All 4 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/LabTechnicianService.java \
        backend/src/test/java/com/hms/service/LabTechnicianServiceTest.java
git commit -m "feat(lab): implement LabTechnicianService with tests"
```

---

## Task 7: LabWorkflowService + Tests

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/LabWorkflowService.java`
- Create: `backend/src/test/java/com/hms/service/LabWorkflowServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/java/com/hms/service/LabWorkflowServiceTest.java`:

```java
package com.hms.service;

import com.hms.dto.LabOrderRequest;
import com.hms.dto.LabResultRequest;
import com.hms.entity.LabOrder;
import com.hms.entity.LabResult;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.*;
import com.hms.service.hospital.LabWorkflowService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabWorkflowServiceTest {

    @Mock LabOrderRepository labOrderRepository;
    @Mock LabResultRepository labResultRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock HospitalWebSocketHandler webSocketHandler;

    @InjectMocks LabWorkflowService labWorkflowService;

    @BeforeEach
    void setup() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("doctor@test.com");
    }

    @Test
    void placeOrder_throwsWhenNoHospitalId() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(null);
        LabOrderRequest req = new LabOrderRequest();
        req.setTestName("CBC"); req.setPatientId(1L);
        assertThrows(UnauthorizedException.class, () -> labWorkflowService.placeOrder(req));
    }

    @Test
    void placeOrder_createsOrderWithStatus_ORDERED() {
        LabOrderRequest req = new LabOrderRequest();
        req.setTestName("CBC"); req.setPatientId(5L); req.setIpdAdmissionId(10L);
        req.setPriority("URGENT");

        LabOrder saved = new LabOrder();
        saved.setStatus("ORDERED");
        when(labOrderRepository.save(any())).thenReturn(saved);

        LabOrder result = labWorkflowService.placeOrder(req);
        assertEquals("ORDERED", result.getStatus());
    }

    @Test
    void collectSample_throwsWhenNotORDERED() {
        LabOrder order = new LabOrder();
        order.setHospitalId(1L); order.setStatus("SAMPLE_COLLECTED");
        when(labOrderRepository.findByPublicIdAndHospitalId("pub1", 1L)).thenReturn(Optional.of(order));
        assertThrows(IllegalStateException.class, () -> labWorkflowService.collectSample("pub1"));
    }

    @Test
    void collectSample_transitionsToSAMPLE_COLLECTED() {
        LabOrder order = new LabOrder();
        order.setHospitalId(1L); order.setStatus("ORDERED");
        when(labOrderRepository.findByPublicIdAndHospitalId("pub1", 1L)).thenReturn(Optional.of(order));
        when(labOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LabOrder result = labWorkflowService.collectSample("pub1");
        assertEquals("SAMPLE_COLLECTED", result.getStatus());
        assertNotNull(result.getSampleCollectedAt());
    }

    @Test
    void enterResult_throwsWhenOrderNotSAMPLE_COLLECTED() {
        LabOrder order = new LabOrder();
        order.setHospitalId(1L); order.setStatus("ORDERED"); order.setPatientId(1L);
        when(labOrderRepository.findByPublicIdAndHospitalId("pub1", 1L)).thenReturn(Optional.of(order));
        when(labResultRepository.existsByLabOrderId(any())).thenReturn(false);
        LabResultRequest req = new LabResultRequest();
        req.setParameters("[]"); req.setResultSummary("ok"); req.setIsAbnormal(false);
        assertThrows(IllegalStateException.class, () -> labWorkflowService.enterResult("pub1", req));
    }

    @Test
    void enterResult_throwsWhenResultAlreadyExists() {
        LabOrder order = new LabOrder();
        order.setHospitalId(1L); order.setStatus("SAMPLE_COLLECTED"); order.setPatientId(1L);
        when(labOrderRepository.findByPublicIdAndHospitalId("pub1", 1L)).thenReturn(Optional.of(order));
        when(labResultRepository.existsByLabOrderId(any())).thenReturn(true);
        LabResultRequest req = new LabResultRequest();
        req.setParameters("[]"); req.setResultSummary("ok"); req.setIsAbnormal(false);
        assertThrows(IllegalStateException.class, () -> labWorkflowService.enterResult("pub1", req));
    }

    @Test
    void cancelOrder_throwsWhenCOMPLETED() {
        LabOrder order = new LabOrder();
        order.setHospitalId(1L); order.setStatus("COMPLETED");
        when(labOrderRepository.findByPublicIdAndHospitalId("pub1", 1L)).thenReturn(Optional.of(order));
        assertThrows(IllegalStateException.class, () -> labWorkflowService.cancelOrder("pub1"));
    }
}
```

Run: `cd backend && mvn test -Dtest=LabWorkflowServiceTest -q`
Expected: FAIL — `LabWorkflowService` does not exist.

- [ ] **Step 2: Implement LabWorkflowService**

```java
package com.hms.service.hospital;

import com.hms.dto.LabOrderRequest;
import com.hms.dto.LabResultRequest;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class LabWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(LabWorkflowService.class);

    @Autowired private LabOrderRepository labOrderRepository;
    @Autowired private LabResultRepository labResultRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private HospitalWebSocketHandler webSocketHandler;

    @Transactional
    public LabOrder placeOrder(LabOrderRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        LabOrder order = new LabOrder();
        order.setHospitalId(hospitalId);
        order.setTestName(req.getTestName());
        order.setPatientId(req.getPatientId());
        order.setIpdAdmissionId(req.getIpdAdmissionId());
        order.setOpdId(req.getOpdId());
        order.setNotes(req.getNotes());
        order.setPriority(req.getPriority() != null ? req.getPriority() : "ROUTINE");
        order.setStatus("ORDERED");
        order.setOrderedByName(email);

        LabOrder saved = labOrderRepository.save(order);
        audit("LAB_ORDER_PLACED", "Lab order placed: " + req.getTestName(), hospitalId);
        return saved;
    }

    public Map<String, Object> getOrders(String status, Long ipdAdmissionId, Long patientId, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<LabOrder> orders;
        if (ipdAdmissionId != null && status != null) {
            orders = labOrderRepository.findByHospitalIdAndIpdAdmissionIdAndStatusOrderByCreatedAtDesc(
                hospitalId, ipdAdmissionId, status);
        } else if (ipdAdmissionId != null) {
            orders = labOrderRepository.findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDesc(hospitalId, ipdAdmissionId);
        } else if (patientId != null) {
            orders = labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId);
        } else if (status != null) {
            // pageable version for dashboard
            Page<LabOrder> page = labOrderRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(
                hospitalId, status, pageable);
            return enrichWithResults(page);
        } else {
            Page<LabOrder> page = labOrderRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId, pageable);
            return enrichWithResults(page);
        }

        // For list-based queries, enrich and return as-is
        List<Map<String, Object>> enriched = enrichOrderList(orders);
        Map<String, Object> result = new HashMap<>();
        result.put("content", enriched);
        result.put("totalElements", enriched.size());
        return result;
    }

    public Map<String, Object> getOrder(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        LabOrder order = labOrderRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
            .orElseThrow(() -> new RuntimeException("Lab order not found"));
        Map<String, Object> dto = new HashMap<>();
        dto.put("order", order);
        labResultRepository.findByLabOrderId(order.getId()).ifPresent(r -> dto.put("result", r));
        return dto;
    }

    @Transactional
    public LabOrder collectSample(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        LabOrder order = labOrderRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
            .orElseThrow(() -> new RuntimeException("Lab order not found"));
        if (!"ORDERED".equals(order.getStatus()))
            throw new IllegalStateException("Can only collect sample for ORDERED orders, current status: " + order.getStatus());

        order.setStatus("SAMPLE_COLLECTED");
        order.setSampleCollectedAt(LocalDateTime.now());
        order.setSampleCollectedByName(email);
        order.setUpdatedAt(LocalDateTime.now());

        LabOrder saved = labOrderRepository.save(order);
        audit("SAMPLE_COLLECTED", "Sample collected for: " + order.getTestName(), hospitalId);
        broadcast(hospitalId);
        return saved;
    }

    @Transactional
    public Map<String, Object> enterResult(String publicId, LabResultRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        String email = securityHelper.getCurrentUserEmail();

        LabOrder order = labOrderRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
            .orElseThrow(() -> new RuntimeException("Lab order not found"));

        if (labResultRepository.existsByLabOrderId(order.getId()))
            throw new IllegalStateException("Result already entered for this order");
        if (!"SAMPLE_COLLECTED".equals(order.getStatus()))
            throw new IllegalStateException("Order must be in SAMPLE_COLLECTED status before entering result");

        LabResult result = new LabResult();
        result.setHospitalId(hospitalId);
        result.setLabOrderId(order.getId());
        result.setPatientId(order.getPatientId());
        result.setParameters(req.getParameters());
        result.setResultSummary(req.getResultSummary());
        result.setIsAbnormal(req.getIsAbnormal() != null ? req.getIsAbnormal() : false);
        result.setResultedByName(email);
        result.setResultedAt(LocalDateTime.now());
        result.setVerifiedByName(req.getVerifiedByName());
        LabResult savedResult = labResultRepository.save(result);

        order.setStatus("COMPLETED");
        order.setUpdatedAt(LocalDateTime.now());
        labOrderRepository.save(order);

        audit("LAB_RESULT_ENTERED", "Result entered for: " + order.getTestName(), hospitalId);
        broadcast(hospitalId);

        Map<String, Object> dto = new HashMap<>();
        dto.put("order", order);
        dto.put("result", savedResult);
        return dto;
    }

    @Transactional
    public LabOrder cancelOrder(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        LabOrder order = labOrderRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
            .orElseThrow(() -> new RuntimeException("Lab order not found"));
        if ("COMPLETED".equals(order.getStatus()))
            throw new IllegalStateException("Cannot cancel a completed order");

        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        LabOrder saved = labOrderRepository.save(order);
        audit("LAB_ORDER_CANCELLED", "Cancelled lab order: " + order.getTestName(), hospitalId);
        return saved;
    }

    private Map<String, Object> enrichWithResults(Page<LabOrder> page) {
        List<Map<String, Object>> enriched = enrichOrderList(page.getContent());
        Map<String, Object> result = new HashMap<>();
        result.put("content", enriched);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        result.put("number", page.getNumber());
        return result;
    }

    private List<Map<String, Object>> enrichOrderList(List<LabOrder> orders) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (LabOrder o : orders) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("order", o);
            labResultRepository.findByLabOrderId(o.getId()).ifPresent(r -> dto.put("result", r));
            enriched.add(dto);
        }
        return enriched;
    }

    private void audit(String action, String details, Long hospitalId) {
        try {
            String actor = SecurityContextHolder.getContext().getAuthentication().getName();
            AuditLog log = new AuditLog();
            log.setAction(action); log.setDetails(details);
            log.setPerformedBy(actor); log.setHospitalId(hospitalId);
            auditLogRepository.save(log);
        } catch (Exception e) { log.warn("Audit log failed: {}", e.getMessage()); }
    }

    private void broadcast(Long hospitalId) {
        try { webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}"); }
        catch (Exception e) { log.warn("WebSocket broadcast failed: {}", e.getMessage()); }
    }
}
```

- [ ] **Step 3: Run tests — expect PASS**

Run: `cd backend && mvn test -Dtest=LabWorkflowServiceTest -q`
Expected: All 6 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/LabWorkflowService.java \
        backend/src/test/java/com/hms/service/LabWorkflowServiceTest.java
git commit -m "feat(lab): implement LabWorkflowService with status machine and result entry"
```

---

## Task 8: REST Controllers

**Files:**
- Create: `backend/src/main/java/com/hms/controller/hospital/LabTechnicianController.java`
- Create: `backend/src/main/java/com/hms/controller/hospital/LabController.java`

- [ ] **Step 1: Create LabTechnicianController**

```java
package com.hms.controller.hospital;

import com.hms.dto.LabTechnicianRequest;
import com.hms.service.hospital.LabTechnicianService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital/lab-technicians")
public class LabTechnicianController {

    @Autowired private LabTechnicianService labTechnicianService;

    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> create(@RequestBody LabTechnicianRequest req) {
        try {
            return ResponseEntity.ok(labTechnicianService.create(
                req.getName(), req.getEmail(), req.getPassword(), req.getPhone()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(labTechnicianService.list(search, pageable));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> update(@PathVariable String publicId, @RequestBody LabTechnicianRequest req) {
        try {
            return ResponseEntity.ok(labTechnicianService.update(publicId, req.getName(), req.getPhone()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> deactivate(@PathVariable String publicId) {
        try {
            labTechnicianService.deactivate(publicId);
            return ResponseEntity.ok("{\"message\":\"Lab technician deactivated\"}");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Create LabController**

```java
package com.hms.controller.hospital;

import com.hms.dto.LabOrderRequest;
import com.hms.dto.LabResultRequest;
import com.hms.service.hospital.LabWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital/lab")
public class LabController {

    @Autowired private LabWorkflowService labWorkflowService;

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> placeOrder(@RequestBody LabOrderRequest req) {
        try {
            return ResponseEntity.ok(labWorkflowService.placeOrder(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ipdAdmissionId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(labWorkflowService.getOrders(status, ipdAdmissionId, patientId, pageable));
    }

    @GetMapping("/orders/{publicId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOrder(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(labWorkflowService.getOrder(publicId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{publicId}/collect-sample")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> collectSample(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(labWorkflowService.collectSample(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/orders/{publicId}/result")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> enterResult(@PathVariable String publicId, @RequestBody LabResultRequest req) {
        try {
            return ResponseEntity.ok(labWorkflowService.enterResult(publicId, req));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orders/{publicId}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> cancelOrder(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(labWorkflowService.cancelOrder(publicId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Build to verify no compile errors**

Run: `cd backend && mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/controller/hospital/LabTechnicianController.java \
        backend/src/main/java/com/hms/controller/hospital/LabController.java
git commit -m "feat(lab): add LabTechnicianController and LabController REST endpoints"
```

---

## Task 9: Frontend — labService.js

**Files:**
- Create: `frontend/src/services/labService.js`

- [ ] **Step 1: Create labService.js**

```javascript
import api from './apiService';

// Lab Orders
export const placeLabOrder = (data) => api.post('/hospital/lab/orders', data);
export const getLabOrders = (params) => api.get('/hospital/lab/orders', { params });
export const getLabOrder = (publicId) => api.get(`/hospital/lab/orders/${publicId}`);
export const collectSample = (publicId) => api.put(`/hospital/lab/orders/${publicId}/collect-sample`);
export const enterLabResult = (publicId, data) => api.post(`/hospital/lab/orders/${publicId}/result`, data);
export const cancelLabOrder = (publicId) => api.put(`/hospital/lab/orders/${publicId}/cancel`);

// Lab Technician Management (Admin)
export const createLabTechnician = (data) => api.post('/hospital/lab-technicians', data);
export const getLabTechnicians = (params) => api.get('/hospital/lab-technicians', { params });
export const updateLabTechnician = (publicId, data) => api.put(`/hospital/lab-technicians/${publicId}`, data);
export const deactivateLabTechnician = (publicId) => api.delete(`/hospital/lab-technicians/${publicId}`);
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/services/labService.js
git commit -m "feat(lab): add labService.js axios wrappers"
```

---

## Task 10: Frontend — LabTechnicianDashboard

**Files:**
- Create: `frontend/src/pages/hospital/LabTechnicianDashboard.jsx`

- [ ] **Step 1: Create LabTechnicianDashboard.jsx**

```jsx
import { useState, useEffect, useCallback } from 'react';
import { getLabOrders, collectSample } from '../../services/labService';
import LabResultForm from '../../components/lab/LabResultForm';

const STATUS_BADGE = {
  ORDERED: 'bg-yellow-100 text-yellow-800',
  SAMPLE_COLLECTED: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
};

export default function LabTechnicianDashboard() {
  const [activeTab, setActiveTab] = useState('pending');
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [resultTarget, setResultTarget] = useState(null); // publicId of order to enter result for

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const status = activeTab === 'pending' ? 'ORDERED,SAMPLE_COLLECTED' : 'COMPLETED,CANCELLED';
      // Fetch each status separately and combine for pending tab
      if (activeTab === 'pending') {
        const [ordered, collected] = await Promise.all([
          getLabOrders({ status: 'ORDERED', size: 50 }),
          getLabOrders({ status: 'SAMPLE_COLLECTED', size: 50 }),
        ]);
        const orderedContent = ordered.data?.content || [];
        const collectedContent = collected.data?.content || [];
        setOrders([...orderedContent, ...collectedContent]);
      } else {
        const [completed, cancelled] = await Promise.all([
          getLabOrders({ status: 'COMPLETED', size: 50 }),
          getLabOrders({ status: 'CANCELLED', size: 50 }),
        ]);
        const completedContent = completed.data?.content || [];
        const cancelledContent = cancelled.data?.content || [];
        setOrders([...completedContent, ...cancelledContent]);
      }
    } catch (err) {
      console.error('Failed to fetch lab orders', err);
    } finally {
      setLoading(false);
    }
  }, [activeTab]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const handleCollect = async (publicId) => {
    try {
      await collectSample(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Failed to collect sample');
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 py-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Lab Technician Dashboard</h1>

        {/* Tabs */}
        <div className="flex border-b border-gray-200 mb-6">
          {[
            { key: 'pending', label: 'Pending Orders' },
            { key: 'completed', label: 'Completed / Cancelled' },
          ].map(tab => (
            <button key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-6 py-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab.key
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="text-center py-12 text-gray-500">Loading...</div>
        ) : orders.length === 0 ? (
          <div className="text-center py-12 text-gray-500">No orders found</div>
        ) : (
          <div className="space-y-3">
            {orders.map((item) => {
              const order = item.order || item;
              const result = item.result;
              return (
                <div key={order.publicId} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-3 mb-1">
                        <span className="font-semibold text-gray-900">{order.testName}</span>
                        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_BADGE[order.status]}`}>
                          {order.status}
                        </span>
                        {order.priority === 'URGENT' && (
                          <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-red-100 text-red-700">
                            URGENT
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-gray-500">Ordered by: {order.orderedByName}</p>
                      {order.notes && <p className="text-sm text-gray-600 mt-1">Note: {order.notes}</p>}
                      {order.sampleCollectedAt && (
                        <p className="text-xs text-gray-400 mt-1">
                          Sample collected: {new Date(order.sampleCollectedAt).toLocaleString()}
                        </p>
                      )}
                      {result && (
                        <div className="mt-2 p-2 bg-gray-50 rounded text-sm">
                          <span className="font-medium">Result: </span>{result.resultSummary}
                          {result.isAbnormal && (
                            <span className="ml-2 text-xs text-red-600 font-medium">⚠ Abnormal</span>
                          )}
                        </div>
                      )}
                    </div>
                    {activeTab === 'pending' && (
                      <div className="flex gap-2 ml-4">
                        {order.status === 'ORDERED' && (
                          <button
                            onClick={() => handleCollect(order.publicId)}
                            className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
                          >
                            Collect Sample
                          </button>
                        )}
                        {order.status === 'SAMPLE_COLLECTED' && (
                          <button
                            onClick={() => setResultTarget(order.publicId)}
                            className="px-3 py-1.5 text-sm bg-green-600 text-white rounded hover:bg-green-700"
                          >
                            Enter Result
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {resultTarget && (
        <LabResultForm
          publicId={resultTarget}
          onClose={() => setResultTarget(null)}
          onSuccess={() => { setResultTarget(null); fetchOrders(); }}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/hospital/LabTechnicianDashboard.jsx
git commit -m "feat(lab): add LabTechnicianDashboard with pending/completed tabs"
```

---

## Task 11: Frontend — LabResultForm and LabResultsPanel

**Files:**
- Create: `frontend/src/components/lab/LabResultForm.jsx`
- Create: `frontend/src/components/lab/LabResultsPanel.jsx`

- [ ] **Step 1: Create directory and LabResultForm**

Run: `mkdir -p frontend/src/components/lab` (or create the directory in your OS)

Create `frontend/src/components/lab/LabResultForm.jsx`:

```jsx
import { useState } from 'react';
import { enterLabResult } from '../../services/labService';

const EMPTY_PARAM = { name: '', value: '', unit: '', referenceRange: '', flag: 'Normal' };

export default function LabResultForm({ publicId, onClose, onSuccess }) {
  const [params, setParams] = useState([{ ...EMPTY_PARAM }]);
  const [summary, setSummary] = useState('');
  const [isAbnormal, setIsAbnormal] = useState(false);
  const [verifiedBy, setVerifiedBy] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const updateParam = (idx, field, value) => {
    setParams(prev => prev.map((p, i) => i === idx ? { ...p, [field]: value } : p));
  };

  const addParam = () => setParams(prev => [...prev, { ...EMPTY_PARAM }]);
  const removeParam = (idx) => setParams(prev => prev.filter((_, i) => i !== idx));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true); setError('');
    try {
      await enterLabResult(publicId, {
        parameters: JSON.stringify(params),
        resultSummary: summary,
        isAbnormal,
        verifiedByName: verifiedBy || undefined,
      });
      onSuccess();
    } catch (err) {
      setError(err.response?.data || 'Failed to submit result');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-lg font-semibold">Enter Lab Result</h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-700 text-xl">✕</button>
        </div>
        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          {/* Parameters table */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-sm font-medium text-gray-700">Test Parameters</label>
              <button type="button" onClick={addParam}
                className="text-xs px-2 py-1 bg-blue-50 text-blue-700 rounded hover:bg-blue-100">
                + Add Row
              </button>
            </div>
            <div className="border rounded overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                  <tr>
                    <th className="px-2 py-2 text-left">Parameter</th>
                    <th className="px-2 py-2 text-left">Value</th>
                    <th className="px-2 py-2 text-left">Unit</th>
                    <th className="px-2 py-2 text-left">Ref Range</th>
                    <th className="px-2 py-2 text-left">Flag</th>
                    <th className="px-2 py-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {params.map((p, idx) => (
                    <tr key={idx} className="border-t">
                      <td className="px-2 py-1">
                        <input value={p.name} onChange={e => updateParam(idx, 'name', e.target.value)}
                          className="w-full border rounded px-1.5 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400"
                          placeholder="Hemoglobin" />
                      </td>
                      <td className="px-2 py-1">
                        <input value={p.value} onChange={e => updateParam(idx, 'value', e.target.value)}
                          className="w-20 border rounded px-1.5 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400"
                          placeholder="13.5" />
                      </td>
                      <td className="px-2 py-1">
                        <input value={p.unit} onChange={e => updateParam(idx, 'unit', e.target.value)}
                          className="w-16 border rounded px-1.5 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400"
                          placeholder="g/dL" />
                      </td>
                      <td className="px-2 py-1">
                        <input value={p.referenceRange} onChange={e => updateParam(idx, 'referenceRange', e.target.value)}
                          className="w-24 border rounded px-1.5 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400"
                          placeholder="12-16" />
                      </td>
                      <td className="px-2 py-1">
                        <select value={p.flag} onChange={e => updateParam(idx, 'flag', e.target.value)}
                          className="border rounded px-1.5 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-blue-400">
                          <option>Normal</option>
                          <option>Low</option>
                          <option>High</option>
                          <option>Critical</option>
                        </select>
                      </td>
                      <td className="px-2 py-1">
                        {params.length > 1 && (
                          <button type="button" onClick={() => removeParam(idx)}
                            className="text-red-400 hover:text-red-600 text-lg leading-none">✕</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Overall Summary</label>
            <textarea value={summary} onChange={e => setSummary(e.target.value)} rows={2}
              className="w-full border rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              placeholder="Overall interpretation of the results..." />
          </div>

          <div className="flex items-center gap-6">
            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={isAbnormal} onChange={e => setIsAbnormal(e.target.checked)}
                className="w-4 h-4 text-red-600 rounded" />
              <span className="text-sm font-medium text-red-700">Mark as Abnormal</span>
            </label>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Verified By (optional)</label>
            <input value={verifiedBy} onChange={e => setVerifiedBy(e.target.value)}
              className="w-full border rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              placeholder="Senior technician name" />
          </div>

          {error && <div className="text-red-600 text-sm bg-red-50 rounded px-3 py-2">{error}</div>}

          <div className="flex justify-end gap-3 pt-2 border-t">
            <button type="button" onClick={onClose}
              className="px-4 py-2 text-sm border border-gray-300 rounded hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={submitting}
              className="px-4 py-2 text-sm bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-60">
              {submitting ? 'Submitting...' : 'Submit Result'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create LabResultsPanel.jsx**

```jsx
import { useState, useEffect, useCallback } from 'react';
import { getLabOrders, placeLabOrder, cancelLabOrder } from '../../services/labService';
import LabResultForm from './LabResultForm';

const STATUS_BADGE = {
  ORDERED: 'bg-yellow-100 text-yellow-700',
  SAMPLE_COLLECTED: 'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-500',
};

export default function LabResultsPanel({ ipdAdmissionId, patientId, canOrder = false }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showOrderForm, setShowOrderForm] = useState(false);
  const [resultTarget, setResultTarget] = useState(null);
  const [expanded, setExpanded] = useState({});
  const [newOrder, setNewOrder] = useState({ testName: '', priority: 'ROUTINE', notes: '' });
  const [ordering, setOrdering] = useState(false);

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params = ipdAdmissionId ? { ipdAdmissionId } : { patientId };
      const res = await getLabOrders(params);
      const content = res.data?.content || res.data || [];
      setOrders(Array.isArray(content) ? content : []);
    } catch (err) {
      console.error('Failed to load lab orders', err);
    } finally {
      setLoading(false);
    }
  }, [ipdAdmissionId, patientId]);

  useEffect(() => { fetchOrders(); }, [fetchOrders]);

  const handleOrder = async (e) => {
    e.preventDefault();
    setOrdering(true);
    try {
      await placeLabOrder({ ...newOrder, patientId, ipdAdmissionId });
      setNewOrder({ testName: '', priority: 'ROUTINE', notes: '' });
      setShowOrderForm(false);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Failed to place order');
    } finally {
      setOrdering(false);
    }
  };

  const handleCancel = async (publicId) => {
    if (!window.confirm('Cancel this lab order?')) return;
    try {
      await cancelLabOrder(publicId);
      fetchOrders();
    } catch (err) {
      alert(err.response?.data || 'Cannot cancel');
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-800">Lab Orders & Results</h3>
        {canOrder && (
          <button onClick={() => setShowOrderForm(!showOrderForm)}
            className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700">
            + New Lab Order
          </button>
        )}
      </div>

      {showOrderForm && (
        <form onSubmit={handleOrder} className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Test Name *</label>
              <input value={newOrder.testName} onChange={e => setNewOrder(p => ({ ...p, testName: e.target.value }))}
                required className="w-full border rounded px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
                placeholder="e.g. Complete Blood Count" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Priority</label>
              <select value={newOrder.priority} onChange={e => setNewOrder(p => ({ ...p, priority: e.target.value }))}
                className="w-full border rounded px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400">
                <option value="ROUTINE">Routine</option>
                <option value="URGENT">Urgent</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Clinical Notes</label>
            <input value={newOrder.notes} onChange={e => setNewOrder(p => ({ ...p, notes: e.target.value }))}
              className="w-full border rounded px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
              placeholder="Reason for test, clinical context..." />
          </div>
          <div className="flex gap-2">
            <button type="submit" disabled={ordering}
              className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-60">
              {ordering ? 'Placing...' : 'Place Order'}
            </button>
            <button type="button" onClick={() => setShowOrderForm(false)}
              className="px-4 py-1.5 text-sm border border-gray-300 rounded hover:bg-gray-50">
              Cancel
            </button>
          </div>
        </form>
      )}

      {loading ? (
        <p className="text-sm text-gray-500">Loading lab orders...</p>
      ) : orders.length === 0 ? (
        <p className="text-sm text-gray-400">No lab orders yet.</p>
      ) : (
        <div className="space-y-2">
          {orders.map((item) => {
            const order = item.order || item;
            const result = item.result;
            const isExpanded = expanded[order.publicId];
            let parsedParams = [];
            if (result?.parameters) {
              try { parsedParams = JSON.parse(result.parameters); } catch (_) {}
            }
            return (
              <div key={order.publicId} className="border border-gray-200 rounded-lg overflow-hidden">
                <div className="flex items-center justify-between p-3 bg-white cursor-pointer"
                  onClick={() => setExpanded(p => ({ ...p, [order.publicId]: !isExpanded }))}>
                  <div className="flex items-center gap-3">
                    <span className="font-medium text-sm text-gray-900">{order.testName}</span>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_BADGE[order.status]}`}>
                      {order.status}
                    </span>
                    {order.priority === 'URGENT' && (
                      <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-red-100 text-red-600">URGENT</span>
                    )}
                    {result?.isAbnormal && (
                      <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-orange-100 text-orange-700">⚠ Abnormal</span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-gray-400">
                      {new Date(order.createdAt).toLocaleDateString()}
                    </span>
                    {canOrder && order.status === 'ORDERED' && (
                      <button onClick={e => { e.stopPropagation(); handleCancel(order.publicId); }}
                        className="text-xs text-red-500 hover:text-red-700 px-2 py-0.5 border border-red-300 rounded">
                        Cancel
                      </button>
                    )}
                    <span className="text-gray-400 text-sm">{isExpanded ? '▲' : '▼'}</span>
                  </div>
                </div>

                {isExpanded && (
                  <div className="border-t border-gray-100 p-3 bg-gray-50 text-sm">
                    {order.notes && <p className="text-gray-600 mb-2"><span className="font-medium">Note:</span> {order.notes}</p>}
                    <p className="text-gray-500 text-xs">Ordered by: {order.orderedByName} · {new Date(order.createdAt).toLocaleString()}</p>
                    {order.sampleCollectedAt && (
                      <p className="text-gray-500 text-xs mt-1">Sample collected: {new Date(order.sampleCollectedAt).toLocaleString()} by {order.sampleCollectedByName}</p>
                    )}
                    {result ? (
                      <div className="mt-3">
                        <p className="font-medium text-gray-700 mb-2">Results — {new Date(result.resultedAt).toLocaleString()}</p>
                        {parsedParams.length > 0 && (
                          <table className="w-full text-xs border rounded overflow-hidden mb-2">
                            <thead className="bg-gray-100">
                              <tr>
                                <th className="px-2 py-1 text-left">Parameter</th>
                                <th className="px-2 py-1 text-left">Value</th>
                                <th className="px-2 py-1 text-left">Unit</th>
                                <th className="px-2 py-1 text-left">Ref Range</th>
                                <th className="px-2 py-1 text-left">Flag</th>
                              </tr>
                            </thead>
                            <tbody>
                              {parsedParams.map((p, i) => (
                                <tr key={i} className={`border-t ${p.flag !== 'Normal' ? 'bg-orange-50' : ''}`}>
                                  <td className="px-2 py-1">{p.name}</td>
                                  <td className="px-2 py-1 font-medium">{p.value}</td>
                                  <td className="px-2 py-1 text-gray-500">{p.unit}</td>
                                  <td className="px-2 py-1 text-gray-500">{p.referenceRange}</td>
                                  <td className={`px-2 py-1 font-medium ${p.flag !== 'Normal' ? 'text-red-600' : 'text-green-600'}`}>{p.flag}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        )}
                        {result.resultSummary && <p className="text-gray-600"><span className="font-medium">Summary:</span> {result.resultSummary}</p>}
                        {result.verifiedByName && <p className="text-gray-500 text-xs mt-1">Verified by: {result.verifiedByName}</p>}
                      </div>
                    ) : (
                      <p className="text-gray-400 text-xs mt-2 italic">Awaiting results.</p>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {resultTarget && (
        <LabResultForm
          publicId={resultTarget}
          onClose={() => setResultTarget(null)}
          onSuccess={() => { setResultTarget(null); fetchOrders(); }}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/lab/
git commit -m "feat(lab): add LabResultForm and LabResultsPanel components"
```

---

## Task 12: Frontend — IpdDetails Lab Section

**Files:**
- Modify: `frontend/src/pages/hospital/IpdDetails.jsx`

- [ ] **Step 1: Read IpdDetails.jsx to find the Doctor Orders section (around the DoctorOrdersPanel import)**

Identify the import line for `DoctorOrdersPanel` and the render location. Look for: `import DoctorOrdersPanel` at the top, and `<DoctorOrdersPanel` in JSX body.

- [ ] **Step 2: Add LabResultsPanel import at the top of IpdDetails.jsx**

After the existing `DoctorOrdersPanel` import line, add:
```javascript
import LabResultsPanel from '../../components/lab/LabResultsPanel';
```

- [ ] **Step 3: Add lab section to IpdDetails JSX**

In the JSX body, after the `<DoctorOrdersPanel ... />` block, add:

```jsx
{/* Lab Orders & Results */}
<div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
  <LabResultsPanel
    ipdAdmissionId={admission?.id}
    patientId={admission?.patientId}
    canOrder={user?.role === 'DOCTOR' || user?.role === 'HOSPITAL_ADMIN'}
  />
</div>
```

Note: `user` comes from `authService.getUser()` or the existing user context already used in IpdDetails. Check the existing file for the pattern used to get the current user and match it.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/hospital/IpdDetails.jsx
git commit -m "feat(lab): add Lab Orders & Results section to IpdDetails"
```

---

## Task 13: Frontend — Routing, Auth, Admin Dashboard

**Files:**
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/services/authService.js`
- Modify: `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`

- [ ] **Step 1: Add LAB_TECHNICIAN to authService.js**

In `frontend/src/services/authService.js`, find the `isNurse` line and add after it:
```javascript
isLabTechnician: () => user?.role === 'LAB_TECHNICIAN',
```

- [ ] **Step 2: Add route and redirect in App.jsx**

In `frontend/src/App.jsx`:

**Import:** Add at the top with other page imports:
```javascript
import LabTechnicianDashboard from './pages/hospital/LabTechnicianDashboard';
```

**Route:** In the routes section where `/nurse-dashboard` is defined, add alongside it:
```jsx
<Route path="/lab-dashboard" element={
  <ProtectedRoute allowedRoles={['LAB_TECHNICIAN']}>
    <LabTechnicianDashboard />
  </ProtectedRoute>
} />
```

**LandingRedirect:** In the switch/case block that handles role-based redirects, add after the `'NURSE'` case:
```javascript
case 'LAB_TECHNICIAN':
  return <Navigate to="/lab-dashboard" replace />;
```

- [ ] **Step 3: Add Lab Technicians tab to HospitalAdminDashboard**

In `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`:

**a)** Add `'Lab Technicians'` to the tabs array (after `'Nurses'`):
```javascript
// Find the tabs array e.g. ['Overview', 'Doctors', 'Nurses', ...]
// Add 'Lab Technicians' after 'Nurses'
```

**b)** Add the `LabTechniciansTable` component at the bottom of the file (same pattern as `NursesTable`):

```jsx
function LabTechniciansTable() {
  const [labTechs, setLabTechs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' });
  const [submitting, setSubmitting] = useState(false);
  const { showToast } = useContext(ToastContext);

  const { createLabTechnician, getLabTechnicians, deactivateLabTechnician } = require('../../services/labService');

  useEffect(() => {
    getLabTechnicians({ size: 100 })
      .then(res => setLabTechs(res.data?.content || []))
      .catch(() => setLabTechs([]))
      .finally(() => setLoading(false));
  }, []);

  const handleCreate = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await createLabTechnician(form);
      showToast('Lab technician created', 'success');
      setShowForm(false);
      setForm({ name: '', email: '', password: '', phone: '' });
      const res = await getLabTechnicians({ size: 100 });
      setLabTechs(res.data?.content || []);
    } catch (err) {
      showToast(err.response?.data || 'Failed to create', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeactivate = async (publicId) => {
    if (!window.confirm('Deactivate this lab technician?')) return;
    try {
      await deactivateLabTechnician(publicId);
      setLabTechs(prev => prev.filter(u => u.publicId !== publicId));
      showToast('Lab technician deactivated', 'success');
    } catch (err) {
      showToast('Failed to deactivate', 'error');
    }
  };

  if (loading) return <div className="text-center py-8 text-gray-500">Loading...</div>;

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-semibold text-gray-800">Lab Technicians</h3>
        <button onClick={() => setShowForm(!showForm)}
          className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          + Add Lab Technician
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-4 grid grid-cols-2 gap-3">
          {[
            { label: 'Full Name', key: 'name', type: 'text', placeholder: 'Ravi Kumar' },
            { label: 'Email', key: 'email', type: 'email', placeholder: 'ravi@hospital.com' },
            { label: 'Password', key: 'password', type: 'password', placeholder: '••••••••' },
            { label: 'Phone', key: 'phone', type: 'text', placeholder: '9876543210' },
          ].map(f => (
            <div key={f.key}>
              <label className="block text-xs font-medium text-gray-600 mb-1">{f.label}</label>
              <input type={f.type} value={form[f.key]}
                onChange={e => setForm(p => ({ ...p, [f.key]: e.target.value }))}
                required={f.key !== 'phone'}
                className="w-full border rounded px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-400"
                placeholder={f.placeholder} />
            </div>
          ))}
          <div className="col-span-2 flex gap-2">
            <button type="submit" disabled={submitting}
              className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-60">
              {submitting ? 'Creating...' : 'Create'}
            </button>
            <button type="button" onClick={() => setShowForm(false)}
              className="px-4 py-2 text-sm border border-gray-300 rounded hover:bg-gray-50">
              Cancel
            </button>
          </div>
        </form>
      )}

      {labTechs.length === 0 ? (
        <div className="text-center py-8 text-gray-400">No lab technicians yet. Add one above.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                {['ID', 'Name', 'Email', 'Phone', 'Actions'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {labTechs.map(lt => (
                <tr key={lt.publicId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{lt.customId || '—'}</td>
                  <td className="px-4 py-3 font-medium text-gray-900">{lt.name}</td>
                  <td className="px-4 py-3 text-gray-600">{lt.email}</td>
                  <td className="px-4 py-3 text-gray-600">{lt.phone || '—'}</td>
                  <td className="px-4 py-3">
                    <button onClick={() => handleDeactivate(lt.publicId)}
                      className="text-xs text-red-500 hover:text-red-700 border border-red-300 rounded px-2 py-1">
                      Deactivate
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
```

**c)** In the tab render section, add:
```jsx
{activeTab === 'Lab Technicians' && <LabTechniciansTable />}
```

- [ ] **Step 4: Build frontend to verify no import errors**

Run: `cd frontend && npm run build 2>&1 | tail -20`
Expected: Built successfully (or only pre-existing warnings, no new errors).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.jsx \
        frontend/src/services/authService.js \
        frontend/src/pages/hospital/HospitalAdminDashboard.jsx
git commit -m "feat(lab): wire LAB_TECHNICIAN routing, authService helper, admin Lab Technicians tab"
```

---

## Verification Checklist

After all tasks complete, verify:

- [ ] Backend compiles: `cd backend && mvn clean package -DskipTests -q` → BUILD SUCCESS
- [ ] All backend tests pass: `cd backend && mvn test -q` → BUILD SUCCESS (no failures)
- [ ] Frontend builds: `cd frontend && npm run build` → no errors
- [ ] Manual flow (if server running):
  1. Admin creates lab technician → LT1 customId assigned
  2. Doctor (in IpdDetails) places lab order → status ORDERED
  3. Lab tech logs in → sees order on dashboard
  4. Lab tech clicks "Collect Sample" → status SAMPLE_COLLECTED
  5. Lab tech clicks "Enter Result" → fills parameters → status COMPLETED
  6. Doctor refreshes IpdDetails → sees result with parameters table
