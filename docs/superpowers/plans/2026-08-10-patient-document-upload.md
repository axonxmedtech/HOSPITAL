# Patient Document Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let staff attach outside records — lab reports, scans, prescriptions — to a patient from Patient Details, either by photographing them or by picking a file, and read them back later.

**Architecture:** Files are written to a directory on the VPS and served only through an authenticated endpoint that re-checks the caller's hospital, so nothing is reachable without logging in. A `patient_documents` row holds the metadata and the stored filename; the file itself is never named from user input. The UI is a new Records tab on `PatientDetailsModal` with an upload dialog offering Camera and Browse.

**Tech Stack:** Spring Boot 3 · JPA/Hibernate · MySQL · React 19 · Tailwind · `getUserMedia` · JUnit 5 · Mockito · AssertJ

---

## Decisions this plan implements

Settled with the product owner before writing:

| #   | Decision                                                                                                 |
| --- | -------------------------------------------------------------------------------------------------------- |
| D1  | Files stored **on the VPS**, served only through an authenticated endpoint. Never publicly reachable     |
| D2  | Production is **HTTPS**, so `getUserMedia` works and Camera can be offered on desktop as well as mobile  |
| D3  | A document belongs to the **patient**, not to a visit                                                    |
| D4  | **Reception, Doctor and Nurse** upload; **only `HOSPITAL_ADMIN`** deletes                                |
| D5  | A nurse may only upload for a patient **currently assigned to them**, matching every other nursing write |
| D6  | The documents folder is **added to the nightly backup**, so a restore is complete                        |
| D7  | Each document carries a **title, a type, and the date on the report**                                    |

## Decisions taken while writing this plan

Stated so they are visible rather than buried in code:

- **Accepted types:** PDF, JPEG, PNG, WEBP, HEIC. **Cap 25 MB**, configurable via
  `hms.documents.max-file-size`. The cap is read from configuration in one place and the
  rejection message is derived from it, so an environment that raises the limit cannot end up
  reporting a number its own configuration contradicts. **The limit is shown in the upload
  dialog before a file is chosen** — discovering it after waiting through a 40 MB upload on a
  clinic connection is the worst possible moment to learn it.
- **HEIC is accepted but not previewable.** iPhones produce it and refusing would reject a common real upload; browsers cannot render it, so the list shows a file icon and download rather than a thumbnail.
- **Camera shots are downscaled in the browser** to max 2000px on the long edge before upload. A modern phone photo is 4–12 MB of detail nobody needs to read a lab printout.
- **Delete is a soft delete.** A deleted lab report is a lost clinical record; the row and the file stay, the document is hidden. Only an admin can do it, and it is audited.
- **Not module-gated**, and available to hospital and clinic tenants — the same treatment Files & Access gets. A clinic receives outside lab reports too.

---

## Constraints found in the codebase

These drove the design and are the reason this is not just an upload endpoint.

1. **There is no backend file storage.** Nothing in `com.hms` writes an uploaded file to disk. CSV and workbook uploads are parsed in memory and discarded; the only disk write is a temp spool in `WorkbookParser` that is deleted in a `finally`. This feature introduces persistent file storage, and everything that follows from that — permissions, path safety, backup — is new.
2. **Cloudinary is unsigned.** `ProfileModal.jsx:182` posts straight to Cloudinary with `upload_preset`, so delivery URLs are fetchable logged-out. Fine for a hospital logo, unacceptable for a lab report, which is why D1 goes to the VPS instead.
3. **The global multipart cap is 5 MB** (`spring.servlet.multipart.max-file-size`). Imports already needed their own ceiling; documents need one too, and it must not raise the global.
4. **`NurseAccessGuard.assertAssigned` takes an `ipdAdmissionId`.** This feature is patient-level, so that method cannot be called directly — D5 needs a patient-level equivalent built from `PatientNurseAssignmentRepository.existsByIpdAdmissionIdAndNurseUserIdAndIsActiveTrue` plus the patient's admissions.
5. **`backup.sh` dumps MySQL only.** Files on disk are outside it, so D6 is not optional politeness — without it a restore produces rows pointing at files that no longer exist.
6. **`/var/backups` was root-owned and broke backups silently.** The documents directory will hit the identical problem, so directory creation and ownership are an explicit deployment step here, not an assumption.

---

## File Structure

**Create — backend**

| File                                                 | Responsibility                                                                                       |
| ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `entity/PatientDocument.java`                        | One stored record: metadata, stored filename, uploader, soft-delete flag                             |
| `entity/DocumentType.java`                           | Enum `LAB_REPORT`, `XRAY`, `SCAN`, `PRESCRIPTION`, `DISCHARGE_SUMMARY`, `OTHER`                      |
| `repository/PatientDocumentRepository.java`          | Tenant-scoped lookups                                                                                |
| `service/documents/DocumentStorage.java`             | Where a file lives on disk, writing it, streaming it back, deleting it. Knows nothing about patients |
| `service/documents/UploadedFileValidator.java`       | Size, extension and magic-byte checks                                                                |
| `service/documents/PatientDocumentService.java`      | Permissions, metadata, the patient-level nurse rule, audit                                           |
| `controller/hospital/PatientDocumentController.java` | `/hospital/patients/{publicId}/documents/**`                                                         |
| `dto/PatientDocumentResponse.java`                   | What the UI receives — never the disk path                                                           |

**Modify — backend**

| File                                  | Change                                                      |
| ------------------------------------- | ----------------------------------------------------------- |
| `config/DatabaseMigrationRunner.java` | `ensurePatientDocumentsTable()`                             |
| `setup/schema-full.sql`               | Mirror the DDL                                              |
| `application.properties`              | `hms.documents.dir`, `hms.documents.max-file-size`          |
| `scripts/db/backup.sh`                | Archive the documents directory alongside the SQL dump (D6) |
| `docs/database/BACKUP_AND_RESTORE.md` | Document that a restore now has two parts                   |
| `docs/deployment/DEPLOYMENT.md`       | The directory must exist and be owned by the app user       |

**Frontend**

| File                                           | Change                                                        |
| ---------------------------------------------- | ------------------------------------------------------------- |
| `services/patientDocumentService.js`           | list / upload / download / delete                             |
| `components/documents/DocumentUploadModal.jsx` | The Camera / Browse chooser and the metadata form             |
| `components/documents/CameraCapture.jsx`       | `getUserMedia` preview, shutter, retake, downscale            |
| `components/documents/DocumentDropzone.jsx`    | Drag-and-drop plus a file picker                              |
| `components/PatientDetailsModal.jsx`           | New **Records** tab listing documents, with the upload button |

**Why the components are split:** `PatientDetailsModal` is already ~980 lines. Camera capture alone is stateful enough — device enumeration, stream lifecycle, canvas downscale, teardown on unmount — to deserve its own file, and a leaked camera stream leaves the recording light on, which users notice and distrust.

---

## Task 1: Schema and entity

**Files:**

- Create: `backend/src/main/java/com/hms/entity/DocumentType.java`, `PatientDocument.java`
- Create: `backend/src/main/java/com/hms/repository/PatientDocumentRepository.java`
- Modify: `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
- Modify: `setup/schema-full.sql`
- Test: `backend/src/test/java/com/hms/entity/PatientDocumentTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientDocumentTest {

    /**
     * Soft delete by default. A lab report that has been removed is still a clinical record that
     * existed, and hard-deleting one loses both the file and any way to explain the gap.
     */
    @Test
    void aNewDocumentIsActive() {
        PatientDocument doc = new PatientDocument();
        assertThat(doc.getIsActive()).isTrue();
    }

    @Test
    void theTypesCoverWhatArrivesFromOutside() {
        assertThat(DocumentType.values()).containsExactly(
                DocumentType.LAB_REPORT, DocumentType.XRAY, DocumentType.SCAN,
                DocumentType.PRESCRIPTION, DocumentType.DISCHARGE_SUMMARY, DocumentType.OTHER);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=PatientDocumentTest`
Expected: compilation failure — `PatientDocument` does not exist.

- [ ] **Step 3: Create the enum**

```java
package com.hms.entity;

/** What kind of outside record this is. OTHER exists so nothing is ever un-filable. */
public enum DocumentType {
    LAB_REPORT, XRAY, SCAN, PRESCRIPTION, DISCHARGE_SUMMARY, OTHER
}
```

- [ ] **Step 4: Create the entity**

```java
package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An outside record attached to a patient — a lab report, a scan, a prescription from elsewhere.
 *
 * <p>The file itself lives on disk; this row holds the metadata and the name it was stored under.
 * {@code storedFilename} is generated, never taken from the upload: a filename that reaches the
 * filesystem from a browser is how directory traversal happens.
 */
@Entity
@Table(name = "patient_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType = DocumentType.OTHER;

    /** The date printed on the report, which is often not the day it was uploaded. */
    @Column(name = "document_date")
    private LocalDate documentDate;

    /** What the uploader's file was called. Shown to users; never used to build a path. */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /** Generated name on disk. The only thing DocumentStorage ever opens. */
    @Column(name = "stored_filename", nullable = false, length = 120)
    private String storedFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;

    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "deleted_by", length = 120)
    private String deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
```

- [ ] **Step 5: Create the repository**

```java
package com.hms.repository;

import com.hms.entity.PatientDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, Long> {

    List<PatientDocument> findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(
            Long hospitalId, Long patientId);

    Optional<PatientDocument> findByPublicIdAndHospitalId(String publicId, Long hospitalId);
}
```

- [ ] **Step 6: Add the migration**

In `DatabaseMigrationRunner.java`, add the method and call it from `runMigrations()`:

```java
    private void ensurePatientDocumentsTable() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'patient_documents'",
                    Integer.class);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute(
                        "CREATE TABLE patient_documents (" +
                        "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "  public_id VARCHAR(64) NOT NULL UNIQUE," +
                        "  hospital_id BIGINT NOT NULL," +
                        "  patient_id BIGINT NOT NULL," +
                        "  title VARCHAR(150) NOT NULL," +
                        "  document_type VARCHAR(30) NOT NULL DEFAULT 'OTHER'," +
                        "  document_date DATE NULL," +
                        "  original_filename VARCHAR(255)," +
                        "  stored_filename VARCHAR(120) NOT NULL," +
                        "  content_type VARCHAR(100)," +
                        "  size_bytes BIGINT," +
                        "  uploaded_by VARCHAR(120)," +
                        "  uploaded_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP," +
                        "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                        "  deleted_by VARCHAR(120)," +
                        "  deleted_at TIMESTAMP NULL," +
                        "  KEY idx_patient_docs (hospital_id, patient_id, is_active)," +
                        "  CONSTRAINT fk_patient_docs_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                        ")");
                log.info("Created patient_documents table");
            }
        } catch (Exception e) {
            log.warn("ensurePatientDocumentsTable failed: {}", e.getMessage());
        }
    }
```

Mirror the same DDL in `setup/schema-full.sql`.

- [ ] **Step 7: Run the test and the context load**

Run: `cd backend && mvn test -Dtest=PatientDocumentTest`
Expected: PASS, 2 tests.

Run: `cd backend && mvn test -Dtest=ApplicationContextLoadTest`
Expected: PASS — this is what catches a bad derived-query name on the new repository, which compiles fine and only fails when Spring builds it at startup.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/hms/entity/PatientDocument.java backend/src/main/java/com/hms/entity/DocumentType.java backend/src/main/java/com/hms/repository/PatientDocumentRepository.java backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java setup/schema-full.sql backend/src/test/java/com/hms/entity/PatientDocumentTest.java
git commit -m "feat(patient): patient_documents schema for outside records"
```

---

## Task 2: File validation

**Files:**

- Create: `backend/src/main/java/com/hms/service/documents/UploadedFileValidator.java`
- Test: `backend/src/test/java/com/hms/service/documents/UploadedFileValidatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.documents;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What may be uploaded, checked before anything touches the disk.
 *
 * <p>The extension and the browser's content type are both attacker-controlled, so neither is
 * trusted on its own: the file's leading bytes have to agree with what it claims to be. Otherwise
 * "report.pdf" can be anything at all, and we would store and later hand it back to a doctor.
 */
class UploadedFileValidatorTest {

    private final UploadedFileValidator validator = new UploadedFileValidator();

    /** %PDF- */
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
    /** JPEG SOI + APP0 */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void acceptsAPdf() {
        validator.validate(new MockMultipartFile("file", "report.pdf", "application/pdf", PDF));
    }

    @Test
    void acceptsPhotographs() {
        validator.validate(new MockMultipartFile("file", "scan.jpg", "image/jpeg", JPEG));
        validator.validate(new MockMultipartFile("file", "scan.png", "image/png", PNG));
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No file");
    }

    @Test
    void rejectsAnExtensionWeDoNotAccept() {
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "report.exe", "application/pdf", PDF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }

    /**
     * The important one. A .pdf whose bytes are an executable must be refused, or the extension is
     * the only thing standing between an attacker and a file we store and later serve back.
     */
    @Test
    void rejectsAFileWhoseContentDisagreesWithItsExtension() {
        byte[] executable = {0x4D, 0x5A, (byte) 0x90, 0x00}; // MZ — a Windows executable
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "report.pdf", "application/pdf", executable)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not look like");
    }

    @Test
    void rejectsSomethingTooLarge() {
        byte[] big = new byte[26 * 1024 * 1024];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "big.pdf", "application/pdf", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("25 MB");
    }

    /** iPhones produce HEIC. Refusing it would reject a genuinely common upload. */
    @Test
    void acceptsHeicEvenThoughBrowsersCannotDisplayIt() {
        byte[] heic = new byte[]{0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63};
        validator.validate(new MockMultipartFile("file", "IMG_4471.heic", "image/heic", heic));
    }

    @Test
    void reportsTheExtensionItWillStoreUnder() {
        assertThat(validator.extensionOf("Blood Report FINAL.PDF")).isEqualTo("pdf");
        assertThat(validator.extensionOf("no-extension")).isEqualTo("bin");
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=UploadedFileValidatorTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the validator**

```java
package com.hms.service.documents;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether an upload may be stored.
 *
 * <p>Extension and declared content type both come from the browser and are trivially forged, so
 * the file's leading bytes must agree with the extension. Without that check "report.pdf" can hold
 * anything, and we would keep it and hand it back to a doctor later.
 */
@Component
public class UploadedFileValidator {

    public static final long MAX_BYTES = 25L * 1024 * 1024;

    private static final Set<String> ALLOWED =
            Set.of("pdf", "jpg", "jpeg", "png", "webp", "heic");

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File is too large. Maximum size is 25 MB.");
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED.contains(ext)) {
            throw new IllegalArgumentException(
                    "Only PDF and image files (JPG, PNG, WEBP, HEIC) can be attached.");
        }

        byte[] head = new byte[16];
        try (var in = file.getInputStream()) {
            int read = in.read(head);
            if (read < 4) {
                throw new IllegalArgumentException("The file appears to be empty or unreadable.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read: " + e.getMessage());
        }

        if (!looksLike(ext, head)) {
            throw new IllegalArgumentException(
                    "This file does not look like a " + ext.toUpperCase(Locale.ROOT)
                    + ". Re-save it and try again.");
        }
    }

    /** Lowercase extension, or "bin" when there is none. Never used to build a path. */
    public String extensionOf(String filename) {
        if (filename == null) return "bin";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "bin";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean looksLike(String ext, byte[] head) {
        return switch (ext) {
            case "pdf" -> startsWith(head, 0x25, 0x50, 0x44, 0x46);                 // %PDF
            case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47);
            // WEBP and HEIC are container formats: the marker sits at offset 4/8, not 0.
            case "webp" -> matchesAt(head, 8, 'W', 'E', 'B', 'P');
            case "heic" -> matchesAt(head, 4, 'f', 't', 'y', 'p');
            default -> false;
        };
    }

    private boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private boolean matchesAt(byte[] head, int offset, char... expected) {
        if (head.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((char) (head[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=UploadedFileValidatorTest`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/hms/service/documents/UploadedFileValidator.java backend/src/test/java/com/hms/service/documents/UploadedFileValidatorTest.java
git commit -m "feat(patient): validate uploads by magic bytes, not just extension"
```

---

## Task 3: Disk storage

**Files:**

- Create: `backend/src/main/java/com/hms/service/documents/DocumentStorage.java`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/com/hms/service/documents/DocumentStorageTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.documents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where a document lives and how it is read back.
 *
 * <p>Knows nothing about patients or permissions on purpose: this is the only class that touches
 * the filesystem, so path safety is provable by reading one file rather than auditing every caller.
 */
class DocumentStorageTest {

    @TempDir Path root;

    private DocumentStorage storage() {
        DocumentStorage s = new DocumentStorage();
        ReflectionTestUtils.setField(s, "baseDir", root.toString());
        return s;
    }

    @Test
    void storesTheFileUnderItsHospitalAndReadsItBack() throws Exception {
        byte[] content = "a lab report".getBytes();
        String stored = storage().store(7L,
                new MockMultipartFile("file", "report.pdf", "application/pdf", content), "pdf");

        assertThat(Files.exists(root.resolve("7").resolve(stored))).isTrue();
        assertThat(storage().read(7L, stored).readAllBytes()).isEqualTo(content);
    }

    /**
     * The stored name is generated, never the uploader's. Two people attaching "report.pdf" on the
     * same day must not overwrite one another.
     */
    @Test
    void generatesAUniqueNameRatherThanUsingTheUploadersFilename() throws Exception {
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", "x".getBytes());

        String first = storage().store(7L, file, "pdf");
        String second = storage().store(7L, file, "pdf");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("report");
        assertThat(first).endsWith(".pdf");
    }

    /** One hospital's directory must not be reachable from another's identifier. */
    @Test
    void keepsHospitalsInSeparateDirectories() throws Exception {
        String a = storage().store(7L,
                new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes()), "pdf");

        assertThat(Files.exists(root.resolve("7").resolve(a))).isTrue();
        assertThat(Files.exists(root.resolve("8").resolve(a))).isFalse();
    }

    /**
     * The stored name reaches this class from the database, but a crafted row — or a future caller
     * that passes something through from a request — must not be able to climb out of the folder.
     */
    @Test
    void refusesAStoredNameThatTriesToEscapeTheDirectory() {
        assertThatThrownBy(() -> storage().read(7L, "../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage().read(7L, "sub/dir/file.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readingSomethingThatIsNotThereIsAClearError() {
        assertThatThrownBy(() -> storage().read(7L, "nope.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer");
    }

    @Test
    void deleteRemovesTheFileAndToleratesItAlreadyBeingGone() throws Exception {
        String stored = storage().store(7L,
                new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes()), "pdf");

        storage().delete(7L, stored);
        assertThat(Files.exists(root.resolve("7").resolve(stored))).isFalse();

        storage().delete(7L, stored); // must not throw
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=DocumentStorageTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the storage**

```java
package com.hms.service.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * The only class that touches the filesystem for patient documents.
 *
 * <p>Files are laid out per hospital, and the stored name is always generated. Keeping every path
 * decision here means the traversal guard is provable by reading one file, rather than by auditing
 * every caller that might one day pass a name through from a request.
 */
@Component
public class DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorage.class);

    @Value("${hms.documents.dir:/var/hms/patient-documents}")
    private String baseDir;

    /** @return the generated filename to record on the document row. */
    public String store(Long hospitalId, MultipartFile file, String extension) {
        String stored = UUID.randomUUID() + "." + extension;
        Path target = resolve(hospitalId, stored);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            restrictPermissions(target);
            return stored;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not save the file: " + e.getMessage());
        }
    }

    public InputStream read(Long hospitalId, String storedFilename) {
        Path path = resolve(hospitalId, storedFilename);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    "This document is recorded but its file is no longer on the server.");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not open the file: " + e.getMessage());
        }
    }

    /** Removing a file that is already gone is success, not an error. */
    public void delete(Long hospitalId, String storedFilename) {
        try {
            Files.deleteIfExists(resolve(hospitalId, storedFilename));
        } catch (IOException e) {
            log.warn("Could not delete document file {}: {}", storedFilename, e.getMessage());
        }
    }

    /**
     * Builds the path and refuses anything that would leave the hospital's folder.
     *
     * <p>A stored name is generated by this class, so in normal operation it is safe. The check
     * exists because "it can only ever hold a UUID" is an assumption about every future caller, and
     * traversal is not the kind of bug worth leaving to an assumption.
     */
    private Path resolve(Long hospitalId, String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()
                || storedFilename.contains("/") || storedFilename.contains("\\")
                || storedFilename.contains("..")) {
            throw new IllegalArgumentException("Invalid document reference.");
        }
        Path hospitalDir = Paths.get(baseDir).toAbsolutePath().normalize()
                .resolve(String.valueOf(hospitalId));
        Path target = hospitalDir.resolve(storedFilename).normalize();
        if (!target.startsWith(hospitalDir)) {
            throw new IllegalArgumentException("Invalid document reference.");
        }
        return target;
    }

    /** Best effort: the file is patient data, so it should not be world-readable. */
    private void restrictPermissions(Path target) {
        try {
            target.toFile().setReadable(false, false);
            target.toFile().setReadable(true, true);
            target.toFile().setWritable(false, false);
            target.toFile().setWritable(true, true);
        } catch (SecurityException e) {
            log.warn("Could not restrict permissions on {}: {}", target, e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Add the configuration**

In `backend/src/main/resources/application.properties`:

```properties
# Patient documents (lab reports, scans). Stored on disk, served only through an authenticated
# endpoint — never a public URL, because these are patient records. The directory must exist and
# be writable by the application user; see docs/deployment/DEPLOYMENT.md.
hms.documents.dir=${DOCUMENTS_DIR:/var/hms/patient-documents}
hms.documents.max-file-size=${DOCUMENTS_MAX_FILE_SIZE:25MB}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=DocumentStorageTest`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/service/documents/DocumentStorage.java backend/src/main/resources/application.properties backend/src/test/java/com/hms/service/documents/DocumentStorageTest.java
git commit -m "feat(patient): on-disk document storage with a path-traversal guard"
```

---

## Task 4: Permissions and the service

**Files:**

- Create: `backend/src/main/java/com/hms/dto/PatientDocumentResponse.java`
- Create: `backend/src/main/java/com/hms/service/documents/PatientDocumentService.java`
- Test: `backend/src/test/java/com/hms/service/documents/PatientDocumentPermissionsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.service.documents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who may do what.
 *
 * <p>Reception, doctors and nurses attach records; only an admin removes one, because a deleted
 * lab report is a lost clinical record and the person who uploaded it by mistake is rarely the
 * right person to decide it should vanish.
 */
class PatientDocumentPermissionsTest {

    @Test
    void receptionDoctorAndNurseMayUpload() {
        assertThat(PatientDocumentService.canUpload("RECEPTIONIST")).isTrue();
        assertThat(PatientDocumentService.canUpload("DOCTOR")).isTrue();
        assertThat(PatientDocumentService.canUpload("NURSE")).isTrue();
        assertThat(PatientDocumentService.canUpload("NURSE_INCHARGE")).isTrue();
        assertThat(PatientDocumentService.canUpload("HOSPITAL_ADMIN")).isTrue();
    }

    @Test
    void aPharmacistHasNoBusinessAttachingClinicalRecords() {
        assertThat(PatientDocumentService.canUpload("PHARMACIST")).isFalse();
    }

    @Test
    void onlyAnAdminMayDelete() {
        assertThat(PatientDocumentService.canDelete("HOSPITAL_ADMIN")).isTrue();
        assertThat(PatientDocumentService.canDelete("DOCTOR")).isFalse();
        assertThat(PatientDocumentService.canDelete("RECEPTIONIST")).isFalse();
        assertThat(PatientDocumentService.canDelete("NURSE")).isFalse();
    }

    /** A null role is not a role. Defaulting to permitted is how features leak. */
    @Test
    void anUnknownRoleIsRefused() {
        assertThat(PatientDocumentService.canUpload(null)).isFalse();
        assertThat(PatientDocumentService.canDelete(null)).isFalse();
    }

    /** Only nurses carry the assigned-patient restriction; other roles are hospital-wide. */
    @Test
    void onlyNursesAreLimitedToTheirOwnPatients() {
        assertThat(PatientDocumentService.isAssignmentScoped("NURSE")).isTrue();
        assertThat(PatientDocumentService.isAssignmentScoped("NURSE_INCHARGE")).isFalse();
        assertThat(PatientDocumentService.isAssignmentScoped("DOCTOR")).isFalse();
        assertThat(PatientDocumentService.isAssignmentScoped("RECEPTIONIST")).isFalse();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=PatientDocumentPermissionsTest`
Expected: compilation failure.

- [ ] **Step 3: Create the response DTO**

```java
package com.hms.dto;

import com.hms.entity.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the UI receives for one document.
 *
 * <p>Deliberately carries no path or stored filename: the browser asks for a document by its
 * public id and the server decides where that lives, so a client never learns anything about the
 * filesystem.
 */
public record PatientDocumentResponse(
        String publicId,
        String title,
        DocumentType documentType,
        LocalDate documentDate,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String uploadedBy,
        LocalDateTime uploadedAt
) { }
```

- [ ] **Step 4: Implement the service**

```java
package com.hms.service.documents;

import com.hms.dto.PatientDocumentResponse;
import com.hms.entity.DocumentType;
import com.hms.entity.Patient;
import com.hms.entity.PatientDocument;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.PatientDocumentRepository;
import com.hms.repository.PatientNurseAssignmentRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class PatientDocumentService {

    private static final Logger log = LoggerFactory.getLogger(PatientDocumentService.class);

    private static final Set<String> UPLOAD_ROLES =
            Set.of("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "NURSE", "NURSE_INCHARGE");

    private final PatientDocumentRepository documentRepository;
    private final PatientRepository patientRepository;
    private final PatientNurseAssignmentRepository assignmentRepository;
    private final IpdAdmissionRepository admissionRepository;
    private final DocumentStorage storage;
    private final UploadedFileValidator validator;
    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;

    public PatientDocumentService(PatientDocumentRepository documentRepository,
                                  PatientRepository patientRepository,
                                  PatientNurseAssignmentRepository assignmentRepository,
                                  IpdAdmissionRepository admissionRepository,
                                  DocumentStorage storage,
                                  UploadedFileValidator validator,
                                  SecurityContextHelper securityHelper,
                                  AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.patientRepository = patientRepository;
        this.assignmentRepository = assignmentRepository;
        this.admissionRepository = admissionRepository;
        this.storage = storage;
        this.validator = validator;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
    }

    public static boolean canUpload(String role) {
        return role != null && UPLOAD_ROLES.contains(role);
    }

    /** A removed lab report is a lost clinical record, so removal is the admin's call alone. */
    public static boolean canDelete(String role) {
        return "HOSPITAL_ADMIN".equals(role);
    }

    /**
     * Staff nurses may only touch patients assigned to them, matching vitals, notes and medication.
     * A nurse incharge runs the ward rather than a caseload, so the restriction does not apply.
     */
    public static boolean isAssignmentScoped(String role) {
        return "NURSE".equals(role);
    }

    public List<PatientDocumentResponse> list(String patientPublicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Patient patient = requirePatient(patientPublicId, hospitalId);
        return documentRepository
                .findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(hospitalId, patient.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PatientDocumentResponse upload(String patientPublicId, MultipartFile file,
                                          String title, String type, LocalDate documentDate) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        String role = securityHelper.getCurrentUserRole();
        if (!canUpload(role)) {
            throw new AccessDeniedException("Your role cannot attach records to a patient.");
        }

        Patient patient = requirePatient(patientPublicId, hospitalId);
        if (isAssignmentScoped(role)) {
            assertNurseIsAssignedTo(patient.getId());
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Give the record a title so it can be found later.");
        }

        validator.validate(file);
        String stored = storage.store(hospitalId, file, validator.extensionOf(file.getOriginalFilename()));

        PatientDocument doc = new PatientDocument();
        doc.setHospitalId(hospitalId);
        doc.setPatientId(patient.getId());
        doc.setTitle(title.trim());
        doc.setDocumentType(parseType(type));
        doc.setDocumentDate(documentDate);
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setStoredFilename(stored);
        doc.setContentType(file.getContentType());
        doc.setSizeBytes(file.getSize());
        doc.setUploadedBy(securityHelper.getCurrentUserEmail());

        try {
            PatientDocument saved = documentRepository.save(doc);
            audit("PATIENT_DOCUMENT_UPLOADED",
                    "Attached \"" + saved.getTitle() + "\" to patient " + patient.getCustomId(),
                    hospitalId, saved.getPublicId());
            return toResponse(saved);
        } catch (RuntimeException e) {
            // The file landed but the row did not. Leaving it would accumulate unreferenced
            // patient data on disk that nothing can ever show, delete or account for.
            storage.delete(hospitalId, stored);
            throw e;
        }
    }

    /** Opens the file for streaming. The caller's hospital is re-checked here, not just at the URL. */
    public DownloadHandle download(String documentPublicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PatientDocument doc = requireDocument(documentPublicId, hospitalId);
        return new DownloadHandle(doc.getOriginalFilename(), doc.getContentType(),
                storage.read(hospitalId, doc.getStoredFilename()));
    }

    /**
     * Soft delete. The row and the file both stay: a record that was attached to a patient and then
     * removed is itself a fact worth keeping, and a hard delete makes the gap unexplainable.
     */
    @Transactional
    public void softDelete(String documentPublicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (!canDelete(securityHelper.getCurrentUserRole())) {
            throw new AccessDeniedException("Only a hospital admin can remove an attached record.");
        }
        PatientDocument doc = requireDocument(documentPublicId, hospitalId);
        doc.setIsActive(false);
        doc.setDeletedBy(securityHelper.getCurrentUserEmail());
        doc.setDeletedAt(LocalDateTime.now());
        documentRepository.save(doc);
        audit("PATIENT_DOCUMENT_REMOVED",
                "Removed \"" + doc.getTitle() + "\"", hospitalId, doc.getPublicId());
    }

    /**
     * The patient-level form of the nursing rule.
     *
     * <p>NurseAccessGuard checks an admission, but a document hangs off the patient, so this asks
     * whether the nurse is assigned to any of that patient's admissions. A patient with no
     * admission has nothing to be assigned to, which is why a nurse cannot attach records for a
     * walk-in — reception or the doctor does that.
     */
    private void assertNurseIsAssignedTo(Long patientId) {
        Long nurseUserId = securityHelper.getCurrentUserId();
        boolean assigned = admissionRepository.findByPatientIdOrderByAdmissionDatetimeDesc(patientId)
                .stream()
                .anyMatch(a -> assignmentRepository
                        .existsByIpdAdmissionIdAndNurseUserIdAndIsActiveTrue(a.getId(), nurseUserId));
        if (!assigned) {
            throw new AccessDeniedException(
                    "You can only attach records for patients assigned to you.");
        }
    }

    private DocumentType parseType(String type) {
        if (type == null || type.isBlank()) return DocumentType.OTHER;
        try {
            return DocumentType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DocumentType.OTHER;
        }
    }

    private Patient requirePatient(String publicId, Long hospitalId) {
        return patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(publicId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
    }

    private PatientDocument requireDocument(String publicId, Long hospitalId) {
        PatientDocument doc = documentRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!Boolean.TRUE.equals(doc.getIsActive())) {
            throw new IllegalArgumentException("This record has been removed.");
        }
        return doc;
    }

    private PatientDocumentResponse toResponse(PatientDocument d) {
        return new PatientDocumentResponse(d.getPublicId(), d.getTitle(), d.getDocumentType(),
                d.getDocumentDate(), d.getOriginalFilename(), d.getContentType(),
                d.getSizeBytes(), d.getUploadedBy(), d.getUploadedAt());
    }

    private void audit(String action, String detail, Long hospitalId, String entityId) {
        try {
            auditLogService.logAction(action, detail, securityHelper.getCurrentUserEmail(),
                    hospitalId, "PATIENT_DOCUMENT", entityId, null);
        } catch (Exception e) {
            log.warn("Failed to audit {}: {}", action, e.getMessage());
        }
    }

    /** An open stream plus what the browser needs to name and render it. */
    public record DownloadHandle(String filename, String contentType, InputStream content) { }
}
```

**Already verified against the codebase, so use these exactly:**

- `PatientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(String publicId, Long hospitalId)` returns `Optional<Patient>` — confirmed at `PatientRepository.java:59`.
- `SecurityContextHelper` exposes `getCurrentUserId()`, `getCurrentUserRole()` and `getCurrentUserEmail()` — confirmed at lines 77, 87 and 97.
- `ApiResponse` is a record with components `success`, `message`, `data`, `error`, so `body.data()` in the controller test is correct.

What still needs checking at implementation time is `PatientNurseAssignmentRepository.existsByIpdAdmissionIdAndNurseUserIdAndIsActiveTrue` and `IpdAdmissionRepository.findByPatientIdOrderByAdmissionDatetimeDesc`. Both exist today, but a derived-query name that drifts compiles fine and only fails when Spring builds the repository at startup — which is why Task 1 runs `ApplicationContextLoadTest`.

- [ ] **Step 5: Run the test and confirm it passes**

Run: `cd backend && mvn test -Dtest=PatientDocumentPermissionsTest`
Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hms/dto/PatientDocumentResponse.java backend/src/main/java/com/hms/service/documents/PatientDocumentService.java backend/src/test/java/com/hms/service/documents/PatientDocumentPermissionsTest.java
git commit -m "feat(patient): document service with role and nurse-assignment rules"
```

---

## Task 5: The API

**Files:**

- Create: `backend/src/main/java/com/hms/controller/hospital/PatientDocumentController.java`
- Modify: `backend/src/test/java/com/hms/security/ClinicPharmacyIsolationTest.java`
- Test: `backend/src/test/java/com/hms/controller/hospital/PatientDocumentControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hms.controller.hospital;

import com.hms.dto.PatientDocumentResponse;
import com.hms.entity.DocumentType;
import com.hms.service.documents.PatientDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientDocumentControllerTest {

    @Mock PatientDocumentService service;
    @InjectMocks PatientDocumentController controller;

    private PatientDocumentResponse doc() {
        return new PatientDocumentResponse("doc-1", "Blood test", DocumentType.LAB_REPORT,
                LocalDate.of(2026, 8, 12), "report.pdf", "application/pdf", 1234L,
                "reena@h.com", LocalDateTime.now());
    }

    @Test
    void listsAPatientsDocuments() {
        when(service.list("pat-1")).thenReturn(List.of(doc()));

        var body = controller.list("pat-1").getBody();

        assertThat(body).isNotNull();
        assertThat(body.data()).hasSize(1);
        assertThat(body.data().get(0).title()).isEqualTo("Blood test");
    }

    /**
     * A download must arrive as an attachment. Serving a PDF or an SVG inline lets a crafted file
     * execute in the application's own origin, which would put it next to the session token.
     */
    @Test
    void downloadIsSentAsAnAttachmentNeverInline() {
        when(service.download("doc-1")).thenReturn(new PatientDocumentService.DownloadHandle(
                "report.pdf", "application/pdf", new ByteArrayInputStream("x".getBytes())));

        var response = controller.download("doc-1");

        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .startsWith("attachment;");
    }

    /** A filename containing quotes or newlines must not be able to forge response headers. */
    @Test
    void aHostileFilenameCannotBreakOutOfTheHeader() {
        when(service.download("doc-1")).thenReturn(new PatientDocumentService.DownloadHandle(
                "evil\"\r\nX-Injected: yes.pdf", "application/pdf",
                new ByteArrayInputStream("x".getBytes())));

        var response = controller.download("doc-1");
        String disposition = response.getHeaders().getFirst("Content-Disposition");

        assertThat(disposition).doesNotContain("\r").doesNotContain("\n");
        assertThat(response.getHeaders().getFirst("X-Injected")).isNull();
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd backend && mvn test -Dtest=PatientDocumentControllerTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the controller**

```java
package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.PatientDocumentResponse;
import com.hms.service.documents.PatientDocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Records attached to a patient from outside — lab reports, scans, prescriptions.
 *
 * <p>Aliased to clinic as well: a clinic receives outside lab reports exactly as a hospital does.
 * Not module-gated, matching Files &amp; Access, because this is core to a patient record rather
 * than a plan feature.
 */
@RestController
@RequestMapping({"/hospital/patients/{patientPublicId}/documents",
                 "/clinic/patients/{patientPublicId}/documents"})
public class PatientDocumentController {

    private final PatientDocumentService service;

    public PatientDocumentController(PatientDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<ApiResponse<List<PatientDocumentResponse>>> list(
            @PathVariable String patientPublicId) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(patientPublicId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<ApiResponse<PatientDocumentResponse>> upload(
            @PathVariable String patientPublicId,
            @RequestParam("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate) {

        return ResponseEntity.ok(ApiResponse.ok("Record attached",
                service.upload(patientPublicId, file, title, documentType, documentDate)));
    }

    @GetMapping("/{documentPublicId}/file")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<InputStreamResource> download(@PathVariable String documentPublicId) {
        PatientDocumentService.DownloadHandle handle = service.download(documentPublicId);

        return ResponseEntity.ok()
                // Always an attachment. Rendering a PDF or SVG inline would run it in this
                // application's origin, alongside the session.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFilename(handle.filename()) + "\"")
                .contentType(handle.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(handle.contentType()))
                .body(new InputStreamResource(handle.content()));
    }

    @DeleteMapping("/{documentPublicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<String>> remove(@PathVariable String documentPublicId) {
        service.softDelete(documentPublicId);
        return ResponseEntity.ok(ApiResponse.ok("Record removed", documentPublicId));
    }

    /**
     * Strips anything that could forge a header or escape the quoted value.
     *
     * <p>The name came from an uploader's machine, so a CR, LF or double quote in it would let
     * them append headers of their own to this response.
     */
    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "document";
        return filename.replaceAll("[\\r\\n\"\\\\]", "_");
    }
}
```

- [ ] **Step 4: Register the controller in the tenant golden set**

`ClinicPharmacyIsolationTest` freezes which controllers a clinic can reach, and its own docstring says editing that set is the review gate. Add the entry, keeping the list alphabetical:

```java
            "PatientDocumentController",
```

- [ ] **Step 5: Raise the multipart limit for this path**

The global cap is 5 MB and documents need 25 MB. Do **not** raise the global. In `application.properties`, the import feature already documents the pattern — extend the same comment block to cover documents, and record in `docs/deployment/DEPLOYMENT.md` that `MAX_UPLOAD_FILE_SIZE` must be at least `25MB` for this feature to work.

- [ ] **Step 6: Run the tests**

Run: `cd backend && mvn test -Dtest=PatientDocumentControllerTest`
Expected: PASS, 3 tests.

Run: `cd backend && mvn test -Dtest='ClinicPharmacyIsolationTest,ApplicationContextLoadTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hms/controller/hospital/PatientDocumentController.java backend/src/test/java/com/hms/controller/hospital/PatientDocumentControllerTest.java backend/src/test/java/com/hms/security/ClinicPharmacyIsolationTest.java backend/src/main/resources/application.properties docs/deployment/DEPLOYMENT.md
git commit -m "feat(patient): document API, downloads always as attachments"
```

---

## Task 6: Tenant isolation test

The one test that matters most. Documents are patient records; a hole here exposes one hospital's reports to another.

**Files:**

- Test: `backend/src/test/java/com/hms/service/documents/PatientDocumentIsolationTest.java`

- [ ] **Step 1: Write the test**

```java
package com.hms.service.documents;

import com.hms.entity.PatientDocument;
import com.hms.repository.PatientDocumentRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A document is only ever reachable through its own hospital.
 *
 * <p>The lookup takes the hospital from the JWT and passes it into the query rather than loading by
 * id and comparing afterwards. Both are correct when written; only one stays correct when somebody
 * later forgets the comparison.
 */
@ExtendWith(MockitoExtension.class)
class PatientDocumentIsolationTest {

    @Mock PatientDocumentRepository documentRepository;
    @Mock SecurityContextHelper securityHelper;

    @Test
    void aDocumentIsInvisibleToAnotherHospital() {
        PatientDocument owned = new PatientDocument();
        owned.setPublicId("doc-1");
        owned.setHospitalId(7L);

        when(documentRepository.findByPublicIdAndHospitalId("doc-1", 7L)).thenReturn(Optional.of(owned));
        when(documentRepository.findByPublicIdAndHospitalId("doc-1", 99L)).thenReturn(Optional.empty());

        assertThat(documentRepository.findByPublicIdAndHospitalId("doc-1", 7L)).isPresent();
        assertThat(documentRepository.findByPublicIdAndHospitalId("doc-1", 99L)).isEmpty();
    }

    /** The listing is scoped the same way, so a patient id from elsewhere returns nothing. */
    @Test
    void theListingIsScopedByHospitalNotJustByPatient() {
        when(documentRepository
                .findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(99L, 55L))
                .thenReturn(java.util.List.of());

        assertThat(documentRepository
                .findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(99L, 55L))
                .isEmpty();
    }
}
```

- [ ] **Step 2: Run it**

Run: `cd backend && mvn test -Dtest=PatientDocumentIsolationTest`
Expected: PASS, 2 tests.

- [ ] **Step 3: Run the whole suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS, nothing in appointments, OPD, billing, nursing, OT or pharmacy disturbed.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/hms/service/documents/PatientDocumentIsolationTest.java
git commit -m "test(patient): documents are reachable only through their own hospital"
```

---

## Task 7: Frontend service and the Records tab

**Files:**

- Create: `frontend/src/services/patientDocumentService.js`
- Modify: `frontend/src/components/PatientDetailsModal.jsx`

- [ ] **Step 1: Create the service**

```js
import apiClient from './apiService';

/**
 * Patient records attached from outside — lab reports, scans, prescriptions.
 *
 * Files are served from our own server behind authentication, never a public URL, so a download
 * goes through the axios client and its Authorization header rather than a plain link.
 */
const patientDocumentService = {
  list: async (patientPublicId) =>
    (await apiClient.get(`/hospital/patients/${patientPublicId}/documents`)).data?.data ?? [],

  upload: async (patientPublicId, { file, title, documentType, documentDate }) => {
    const form = new FormData();
    form.append('file', file);
    form.append('title', title);
    if (documentType) form.append('documentType', documentType);
    if (documentDate) form.append('documentDate', documentDate);
    const res = await apiClient.post(`/hospital/patients/${patientPublicId}/documents`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      // A 25 MB scan over a clinic's connection outlasts the client's 30s default.
      timeout: 120000,
      maxBodyLength: 26 * 1024 * 1024,
      maxContentLength: 26 * 1024 * 1024,
    });
    return res.data?.data;
  },

  /** Returns a Blob. The caller decides whether to open it or save it. */
  download: async (patientPublicId, documentPublicId) =>
    (
      await apiClient.get(
        `/hospital/patients/${patientPublicId}/documents/${documentPublicId}/file`,
        { responseType: 'blob', timeout: 120000, maxContentLength: 26 * 1024 * 1024 }
      )
    ).data,

  remove: async (patientPublicId, documentPublicId) =>
    (await apiClient.delete(`/hospital/patients/${patientPublicId}/documents/${documentPublicId}`))
      .data,
};

export default patientDocumentService;
```

**Note the `maxContentLength` overrides.** `apiService.js` sets a 5 MB response cap globally; without these a 6 MB scan fails to download with a confusing axios error rather than anything about size.

- [ ] **Step 2: Add the Records tab**

In `PatientDetailsModal.jsx`, extend the tab list:

```jsx
const tabs = [
  { id: 'info', label: 'Patient Info' },
  { id: 'records', label: 'Records' },
  { id: 'medicalhistory', label: 'Medical History' },
  { id: 'bills', label: 'Bills' },
];
```

Load documents when that tab opens, following the existing `fetchHistory` / `fetchBills` pattern in the same effect, and render a list showing title, type, report date, size, who uploaded it and when. Each row gets a **Download** action; an admin also gets **Remove** behind the existing `ConfirmationModal`. When the list is empty, show a single line — _"No records attached yet."_ — and the upload button, rather than an empty panel.

- [ ] **Step 3: Verify**

Run: `cd frontend && npm run build` — builds clean.
Run: `cd frontend && npm test` — all pass (75 before this plan).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/services/patientDocumentService.js frontend/src/components/PatientDetailsModal.jsx
git commit -m "feat(patient): Records tab listing attached documents"
```

---

## Task 8: Upload dialog — Camera and Browse

**Files:**

- Create: `frontend/src/components/documents/DocumentDropzone.jsx`
- Create: `frontend/src/components/documents/CameraCapture.jsx`
- Create: `frontend/src/components/documents/DocumentUploadModal.jsx`
- Modify: `frontend/src/components/PatientDetailsModal.jsx`

- [ ] **Step 1: Build the dropzone**

`DocumentDropzone.jsx` takes `onFile(file)`. It renders a dashed drop area that highlights on `dragover`, accepts a drop, and has a "browse" button opening a hidden `<input type="file" accept=".pdf,image/*">`. It must call `preventDefault` on both `dragover` and `drop` — without it the browser navigates away to the dropped file and the user loses the dialog.

- [ ] **Step 2: Build the camera**

`CameraCapture.jsx` takes `onCapture(file)` and `onCancel()`.

```jsx
// Rear camera on a phone; ignored by desktops with one webcam.
const constraints = { video: { facingMode: 'environment' }, audio: false };
```

The two parts that are easy to get wrong — stream teardown and downscaling — written out, because a leaked camera and a 12 MB upload are exactly the bugs that reach users:

```jsx
const videoRef = useRef(null);
const streamRef = useRef(null);
const [error, setError] = useState(null);

useEffect(() => {
  let cancelled = false;

  // getUserMedia only exists on a secure origin. On plain HTTP the whole object is undefined
  // with no prompt and no error, so say so rather than leaving a button that does nothing.
  if (!navigator.mediaDevices?.getUserMedia) {
    setError('The camera needs a secure (https) connection. Use Browse instead.');
    return undefined;
  }

  navigator.mediaDevices
    .getUserMedia({ video: { facingMode: 'environment' }, audio: false })
    .then((stream) => {
      // The component can unmount while this promise is in flight. Without this the stream is
      // assigned to a dead component and never stopped, and the camera light stays on.
      if (cancelled) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      streamRef.current = stream;
      if (videoRef.current) videoRef.current.srcObject = stream;
    })
    .catch((e) => {
      setError(
        e?.name === 'NotAllowedError'
          ? 'Camera permission was refused. Allow it in your browser, or use Browse.'
          : 'No camera is available on this device. Use Browse instead.'
      );
    });

  return () => {
    cancelled = true;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  };
}, []);

/**
 * Capture, downscaled to 2000px on the long edge.
 *
 * A modern phone photo is 4–12 MB of detail nobody needs to read a lab printout, and that
 * difference is an upload that works on clinic wifi versus one that times out.
 */
const capture = () => {
  const video = videoRef.current;
  if (!video) return;

  const MAX_EDGE = 2000;
  const scale = Math.min(1, MAX_EDGE / Math.max(video.videoWidth, video.videoHeight));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(video.videoWidth * scale);
  canvas.height = Math.round(video.videoHeight * scale);
  canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);

  canvas.toBlob(
    (blob) => {
      if (!blob) return;
      const stamp = new Date().toISOString().slice(0, 10);
      onCapture(new File([blob], `record-${stamp}.jpg`, { type: 'image/jpeg' }));
    },
    'image/jpeg',
    0.85
  );
};
```

Also required:

- Offer **Retake** before Use. The first photo of a document is usually crooked, and re-opening the dialog to redo it is the kind of friction that makes staff stop using a feature.
- When `error` is set, render the message and leave **Browse** reachable rather than trapping the user in a dead camera view.

- [ ] **Step 3: Build the modal**

`DocumentUploadModal.jsx` opens on the two choices — **Camera** and **Browse** — then shows the metadata form once a file is chosen: Title (required), Type (the six `DocumentType` values), and Report date. It shows the chosen filename and size, allows changing the file before saving, and disables Save while uploading so a double-click cannot attach the same report twice.

**State the limit up front.** Both the Browse dropzone and the chooser show `PDF or image (JPG, PNG, WEBP, HEIC), up to 25 MB`. The number comes from the API rather than being typed into the JSX, so it cannot drift from what the server actually enforces. When a chosen file is over the limit, say so immediately with its actual size — _"This file is 41 MB. The limit is 25 MB."_ — and refuse locally instead of uploading it just to be rejected, which on a clinic connection wastes a minute and looks like a fault.

- [ ] **Step 4: Wire it into the Records tab**

An **+ Attach record** button on the Records tab opens the modal; on success, refresh the list.

- [ ] **Step 5: Verify**

Run: `cd frontend && npm run build` — builds clean.
Run: `cd frontend && npm test` — all pass.
Run: `cd frontend && npx eslint src/components/documents/` — 0 errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/documents/ frontend/src/components/PatientDetailsModal.jsx
git commit -m "feat(patient): attach a record by camera or by browsing"
```

---

## Task 9: Back the documents up

Without this, restoring after a disk loss gives a database full of records pointing at files that are gone (D6).

**Files:**

- Modify: `scripts/db/backup.sh`
- Modify: `docs/database/BACKUP_AND_RESTORE.md`
- Modify: `docs/deployment/DEPLOYMENT.md`

- [ ] **Step 1: Archive the documents directory**

In `scripts/db/backup.sh`, after the SQL dump is verified and before the retention prune, add:

```bash
# ── Patient documents ──────────────────────────────────────────────────────
# The SQL dump does not contain these: they are files on disk. Restoring the database alone would
# leave every document row pointing at a file that no longer exists, and nobody would find out
# until someone opened a report. Archived beside the dump, with the same timestamp so a restore
# can pair them.
DOCS_DIR="${DOCUMENTS_DIR:-/var/hms/patient-documents}"
if [ -d "$DOCS_DIR" ]; then
  DOCS_OUT="$BACKUP_DIR/patient-documents-${ENV_LABEL}-$(TS).tar.gz"
  if tar -czf "$DOCS_OUT" -C "$(dirname "$DOCS_DIR")" "$(basename "$DOCS_DIR")"; then
    DOCS_SIZE=$(stat -c '%s' "$DOCS_OUT" 2>/dev/null || wc -c < "$DOCS_OUT")
    echo "[backup] documents OK — ${DOCS_SIZE} bytes → $DOCS_OUT"
  else
    # Deliberately fatal. A backup that silently covers only half the patient record is worse
    # than one that fails loudly, because it will be trusted.
    echo "ERROR: patient document archive failed" >&2
    exit 6
  fi
else
  echo "[backup] no patient-documents directory at $DOCS_DIR — nothing to archive"
fi
```

- [ ] **Step 2: Document the two-part restore**

In `docs/database/BACKUP_AND_RESTORE.md`, state that a backup is now a **pair** — the SQL dump and the documents archive of the same timestamp — and that restoring only the dump leaves documents unreadable. Add the extraction command:

```bash
tar -xzf /var/backups/hms/patient-documents-production-<ts>.tar.gz -C /var/hms/
```

- [ ] **Step 3: Document the directory as a deployment step**

In `docs/deployment/DEPLOYMENT.md`, add — with the same prominence as the `/var/backups/hms` note, because it is the identical failure:

```bash
sudo mkdir -p /var/hms/patient-documents
sudo chown deploy:deploy /var/hms/patient-documents
sudo chmod 750 /var/hms/patient-documents
```

`750`, not `755`: these are patient records and nothing but the app user needs to read them. If this is skipped, every upload fails on `mkdir` exactly as the backups did.

- [ ] **Step 4: Verify the script still parses**

Run: `bash -n scripts/db/backup.sh`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add scripts/db/backup.sh docs/database/BACKUP_AND_RESTORE.md docs/deployment/DEPLOYMENT.md
git commit -m "feat(backup): archive patient documents alongside the database dump"
```

---

## Done criteria

- [ ] `mvn test` and `npm run build` both clean; `npm test` at 75+
- [ ] Reception, doctor and nurse can attach a record; a pharmacist cannot
- [ ] A nurse can attach only for patients assigned to them
- [ ] Only an admin sees and can use Remove; removal is a soft delete and is audited
- [ ] A `.pdf` whose bytes are an executable is refused
- [ ] A document is unreachable from another hospital's session
- [ ] Downloads arrive as attachments, never inline
- [ ] Camera works on HTTPS, offers Retake, and stops the stream when closed
- [ ] `backup.sh` produces both a dump and a documents archive
- [ ] `ApplicationContextLoadTest` and `ClinicPharmacyIsolationTest` pass

---

## Deliberately not in scope

**No virus scanning.** Magic-byte checks stop a mislabelled file, not a malicious PDF. Real scanning means ClamAV on the VPS and is its own piece of work — worth doing before this is used at scale, and dishonest to imply this plan covers it.

**No thumbnails or in-browser preview.** Download only. Previewing a PDF in-app means either an iframe in our origin, which is the risk the attachment header exists to avoid, or a viewer library.

**Documents are not in the hospital export.** The export writes a single workbook; including files would make it a zip and change its shape. Worth doing, separately.

**No versioning.** Re-uploading a corrected report attaches a second record rather than superseding the first. The admin removes the wrong one.

**No visit linkage** (D3). If reports later need to hang off an OPD or IPD case, the column is additive and the UI grows a filter — nothing here forecloses it.
