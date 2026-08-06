# Legacy Patient Import — Phase 1A (Backend Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the backend import engine and `PatientImporter` so an admin can upload a patient spreadsheet, preview it without writing anything, commit it, and undo it — via API, with full test coverage.

**Architecture:** A generic `ImportEngine` owns upload → parse → map → dry-run → commit → undo. Entities plug in via a small `EntityImporter` interface; Phase 1A ships only `PatientImporter`. Every imported row carries `import_batch_id` for lineage, which is what makes undo possible. Strict field validation moves off the `Patient` entity onto a `PatientRequest` DTO so imports can keep blanks blank while manual entry stays strict.

**Tech Stack:** Spring Boot 3 · JPA/Hibernate · MySQL · Apache POI (streaming SAX reader) · JUnit 5 · Mockito · AssertJ

**Spec:** [2026-08-06-legacy-patient-import-design.md](../specs/2026-08-06-legacy-patient-import-design.md)

**Not in this plan:** the admin wizard UI (Phase 1B), and the Visit/Prescription/Bill importers (Phases 2–4).

---

## File Structure

**Create — backend/src/main/java/com/hms/**

| File                                        | Responsibility                                               |
| ------------------------------------------- | ------------------------------------------------------------ |
| `entity/ImportBatch.java`                   | Batch record: status, counts, mapping, audit fields          |
| `entity/ImportStatus.java`                  | Enum `DRAFT/PREVIEWED/RUNNING/COMPLETED/FAILED/UNDONE`       |
| `entity/ImportEntityType.java`              | Enum `PATIENT/VISIT/PRESCRIPTION/BILL`                       |
| `entity/ImportRowError.java`                | One failed row: number, column, message, raw JSON            |
| `entity/ImportSource.java`                  | Enum `MANUAL/IMPORTED` for `Patient.source`                  |
| `repository/ImportBatchRepository.java`     | Batch queries, tenant-scoped                                 |
| `repository/ImportRowErrorRepository.java`  | Paged error lookup by batch                                  |
| `dto/PatientRequest.java`                   | Strict-validated create/update payload (two-tier validation) |
| `dto/import_/ImportFieldDef.java`           | One mappable target field + header synonyms                  |
| `dto/import_/ParsedSheet.java`              | Headers + row iterator from the parser                       |
| `dto/import_/ImportPreview.java`            | Dry-run result: counts, samples, errors                      |
| `dto/import_/RowOutcome.java`               | Per-row verdict: CREATE/UPDATE/SKIP/ERROR                    |
| `service/import_/ImportFieldRegistry.java`  | Canonical target fields per entity                           |
| `service/import_/ColumnMapper.java`         | Auto-suggest header → field                                  |
| `service/import_/WorkbookParser.java`       | Streaming `.xlsx` + `.csv` reader                            |
| `service/import_/EntityImporter.java`       | Interface every entity importer implements                   |
| `service/import_/PatientImporter.java`      | Patient field mapping, dedupe, apply                         |
| `service/import_/ImportEngine.java`         | Orchestrates dry-run and commit                              |
| `service/import_/ImportBatchService.java`   | Lifecycle, undo, error CSV                                   |
| `controller/hospital/ImportController.java` | `/hospital/imports/**` REST surface                          |

**Modify**

| File                                               | Change                                                                                                                   |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `entity/Patient.java`                              | Remove strict Bean Validation; add `legacyId`, `source`, `importBatchId`, `customFields`; make `gender`/`phone` nullable |
| `controller/hospital/PatientController.java:32-42` | Bind `PatientRequest` instead of `Patient`                                                                               |
| `service/hospital/PatientService.java`             | Accept `PatientRequest` on create/update                                                                                 |
| `config/DatabaseMigrationRunner.java`              | Add `ensureImportTables()` + patient columns                                                                             |
| `setup/schema-full.sql`                            | Mirror the DDL                                                                                                           |
| `backend/pom.xml`                                  | Add `poi-ooxml`                                                                                                          |

**Design note:** import code lives in its own `service/import_/` package (trailing underscore — `import` is a Java keyword and cannot be a package name). This keeps 19 new classes out of the already-crowded `service/hospital/`.

---

## Task 1: Schema migration

**Files:**

- Modify: `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
- Modify: `setup/schema-full.sql`

- [ ] **Step 1: Add the migration methods**

In `DatabaseMigrationRunner.java`, add before the closing brace:

```java
    private void ensureImportTables() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'import_batch'",
                    Integer.class);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute(
                        "CREATE TABLE import_batch (" +
                        "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "  public_id VARCHAR(64) NOT NULL UNIQUE," +
                        "  hospital_id BIGINT NOT NULL," +
                        "  entity_type VARCHAR(20) NOT NULL," +
                        "  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'," +
                        "  source_filename VARCHAR(255)," +
                        "  sheet_name VARCHAR(120)," +
                        "  mapping_json TEXT," +
                        "  total_rows INT NOT NULL DEFAULT 0," +
                        "  created_count INT NOT NULL DEFAULT 0," +
                        "  updated_count INT NOT NULL DEFAULT 0," +
                        "  skipped_count INT NOT NULL DEFAULT 0," +
                        "  failed_count INT NOT NULL DEFAULT 0," +
                        "  created_by VARCHAR(120)," +
                        "  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP," +
                        "  committed_at TIMESTAMP NULL," +
                        "  undone_at TIMESTAMP NULL," +
                        "  KEY idx_import_batch_hosp (hospital_id, status)," +
                        "  CONSTRAINT fk_import_batch_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                        ")");
                log.info("Created import_batch table");
            }
        } catch (Exception e) {
            log.warn("ensureImportTables(import_batch) failed: {}", e.getMessage());
        }

        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'import_row_error'",
                    Integer.class);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute(
                        "CREATE TABLE import_row_error (" +
                        "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "  batch_id BIGINT NOT NULL," +
                        "  row_number INT NOT NULL," +
                        "  column_name VARCHAR(120)," +
                        "  message VARCHAR(500) NOT NULL," +
                        "  raw_row_json TEXT," +
                        "  KEY idx_import_err_batch (batch_id)," +
                        "  CONSTRAINT fk_import_err_batch FOREIGN KEY (batch_id) REFERENCES import_batch(id) ON DELETE CASCADE" +
                        ")");
                log.info("Created import_row_error table");
            }
        } catch (Exception e) {
            log.warn("ensureImportTables(import_row_error) failed: {}", e.getMessage());
        }
    }

    private void ensurePatientImportColumns() {
        addColumnIfMissing("patients", "legacy_id", "VARCHAR(100) NULL");
        addColumnIfMissing("patients", "source", "VARCHAR(20) NOT NULL DEFAULT 'MANUAL'");
        addColumnIfMissing("patients", "import_batch_id", "BIGINT NULL");
        addColumnIfMissing("patients", "custom_fields", "TEXT NULL");
        // gender/phone become optional so imported records can keep blanks blank.
        try {
            jdbcTemplate.execute("ALTER TABLE patients MODIFY COLUMN gender VARCHAR(10) NULL");
            jdbcTemplate.execute("ALTER TABLE patients MODIFY COLUMN phone VARCHAR(15) NULL");
        } catch (Exception e) {
            log.warn("relaxing patients.gender/phone failed: {}", e.getMessage());
        }
        // Dedupe key. Partial index is not available in MySQL, so NULLs simply never collide.
        try {
            Integer idx = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                    "AND table_name = 'patients' AND index_name = 'uq_patient_legacy'",
                    Integer.class);
            if (idx == null || idx == 0) {
                jdbcTemplate.execute("ALTER TABLE patients ADD UNIQUE KEY uq_patient_legacy (hospital_id, legacy_id)");
                log.info("Created uq_patient_legacy index");
            }
        } catch (Exception e) {
            log.warn("uq_patient_legacy index skipped: {}", e.getMessage());
        }
    }
```

- [ ] **Step 2: Call them from `runMigrations()`**

Inside `runMigrations()`, alongside the other `ensure*` calls:

```java
        ensureImportTables();
        ensurePatientImportColumns();
```

- [ ] **Step 3: Mirror the DDL in `setup/schema-full.sql`**

Append the two `CREATE TABLE` statements above (without the string concatenation) and add to the `patients` table definition:

```sql
  legacy_id VARCHAR(100) NULL,
  source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  import_batch_id BIGINT NULL,
  custom_fields TEXT NULL,
  UNIQUE KEY uq_patient_legacy (hospital_id, legacy_id),
```

- [ ] **Step 4: Verify the app boots and migrations apply**

Run: `cd backend && mvn spring-boot:run`
Expected: log lines `Created import_batch table`, `Created import_row_error table`, `DB migration applied: added patients.legacy_id`. Stop the server.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java setup/schema-full.sql
git commit -m "feat(import): schema for import batches and patient lineage columns"
```

---

## Task 2: Entities and repositories

**Files:**

- Create: `backend/src/main/java/com/hms/entity/ImportStatus.java`, `ImportEntityType.java`, `ImportSource.java`, `ImportBatch.java`, `ImportRowError.java`
- Create: `backend/src/main/java/com/hms/repository/ImportBatchRepository.java`, `ImportRowErrorRepository.java`
- Test: `backend/src/test/java/com/hms/entity/ImportBatchTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ImportBatchTest {

    @Test
    void newBatchStartsAsDraftWithZeroCounts() {
        ImportBatch b = new ImportBatch();
        assertThat(b.getStatus()).isEqualTo(ImportStatus.DRAFT);
        assertThat(b.getCreatedCount()).isZero();
        assertThat(b.getFailedCount()).isZero();
    }

    @Test
    void undoIsOnlyAllowedForCompletedBatches() {
        ImportBatch b = new ImportBatch();
        b.setStatus(ImportStatus.DRAFT);
        assertThat(b.isUndoable()).isFalse();
        b.setStatus(ImportStatus.COMPLETED);
        assertThat(b.isUndoable()).isTrue();
        b.setStatus(ImportStatus.UNDONE);
        assertThat(b.isUndoable()).isFalse();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ImportBatchTest`
Expected: compilation failure — `ImportBatch` does not exist.

- [ ] **Step 3: Create the enums**

`ImportStatus.java`:

```java
package com.hms.entity;

public enum ImportStatus {
    DRAFT, PREVIEWED, RUNNING, COMPLETED, FAILED, UNDONE
}
```

`ImportEntityType.java`:

```java
package com.hms.entity;

public enum ImportEntityType {
    PATIENT, VISIT, PRESCRIPTION, BILL
}
```

`ImportSource.java`:

```java
package com.hms.entity;

public enum ImportSource {
    MANUAL, IMPORTED
}
```

- [ ] **Step 4: Create `ImportBatch`**

```java
package com.hms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private ImportEntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status = ImportStatus.DRAFT;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "sheet_name", length = 120)
    private String sheetName;

    @Column(name = "mapping_json", columnDefinition = "text")
    private String mappingJson;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Column(name = "created_count", nullable = false)
    private Integer createdCount = 0;

    @Column(name = "updated_count", nullable = false)
    private Integer updatedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "undone_at")
    private LocalDateTime undoneAt;

    /** A batch can only be reversed once it has actually written rows and has not been reversed already. */
    public boolean isUndoable() {
        return status == ImportStatus.COMPLETED || status == ImportStatus.FAILED;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public ImportEntityType getEntityType() { return entityType; }
    public void setEntityType(ImportEntityType entityType) { this.entityType = entityType; }
    public ImportStatus getStatus() { return status; }
    public void setStatus(ImportStatus status) { this.status = status; }
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public String getMappingJson() { return mappingJson; }
    public void setMappingJson(String mappingJson) { this.mappingJson = mappingJson; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getCreatedCount() { return createdCount; }
    public void setCreatedCount(Integer createdCount) { this.createdCount = createdCount; }
    public Integer getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(Integer updatedCount) { this.updatedCount = updatedCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCommittedAt() { return committedAt; }
    public void setCommittedAt(LocalDateTime committedAt) { this.committedAt = committedAt; }
    public LocalDateTime getUndoneAt() { return undoneAt; }
    public void setUndoneAt(LocalDateTime undoneAt) { this.undoneAt = undoneAt; }
}
```

- [ ] **Step 5: Create `ImportRowError`**

```java
package com.hms.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "import_row_error")
public class ImportRowError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "column_name", length = 120)
    private String columnName;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "raw_row_json", columnDefinition = "text")
    private String rawRowJson;

    public ImportRowError() { }

    public ImportRowError(Long batchId, Integer rowNumber, String columnName, String message, String rawRowJson) {
        this.batchId = batchId;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.message = message;
        this.rawRowJson = rawRowJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRawRowJson() { return rawRowJson; }
    public void setRawRowJson(String rawRowJson) { this.rawRowJson = rawRowJson; }
}
```

- [ ] **Step 6: Create the repositories**

`ImportBatchRepository.java`:

```java
package com.hms.repository;

import com.hms.entity.ImportBatch;
import com.hms.entity.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
    List<ImportBatch> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
    boolean existsByHospitalIdAndStatus(Long hospitalId, ImportStatus status);
}
```

`ImportRowErrorRepository.java`:

```java
package com.hms.repository;

import com.hms.entity.ImportRowError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {
    Page<ImportRowError> findByBatchId(Long batchId, Pageable pageable);
    List<ImportRowError> findByBatchIdOrderByRowNumberAsc(Long batchId);
    void deleteByBatchId(Long batchId);
}
```

- [ ] **Step 7: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ImportBatchTest`
Expected: PASS, 2 tests.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/hms/entity/Import*.java backend/src/main/java/com/hms/repository/Import*.java backend/src/test/java/com/hms/entity/ImportBatchTest.java
git commit -m "feat(import): batch and row-error entities with repositories"
```

---

## Task 3: Two-tier validation — `PatientRequest` DTO

This is the change that makes blank-preservation possible. Do it before any importer code.

**Files:**

- Create: `backend/src/main/java/com/hms/dto/PatientRequest.java`
- Modify: `backend/src/main/java/com/hms/entity/Patient.java`
- Modify: `backend/src/main/java/com/hms/controller/hospital/PatientController.java`
- Test: `backend/src/test/java/com/hms/dto/PatientRequestValidationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private PatientRequest valid() {
        PatientRequest r = new PatientRequest();
        r.setName("Ramesh Patel");
        r.setGender("Male");
        r.setPhone("9876543210");
        return r;
    }

    @Test
    void acceptsAValidManualPatient() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void rejectsBlankPhoneForManualEntry() {
        PatientRequest r = valid();
        r.setPhone("");
        assertThat(validator.validate(r)).isNotEmpty();
    }

    @Test
    void rejectsNonTenDigitPhone() {
        PatientRequest r = valid();
        r.setPhone("+919876543210");
        assertThat(validator.validate(r)).isNotEmpty();
    }

    @Test
    void rejectsBlankGender() {
        PatientRequest r = valid();
        r.setGender("");
        assertThat(validator.validate(r)).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=PatientRequestValidationTest`
Expected: compilation failure — `PatientRequest` does not exist.

- [ ] **Step 3: Create `PatientRequest` carrying the strict rules**

```java
package com.hms.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * Create/update payload for manually entered patients. The strict field rules live here rather than
 * on the Patient entity so that the importer can persist legacy records with blank or oddly
 * formatted values without Hibernate rejecting them (see the legacy import design, section 4.1).
 * Field names match the previous entity binding exactly, so the frontend contract is unchanged.
 */
public class PatientRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name is too long")
    private String name;

    @NotBlank(message = "Gender is required")
    @Pattern(regexp = "^[A-Za-z \\-]{1,10}$", message = "Invalid gender")
    private String gender;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email is too long")
    private String email;

    @Size(max = 255, message = "Address is too long")
    private String address;

    @Size(max = 1000, message = "Medical history is too long")
    private String medicalHistory;

    private LocalDate dateOfBirth;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getMedicalHistory() { return medicalHistory; }
    public void setMedicalHistory(String medicalHistory) { this.medicalHistory = medicalHistory; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=PatientRequestValidationTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Strip strict validation from the entity and add the new columns**

In `Patient.java`, **delete** these annotations (keep the `@Column` declarations and the `@Size` ones that guard column width):

- on `name`: remove `@NotBlank`
- on `gender`: remove `@NotBlank` and `@Pattern`
- on `phone`: remove `@NotBlank` and `@Pattern`

Change the two column declarations to allow NULL:

```java
    @Column(length = 10)
    private String gender;

    @Column(length = 15)
    private String phone;
```

Then add the import-lineage fields alongside the existing ones:

```java
    /** The hospital's own patient number from their previous system. Dedupe and join key for imports. */
    @Column(name = "legacy_id", length = 100)
    private String legacyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ImportSource source = ImportSource.MANUAL;

    /** Set only on imported rows. Lineage for undo. */
    @Column(name = "import_batch_id")
    private Long importBatchId;

    /** JSON map of columns the schema does not model, captured verbatim from the import file. */
    @Column(name = "custom_fields", columnDefinition = "text")
    private String customFields;

    public String getLegacyId() { return legacyId; }
    public void setLegacyId(String legacyId) { this.legacyId = legacyId; }
    public ImportSource getSource() { return source; }
    public void setSource(ImportSource source) { this.source = source; }
    public Long getImportBatchId() { return importBatchId; }
    public void setImportBatchId(Long importBatchId) { this.importBatchId = importBatchId; }
    public String getCustomFields() { return customFields; }
    public void setCustomFields(String customFields) { this.customFields = customFields; }
```

Add `import com.hms.entity.ImportSource;` if the entity is in a different package — it is not, so no import is needed. Ensure `jakarta.persistence.Enumerated` and `EnumType` are imported.

- [ ] **Step 6: Update the controller to bind the DTO**

In `PatientController.java`, replace the two bindings:

```java
    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> addPatient(@Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(patientService.addPatient(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> updatePatient(@PathVariable Long id, @Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(patientService.updatePatient(id, request)));
    }
```

Keep the existing `@PreAuthorize` values from the file if they differ — do not change authorisation as part of this task. Add `import com.hms.dto.PatientRequest;`.

- [ ] **Step 7: Update `PatientService` to accept the DTO**

In `PatientService.java`, change the `addPatient` and `updatePatient` signatures to take `PatientRequest` and map onto the entity at the top of each method:

```java
    public Patient addPatient(PatientRequest request) {
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setMedicalHistory(request.getMedicalHistory());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setSource(ImportSource.MANUAL);
        // ... existing body continues unchanged, operating on `patient`
    }
```

Apply the same field-copy at the start of `updatePatient(Long publicId, PatientRequest request)`, assigning onto the loaded entity instead of a new one.

- [ ] **Step 8: Run the full backend suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. If `PatientApiTest` fails on the request shape, update its payload to match `PatientRequest` — the JSON field names are unchanged, so only compile-level references need fixing.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/hms/dto/PatientRequest.java backend/src/main/java/com/hms/entity/Patient.java backend/src/main/java/com/hms/controller/hospital/PatientController.java backend/src/main/java/com/hms/service/hospital/PatientService.java backend/src/test/java/com/hms/dto/PatientRequestValidationTest.java
git commit -m "refactor(patient): move strict validation to PatientRequest DTO

Imports must be able to persist legacy records with blank gender or a non-10-digit phone.
Bean Validation on the entity fires on every JPA persist, so that was impossible while the
rules lived there. Manual entry keeps identical rules and messages via the DTO."
```

---

## Task 4: Import field registry

**Files:**

- Create: `backend/src/main/java/com/hms/dto/import_/ImportFieldDef.java`
- Create: `backend/src/main/java/com/hms/service/import_/ImportFieldRegistry.java`
- Test: `backend/src/test/java/com/hms/service/import_/ImportFieldRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.entity.ImportEntityType;
import com.hms.dto.import_.ImportFieldDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportFieldRegistryTest {

    private final ImportFieldRegistry registry = new ImportFieldRegistry();

    @Test
    void exposesPatientTargetFields() {
        List<ImportFieldDef> fields = registry.fieldsFor(ImportEntityType.PATIENT);
        assertThat(fields).extracting(ImportFieldDef::key)
                .contains("name", "gender", "phone", "legacyId", "dateOfBirth");
    }

    @Test
    void nameIsTheOnlyRequiredPatientField() {
        List<ImportFieldDef> required = registry.fieldsFor(ImportEntityType.PATIENT)
                .stream().filter(ImportFieldDef::required).toList();
        assertThat(required).extracting(ImportFieldDef::key).containsExactly("name");
    }

    @Test
    void synonymsAreLowercasedForMatching() {
        ImportFieldDef phone = registry.fieldsFor(ImportEntityType.PATIENT).stream()
                .filter(f -> f.key().equals("phone")).findFirst().orElseThrow();
        assertThat(phone.synonyms()).contains("mobile", "mob no", "contact number");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ImportFieldRegistryTest`
Expected: compilation failure.

- [ ] **Step 3: Create `ImportFieldDef`**

```java
package com.hms.dto.import_;

import java.util.List;

/**
 * One mappable target field. `synonyms` are lowercase header spellings seen in real legacy exports
 * and drive auto-mapping; the admin can always override the suggestion.
 */
public record ImportFieldDef(
        String key,
        String label,
        boolean required,
        List<String> synonyms
) { }
```

- [ ] **Step 4: Create `ImportFieldRegistry`**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ImportFieldDef;
import com.hms.entity.ImportEntityType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Canonical list of importable fields per entity, in the same spirit as FormRegistry and
 * VitalRegistry. Only PATIENT is populated in Phase 1A; later phases add their own lists.
 */
@Component
public class ImportFieldRegistry {

    private static final List<ImportFieldDef> PATIENT_FIELDS = List.of(
            new ImportFieldDef("name", "Full name", true,
                    List.of("name", "patient name", "full name", "patientname")),
            new ImportFieldDef("legacyId", "Old patient ID / MRN", false,
                    List.of("mrn", "patient id", "old id", "card no", "old card no", "registration no", "reg no")),
            new ImportFieldDef("gender", "Gender", false,
                    List.of("gender", "sex")),
            new ImportFieldDef("phone", "Phone", false,
                    List.of("phone", "mobile", "mob no", "mobile no", "contact", "contact number")),
            new ImportFieldDef("email", "Email", false,
                    List.of("email", "email id", "e-mail")),
            new ImportFieldDef("dateOfBirth", "Date of birth", false,
                    List.of("dob", "date of birth", "birth date", "birthdate")),
            new ImportFieldDef("address", "Address", false,
                    List.of("address", "residence", "addr")),
            new ImportFieldDef("medicalHistory", "Medical history", false,
                    List.of("medical history", "history", "notes", "remarks"))
    );

    public List<ImportFieldDef> fieldsFor(ImportEntityType type) {
        if (type == ImportEntityType.PATIENT) {
            return PATIENT_FIELDS;
        }
        return List.of();
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ImportFieldRegistryTest`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/dto/import_/ImportFieldDef.java backend/src/main/java/com/hms/service/import_/ImportFieldRegistry.java backend/src/test/java/com/hms/service/import_/ImportFieldRegistryTest.java
git commit -m "feat(import): canonical patient field registry with header synonyms"
```

---

## Task 5: Column auto-mapper

**Files:**

- Create: `backend/src/main/java/com/hms/service/import_/ColumnMapper.java`
- Test: `backend/src/test/java/com/hms/service/import_/ColumnMapperTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.entity.ImportEntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnMapperTest {

    private final ColumnMapper mapper = new ColumnMapper(new ImportFieldRegistry());

    @Test
    void matchesHeadersToFieldsCaseAndSpaceInsensitively() {
        Map<String, String> m = mapper.suggest(
                List.of("Patient Name", "MOB NO", "  DOB  ", "MRN"), ImportEntityType.PATIENT);
        assertThat(m).containsEntry("Patient Name", "name");
        assertThat(m).containsEntry("MOB NO", "phone");
        assertThat(m).containsEntry("  DOB  ", "dateOfBirth");
        assertThat(m).containsEntry("MRN", "legacyId");
    }

    @Test
    void leavesUnknownHeadersUnmapped() {
        Map<String, String> m = mapper.suggest(List.of("Referred By", "Caste"), ImportEntityType.PATIENT);
        assertThat(m).isEmpty();
    }

    @Test
    void neverMapsTwoHeadersToTheSameField() {
        Map<String, String> m = mapper.suggest(List.of("Name", "Patient Name"), ImportEntityType.PATIENT);
        assertThat(m.values()).containsExactly("name");
    }

    @Test
    void reportsHeadersThatWillBecomeCustomFields() {
        List<String> headers = List.of("Name", "Referred By", "Caste");
        Map<String, String> m = mapper.suggest(headers, ImportEntityType.PATIENT);
        assertThat(mapper.unmapped(headers, m)).containsExactly("Referred By", "Caste");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ColumnMapperTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `ColumnMapper`**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ImportFieldDef;
import com.hms.entity.ImportEntityType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Suggests header -> field mappings. Suggestions only: the admin confirms or overrides every one
 * before a dry-run happens, so a wrong guess is never destructive.
 */
@Component
public class ColumnMapper {

    private final ImportFieldRegistry registry;

    public ColumnMapper(ImportFieldRegistry registry) {
        this.registry = registry;
    }

    public Map<String, String> suggest(List<String> headers, ImportEntityType type) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> claimed = new HashSet<>();
        List<ImportFieldDef> fields = registry.fieldsFor(type);

        for (String header : headers) {
            String norm = normalise(header);
            if (norm.isEmpty()) continue;
            for (ImportFieldDef field : fields) {
                if (claimed.contains(field.key())) continue;
                if (field.synonyms().contains(norm)) {
                    result.put(header, field.key());
                    claimed.add(field.key());
                    break;
                }
            }
        }
        return result;
    }

    /** Headers with no target field. Their values are preserved verbatim in Patient.customFields. */
    public List<String> unmapped(List<String> headers, Map<String, String> mapping) {
        List<String> out = new ArrayList<>();
        for (String h : headers) {
            if (!mapping.containsKey(h) && !normalise(h).isEmpty()) {
                out.add(h);
            }
        }
        return out;
    }

    private String normalise(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ColumnMapperTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/service/import_/ColumnMapper.java backend/src/test/java/com/hms/service/import_/ColumnMapperTest.java
git commit -m "feat(import): header auto-mapping with unmapped-column reporting"
```

---

## Task 6: Workbook parser

**Files:**

- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/hms/dto/import_/ParsedSheet.java`
- Create: `backend/src/main/java/com/hms/service/import_/WorkbookParser.java`
- Test: `backend/src/test/java/com/hms/service/import_/WorkbookParserTest.java`

- [ ] **Step 1: Add Apache POI to `pom.xml`**

Inside `<dependencies>`:

```xml
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.4.1</version>
        </dependency>
```

Run: `cd backend && mvn -q dependency:resolve`
Expected: resolves without error.

- [ ] **Step 2: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkbookParserTest {

    private final WorkbookParser parser = new WorkbookParser();

    private byte[] xlsx(String sheetName, String[][] rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void readsHeadersAndRowsFromXlsx() throws Exception {
        byte[] data = xlsx("Patients", new String[][]{
                {"Name", "Mob No"},
                {"Ramesh Patel", "9876543210"},
                {"Sita Rao", ""}
        });
        ParsedSheet sheet = parser.parseXlsx(new ByteArrayInputStream(data), "Patients");
        assertThat(sheet.headers()).containsExactly("Name", "Mob No");
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0)).containsEntry("Name", "Ramesh Patel");
        assertThat(sheet.rows().get(1)).containsEntry("Mob No", "");
    }

    @Test
    void blankValuesAreEmptyStringsNotMissingKeys() throws Exception {
        byte[] data = xlsx("Patients", new String[][]{
                {"Name", "Gender"},
                {"Sita Rao", null}
        });
        ParsedSheet sheet = parser.parseXlsx(new ByteArrayInputStream(data), "Patients");
        assertThat(sheet.rows().get(0)).containsKey("Gender");
        assertThat(sheet.rows().get(0).get("Gender")).isEmpty();
    }

    @Test
    void skipsFullyBlankRows() throws Exception {
        byte[] data = xlsx("Patients", new String[][]{
                {"Name"},
                {""},
                {"Sita Rao"}
        });
        ParsedSheet sheet = parser.parseXlsx(new ByteArrayInputStream(data), "Patients");
        assertThat(sheet.rows()).hasSize(1);
    }

    @Test
    void readsCsvIncludingByteOrderMark() {
        String csv = "\uFEFFName,Mob No\nRamesh Patel,9876543210\n";
        ParsedSheet sheet = parser.parseCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(sheet.headers()).containsExactly("Name", "Mob No");
        assertThat(sheet.rows().get(0)).containsEntry("Name", "Ramesh Patel");
    }

    @Test
    void rejectsAFileWithNoHeaderRow() {
        assertThatThrownBy(() -> parser.parseCsv(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header");
    }

    @Test
    void listsSheetNames() throws Exception {
        byte[] data = xlsx("Patients", new String[][]{{"Name"}, {"A"}});
        List<String> names = parser.sheetNames(new ByteArrayInputStream(data));
        assertThat(names).containsExactly("Patients");
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=WorkbookParserTest`
Expected: compilation failure.

- [ ] **Step 4: Create `ParsedSheet`**

```java
package com.hms.dto.import_;

import java.util.List;
import java.util.Map;

/**
 * A sheet reduced to a header list and one map per data row, keyed by header text.
 * Values are always present as strings; a blank cell is "" and never a missing key, so that
 * "blank stays blank" is representable rather than indistinguishable from "column absent".
 */
public record ParsedSheet(
        String sheetName,
        List<String> headers,
        List<Map<String, String>> rows
) { }
```

- [ ] **Step 5: Implement `WorkbookParser`**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads .xlsx and .csv into a ParsedSheet. Values are returned as trimmed strings; interpreting
 * them (dates, numbers) is each EntityImporter's job, because only it knows what a column means.
 */
@Component
public class WorkbookParser {

    /** Guards against zip bombs: an .xlsx is a zip of XML and is attacker-controlled input. */
    static {
        ZipSecureFile.setMinInflateRatio(0.001);
    }

    private static final int MAX_ROWS = 100_000;

    public List<String> sheetNames(InputStream in) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                names.add(wb.getSheetName(i));
            }
            return names;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the workbook: " + e.getMessage());
        }
    }

    public ParsedSheet parseXlsx(InputStream in, String sheetName) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = sheetName == null ? wb.getSheetAt(0) : wb.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName);
            }
            DataFormatter fmt = new DataFormatter();
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext()) {
                throw new IllegalArgumentException("The sheet has no header row.");
            }

            List<String> headers = new ArrayList<>();
            Row headerRow = it.next();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(fmt.formatCellValue(headerRow.getCell(c)).trim());
            }
            if (headers.stream().allMatch(String::isEmpty)) {
                throw new IllegalArgumentException("The sheet has no header row.");
            }

            List<Map<String, String>> rows = new ArrayList<>();
            while (it.hasNext() && rows.size() < MAX_ROWS) {
                Row row = it.next();
                Map<String, String> values = new LinkedHashMap<>();
                boolean anyValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header.isEmpty()) continue;
                    String v = fmt.formatCellValue(row.getCell(c)).trim();
                    values.put(header, v);
                    if (!v.isEmpty()) anyValue = true;
                }
                if (anyValue) rows.add(values);
            }
            return new ParsedSheet(sheet.getSheetName(), headers, rows);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the workbook: " + e.getMessage());
        }
    }

    public ParsedSheet parseCsv(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("The file has no header row.");
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            List<String> headers = splitCsv(headerLine);

            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null && rows.size() < MAX_ROWS) {
                if (line.isBlank()) continue;
                List<String> cells = splitCsv(line);
                Map<String, String> values = new LinkedHashMap<>();
                boolean anyValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header.isEmpty()) continue;
                    String v = c < cells.size() ? cells.get(c) : "";
                    values.put(header, v);
                    if (!v.isEmpty()) anyValue = true;
                }
                if (anyValue) rows.add(values);
            }
            return new ParsedSheet("csv", headers, rows);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the file: " + e.getMessage());
        }
    }

    /** Minimal RFC4180 splitter: handles quoted fields and doubled quotes. */
    private List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }
}
```

- [ ] **Step 6: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=WorkbookParserTest`
Expected: PASS, 6 tests.

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/hms/dto/import_/ParsedSheet.java backend/src/main/java/com/hms/service/import_/WorkbookParser.java backend/src/test/java/com/hms/service/import_/WorkbookParserTest.java
git commit -m "feat(import): xlsx and csv parser with zip-bomb guard and blank preservation"
```

---

## Task 7: `EntityImporter` interface and `PatientImporter`

**Files:**

- Create: `backend/src/main/java/com/hms/dto/import_/RowOutcome.java`
- Create: `backend/src/main/java/com/hms/service/import_/EntityImporter.java`
- Create: `backend/src/main/java/com/hms/service/import_/PatientImporter.java`
- Test: `backend/src/test/java/com/hms/service/import_/PatientImporterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportSource;
import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientImporterTest {

    @Mock PatientRepository patientRepository;

    private PatientImporter importer() {
        return new PatientImporter(patientRepository);
    }

    private Map<String, String> mapping() {
        return Map.of("Name", "name", "Gender", "gender", "Mob No", "phone", "MRN", "legacyId");
    }

    @Test
    void keepsBlankValuesBlankRatherThanRejecting() {
        Map<String, String> row = Map.of("Name", "Sita Rao", "Gender", "", "Mob No", "", "MRN", "A-1");
        RowOutcome outcome = importer().evaluate(row, mapping(), java.util.List.of(), 7L, 2);
        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.CREATE);
        Patient p = outcome.patient();
        assertThat(p.getName()).isEqualTo("Sita Rao");
        assertThat(p.getGender()).isNull();
        assertThat(p.getPhone()).isNull();
        assertThat(p.getSource()).isEqualTo(ImportSource.IMPORTED);
    }

    @Test
    void storesOddlyFormattedPhoneVerbatim() {
        Map<String, String> row = Map.of("Name", "R", "Gender", "M", "Mob No", "+91 98765 43210", "MRN", "A-2");
        RowOutcome outcome = importer().evaluate(row, mapping(), java.util.List.of(), 7L, 2);
        assertThat(outcome.patient().getPhone()).isEqualTo("+91 98765 43210");
    }

    @Test
    void rejectsARowWithNoName() {
        Map<String, String> row = Map.of("Name", "", "MRN", "A-3");
        RowOutcome outcome = importer().evaluate(row, mapping(), java.util.List.of(), 7L, 4);
        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.ERROR);
        assertThat(outcome.message()).contains("Full name");
    }

    @Test
    void reportsAnUnparseableDateAsARowErrorNamingTheColumn() {
        Map<String, String> row = Map.of("Name", "R", "DOB", "31-02-2020");
        RowOutcome outcome = importer().evaluate(row, Map.of("Name", "name", "DOB", "dateOfBirth"),
                java.util.List.of(), 7L, 5);
        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.ERROR);
        assertThat(outcome.columnName()).isEqualTo("DOB");
    }

    @Test
    void matchesAnExistingPatientOnLegacyIdAndUpdatesInsteadOfInserting() {
        Patient existing = new Patient();
        existing.setId(55L);
        existing.setLegacyId("A-9");
        when(patientRepository.findByHospitalIdAndLegacyId(eq(7L), eq("A-9")))
                .thenReturn(Optional.of(existing));

        Map<String, String> row = Map.of("Name", "Updated Name", "MRN", "A-9");
        RowOutcome outcome = importer().evaluate(row, Map.of("Name", "name", "MRN", "legacyId"),
                java.util.List.of(), 7L, 6);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.UPDATE);
        assertThat(outcome.patient().getId()).isEqualTo(55L);
        assertThat(outcome.patient().getName()).isEqualTo("Updated Name");
    }

    @Test
    void preservesUnmappedColumnsAsJsonCustomFields() {
        Map<String, String> row = Map.of("Name", "R", "Referred By", "Dr. Kulkarni", "Caste", "");
        RowOutcome outcome = importer().evaluate(row, Map.of("Name", "name"),
                java.util.List.of("Referred By", "Caste"), 7L, 3);
        assertThat(outcome.patient().getCustomFields())
                .contains("Referred By").contains("Dr. Kulkarni").contains("Caste");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=PatientImporterTest`
Expected: compilation failure.

- [ ] **Step 3: Add the repository lookup method**

In `backend/src/main/java/com/hms/repository/PatientRepository.java`, add:

```java
    java.util.Optional<com.hms.entity.Patient> findByHospitalIdAndLegacyId(Long hospitalId, String legacyId);
```

Note this deliberately does **not** filter on `isActive`, so an undone batch's soft-deleted rows are matched and reactivated on re-import rather than colliding on the unique index (design §5.4).

- [ ] **Step 4: Create `RowOutcome`**

```java
package com.hms.dto.import_;

import com.hms.entity.Patient;

/**
 * The verdict for one parsed row. `patient` is populated for CREATE and UPDATE and is null
 * otherwise. Nothing here is persisted — the engine decides whether this is a dry run.
 */
public record RowOutcome(
        Action action,
        Patient patient,
        String columnName,
        String message
) {
    public enum Action { CREATE, UPDATE, SKIP, ERROR }

    public static RowOutcome create(Patient p) { return new RowOutcome(Action.CREATE, p, null, null); }
    public static RowOutcome update(Patient p) { return new RowOutcome(Action.UPDATE, p, null, null); }
    public static RowOutcome skip(String message) { return new RowOutcome(Action.SKIP, null, null, message); }
    public static RowOutcome error(String column, String message) {
        return new RowOutcome(Action.ERROR, null, column, message);
    }
}
```

- [ ] **Step 5: Create the `EntityImporter` interface**

```java
package com.hms.service.import_;

import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportEntityType;

import java.util.List;
import java.util.Map;

/**
 * One implementation per importable entity. Implementations are pure: evaluate() decides what
 * would happen to a row and never writes. Persisting is ImportEngine's job, which is what lets
 * dry-run and commit share exactly the same logic.
 */
public interface EntityImporter {

    ImportEntityType entityType();

    /**
     * @param row             header -> trimmed cell value, blanks present as ""
     * @param mapping         header -> field key
     * @param unmappedHeaders headers with no field, preserved as custom fields
     * @param hospitalId      always from the JWT, never from the file
     * @param rowNumber       1-based row number in the source file, for error reporting
     */
    RowOutcome evaluate(Map<String, String> row, Map<String, String> mapping,
                        List<String> unmappedHeaders, Long hospitalId, int rowNumber);
}
```

- [ ] **Step 6: Implement `PatientImporter`**

```java
package com.hms.service.import_;

import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportEntityType;
import com.hms.entity.ImportSource;
import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class PatientImporter implements EntityImporter {

    /** Formats seen in real legacy exports, tried in order. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
    );

    private final PatientRepository patientRepository;

    public PatientImporter(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.PATIENT;
    }

    @Override
    public RowOutcome evaluate(Map<String, String> row, Map<String, String> mapping,
                               List<String> unmappedHeaders, Long hospitalId, int rowNumber) {

        Map<String, String> byField = new HashMap<>();
        String nameHeader = null;
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            String value = row.getOrDefault(e.getKey(), "");
            byField.put(e.getValue(), value);
            if ("name".equals(e.getValue())) nameHeader = e.getKey();
        }

        String name = blankToNull(byField.get("name"));
        if (name == null) {
            return RowOutcome.error(nameHeader, "Full name is required and was blank");
        }

        String legacyId = blankToNull(byField.get("legacyId"));

        Patient patient = null;
        boolean isUpdate = false;
        if (legacyId != null) {
            Optional<Patient> existing = patientRepository.findByHospitalIdAndLegacyId(hospitalId, legacyId);
            if (existing.isPresent()) {
                patient = existing.get();
                isUpdate = true;
            }
        }
        if (patient == null) {
            patient = new Patient();
        }

        patient.setHospitalId(hospitalId);
        patient.setName(name);
        patient.setLegacyId(legacyId);
        patient.setSource(ImportSource.IMPORTED);
        patient.setGender(blankToNull(byField.get("gender")));
        patient.setPhone(blankToNull(byField.get("phone")));
        patient.setEmail(blankToNull(byField.get("email")));
        patient.setAddress(blankToNull(byField.get("address")));
        patient.setMedicalHistory(blankToNull(byField.get("medicalHistory")));

        String dob = blankToNull(byField.get("dateOfBirth"));
        if (dob != null) {
            LocalDate parsed = parseDate(dob);
            if (parsed == null) {
                String header = headerFor(mapping, "dateOfBirth");
                return RowOutcome.error(header, "Could not read \"" + dob + "\" as a date");
            }
            patient.setDateOfBirth(parsed);
        }

        patient.setCustomFields(buildCustomFields(row, unmappedHeaders));

        return isUpdate ? RowOutcome.update(patient) : RowOutcome.create(patient);
    }

    private String headerFor(Map<String, String> mapping, String fieldKey) {
        return mapping.entrySet().stream()
                .filter(e -> e.getValue().equals(fieldKey))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, f);
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return null;
    }

    /** Unmapped columns are kept verbatim, blanks included, so nothing from the file is lost. */
    private String buildCustomFields(Map<String, String> row, List<String> unmappedHeaders) {
        if (unmappedHeaders == null || unmappedHeaders.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String header : unmappedHeaders) {
            if (!first) sb.append(",");
            sb.append(jsonString(header)).append(":").append(jsonString(row.getOrDefault(header, "")));
            first = false;
        }
        return sb.append("}").toString();
    }

    private String jsonString(String raw) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append("\"").toString();
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
```

- [ ] **Step 7: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=PatientImporterTest`
Expected: PASS, 6 tests.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/hms/dto/import_/RowOutcome.java backend/src/main/java/com/hms/service/import_/EntityImporter.java backend/src/main/java/com/hms/service/import_/PatientImporter.java backend/src/main/java/com/hms/repository/PatientRepository.java backend/src/test/java/com/hms/service/import_/PatientImporterTest.java
git commit -m "feat(import): PatientImporter with blank preservation, dedupe and custom fields"
```

---

## Task 8: Import engine — dry run

**Files:**

- Create: `backend/src/main/java/com/hms/dto/import_/ImportPreview.java`
- Create: `backend/src/main/java/com/hms/service/import_/ImportEngine.java`
- Test: `backend/src/test/java/com/hms/service/import_/ImportEngineDryRunTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImportEngineDryRunTest {

    @Mock PatientRepository patientRepository;

    @Mock com.hms.repository.ImportRowErrorRepository rowErrorRepository;

    private ImportEngine engine() {
        PatientImporter importer = new PatientImporter(patientRepository);
        return new ImportEngine(List.of(importer), new ColumnMapper(new ImportFieldRegistry()),
                patientRepository, rowErrorRepository);
    }

    private ParsedSheet sheet() {
        return new ParsedSheet("Patients",
                List.of("Name", "DOB", "Referred By"),
                List.of(
                        Map.of("Name", "Ramesh", "DOB", "1990-01-01", "Referred By", "Dr. K"),
                        Map.of("Name", "", "DOB", "", "Referred By", ""),
                        Map.of("Name", "Sita", "DOB", "31-02-2020", "Referred By", "")
                ));
    }

    @Test
    void countsOutcomesWithoutWritingAnything() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);

        assertThat(preview.createCount()).isEqualTo(1);
        assertThat(preview.errorCount()).isEqualTo(2);
        verify(patientRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(patientRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void errorsCarryRowNumbersAndColumnNames() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);

        assertThat(preview.errors()).extracting("rowNumber").containsExactly(2, 3);
        assertThat(preview.errors().get(1).columnName()).isEqualTo("DOB");
    }

    @Test
    void reportsWhichColumnsWillBecomeCustomFields() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);
        assertThat(preview.unmappedHeaders()).containsExactly("Referred By");
    }

    @Test
    void warnsWhenNoLegacyIdIsMappedBecauseReuploadCannotMatch() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);
        assertThat(preview.warnings()).anyMatch(w -> w.toLowerCase().contains("re-upload"));
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ImportEngineDryRunTest`
Expected: compilation failure.

- [ ] **Step 3: Create `ImportPreview`**

```java
package com.hms.dto.import_;

import java.util.List;

public record ImportPreview(
        int totalRows,
        int createCount,
        int updateCount,
        int skipCount,
        int errorCount,
        List<String> unmappedHeaders,
        List<String> warnings,
        List<PreviewError> errors,
        List<String> sampleNames
) {
    public record PreviewError(int rowNumber, String columnName, String message) { }
}
```

- [ ] **Step 4: Implement `ImportEngine.dryRun`**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportEntityType;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Orchestrates a dry run and (Task 9) a commit. dryRun() writes nothing at all — that is the
 * safety property the whole feature rests on, and it is asserted directly in the tests.
 */
@Service
public class ImportEngine {

    private static final int SAMPLE_SIZE = 10;
    private static final int MAX_PREVIEW_ERRORS = 500;

    private final Map<ImportEntityType, EntityImporter> importers = new EnumMap<>(ImportEntityType.class);
    private final ColumnMapper columnMapper;
    private final com.hms.repository.PatientRepository patientRepository;
    private final com.hms.repository.ImportRowErrorRepository rowErrorRepository;

    public ImportEngine(List<EntityImporter> entityImporters,
                        ColumnMapper columnMapper,
                        com.hms.repository.PatientRepository patientRepository,
                        com.hms.repository.ImportRowErrorRepository rowErrorRepository) {
        for (EntityImporter i : entityImporters) {
            importers.put(i.entityType(), i);
        }
        this.columnMapper = columnMapper;
        this.patientRepository = patientRepository;
        this.rowErrorRepository = rowErrorRepository;
    }

    public EntityImporter importerFor(ImportEntityType type) {
        EntityImporter i = importers.get(type);
        if (i == null) {
            throw new IllegalArgumentException("No importer is available for " + type);
        }
        return i;
    }

    public ImportPreview dryRun(ParsedSheet sheet, Map<String, String> mapping, Long hospitalId) {
        EntityImporter importer = importerFor(ImportEntityType.PATIENT);
        List<String> unmapped = columnMapper.unmapped(sheet.headers(), mapping);

        int create = 0, update = 0, skip = 0, error = 0;
        List<ImportPreview.PreviewError> errors = new ArrayList<>();
        List<String> samples = new ArrayList<>();

        int rowNumber = 1; // row 1 is the header
        for (Map<String, String> row : sheet.rows()) {
            rowNumber++;
            RowOutcome outcome = importer.evaluate(row, mapping, unmapped, hospitalId, rowNumber);
            switch (outcome.action()) {
                case CREATE -> {
                    create++;
                    if (samples.size() < SAMPLE_SIZE) samples.add(outcome.patient().getName());
                }
                case UPDATE -> update++;
                case SKIP -> skip++;
                case ERROR -> {
                    error++;
                    if (errors.size() < MAX_PREVIEW_ERRORS) {
                        errors.add(new ImportPreview.PreviewError(
                                rowNumber, outcome.columnName(), outcome.message()));
                    }
                }
            }
        }

        List<String> warnings = new ArrayList<>();
        if (!mapping.containsValue("legacyId")) {
            warnings.add("No old patient ID (MRN) column is mapped. Re-uploading this file after "
                    + "committing cannot match existing records and would create duplicates. Correct "
                    + "any problems before committing, or re-upload only the failed rows.");
        }
        if (!unmapped.isEmpty()) {
            warnings.add(unmapped.size() + " column(s) will be preserved as Imported information: "
                    + String.join(", ", unmapped));
        }

        return new ImportPreview(sheet.rows().size(), create, update, skip, error,
                unmapped, warnings, errors, samples);
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ImportEngineDryRunTest`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/dto/import_/ImportPreview.java backend/src/main/java/com/hms/service/import_/ImportEngine.java backend/src/test/java/com/hms/service/import_/ImportEngineDryRunTest.java
git commit -m "feat(import): dry-run preview that writes nothing"
```

---

## Task 9: Import engine — commit with batch lineage

**Files:**

- Modify: `backend/src/main/java/com/hms/service/import_/ImportEngine.java`
- Test: `backend/src/test/java/com/hms/service/import_/ImportEngineCommitTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.*;
import com.hms.repository.ImportRowErrorRepository;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportEngineCommitTest {

    @Mock PatientRepository patientRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;

    private ImportEngine engine() {
        return new ImportEngine(List.of(new PatientImporter(patientRepository)),
                new ColumnMapper(new ImportFieldRegistry()), patientRepository, rowErrorRepository);
    }

    private ImportBatch batch() {
        ImportBatch b = new ImportBatch();
        b.setId(17L);
        b.setHospitalId(7L);
        b.setEntityType(ImportEntityType.PATIENT);
        return b;
    }

    private ParsedSheet sheet() {
        return new ParsedSheet("Patients", List.of("Name"),
                List.of(Map.of("Name", "Ramesh"), Map.of("Name", ""), Map.of("Name", "Sita")));
    }

    @Test
    void stampsEverySavedRowWithTheBatchIdAndHospitalFromTheJwt() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        engine().commit(sheet(), Map.of("Name", "name"), batch());

        ArgumentCaptor<List<Patient>> captor = ArgumentCaptor.forClass(List.class);
        verify(patientRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(p -> {
            assertThat(p.getImportBatchId()).isEqualTo(17L);
            assertThat(p.getHospitalId()).isEqualTo(7L);
            assertThat(p.getSource()).isEqualTo(ImportSource.IMPORTED);
        });
    }

    @Test
    void oneBadRowDoesNotStopTheRest() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ImportBatch b = batch();
        engine().commit(sheet(), Map.of("Name", "name"), b);

        assertThat(b.getCreatedCount()).isEqualTo(2);
        assertThat(b.getFailedCount()).isEqualTo(1);
        assertThat(b.getStatus()).isEqualTo(ImportStatus.COMPLETED);
    }

    @Test
    void failedRowsArePersistedAsRowErrors() {
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        engine().commit(sheet(), Map.of("Name", "name"), batch());

        ArgumentCaptor<List<ImportRowError>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowErrorRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRowNumber()).isEqualTo(3);
        assertThat(captor.getValue().get(0).getRawRowJson()).contains("Name");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ImportEngineCommitTest`
Expected: compilation failure — `commit` does not exist.

- [ ] **Step 3: Add `commit` to `ImportEngine`**

Add these imports at the top of `ImportEngine.java`:

```java
import com.hms.entity.ImportBatch;
import com.hms.entity.ImportRowError;
import com.hms.entity.ImportStatus;
import com.hms.entity.Patient;
import com.hms.repository.ImportRowErrorRepository;
import com.hms.repository.PatientRepository;
import java.time.LocalDateTime;
```

Add the constant and method:

```java
    /** Rows per transaction. Bounds memory and limits how much a mid-run crash leaves half-done. */
    private static final int CHUNK_SIZE = 500;

    /**
     * Applies the sheet for real. Row-level problems are recorded and the run continues — one bad
     * row out of 8,000 must not cost the other 7,999. Counts land on the batch.
     */
    public void commit(ParsedSheet sheet, Map<String, String> mapping, ImportBatch batch) {

        EntityImporter importer = importerFor(batch.getEntityType());
        List<String> unmapped = columnMapper.unmapped(sheet.headers(), mapping);

        batch.setStatus(ImportStatus.RUNNING);
        batch.setTotalRows(sheet.rows().size());

        List<Patient> pending = new ArrayList<>();
        List<ImportRowError> rowErrors = new ArrayList<>();
        int created = 0, updated = 0, skipped = 0, failed = 0;

        int rowNumber = 1;
        for (Map<String, String> row : sheet.rows()) {
            rowNumber++;
            RowOutcome outcome;
            try {
                outcome = importer.evaluate(row, mapping, unmapped, batch.getHospitalId(), rowNumber);
            } catch (Exception e) {
                outcome = RowOutcome.error(null, "Unexpected problem: " + e.getMessage());
            }

            switch (outcome.action()) {
                case CREATE -> {
                    Patient p = outcome.patient();
                    p.setImportBatchId(batch.getId());
                    p.setIsActive(true);
                    pending.add(p);
                    created++;
                }
                case UPDATE -> {
                    Patient p = outcome.patient();
                    p.setImportBatchId(batch.getId());
                    p.setIsActive(true);
                    pending.add(p);
                    updated++;
                }
                case SKIP -> skipped++;
                case ERROR -> {
                    failed++;
                    rowErrors.add(new ImportRowError(batch.getId(), rowNumber,
                            outcome.columnName(), outcome.message(), toJson(row)));
                }
            }

            if (pending.size() >= CHUNK_SIZE) {
                patientRepository.saveAll(pending);
                pending.clear();
            }
        }

        if (!pending.isEmpty()) {
            patientRepository.saveAll(pending);
        }
        if (!rowErrors.isEmpty()) {
            rowErrorRepository.saveAll(rowErrors);
        }

        batch.setCreatedCount(created);
        batch.setUpdatedCount(updated);
        batch.setSkippedCount(skipped);
        batch.setFailedCount(failed);
        batch.setCommittedAt(LocalDateTime.now());
        batch.setStatus(ImportStatus.COMPLETED);
    }

    private String toJson(Map<String, String> row) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (!first) sb.append(",");
            sb.append(quote(e.getKey())).append(":").append(quote(e.getValue()));
            first = false;
        }
        return sb.append("}").toString();
    }

    private String quote(String raw) {
        String escaped = raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ImportEngineCommitTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/service/import_/ImportEngine.java backend/src/test/java/com/hms/service/import_/ImportEngineCommitTest.java
git commit -m "feat(import): chunked commit with batch lineage and per-row error capture"
```

---

## Task 10: Undo with the clinical-activity guard

**Files:**

- Create: `backend/src/main/java/com/hms/service/import_/ImportBatchService.java`
- Test: `backend/src/test/java/com/hms/service/import_/ImportBatchServiceUndoTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportBatchServiceUndoTest {

    @Mock ImportBatchRepository batchRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;
    @Mock PatientRepository patientRepository;
    @Mock OpdRepository opdRepository;
    @Mock BillingRepository billingRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;

    @InjectMocks ImportBatchService service;

    private ImportBatch completedBatch() {
        ImportBatch b = new ImportBatch();
        b.setId(17L);
        b.setPublicId("pub-17");
        b.setHospitalId(7L);
        b.setEntityType(ImportEntityType.PATIENT);
        b.setStatus(ImportStatus.COMPLETED);
        return b;
    }

    private Patient imported(Long id) {
        Patient p = new Patient();
        p.setId(id);
        p.setHospitalId(7L);
        p.setImportBatchId(17L);
        p.setIsActive(true);
        return p;
    }

    @Test
    void softDeletesImportedPatientsAndMarksTheBatchUndone() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.findByPublicIdAndHospitalId("pub-17", 7L))
                .thenReturn(Optional.of(completedBatch()));
        when(patientRepository.findByImportBatchId(17L)).thenReturn(List.of(imported(1L), imported(2L)));
        when(opdRepository.countByPatientIdIn(anyList())).thenReturn(0L);
        when(billingRepository.countByPatientIdIn(anyList())).thenReturn(0L);

        ImportBatch result = service.undo("pub-17");

        verify(patientRepository).saveAll(argThat((List<Patient> list) ->
                list.size() == 2 && list.stream().noneMatch(Patient::getIsActive)));
        assertThat(result.getStatus()).isEqualTo(ImportStatus.UNDONE);
        assertThat(result.getUndoneAt()).isNotNull();
        verify(patientRepository, never()).deleteAll(anyList());
    }

    @Test
    void refusesWhenAnImportedPatientHasRealClinicalActivity() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.findByPublicIdAndHospitalId("pub-17", 7L))
                .thenReturn(Optional.of(completedBatch()));
        when(patientRepository.findByImportBatchId(17L)).thenReturn(List.of(imported(1L)));
        when(opdRepository.countByPatientIdIn(anyList())).thenReturn(3L);

        assertThatThrownBy(() -> service.undo("pub-17"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clinical");

        verify(patientRepository, never()).saveAll(anyList());
    }

    @Test
    void refusesToUndoABatchTwice() {
        ImportBatch b = completedBatch();
        b.setStatus(ImportStatus.UNDONE);
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.findByPublicIdAndHospitalId("pub-17", 7L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.undo("pub-17"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotTouchAnotherHospitalsBatch() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(99L);
        when(batchRepository.findByPublicIdAndHospitalId("pub-17", 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.undo("pub-17"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ImportBatchServiceUndoTest`
Expected: compilation failure.

- [ ] **Step 3: Add the repository lookups**

In `PatientRepository.java`:

```java
    java.util.List<com.hms.entity.Patient> findByImportBatchId(Long importBatchId);
```

In `OpdRepository.java`:

```java
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(o) FROM Opd o WHERE o.patientId IN :patientIds")
    long countByPatientIdIn(@org.springframework.data.repository.query.Param("patientIds") java.util.List<Long> patientIds);
```

In `BillingRepository.java`:

```java
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(b) FROM Billing b WHERE b.patientId IN :patientIds")
    long countByPatientIdIn(@org.springframework.data.repository.query.Param("patientIds") java.util.List<Long> patientIds);
```

If the `Opd` or `Billing` entity names its patient reference something other than `patientId`, adjust the JPQL property name to match — check the entity before writing the query.

- [ ] **Step 4: Implement `ImportBatchService`**

```java
package com.hms.service.import_;

import com.hms.entity.ImportBatch;
import com.hms.entity.ImportStatus;
import com.hms.entity.Patient;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ImportBatchService {

    private final ImportBatchRepository batchRepository;
    private final ImportRowErrorRepository rowErrorRepository;
    private final PatientRepository patientRepository;
    private final OpdRepository opdRepository;
    private final BillingRepository billingRepository;
    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;

    public ImportBatchService(ImportBatchRepository batchRepository,
                              ImportRowErrorRepository rowErrorRepository,
                              PatientRepository patientRepository,
                              OpdRepository opdRepository,
                              BillingRepository billingRepository,
                              SecurityContextHelper securityHelper,
                              AuditLogService auditLogService) {
        this.batchRepository = batchRepository;
        this.rowErrorRepository = rowErrorRepository;
        this.patientRepository = patientRepository;
        this.opdRepository = opdRepository;
        this.billingRepository = billingRepository;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
    }

    public ImportBatch requireOwnBatch(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return batchRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Import not found"));
    }

    /**
     * Reverses a batch by soft-deleting the rows it created. Never a hard delete: the records may
     * be referenced elsewhere, and a soft delete keeps legacy_id in place so a corrected re-import
     * matches and reactivates them instead of colliding on the unique index.
     */
    @Transactional
    public ImportBatch undo(String publicId) {
        ImportBatch batch = requireOwnBatch(publicId);

        if (!batch.isUndoable()) {
            throw new IllegalArgumentException(
                    "This import cannot be undone (status: " + batch.getStatus() + ").");
        }

        List<Patient> imported = patientRepository.findByImportBatchId(batch.getId());
        if (!imported.isEmpty()) {
            List<Long> ids = imported.stream().map(Patient::getId).toList();
            long visits = opdRepository.countByPatientIdIn(ids);
            long bills = visits > 0 ? 0 : billingRepository.countByPatientIdIn(ids);
            if (visits > 0 || bills > 0) {
                throw new IllegalArgumentException(
                        "Cannot undo: " + (visits > 0 ? visits + " visit(s)" : bills + " bill(s)")
                        + " have been recorded against patients from this import. Undoing would "
                        + "delete live clinical data. Remove or reassign those records first.");
            }
            imported.forEach(p -> p.setIsActive(false));
            patientRepository.saveAll(imported);
        }

        batch.setStatus(ImportStatus.UNDONE);
        batch.setUndoneAt(LocalDateTime.now());
        batchRepository.save(batch);

        try {
            auditLogService.logAction("IMPORT_UNDO",
                    "Reversed import batch " + batch.getPublicId() + " (" + imported.size() + " patients)",
                    securityHelper.getCurrentUserEmail(), batch.getHospitalId(),
                    "ImportBatch", batch.getId(), null);
        } catch (Exception ignored) {
            // audit logging is best-effort by convention in this codebase
        }

        return batch;
    }

    public List<ImportBatch> listBatches() {
        return batchRepository.findByHospitalIdOrderByCreatedAtDesc(securityHelper.getCurrentHospitalId());
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ImportBatchServiceUndoTest`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/service/import_/ImportBatchService.java backend/src/main/java/com/hms/repository/PatientRepository.java backend/src/main/java/com/hms/repository/OpdRepository.java backend/src/main/java/com/hms/repository/BillingRepository.java backend/src/test/java/com/hms/service/import_/ImportBatchServiceUndoTest.java
git commit -m "feat(import): undo via soft delete, refused when clinical activity exists"
```

---

## Task 11: Error CSV export, safe to open in Excel

**Files:**

- Modify: `backend/src/main/java/com/hms/service/import_/ImportBatchService.java`
- Test: `backend/src/test/java/com/hms/service/import_/ImportErrorCsvTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.entity.ImportRowError;
import com.hms.repository.ImportRowErrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportErrorCsvTest {

    @Mock ImportRowErrorRepository rowErrorRepository;

    @Test
    void producesReuploadableRowsWithATrailingErrorColumn() {
        when(rowErrorRepository.findByBatchIdOrderByRowNumberAsc(17L)).thenReturn(List.of(
                new ImportRowError(17L, 5, "DOB", "Could not read \"31-02-2020\" as a date",
                        "{\"Name\":\"Sita\",\"DOB\":\"31-02-2020\"}")));

        String csv = ImportCsvWriter.write(rowErrorRepository.findByBatchIdOrderByRowNumberAsc(17L),
                List.of("Name", "DOB"));

        assertThat(csv.lines().toList().get(0)).isEqualTo("Name,DOB,_error");
        assertThat(csv).contains("Sita");
        assertThat(csv).contains("31-02-2020");
        assertThat(csv).contains("DOB: Could not read");
    }

    @Test
    void neutralisesFormulaInjection() {
        when(rowErrorRepository.findByBatchIdOrderByRowNumberAsc(17L)).thenReturn(List.of(
                new ImportRowError(17L, 2, "Name", "bad", "{\"Name\":\"=cmd|'/c calc'!A1\"}")));

        String csv = ImportCsvWriter.write(rowErrorRepository.findByBatchIdOrderByRowNumberAsc(17L),
                List.of("Name"));

        assertThat(csv).doesNotContain(",=cmd").doesNotContain("\n=cmd");
        assertThat(csv).contains("'=cmd");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ImportErrorCsvTest`
Expected: compilation failure — `ImportCsvWriter` does not exist.

- [ ] **Step 3: Create `ImportCsvWriter`**

Create `backend/src/main/java/com/hms/service/import_/ImportCsvWriter.java`:

```java
package com.hms.service.import_;

import com.hms.entity.ImportRowError;

import java.util.List;
import java.util.Map;

/**
 * Renders failed rows back into a CSV shaped for re-upload: the original headers, the original
 * values, plus a trailing _error column. Fixing those rows and uploading just this file leaves
 * everything that already imported untouched.
 */
public final class ImportCsvWriter {

    private ImportCsvWriter() { }

    public static String write(List<ImportRowError> errors, List<String> headers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(cell(headers.get(i)));
        }
        sb.append(",_error\n");

        for (ImportRowError e : errors) {
            Map<String, String> row = parseFlatJson(e.getRawRowJson());
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(cell(row.getOrDefault(headers.get(i), "")));
            }
            String msg = (e.getColumnName() == null ? "" : e.getColumnName() + ": ") + e.getMessage();
            sb.append(",").append(cell(msg)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Escapes for CSV and defuses spreadsheet formula injection. A legacy field beginning =, +, -
     * or @ is executable when opened in Excel, so it is prefixed with an apostrophe.
     */
    private static String cell(String raw) {
        String v = raw == null ? "" : raw;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Reads the flat {"k":"v"} objects written by ImportEngine.toJson. */
    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (json == null || json.length() < 2) return out;
        String body = json.substring(1, json.length() - 1);
        boolean inQuotes = false, escaped = false, readingKey = true;
        StringBuilder key = new StringBuilder(), val = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (escaped) {
                (readingKey ? key : val).append(unescape(c));
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && c == ':') {
                readingKey = false;
            } else if (!inQuotes && c == ',') {
                out.put(key.toString(), val.toString());
                key.setLength(0);
                val.setLength(0);
                readingKey = true;
            } else {
                (readingKey ? key : val).append(c);
            }
        }
        if (key.length() > 0) out.put(key.toString(), val.toString());
        return out;
    }

    private static char unescape(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> c;
        };
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ImportErrorCsvTest`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/service/import_/ImportCsvWriter.java backend/src/test/java/com/hms/service/import_/ImportErrorCsvTest.java
git commit -m "feat(import): re-uploadable error CSV with formula-injection escaping"
```

---

## Task 12: REST controller and tenant-isolation test

**Files:**

- Create: `backend/src/main/java/com/hms/controller/hospital/ImportController.java`
- Test: `backend/src/test/java/com/hms/controller/hospital/ImportControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.controller.hospital;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.import_.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {

    @Mock PatientRepository patientRepository;
    @Mock ImportBatchRepository batchRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock ImportBatchService batchService;

    private ImportController controller() {
        ImportEngine engine = new ImportEngine(List.of(new PatientImporter(patientRepository)),
                new ColumnMapper(new ImportFieldRegistry()), patientRepository, rowErrorRepository);
        return new ImportController(engine, new WorkbookParser(), new ColumnMapper(new ImportFieldRegistry()),
                new ImportFieldRegistry(), batchService, batchRepository, rowErrorRepository, securityHelper);
    }

    @Test
    void previewIgnoresAHospitalIdColumnInTheFileAndUsesTheJwt() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);

        ParsedSheet sheet = new ParsedSheet("Patients",
                List.of("Name", "hospital_id"),
                List.of(Map.of("Name", "Ramesh", "hospital_id", "999")));

        ImportPreview preview = controller().previewSheet(sheet, Map.of("Name", "name"));

        assertThat(preview.createCount()).isEqualTo(1);
        // hospital_id is not a mappable field, so it is preserved as data, never used as tenancy
        assertThat(preview.unmappedHeaders()).contains("hospital_id");
    }

    @Test
    void rejectsAnUnsupportedFileExtension() {
        MockMultipartFile bad = new MockMultipartFile(
                "file", "patients.exe", "application/octet-stream", new byte[]{1, 2, 3});

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller().upload(bad, "PATIENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ImportControllerTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `ImportController`**

```java
package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.import_.ImportFieldDef;
import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.ImportBatch;
import com.hms.entity.ImportEntityType;
import com.hms.entity.ImportRowError;
import com.hms.repository.ImportBatchRepository;
import com.hms.repository.ImportRowErrorRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.import_.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Import API. hospital_id always comes from the JWT — a hospital_id column present in an uploaded
 * file is treated as ordinary data and never as tenancy, or a crafted spreadsheet would become a
 * cross-tenant write.
 */
@RestController
@RequestMapping({"/hospital/imports", "/clinic/imports"})
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class ImportController {

    private static final long MAX_BYTES = 50L * 1024 * 1024;

    private final ImportEngine engine;
    private final WorkbookParser parser;
    private final ColumnMapper columnMapper;
    private final ImportFieldRegistry fieldRegistry;
    private final ImportBatchService batchService;
    private final ImportBatchRepository batchRepository;
    private final ImportRowErrorRepository rowErrorRepository;
    private final SecurityContextHelper securityHelper;

    public ImportController(ImportEngine engine, WorkbookParser parser, ColumnMapper columnMapper,
                            ImportFieldRegistry fieldRegistry, ImportBatchService batchService,
                            ImportBatchRepository batchRepository,
                            ImportRowErrorRepository rowErrorRepository,
                            SecurityContextHelper securityHelper) {
        this.engine = engine;
        this.parser = parser;
        this.columnMapper = columnMapper;
        this.fieldRegistry = fieldRegistry;
        this.batchService = batchService;
        this.batchRepository = batchRepository;
        this.rowErrorRepository = rowErrorRepository;
        this.securityHelper = securityHelper;
    }

    @GetMapping("/fields")
    public ResponseEntity<ApiResponse<List<ImportFieldDef>>> fields(
            @RequestParam(defaultValue = "PATIENT") String entityType) {
        return ResponseEntity.ok(ApiResponse.ok(
                fieldRegistry.fieldsFor(ImportEntityType.valueOf(entityType))));
    }

    /** Parses the upload and returns headers, suggested mapping and sample rows. Writes nothing. */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "PATIENT") String entityType) throws IOException {

        validateUpload(file);
        ImportEntityType type = ImportEntityType.valueOf(entityType);
        ParsedSheet sheet = readSheet(file, null);
        Map<String, String> suggested = columnMapper.suggest(sheet.headers(), type);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "sheetName", sheet.sheetName(),
                "headers", sheet.headers(),
                "suggestedMapping", suggested,
                "unmapped", columnMapper.unmapped(sheet.headers(), suggested),
                "totalRows", sheet.rows().size(),
                "sampleRows", sheet.rows().stream().limit(10).toList())));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<ImportPreview>> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> mapping) throws IOException {

        validateUpload(file);
        ParsedSheet sheet = readSheet(file, null);
        return ResponseEntity.ok(ApiResponse.ok(previewSheet(sheet, mapping)));
    }

    /** Extracted so the tenant-isolation test can drive it without building a multipart request. */
    ImportPreview previewSheet(ParsedSheet sheet, Map<String, String> mapping) {
        return engine.dryRun(sheet, mapping, securityHelper.getCurrentHospitalId());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ImportBatch>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(batchService.listBatches()));
    }

    @PostMapping("/{publicId}/undo")
    public ResponseEntity<ApiResponse<ImportBatch>> undo(@PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok("Import reversed", batchService.undo(publicId)));
    }

    @GetMapping("/{publicId}/errors.csv")
    public ResponseEntity<byte[]> errorsCsv(@PathVariable String publicId,
                                            @RequestParam List<String> headers) {
        ImportBatch batch = batchService.requireOwnBatch(publicId);
        List<ImportRowError> errors = rowErrorRepository.findByBatchIdOrderByRowNumberAsc(batch.getId());
        byte[] body = ImportCsvWriter.write(errors, headers).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import-errors.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    private ParsedSheet readSheet(MultipartFile file, String sheetName) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".csv")) {
            return parser.parseCsv(file.getInputStream());
        }
        return parser.parseXlsx(file.getInputStream(), sheetName);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File is too large. Maximum size is 50 MB.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".csv")) {
            throw new IllegalArgumentException("Only .xlsx and .csv files are allowed.");
        }
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ImportControllerTest`
Expected: PASS, 2 tests.

- [ ] **Step 5: Raise the multipart limit for this endpoint only**

In `backend/src/main/resources/application.properties`, add a dedicated override so the global 5 MB cap is unchanged for every other endpoint:

```properties
# Imports stream large workbooks; the global cap above stays as-is for all other uploads.
spring.servlet.multipart.max-file-size=${MAX_UPLOAD_FILE_SIZE:5MB}
spring.servlet.multipart.max-request-size=${MAX_UPLOAD_REQUEST_SIZE:6MB}
hms.import.max-file-size=${IMPORT_MAX_FILE_SIZE:50MB}
```

Then register a `MultipartConfigElement` scoped to the import path, or set
`MAX_UPLOAD_FILE_SIZE=50MB` in the deployment `.env` if a single global limit is acceptable
operationally. **Record whichever you choose in `docs/deployment/DEPLOYMENT.md`.**

- [ ] **Step 6: Run the full suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hms/controller/hospital/ImportController.java backend/src/main/resources/application.properties backend/src/test/java/com/hms/controller/hospital/ImportControllerTest.java
git commit -m "feat(import): admin REST surface with tenant isolation and 50MB cap"
```

---

## Task 13: Messy real-world fixture test

The single most valuable test in this plan — it models actual acquisition data.

**Files:**

- Test: `backend/src/test/java/com/hms/service/import_/MessyLegacyFileTest.java`

- [ ] **Step 1: Write the test**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MessyLegacyFileTest {

    @Mock PatientRepository patientRepository;

    private Map<String, String> row(String name, String gender, String phone, String dob,
                                    String mrn, String referredBy) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Patient Name", name);
        m.put("Sex", gender);
        m.put("Mob No", phone);
        m.put("DOB", dob);
        m.put("MRN", mrn);
        m.put("Referred By", referredBy);
        return m;
    }

    @Test
    void handlesRealAcquisitionDataWithoutLosingAnything() {
        ParsedSheet sheet = new ParsedSheet("Patients",
                List.of("Patient Name", "Sex", "Mob No", "DOB", "MRN", "Referred By"),
                List.of(
                        row("Ramesh Patel", "M", "9876543210", "1985-04-11", "A-1", "Dr. Kulkarni"),
                        row("Sita Rao", "", "", "", "A-2", ""),
                        row("Anil Kumar", "Male", "+91 98765 43210", "12/03/1977", "A-3", ""),
                        row("", "F", "9999999999", "2000-01-01", "A-4", ""),
                        row("Meena Shah", "F", "022-24445555", "31-02-2020", "A-5", "Walk-in")
                ));

        ImportEngine engine = new ImportEngine(
                List.of(new PatientImporter(patientRepository)),
                new ColumnMapper(new ImportFieldRegistry()));

        Map<String, String> mapping = Map.of(
                "Patient Name", "name", "Sex", "gender", "Mob No", "phone",
                "DOB", "dateOfBirth", "MRN", "legacyId");

        ImportPreview preview = engine.dryRun(sheet, mapping, 7L);

        // 3 good rows; row 5 has no name; row 6 has an impossible date
        assertThat(preview.createCount()).isEqualTo(3);
        assertThat(preview.errorCount()).isEqualTo(2);

        // blanks and odd formats do not fail
        assertThat(preview.errors()).extracting("rowNumber").containsExactly(5, 6);

        // the unknown column is preserved, not dropped
        assertThat(preview.unmappedHeaders()).containsExactly("Referred By");

        // MRN mapped, so no re-upload warning
        assertThat(preview.warnings()).noneMatch(w -> w.toLowerCase().contains("re-upload"));
    }
}
```

- [ ] **Step 2: Run it**

Run: `cd backend && mvn test -Dtest=MessyLegacyFileTest`
Expected: PASS. If `createCount` is 2 rather than 3, the landline row (`022-24445555`) is being rejected — the importer must store phones verbatim, so fix `PatientImporter` rather than the test.

- [ ] **Step 3: Run the whole suite and check coverage still passes**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS, JaCoCo coverage floor met.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/hms/service/import_/MessyLegacyFileTest.java
git commit -m "test(import): end-to-end fixture modelled on real acquisition data"
```

---

## Task 14: Commit endpoint and batch lifecycle

Tasks 9 and 12 left a hole: `ImportEngine.commit` exists but nothing creates an `ImportBatch` or exposes commit over HTTP. This closes it.

**Files:**

- Modify: `backend/src/main/java/com/hms/service/import_/ImportBatchService.java`
- Modify: `backend/src/main/java/com/hms/controller/hospital/ImportController.java`
- Test: `backend/src/test/java/com/hms/service/import_/ImportBatchServiceCommitTest.java`

**Design note — no PHI at rest.** The uploaded file is re-sent for preview and again for commit rather than stored on disk. This removes the spec's temp-file handling and its 30-day retention entirely: the patient spreadsheet never lands on the server's filesystem, so it cannot be leaked from there or forgotten. The trade-off is one extra upload of the same file, which is acceptable for an operation run a handful of times per hospital.

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportBatchServiceCommitTest {

    @Mock ImportBatchRepository batchRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;
    @Mock PatientRepository patientRepository;
    @Mock OpdRepository opdRepository;
    @Mock BillingRepository billingRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;

    private ImportBatchService service() {
        ImportEngine engine = new ImportEngine(List.of(new PatientImporter(patientRepository)),
                new ColumnMapper(new ImportFieldRegistry()), patientRepository, rowErrorRepository);
        return new ImportBatchService(batchRepository, rowErrorRepository, patientRepository,
                opdRepository, billingRepository, securityHelper, auditLogService, engine);
    }

    private ParsedSheet sheet() {
        return new ParsedSheet("Patients", List.of("Name"),
                List.of(Map.of("Name", "Ramesh"), Map.of("Name", "Sita")));
    }

    @Test
    void createsAPersistedBatchStampedWithTheJwtHospital() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@h.com");
        when(batchRepository.existsByHospitalIdAndStatus(7L, ImportStatus.RUNNING)).thenReturn(false);
        when(batchRepository.save(any(ImportBatch.class))).thenAnswer(inv -> {
            ImportBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(17L);
            return b;
        });
        when(patientRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ImportBatch batch = service().commit(sheet(), Map.of("Name", "name"), "patients.xlsx");

        assertThat(batch.getHospitalId()).isEqualTo(7L);
        assertThat(batch.getCreatedBy()).isEqualTo("admin@h.com");
        assertThat(batch.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(batch.getCreatedCount()).isEqualTo(2);
        verify(auditLogService).logAction(eq("IMPORT_COMMIT"), anyString(), eq("admin@h.com"),
                eq(7L), eq("ImportBatch"), eq(17L), isNull());
    }

    @Test
    void refusesASecondConcurrentImportForTheSameHospital() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.existsByHospitalIdAndStatus(7L, ImportStatus.RUNNING)).thenReturn(true);

        assertThatThrownBy(() -> service().commit(sheet(), Map.of("Name", "name"), "patients.xlsx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void marksTheBatchFailedIfTheEngineThrows() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(batchRepository.existsByHospitalIdAndStatus(7L, ImportStatus.RUNNING)).thenReturn(false);
        when(batchRepository.save(any(ImportBatch.class))).thenAnswer(inv -> {
            ImportBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(17L);
            return b;
        });
        when(patientRepository.saveAll(anyList())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service().commit(sheet(), Map.of("Name", "name"), "patients.xlsx"))
                .isInstanceOf(RuntimeException.class);

        verify(batchRepository, atLeastOnce()).save(argThat((ImportBatch b) ->
                b.getStatus() == ImportStatus.FAILED));
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=ImportBatchServiceCommitTest`
Expected: compilation failure — `ImportBatchService` has no `commit` and its constructor takes 7 arguments, not 8.

- [ ] **Step 3: Add `ImportEngine` to `ImportBatchService` and implement `commit`**

Add the field and constructor parameter:

```java
    private final ImportEngine importEngine;
```

Append `ImportEngine importEngine` as the final constructor parameter and assign `this.importEngine = importEngine;`.

Add the method:

```java
    /**
     * Creates a batch, applies the sheet, and records the outcome. The batch is saved before the
     * run so that every written row has a real import_batch_id to point at — that lineage is the
     * only thing undo has to work with.
     */
    @Transactional
    public ImportBatch commit(com.hms.dto.import_.ParsedSheet sheet,
                              java.util.Map<String, String> mapping,
                              String sourceFilename) {

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (batchRepository.existsByHospitalIdAndStatus(hospitalId, ImportStatus.RUNNING)) {
            throw new IllegalArgumentException(
                    "An import is already running for this hospital. Wait for it to finish before starting another.");
        }

        ImportBatch batch = new ImportBatch();
        batch.setHospitalId(hospitalId);
        batch.setEntityType(com.hms.entity.ImportEntityType.PATIENT);
        batch.setSourceFilename(sourceFilename);
        batch.setSheetName(sheet.sheetName());
        batch.setMappingJson(mappingToJson(mapping));
        batch.setCreatedBy(securityHelper.getCurrentUserEmail());
        batch.setStatus(ImportStatus.RUNNING);
        batch = batchRepository.save(batch);

        try {
            importEngine.commit(sheet, mapping, batch);
        } catch (RuntimeException e) {
            batch.setStatus(ImportStatus.FAILED);
            batchRepository.save(batch);
            throw e;
        }

        batchRepository.save(batch);

        try {
            auditLogService.logAction("IMPORT_COMMIT",
                    "Imported " + batch.getCreatedCount() + " new and " + batch.getUpdatedCount()
                            + " updated patient(s) from " + sourceFilename
                            + "; " + batch.getFailedCount() + " row(s) failed",
                    batch.getCreatedBy(), hospitalId, "ImportBatch", batch.getId(), null);
        } catch (Exception ignored) {
            // audit logging is best-effort by convention in this codebase
        }

        return batch;
    }

    private String mappingToJson(java.util.Map<String, String> mapping) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : mapping.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey().replace("\"", "\\\"")).append("\":")
              .append("\"").append(e.getValue().replace("\"", "\\\"")).append("\"");
            first = false;
        }
        return sb.append("}").toString();
    }
```

- [ ] **Step 4: Expose it on the controller**

Add to `ImportController`:

```java
    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<ImportBatch>> commit(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> mapping) throws IOException {

        validateUpload(file);
        ParsedSheet sheet = readSheet(file, null);
        Map<String, String> fieldMapping = new java.util.LinkedHashMap<>(mapping);
        fieldMapping.remove("file");
        ImportBatch batch = batchService.commit(sheet, fieldMapping, file.getOriginalFilename());
        return ResponseEntity.ok(ApiResponse.ok("Import complete", batch));
    }
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=ImportBatchServiceCommitTest`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/service/import_/ImportBatchService.java backend/src/main/java/com/hms/controller/hospital/ImportController.java backend/src/test/java/com/hms/service/import_/ImportBatchServiceCommitTest.java
git commit -m "feat(import): commit endpoint with batch lifecycle, concurrency guard and audit"
```

---

## Task 15: Fallback dedupe and reactivation after undo

Two spec requirements not yet covered: the `name + phone` fallback when no MRN column exists (§5.2), and re-import reactivating soft-deleted rows (§5.4, which the spec explicitly says must be tested).

**Files:**

- Modify: `backend/src/main/java/com/hms/service/import_/PatientImporter.java`
- Modify: `backend/src/main/java/com/hms/repository/PatientRepository.java`
- Test: `backend/src/test/java/com/hms/service/import_/PatientImporterDedupeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.import_;

import com.hms.dto.import_.RowOutcome;
import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientImporterDedupeTest {

    @Mock PatientRepository patientRepository;

    private PatientImporter importer() {
        return new PatientImporter(patientRepository);
    }

    @Test
    void reactivatesASoftDeletedRowFromAnUndoneBatchInsteadOfColliding() {
        Patient softDeleted = new Patient();
        softDeleted.setId(55L);
        softDeleted.setLegacyId("A-9");
        softDeleted.setIsActive(false);
        when(patientRepository.findByHospitalIdAndLegacyId(7L, "A-9")).thenReturn(Optional.of(softDeleted));

        RowOutcome outcome = importer().evaluate(Map.of("Name", "Ramesh", "MRN", "A-9"),
                Map.of("Name", "name", "MRN", "legacyId"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.UPDATE);
        assertThat(outcome.patient().getId()).isEqualTo(55L);
    }

    @Test
    void fallsBackToNamePlusPhoneWhenNoMrnColumnIsMapped() {
        Patient existing = new Patient();
        existing.setId(88L);
        when(patientRepository.findByHospitalIdAndNameAndPhone(7L, "Ramesh Patel", "9876543210"))
                .thenReturn(List.of(existing));

        RowOutcome outcome = importer().evaluate(
                Map.of("Name", "Ramesh Patel", "Mob No", "9876543210"),
                Map.of("Name", "name", "Mob No", "phone"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.UPDATE);
        assertThat(outcome.patient().getId()).isEqualTo(88L);
    }

    @Test
    void treatsNameOnlyRowsAsNewBecauseNameAloneIsNotAnIdentity() {
        RowOutcome outcome = importer().evaluate(
                Map.of("Name", "Ramesh Patel", "Mob No", ""),
                Map.of("Name", "name", "Mob No", "phone"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.CREATE);
        assertThat(outcome.patient().getId()).isNull();
    }

    @Test
    void skipsRatherThanMergingWhenTheFallbackMatchesMoreThanOnePatient() {
        Patient a = new Patient(); a.setId(1L);
        Patient b = new Patient(); b.setId(2L);
        when(patientRepository.findByHospitalIdAndNameAndPhone(7L, "Ramesh Patel", "9876543210"))
                .thenReturn(List.of(a, b));

        RowOutcome outcome = importer().evaluate(
                Map.of("Name", "Ramesh Patel", "Mob No", "9876543210"),
                Map.of("Name", "name", "Mob No", "phone"), List.of(), 7L, 2);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.SKIP);
        assertThat(outcome.message()).containsIgnoringCase("more than one");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=PatientImporterDedupeTest`
Expected: failure — `findByHospitalIdAndNameAndPhone` does not exist.

- [ ] **Step 3: Add the fallback lookup to `PatientRepository`**

```java
    java.util.List<com.hms.entity.Patient> findByHospitalIdAndNameAndPhone(
            Long hospitalId, String name, String phone);
```

- [ ] **Step 4: Add the fallback branch to `PatientImporter.evaluate`**

Replace the block that resolves `patient` with:

```java
        Patient patient = null;
        boolean isUpdate = false;

        if (legacyId != null) {
            // Deliberately not filtered on isActive: an undone batch leaves soft-deleted rows whose
            // legacy_id still occupies the unique index, so matching them reactivates rather than collides.
            Optional<Patient> existing = patientRepository.findByHospitalIdAndLegacyId(hospitalId, legacyId);
            if (existing.isPresent()) {
                patient = existing.get();
                isUpdate = true;
            }
        } else {
            // No MRN column. name+phone is the only fallback, and name alone is not an identity —
            // matching on it would merge unrelated people, which is worse than a duplicate.
            String phoneValue = blankToNull(byField.get("phone"));
            if (phoneValue != null) {
                List<Patient> candidates =
                        patientRepository.findByHospitalIdAndNameAndPhone(hospitalId, name, phoneValue);
                if (candidates.size() > 1) {
                    return RowOutcome.skip("Matches more than one existing patient with the same "
                            + "name and phone; skipped rather than merged. Resolve manually.");
                }
                if (candidates.size() == 1) {
                    patient = candidates.get(0);
                    isUpdate = true;
                }
            }
        }

        if (patient == null) {
            patient = new Patient();
        }
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=PatientImporterDedupeTest`
Expected: PASS, 4 tests.

- [ ] **Step 6: Run the whole suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hms/service/import_/PatientImporter.java backend/src/main/java/com/hms/repository/PatientRepository.java backend/src/test/java/com/hms/service/import_/PatientImporterDedupeTest.java
git commit -m "feat(import): name+phone fallback dedupe and reactivation of undone rows"
```

---

## Done criteria for Phase 1A

- [ ] `mvn test` passes with all new suites green
- [ ] An admin can `POST /hospital/imports/upload`, get a suggested mapping, `POST /hospital/imports/preview` and see counts without a single row being written
- [ ] `POST /hospital/imports/commit` creates a batch and stamps `import_batch_id`, `source=IMPORTED` and `hospital_id` from the JWT on every row
- [ ] A second concurrent import for the same hospital is refused
- [ ] `POST /hospital/imports/{id}/undo` soft-deletes and refuses when clinical activity exists
- [ ] Re-importing after an undo reactivates the soft-deleted rows instead of failing on the unique index
- [ ] With no MRN column, `name + phone` matches; name alone does not; multiple matches skip rather than merge
- [ ] `GET /hospital/imports/{id}/errors.csv` returns a re-uploadable file with formula injection defused
- [ ] A `hospital_id` column in an uploaded file never affects tenancy
- [ ] The uploaded spreadsheet is never written to the server filesystem

**Next:** Phase 1B — the admin wizard UI (upload → map → preview → commit → progress → undo), as a Settings card beside `VitalsSettingsCard`.
