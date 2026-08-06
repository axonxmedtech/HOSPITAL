package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_batch")
@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
