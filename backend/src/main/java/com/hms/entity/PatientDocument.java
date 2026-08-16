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
