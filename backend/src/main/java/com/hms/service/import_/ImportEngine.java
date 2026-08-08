package com.hms.service.import_;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportEntityType;
import org.springframework.stereotype.Service;

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
}
