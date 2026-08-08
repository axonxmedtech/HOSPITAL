package com.hms.service.import_;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportBatch;
import com.hms.entity.ImportEntityType;
import com.hms.entity.ImportRowError;
import com.hms.entity.ImportStatus;
import com.hms.entity.Patient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates a dry run and (Task 9) a commit. dryRun() writes nothing at all — that is the
 * safety property the whole feature rests on, and it is asserted directly in the tests.
 */
@Service
public class ImportEngine {

    private static final int SAMPLE_SIZE = 10;
    private static final int MAX_PREVIEW_ERRORS = 500;

    private final Map<ImportEntityType, EntityImporter> importers = new EnumMap<>(ImportEntityType.class);
    private final ColumnMapper columnMapper;
    private final com.hms.repository.PatientRepository patientRepository;
    private final com.hms.repository.ImportRowErrorRepository rowErrorRepository;

    public ImportEngine(List<EntityImporter> entityImporters,
                        ColumnMapper columnMapper,
                        com.hms.repository.PatientRepository patientRepository,
                        com.hms.repository.ImportRowErrorRepository rowErrorRepository) {
        for (EntityImporter i : entityImporters) {
            importers.put(i.entityType(), i);
        }
        this.columnMapper = columnMapper;
        this.patientRepository = patientRepository;
        this.rowErrorRepository = rowErrorRepository;
    }

    public EntityImporter importerFor(ImportEntityType type) {
        EntityImporter i = importers.get(type);
        if (i == null) {
            throw new IllegalArgumentException("No importer is available for " + type);
        }
        return i;
    }

    public ImportPreview dryRun(ParsedSheet sheet, Map<String, String> mapping, Long hospitalId) {
        EntityImporter importer = importerFor(ImportEntityType.PATIENT);
        List<String> unmapped = columnMapper.unmapped(sheet.headers(), mapping);

        int create = 0, update = 0, skip = 0, error = 0;
        List<ImportPreview.PreviewError> errors = new ArrayList<>();
        List<String> samples = new ArrayList<>();

        int rowNumber = 1; // row 1 is the header
        for (Map<String, String> row : sheet.rows()) {
            rowNumber++;
            RowOutcome outcome = importer.evaluate(row, mapping, unmapped, hospitalId, rowNumber);
            switch (outcome.action()) {
                case CREATE -> {
                    create++;
                    if (samples.size() < SAMPLE_SIZE) samples.add(outcome.patient().getName());
                }
                case UPDATE -> update++;
                case SKIP -> skip++;
                case ERROR -> {
                    error++;
                    if (errors.size() < MAX_PREVIEW_ERRORS) {
                        errors.add(new ImportPreview.PreviewError(
                                rowNumber, outcome.columnName(), outcome.message()));
                    }
                }
            }
        }

        List<String> warnings = new ArrayList<>();
        if (!mapping.containsValue("legacyId")) {
            warnings.add("No old patient ID (MRN) column is mapped. Re-uploading this file after "
                    + "committing cannot match existing records and would create duplicates. Correct "
                    + "any problems before committing, or re-upload only the failed rows.");
        }
        if (!unmapped.isEmpty()) {
            warnings.add(unmapped.size() + " column(s) will be preserved as Imported information: "
                    + String.join(", ", unmapped));
        }
        if (mapping.containsValue("dateOfBirth")) {
            warnings.add("Dates were read day-first (DD/MM/YYYY), so 03/04/1977 means 3 April 1977. "
                    + "Check a few dates in the preview below if your old system used month-first.");
        }

        return new ImportPreview(sheet.rows().size(), create, update, skip, error,
                unmapped, warnings, errors, samples);
    }

    /** Rows per transaction. Bounds memory and limits how much a mid-run crash leaves half-done. */
    private static final int CHUNK_SIZE = 500;

    /**
     * Applies the sheet for real. Row-level problems are recorded and the run continues — one bad
     * row out of 8,000 must not cost the other 7,999. Counts land on the batch.
     *
     * <p><b>Crash behaviour is deliberate.</b> If a chunk fails to save, the exception propagates so
     * the caller can mark the batch FAILED, but the counts and the row errors gathered so far are
     * flushed first. Without that, a mid-run failure left the batch showing zero rows imported and
     * discarded every error collected up to that point — the admin would be told nothing about a
     * run that had in fact already written thousands of records, and undo would be their only
     * recourse with no report explaining why. Counts reflect rows actually persisted, not rows
     * merely tallied, so an interrupted run reports the truth.
     */
    public void commit(ParsedSheet sheet, Map<String, String> mapping, ImportBatch batch) {

        EntityImporter importer = importerFor(batch.getEntityType());
        List<String> unmapped = columnMapper.unmapped(sheet.headers(), mapping);

        batch.setStatus(ImportStatus.RUNNING);
        batch.setTotalRows(sheet.rows().size());

        List<Patient> pending = new ArrayList<>();
        List<ImportRowError> rowErrors = new ArrayList<>();
        // Persisted totals, only advanced once a chunk has actually been written.
        int[] totals = new int[]{0, 0, 0, 0}; // created, updated, skipped, failed
        int[] inChunk = new int[]{0, 0};      // created, updated awaiting flush

        try {
            int rowNumber = 1; // row 1 is the header
            for (Map<String, String> row : sheet.rows()) {
                rowNumber++;
                RowOutcome outcome;
                try {
                    outcome = importer.evaluate(row, mapping, unmapped, batch.getHospitalId(), rowNumber);
                } catch (Exception e) {
                    outcome = RowOutcome.error(null, "Unexpected problem: " + e.getMessage());
                }

                switch (outcome.action()) {
                    case CREATE -> {
                        Patient p = outcome.patient();
                        p.setImportBatchId(batch.getId());
                        p.setIsActive(true);
                        pending.add(p);
                        inChunk[0]++;
                    }
                    case UPDATE -> {
                        Patient p = outcome.patient();
                        p.setImportBatchId(batch.getId());
                        p.setIsActive(true);
                        pending.add(p);
                        inChunk[1]++;
                    }
                    case SKIP -> totals[2]++;
                    case ERROR -> {
                        totals[3]++;
                        rowErrors.add(new ImportRowError(batch.getId(), rowNumber,
                                outcome.columnName(), outcome.message(), toJson(row)));
                    }
                }

                if (pending.size() >= CHUNK_SIZE) {
                    patientRepository.saveAll(pending);
                    pending.clear();
                    totals[0] += inChunk[0];
                    totals[1] += inChunk[1];
                    inChunk[0] = 0;
                    inChunk[1] = 0;
                }
                // Flush errors on the same cadence so a later crash cannot discard them.
                if (rowErrors.size() >= CHUNK_SIZE) {
                    rowErrorRepository.saveAll(rowErrors);
                    rowErrors.clear();
                }
            }

            if (!pending.isEmpty()) {
                patientRepository.saveAll(pending);
                totals[0] += inChunk[0];
                totals[1] += inChunk[1];
                inChunk[0] = 0;
                inChunk[1] = 0;
            }

            batch.setCommittedAt(LocalDateTime.now());
            batch.setStatus(ImportStatus.COMPLETED);
        } finally {
            // Runs on the happy path and on the way out of a failure, so the batch never sits at
            // RUNNING with stale zeroes and the error report survives.
            if (!rowErrors.isEmpty()) {
                try {
                    rowErrorRepository.saveAll(rowErrors);
                } catch (Exception ignored) {
                    // Never let error-report bookkeeping mask the original failure.
                }
            }
            batch.setCreatedCount(totals[0]);
            batch.setUpdatedCount(totals[1]);
            batch.setSkippedCount(totals[2]);
            batch.setFailedCount(totals[3]);
        }
    }

    private String toJson(Map<String, String> row) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (!first) sb.append(",");
            sb.append(quote(e.getKey())).append(":").append(quote(e.getValue()));
            first = false;
        }
        return sb.append("}").toString();
    }

    private String quote(String raw) {
        String escaped = raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
