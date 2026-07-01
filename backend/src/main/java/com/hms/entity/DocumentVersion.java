package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DocumentVersion - Entity tracking logical document revisions (versions),
 * update authors, and direct content references.
 *
 * @author HMS Team
 * @version Phase-0.8
 */
@Entity
@Table(name = "document_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType; // e.g. "CONSENT_GENERAL", "DISCHARGE_SUMMARY"

    @Column(name = "document_id", nullable = false, length = 50)
    private String documentId; // Links logically to the document record ID

    @Column(name = "version", nullable = false)
    private Integer version; // Incremental version number starting at 1

    @Column(name = "content_url", length = 255)
    private String contentUrl; // PDF or file location URL

    @Column(name = "updated_by")
    private Long updatedBy; // Reference to user.id who saved/updated the revision

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
