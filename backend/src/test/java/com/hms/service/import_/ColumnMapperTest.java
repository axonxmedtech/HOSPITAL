package com.hms.service.import_;

import com.hms.entity.ImportEntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnMapperTest {

    private final ColumnMapper mapper = new ColumnMapper(new ImportFieldRegistry());

    @Test
    void matchesHeadersToFieldsCaseAndSpaceInsensitively() {
        Map<String, String> m = mapper.suggest(
                List.of("Patient Name", "MOB NO", "  DOB  ", "MRN"), ImportEntityType.PATIENT);
        assertThat(m).containsEntry("Patient Name", "name");
        assertThat(m).containsEntry("MOB NO", "phone");
        assertThat(m).containsEntry("  DOB  ", "dateOfBirth");
        assertThat(m).containsEntry("MRN", "legacyId");
    }

    @Test
    void leavesUnknownHeadersUnmapped() {
        Map<String, String> m = mapper.suggest(List.of("Referred By", "Caste"), ImportEntityType.PATIENT);
        assertThat(m).isEmpty();
    }

    @Test
    void neverMapsTwoHeadersToTheSameField() {
        Map<String, String> m = mapper.suggest(List.of("Name", "Patient Name"), ImportEntityType.PATIENT);
        assertThat(m.values()).containsExactly("name");
    }

    @Test
    void reportsHeadersThatWillBecomeCustomFields() {
        List<String> headers = List.of("Name", "Referred By", "Caste");
        Map<String, String> m = mapper.suggest(headers, ImportEntityType.PATIENT);
        assertThat(mapper.unmapped(headers, m)).containsExactly("Referred By", "Caste");
    }

    @Test
    void matchesHeadersWithPunctuationAfterStripping() {
        Map<String, String> m = mapper.suggest(
                List.of("Mobile No.", "UHID", "D.O.B."), ImportEntityType.PATIENT);
        assertThat(m).containsEntry("Mobile No.", "phone");
        assertThat(m).containsEntry("UHID", "legacyId");
        assertThat(m).containsEntry("D.O.B.", "dateOfBirth");
    }

    @Test
    void punctuationStrippingDoesNotCreateFalseMatches() {
        Map<String, String> m = mapper.suggest(
                List.of("Ref. By", "Blood Grp.", "S/O"), ImportEntityType.PATIENT);
        assertThat(m).isEmpty();
    }
}
