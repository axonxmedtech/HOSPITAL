package com.hms.service.import_;

import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportSource;
import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientImporterTest {

    @Mock PatientRepository patientRepository;

    private PatientImporter importer() {
        return new PatientImporter(patientRepository);
    }

    private Map<String, String> mapping() {
        return Map.of("Name", "name", "Gender", "gender", "Mob No", "phone", "MRN", "legacyId");
    }

    @Test
    void keepsBlankValuesBlankRatherThanRejecting() {
        Map<String, String> row = Map.of("Name", "Sita Rao", "Gender", "", "Mob No", "", "MRN", "A-1");
        RowOutcome outcome = importer().evaluate(row, mapping(), java.util.List.of(), 7L, 2);
        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.CREATE);
        Patient p = outcome.patient();
        assertThat(p.getName()).isEqualTo("Sita Rao");
        assertThat(p.getGender()).isNull();
        assertThat(p.getPhone()).isNull();
        assertThat(p.getSource()).isEqualTo(ImportSource.IMPORTED);
    }

    @Test
    void storesOddlyFormattedPhoneVerbatim() {
        Map<String, String> row = Map.of("Name", "R", "Gender", "M", "Mob No", "+91 98765 43210", "MRN", "A-2");
        RowOutcome outcome = importer().evaluate(row, mapping(), java.util.List.of(), 7L, 2);
        assertThat(outcome.patient().getPhone()).isEqualTo("+91 98765 43210");
    }

    @Test
    void rejectsARowWithNoName() {
        Map<String, String> row = Map.of("Name", "", "MRN", "A-3");
        RowOutcome outcome = importer().evaluate(row, mapping(), java.util.List.of(), 7L, 4);
        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.ERROR);
        assertThat(outcome.message()).contains("Full name");
    }

    @Test
    void reportsAnUnparseableDateAsARowErrorNamingTheColumn() {
        Map<String, String> row = Map.of("Name", "R", "DOB", "31-02-2020");
        RowOutcome outcome = importer().evaluate(row, Map.of("Name", "name", "DOB", "dateOfBirth"),
                java.util.List.of(), 7L, 5);
        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.ERROR);
        assertThat(outcome.columnName()).isEqualTo("DOB");
    }

    /**
     * Day 31 in a 30-day month. Java's default SMART resolver silently clamps this to the 30th and
     * returns a date rather than throwing, which would write a wrong date of birth into a patient
     * record with nothing to notice. The formatters use ResolverStyle.STRICT to reject it instead.
     */
    @Test
    void rejectsAnOverflowingDayInsteadOfSilentlyClampingIt() {
        Map<String, String> row = Map.of("Name", "R", "DOB", "31-04-2020");
        RowOutcome outcome = importer().evaluate(row, Map.of("Name", "name", "DOB", "dateOfBirth"),
                java.util.List.of(), 7L, 5);
        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.ERROR);
        assertThat(outcome.columnName()).isEqualTo("DOB");
    }

    /**
     * Guards the other half of the STRICT change. ResolverStyle.STRICT cannot resolve the pattern
     * symbol `yyyy` (year-of-era) without an era field, so the formatters had to switch to `uuuu`.
     * Get that wrong and every date fails to parse while the rejection tests above still pass —
     * this is the test that catches it.
     */
    @Test
    void stillParsesEverySupportedValidDateFormat() {
        Map<String, String> mapping = Map.of("Name", "name", "DOB", "dateOfBirth");

        assertThat(importer().evaluate(Map.of("Name", "R", "DOB", "1985-04-11"), mapping,
                java.util.List.of(), 7L, 2).patient().getDateOfBirth())
                .isEqualTo(java.time.LocalDate.of(1985, 4, 11));

        assertThat(importer().evaluate(Map.of("Name", "R", "DOB", "11-04-1985"), mapping,
                java.util.List.of(), 7L, 3).patient().getDateOfBirth())
                .isEqualTo(java.time.LocalDate.of(1985, 4, 11));

        // Day-first is deliberate: 12/03/1977 is 12 March, not 3 December.
        assertThat(importer().evaluate(Map.of("Name", "R", "DOB", "12/03/1977"), mapping,
                java.util.List.of(), 7L, 4).patient().getDateOfBirth())
                .isEqualTo(java.time.LocalDate.of(1977, 3, 12));

        assertThat(importer().evaluate(Map.of("Name", "R", "DOB", "5-7-1990"), mapping,
                java.util.List.of(), 7L, 5).patient().getDateOfBirth())
                .isEqualTo(java.time.LocalDate.of(1990, 7, 5));
    }

    /** Jackson replaced hand-rolled escaping; this pins that quotes and newlines survive a round trip. */
    @Test
    void customFieldsAreValidJsonEvenWithAwkwardCharacters() throws Exception {
        Map<String, String> row = Map.of("Name", "R", "Referred By", "Dr. \"Bob\" O'Neil\nClinic");
        RowOutcome outcome = importer().evaluate(row, Map.of("Name", "name"),
                java.util.List.of("Referred By"), 7L, 3);

        com.fasterxml.jackson.databind.JsonNode parsed =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(outcome.patient().getCustomFields());
        assertThat(parsed.get("Referred By").asText()).isEqualTo("Dr. \"Bob\" O'Neil\nClinic");
    }

    @Test
    void matchesAnExistingPatientOnLegacyIdAndUpdatesInsteadOfInserting() {
        Patient existing = new Patient();
        existing.setId(55L);
        existing.setLegacyId("A-9");
        when(patientRepository.findByHospitalIdAndLegacyId(eq(7L), eq("A-9")))
                .thenReturn(Optional.of(existing));

        Map<String, String> row = Map.of("Name", "Updated Name", "MRN", "A-9");
        RowOutcome outcome = importer().evaluate(row, Map.of("Name", "name", "MRN", "legacyId"),
                java.util.List.of(), 7L, 6);

        assertThat(outcome.action()).isEqualTo(RowOutcome.Action.UPDATE);
        assertThat(outcome.patient().getId()).isEqualTo(55L);
        assertThat(outcome.patient().getName()).isEqualTo("Updated Name");
    }

    @Test
    void preservesUnmappedColumnsAsJsonCustomFields() {
        Map<String, String> row = Map.of("Name", "R", "Referred By", "Dr. Kulkarni", "Caste", "");
        RowOutcome outcome = importer().evaluate(row, Map.of("Name", "name"),
                java.util.List.of("Referred By", "Caste"), 7L, 3);
        assertThat(outcome.patient().getCustomFields())
                .contains("Referred By").contains("Dr. Kulkarni").contains("Caste");
    }
}
