# Master Data Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce controlled catalogs (Lab Tests, Radiology Tests, Allergies, Diagnoses, Procedures) and link existing clinical entities to them, eliminating free-text inconsistencies and laying the foundation for CDSS.

**Architecture:** Six new hospital-scoped JPA entities + one join entity (PatientAllergy). A single `MasterDataService` handles all five read/write catalogs. A `PatientAllergyService` handles per-patient allergy records. All read endpoints are open to all hospital roles; write endpoints are HOSPITAL_ADMIN only. Three existing entities (LabOrder, RadiologyOrder, Prescription) get nullable FK columns — fully backward-compatible.

**Tech Stack:** Spring Boot 3 / JPA / MySQL, React 18 / Vite, @tanstack/react-table, Axios

---

## File Map

**Create (backend):**
- `entity/LabTestMaster.java`
- `entity/RadiologyTestMaster.java`
- `entity/AllergyMaster.java`
- `entity/PatientAllergy.java`
- `entity/DiagnosisMaster.java`
- `entity/ProcedureMaster.java`
- `repository/LabTestMasterRepository.java`
- `repository/RadiologyTestMasterRepository.java`
- `repository/AllergyMasterRepository.java`
- `repository/PatientAllergyRepository.java`
- `repository/DiagnosisMasterRepository.java`
- `repository/ProcedureMasterRepository.java`
- `service/hospital/MasterDataService.java`
- `service/hospital/PatientAllergyService.java`
- `controller/hospital/MasterDataController.java`
- `controller/hospital/PatientAllergyController.java`
- `test/java/com/hms/service/MasterDataServiceTest.java`
- `test/java/com/hms/service/PatientAllergyServiceTest.java`

**Modify (backend):**
- `entity/LabOrder.java` — add `labTestMasterId` column
- `entity/RadiologyOrder.java` — add `radiologyTestMasterId` column
- `entity/Prescription.java` — add `medicineMasterId` column

**Create (migration):**
- `setup/migrations/2026-06-30-master-data.sql`

**Create (frontend):**
- `frontend/src/services/masterDataService.js`
- `frontend/src/pages/hospital/MasterDataView.jsx`
- `frontend/src/components/SearchableSelect.jsx`

**Modify (frontend):**
- `frontend/src/pages/hospital/HospitalAdminDashboard.jsx` — add Masters tab
- `frontend/src/pages/hospital/IpdDetails.jsx` — add patient allergy card
- `frontend/src/services/hospitalService.js` — add patient allergy API calls

---

## Task 1: Database Migration

**Files:**
- Create: `setup/migrations/2026-06-30-master-data.sql`

- [ ] **Step 1: Write the migration SQL**

```sql
-- =============================================================
-- Master Data Architecture Migration
-- Date: 2026-06-30
-- =============================================================

-- 1. Lab Test Master
CREATE TABLE IF NOT EXISTS `lab_test_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `test_code` varchar(50) DEFAULT NULL,
  `test_name` varchar(200) NOT NULL,
  `department` varchar(50) NOT NULL DEFAULT 'OTHER',
  `sample_type` varchar(50) NOT NULL DEFAULT 'BLOOD',
  `normal_range_text` varchar(500) DEFAULT NULL,
  `unit` varchar(50) DEFAULT NULL,
  `turnaround_hours` int DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_ltm_hospital` (`hospital_id`),
  KEY `idx_ltm_name` (`test_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Radiology Test Master
CREATE TABLE IF NOT EXISTS `radiology_test_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `test_code` varchar(50) DEFAULT NULL,
  `test_name` varchar(200) NOT NULL,
  `modality` varchar(50) NOT NULL DEFAULT 'OTHER',
  `preparation_instructions` text DEFAULT NULL,
  `estimated_duration_minutes` int DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_rtm_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Allergy Master
CREATE TABLE IF NOT EXISTS `allergy_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `allergy_name` varchar(200) NOT NULL,
  `category` varchar(50) NOT NULL DEFAULT 'OTHER',
  `is_custom` bit(1) NOT NULL DEFAULT 0,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_am_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Patient Allergies
CREATE TABLE IF NOT EXISTS `patient_allergies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `patient_id` bigint NOT NULL,
  `allergy_master_id` bigint NOT NULL,
  `severity` varchar(20) NOT NULL DEFAULT 'UNKNOWN',
  `notes` text DEFAULT NULL,
  `recorded_by_user_id` bigint DEFAULT NULL,
  `recorded_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_pa_patient` (`patient_id`),
  KEY `idx_pa_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Diagnosis Master
CREATE TABLE IF NOT EXISTS `diagnosis_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `icd_code` varchar(20) NOT NULL,
  `icd_description` varchar(500) NOT NULL,
  `category` varchar(50) NOT NULL DEFAULT 'OTHER',
  `is_custom` bit(1) NOT NULL DEFAULT 0,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_dm_hospital` (`hospital_id`),
  KEY `idx_dm_code` (`icd_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Procedure Master
CREATE TABLE IF NOT EXISTS `procedure_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `procedure_code` varchar(50) DEFAULT NULL,
  `procedure_name` varchar(200) NOT NULL,
  `department` varchar(100) DEFAULT NULL,
  `estimated_duration_minutes` int DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_pm_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Add FK columns to existing tables (nullable for backward compat)
ALTER TABLE `lab_orders`
  ADD COLUMN IF NOT EXISTS `lab_test_master_id` bigint DEFAULT NULL;

ALTER TABLE `radiology_orders`
  ADD COLUMN IF NOT EXISTS `radiology_test_master_id` bigint DEFAULT NULL;

ALTER TABLE `prescriptions`
  ADD COLUMN IF NOT EXISTS `medicine_master_id` bigint DEFAULT NULL;

-- 8. Seed: Allergy Master (hospital_id=0 means platform-level seed — each hospital copies on first load)
-- NOTE: Seeds are inserted per-hospital by MasterDataService.seedDefaultsForHospital()
-- No raw seed inserts here — service handles it at hospital onboarding time.
```

- [ ] **Step 2: Run the migration against local DB**

```bash
mysql -u root -p hospital_management < setup/migrations/2026-06-30-master-data.sql
```

Expected: No errors. Verify with `SHOW TABLES LIKE '%master%';` — should show 5 new tables.

- [ ] **Step 3: Commit**

```bash
git add setup/migrations/2026-06-30-master-data.sql
git commit -m "feat(master-data): add DB migration for 6 master tables + FK columns on existing tables"
```

---

## Task 2: Backend Entities

**Files:**
- Create: `backend/src/main/java/com/hms/entity/LabTestMaster.java`
- Create: `backend/src/main/java/com/hms/entity/RadiologyTestMaster.java`
- Create: `backend/src/main/java/com/hms/entity/AllergyMaster.java`
- Create: `backend/src/main/java/com/hms/entity/PatientAllergy.java`
- Create: `backend/src/main/java/com/hms/entity/DiagnosisMaster.java`
- Create: `backend/src/main/java/com/hms/entity/ProcedureMaster.java`
- Modify: `backend/src/main/java/com/hms/entity/LabOrder.java`
- Modify: `backend/src/main/java/com/hms/entity/RadiologyOrder.java`
- Modify: `backend/src/main/java/com/hms/entity/Prescription.java`

- [ ] **Step 1: Create LabTestMaster.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_test_master")
@Data @NoArgsConstructor @AllArgsConstructor
public class LabTestMaster {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "test_code", length = 50)
    private String testCode;

    @Column(name = "test_name", nullable = false, length = 200)
    private String testName;

    // BIOCHEMISTRY / HEMATOLOGY / MICROBIOLOGY / SEROLOGY / PATHOLOGY / OTHER
    @Column(length = 50, nullable = false)
    private String department = "OTHER";

    // BLOOD / URINE / STOOL / SWAB / CSF / OTHER
    @Column(name = "sample_type", length = 50, nullable = false)
    private String sampleType = "BLOOD";

    @Column(name = "normal_range_text", length = 500)
    private String normalRangeText;

    @Column(length = 50)
    private String unit;

    @Column(name = "turnaround_hours")
    private Integer turnaroundHours;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create RadiologyTestMaster.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "radiology_test_master")
@Data @NoArgsConstructor @AllArgsConstructor
public class RadiologyTestMaster {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "test_code", length = 50)
    private String testCode;

    @Column(name = "test_name", nullable = false, length = 200)
    private String testName;

    // X_RAY / CT / MRI / USG / ECHO / ECG / OTHER
    @Column(length = 50, nullable = false)
    private String modality = "OTHER";

    @Column(name = "preparation_instructions", columnDefinition = "text")
    private String preparationInstructions;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Create AllergyMaster.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "allergy_master")
@Data @NoArgsConstructor @AllArgsConstructor
public class AllergyMaster {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "allergy_name", nullable = false, length = 200)
    private String allergyName;

    // DRUG / FOOD / ENVIRONMENTAL / OTHER
    @Column(length = 50, nullable = false)
    private String category = "OTHER";

    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Create PatientAllergy.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_allergies")
@Data @NoArgsConstructor @AllArgsConstructor
public class PatientAllergy {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "allergy_master_id", nullable = false)
    private Long allergyMasterId;

    // MILD / MODERATE / SEVERE / UNKNOWN
    @Column(length = 20, nullable = false)
    private String severity = "UNKNOWN";

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false)
    private LocalDateTime recordedAt;
}
```

- [ ] **Step 5: Create DiagnosisMaster.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagnosis_master")
@Data @NoArgsConstructor @AllArgsConstructor
public class DiagnosisMaster {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "icd_code", nullable = false, length = 20)
    private String icdCode;

    @Column(name = "icd_description", nullable = false, length = 500)
    private String icdDescription;

    // INFECTIOUS / CARDIOVASCULAR / RESPIRATORY / ENDOCRINE / NEUROLOGICAL /
    // MUSCULOSKELETAL / GASTROINTESTINAL / GENITOURINARY / OBSTETRIC /
    // MENTAL / INJURY / NEOPLASM / OTHER
    @Column(length = 50, nullable = false)
    private String category = "OTHER";

    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: Create ProcedureMaster.java**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "procedure_master")
@Data @NoArgsConstructor @AllArgsConstructor
public class ProcedureMaster {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "procedure_code", length = 50)
    private String procedureCode;

    @Column(name = "procedure_name", nullable = false, length = 200)
    private String procedureName;

    @Column(length = 100)
    private String department;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 7: Update LabOrder.java — add labTestMasterId field**

In `backend/src/main/java/com/hms/entity/LabOrder.java`, add after the `testName` field (line 50):

```java
@Column(name = "lab_test_master_id")
private Long labTestMasterId;
```

- [ ] **Step 8: Update RadiologyOrder.java — add radiologyTestMasterId field**

In `backend/src/main/java/com/hms/entity/RadiologyOrder.java`, add after the `testName` field (line 50):

```java
@Column(name = "radiology_test_master_id")
private Long radiologyTestMasterId;
```

- [ ] **Step 9: Update Prescription.java — add medicineMasterId field**

In `backend/src/main/java/com/hms/entity/Prescription.java`, add after `medicineName` field (line 39):

```java
@Column(name = "medicine_master_id")
private Long medicineMasterId;
```

- [ ] **Step 10: Verify compile**

```bash
cd backend && mvn clean compile -q
```

Expected: BUILD SUCCESS with no errors.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/hms/entity/
git commit -m "feat(master-data): add 6 master data entities + FK fields on LabOrder, RadiologyOrder, Prescription"
```

---

## Task 3: Repositories

**Files:**
- Create: `backend/src/main/java/com/hms/repository/LabTestMasterRepository.java`
- Create: `backend/src/main/java/com/hms/repository/RadiologyTestMasterRepository.java`
- Create: `backend/src/main/java/com/hms/repository/AllergyMasterRepository.java`
- Create: `backend/src/main/java/com/hms/repository/PatientAllergyRepository.java`
- Create: `backend/src/main/java/com/hms/repository/DiagnosisMasterRepository.java`
- Create: `backend/src/main/java/com/hms/repository/ProcedureMasterRepository.java`

- [ ] **Step 1: Create LabTestMasterRepository.java**

```java
package com.hms.repository;

import com.hms.entity.LabTestMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface LabTestMasterRepository extends JpaRepository<LabTestMaster, Long> {
    List<LabTestMaster> findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(Long hospitalId);

    @Query("SELECT t FROM LabTestMaster t WHERE t.hospitalId = :hospitalId AND t.isActive = true " +
           "AND (LOWER(t.testName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.testCode) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<LabTestMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);

    boolean existsByHospitalIdAndIsActiveTrue(Long hospitalId);
}
```

- [ ] **Step 2: Create RadiologyTestMasterRepository.java**

```java
package com.hms.repository;

import com.hms.entity.RadiologyTestMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RadiologyTestMasterRepository extends JpaRepository<RadiologyTestMaster, Long> {
    List<RadiologyTestMaster> findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(Long hospitalId);

    @Query("SELECT t FROM RadiologyTestMaster t WHERE t.hospitalId = :hospitalId AND t.isActive = true " +
           "AND (LOWER(t.testName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.testCode) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<RadiologyTestMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);

    boolean existsByHospitalIdAndIsActiveTrue(Long hospitalId);
}
```

- [ ] **Step 3: Create AllergyMasterRepository.java**

```java
package com.hms.repository;

import com.hms.entity.AllergyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AllergyMasterRepository extends JpaRepository<AllergyMaster, Long> {
    List<AllergyMaster> findByHospitalIdAndIsActiveTrueOrderByAllergyNameAsc(Long hospitalId);

    @Query("SELECT a FROM AllergyMaster a WHERE a.hospitalId = :hospitalId AND a.isActive = true " +
           "AND LOWER(a.allergyName) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<AllergyMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);

    boolean existsByHospitalIdAndIsActiveTrue(Long hospitalId);
}
```

- [ ] **Step 4: Create PatientAllergyRepository.java**

```java
package com.hms.repository;

import com.hms.entity.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {
    List<PatientAllergy> findByPatientIdAndHospitalId(Long patientId, Long hospitalId);
    boolean existsByPatientIdAndAllergyMasterId(Long patientId, Long allergyMasterId);
}
```

- [ ] **Step 5: Create DiagnosisMasterRepository.java**

```java
package com.hms.repository;

import com.hms.entity.DiagnosisMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DiagnosisMasterRepository extends JpaRepository<DiagnosisMaster, Long> {
    List<DiagnosisMaster> findByHospitalIdAndIsActiveTrueOrderByIcdCodeAsc(Long hospitalId);

    @Query("SELECT d FROM DiagnosisMaster d WHERE d.hospitalId = :hospitalId AND d.isActive = true " +
           "AND (LOWER(d.icdCode) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(d.icdDescription) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<DiagnosisMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);

    boolean existsByHospitalIdAndIsActiveTrue(Long hospitalId);
}
```

- [ ] **Step 6: Create ProcedureMasterRepository.java**

```java
package com.hms.repository;

import com.hms.entity.ProcedureMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProcedureMasterRepository extends JpaRepository<ProcedureMaster, Long> {
    List<ProcedureMaster> findByHospitalIdAndIsActiveTrueOrderByProcedureNameAsc(Long hospitalId);

    @Query("SELECT p FROM ProcedureMaster p WHERE p.hospitalId = :hospitalId AND p.isActive = true " +
           "AND (LOWER(p.procedureName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(p.department) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<ProcedureMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);
}
```

- [ ] **Step 7: Compile check**

```bash
cd backend && mvn clean compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/hms/repository/
git commit -m "feat(master-data): add 6 master data repositories"
```

---

## Task 4: MasterDataService + Unit Tests

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/MasterDataService.java`
- Create: `backend/src/test/java/com/hms/service/MasterDataServiceTest.java`

- [ ] **Step 1: Write the failing test first**

```java
package com.hms.service;

import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.MasterDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterDataServiceTest {

    @Mock private LabTestMasterRepository labTestRepo;
    @Mock private RadiologyTestMasterRepository radiologyTestRepo;
    @Mock private AllergyMasterRepository allergyRepo;
    @Mock private DiagnosisMasterRepository diagnosisRepo;
    @Mock private ProcedureMasterRepository procedureRepo;
    @Mock private SecurityContextHelper securityHelper;

    @InjectMocks
    private MasterDataService masterDataService;

    @Test
    void searchLabTests_returnsMatchingResults() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabTestMaster cbc = new LabTestMaster();
        cbc.setId(1L); cbc.setTestName("CBC"); cbc.setHospitalId(hospitalId);
        when(labTestRepo.searchByHospital(hospitalId, "cbc")).thenReturn(List.of(cbc));

        List<LabTestMaster> result = masterDataService.searchLabTests("cbc");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTestName()).isEqualTo("CBC");
    }

    @Test
    void createLabTest_savesAndReturns() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabTestMaster input = new LabTestMaster();
        input.setTestName("LFT");
        input.setSampleType("BLOOD");
        input.setDepartment("BIOCHEMISTRY");
        when(labTestRepo.save(any())).thenAnswer(inv -> {
            LabTestMaster saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        LabTestMaster result = masterDataService.createLabTest(input);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getHospitalId()).isEqualTo(hospitalId);
        verify(labTestRepo).save(any(LabTestMaster.class));
    }

    @Test
    void deactivateLabTest_setsIsActiveFalse() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabTestMaster existing = new LabTestMaster();
        existing.setId(5L); existing.setHospitalId(hospitalId); existing.setIsActive(true);
        when(labTestRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(labTestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        masterDataService.deactivateLabTest(5L);

        assertThat(existing.getIsActive()).isFalse();
    }

    @Test
    void searchDiagnoses_returnsMatchingIcdResults() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        DiagnosisMaster dm = new DiagnosisMaster();
        dm.setIcdCode("I10"); dm.setIcdDescription("Hypertension"); dm.setHospitalId(hospitalId);
        when(diagnosisRepo.searchByHospital(hospitalId, "hypert")).thenReturn(List.of(dm));

        List<DiagnosisMaster> result = masterDataService.searchDiagnoses("hypert");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIcdCode()).isEqualTo("I10");
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (class not found)**

```bash
cd backend && mvn test -pl . -Dtest=MasterDataServiceTest -q 2>&1 | tail -5
```

Expected: compilation failure — `MasterDataService` does not exist yet.

- [ ] **Step 3: Implement MasterDataService.java**

```java
package com.hms.service.hospital;

import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MasterDataService {

    @Autowired private LabTestMasterRepository labTestRepo;
    @Autowired private RadiologyTestMasterRepository radiologyTestRepo;
    @Autowired private AllergyMasterRepository allergyRepo;
    @Autowired private DiagnosisMasterRepository diagnosisRepo;
    @Autowired private ProcedureMasterRepository procedureRepo;
    @Autowired private SecurityContextHelper securityHelper;

    // ─── Lab Tests ───────────────────────────────────────────────────────────

    public List<LabTestMaster> searchLabTests(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return labTestRepo.findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(hospitalId);
        return labTestRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public LabTestMaster createLabTest(LabTestMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsActive(true);
        return labTestRepo.save(input);
    }

    @Transactional
    public LabTestMaster updateLabTest(Long id, LabTestMaster input) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        LabTestMaster existing = labTestRepo.findById(id)
            .filter(t -> t.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Lab test not found"));
        existing.setTestCode(input.getTestCode());
        existing.setTestName(input.getTestName());
        existing.setDepartment(input.getDepartment());
        existing.setSampleType(input.getSampleType());
        existing.setNormalRangeText(input.getNormalRangeText());
        existing.setUnit(input.getUnit());
        existing.setTurnaroundHours(input.getTurnaroundHours());
        existing.setPrice(input.getPrice());
        return labTestRepo.save(existing);
    }

    @Transactional
    public void deactivateLabTest(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        LabTestMaster existing = labTestRepo.findById(id)
            .filter(t -> t.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Lab test not found"));
        existing.setIsActive(false);
        labTestRepo.save(existing);
    }

    // ─── Radiology Tests ─────────────────────────────────────────────────────

    public List<RadiologyTestMaster> searchRadiologyTests(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return radiologyTestRepo.findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(hospitalId);
        return radiologyTestRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public RadiologyTestMaster createRadiologyTest(RadiologyTestMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsActive(true);
        return radiologyTestRepo.save(input);
    }

    @Transactional
    public RadiologyTestMaster updateRadiologyTest(Long id, RadiologyTestMaster input) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        RadiologyTestMaster existing = radiologyTestRepo.findById(id)
            .filter(t -> t.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Radiology test not found"));
        existing.setTestCode(input.getTestCode());
        existing.setTestName(input.getTestName());
        existing.setModality(input.getModality());
        existing.setPreparationInstructions(input.getPreparationInstructions());
        existing.setEstimatedDurationMinutes(input.getEstimatedDurationMinutes());
        existing.setPrice(input.getPrice());
        return radiologyTestRepo.save(existing);
    }

    @Transactional
    public void deactivateRadiologyTest(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        RadiologyTestMaster existing = radiologyTestRepo.findById(id)
            .filter(t -> t.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Radiology test not found"));
        existing.setIsActive(false);
        radiologyTestRepo.save(existing);
    }

    // ─── Allergies ───────────────────────────────────────────────────────────

    public List<AllergyMaster> searchAllergies(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return allergyRepo.findByHospitalIdAndIsActiveTrueOrderByAllergyNameAsc(hospitalId);
        return allergyRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public AllergyMaster createAllergy(AllergyMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsCustom(true);
        input.setIsActive(true);
        return allergyRepo.save(input);
    }

    @Transactional
    public void deactivateAllergy(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        AllergyMaster existing = allergyRepo.findById(id)
            .filter(a -> a.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Allergy not found"));
        existing.setIsActive(false);
        allergyRepo.save(existing);
    }

    // ─── Diagnoses ───────────────────────────────────────────────────────────

    public List<DiagnosisMaster> searchDiagnoses(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return diagnosisRepo.findByHospitalIdAndIsActiveTrueOrderByIcdCodeAsc(hospitalId);
        return diagnosisRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public DiagnosisMaster createDiagnosis(DiagnosisMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsCustom(true);
        input.setIsActive(true);
        return diagnosisRepo.save(input);
    }

    @Transactional
    public void deactivateDiagnosis(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        DiagnosisMaster existing = diagnosisRepo.findById(id)
            .filter(d -> d.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Diagnosis not found"));
        existing.setIsActive(false);
        diagnosisRepo.save(existing);
    }

    // ─── Procedures ──────────────────────────────────────────────────────────

    public List<ProcedureMaster> searchProcedures(String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) return procedureRepo.findByHospitalIdAndIsActiveTrueOrderByProcedureNameAsc(hospitalId);
        return procedureRepo.searchByHospital(hospitalId, q.trim());
    }

    @Transactional
    public ProcedureMaster createProcedure(ProcedureMaster input) {
        input.setId(null);
        input.setHospitalId(securityHelper.getCurrentHospitalId());
        input.setIsActive(true);
        return procedureRepo.save(input);
    }

    @Transactional
    public ProcedureMaster updateProcedure(Long id, ProcedureMaster input) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ProcedureMaster existing = procedureRepo.findById(id)
            .filter(p -> p.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Procedure not found"));
        existing.setProcedureCode(input.getProcedureCode());
        existing.setProcedureName(input.getProcedureName());
        existing.setDepartment(input.getDepartment());
        existing.setEstimatedDurationMinutes(input.getEstimatedDurationMinutes());
        existing.setPrice(input.getPrice());
        return procedureRepo.save(existing);
    }

    @Transactional
    public void deactivateProcedure(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ProcedureMaster existing = procedureRepo.findById(id)
            .filter(p -> p.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Procedure not found"));
        existing.setIsActive(false);
        procedureRepo.save(existing);
    }

    // ─── Seed defaults for a hospital ────────────────────────────────────────

    @Transactional
    public void seedDefaultsForHospital(Long hospitalId) {
        if (!allergyRepo.existsByHospitalIdAndIsActiveTrue(hospitalId)) {
            String[][] allergies = {
                {"Penicillin","DRUG"},{"Amoxicillin","DRUG"},{"Ampicillin","DRUG"},
                {"Sulfa drugs","DRUG"},{"Aspirin","DRUG"},{"NSAIDs (Ibuprofen)","DRUG"},
                {"Codeine","DRUG"},{"Morphine","DRUG"},{"Tetracycline","DRUG"},
                {"Erythromycin","DRUG"},{"Ciprofloxacin","DRUG"},{"Metronidazole","DRUG"},
                {"Contrast dye (Iodine)","DRUG"},{"Insulin","DRUG"},
                {"Peanuts","FOOD"},{"Tree nuts","FOOD"},{"Milk / Dairy","FOOD"},
                {"Eggs","FOOD"},{"Wheat / Gluten","FOOD"},{"Shellfish","FOOD"},
                {"Soy","FOOD"},{"Sesame","FOOD"},{"Fish","FOOD"},
                {"Latex","ENVIRONMENTAL"},{"Dust mites","ENVIRONMENTAL"},
                {"Pollen","ENVIRONMENTAL"},{"Mold","ENVIRONMENTAL"},
                {"Pet dander (cats)","ENVIRONMENTAL"},{"Pet dander (dogs)","ENVIRONMENTAL"},
                {"Nickel","ENVIRONMENTAL"},{"Bee venom","OTHER"},{"Wasp venom","OTHER"}
            };
            for (String[] a : allergies) {
                AllergyMaster am = new AllergyMaster();
                am.setHospitalId(hospitalId); am.setAllergyName(a[0]);
                am.setCategory(a[1]); am.setIsCustom(false); am.setIsActive(true);
                allergyRepo.save(am);
            }
        }
        if (!diagnosisRepo.existsByHospitalIdAndIsActiveTrue(hospitalId)) {
            String[][] diagnoses = {
                // Cardiovascular
                {"I10","Essential Hypertension","CARDIOVASCULAR"},
                {"I11","Hypertensive Heart Disease","CARDIOVASCULAR"},
                {"I20","Angina Pectoris","CARDIOVASCULAR"},
                {"I21","Acute Myocardial Infarction","CARDIOVASCULAR"},
                {"I25","Chronic Ischaemic Heart Disease","CARDIOVASCULAR"},
                {"I48","Atrial Fibrillation","CARDIOVASCULAR"},
                {"I50","Heart Failure","CARDIOVASCULAR"},
                {"I63","Cerebral Infarction (Ischaemic Stroke)","CARDIOVASCULAR"},
                {"I64","Stroke NOS","CARDIOVASCULAR"},
                {"I70","Atherosclerosis","CARDIOVASCULAR"},
                // Respiratory
                {"J00","Acute Nasopharyngitis (Common Cold)","RESPIRATORY"},
                {"J02","Acute Pharyngitis","RESPIRATORY"},
                {"J03","Acute Tonsillitis","RESPIRATORY"},
                {"J06","Acute Upper Respiratory Infection","RESPIRATORY"},
                {"J18","Pneumonia","RESPIRATORY"},
                {"J20","Acute Bronchitis","RESPIRATORY"},
                {"J44","COPD","RESPIRATORY"},
                {"J45","Asthma","RESPIRATORY"},
                {"J46","Status Asthmaticus","RESPIRATORY"},
                // Endocrine
                {"E11","Type 2 Diabetes Mellitus","ENDOCRINE"},
                {"E10","Type 1 Diabetes Mellitus","ENDOCRINE"},
                {"E14","Unspecified Diabetes Mellitus","ENDOCRINE"},
                {"E03","Hypothyroidism","ENDOCRINE"},
                {"E05","Hyperthyroidism","ENDOCRINE"},
                {"E66","Obesity","ENDOCRINE"},
                // Infectious
                {"A01","Typhoid Fever","INFECTIOUS"},
                {"A09","Diarrhoea and Gastroenteritis","INFECTIOUS"},
                {"A15","Respiratory Tuberculosis","INFECTIOUS"},
                {"A90","Dengue Fever","INFECTIOUS"},
                {"A91","Dengue Haemorrhagic Fever","INFECTIOUS"},
                {"B15","Acute Hepatitis A","INFECTIOUS"},
                {"B16","Acute Hepatitis B","INFECTIOUS"},
                {"B50","Plasmodium Falciparum Malaria","INFECTIOUS"},
                {"B54","Unspecified Malaria","INFECTIOUS"},
                {"A80","Acute Poliomyelitis","INFECTIOUS"},
                {"B34","Viral Infection NOS","INFECTIOUS"},
                {"A49","Bacterial Infection NOS","INFECTIOUS"},
                // Gastrointestinal
                {"K21","Gastro-oesophageal Reflux Disease","GASTROINTESTINAL"},
                {"K25","Gastric Ulcer","GASTROINTESTINAL"},
                {"K26","Duodenal Ulcer","GASTROINTESTINAL"},
                {"K29","Gastritis","GASTROINTESTINAL"},
                {"K37","Appendicitis","GASTROINTESTINAL"},
                {"K57","Diverticular Disease","GASTROINTESTINAL"},
                {"K72","Hepatic Failure","GASTROINTESTINAL"},
                {"K80","Cholelithiasis (Gallstones)","GASTROINTESTINAL"},
                {"K85","Acute Pancreatitis","GASTROINTESTINAL"},
                {"K92","Gastrointestinal Haemorrhage","GASTROINTESTINAL"},
                // Genitourinary
                {"N18","Chronic Kidney Disease","GENITOURINARY"},
                {"N20","Kidney Stone","GENITOURINARY"},
                {"N39","Urinary Tract Infection","GENITOURINARY"},
                {"N40","Benign Prostatic Hyperplasia","GENITOURINARY"},
                // Neurological
                {"G40","Epilepsy","NEUROLOGICAL"},
                {"G43","Migraine","NEUROLOGICAL"},
                {"G45","Transient Ischaemic Attack","NEUROLOGICAL"},
                {"G35","Multiple Sclerosis","NEUROLOGICAL"},
                {"G20","Parkinson's Disease","NEUROLOGICAL"},
                {"G30","Alzheimer's Disease","NEUROLOGICAL"},
                // Musculoskeletal
                {"M05","Rheumatoid Arthritis","MUSCULOSKELETAL"},
                {"M10","Gout","MUSCULOSKELETAL"},
                {"M15","Osteoarthritis","MUSCULOSKELETAL"},
                {"M54","Back Pain","MUSCULOSKELETAL"},
                {"M79","Fibromyalgia","MUSCULOSKELETAL"},
                // Obstetric
                {"O10","Pre-existing Hypertension in Pregnancy","OBSTETRIC"},
                {"O14","Gestational Hypertension","OBSTETRIC"},
                {"O24","Gestational Diabetes","OBSTETRIC"},
                {"O60","Preterm Labour","OBSTETRIC"},
                {"O80","Normal Delivery","OBSTETRIC"},
                // Mental
                {"F32","Depressive Episode","MENTAL"},
                {"F41","Anxiety Disorder","MENTAL"},
                {"F20","Schizophrenia","MENTAL"},
                {"F10","Alcohol Use Disorder","MENTAL"},
                // Injury
                {"S00","Head Injury","INJURY"},
                {"S52","Fracture of Forearm","INJURY"},
                {"S72","Fracture of Femur","INJURY"},
                {"T14","Injury NOS","INJURY"},
                // Neoplasm
                {"C34","Lung Cancer","NEOPLASM"},
                {"C50","Breast Cancer","NEOPLASM"},
                {"C18","Colon Cancer","NEOPLASM"},
                {"C61","Prostate Cancer","NEOPLASM"},
                {"C67","Bladder Cancer","NEOPLASM"},
                // Blood
                {"D50","Iron Deficiency Anaemia","OTHER"},
                {"D64","Other Anaemia","OTHER"},
                {"D69","Thrombocytopenia","OTHER"},
                {"L30","Eczema / Dermatitis","OTHER"},
                {"H10","Conjunctivitis","OTHER"},
                {"H52","Refractive Error","OTHER"}
            };
            for (String[] d : diagnoses) {
                DiagnosisMaster dm = new DiagnosisMaster();
                dm.setHospitalId(hospitalId); dm.setIcdCode(d[0]);
                dm.setIcdDescription(d[1]); dm.setCategory(d[2]);
                dm.setIsCustom(false); dm.setIsActive(true);
                diagnosisRepo.save(dm);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd backend && mvn test -Dtest=MasterDataServiceTest -q
```

Expected: Tests PASS (4/4).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/MasterDataService.java
git add backend/src/test/java/com/hms/service/MasterDataServiceTest.java
git commit -m "feat(master-data): implement MasterDataService with CRUD + seed logic, tests passing"
```

---

## Task 5: PatientAllergyService + Unit Tests

**Files:**
- Create: `backend/src/main/java/com/hms/service/hospital/PatientAllergyService.java`
- Create: `backend/src/test/java/com/hms/service/PatientAllergyServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service;

import com.hms.entity.AllergyMaster;
import com.hms.entity.PatientAllergy;
import com.hms.repository.AllergyMasterRepository;
import com.hms.repository.PatientAllergyRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.PatientAllergyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientAllergyServiceTest {

    @Mock private PatientAllergyRepository patientAllergyRepo;
    @Mock private AllergyMasterRepository allergyMasterRepo;
    @Mock private SecurityContextHelper securityHelper;

    @InjectMocks
    private PatientAllergyService patientAllergyService;

    @Test
    void addAllergy_savesSuccessfully() {
        Long hospitalId = 1L, patientId = 10L, allergyMasterId = 5L, userId = 99L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserId()).thenReturn(userId);
        AllergyMaster master = new AllergyMaster();
        master.setId(allergyMasterId); master.setHospitalId(hospitalId);
        when(allergyMasterRepo.findById(allergyMasterId)).thenReturn(Optional.of(master));
        when(patientAllergyRepo.existsByPatientIdAndAllergyMasterId(patientId, allergyMasterId)).thenReturn(false);
        when(patientAllergyRepo.save(any())).thenAnswer(inv -> {
            PatientAllergy saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        PatientAllergy result = patientAllergyService.addAllergy(patientId, allergyMasterId, "SEVERE", "Anaphylaxis history");

        assertThat(result.getPatientId()).isEqualTo(patientId);
        assertThat(result.getSeverity()).isEqualTo("SEVERE");
        assertThat(result.getRecordedByUserId()).isEqualTo(userId);
    }

    @Test
    void addAllergy_throwsIfDuplicate() {
        Long hospitalId = 1L, patientId = 10L, allergyMasterId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        AllergyMaster master = new AllergyMaster();
        master.setId(allergyMasterId); master.setHospitalId(hospitalId);
        when(allergyMasterRepo.findById(allergyMasterId)).thenReturn(Optional.of(master));
        when(patientAllergyRepo.existsByPatientIdAndAllergyMasterId(patientId, allergyMasterId)).thenReturn(true);

        assertThatThrownBy(() -> patientAllergyService.addAllergy(patientId, allergyMasterId, "MILD", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already recorded");
    }

    @Test
    void getPatientAllergies_returnsAll() {
        Long hospitalId = 1L, patientId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        PatientAllergy pa = new PatientAllergy();
        pa.setPatientId(patientId); pa.setSeverity("MODERATE");
        when(patientAllergyRepo.findByPatientIdAndHospitalId(patientId, hospitalId)).thenReturn(List.of(pa));

        List<PatientAllergy> result = patientAllergyService.getPatientAllergies(patientId);
        assertThat(result).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
cd backend && mvn test -Dtest=PatientAllergyServiceTest -q 2>&1 | tail -5
```

Expected: compilation failure.

- [ ] **Step 3: Implement PatientAllergyService.java**

```java
package com.hms.service.hospital;

import com.hms.entity.AllergyMaster;
import com.hms.entity.PatientAllergy;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.AllergyMasterRepository;
import com.hms.repository.PatientAllergyRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientAllergyService {

    @Autowired private PatientAllergyRepository patientAllergyRepo;
    @Autowired private AllergyMasterRepository allergyMasterRepo;
    @Autowired private SecurityContextHelper securityHelper;

    public List<PatientAllergy> getPatientAllergies(Long patientId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return patientAllergyRepo.findByPatientIdAndHospitalId(patientId, hospitalId);
    }

    @Transactional
    public PatientAllergy addAllergy(Long patientId, Long allergyMasterId, String severity, String notes) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long userId = securityHelper.getCurrentUserId();

        AllergyMaster master = allergyMasterRepo.findById(allergyMasterId)
            .filter(a -> a.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Allergy master not found"));

        if (patientAllergyRepo.existsByPatientIdAndAllergyMasterId(patientId, allergyMasterId)) {
            throw new IllegalStateException("Allergy already recorded for this patient");
        }

        PatientAllergy pa = new PatientAllergy();
        pa.setHospitalId(hospitalId);
        pa.setPatientId(patientId);
        pa.setAllergyMasterId(allergyMasterId);
        pa.setSeverity(severity != null ? severity : "UNKNOWN");
        pa.setNotes(notes);
        pa.setRecordedByUserId(userId);
        return patientAllergyRepo.save(pa);
    }

    @Transactional
    public void removeAllergy(Long patientId, Long allergyId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PatientAllergy pa = patientAllergyRepo.findById(allergyId)
            .filter(a -> a.getPatientId().equals(patientId) && a.getHospitalId().equals(hospitalId))
            .orElseThrow(() -> new ResourceNotFoundException("Patient allergy not found"));
        patientAllergyRepo.delete(pa);
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd backend && mvn test -Dtest=PatientAllergyServiceTest -q
```

Expected: 3/3 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/service/hospital/PatientAllergyService.java
git add backend/src/test/java/com/hms/service/PatientAllergyServiceTest.java
git commit -m "feat(master-data): implement PatientAllergyService with duplicate guard, tests passing"
```

---

## Task 6: REST Controllers

**Files:**
- Create: `backend/src/main/java/com/hms/controller/hospital/MasterDataController.java`
- Create: `backend/src/main/java/com/hms/controller/hospital/PatientAllergyController.java`

- [ ] **Step 1: Create MasterDataController.java**

```java
package com.hms.controller.hospital;

import com.hms.entity.*;
import com.hms.service.hospital.MasterDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hospital/master")
public class MasterDataController {

    @Autowired private MasterDataService masterDataService;

    // ─── Lab Tests ───────────────────────────────────────────────────────────

    @GetMapping("/lab-tests/search")
    public ResponseEntity<List<LabTestMaster>> searchLabTests(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchLabTests(q));
    }

    @PostMapping("/lab-tests")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<LabTestMaster> createLabTest(@RequestBody LabTestMaster input) {
        return ResponseEntity.ok(masterDataService.createLabTest(input));
    }

    @PutMapping("/lab-tests/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<LabTestMaster> updateLabTest(@PathVariable Long id, @RequestBody LabTestMaster input) {
        return ResponseEntity.ok(masterDataService.updateLabTest(id, input));
    }

    @DeleteMapping("/lab-tests/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateLabTest(@PathVariable Long id) {
        masterDataService.deactivateLabTest(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Radiology Tests ─────────────────────────────────────────────────────

    @GetMapping("/radiology-tests/search")
    public ResponseEntity<List<RadiologyTestMaster>> searchRadiologyTests(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchRadiologyTests(q));
    }

    @PostMapping("/radiology-tests")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<RadiologyTestMaster> createRadiologyTest(@RequestBody RadiologyTestMaster input) {
        return ResponseEntity.ok(masterDataService.createRadiologyTest(input));
    }

    @PutMapping("/radiology-tests/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<RadiologyTestMaster> updateRadiologyTest(@PathVariable Long id, @RequestBody RadiologyTestMaster input) {
        return ResponseEntity.ok(masterDataService.updateRadiologyTest(id, input));
    }

    @DeleteMapping("/radiology-tests/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateRadiologyTest(@PathVariable Long id) {
        masterDataService.deactivateRadiologyTest(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Allergies ───────────────────────────────────────────────────────────

    @GetMapping("/allergies/search")
    public ResponseEntity<List<AllergyMaster>> searchAllergies(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchAllergies(q));
    }

    @PostMapping("/allergies")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<AllergyMaster> createAllergy(@RequestBody AllergyMaster input) {
        return ResponseEntity.ok(masterDataService.createAllergy(input));
    }

    @DeleteMapping("/allergies/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateAllergy(@PathVariable Long id) {
        masterDataService.deactivateAllergy(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Diagnoses ───────────────────────────────────────────────────────────

    @GetMapping("/diagnoses/search")
    public ResponseEntity<List<DiagnosisMaster>> searchDiagnoses(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchDiagnoses(q));
    }

    @PostMapping("/diagnoses")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<DiagnosisMaster> createDiagnosis(@RequestBody DiagnosisMaster input) {
        return ResponseEntity.ok(masterDataService.createDiagnosis(input));
    }

    @DeleteMapping("/diagnoses/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateDiagnosis(@PathVariable Long id) {
        masterDataService.deactivateDiagnosis(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Procedures ──────────────────────────────────────────────────────────

    @GetMapping("/procedures/search")
    public ResponseEntity<List<ProcedureMaster>> searchProcedures(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchProcedures(q));
    }

    @PostMapping("/procedures")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ProcedureMaster> createProcedure(@RequestBody ProcedureMaster input) {
        return ResponseEntity.ok(masterDataService.createProcedure(input));
    }

    @PutMapping("/procedures/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ProcedureMaster> updateProcedure(@PathVariable Long id, @RequestBody ProcedureMaster input) {
        return ResponseEntity.ok(masterDataService.updateProcedure(id, input));
    }

    @DeleteMapping("/procedures/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateProcedure(@PathVariable Long id) {
        masterDataService.deactivateProcedure(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Seed ────────────────────────────────────────────────────────────────

    @PostMapping("/seed")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<String> seedDefaults() {
        Long hospitalId = new com.hms.security.SecurityContextHelper().getCurrentHospitalId();
        masterDataService.seedDefaultsForHospital(hospitalId);
        return ResponseEntity.ok("Seed complete");
    }
}
```

- [ ] **Step 2: Create PatientAllergyController.java**

```java
package com.hms.controller.hospital;

import com.hms.entity.PatientAllergy;
import com.hms.service.hospital.PatientAllergyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hospital/patients/{patientId}/allergies")
public class PatientAllergyController {

    @Autowired private PatientAllergyService patientAllergyService;

    @GetMapping
    public ResponseEntity<List<PatientAllergy>> getPatientAllergies(@PathVariable Long patientId) {
        return ResponseEntity.ok(patientAllergyService.getPatientAllergies(patientId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> addAllergy(@PathVariable Long patientId, @RequestBody Map<String, Object> body) {
        try {
            Long allergyMasterId = Long.valueOf(body.get("allergyMasterId").toString());
            String severity = body.getOrDefault("severity", "UNKNOWN").toString();
            String notes = body.containsKey("notes") ? body.get("notes").toString() : null;
            PatientAllergy result = patientAllergyService.addAllergy(patientId, allergyMasterId, severity, notes);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{allergyId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<Void> removeAllergy(@PathVariable Long patientId, @PathVariable Long allergyId) {
        patientAllergyService.removeAllergy(patientId, allergyId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: Fix seed endpoint — inject SecurityContextHelper properly**

The `/seed` endpoint in `MasterDataController` has an incorrect instantiation. Replace that seed method with:

```java
@Autowired
private com.hms.security.SecurityContextHelper securityContextHelper;

@PostMapping("/seed")
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public ResponseEntity<String> seedDefaults() {
    masterDataService.seedDefaultsForHospital(securityContextHelper.getCurrentHospitalId());
    return ResponseEntity.ok("Seed complete");
}
```

- [ ] **Step 4: Compile and test**

```bash
cd backend && mvn clean compile -q && mvn test -q
```

Expected: BUILD SUCCESS, all existing tests still PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/controller/hospital/MasterDataController.java
git add backend/src/main/java/com/hms/controller/hospital/PatientAllergyController.java
git commit -m "feat(master-data): add MasterDataController and PatientAllergyController REST endpoints"
```

---

## Task 7: Frontend — masterDataService.js

**Files:**
- Create: `frontend/src/services/masterDataService.js`
- Modify: `frontend/src/services/hospitalService.js`

- [ ] **Step 1: Create masterDataService.js**

```javascript
import apiClient from './apiService';

const masterDataService = {
  // Lab Tests
  searchLabTests: (q = '') =>
    apiClient.get(`/hospital/master/lab-tests/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createLabTest: (data) => apiClient.post('/hospital/master/lab-tests', data).then(r => r.data),
  updateLabTest: (id, data) => apiClient.put(`/hospital/master/lab-tests/${id}`, data).then(r => r.data),
  deactivateLabTest: (id) => apiClient.delete(`/hospital/master/lab-tests/${id}`),

  // Radiology Tests
  searchRadiologyTests: (q = '') =>
    apiClient.get(`/hospital/master/radiology-tests/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createRadiologyTest: (data) => apiClient.post('/hospital/master/radiology-tests', data).then(r => r.data),
  updateRadiologyTest: (id, data) => apiClient.put(`/hospital/master/radiology-tests/${id}`, data).then(r => r.data),
  deactivateRadiologyTest: (id) => apiClient.delete(`/hospital/master/radiology-tests/${id}`),

  // Allergies
  searchAllergies: (q = '') =>
    apiClient.get(`/hospital/master/allergies/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createAllergy: (data) => apiClient.post('/hospital/master/allergies', data).then(r => r.data),
  deactivateAllergy: (id) => apiClient.delete(`/hospital/master/allergies/${id}`),

  // Diagnoses
  searchDiagnoses: (q = '') =>
    apiClient.get(`/hospital/master/diagnoses/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createDiagnosis: (data) => apiClient.post('/hospital/master/diagnoses', data).then(r => r.data),
  deactivateDiagnosis: (id) => apiClient.delete(`/hospital/master/diagnoses/${id}`),

  // Procedures
  searchProcedures: (q = '') =>
    apiClient.get(`/hospital/master/procedures/search${q ? `?q=${encodeURIComponent(q)}` : ''}`).then(r => r.data),
  createProcedure: (data) => apiClient.post('/hospital/master/procedures', data).then(r => r.data),
  updateProcedure: (id, data) => apiClient.put(`/hospital/master/procedures/${id}`, data).then(r => r.data),
  deactivateProcedure: (id) => apiClient.delete(`/hospital/master/procedures/${id}`),

  // Seed defaults for this hospital
  seedDefaults: () => apiClient.post('/hospital/master/seed').then(r => r.data),
};

export default masterDataService;
```

- [ ] **Step 2: Add patient allergy methods to hospitalService.js**

In `frontend/src/services/hospitalService.js`, add these methods to the exported object:

```javascript
// Patient Allergies
getPatientAllergies: async (patientId) => {
  const response = await apiClient.get(`/hospital/patients/${patientId}/allergies`);
  return response.data;
},
addPatientAllergy: async (patientId, payload) => {
  const response = await apiClient.post(`/hospital/patients/${patientId}/allergies`, payload);
  return response.data;
},
removePatientAllergy: async (patientId, allergyId) => {
  await apiClient.delete(`/hospital/patients/${patientId}/allergies/${allergyId}`);
},
```

- [ ] **Step 3: Build check**

```bash
cd frontend && npm run build 2>&1 | tail -10
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/services/masterDataService.js frontend/src/services/hospitalService.js
git commit -m "feat(master-data): add masterDataService.js and patient allergy API methods"
```

---

## Task 8: SearchableSelect Component

**Files:**
- Create: `frontend/src/components/SearchableSelect.jsx`

- [ ] **Step 1: Create SearchableSelect.jsx**

This is a reusable autocomplete dropdown used in prescription, lab order, and radiology order forms.

```jsx
import React, { useState, useEffect, useRef } from 'react';

/**
 * SearchableSelect — async search autocomplete dropdown.
 * Props:
 *   onSearch: async (query: string) => Item[]  — fetch matching items
 *   onSelect: (item: Item) => void             — called when user picks an item
 *   getLabel: (item: Item) => string           — how to display each item
 *   placeholder: string
 *   value: string                              — current display value (controlled)
 *   disabled: boolean
 *   hint: string                               — shown below input
 */
export default function SearchableSelect({
  onSearch, onSelect, getLabel,
  placeholder = 'Search...', value = '', disabled = false, hint = ''
}) {
  const [query, setQuery] = useState(value);
  const [results, setResults] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef(null);
  const containerRef = useRef(null);

  useEffect(() => { setQuery(value); }, [value]);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleChange = (e) => {
    const q = e.target.value;
    setQuery(q);
    setOpen(true);
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const items = await onSearch(q);
        setResults(items || []);
      } catch { setResults([]); }
      setLoading(false);
    }, 300);
  };

  const handleSelect = (item) => {
    setQuery(getLabel(item));
    setOpen(false);
    setResults([]);
    onSelect(item);
  };

  return (
    <div ref={containerRef} className="relative">
      <input
        type="text"
        value={query}
        onChange={handleChange}
        onFocus={() => { if (query.length === 0) handleChange({ target: { value: '' } }); }}
        placeholder={placeholder}
        disabled={disabled}
        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
      />
      {hint && <p className="text-xs text-gray-400 mt-0.5">{hint}</p>}
      {open && (
        <div className="absolute z-50 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg max-h-56 overflow-auto">
          {loading && <div className="px-4 py-3 text-sm text-gray-400">Searching...</div>}
          {!loading && results.length === 0 && (
            <div className="px-4 py-3 text-sm text-gray-400">No results</div>
          )}
          {!loading && results.map((item, i) => (
            <button
              key={i}
              type="button"
              onMouseDown={() => handleSelect(item)}
              className="w-full text-left px-4 py-2 text-sm hover:bg-blue-50 hover:text-blue-700"
            >
              {getLabel(item)}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/SearchableSelect.jsx
git commit -m "feat(master-data): add reusable SearchableSelect autocomplete component"
```

---

## Task 9: MasterDataView — Admin Masters Tab

**Files:**
- Create: `frontend/src/pages/hospital/MasterDataView.jsx`

- [ ] **Step 1: Create MasterDataView.jsx**

```jsx
import React, { useState, useEffect, useCallback } from 'react';
import masterDataService from '../../services/masterDataService';
import { useToast } from '../../context/ToastContext';

const TABS = [
  { id: 'lab', label: 'Lab Tests' },
  { id: 'radiology', label: 'Radiology Tests' },
  { id: 'allergies', label: 'Allergies' },
  { id: 'diagnoses', label: 'Diagnoses (ICD)' },
  { id: 'procedures', label: 'Procedures' },
];

export default function MasterDataView() {
  const [activeTab, setActiveTab] = useState('lab');
  const [items, setItems] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editItem, setEditItem] = useState(null);
  const [seeded, setSeeded] = useState(false);
  const { toastSuccess, toastError } = useToast();

  const fetchItems = useCallback(async (q = '') => {
    setLoading(true);
    try {
      let data = [];
      if (activeTab === 'lab') data = await masterDataService.searchLabTests(q);
      else if (activeTab === 'radiology') data = await masterDataService.searchRadiologyTests(q);
      else if (activeTab === 'allergies') data = await masterDataService.searchAllergies(q);
      else if (activeTab === 'diagnoses') data = await masterDataService.searchDiagnoses(q);
      else if (activeTab === 'procedures') data = await masterDataService.searchProcedures(q);
      setItems(data);
    } catch { toastError('Failed to load master data'); }
    setLoading(false);
  }, [activeTab]);

  useEffect(() => { setSearch(''); fetchItems(''); }, [activeTab]);

  useEffect(() => {
    const t = setTimeout(() => fetchItems(search), 300);
    return () => clearTimeout(t);
  }, [search, fetchItems]);

  const handleSeed = async () => {
    try {
      await masterDataService.seedDefaults();
      toastSuccess('Default allergies and diagnoses seeded successfully');
      setSeeded(true);
      fetchItems('');
    } catch { toastError('Seed failed'); }
  };

  const handleDeactivate = async (id) => {
    if (!window.confirm('Deactivate this entry?')) return;
    try {
      if (activeTab === 'lab') await masterDataService.deactivateLabTest(id);
      else if (activeTab === 'radiology') await masterDataService.deactivateRadiologyTest(id);
      else if (activeTab === 'allergies') await masterDataService.deactivateAllergy(id);
      else if (activeTab === 'diagnoses') await masterDataService.deactivateDiagnosis(id);
      else if (activeTab === 'procedures') await masterDataService.deactivateProcedure(id);
      toastSuccess('Deactivated');
      fetchItems(search);
    } catch { toastError('Failed to deactivate'); }
  };

  const handleSave = async (formData) => {
    try {
      if (editItem) {
        if (activeTab === 'lab') await masterDataService.updateLabTest(editItem.id, formData);
        else if (activeTab === 'radiology') await masterDataService.updateRadiologyTest(editItem.id, formData);
        else if (activeTab === 'procedures') await masterDataService.updateProcedure(editItem.id, formData);
      } else {
        if (activeTab === 'lab') await masterDataService.createLabTest(formData);
        else if (activeTab === 'radiology') await masterDataService.createRadiologyTest(formData);
        else if (activeTab === 'allergies') await masterDataService.createAllergy(formData);
        else if (activeTab === 'diagnoses') await masterDataService.createDiagnosis(formData);
        else if (activeTab === 'procedures') await masterDataService.createProcedure(formData);
      }
      toastSuccess(editItem ? 'Updated' : 'Created');
      setShowModal(false);
      setEditItem(null);
      fetchItems(search);
    } catch { toastError('Save failed'); }
  };

  const columns = getColumns(activeTab);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex gap-2 flex-wrap">
          {TABS.map(t => (
            <button key={t.id} onClick={() => setActiveTab(t.id)}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition ${activeTab === t.id ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
              {t.label}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          {(activeTab === 'allergies' || activeTab === 'diagnoses') && !seeded && (
            <button onClick={handleSeed}
              className="px-3 py-2 text-sm rounded-lg bg-amber-100 text-amber-700 hover:bg-amber-200 font-medium">
              Seed Defaults
            </button>
          )}
          <button onClick={() => { setEditItem(null); setShowModal(true); }}
            className="px-4 py-2 text-sm rounded-lg bg-blue-600 text-white hover:bg-blue-700 font-medium">
            + Add New
          </button>
        </div>
      </div>

      <input
        type="text" value={search} onChange={e => setSearch(e.target.value)}
        placeholder={`Search ${TABS.find(t => t.id === activeTab)?.label}...`}
        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />

      {loading ? (
        <div className="text-center py-12 text-gray-400">Loading...</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                {columns.map(col => (
                  <th key={col.key} className="px-4 py-3 text-left font-medium text-gray-500">{col.label}</th>
                ))}
                <th className="px-4 py-3 text-left font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {items.length === 0 && (
                <tr><td colSpan={columns.length + 1} className="px-4 py-8 text-center text-gray-400">No entries yet. Click "Add New" or "Seed Defaults".</td></tr>
              )}
              {items.map(item => (
                <tr key={item.id} className="hover:bg-gray-50">
                  {columns.map(col => (
                    <td key={col.key} className="px-4 py-3 text-gray-700">{col.render ? col.render(item) : item[col.key] ?? '-'}</td>
                  ))}
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      {(activeTab === 'lab' || activeTab === 'radiology' || activeTab === 'procedures') && (
                        <button onClick={() => { setEditItem(item); setShowModal(true); }}
                          className="text-blue-600 hover:underline text-xs">Edit</button>
                      )}
                      <button onClick={() => handleDeactivate(item.id)}
                        className="text-red-500 hover:underline text-xs">Deactivate</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showModal && (
        <MasterDataModal
          tab={activeTab}
          editItem={editItem}
          onSave={handleSave}
          onClose={() => { setShowModal(false); setEditItem(null); }}
        />
      )}
    </div>
  );
}

function getColumns(tab) {
  if (tab === 'lab') return [
    { key: 'testCode', label: 'Code' },
    { key: 'testName', label: 'Test Name' },
    { key: 'department', label: 'Department' },
    { key: 'sampleType', label: 'Sample' },
    { key: 'normalRangeText', label: 'Normal Range' },
    { key: 'unit', label: 'Unit' },
    { key: 'turnaroundHours', label: 'TAT (hrs)' },
    { key: 'price', label: 'Price' },
  ];
  if (tab === 'radiology') return [
    { key: 'testCode', label: 'Code' },
    { key: 'testName', label: 'Test Name' },
    { key: 'modality', label: 'Modality' },
    { key: 'estimatedDurationMinutes', label: 'Duration (min)' },
    { key: 'price', label: 'Price' },
  ];
  if (tab === 'allergies') return [
    { key: 'allergyName', label: 'Allergy' },
    { key: 'category', label: 'Category' },
    { key: 'isCustom', label: 'Type', render: item => item.isCustom ? 'Custom' : 'Default' },
  ];
  if (tab === 'diagnoses') return [
    { key: 'icdCode', label: 'ICD Code' },
    { key: 'icdDescription', label: 'Description' },
    { key: 'category', label: 'Category' },
    { key: 'isCustom', label: 'Type', render: item => item.isCustom ? 'Custom' : 'Default' },
  ];
  if (tab === 'procedures') return [
    { key: 'procedureCode', label: 'Code' },
    { key: 'procedureName', label: 'Procedure' },
    { key: 'department', label: 'Department' },
    { key: 'estimatedDurationMinutes', label: 'Duration (min)' },
    { key: 'price', label: 'Price' },
  ];
  return [];
}

function MasterDataModal({ tab, editItem, onSave, onClose }) {
  const [form, setForm] = useState(editItem ? { ...editItem } : {});
  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const handleSubmit = (e) => { e.preventDefault(); onSave(form); };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-gray-800">
            {editItem ? 'Edit' : 'Add'} {TABS.find(t => t.id === tab)?.label}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl leading-none">&times;</button>
        </div>
        <form onSubmit={handleSubmit} className="space-y-3">
          {tab === 'lab' && <>
            <Field label="Test Name *" value={form.testName || ''} onChange={v => set('testName', v)} required />
            <Field label="Code" value={form.testCode || ''} onChange={v => set('testCode', v)} />
            <SelectField label="Department" value={form.department || 'OTHER'} onChange={v => set('department', v)}
              options={['BIOCHEMISTRY','HEMATOLOGY','MICROBIOLOGY','SEROLOGY','PATHOLOGY','OTHER']} />
            <SelectField label="Sample Type" value={form.sampleType || 'BLOOD'} onChange={v => set('sampleType', v)}
              options={['BLOOD','URINE','STOOL','SWAB','CSF','OTHER']} />
            <Field label="Normal Range" value={form.normalRangeText || ''} onChange={v => set('normalRangeText', v)} />
            <Field label="Unit" value={form.unit || ''} onChange={v => set('unit', v)} />
            <Field label="TAT (hours)" value={form.turnaroundHours || ''} onChange={v => set('turnaroundHours', v)} type="number" />
            <Field label="Price" value={form.price || ''} onChange={v => set('price', v)} type="number" />
          </>}
          {tab === 'radiology' && <>
            <Field label="Test Name *" value={form.testName || ''} onChange={v => set('testName', v)} required />
            <Field label="Code" value={form.testCode || ''} onChange={v => set('testCode', v)} />
            <SelectField label="Modality" value={form.modality || 'OTHER'} onChange={v => set('modality', v)}
              options={['X_RAY','CT','MRI','USG','ECHO','ECG','OTHER']} />
            <Field label="Preparation Instructions" value={form.preparationInstructions || ''} onChange={v => set('preparationInstructions', v)} textarea />
            <Field label="Duration (minutes)" value={form.estimatedDurationMinutes || ''} onChange={v => set('estimatedDurationMinutes', v)} type="number" />
            <Field label="Price" value={form.price || ''} onChange={v => set('price', v)} type="number" />
          </>}
          {tab === 'allergies' && <>
            <Field label="Allergy Name *" value={form.allergyName || ''} onChange={v => set('allergyName', v)} required />
            <SelectField label="Category" value={form.category || 'OTHER'} onChange={v => set('category', v)}
              options={['DRUG','FOOD','ENVIRONMENTAL','OTHER']} />
          </>}
          {tab === 'diagnoses' && <>
            <Field label="ICD Code *" value={form.icdCode || ''} onChange={v => set('icdCode', v)} required />
            <Field label="Description *" value={form.icdDescription || ''} onChange={v => set('icdDescription', v)} required />
            <SelectField label="Category" value={form.category || 'OTHER'} onChange={v => set('category', v)}
              options={['INFECTIOUS','CARDIOVASCULAR','RESPIRATORY','ENDOCRINE','NEUROLOGICAL',
                'MUSCULOSKELETAL','GASTROINTESTINAL','GENITOURINARY','OBSTETRIC','MENTAL','INJURY','NEOPLASM','OTHER']} />
          </>}
          {tab === 'procedures' && <>
            <Field label="Procedure Name *" value={form.procedureName || ''} onChange={v => set('procedureName', v)} required />
            <Field label="Code" value={form.procedureCode || ''} onChange={v => set('procedureCode', v)} />
            <Field label="Department" value={form.department || ''} onChange={v => set('department', v)} />
            <Field label="Duration (minutes)" value={form.estimatedDurationMinutes || ''} onChange={v => set('estimatedDurationMinutes', v)} type="number" />
            <Field label="Price" value={form.price || ''} onChange={v => set('price', v)} type="number" />
          </>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50">Cancel</button>
            <button type="submit" className="px-4 py-2 text-sm rounded-lg bg-blue-600 text-white hover:bg-blue-700 font-medium">
              {editItem ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Field({ label, value, onChange, required, type = 'text', textarea }) {
  const cls = "w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500";
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
      {textarea
        ? <textarea className={cls} rows={2} value={value} onChange={e => onChange(e.target.value)} required={required} />
        : <input className={cls} type={type} value={value} onChange={e => onChange(e.target.value)} required={required} />}
    </div>
  );
}

function SelectField({ label, value, onChange, options }) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
      <select className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        value={value} onChange={e => onChange(e.target.value)}>
        {options.map(o => <option key={o} value={o}>{o.replace(/_/g, ' ')}</option>)}
      </select>
    </div>
  );
}
```

- [ ] **Step 2: Build check**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/hospital/MasterDataView.jsx
git commit -m "feat(master-data): add MasterDataView admin tab with CRUD for all 5 catalogs + seed button"
```

---

## Task 10: Wire Masters Tab into HospitalAdminDashboard

**Files:**
- Modify: `frontend/src/pages/hospital/HospitalAdminDashboard.jsx`

- [ ] **Step 1: Add import at the top of HospitalAdminDashboard.jsx**

After the existing import for `MrdArchive` (or any existing import), add:

```jsx
import MasterDataView from './MasterDataView';
```

- [ ] **Step 2: Add "masters" to the tabs array**

Find the tabs array in `HospitalAdminDashboard.jsx` (around line 1292):
```jsx
{ id: 'overview', label: 'Overview', icon: null, requiredModule: null },
```

Add after the `analytics` tab entry:
```jsx
{ id: 'masters', label: 'Master Data', icon: null, requiredModule: null },
```

- [ ] **Step 3: Add tab render in the JSX content section**

Find the section where other tabs render their content (where `activeTab === 'analytics'` check is). Add:

```jsx
{activeTab === 'masters' && (
  <div className="p-6">
    <MasterDataView />
  </div>
)}
```

- [ ] **Step 4: Build check**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: success.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/hospital/HospitalAdminDashboard.jsx
git commit -m "feat(master-data): wire Masters tab into HospitalAdminDashboard"
```

---

## Task 11: Patient Allergy Card in IpdDetails

**Files:**
- Modify: `frontend/src/pages/hospital/IpdDetails.jsx`

- [ ] **Step 1: Add import and state in IpdDetails.jsx**

At the top with other imports, add:
```jsx
import masterDataService from '../../services/masterDataService';
import SearchableSelect from '../../components/SearchableSelect';
```

In the component, add state near other state declarations:
```jsx
const [allergies, setAllergies] = useState([]);
const [showAllergyModal, setShowAllergyModal] = useState(false);
const [allergyForm, setAllergyForm] = useState({ allergyMasterId: null, allergyName: '', severity: 'UNKNOWN', notes: '' });
```

- [ ] **Step 2: Add allergy fetch in the useEffect that loads IPD data**

In the existing `useEffect` that fetches admission data, add after the data is loaded:

```jsx
if (data?.patientId) {
  hospitalService.getPatientAllergies(data.patientId)
    .then(setAllergies)
    .catch(() => {});
}
```

- [ ] **Step 3: Add allergy card JSX in the sidebar section**

Find a logical place in the IpdDetails sidebar (near VitalSigns or patient info section) and insert:

```jsx
{/* Patient Allergies */}
<div className="bg-white rounded-xl border border-gray-200 p-4">
  <div className="flex items-center justify-between mb-3">
    <h3 className="text-sm font-semibold text-gray-700">Known Allergies</h3>
    {(isDoctor || isNurse || isAdmin) && !data?.isArchived && (
      <button
        onClick={() => setShowAllergyModal(true)}
        className="text-xs px-2 py-1 rounded bg-red-50 text-red-600 hover:bg-red-100 font-medium"
      >
        + Add
      </button>
    )}
  </div>
  {allergies.length === 0 ? (
    <p className="text-xs text-gray-400">No known allergies recorded</p>
  ) : (
    <div className="flex flex-wrap gap-2">
      {allergies.map(a => (
        <span key={a.id}
          className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium
            ${a.severity === 'SEVERE' ? 'bg-red-100 text-red-700' :
              a.severity === 'MODERATE' ? 'bg-orange-100 text-orange-700' :
              'bg-yellow-100 text-yellow-700'}`}>
          ⚠ {a.allergyName || `Allergy #${a.allergyMasterId}`}
          <span className="opacity-60">· {a.severity}</span>
          {(isDoctor || isNurse || isAdmin) && !data?.isArchived && (
            <button
              onClick={async () => {
                await hospitalService.removePatientAllergy(data.patientId, a.id);
                setAllergies(prev => prev.filter(x => x.id !== a.id));
              }}
              className="ml-1 opacity-50 hover:opacity-100"
            >×</button>
          )}
        </span>
      ))}
    </div>
  )}
</div>

{/* Add Allergy Modal */}
{showAllergyModal && (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div className="bg-white rounded-xl shadow-2xl w-full max-w-md p-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-800">Add Allergy</h3>
        <button onClick={() => setShowAllergyModal(false)} className="text-gray-400 hover:text-gray-600 text-2xl">&times;</button>
      </div>
      <div className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Search Allergy</label>
          <SearchableSelect
            onSearch={masterDataService.searchAllergies}
            onSelect={item => setAllergyForm(f => ({ ...f, allergyMasterId: item.id, allergyName: item.allergyName }))}
            getLabel={item => item.allergyName}
            placeholder="Search allergy (e.g. Penicillin)"
            value={allergyForm.allergyName}
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Severity</label>
          <select value={allergyForm.severity}
            onChange={e => setAllergyForm(f => ({ ...f, severity: e.target.value }))}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
            <option value="UNKNOWN">Unknown</option>
            <option value="MILD">Mild</option>
            <option value="MODERATE">Moderate</option>
            <option value="SEVERE">Severe</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
          <input type="text" value={allergyForm.notes}
            onChange={e => setAllergyForm(f => ({ ...f, notes: e.target.value }))}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="e.g. Anaphylaxis history" />
        </div>
        <div className="flex justify-end gap-3 pt-2">
          <button onClick={() => setShowAllergyModal(false)}
            className="px-4 py-2 text-sm rounded-lg border border-gray-300 text-gray-600">Cancel</button>
          <button
            disabled={!allergyForm.allergyMasterId}
            onClick={async () => {
              try {
                const saved = await hospitalService.addPatientAllergy(data.patientId, {
                  allergyMasterId: allergyForm.allergyMasterId,
                  severity: allergyForm.severity,
                  notes: allergyForm.notes,
                });
                setAllergies(prev => [...prev, { ...saved, allergyName: allergyForm.allergyName }]);
                setShowAllergyModal(false);
                setAllergyForm({ allergyMasterId: null, allergyName: '', severity: 'UNKNOWN', notes: '' });
              } catch (e) {
                alert(e.response?.data?.message || 'Failed to add allergy');
              }
            }}
            className="px-4 py-2 text-sm rounded-lg bg-red-600 text-white hover:bg-red-700 font-medium disabled:opacity-50">
            Save Allergy
          </button>
        </div>
      </div>
    </div>
  </div>
)}
```

- [ ] **Step 4: Build check**

```bash
cd frontend && npm run build 2>&1 | tail -10
```

Fix any TypeScript/JSX errors shown.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/hospital/IpdDetails.jsx
git commit -m "feat(master-data): add patient allergy card to IpdDetails with add/remove UI"
```

---

## Task 12: Update Lab Order Form to Use LabTestMaster Autocomplete

Find where lab orders are created in the frontend. Grep to locate:

```bash
grep -r "orderLabTest\|lab.*order.*modal\|testName" frontend/src --include="*.jsx" -l
```

Typical location: `frontend/src/pages/hospital/IpdDetails.jsx` inside the lab order creation modal.

- [ ] **Step 1: Replace the test name text input with SearchableSelect**

Find the lab order form's test name input (look for `testName` or `test_name` field in the lab order modal). Replace:

```jsx
<input
  type="text"
  value={labOrderForm.testName}
  onChange={e => setLabOrderForm(f => ({ ...f, testName: e.target.value }))}
  placeholder="e.g. CBC, LFT, RFT"
  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm ..."
/>
```

With:

```jsx
<SearchableSelect
  onSearch={masterDataService.searchLabTests}
  onSelect={item => setLabOrderForm(f => ({
    ...f,
    testName: item.testName,
    labTestMasterId: item.id
  }))}
  getLabel={item => `${item.testName}${item.testCode ? ' (' + item.testCode + ')' : ''}`}
  placeholder="Search lab test (e.g. CBC, LFT)"
  value={labOrderForm.testName || ''}
  hint={labOrderForm.labTestMasterId ? `Sample: ${labOrderForm.sampleType || 'See master'}` : ''}
/>
```

Also ensure `labTestMasterId` is sent in the API payload when ordering.

- [ ] **Step 2: Update the lab order API call in the service/controller to accept labTestMasterId**

In `LabWorkflowService.java` (the `orderTest` or equivalent method), after setting `testName`, add:
```java
if (req.getLabTestMasterId() != null) {
    order.setLabTestMasterId(req.getLabTestMasterId());
}
```

And in the DTO/request class for lab orders, add:
```java
private Long labTestMasterId;
```

- [ ] **Step 3: Build check and commit**

```bash
cd frontend && npm run build 2>&1 | tail -5
cd backend && mvn clean compile -q
git add frontend/src/pages/hospital/IpdDetails.jsx
git add backend/src/main/java/com/hms/
git commit -m "feat(master-data): lab order form now uses LabTestMaster autocomplete"
```

---

## Task 13: Update Radiology Order Form to Use RadiologyTestMaster Autocomplete

Same pattern as Task 12 but for radiology orders.

- [ ] **Step 1: Replace radiology test name input with SearchableSelect**

Find the radiology order form (likely also in `IpdDetails.jsx`). Replace free-text scan name with:

```jsx
<SearchableSelect
  onSearch={masterDataService.searchRadiologyTests}
  onSelect={item => setRadiologyOrderForm(f => ({
    ...f,
    testName: item.testName,
    radiologyTestMasterId: item.id,
    modality: item.modality
  }))}
  getLabel={item => `${item.testName}${item.modality ? ' [' + item.modality.replace('_',' ') + ']' : ''}`}
  placeholder="Search radiology test (e.g. X-Ray Chest, MRI Brain)"
  value={radiologyOrderForm.testName || ''}
  hint={radiologyOrderForm.modality ? `Modality: ${radiologyOrderForm.modality.replace('_',' ')}` : ''}
/>
```

- [ ] **Step 2: Update RadiologyWorkflowService request DTO**

Add `radiologyTestMasterId` field to radiology order request DTO and set it in service.

- [ ] **Step 3: Build + compile + commit**

```bash
cd frontend && npm run build 2>&1 | tail -5
cd backend && mvn clean compile -q
git add frontend/src/pages/hospital/IpdDetails.jsx
git add backend/src/main/java/com/hms/
git commit -m "feat(master-data): radiology order form now uses RadiologyTestMaster autocomplete"
```

---

## Task 14: Update Prescription Form to Use MedicineMaster Autocomplete

- [ ] **Step 1: Find the prescription form**

```bash
grep -r "medicineName\|prescribe\|prescription.*form" frontend/src --include="*.jsx" -l
```

Locate the medicine name input in the prescription creation modal.

- [ ] **Step 2: Replace medicine name text input**

Replace free-text medicine name with:

```jsx
<SearchableSelect
  onSearch={async (q) => {
    const res = await apiClient.get(`/hospital/master/lab-tests/search`); // wrong
    // Actually use pharmacy medicine endpoint:
    const r = await apiClient.get(`/api/pharmacy/medicines/search?q=${encodeURIComponent(q)}`);
    return r.data;
  }}
  onSelect={item => setPrescriptionForm(f => ({
    ...f,
    medicineName: item.medicineName,
    medicineMasterId: item.id,
    dosage: item.strength || f.dosage
  }))}
  getLabel={item => `${item.medicineName}${item.strength ? ' ' + item.strength : ''}`}
  placeholder="Search medicine (e.g. Paracetamol, Amoxicillin)"
  value={prescriptionForm.medicineName || ''}
/>
```

**Note:** Check what endpoint the pharmacy medicine search uses. Look for it with:
```bash
grep -r "medicines/search\|medicine.*search" backend/src --include="*.java" -l
```

If no search endpoint exists on pharmacy medicines, add one to `MasterDataController` or `PharmacyController`:
```java
@GetMapping("/medicines/search")
public ResponseEntity<List<MedicineMaster>> searchMedicines(@RequestParam(required = false) String q) {
    Long hospitalId = securityHelper.getCurrentHospitalId();
    if (q == null || q.isBlank()) {
        return ResponseEntity.ok(medicineMasterRepo.findByHospitalIdAndIsActiveTrueOrderByMedicineNameAsc(hospitalId));
    }
    return ResponseEntity.ok(medicineMasterRepo.searchByHospitalAndName(hospitalId, q));
}
```

Add to `MedicineMasterRepository`:
```java
@Query("SELECT m FROM MedicineMaster m WHERE m.hospitalId = :hospitalId AND m.isActive = true " +
       "AND LOWER(m.medicineName) LIKE LOWER(CONCAT('%',:q,'%'))")
List<MedicineMaster> searchByHospitalAndName(@Param("hospitalId") Long hospitalId, @Param("q") String q);

List<MedicineMaster> findByHospitalIdAndIsActiveTrueOrderByMedicineNameAsc(Long hospitalId);
```

- [ ] **Step 3: Update Prescription DTO to carry medicineMasterId**

In whatever request class creates prescriptions, add:
```java
private Long medicineMasterId;
```

In the prescription service (wherever it saves the `Prescription` entity), add:
```java
if (req.getMedicineMasterId() != null) {
    prescription.setMedicineMasterId(req.getMedicineMasterId());
}
```

- [ ] **Step 4: Full build + tests**

```bash
cd backend && mvn clean compile -q && mvn test -q
cd frontend && npm run build 2>&1 | tail -5
```

Expected: both pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/
git add frontend/src/
git commit -m "feat(master-data): prescription form uses MedicineMaster autocomplete, medicineMasterId stored"
```

---

## Task 15: Final Verification

- [ ] **Step 1: Run full backend test suite**

```bash
cd backend && mvn test -q
```

Expected: all tests PASS, no regressions.

- [ ] **Step 2: Run frontend build**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: no errors or warnings about undefined variables.

- [ ] **Step 3: Manual smoke test checklist**

Start both servers:
```bash
# Terminal 1
cd backend && mvn spring-boot:run

# Terminal 2
cd frontend && npm run dev
```

Test each of these:
- [ ] Login as HOSPITAL_ADMIN → navigate to "Master Data" tab → should show 5 sub-tabs
- [ ] Click "Seed Defaults" on Allergies tab → rows appear (Penicillin, Peanuts, etc.)
- [ ] Click "Seed Defaults" on Diagnoses tab → rows appear (I10 Hypertension, E11 Diabetes, etc.)
- [ ] Add a custom Lab Test (e.g. "Vitamin D", BIOCHEMISTRY, BLOOD, price 500) → appears in table
- [ ] Add a custom Radiology Test (e.g. "X-Ray Chest PA View", X_RAY, 15 min) → appears
- [ ] Open any IPD admission → Allergies card visible with "+ Add" button
- [ ] Add allergy "Penicillin · SEVERE" to patient → red chip appears
- [ ] Create a lab order from IPD → type "CBC" in test field → autocomplete appears → select → order created
- [ ] Create a radiology order → type "X-Ray" → autocomplete → select → order created
- [ ] Create a prescription → type "Paracetamol" → autocomplete from medicine master → select → prescription saved

- [ ] **Step 4: Commit schema-full.sql update**

Add the new tables to `setup/schema-full.sql` to keep it as the canonical schema:
```bash
# Append the CREATE TABLE statements from the migration to schema-full.sql
# Then commit
git add setup/schema-full.sql
git commit -m "docs(schema): add master data tables to schema-full.sql"
```

- [ ] **Step 5: Final commit tag**

```bash
git log --oneline -8
git tag v-master-data-complete
```

---

## Summary of API Endpoints

| Method | URL | Auth |
|--------|-----|------|
| GET | `/hospital/master/lab-tests/search?q=` | All hospital roles |
| POST | `/hospital/master/lab-tests` | HOSPITAL_ADMIN |
| PUT | `/hospital/master/lab-tests/{id}` | HOSPITAL_ADMIN |
| DELETE | `/hospital/master/lab-tests/{id}` | HOSPITAL_ADMIN |
| GET | `/hospital/master/radiology-tests/search?q=` | All |
| POST/PUT/DELETE | `/hospital/master/radiology-tests/{id}` | HOSPITAL_ADMIN |
| GET | `/hospital/master/allergies/search?q=` | All |
| POST/DELETE | `/hospital/master/allergies/{id}` | HOSPITAL_ADMIN |
| GET | `/hospital/master/diagnoses/search?q=` | All |
| POST/DELETE | `/hospital/master/diagnoses/{id}` | HOSPITAL_ADMIN |
| GET | `/hospital/master/procedures/search?q=` | All |
| POST/PUT/DELETE | `/hospital/master/procedures/{id}` | HOSPITAL_ADMIN |
| POST | `/hospital/master/seed` | HOSPITAL_ADMIN |
| GET | `/hospital/patients/{id}/allergies` | All |
| POST | `/hospital/patients/{id}/allergies` | DOCTOR, NURSE, ADMIN |
| DELETE | `/hospital/patients/{id}/allergies/{aid}` | DOCTOR, NURSE, ADMIN |
