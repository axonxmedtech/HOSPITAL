package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.dto.import_.ImportFieldDef;
import com.hms.entity.import_.ImportEntityType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mapping suggestions are UI assistance: deterministic where a header can only mean one thing,
 * silent — and visibly so — where it could mean two, or where two headers want the same field.
 * And the registry is a closed list: nothing tenant- or server-controlled is ever writable.
 */
class ColumnMapperTest {

    private final ImportFieldRegistry registry = new ImportFieldRegistry();
    private final ColumnMapper mapper = new ColumnMapper(registry);

    @Test
    void deterministicHeadersAreSuggested() {
        MappingSuggestion s = mapper.suggest(
                List.of("Patient Name", "Mobile Number", "DOB", "MRN", "Sex", "E-Mail", "Address", "Mob.No"),
                ImportEntityType.PATIENT);

        // Mob.No and Mobile Number both mean phone → both ambiguous, neither guessed (see below).
        assertThat(s.mapping())
                .containsEntry("Patient Name", "name")
                .containsEntry("DOB", "dateOfBirth")
                .containsEntry("MRN", "legacyId")
                .containsEntry("Sex", "gender")
                .containsEntry("E-Mail", "email")
                .containsEntry("Address", "address")
                .doesNotContainKeys("Mobile Number", "Mob.No");
        assertThat(s.ambiguous()).containsKeys("Mobile Number", "Mob.No");
        assertThat(s.unmapped()).isEmpty();
    }

    @Test
    void matchingIsCaseAndPunctuationInsensitiveButDisplayHeadersAreKept() {
        MappingSuggestion s = mapper.suggest(List.of("  PATIENT-NAME ", "d.o.b", "Reg. No."), ImportEntityType.PATIENT);

        assertThat(s.mapping())
                .containsEntry("  PATIENT-NAME ", "name")
                .containsEntry("d.o.b", "dateOfBirth")
                .containsEntry("Reg. No.", "legacyId");
    }

    @Test
    void ambiguousHeadersStayUnmappedRatherThanGuessed() {
        // "Contact" could be a phone or an email; "Notes"/"Remarks" could be anything; "City" is
        // not an address; "OPD No" is a visit number. None is suggested.
        MappingSuggestion s = mapper.suggest(
                List.of("Name", "Contact", "Notes", "Remarks", "City", "OPD No", "Patient"), ImportEntityType.PATIENT);

        assertThat(s.mapping()).containsOnlyKeys("Name");
        assertThat(s.unmapped()).containsExactly("Contact", "Notes", "Remarks", "City", "OPD No", "Patient");
        assertThat(s.ambiguous()).isEmpty();
    }

    @Test
    void twoHeadersWantingTheSameFieldAreBothReportedNotFirstWins() {
        MappingSuggestion s = mapper.suggest(List.of("Phone", "Name", "Mobile"), ImportEntityType.PATIENT);

        assertThat(s.mapping()).containsOnlyKeys("Name");
        assertThat(s.ambiguous()).containsOnlyKeys("Phone", "Mobile");
        assertThat(s.ambiguous().get("Phone")).contains("phone");
    }

    @Test
    void unmappedListsHeadersAConfirmedMappingDoesNotCover() {
        List<String> headers = List.of("Name", "Phone", "Blood Group", "Caste");
        Map<String, String> confirmed = Map.of("Name", "name", "Phone", "phone");

        assertThat(mapper.unmapped(headers, confirmed)).containsExactly("Blood Group", "Caste");
    }

    @Test
    void suggestionsWorkFromAParsedHeaderToo() throws Exception {
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        try (SpooledUpload upload = ImportTestFiles.csv("Full Name,Mobile No,hospital_id\nT,9000000001,7\n")) {
            new WorkbookParser().parse(upload, ImportFormat.CSV, null, sink);
        }
        MappingSuggestion s = mapper.suggest(sink.header, ImportEntityType.PATIENT);

        assertThat(s.mapping()).containsEntry("Full Name", "name").containsEntry("Mobile No", "phone");
        assertThat(s.unmapped()).containsExactly("hospital_id"); // present in the file, never a target
    }

    @Test
    void forbiddenAndInternalFieldsAreNeverWritable() {
        for (String forbidden : List.of(
                "hospital_id", "hospitalId", "HOSPITAL_ID", "publicId", "public_id", "customId", "id",
                "isActive", "is_active", "status", "duplicate_phone_ack_for", "duplicatePhoneAckFor",
                "duplicate_phone_ack_at", "duplicate_phone_ack_by", "import_batch_id", "importBatchId",
                "anythingElse", "", "Name")) {
            assertThat(registry.isWritable(ImportEntityType.PATIENT, forbidden))
                    .as("%s must not be a writable import field", forbidden)
                    .isFalse();
        }
        assertThat(registry.isWritable(ImportEntityType.PATIENT, null)).isFalse();
    }

    @Test
    void onlyRegistryKeysAreWritableAndNoSynonymEverPointsAtAForbiddenKey() {
        List<ImportFieldDef> fields = registry.fieldsFor(ImportEntityType.PATIENT);

        assertThat(fields).extracting(ImportFieldDef::key)
                .containsExactly("name", "legacyId", "gender", "phone", "email", "dateOfBirth", "address", "medicalHistory");
        for (ImportFieldDef f : fields) {
            assertThat(registry.isWritable(ImportEntityType.PATIENT, f.key())).isTrue();
            assertThat(f.synonyms()).allSatisfy(syn -> assertThat(syn).isEqualTo(ColumnMapper.normalise(syn)));
        }
        assertThat(fields.stream().filter(ImportFieldDef::required).map(ImportFieldDef::key)).containsExactly("name");

        // A header literally named like a forbidden column is at most unmapped, never a suggestion.
        MappingSuggestion s = mapper.suggest(
                List.of("hospital_id", "public_id", "is_active", "duplicate_phone_ack_for", "id"), ImportEntityType.PATIENT);
        assertThat(s.mapping()).isEmpty();
        assertThat(s.unmapped()).hasSize(5);
    }

    @Test
    void synonymsAreUniqueAcrossFieldsSoASingleHeaderCannotMatchTwo() {
        List<String> all = registry.fieldsFor(ImportEntityType.PATIENT).stream()
                .flatMap(f -> f.synonyms().stream())
                .toList();
        assertThat(all).doesNotHaveDuplicates();
    }
}
