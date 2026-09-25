package com.hms.dto.import_;

import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import com.hms.entity.import_.ImportStatus;
import com.hms.service.import_.ImportPreview;
import java.time.LocalDateTime;
import java.util.List;

/** The dry run's answer for the client: counts, bounded problem samples (no cell values), and any live batch for the same file. */
public record ImportPreviewResponse(
        String sheetName,
        List<String> headers,
        List<String> sheetNames,
        ImportCountsResponse counts,
        List<Sample> samples,
        boolean samplesTruncated,
        PreviousImport previousImport) {

    public record Sample(int rowNum, ImportRowState state, ImportReasonCode reasonCode, String column, String message, String phoneMasked) {}

    public record PreviousImport(String batchPublicId, ImportStatus status, LocalDateTime committedAt) {}

    public static ImportPreviewResponse from(ImportPreview p, List<String> sheetNames) {
        return new ImportPreviewResponse(
                p.sheetName(),
                p.headers(),
                sheetNames,
                ImportCountsResponse.of(p.counts()),
                p.samples().stream().map(s -> new Sample(s.rowNum(), s.state(), s.reasonCode(), s.column(), s.message(), s.phoneMasked())).toList(),
                p.samplesTruncated(),
                p.previousImport() == null ? null : new PreviousImport(p.previousImport().batchPublicId(), p.previousImport().status(), p.previousImport().committedAt()));
    }
}
