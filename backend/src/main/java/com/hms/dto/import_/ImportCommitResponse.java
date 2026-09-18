package com.hms.dto.import_;

import com.hms.entity.import_.ImportStatus;
import com.hms.service.import_.ImportCommitSummary;
import java.time.LocalDateTime;

/** A finished (synchronous) commit. */
public record ImportCommitResponse(String batchPublicId, ImportStatus status, ImportCountsResponse counts, LocalDateTime committedAt) {
    public static ImportCommitResponse from(ImportCommitSummary s) {
        return new ImportCommitResponse(s.batchPublicId(), s.status(), ImportCountsResponse.of(s.counts()), s.committedAt());
    }
}
