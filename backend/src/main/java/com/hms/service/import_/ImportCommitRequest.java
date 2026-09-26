package com.hms.service.import_;

import java.util.Map;
import java.util.List;
import java.util.Objects;

/**
 * Trusted context for a commit. The hospital and the actor are the authenticated caller's — the
 * orchestration layer never reads either from the file.
 */
public record ImportCommitRequest(Long hospitalId, String createdBy, String sourceFilename, String sheetName, Map<String, String> mapping, List<String> excludedColumns) {
    public ImportCommitRequest(Long hospitalId, String createdBy, String sourceFilename, String sheetName, Map<String, String> mapping) {
        this(hospitalId, createdBy, sourceFilename, sheetName, mapping, List.of());
    }
    public ImportCommitRequest {
        Objects.requireNonNull(hospitalId, "hospitalId comes from the authenticated context");
        mapping = Map.copyOf(mapping);
        excludedColumns = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(excludedColumns));
    }
}
