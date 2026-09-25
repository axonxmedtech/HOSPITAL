package com.hms.service.import_;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * What a row write needs from its surroundings and must never take from the file: the tenant
 * (authenticated context), the batch the write belongs to, and the clock.
 */
public record ImportWriteContext(Long hospitalId, Long batchId, LocalDateTime now) {

    public ImportWriteContext {
        Objects.requireNonNull(hospitalId, "hospitalId comes from the authenticated context and is required");
        Objects.requireNonNull(batchId, "batchId is required: every written row belongs to a batch");
        Objects.requireNonNull(now, "now");
    }
}
