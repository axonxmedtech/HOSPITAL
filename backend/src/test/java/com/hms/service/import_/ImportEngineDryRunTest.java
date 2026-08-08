package com.hms.service.import_;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ImportEngineDryRunTest {

    @Mock PatientRepository patientRepository;

    @Mock com.hms.repository.ImportRowErrorRepository rowErrorRepository;

    private ImportEngine engine() {
        PatientImporter importer = new PatientImporter(patientRepository);
        return new ImportEngine(List.of(importer), new ColumnMapper(new ImportFieldRegistry()),
                patientRepository, rowErrorRepository);
    }

    private ParsedSheet sheet() {
        return new ParsedSheet("Patients",
                List.of("Name", "DOB", "Referred By"),
                List.of(
                        Map.of("Name", "Ramesh", "DOB", "1990-01-01", "Referred By", "Dr. K"),
                        Map.of("Name", "", "DOB", "", "Referred By", ""),
                        Map.of("Name", "Sita", "DOB", "31-02-2020", "Referred By", "")
                ));
    }

    @Test
    void countsOutcomesWithoutWritingAnything() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);

        assertThat(preview.createCount()).isEqualTo(1);
        assertThat(preview.errorCount()).isEqualTo(2);
        verify(patientRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(patientRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(rowErrorRepository);
    }

    @Test
    void errorsCarryRowNumbersAndColumnNames() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);

        assertThat(preview.errors()).extracting("rowNumber").containsExactly(3, 4);
        assertThat(preview.errors().get(1).columnName()).isEqualTo("DOB");
    }

    @Test
    void reportsWhichColumnsWillBecomeCustomFields() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);
        assertThat(preview.unmappedHeaders()).containsExactly("Referred By");
    }

    /**
     * Two rows sharing an MRN both looked new, so both became CREATEs and the second violated the
     * unique (hospital_id, legacy_id) index — failing the whole run, and only sometimes, depending
     * on where the chunk boundary happened to fall. Reported as a skip now so the preview predicts
     * the commit and the admin is told which ID is duplicated.
     */
    @Test
    void reportsARepeatedMrnWithinTheSameFileInsteadOfFailingTheRun() {
        ParsedSheet duplicated = new ParsedSheet("Patients",
                List.of("Name", "MRN"),
                List.of(
                        Map.of("Name", "Ramesh", "MRN", "A-1"),
                        Map.of("Name", "Ramesh Patel", "MRN", "A-1")));

        ImportPreview preview = engine().dryRun(duplicated,
                Map.of("Name", "name", "MRN", "legacyId"), 7L);

        assertThat(preview.createCount()).isEqualTo(1);
        assertThat(preview.skipCount()).isEqualTo(1);
    }

    @Test
    void warnsWhenNoLegacyIdIsMappedBecauseReuploadCannotMatch() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);
        assertThat(preview.warnings()).anyMatch(w -> w.toLowerCase().contains("re-upload"));
    }

    /**
     * PatientImporter parses dates day-first (see its DATE_FORMATS doc comment): "03/04/1977"
     * means 3 April, not 4 March. That is a silent assumption baked into the parser, and a wrong
     * date of birth is a real clinical problem, so dryRun must surface it before anything commits.
     */
    @Test
    void warnsAboutDayFirstDateParsingWhenDateOfBirthIsMapped() {
        ImportPreview preview = engine().dryRun(sheet(),
                Map.of("Name", "name", "DOB", "dateOfBirth"), 7L);
        assertThat(preview.warnings()).anyMatch(w -> w.toLowerCase().contains("day-first"));
    }

    @Test
    void doesNotWarnAboutDateParsingWhenDateOfBirthIsNotMapped() {
        ImportPreview preview = engine().dryRun(sheet(), Map.of("Name", "name"), 7L);
        assertThat(preview.warnings()).noneMatch(w -> w.toLowerCase().contains("day-first"));
    }
}
