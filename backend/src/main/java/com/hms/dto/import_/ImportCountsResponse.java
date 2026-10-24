package com.hms.dto.import_;

import com.hms.service.import_.ImportCounters;

public record ImportCountsResponse(int total, int created, int updated, int skipped, int needsReview, int failed) {
    public static ImportCountsResponse of(ImportCounters.Snapshot s) {
        return new ImportCountsResponse(s.total(), s.created(), s.updated(), s.skipped(), s.needsReview(), s.failed());
    }
}
