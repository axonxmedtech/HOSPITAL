# Nurse IPD Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a NURSE role with initial assessment, vitals recording, doctor orders, and a nurse task list on top of the existing IPD admission system.

**Architecture:** Six new JPA entities (Nurse, NurseWardAssignment, NurseAssessment, VitalSigns, DoctorOrder, NurseTask). Backend follows the existing Receptionist pattern: User record (role="NURSE") + profile entity + service + controller. Clinical sub-resources hang off `/api/ipd/{admissionId}/`. Frontend adds NurseDashboard page and extends existing IPD detail with an Orders tab.

**Tech Stack:** Spring Boot 3, JPA/Hibernate, MySQL, React 18, Axios, @tanstack/react-table

---

## File Map

### New Backend Files
- `entity/Nurse.java`
- `entity/NurseWardAssignment.java`
- `entity/NurseAssessment.java`
- `entity/VitalSigns.java`
- `entity/DoctorOrder.java`
- `entity/NurseTask.java`
- `repository/NurseRepository.java`
- `repository/NurseWardAssignmentRepository.java`
- `repository/NurseAssessmentRepository.java`
- `repository/VitalSignsRepository.java`
- `repository/DoctorOrderRepository.java`
- `repository/NurseTaskRepository.java`
- `service/hospital/NurseService.java`
- `service/hospital/NurseAssessmentService.java`
- `service/hospital/DoctorOrderService.java`
- `service/hospital/NurseTaskService.java`
- `service/hospital/NurseDashboardService.java`
- `controller/hospital/NurseController.java`
- `controller/hospital/NurseAssessmentController.java`
- `controller/hospital/DoctorOrderController.java`
- `controller/hospital/NurseTaskController.java`
- `controller/hospital/NurseDashboardController.java`
- `scheduler/NurseTaskScheduler.java`
- `src/test/java/com/hms/service/NurseServiceTest.java`
- `src/test/java/com/hms/service/DoctorOrderServiceTest.java`

### Modified Backend Files
- `config/SecurityConfig.java` — add NURSE to `/hospital/**` and `/ws/**`
- `setup/schema-full.sql` — add 6 new tables

### New Frontend Files
- `src/services/nurseService.js`
- `src/pages/hospital/NurseDashboard.jsx`
- `src/components/nurse/PatientClinicalRecord.jsx`
- `src/components/nurse/NurseAssessmentForm.jsx`
- `src/components/nurse/VitalsForm.jsx`
- `src/components/nurse/DoctorOrdersPanel.jsx`
- `src/components/nurse/NurseTaskList.jsx`

### Modified Frontend Files
- `src/App.jsx` — add `/nurse-dashboard` route
- `src/services/authService.js` — NURSE redirect
- `src/components/Sidebar.jsx` — NURSE sidebar items
- `src/pages/hospital/HospitalAdminDashboard.jsx` — Nurses tab
- IPD detail component — add Orders + Assessment + Vitals tabs for doctor view

---

## Task 1: SecurityConfig — add NURSE role

**Files:**
- Modify: `backend/src/main/java/com/hms/config/SecurityConfig.java:81,84-85`

- [ ] **Step 1: Add NURSE to the two role allowlists**

```java
// Line ~81 — WebSocket
.requestMatchers("/ws/**").hasAnyRole("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "PHARMACIST", "NURSE", "SUPER_ADMIN")

// Line ~84-85 — Hospital + pharmacy endpoints
.requestMatchers("/hospital/**", "/api/pharmacy/**")
.hasAnyRole("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "PHARMACIST", "NURSE")
```

- [ ] **Step 2: Verify backend compiles**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/config/SecurityConfig.java
git commit -m "feat: add NURSE role to SecurityConfig allowlists"
```

---

## Task 2: Nurse + NurseWardAssignment entities

**Files:**
- Create: `backend/src/main/java/com/hms/entity/Nurse.java`
- Create: `backend/src/main/java/com/hms/entity/NurseWardAssignment.java`

- [ ] **Step 1: Create Nurse.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "nurses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Nurse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(name = "custom_id")
    private String customId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
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

- [ ] **Step 2: Create NurseWardAssignment.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nurse_ward_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"nurse_id", "ward_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NurseWardAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nurse_id", nullable = false)
    private Long nurseId;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;
}
```

- [ ] **Step 3: Compile**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/entity/Nurse.java \
        backend/src/main/java/com/hms/entity/NurseWardAssignment.java
git commit -m "feat: add Nurse and NurseWardAssignment entities"
```

---

## Task 3: NurseAssessment + VitalSigns entities

**Files:**
- Create: `backend/src/main/java/com/hms/entity/NurseAssessment.java`
- Create: `backend/src/main/java/com/hms/entity/VitalSigns.java`

- [ ] **Step 1: Create NurseAssessment.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nurse_assessments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NurseAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false, unique = true)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    private Integer pulse;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature;

    private Integer spo2;

    @Column(precision = 5, scale = 1)
    private BigDecimal height;

    @Column(precision = 5, scale = 1)
    private BigDecimal weight;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(name = "fall_risk", length = 10)
    private String fallRisk; // LOW, MEDIUM, HIGH

    @Column(name = "general_condition", columnDefinition = "TEXT")
    private String generalCondition;

    @Column(name = "chief_complaint_on_admission", columnDefinition = "TEXT")
    private String chiefComplaintOnAdmission;

    @Column(name = "assessed_by")
    private Long assessedBy;

    @Column(name = "assessed_by_name", length = 100)
    private String assessedByName;

    @Column(name = "assessed_at")
    private LocalDateTime assessedAt;
}
```

- [ ] **Step 2: Create VitalSigns.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vital_signs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VitalSigns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    private Integer pulse;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature;

    private Integer spo2;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "recorded_by_name", length = 100)
    private String recordedByName;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;
}
```

- [ ] **Step 3: Compile**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/entity/NurseAssessment.java \
        backend/src/main/java/com/hms/entity/VitalSigns.java
git commit -m "feat: add NurseAssessment and VitalSigns entities"
```

---

## Task 4: DoctorOrder + NurseTask entities

**Files:**
- Create: `backend/src/main/java/com/hms/entity/DoctorOrder.java`
- Create: `backend/src/main/java/com/hms/entity/NurseTask.java`

- [ ] **Step 1: Create DoctorOrder.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @PrePersist
    public void generatePublicId() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    // MEDICATION, INVESTIGATION, PROCEDURE, DIET
    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // BD, TDS, QID, OD, SOS, Once
    @Column(length = 20)
    private String frequency;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // ACTIVE, COMPLETED, CANCELLED
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
```

- [ ] **Step 2: Create NurseTask.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "nurse_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NurseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_order_id", nullable = false)
    private Long doctorOrderId;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "executed_by")
    private Long executedBy;

    @Column(name = "executed_by_name", length = 100)
    private String executedByName;

    // PENDING, DONE, SKIPPED
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String notes;
}
```

- [ ] **Step 3: Compile**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/entity/DoctorOrder.java \
        backend/src/main/java/com/hms/entity/NurseTask.java
git commit -m "feat: add DoctorOrder and NurseTask entities"
```

---

## Task 5: Repositories

**Files:**
- Create: `backend/src/main/java/com/hms/repository/NurseRepository.java`
- Create: `backend/src/main/java/com/hms/repository/NurseWardAssignmentRepository.java`
- Create: `backend/src/main/java/com/hms/repository/NurseAssessmentRepository.java`
- Create: `backend/src/main/java/com/hms/repository/VitalSignsRepository.java`
- Create: `backend/src/main/java/com/hms/repository/DoctorOrderRepository.java`
- Create: `backend/src/main/java/com/hms/repository/NurseTaskRepository.java`

- [ ] **Step 1: Create all six repository files**

```java
// NurseRepository.java
package com.hms.repository;
import com.hms.entity.Nurse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;

public interface NurseRepository extends JpaRepository<Nurse, Long> {
    Optional<Nurse> findByPublicId(String publicId);
    Page<Nurse> findByHospitalIdAndIsActiveTrue(Long hospitalId, Pageable pageable);
    List<Nurse> findByHospitalIdAndIsActiveTrue(Long hospitalId);
    @Query("SELECT n FROM Nurse n WHERE n.hospitalId = :hospitalId AND n.isActive = true AND (LOWER(n.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(n.email) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Nurse> searchByHospitalId(Long hospitalId, String search, Pageable pageable);
    Optional<Nurse> findByEmailAndIsActiveTrue(String email);
    @Query("SELECT MAX(CAST(SUBSTRING(n.customId, 4) AS int)) FROM Nurse n WHERE n.hospitalId = :hospitalId AND n.customId IS NOT NULL")
    Integer findMaxNurseSequence(Long hospitalId);
}
```

```java
// NurseWardAssignmentRepository.java
package com.hms.repository;
import com.hms.entity.NurseWardAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NurseWardAssignmentRepository extends JpaRepository<NurseWardAssignment, Long> {
    List<NurseWardAssignment> findByNurseId(Long nurseId);
    Optional<NurseWardAssignment> findByNurseIdAndWardId(Long nurseId, Long wardId);
    void deleteByNurseId(Long nurseId);
}
```

```java
// NurseAssessmentRepository.java
package com.hms.repository;
import com.hms.entity.NurseAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NurseAssessmentRepository extends JpaRepository<NurseAssessment, Long> {
    Optional<NurseAssessment> findByIpdAdmissionId(Long ipdAdmissionId);
    boolean existsByIpdAdmissionId(Long ipdAdmissionId);
}
```

```java
// VitalSignsRepository.java
package com.hms.repository;
import com.hms.entity.VitalSigns;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {
    List<VitalSigns> findByIpdAdmissionIdOrderByRecordedAtDesc(Long ipdAdmissionId);
}
```

```java
// DoctorOrderRepository.java
package com.hms.repository;
import com.hms.entity.DoctorOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DoctorOrderRepository extends JpaRepository<DoctorOrder, Long> {
    Optional<DoctorOrder> findByPublicId(String publicId);
    List<DoctorOrder> findByIpdAdmissionIdOrderByCreatedAtDesc(Long ipdAdmissionId);
    List<DoctorOrder> findByIpdAdmissionIdAndStatus(Long ipdAdmissionId, String status);
    List<DoctorOrder> findByHospitalIdAndStatusAndFrequencyNot(Long hospitalId, String status, String frequency);
}
```

```java
// NurseTaskRepository.java
package com.hms.repository;
import com.hms.entity.NurseTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NurseTaskRepository extends JpaRepository<NurseTask, Long> {
    List<NurseTask> findByIpdAdmissionIdOrderByScheduledAtDesc(Long ipdAdmissionId);
    List<NurseTask> findByIpdAdmissionIdAndStatus(Long ipdAdmissionId, String status);
    List<NurseTask> findByDoctorOrderIdAndStatus(Long doctorOrderId, String status);
    List<NurseTask> findByIpdAdmissionIdInAndStatus(List<Long> ipdAdmissionIds, String status);
    boolean existsByDoctorOrderIdAndStatus(Long doctorOrderId, String status);
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/hms/repository/Nurse*.java \
        backend/src/main/java/com/hms/repository/VitalSigns*.java \
        backend/src/main/java/com/hms/repository/DoctorOrder*.java \
        backend/src/main/java/com/hms/repository/NurseTask*.java
git commit -m "feat: add repositories for nurse clinical workflow entities"
```

---

## Task 6: Database migration SQL

**Files:**
- Modify: `setup/schema-full.sql` — append 6 new CREATE TABLE statements

- [ ] **Step 1: Append to schema-full.sql**

Add at the end of `setup/schema-full.sql`:

```sql
-- =============================================
-- NURSE IPD WORKFLOW (Phase 1)
-- =============================================

CREATE TABLE IF NOT EXISTS nurses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    custom_id VARCHAR(20),
    hospital_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nurse_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);

CREATE TABLE IF NOT EXISTS nurse_ward_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nurse_id BIGINT NOT NULL,
    ward_id BIGINT NOT NULL,
    UNIQUE KEY uq_nurse_ward (nurse_id, ward_id),
    CONSTRAINT fk_nwa_nurse FOREIGN KEY (nurse_id) REFERENCES nurses(id),
    CONSTRAINT fk_nwa_ward FOREIGN KEY (ward_id) REFERENCES wards(id)
);

CREATE TABLE IF NOT EXISTS nurse_assessments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ipd_admission_id BIGINT NOT NULL UNIQUE,
    hospital_id BIGINT NOT NULL,
    blood_pressure VARCHAR(20),
    pulse INT,
    temperature DECIMAL(4,1),
    spo2 INT,
    height DECIMAL(5,1),
    weight DECIMAL(5,1),
    pain_score INT,
    allergies TEXT,
    fall_risk VARCHAR(10),
    general_condition TEXT,
    chief_complaint_on_admission TEXT,
    assessed_by BIGINT,
    assessed_by_name VARCHAR(100),
    assessed_at DATETIME,
    CONSTRAINT fk_na_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admissions(id)
);

CREATE TABLE IF NOT EXISTS vital_signs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ipd_admission_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    blood_pressure VARCHAR(20),
    pulse INT,
    temperature DECIMAL(4,1),
    spo2 INT,
    recorded_by BIGINT,
    recorded_by_name VARCHAR(100),
    recorded_at DATETIME NOT NULL,
    CONSTRAINT fk_vs_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admissions(id)
);

CREATE TABLE IF NOT EXISTS doctor_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    ipd_admission_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    frequency VARCHAR(20),
    start_date DATE,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_by_name VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    CONSTRAINT fk_do_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admissions(id)
);

CREATE TABLE IF NOT EXISTS nurse_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_order_id BIGINT NOT NULL,
    ipd_admission_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    scheduled_at DATETIME,
    executed_at DATETIME,
    executed_by BIGINT,
    executed_by_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    CONSTRAINT fk_nt_order FOREIGN KEY (doctor_order_id) REFERENCES doctor_orders(id)
);
```

- [ ] **Step 2: Run the new tables against your dev database**

```bash
mysql -u root -p hospital_management < setup/schema-full.sql
```
Verify no errors. If tables already exist, statements are safe (`IF NOT EXISTS`).

- [ ] **Step 3: Commit**

```bash
git add setup/schema-full.sql
git commit -m "feat: add nurse workflow tables to schema"
```

---

## Task 7: NurseService + NurseController

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/NurseService.java`
- Create: `backend/src/main/java/com/hms/controller/hospital/NurseController.java`
- Create: `backend/src/test/java/com/hms/service/NurseServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
// backend/src/test/java/com/hms/service/NurseServiceTest.java
package com.hms.service;

import com.hms.entity.Nurse;
import com.hms.entity.User;
import com.hms.repository.NurseRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.NurseWardAssignmentRepository;
import com.hms.repository.AuditLogRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.service.hospital.NurseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NurseServiceTest {

    @Mock NurseRepository nurseRepository;
    @Mock UserRepository userRepository;
    @Mock NurseWardAssignmentRepository wardAssignmentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock HospitalWebSocketHandler webSocketHandler;

    @InjectMocks NurseService nurseService;

    @Test
    void createNurse_savesUserAndNurseProfile() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserName()).thenReturn("admin@test.com");
        when(userRepository.existsByEmail("nurse@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setName("Nurse Priya");
        savedUser.setEmail("nurse@test.com");
        savedUser.setRole("NURSE");
        savedUser.setHospitalId(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(nurseRepository.findMaxNurseSequence(1L)).thenReturn(null);

        Nurse savedNurse = new Nurse();
        savedNurse.setId(1L);
        savedNurse.setName("Nurse Priya");
        when(nurseRepository.save(any(Nurse.class))).thenReturn(savedNurse);

        User result = nurseService.createNurse("Nurse Priya", "nurse@test.com", "pass123", "9876543210");

        assertThat(result.getRole()).isEqualTo("NURSE");
        verify(nurseRepository).save(any(Nurse.class));
    }

    @Test
    void createNurse_throwsWhenEmailExists() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> nurseService.createNurse("Name", "existing@test.com", "pass", "123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (class doesn't exist yet)**

```bash
cd backend && mvn test -pl . -Dtest=NurseServiceTest -q 2>&1 | tail -5
```
Expected: compilation error or test failure.

- [ ] **Step 3: Create NurseService.java**

```java
package com.hms.service.hospital;

import com.hms.entity.Nurse;
import com.hms.entity.NurseWardAssignment;
import com.hms.entity.User;
import com.hms.entity.AuditLog;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class NurseService {

    private static final Logger logger = LoggerFactory.getLogger(NurseService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private NurseRepository nurseRepository;
    @Autowired private NurseWardAssignmentRepository wardAssignmentRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HospitalWebSocketHandler webSocketHandler;

    @Transactional
    public User createNurse(String name, String email, String password, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        if (userRepository.existsByEmail(email)) throw new IllegalArgumentException("Email already exists");

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("NURSE");
        user.setHospitalId(hospitalId);
        user.setIsActive(true);
        User saved = userRepository.save(user);

        Integer maxSeq = nurseRepository.findMaxNurseSequence(hospitalId);
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        saved.setCustomId("NRS" + nextSeq);
        saved = userRepository.save(saved);

        Nurse profile = new Nurse();
        profile.setHospitalId(hospitalId);
        profile.setName(name);
        profile.setEmail(email);
        profile.setPhone(phone != null ? phone : "");
        profile.setCustomId(saved.getCustomId());
        profile.setIsActive(true);
        nurseRepository.save(profile);

        logAction("NURSE_CREATED", "Created nurse: " + email, hospitalId);
        broadcastRefresh(hospitalId);
        return saved;
    }

    public Page<User> getAllNurses(String search, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        if (StringUtils.hasText(search)) {
            return userRepository.searchReceptionists(hospitalId, "NURSE", search, pageable);
        }
        return userRepository.findByHospitalIdAndRoleAndIsActiveTrue(hospitalId, "NURSE", pageable);
    }

    @Transactional
    public User updateNurse(String publicId, String name, String phone) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        User user = findNurseUser(publicId, hospitalId);
        user.setName(name);
        User saved = userRepository.save(user);
        nurseRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(n -> {
            n.setName(name);
            if (phone != null) n.setPhone(phone);
            nurseRepository.save(n);
        });
        logAction("NURSE_UPDATED", "Updated nurse: " + user.getEmail(), hospitalId);
        broadcastRefresh(hospitalId);
        return saved;
    }

    @Transactional
    public void deleteNurse(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        User user = findNurseUser(publicId, hospitalId);
        user.setIsActive(false);
        userRepository.save(user);
        nurseRepository.findByEmailAndIsActiveTrue(user.getEmail()).ifPresent(n -> {
            n.setIsActive(false);
            nurseRepository.save(n);
        });
        logAction("NURSE_DELETED", "Deleted nurse: " + user.getEmail(), hospitalId);
        broadcastRefresh(hospitalId);
    }

    @Transactional
    public void resetPassword(String publicId, String newPassword) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        User user = findNurseUser(publicId, hospitalId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logAction("NURSE_PASSWORD_RESET", "Reset password for nurse: " + user.getEmail(), hospitalId);
    }

    @Transactional
    public void assignWard(String publicId, Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        User user = findNurseUser(publicId, hospitalId);
        Nurse nurse = nurseRepository.findByEmailAndIsActiveTrue(user.getEmail())
                .orElseThrow(() -> new RuntimeException("Nurse profile not found"));
        if (wardAssignmentRepository.findByNurseIdAndWardId(nurse.getId(), wardId).isEmpty()) {
            NurseWardAssignment assignment = new NurseWardAssignment();
            assignment.setNurseId(nurse.getId());
            assignment.setWardId(wardId);
            wardAssignmentRepository.save(assignment);
        }
    }

    @Transactional
    public void removeWardAssignment(String publicId, Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        User user = findNurseUser(publicId, hospitalId);
        Nurse nurse = nurseRepository.findByEmailAndIsActiveTrue(user.getEmail())
                .orElseThrow(() -> new RuntimeException("Nurse profile not found"));
        wardAssignmentRepository.findByNurseIdAndWardId(nurse.getId(), wardId)
                .ifPresent(wardAssignmentRepository::delete);
    }

    private User findNurseUser(String publicId, Long hospitalId) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Nurse not found"));
        if (!user.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied");
        if (!"NURSE".equals(user.getRole())) throw new UnauthorizedException("User is not a nurse");
        return user;
    }

    private void logAction(String action, String details, Long hospitalId) {
        try {
            String actor = SecurityContextHolder.getContext().getAuthentication().getName();
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setDetails(details);
            log.setPerformedBy(actor);
            log.setHospitalId(hospitalId);
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to save audit log: {}", e.getMessage());
        }
    }

    private void broadcastRefresh(Long hospitalId) {
        try { webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}"); }
        catch (Exception e) { logger.warn("WebSocket broadcast failed", e); }
    }
}
```

- [ ] **Step 4: Create NurseController.java**

```java
package com.hms.controller.hospital;

import com.hms.service.hospital.NurseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/hospital/nurses")
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class NurseController {

    @Autowired private NurseService nurseService;

    @PostMapping
    public ResponseEntity<?> createNurse(@RequestBody Map<String, String> body) {
        String name = body.get("name"), email = body.get("email"),
               password = body.get("password"), phone = body.get("phone");
        if (name == null || email == null || password == null)
            return ResponseEntity.badRequest().body("name, email, and password are required");
        return ResponseEntity.ok(nurseService.createNurse(name, email, password, phone));
    }

    @GetMapping
    public ResponseEntity<?> getAllNurses(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(nurseService.getAllNurses(search, PageRequest.of(page, size)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNurse(@PathVariable String id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().body("name is required");
        return ResponseEntity.ok(nurseService.updateNurse(id, name, body.get("phone")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNurse(@PathVariable String id) {
        nurseService.deleteNurse(id);
        return ResponseEntity.ok(Map.of("message", "Nurse deleted"));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        String pw = body.get("newPassword");
        if (pw == null || pw.length() < 6) return ResponseEntity.badRequest().body("Password must be >= 6 chars");
        nurseService.resetPassword(id, pw);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PostMapping("/{id}/assign-ward")
    public ResponseEntity<?> assignWard(@PathVariable String id, @RequestBody Map<String, Long> body) {
        nurseService.assignWard(id, body.get("wardId"));
        return ResponseEntity.ok(Map.of("message", "Ward assigned"));
    }

    @DeleteMapping("/{id}/assign-ward/{wardId}")
    public ResponseEntity<?> removeWard(@PathVariable String id, @PathVariable Long wardId) {
        nurseService.removeWardAssignment(id, wardId);
        return ResponseEntity.ok(Map.of("message", "Ward assignment removed"));
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && mvn test -Dtest=NurseServiceTest -q
```
Expected: 2 tests pass.

- [ ] **Step 6: Compile full project**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/NurseService.java \
        backend/src/main/java/com/hms/controller/hospital/NurseController.java \
        backend/src/test/java/com/hms/service/NurseServiceTest.java
git commit -m "feat: add NurseService and NurseController (admin CRUD)"
```

---

## Task 8: NurseAssessmentService + Controller + VitalSigns endpoint

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/NurseAssessmentService.java`
- Create: `backend/src/main/java/com/hms/controller/hospital/NurseAssessmentController.java`

- [ ] **Step 1: Create NurseAssessmentService.java**

```java
package com.hms.service.hospital;

import com.hms.entity.NurseAssessment;
import com.hms.entity.VitalSigns;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.NurseAssessmentRepository;
import com.hms.repository.VitalSignsRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NurseAssessmentService {

    @Autowired private NurseAssessmentRepository assessmentRepository;
    @Autowired private VitalSignsRepository vitalsRepository;
    @Autowired private SecurityContextHelper securityHelper;

    @Transactional
    public NurseAssessment createAssessment(Long admissionId, Map<String, Object> data) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        if (assessmentRepository.existsByIpdAdmissionId(admissionId))
            throw new IllegalStateException("Assessment already exists for this admission");

        NurseAssessment a = new NurseAssessment();
        a.setIpdAdmissionId(admissionId);
        a.setHospitalId(hospitalId);
        a.setBloodPressure((String) data.get("bloodPressure"));
        a.setPulse(toInt(data.get("pulse")));
        a.setTemperature(toBigDecimal(data.get("temperature")));
        a.setSpo2(toInt(data.get("spo2")));
        a.setHeight(toBigDecimal(data.get("height")));
        a.setWeight(toBigDecimal(data.get("weight")));
        a.setPainScore(toInt(data.get("painScore")));
        a.setAllergies((String) data.get("allergies"));
        a.setFallRisk((String) data.get("fallRisk"));
        a.setGeneralCondition((String) data.get("generalCondition"));
        a.setChiefComplaintOnAdmission((String) data.get("chiefComplaintOnAdmission"));
        a.setAssessedByName(securityHelper.getCurrentUserEmail());
        a.setAssessedAt(LocalDateTime.now());
        return assessmentRepository.save(a);
    }

    public NurseAssessment getAssessment(Long admissionId) {
        return assessmentRepository.findByIpdAdmissionId(admissionId).orElse(null);
    }

    @Transactional
    public VitalSigns recordVitals(Long admissionId, Map<String, Object> data) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        VitalSigns v = new VitalSigns();
        v.setIpdAdmissionId(admissionId);
        v.setHospitalId(hospitalId);
        v.setBloodPressure((String) data.get("bloodPressure"));
        v.setPulse(toInt(data.get("pulse")));
        v.setTemperature(toBigDecimal(data.get("temperature")));
        v.setSpo2(toInt(data.get("spo2")));
        v.setRecordedByName(securityHelper.getCurrentUserEmail());
        v.setRecordedAt(LocalDateTime.now());
        return vitalsRepository.save(v);
    }

    public List<VitalSigns> getVitals(Long admissionId) {
        return vitalsRepository.findByIpdAdmissionIdOrderByRecordedAtDesc(admissionId);
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer i) return i;
        return Integer.parseInt(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }
}
```

- [ ] **Step 2: Create NurseAssessmentController.java**

```java
package com.hms.controller.hospital;

import com.hms.entity.NurseAssessment;
import com.hms.entity.VitalSigns;
import com.hms.service.hospital.NurseAssessmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ipd/{admissionId}")
public class NurseAssessmentController {

    @Autowired private NurseAssessmentService assessmentService;

    @PostMapping("/assessment")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createAssessment(@PathVariable Long admissionId,
                                               @RequestBody Map<String, Object> body) {
        NurseAssessment result = assessmentService.createAssessment(admissionId, body);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/assessment")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getAssessment(@PathVariable Long admissionId) {
        return ResponseEntity.ok(assessmentService.getAssessment(admissionId));
    }

    @PostMapping("/vitals")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> recordVitals(@PathVariable Long admissionId,
                                          @RequestBody Map<String, Object> body) {
        VitalSigns result = assessmentService.recordVitals(admissionId, body);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/vitals")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<List<VitalSigns>> getVitals(@PathVariable Long admissionId) {
        return ResponseEntity.ok(assessmentService.getVitals(admissionId));
    }
}
```

- [ ] **Step 3: Compile**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/NurseAssessmentService.java \
        backend/src/main/java/com/hms/controller/hospital/NurseAssessmentController.java
git commit -m "feat: add NurseAssessment and VitalSigns endpoints"
```

---

## Task 9: DoctorOrderService + Controller

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/DoctorOrderService.java`
- Create: `backend/src/main/java/com/hms/controller/hospital/DoctorOrderController.java`
- Create: `backend/src/test/java/com/hms/service/DoctorOrderServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
// backend/src/test/java/com/hms/service/DoctorOrderServiceTest.java
package com.hms.service;

import com.hms.entity.DoctorOrder;
import com.hms.entity.NurseTask;
import com.hms.repository.DoctorOrderRepository;
import com.hms.repository.NurseTaskRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.DoctorOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorOrderServiceTest {

    @Mock DoctorOrderRepository orderRepository;
    @Mock NurseTaskRepository taskRepository;
    @Mock SecurityContextHelper securityHelper;

    @InjectMocks DoctorOrderService doctorOrderService;

    @Test
    void createOrder_savesOrderAndCreatesTask() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserName()).thenReturn("Dr. Sharma");

        DoctorOrder saved = new DoctorOrder();
        saved.setId(1L);
        saved.setStatus("ACTIVE");
        when(orderRepository.save(any())).thenReturn(saved);

        NurseTask savedTask = new NurseTask();
        savedTask.setId(1L);
        when(taskRepository.save(any())).thenReturn(savedTask);

        Map<String, Object> data = Map.of(
            "orderType", "MEDICATION",
            "description", "Ceftriaxone 1g IV",
            "frequency", "BD"
        );

        DoctorOrder result = doctorOrderService.createOrder(5L, data);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(taskRepository).save(any(NurseTask.class));
    }

    @Test
    void cancelOrder_setsStatusCancelled() {
        DoctorOrder order = new DoctorOrder();
        order.setId(1L);
        order.setPublicId("abc-123");
        order.setHospitalId(1L);
        order.setStatus("ACTIVE");

        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(orderRepository.findByPublicId("abc-123")).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        doctorOrderService.cancelOrder("abc-123");

        assertThat(order.getStatus()).isEqualTo("CANCELLED");
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
cd backend && mvn test -Dtest=DoctorOrderServiceTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Create DoctorOrderService.java**

```java
package com.hms.service.hospital;

import com.hms.entity.DoctorOrder;
import com.hms.entity.NurseTask;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.DoctorOrderRepository;
import com.hms.repository.NurseTaskRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DoctorOrderService {

    @Autowired private DoctorOrderRepository orderRepository;
    @Autowired private NurseTaskRepository taskRepository;
    @Autowired private SecurityContextHelper securityHelper;

    @Transactional
    public DoctorOrder createOrder(Long admissionId, Map<String, Object> data) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        DoctorOrder order = new DoctorOrder();
        order.setIpdAdmissionId(admissionId);
        order.setHospitalId(hospitalId);
        order.setOrderType((String) data.get("orderType"));
        order.setDescription((String) data.get("description"));
        order.setFrequency((String) data.get("frequency"));
        order.setNotes((String) data.get("notes"));
        order.setStatus("ACTIVE");
        order.setCreatedByName(securityHelper.getCurrentUserEmail());
        order.setStartDate(LocalDate.now());
        if (data.get("endDate") != null) {
            order.setEndDate(LocalDate.parse(data.get("endDate").toString()));
        }
        DoctorOrder saved = orderRepository.save(order);

        // Create initial task for non-SOS orders
        if (!"SOS".equalsIgnoreCase(saved.getFrequency())) {
            createTaskForOrder(saved, LocalDateTime.now());
        }
        return saved;
    }

    public List<DoctorOrder> getOrders(Long admissionId) {
        return orderRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(admissionId);
    }

    @Transactional
    public DoctorOrder updateOrder(String publicId, Map<String, Object> data) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        DoctorOrder order = findOrder(publicId, hospitalId);
        if (data.containsKey("description")) order.setDescription((String) data.get("description"));
        if (data.containsKey("notes")) order.setNotes((String) data.get("notes"));
        if (data.containsKey("endDate") && data.get("endDate") != null)
            order.setEndDate(LocalDate.parse(data.get("endDate").toString()));
        return orderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        DoctorOrder order = findOrder(publicId, hospitalId);
        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }

    public void createTaskForOrder(DoctorOrder order, LocalDateTime scheduledAt) {
        // Skip if a PENDING task already exists for today
        if (taskRepository.existsByDoctorOrderIdAndStatus(order.getId(), "PENDING")) return;

        NurseTask task = new NurseTask();
        task.setDoctorOrderId(order.getId());
        task.setIpdAdmissionId(order.getIpdAdmissionId());
        task.setHospitalId(order.getHospitalId());
        task.setScheduledAt(scheduledAt);
        task.setStatus("PENDING");
        taskRepository.save(task);
    }

    private DoctorOrder findOrder(String publicId, Long hospitalId) {
        DoctorOrder order = orderRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + publicId));
        if (!order.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied");
        return order;
    }
}
```

- [ ] **Step 4: Create DoctorOrderController.java**

```java
package com.hms.controller.hospital;

import com.hms.service.hospital.DoctorOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ipd/{admissionId}/orders")
public class DoctorOrderController {

    @Autowired private DoctorOrderService orderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createOrder(@PathVariable Long admissionId,
                                          @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(orderService.createOrder(admissionId, body));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOrders(@PathVariable Long admissionId) {
        return ResponseEntity.ok(orderService.getOrders(admissionId));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateOrder(@PathVariable Long admissionId,
                                          @PathVariable String publicId,
                                          @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(orderService.updateOrder(publicId, body));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> cancelOrder(@PathVariable Long admissionId,
                                          @PathVariable String publicId) {
        orderService.cancelOrder(publicId);
        return ResponseEntity.ok(Map.of("message", "Order cancelled"));
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd backend && mvn test -Dtest=DoctorOrderServiceTest -q
```
Expected: 2 tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/DoctorOrderService.java \
        backend/src/main/java/com/hms/controller/hospital/DoctorOrderController.java \
        backend/src/test/java/com/hms/service/DoctorOrderServiceTest.java
git commit -m "feat: add DoctorOrderService and DoctorOrderController"
```

---

## Task 10: NurseTaskService + Controller

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/NurseTaskService.java`
- Create: `backend/src/main/java/com/hms/controller/hospital/NurseTaskController.java`

- [ ] **Step 1: Create NurseTaskService.java**

```java
package com.hms.service.hospital;

import com.hms.entity.NurseTask;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.NurseTaskRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NurseTaskService {

    @Autowired private NurseTaskRepository taskRepository;
    @Autowired private SecurityContextHelper securityHelper;

    public List<NurseTask> getTasks(Long admissionId) {
        return taskRepository.findByIpdAdmissionIdOrderByScheduledAtDesc(admissionId);
    }

    public List<NurseTask> getPendingTasks(Long admissionId) {
        return taskRepository.findByIpdAdmissionIdAndStatus(admissionId, "PENDING");
    }

    @Transactional
    public NurseTask executeTask(Long taskId, String status, String notes) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        NurseTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        if (!task.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied");
        if (!"PENDING".equals(task.getStatus()))
            throw new IllegalStateException("Task is already " + task.getStatus());

        task.setStatus(status); // DONE or SKIPPED
        task.setExecutedAt(LocalDateTime.now());
        task.setExecutedByName(securityHelper.getCurrentUserEmail());
        task.setNotes(notes);
        return taskRepository.save(task);
    }
}
```

- [ ] **Step 2: Create NurseTaskController.java**

```java
package com.hms.controller.hospital;

import com.hms.service.hospital.NurseTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ipd/{admissionId}/tasks")
public class NurseTaskController {

    @Autowired private NurseTaskService taskService;

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getTasks(@PathVariable Long admissionId) {
        return ResponseEntity.ok(taskService.getTasks(admissionId));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getPendingTasks(@PathVariable Long admissionId) {
        return ResponseEntity.ok(taskService.getPendingTasks(admissionId));
    }

    @PutMapping("/{taskId}/execute")
    @PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> executeTask(@PathVariable Long admissionId,
                                          @PathVariable Long taskId,
                                          @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "DONE");
        if (!"DONE".equals(status) && !"SKIPPED".equals(status))
            return ResponseEntity.badRequest().body("status must be DONE or SKIPPED");
        return ResponseEntity.ok(taskService.executeTask(taskId, status, body.get("notes")));
    }
}
```

- [ ] **Step 3: Compile and commit**

```bash
cd backend && mvn compile -q
git add backend/src/main/java/com/hms/service/hospital/NurseTaskService.java \
        backend/src/main/java/com/hms/controller/hospital/NurseTaskController.java
git commit -m "feat: add NurseTaskService and NurseTaskController"
```

---

## Task 11: NurseDashboardService + Controller + Scheduler

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/NurseDashboardService.java`
- Create: `backend/src/main/java/com/hms/controller/hospital/NurseDashboardController.java`
- Create: `backend/src/main/java/com/hms/scheduler/NurseTaskScheduler.java`

- [ ] **Step 1: Create NurseDashboardService.java**

```java
package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.Nurse;
import com.hms.entity.NurseTask;
import com.hms.entity.NurseWardAssignment;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NurseDashboardService {

    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private NurseRepository nurseRepository;
    @Autowired private NurseWardAssignmentRepository wardAssignmentRepository;
    @Autowired private NurseTaskRepository nurseTaskRepository;
    @Autowired private SecurityContextHelper securityHelper;

    public List<IpdAdmission> getMyPatients() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        String email = securityHelper.getCurrentUserEmail();
        Nurse nurse = nurseRepository.findByEmailAndIsActiveTrue(email).orElse(null);

        List<IpdAdmission> allAdmitted = ipdAdmissionRepository
                .findByHospitalIdAndStatus(hospitalId, "ADMITTED");

        if (nurse == null) return allAdmitted;

        List<NurseWardAssignment> assignments = wardAssignmentRepository.findByNurseId(nurse.getId());
        if (assignments.isEmpty()) return allAdmitted;

        List<Long> wardIds = assignments.stream()
                .map(NurseWardAssignment::getWardId).collect(Collectors.toList());
        return allAdmitted.stream()
                .filter(a -> a.getWardId() != null && wardIds.contains(a.getWardId()))
                .collect(Collectors.toList());
    }

    public List<NurseTask> getMyTasks() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<IpdAdmission> patients = getMyPatients();
        List<Long> admissionIds = patients.stream()
                .map(IpdAdmission::getId).collect(Collectors.toList());
        if (admissionIds.isEmpty()) return List.of();

        return nurseTaskRepository.findByIpdAdmissionIdInAndStatus(admissionIds, "PENDING");
    }
}
```

- [ ] **Step 2: Check IpdAdmissionRepository has the needed method**

Open `backend/src/main/java/com/hms/repository/IpdAdmissionRepository.java` and check if `findByHospitalIdAndStatus` exists. If not, add:

```java
List<IpdAdmission> findByHospitalIdAndStatus(Long hospitalId, String status);
```

Also check if `IpdAdmission` has a `wardId` field. If it uses a different field name (e.g., `ward`), adjust the stream filter in `NurseDashboardService` accordingly. Check `entity/IpdAdmission.java` for the correct field name.

- [ ] **Step 3: Create NurseDashboardController.java**

```java
package com.hms.controller.hospital;

import com.hms.service.hospital.NurseDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital/nurse/dashboard")
@PreAuthorize("hasAnyRole('NURSE', 'HOSPITAL_ADMIN')")
public class NurseDashboardController {

    @Autowired private NurseDashboardService dashboardService;

    @GetMapping("/patients")
    public ResponseEntity<?> getMyPatients() {
        return ResponseEntity.ok(dashboardService.getMyPatients());
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<?> getMyTasks() {
        return ResponseEntity.ok(dashboardService.getMyTasks());
    }
}
```

- [ ] **Step 4: Create NurseTaskScheduler.java**

```java
package com.hms.scheduler;

import com.hms.entity.DoctorOrder;
import com.hms.repository.DoctorOrderRepository;
import com.hms.service.hospital.DoctorOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class NurseTaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NurseTaskScheduler.class);

    @Autowired private DoctorOrderRepository orderRepository;
    @Autowired private DoctorOrderService orderService;

    // Runs daily at 6 AM — creates today's tasks for all recurring active orders
    @Scheduled(cron = "0 0 6 * * *")
    public void generateDailyTasks() {
        logger.info("NurseTaskScheduler: generating daily tasks");
        List<DoctorOrder> recurringOrders = orderRepository
                .findByHospitalIdAndStatusAndFrequencyNot(null, "ACTIVE", "SOS");
        // null hospitalId means we fetch across all hospitals; replace with
        // a query that returns all active non-SOS orders regardless of hospital
        for (DoctorOrder order : recurringOrders) {
            try {
                orderService.createTaskForOrder(order, LocalDateTime.now().withHour(8).withMinute(0).withSecond(0));
            } catch (Exception e) {
                logger.warn("Failed to create task for order {}: {}", order.getId(), e.getMessage());
            }
        }
        logger.info("NurseTaskScheduler: done, processed {} orders", recurringOrders.size());
    }
}
```

> **Note:** Update `DoctorOrderRepository` to add a method that returns all active non-SOS orders across all hospitals:
> ```java
> List<DoctorOrder> findByStatusAndFrequencyNot(String status, String frequency);
> ```
> Then use `orderRepository.findByStatusAndFrequencyNot("ACTIVE", "SOS")` in the scheduler.

- [ ] **Step 5: Enable scheduling in main app class**

Open the Spring Boot main application class (usually `HmsApplication.java`) and add:

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling  // add this annotation
@SpringBootApplication
public class HmsApplication { ... }
```

- [ ] **Step 6: Compile**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/NurseDashboardService.java \
        backend/src/main/java/com/hms/controller/hospital/NurseDashboardController.java \
        backend/src/main/java/com/hms/scheduler/NurseTaskScheduler.java
git commit -m "feat: add NurseDashboardService, controller, and daily task scheduler"
```

---

## Task 12: Run full backend test suite + manual smoke test

- [ ] **Step 1: Run all tests**

```bash
cd backend && mvn test -q
```
Expected: all tests pass (or same failures as before this feature — don't regress existing tests).

- [ ] **Step 2: Start backend and test key endpoints**

```bash
cd backend && mvn spring-boot:run
```

In a second terminal, get a HOSPITAL_ADMIN JWT first:
```bash
curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@yourhospital.com","password":"yourpassword"}' | jq .token
```

Then test nurse creation:
```bash
curl -s -X POST http://localhost:8080/hospital/nurses \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Nurse Priya","email":"priya@test.com","password":"pass123","phone":"9876543210"}'
```
Expected: JSON with user object and role="NURSE".

- [ ] **Step 3: Commit if any fixes were needed**

```bash
git add -p && git commit -m "fix: nurse backend smoke test corrections"
```

---

## Task 13: Frontend — nurseService.js

**Files:**
- Create: `frontend/src/services/nurseService.js`

- [ ] **Step 1: Create nurseService.js**

```javascript
import apiService from './apiService';

const nurseService = {
  // Admin nurse management
  getNurses: (search = '', page = 0, size = 10) =>
    apiService.get('/hospital/nurses', { params: { search, page, size } }),
  createNurse: (data) => apiService.post('/hospital/nurses', data),
  updateNurse: (id, data) => apiService.put(`/hospital/nurses/${id}`, data),
  deleteNurse: (id) => apiService.delete(`/hospital/nurses/${id}`),
  resetPassword: (id, newPassword) =>
    apiService.post(`/hospital/nurses/${id}/reset-password`, { newPassword }),
  assignWard: (id, wardId) =>
    apiService.post(`/hospital/nurses/${id}/assign-ward`, { wardId }),
  removeWard: (id, wardId) =>
    apiService.delete(`/hospital/nurses/${id}/assign-ward/${wardId}`),

  // Nurse dashboard
  getMyPatients: () => apiService.get('/hospital/nurse/dashboard/patients'),
  getMyTasks: () => apiService.get('/hospital/nurse/dashboard/my-tasks'),

  // Assessment
  createAssessment: (admissionId, data) =>
    apiService.post(`/api/ipd/${admissionId}/assessment`, data),
  getAssessment: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/assessment`),

  // Vitals
  recordVitals: (admissionId, data) =>
    apiService.post(`/api/ipd/${admissionId}/vitals`, data),
  getVitals: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/vitals`),

  // Orders (doctor creates, nurse reads)
  getOrders: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/orders`),
  createOrder: (admissionId, data) =>
    apiService.post(`/api/ipd/${admissionId}/orders`, data),
  updateOrder: (admissionId, publicId, data) =>
    apiService.put(`/api/ipd/${admissionId}/orders/${publicId}`, data),
  cancelOrder: (admissionId, publicId) =>
    apiService.delete(`/api/ipd/${admissionId}/orders/${publicId}`),

  // Tasks
  getTasks: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/tasks`),
  getPendingTasks: (admissionId) =>
    apiService.get(`/api/ipd/${admissionId}/tasks/pending`),
  executeTask: (admissionId, taskId, status, notes = '') =>
    apiService.put(`/api/ipd/${admissionId}/tasks/${taskId}/execute`, { status, notes }),
};

export default nurseService;
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/services/nurseService.js
git commit -m "feat: add nurseService.js (axios wrappers for nurse API)"
```

---

## Task 14: Frontend — NurseDashboard.jsx

**Files:**
- Create: `frontend/src/pages/hospital/NurseDashboard.jsx`

- [ ] **Step 1: Create NurseDashboard.jsx**

```jsx
import { useState, useEffect } from 'react';
import nurseService from '../../services/nurseService';
import PatientClinicalRecord from '../../components/nurse/PatientClinicalRecord';

export default function NurseDashboard() {
  const [tab, setTab] = useState('patients');
  const [patients, setPatients] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [selectedAdmission, setSelectedAdmission] = useState(null);
  const [wardFilter, setWardFilter] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (tab === 'patients') loadPatients();
    if (tab === 'tasks') loadTasks();
  }, [tab]);

  async function loadPatients() {
    setLoading(true);
    try {
      const res = await nurseService.getMyPatients();
      setPatients(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function loadTasks() {
    setLoading(true);
    try {
      const res = await nurseService.getMyTasks();
      setTasks(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleExecuteTask(task, status) {
    try {
      await nurseService.executeTask(task.ipdAdmissionId, task.id, status);
      loadTasks();
    } catch (e) {
      alert(e.response?.data || 'Failed to update task');
    }
  }

  const wards = [...new Set(patients.map(p => p.wardName).filter(Boolean))];
  const filteredPatients = wardFilter
    ? patients.filter(p => p.wardName === wardFilter)
    : patients;

  if (selectedAdmission) {
    return (
      <PatientClinicalRecord
        admission={selectedAdmission}
        onBack={() => setSelectedAdmission(null)}
      />
    );
  }

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-gray-800 mb-6">Nurse Dashboard</h1>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 border-b border-gray-200">
        {['patients', 'tasks'].map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium capitalize border-b-2 transition-colors ${
              tab === t
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {t === 'patients' ? 'My Patients' : 'My Tasks'}
          </button>
        ))}
      </div>

      {loading && <div className="text-center py-8 text-gray-500">Loading...</div>}

      {/* My Patients */}
      {!loading && tab === 'patients' && (
        <div>
          {wards.length > 0 && (
            <div className="mb-4">
              <select
                value={wardFilter}
                onChange={e => setWardFilter(e.target.value)}
                className="border border-gray-300 rounded px-3 py-2 text-sm"
              >
                <option value="">All Wards</option>
                {wards.map(w => <option key={w} value={w}>{w}</option>)}
              </select>
            </div>
          )}
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 text-left text-gray-600 uppercase text-xs tracking-wide">
                  <th className="px-4 py-3">Patient</th>
                  <th className="px-4 py-3">UHID</th>
                  <th className="px-4 py-3">Ward / Bed</th>
                  <th className="px-4 py-3">Admitted</th>
                  <th className="px-4 py-3">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredPatients.length === 0 && (
                  <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">No admitted patients</td></tr>
                )}
                {filteredPatients.map(p => (
                  <tr key={p.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-800">{p.patientName}</td>
                    <td className="px-4 py-3 text-gray-500">{p.uhid || '—'}</td>
                    <td className="px-4 py-3 text-gray-500">{p.wardName} / {p.bedNumber}</td>
                    <td className="px-4 py-3 text-gray-500">
                      {p.admissionDate ? new Date(p.admissionDate).toLocaleDateString() : '—'}
                    </td>
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setSelectedAdmission(p)}
                        className="text-blue-600 hover:underline text-sm"
                      >
                        Open
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* My Tasks */}
      {!loading && tab === 'tasks' && (
        <div className="space-y-3">
          {tasks.length === 0 && (
            <div className="text-center py-8 text-gray-400">No pending tasks</div>
          )}
          {tasks.map(task => {
            const isOverdue = task.scheduledAt && new Date(task.scheduledAt) < new Date();
            return (
              <div
                key={task.id}
                className={`flex items-center justify-between p-4 rounded-lg border ${
                  isOverdue ? 'border-red-200 bg-red-50' : 'border-gray-200 bg-white'
                }`}
              >
                <div>
                  <p className="font-medium text-gray-800">{task.orderDescription}</p>
                  <p className="text-sm text-gray-500 mt-0.5">
                    Patient: {task.patientName} &nbsp;·&nbsp;
                    {task.orderType} &nbsp;·&nbsp;
                    {task.scheduledAt
                      ? new Date(task.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                      : 'No time set'}
                    {isOverdue && <span className="ml-2 text-red-600 font-semibold">OVERDUE</span>}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleExecuteTask(task, 'DONE')}
                    className="px-3 py-1.5 bg-green-600 text-white text-sm rounded hover:bg-green-700"
                  >
                    Done
                  </button>
                  <button
                    onClick={() => handleExecuteTask(task, 'SKIPPED')}
                    className="px-3 py-1.5 bg-gray-200 text-gray-700 text-sm rounded hover:bg-gray-300"
                  >
                    Skip
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/hospital/NurseDashboard.jsx
git commit -m "feat: add NurseDashboard (My Patients + My Tasks tabs)"
```

---

## Task 15: Frontend — PatientClinicalRecord + sub-components

**Files:**
- Create: `frontend/src/components/nurse/PatientClinicalRecord.jsx`
- Create: `frontend/src/components/nurse/NurseAssessmentForm.jsx`
- Create: `frontend/src/components/nurse/VitalsForm.jsx`

- [ ] **Step 1: Create NurseAssessmentForm.jsx**

```jsx
import { useState } from 'react';
import nurseService from '../../services/nurseService';

export default function NurseAssessmentForm({ admissionId, onSaved }) {
  const [form, setForm] = useState({
    bloodPressure: '', pulse: '', temperature: '', spo2: '',
    height: '', weight: '', painScore: '', allergies: '',
    fallRisk: 'LOW', generalCondition: '', chiefComplaintOnAdmission: '',
  });
  const [saving, setSaving] = useState(false);

  const field = (key, label, type = 'text', extra = {}) => (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      <input
        type={type}
        value={form[key]}
        onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
        className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
        {...extra}
      />
    </div>
  );

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    try {
      await nurseService.createAssessment(admissionId, form);
      onSaved();
    } catch (err) {
      alert(err.response?.data || 'Failed to save assessment');
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <h3 className="font-semibold text-gray-800">Initial Assessment</h3>
      <div className="grid grid-cols-2 gap-4">
        {field('bloodPressure', 'Blood Pressure (e.g. 120/80)')}
        {field('pulse', 'Pulse (bpm)', 'number')}
        {field('temperature', 'Temperature (°F)', 'number', { step: '0.1' })}
        {field('spo2', 'SpO2 (%)', 'number')}
        {field('height', 'Height (cm)', 'number', { step: '0.1' })}
        {field('weight', 'Weight (kg)', 'number', { step: '0.1' })}
        {field('painScore', 'Pain Score (0–10)', 'number', { min: 0, max: 10 })}
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Fall Risk</label>
        <select
          value={form.fallRisk}
          onChange={e => setForm(f => ({ ...f, fallRisk: e.target.value }))}
          className="border border-gray-300 rounded px-3 py-2 text-sm"
        >
          {['LOW', 'MEDIUM', 'HIGH'].map(r => <option key={r}>{r}</option>)}
        </select>
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Allergies</label>
        <textarea
          value={form.allergies}
          onChange={e => setForm(f => ({ ...f, allergies: e.target.value }))}
          className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
          rows={2}
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Chief Complaint on Admission</label>
        <textarea
          value={form.chiefComplaintOnAdmission}
          onChange={e => setForm(f => ({ ...f, chiefComplaintOnAdmission: e.target.value }))}
          className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
          rows={2}
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">General Condition</label>
        <textarea
          value={form.generalCondition}
          onChange={e => setForm(f => ({ ...f, generalCondition: e.target.value }))}
          className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
          rows={2}
        />
      </div>
      <button
        type="submit"
        disabled={saving}
        className="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50"
      >
        {saving ? 'Saving...' : 'Save Assessment'}
      </button>
    </form>
  );
}
```

- [ ] **Step 2: Create VitalsForm.jsx**

```jsx
import { useState } from 'react';
import nurseService from '../../services/nurseService';

export default function VitalsForm({ admissionId, onSaved }) {
  const [form, setForm] = useState({ bloodPressure: '', pulse: '', temperature: '', spo2: '' });
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    try {
      await nurseService.recordVitals(admissionId, form);
      setForm({ bloodPressure: '', pulse: '', temperature: '', spo2: '' });
      onSaved();
    } catch (err) {
      alert(err.response?.data || 'Failed to record vitals');
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap gap-3 items-end">
      {[
        ['bloodPressure', 'BP', 'text'],
        ['pulse', 'Pulse', 'number'],
        ['temperature', 'Temp °F', 'number'],
        ['spo2', 'SpO2 %', 'number'],
      ].map(([key, label, type]) => (
        <div key={key}>
          <label className="block text-xs text-gray-600 mb-1">{label}</label>
          <input
            type={type}
            value={form[key]}
            onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
            className="border border-gray-300 rounded px-2 py-1.5 text-sm w-24"
          />
        </div>
      ))}
      <button
        type="submit"
        disabled={saving}
        className="px-3 py-1.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50"
      >
        {saving ? '...' : 'Record'}
      </button>
    </form>
  );
}
```

- [ ] **Step 3: Create PatientClinicalRecord.jsx**

```jsx
import { useState, useEffect } from 'react';
import nurseService from '../../services/nurseService';
import NurseAssessmentForm from './NurseAssessmentForm';
import VitalsForm from './VitalsForm';

export default function PatientClinicalRecord({ admission, onBack }) {
  const [tab, setTab] = useState('assessment');
  const [assessment, setAssessment] = useState(null);
  const [vitals, setVitals] = useState([]);
  const [orders, setOrders] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => { loadData(); }, [tab]);

  async function loadData() {
    setLoading(true);
    try {
      if (tab === 'assessment') {
        const r = await nurseService.getAssessment(admission.id);
        setAssessment(r.data);
      } else if (tab === 'vitals') {
        const r = await nurseService.getVitals(admission.id);
        setVitals(r.data);
      } else if (tab === 'orders') {
        const [ordRes, taskRes] = await Promise.all([
          nurseService.getOrders(admission.id),
          nurseService.getTasks(admission.id),
        ]);
        setOrders(ordRes.data);
        setTasks(taskRes.data);
      }
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }

  const fallRiskColor = { LOW: 'text-green-600', MEDIUM: 'text-yellow-600', HIGH: 'text-red-600' };

  return (
    <div className="p-6">
      <button onClick={onBack} className="text-blue-600 hover:underline text-sm mb-4">
        ← Back to patients
      </button>
      <h2 className="text-xl font-bold text-gray-800 mb-1">{admission.patientName}</h2>
      <p className="text-sm text-gray-500 mb-4">
        {admission.uhid} &nbsp;·&nbsp; {admission.wardName} / {admission.bedNumber}
      </p>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 border-b border-gray-200">
        {['assessment', 'vitals', 'orders'].map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium capitalize border-b-2 transition-colors ${
              tab === t ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {t === 'orders' ? 'Orders & Tasks' : t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {loading && <div className="text-center py-8 text-gray-400">Loading...</div>}

      {/* Assessment Tab */}
      {!loading && tab === 'assessment' && (
        assessment
          ? (
            <div className="grid grid-cols-2 gap-4 text-sm">
              {[
                ['Blood Pressure', assessment.bloodPressure],
                ['Pulse', assessment.pulse ? `${assessment.pulse} bpm` : '—'],
                ['Temperature', assessment.temperature ? `${assessment.temperature} °F` : '—'],
                ['SpO2', assessment.spo2 ? `${assessment.spo2}%` : '—'],
                ['Height', assessment.height ? `${assessment.height} cm` : '—'],
                ['Weight', assessment.weight ? `${assessment.weight} kg` : '—'],
                ['Pain Score', assessment.painScore ?? '—'],
                ['Allergies', assessment.allergies || 'None'],
                ['General Condition', assessment.generalCondition || '—'],
                ['Chief Complaint', assessment.chiefComplaintOnAdmission || '—'],
              ].map(([label, val]) => (
                <div key={label}>
                  <span className="text-gray-500">{label}: </span>
                  <span className="font-medium">{val}</span>
                </div>
              ))}
              <div>
                <span className="text-gray-500">Fall Risk: </span>
                <span className={`font-semibold ${fallRiskColor[assessment.fallRisk] || ''}`}>
                  {assessment.fallRisk}
                </span>
              </div>
              <div className="col-span-2 text-xs text-gray-400 mt-2">
                Assessed by {assessment.assessedByName} on{' '}
                {assessment.assessedAt ? new Date(assessment.assessedAt).toLocaleString() : '—'}
              </div>
            </div>
          )
          : <NurseAssessmentForm admissionId={admission.id} onSaved={loadData} />
      )}

      {/* Vitals Tab */}
      {!loading && tab === 'vitals' && (
        <div>
          <div className="mb-4">
            <VitalsForm admissionId={admission.id} onSaved={loadData} />
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 text-left text-gray-600 uppercase text-xs tracking-wide">
                <th className="px-4 py-2">Time</th>
                <th className="px-4 py-2">BP</th>
                <th className="px-4 py-2">Pulse</th>
                <th className="px-4 py-2">Temp</th>
                <th className="px-4 py-2">SpO2</th>
                <th className="px-4 py-2">Recorded By</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {vitals.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-4 text-center text-gray-400">No vitals recorded yet</td></tr>
              )}
              {vitals.map(v => (
                <tr key={v.id}>
                  <td className="px-4 py-2">{v.recordedAt ? new Date(v.recordedAt).toLocaleString() : '—'}</td>
                  <td className="px-4 py-2">{v.bloodPressure || '—'}</td>
                  <td className="px-4 py-2">{v.pulse ?? '—'}</td>
                  <td className="px-4 py-2">{v.temperature ?? '—'}</td>
                  <td className="px-4 py-2">{v.spo2 ? `${v.spo2}%` : '—'}</td>
                  <td className="px-4 py-2">{v.recordedByName || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Orders & Tasks Tab */}
      {!loading && tab === 'orders' && (
        <div className="space-y-3">
          {orders.length === 0 && (
            <div className="text-center py-6 text-gray-400">No orders yet</div>
          )}
          {orders.map(order => {
            const orderTasks = tasks.filter(t => t.doctorOrderId === order.id);
            const pendingCount = orderTasks.filter(t => t.status === 'PENDING').length;
            return (
              <div key={order.id} className="border border-gray-200 rounded-lg p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <span className={`text-xs px-2 py-0.5 rounded font-medium mr-2 ${
                      order.orderType === 'MEDICATION' ? 'bg-blue-100 text-blue-700' :
                      order.orderType === 'INVESTIGATION' ? 'bg-purple-100 text-purple-700' :
                      'bg-gray-100 text-gray-700'
                    }`}>{order.orderType}</span>
                    <span className="font-medium text-gray-800">{order.description}</span>
                  </div>
                  <span className={`text-xs px-2 py-0.5 rounded ${
                    order.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                    order.status === 'CANCELLED' ? 'bg-red-100 text-red-700' :
                    'bg-gray-100 text-gray-600'
                  }`}>{order.status}</span>
                </div>
                <div className="mt-1 text-sm text-gray-500">
                  Frequency: {order.frequency || '—'} &nbsp;·&nbsp; By: {order.createdByName}
                  {pendingCount > 0 && (
                    <span className="ml-2 bg-yellow-100 text-yellow-700 text-xs px-2 py-0.5 rounded">
                      {pendingCount} pending task{pendingCount > 1 ? 's' : ''}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/nurse/
git commit -m "feat: add PatientClinicalRecord, NurseAssessmentForm, VitalsForm components"
```

---

## Task 16: Frontend — DoctorOrdersPanel (doctor side in IPD)

**Files:**
- Create: `frontend/src/components/nurse/DoctorOrdersPanel.jsx`
- Modify: existing IPD detail component — add Orders tab

- [ ] **Step 1: Create DoctorOrdersPanel.jsx**

```jsx
import { useState, useEffect } from 'react';
import nurseService from '../../services/nurseService';

export default function DoctorOrdersPanel({ admissionId }) {
  const [orders, setOrders] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    orderType: 'MEDICATION', description: '', frequency: 'BD', notes: '',
  });
  const [saving, setSaving] = useState(false);

  useEffect(() => { load(); }, [admissionId]);

  async function load() {
    try {
      const r = await nurseService.getOrders(admissionId);
      setOrders(r.data);
    } catch (e) { console.error(e); }
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!form.description.trim()) return alert('Description is required');
    setSaving(true);
    try {
      await nurseService.createOrder(admissionId, form);
      setForm({ orderType: 'MEDICATION', description: '', frequency: 'BD', notes: '' });
      setShowForm(false);
      load();
    } catch (err) {
      alert(err.response?.data || 'Failed to create order');
    } finally {
      setSaving(false);
    }
  }

  async function handleCancel(publicId) {
    if (!confirm('Cancel this order?')) return;
    try {
      await nurseService.cancelOrder(admissionId, publicId);
      load();
    } catch (err) {
      alert(err.response?.data || 'Failed to cancel');
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="font-semibold text-gray-800">Doctor Orders</h3>
        <button
          onClick={() => setShowForm(v => !v)}
          className="px-3 py-1.5 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
        >
          {showForm ? 'Cancel' : '+ New Order'}
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="border border-gray-200 rounded-lg p-4 space-y-3 bg-gray-50">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-gray-600 mb-1">Type</label>
              <select
                value={form.orderType}
                onChange={e => setForm(f => ({ ...f, orderType: e.target.value }))}
                className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
              >
                {['MEDICATION', 'INVESTIGATION', 'PROCEDURE', 'DIET'].map(t => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-600 mb-1">Frequency</label>
              <select
                value={form.frequency}
                onChange={e => setForm(f => ({ ...f, frequency: e.target.value }))}
                className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
              >
                {['OD', 'BD', 'TDS', 'QID', 'SOS', 'Once'].map(f => (
                  <option key={f}>{f}</option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Description *</label>
            <input
              type="text"
              value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
              placeholder="e.g. Ceftriaxone 1g IV"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Notes</label>
            <input
              type="text"
              value={form.notes}
              onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
              className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={saving}
            className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Create Order'}
          </button>
        </form>
      )}

      {orders.length === 0 && !showForm && (
        <div className="text-center py-6 text-gray-400 text-sm">No orders yet</div>
      )}

      {orders.map(order => (
        <div key={order.id} className="border border-gray-200 rounded-lg p-3 flex justify-between items-start">
          <div>
            <span className={`text-xs px-2 py-0.5 rounded font-medium mr-2 ${
              order.orderType === 'MEDICATION' ? 'bg-blue-100 text-blue-700' :
              order.orderType === 'INVESTIGATION' ? 'bg-purple-100 text-purple-700' :
              'bg-gray-100 text-gray-700'
            }`}>{order.orderType}</span>
            <span className="font-medium text-gray-800 text-sm">{order.description}</span>
            <p className="text-xs text-gray-400 mt-0.5">
              {order.frequency} &nbsp;·&nbsp; {order.createdByName} &nbsp;·&nbsp;{' '}
              {new Date(order.createdAt).toLocaleDateString()}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className={`text-xs px-2 py-0.5 rounded ${
              order.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
              order.status === 'CANCELLED' ? 'bg-red-100 text-red-700' :
              'bg-gray-100 text-gray-600'
            }`}>{order.status}</span>
            {order.status === 'ACTIVE' && (
              <button
                onClick={() => handleCancel(order.publicId)}
                className="text-xs text-red-500 hover:underline"
              >
                Cancel
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Find the IPD detail component used by doctors**

Search for the component that renders the IPD detail view. It's likely used in `HospitalAdminDashboard.jsx` or `DoctorDashboard.jsx`. Run:

```bash
grep -r "IpdDetail\|ipd.*detail\|ipdAdmission.*details\|followup\|plan-discharge" \
  frontend/src --include="*.jsx" -l
```

Open that file and locate the existing tabs (e.g. "Follow-up", "Billing"). Add a new "Orders" tab that renders `<DoctorOrdersPanel admissionId={selectedIpd.id} />`.

The exact integration depends on the existing component structure. The pattern to follow:
1. Import `DoctorOrdersPanel` at the top of the file.
2. Add `'orders'` to the tab list.
3. In the tab content switch, render `<DoctorOrdersPanel admissionId={admission.id} />` when `activeTab === 'orders'`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/nurse/DoctorOrdersPanel.jsx
git add frontend/src  # any modified IPD detail files
git commit -m "feat: add DoctorOrdersPanel and integrate Orders tab into IPD detail"
```

---

## Task 17: Frontend — Auth, routing, sidebar, Admin Nurses tab

**Files:**
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/services/authService.js`
- Modify: `frontend/src/components/Sidebar.jsx`
- Modify: `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`

- [ ] **Step 1: Add /nurse-dashboard route in App.jsx**

Find the section in `App.jsx` where protected routes are declared. Add:

```jsx
import NurseDashboard from './pages/hospital/NurseDashboard';

// Inside the Routes block, alongside other protected routes:
<Route
  path="/nurse-dashboard"
  element={
    <ProtectedRoute allowedRoles={['NURSE']}>
      <NurseDashboard />
    </ProtectedRoute>
  }
/>
```

If `ProtectedRoute` takes a prop like `role` instead of `allowedRoles`, match the existing pattern from nearby route declarations.

- [ ] **Step 2: Add NURSE redirect in authService.js**

Find the post-login redirect logic. It typically looks like a `switch` or `if/else` on the user's role. Add:

```javascript
case 'NURSE':
  return '/nurse-dashboard';
```

- [ ] **Step 3: Add NURSE items to Sidebar.jsx**

Find the sidebar items array or the section that conditionally renders items by role. Add a NURSE-only section:

```jsx
// Render only when role === 'NURSE'
{role === 'NURSE' && (
  <>
    <SidebarItem icon={<UsersIcon />} label="My Patients" path="/nurse-dashboard" tab="patients" />
    <SidebarItem icon={<ClipboardIcon />} label="My Tasks" path="/nurse-dashboard" tab="tasks" />
  </>
)}
```

Use the same icon components already imported in Sidebar.jsx. Match the exact SidebarItem component signature used by existing items.

- [ ] **Step 4: Add Nurses tab to HospitalAdminDashboard.jsx**

Find the tab list in `HospitalAdminDashboard.jsx` (alongside "Doctors", "Receptionists"). Add `'Nurses'` to the array. Then find the tab content switch and add a `NursesTab` section using the same pattern as `ReceptionistsTab`:

```jsx
{activeTab === 'nurses' && (
  <NursesTab />
)}
```

Create `NursesTab` as a local component at the bottom of the file (same pattern as `DoctorsTable`, `ReceptionistsTable`):

```jsx
function NursesTab() {
  const [nurses, setNurses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' });
  const { showToast } = useToast();

  useEffect(() => { loadNurses(); }, []);

  async function loadNurses() {
    setLoading(true);
    try {
      const r = await nurseService.getNurses();
      setNurses(r.data.content || r.data);
    } catch (e) { showToast('Failed to load nurses', 'error'); }
    finally { setLoading(false); }
  }

  async function handleCreate(e) {
    e.preventDefault();
    try {
      await nurseService.createNurse(form);
      showToast('Nurse created successfully', 'success');
      setShowAdd(false);
      setForm({ name: '', email: '', password: '', phone: '' });
      loadNurses();
    } catch (err) {
      showToast(err.response?.data || 'Failed to create nurse', 'error');
    }
  }

  async function handleDelete(id) {
    if (!confirm('Delete this nurse?')) return;
    try {
      await nurseService.deleteNurse(id);
      showToast('Nurse deleted', 'success');
      loadNurses();
    } catch (e) { showToast('Failed to delete', 'error'); }
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-semibold">Nurses</h2>
        <button
          onClick={() => setShowAdd(v => !v)}
          className="px-3 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
        >
          {showAdd ? 'Cancel' : '+ Add Nurse'}
        </button>
      </div>

      {showAdd && (
        <form onSubmit={handleCreate} className="grid grid-cols-2 gap-3 mb-4 p-4 border rounded-lg bg-gray-50">
          {[['name','Name'],['email','Email'],['password','Password'],['phone','Phone']].map(([key, label]) => (
            <div key={key}>
              <label className="block text-xs text-gray-600 mb-1">{label}</label>
              <input
                type={key === 'password' ? 'password' : 'text'}
                value={form[key]}
                onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
                className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
                required={key !== 'phone'}
              />
            </div>
          ))}
          <div className="col-span-2">
            <button type="submit" className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700">
              Create Nurse
            </button>
          </div>
        </form>
      )}

      {loading && <div className="text-center py-6 text-gray-400">Loading...</div>}
      <table className="w-full text-sm">
        <thead>
          <tr className="bg-gray-50 text-left text-gray-600 uppercase text-xs">
            <th className="px-4 py-3">Name</th>
            <th className="px-4 py-3">Email</th>
            <th className="px-4 py-3">Phone</th>
            <th className="px-4 py-3">ID</th>
            <th className="px-4 py-3">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {nurses.map(n => (
            <tr key={n.publicId}>
              <td className="px-4 py-3 font-medium">{n.name}</td>
              <td className="px-4 py-3 text-gray-500">{n.email}</td>
              <td className="px-4 py-3 text-gray-500">{n.phone || '—'}</td>
              <td className="px-4 py-3 text-gray-400">{n.customId}</td>
              <td className="px-4 py-3">
                <button
                  onClick={() => handleDelete(n.publicId)}
                  className="text-red-500 hover:underline text-xs"
                >
                  Delete
                </button>
              </td>
            </tr>
          ))}
          {!loading && nurses.length === 0 && (
            <tr><td colSpan={5} className="text-center py-6 text-gray-400">No nurses yet</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
```

Add `import nurseService from '../../services/nurseService';` at the top of the dashboard file.

- [ ] **Step 5: Start frontend and manually verify**

```bash
cd frontend && npm run dev
```

Verify:
1. Log in as HOSPITAL_ADMIN → see Nurses tab → can add a nurse
2. Log in as the new nurse → redirected to `/nurse-dashboard`
3. My Patients shows admitted IPD patients
4. Open a patient → Assessment form appears → fill and save → form becomes read-only
5. Record Vitals → new row appears in the table
6. Log in as DOCTOR → open an IPD patient → Orders tab present → create an order
7. Log back in as nurse → My Tasks shows the new task → click Done → task disappears

- [ ] **Step 6: Commit**

```bash
git add frontend/src/App.jsx \
        frontend/src/services/authService.js \
        frontend/src/components/Sidebar.jsx \
        frontend/src/pages/hospital/HospitalAdminDashboard.jsx
git commit -m "feat: wire NURSE role into routing, sidebar, and admin dashboard"
```

---

## Task 18: Final verification

- [ ] **Step 1: Run all backend tests**

```bash
cd backend && mvn test -q
```
Expected: no regressions.

- [ ] **Step 2: Build frontend**

```bash
cd frontend && npm run build
```
Expected: no TypeScript or build errors.

- [ ] **Step 3: Walk the full happy path**

1. Admin creates nurse (NRS1) → nurse logs in → sees `/nurse-dashboard`
2. A patient is admitted (via existing IPD flow) → nurse sees them in My Patients
3. Nurse opens patient → fills Initial Assessment → saved, read-only
4. Nurse records vitals → vitals list updates
5. Doctor logs in → opens same IPD patient → Orders tab → creates "Ceftriaxone 1g IV / BD"
6. Nurse logs in → My Tasks shows the order as a pending task
7. Nurse clicks Done → task marked executed
8. Doctor refreshes IPD detail → sees task was executed by nurse

- [ ] **Step 4: Final commit**

```bash
git add -p
git commit -m "feat: nurse IPD workflow Phase 1 complete (assessment, vitals, orders, tasks)"
```
