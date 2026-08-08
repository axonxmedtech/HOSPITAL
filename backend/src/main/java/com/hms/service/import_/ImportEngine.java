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
        Set<String> seenLegacyIds = new HashSet<>();

        int create = 0, update = 0, skip = 0, error = 0;
        List<ImportPreview.PreviewError> errors = new ArrayList<>();
        List<String> samples = new ArrayList<>();

        int rowNumber = 1; // row 1 is the header
        for (Map<String, String> row : sheet.rows()) {
            rowNumber++;
            RowOutcome outcome = importer.evaluate(row, mapping, unmapped, hospitalId, rowNumber);
            outcome = rejectRepeatWithinFile(outcome, seenLegacyIds);
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
        // Lead with the problems. Counts alone are easy to skim past, and an admin about to write
        // thousands of patient records should be told plainly what will not go in before they are
        // told what will.
        if (error > 0) {
            warnings.add(error + " row(s) have problems and will NOT be imported. Review the list "
                    + "below, correct them in the file, and re-upload before committing — or commit "
                    + "now and fix them afterwards using the downloadable error report.");
        }
        if (skip > 0) {
            warnings.add(skip + " row(s) will be skipped — either they duplicate another row in this "
                    + "file, they match more than one existing patient, or the patient has been "
                    + "edited in the system since the last import and staff changes take precedence.");
        }
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

    /**
     * Rows written per {@code saveAll}. Bounds how much sits unflushed in memory at once and how
     * much a mid-run failure leaves unaccounted for. Note this is a batching size, not a
     * transaction boundary — each repository call manages its own transaction.
     */
    private static final int CHUNK_SIZE = 500;

    /**
     * Turns a second occurrence of the same MRN within one file into a reported skip.
     *
     * <p>Two rows sharing an MRN both looked new (neither is in the database yet), so both became
     * CREATEs and the second violated the unique {@code (hospital_id, legacy_id)} index — failing
     * the whole run. Worse, whether it failed at all depended on where the chunk boundary fell,
     * so the same file could behave differently on different days and the dry-run could not
     * predict the commit.
     *
     * <p>Reporting rather than silently taking the first is deliberate: duplicate MRNs in a source
     * file usually mean two different people were given the same number, and merging them would be
     * unrecoverable.
     */
    private RowOutcome rejectRepeatWithinFile(RowOutcome outcome, Set<String> seenLegacyIds) {
        if (outcome.action() != RowOutcome.Action.CREATE
                && outcome.action() != RowOutcome.Action.UPDATE) {
            return outcome;
        }
        String legacyId = outcome.patient() == null ? null : outcome.patient().getLegacyId();
        if (legacyId == null) {
            return outcome;
        }
        if (!seenLegacyIds.add(legacyId)) {
            return RowOutcome.skip("The old patient ID \"" + legacyId + "\" appears more than once "
                    + "in this file. Only the first row was used; resolve the duplicate and re-import "
                    + "this one.");
        }
        return outcome;
    }

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
        Set<String> seenLegacyIds = new HashSet<>();
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
                outcome = rejectRepeatWithinFile(outcome, seenLegacyIds);

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
                        // Deliberately NOT stamped with this batch's id. import_batch_id means
                        // "this batch created this row", and undo soft-deletes everything carrying
                        // it. Stamping updates too meant undoing a re-import would deactivate every
                        // patient the file merely matched — including ones reception registered by
                        // hand months earlier. Undo reverses what an import added; it does not
                        // revert edits it made to records that already existed.
                        //
                        // Reactivation is likewise narrow: only rows a previous import soft-deleted
                        // (they still carry an import_batch_id) come back. A patient an admin
                        // deliberately deactivated is left alone rather than resurrected by a
                        // routine re-import.
                        if (p.getImportBatchId() != null && !Boolean.TRUE.equals(p.getIsActive())) {
                            p.setIsActive(true);
                        }
                        pending.add(p);
                        inChunk[1]++;
                    }
                    case SKIP -> totals[2]++;
                    case ERROR -> {
                        totals[3]++;
                        rowErrors.add(new ImportRowError(batch.getId(), rowNumber,
                                clampColumn(outcome.columnName(), 120),
                                clampColumn(outcome.message(), 500), toJson(row)));
                    }
                }

                if (pending.size() >= CHUNK_SIZE) {
                    savePendingChunk(pending);
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
                savePendingChunk(pending);
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

    /**
     * Persists a chunk and assigns the hospital-facing patient number to anything new.
     *
     * <p>{@code customId} is the {@code PAT<id>} identifier reception sees in patient lists, on case
     * papers and in search. It derives from the auto-increment id, so it cannot be set before the
     * insert — {@code PatientService} does the same two-step for manually created patients. The
     * importer bypasses that service, so without this every imported patient would carry a null
     * patient number and show as blank throughout the UI.
     *
     * <p>Only rows that lack one are re-saved, so re-importing an existing patient does not churn
     * their identifier.
     */
    private void savePendingChunk(List<Patient> pending) {
        List<Patient> saved = patientRepository.saveAll(pending);

        List<Patient> needingCustomId = new ArrayList<>();
        for (Patient p : saved) {
            if (p.getCustomId() == null || p.getCustomId().isBlank()) {
                p.setCustomId("PAT" + p.getId());
                needingCustomId.add(p);
            }
        }
        if (!needingCustomId.isEmpty()) {
            patientRepository.saveAll(needingCustomId);
        }
    }

    /**
     * Keeps a value inside its column width. message is VARCHAR(500) and is built from raw cell
     * values and exception text, so an unusually long one would either abort the import or be
     * swallowed by the finally block - losing the entire error report over one verbose row.
     */
    private String clampColumn(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
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
