package com.hms.service.import_;

import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A dry run's answer: counts of what a commit WOULD do, a bounded sample of the rows that need
 * attention, and — if this exact file has a live batch already — that batch, since a commit
 * would be refused. Nothing here came from a write.
 */
public record ImportPreview(
        String sheetName,
        List<String> headers,
        ImportCounters.Snapshot counts,
        List<RowSample> samples,
        boolean samplesTruncated,
        PreviousImport previousImport) {

    public static final int MAX_SAMPLES = 500;

    public record RowSample(int rowNum, ImportRowState state, ImportReasonCode reasonCode, String column, String message, String phoneMasked) {}

    public record PreviousImport(String batchPublicId, ImportStatus status, LocalDateTime committedAt) {}
}
