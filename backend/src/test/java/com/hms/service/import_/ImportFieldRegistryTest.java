package com.hms.service.import_;

import com.hms.entity.ImportEntityType;
import com.hms.dto.import_.ImportFieldDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportFieldRegistryTest {

    private final ImportFieldRegistry registry = new ImportFieldRegistry();

    @Test
    void exposesPatientTargetFields() {
        List<ImportFieldDef> fields = registry.fieldsFor(ImportEntityType.PATIENT);
        assertThat(fields).extracting(ImportFieldDef::key)
                .contains("name", "gender", "phone", "legacyId", "dateOfBirth");
    }

    @Test
    void nameIsTheOnlyRequiredPatientField() {
        List<ImportFieldDef> required = registry.fieldsFor(ImportEntityType.PATIENT)
                .stream().filter(ImportFieldDef::required).toList();
        assertThat(required).extracting(ImportFieldDef::key).containsExactly("name");
    }

    @Test
    void synonymsAreLowercasedForMatching() {
        ImportFieldDef phone = registry.fieldsFor(ImportEntityType.PATIENT).stream()
                .filter(f -> f.key().equals("phone")).findFirst().orElseThrow();
        assertThat(phone.synonyms()).contains("mobile", "mob no", "contact number");
    }
}
