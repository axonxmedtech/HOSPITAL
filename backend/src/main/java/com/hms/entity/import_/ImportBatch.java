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
    @Column(name = "file_sha256", nullable = false, columnDefinition = "CHAR(64)")
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

    /*
     * Database-derived (Flyway V24): 'active_marker' is a MySQL virtual generated column:
     *   TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('RUNNING','COMPLETED','PARTIAL') THEN 1 END) VIRTUAL
     * backing the database uniqueness index:
     *   UNIQUE KEY uk_import_batch_active (hospital_id, file_sha256, active_marker)
     *
     * It allows at most one live batch per file per hospital while FAILED and UNDONE stay retryable.
     * The application never reads or writes this column directly.
     *
     * Exactly like 'active_phone_key' in Patient (Flyway V21), this column is deliberately NOT mapped
     * as an entity field. If mapped (even with insertable=false, updatable=false), Hibernate's
     * ddl-auto=update compares Java Integer (Types.INTEGER) to MySQL TINYINT (Types.TINYINT) and attempts:
     *   ALTER TABLE import_batches MODIFY COLUMN active_marker INTEGER;
     * which MySQL 8 rejects with ER_UNSUPPORTED_ACTION (MySQL error 3106: "Changing the STORED status
     * is not supported for generated columns").
     *
     * Leaving it unmapped allows MySQL to compute the virtual column on INSERT/UPDATE while Hibernate
     * schema updates cleanly ignore it without producing conflicting DDL.
     */

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }

    public boolean isUndoable() {
        return status != null && status.isUndoable();
    }
}
