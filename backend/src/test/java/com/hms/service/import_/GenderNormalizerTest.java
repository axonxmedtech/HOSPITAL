package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GenderNormalizerTest {

    @ParameterizedTest
    @CsvSource({
        "M,MALE", "m,MALE", "male,MALE", "MALE,MALE", "Male,MALE", " male ,MALE",
        "F,FEMALE", "f,FEMALE", "female,FEMALE", "FEMALE,FEMALE", "Female,FEMALE",
        "O,OTHER", "o,OTHER", "other,OTHER", "others,OTHER", "OTHER,OTHER", "Others,OTHER"
    })
    void deterministicSpellingsMap(String raw, String expected) {
        assertThat(GenderNormalizer.normalize(raw)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"U", "u", "Unknown", "unknown", "UNKNOWN", "T", "trans", "1", "0", "Mal", "Fem", "N/A", "-", "Ma le"})
    void everythingElseIsNotGuessed(String raw) {
        assertThat(GenderNormalizer.normalize(raw)).isEmpty();
    }
}
