package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameMatchingTest {

    @Test
    void caseSpacingAndSimplePunctuationAreNotDifferences() {
        assertThat(NameMatching.sameName("Ramesh Kumar", "ramesh kumar")).isTrue();
        assertThat(NameMatching.sameName("Ramesh Kumar", "Ramesh   Kumar")).isTrue();
        assertThat(NameMatching.sameName("Ramesh Kumar", "Ramesh-Kumar")).isTrue();
        assertThat(NameMatching.sameName("Ramesh Kumar", "  RAMESH KUMAR ")).isTrue();
        assertThat(NameMatching.sameName("Dr. Ramesh Kumar", "Dr Ramesh Kumar")).isTrue();
        assertThat(NameMatching.sameName("O'Brien", "O Brien")).isTrue();
    }

    @Test
    void spellingVariantsAreDifferentPeople() {
        assertThat(NameMatching.sameName("Ramesh Kumar", "Ramesh Kumaar")).isFalse();
        assertThat(NameMatching.sameName("Ramesh Kumar", "Ramesh")).isFalse();
        assertThat(NameMatching.sameName("Ramesh Kumar", "Kumar Ramesh")).isFalse();
        assertThat(NameMatching.sameName("Ramesh Kumar", "Ramesh Kumar Jr")).isFalse();
    }

    @Test
    void blankNeverMatchesAnything() {
        assertThat(NameMatching.sameName("", "")).isFalse();
        assertThat(NameMatching.sameName(null, null)).isFalse();
        assertThat(NameMatching.sameName(" ", " ")).isFalse();
    }

    @Test
    void normalizationIsExactlyAsDocumented() {
        assertThat(NameMatching.normalize("  Ramesh-Kumar,  Jr. ")).isEqualTo("ramesh kumar jr");
        assertThat(NameMatching.normalize("रोगी  परीक्षण")).isEqualTo("रोगी परीक्षण");
    }
}
