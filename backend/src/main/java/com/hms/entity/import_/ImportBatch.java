package com.hms.entity.import_;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One commit attempt of a legacy import: the unit an administrator sees, retries, undoes and
 * audits. Hospital-owned; identified outward by {@code publicId}, never by the numeric id.
 *
 * <p>Column shape mirrors Flyway V22 exactly. Counts are persisted totals — advanced only once the
 * rows they describe are actually in the database — so an interrupted run reports the truth.
 */
@Entity
@Table(name = "import_batches")
@Data
@NoArgsConstructor
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private ImportEntityType entityType = ImportEntityType.PATIENT;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "sheet_name", length = 255)
    private String sheetName;

    @Column(name = "mapping_json", columnDefinition = "TEXT")
    private String mappingJson;

    /** Hospital-scoped fingerprint of the uploaded bytes; the exact-file retry guard. */
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status = ImportStatus.RUNNING;

    /** Human-readable reason for FAILED, including the system's own ("abandoned: no heartbeat since …"). */
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "needs_review_count", nullable = false)
    private int needsReviewCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Advanced per chunk while RUNNING; the stale-batch detector reads this, nothing else. */
    @Column(name = "heartbeat_at")
    private LocalDateTime heartbeatAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "undone_at")
    private LocalDateTime undoneAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }

    public boolean isUndoable() {
        return status != null && status.isUndoable();
    }
}
