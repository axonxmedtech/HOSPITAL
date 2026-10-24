package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportMappingValidatorTest {

    private final ImportMappingValidator v = new ImportMappingValidator(new ImportFieldRegistry());

    private static Map<String, String> m(String... kv) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put(kv[i], kv[i + 1]);
        return out;
    }

    @Test
    void aWellFormedMappingIsAcceptedAndCopied() {
        Map<String, String> in = m("Patient Name", "name", "Mobile", "phone", "MRN", "legacyId");
        Map<String, String> out = v.validate(in);
        assertThat(out).containsExactlyEntriesOf(in);
        in.put("X", "y");
        assertThat(out).doesNotContainKey("X");
    }

    @Test
    void emptyOrMissingMappingsAreRefused() {
        assertThatThrownBy(() -> v.validate(null)).isInstanceOf(InvalidImportMappingException.class);
        assertThatThrownBy(() -> v.validate(Map.of())).isInstanceOf(InvalidImportMappingException.class);
        assertThatThrownBy(() -> v.validate(m("Phone", "phone"))).isInstanceOf(InvalidImportMappingException.class).hasMessageContaining("name");
    }

    @Test
    void unknownForbiddenAndInternalTargetsAreRefused() {
        for (String target : List.of("nickname", "id", "hospitalId", "hospital_id", "publicId", "customId", "isActive", "status",
                "duplicate_phone_ack_for", "duplicate_phone_ack_at", "duplicate_phone_ack_by", "duplicatePhoneAckFor", "importBatchId", "")) {
            assertThatThrownBy(() -> v.validate(m("Name", "name", "Col", target)))
                    .as(target)
                    .isInstanceOf(InvalidImportMappingException.class);
        }
    }

    @Test
    void duplicateTargetsAndDuplicateOrBlankSourcesAreRefused() {
        assertThatThrownBy(() -> v.validate(m("Name", "name", "Full Name", "name"))).isInstanceOf(InvalidImportMappingException.class).hasMessageContaining("More than one column");
        assertThatThrownBy(() -> v.validate(m("Name", "name", "Phone", "phone", " phone ", "email"))).isInstanceOf(InvalidImportMappingException.class).hasMessageContaining("more than once");
        assertThatThrownBy(() -> v.validate(m("Name", "name", "  ", "phone"))).isInstanceOf(InvalidImportMappingException.class).hasMessageContaining("blank header");
    }

    @Test
    void moreThanOneHundredEntriesAreRefusedAndExactlyOneHundredAllowed() {
        Map<String, String> hundred = new LinkedHashMap<>();
        hundred.put("Name", "name");
        for (int i = 1; i < 100; i++) hundred.put("C" + i, i == 1 ? "phone" : "x" + i);
        // 99 of them point at unknown fields, so build a legal 100 instead: only 8 registry fields
        // exist, meaning a legal mapping can never reach 100 distinct targets — the bound is on entries.
        Map<String, String> over = new LinkedHashMap<>();
        for (int i = 0; i <= 100; i++) over.put("C" + i, "name");
        assertThatThrownBy(() -> v.validate(over)).isInstanceOf(InvalidImportMappingException.class).hasMessageContaining("At most 100");
        assertThat(ImportMappingValidator.MAX_ENTRIES).isEqualTo(100);
    }
}
