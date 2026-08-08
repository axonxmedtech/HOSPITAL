package com.hms.service.import_;

import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.repository.ImportRowErrorRepository;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end dry run against a fixture shaped like data a real hospital actually hands over.
 *
 * <p>Every other test in this package isolates one decision. This one is the only test shaped like
 * a customer's file, and it is where the feature's central promise is checked as a whole: a messy
 * legacy export imports what it can, refuses only what it genuinely cannot read, preserves what the
 * schema does not model, and reports every rejection against the row number the admin sees in Excel.
 *
 * <p>The rows below are the failure modes seen in Indian hospital exports: a missing gender, an
 * international phone prefix, a landline in a mobile column, a blank name, an impossible date, and a
 * column nobody modelled. Blank and awkward must import; only unreadable may fail.
 */
@ExtendWith(MockitoExtension.class)
class MessyLegacyFileTest {

    @Mock PatientRepository patientRepository;
    @Mock ImportRowErrorRepository rowErrorRepository;

    private Map<String, String> row(String name, String gender, String phone, String dob,
                                    String mrn, String referredBy) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Patient Name", name);
        m.put("Sex", gender);
        m.put("Mob No", phone);
        m.put("DOB", dob);
        m.put("MRN", mrn);
        m.put("Referred By", referredBy);
        return m;
    }

    private ParsedSheet messySheet() {
        return new ParsedSheet("Patients",
                List.of("Patient Name", "Sex", "Mob No", "DOB", "MRN", "Referred By"),
                List.of(
                        // row 2 — clean
                        row("Ramesh Patel", "M", "9876543210", "1985-04-11", "A-1", "Dr. Kulkarni"),
                        // row 3 — no gender, no phone, no DOB: blanks must stay blank, not fail
                        row("Sita Rao", "", "", "", "A-2", ""),
                        // row 4 — +91 prefix and a day-first date
                        row("Anil Kumar", "Male", "+91 98765 43210", "12/03/1977", "A-3", ""),
                        // row 5 — no name: the only field the importer insists on
                        row("", "F", "9999999999", "2000-01-01", "A-4", ""),
                        // row 6 — landline in a mobile column (fine) + impossible date (not fine)
                        row("Meena Shah", "F", "022-24445555", "31-02-2020", "A-5", "Walk-in")
                ));
    }

    private ImportEngine engine() {
        return new ImportEngine(List.of(new PatientImporter(patientRepository)),
                new ColumnMapper(new ImportFieldRegistry()), patientRepository, rowErrorRepository);
    }

    private Map<String, String> mapping() {
        return Map.of("Patient Name", "name", "Sex", "gender", "Mob No", "phone",
                "DOB", "dateOfBirth", "MRN", "legacyId");
    }

    @Test
    void importsWhatItCanAndRefusesOnlyWhatItCannotRead() {
        ImportPreview preview = engine().dryRun(messySheet(), mapping(), 7L);

        // Rows 2, 3 and 4 import. Blank gender/phone/DOB, a +91 prefix and a landline are all fine.
        assertThat(preview.createCount()).isEqualTo(3);
        // Only the blank name and the impossible date are refused.
        assertThat(preview.errorCount()).isEqualTo(2);
    }

    @Test
    void rejectionsCarryTheSpreadsheetRowTheAdminSeesInExcel() {
        ImportPreview preview = engine().dryRun(messySheet(), mapping(), 7L);

        assertThat(preview.errors()).extracting("rowNumber").containsExactly(5, 6);
        assertThat(preview.errors().get(1).columnName()).isEqualTo("DOB");
    }

    @Test
    void preservesTheColumnNobodyModelledRatherThanDroppingIt() {
        ImportPreview preview = engine().dryRun(messySheet(), mapping(), 7L);

        assertThat(preview.unmappedHeaders()).containsExactly("Referred By");
        assertThat(preview.warnings())
                .anyMatch(w -> w.contains("Referred By") && w.contains("Imported information"));
    }

    @Test
    void warnsAboutDayFirstDatesBecauseTheImporterIsGuessing() {
        ImportPreview preview = engine().dryRun(messySheet(), mapping(), 7L);

        assertThat(preview.warnings()).anyMatch(w -> w.toLowerCase().contains("day-first"));
    }

    @Test
    void doesNotWarnAboutReuploadWhenAnMrnColumnIsMapped() {
        ImportPreview preview = engine().dryRun(messySheet(), mapping(), 7L);

        assertThat(preview.warnings())
                .noneMatch(w -> w.contains("cannot match existing records"));
    }

    /** The same file with the MRN column left unmapped must warn that a re-upload cannot match. */
    @Test
    void warnsAboutReuploadWhenNoMrnColumnIsMapped() {
        Map<String, String> withoutMrn = Map.of("Patient Name", "name", "Sex", "gender",
                "Mob No", "phone", "DOB", "dateOfBirth");

        ImportPreview preview = engine().dryRun(messySheet(), withoutMrn, 7L);

        assertThat(preview.warnings())
                .anyMatch(w -> w.contains("cannot match existing records"));
        assertThat(preview.unmappedHeaders()).contains("MRN", "Referred By");
    }
}
