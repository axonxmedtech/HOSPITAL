package com.hms.dto.import_;

import java.util.List;

public record ImportPreview(
        int totalRows,
        int createCount,
        int updateCount,
        int skipCount,
        int errorCount,
        List<String> unmappedHeaders,
        List<String> warnings,
        List<PreviewError> errors,
        List<String> sampleNames
) {
    public record PreviewError(int rowNumber, String columnName, String message) { }
}
