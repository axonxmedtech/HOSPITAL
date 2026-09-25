package com.hms.dto.import_;

import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportStatus;
import java.time.LocalDateTime;

/**
 * A batch as the administrator may see it. Deliberately absent: the database id, the hospital
 * id, the SHA-256, the mapping JSON, any row data. {@code failureReason} is one of the fixed
 * system strings, never exception text.
 */
public record ImportBatchStatusResponse(
        String publicId,
        ImportStatus status,
        String sourceFilename,
        String sheetName,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime heartbeatAt,
        LocalDateTime committedAt,
        ImportCountsResponse counts,
        String failureReason) {

    public static ImportBatchStatusResponse from(ImportBatch b) {
        return new ImportBatchStatusResponse(
                b.getPublicId(),
                b.getStatus(),
                b.getSourceFilename(),
                b.getSheetName(),
                b.getCreatedBy(),
                b.getCreatedAt(),
                b.getHeartbeatAt(),
                b.getCommittedAt(),
                new ImportCountsResponse(b.getTotalRows(), b.getCreatedCount(), b.getUpdatedCount(), b.getSkippedCount(), b.getNeedsReviewCount(), b.getFailedCount()),
                b.getFailureReason());
    }
}
