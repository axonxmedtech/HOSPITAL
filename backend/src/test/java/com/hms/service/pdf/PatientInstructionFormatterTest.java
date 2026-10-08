package com.hms.service.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientInstructionFormatterTest {

    @Test
    void testNormalizeMode() {
        assertThat(PatientInstructionFormatter.normalizeMode(null)).isEqualTo("EN");
        assertThat(PatientInstructionFormatter.normalizeMode("")).isEqualTo("EN");
        assertThat(PatientInstructionFormatter.normalizeMode("en")).isEqualTo("EN");
        assertThat(PatientInstructionFormatter.normalizeMode("mr")).isEqualTo("EN_MR");
        assertThat(PatientInstructionFormatter.normalizeMode("EN_MR")).isEqualTo("EN_MR");
        assertThat(PatientInstructionFormatter.normalizeMode("hi")).isEqualTo("EN_HI");
        assertThat(PatientInstructionFormatter.normalizeMode("EN_HI")).isEqualTo("EN_HI");
    }

    @Test
    void testFoodTimingBilingual() {
        // English
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("BEFORE_FOOD", "EN")).isEqualTo("Before Food");
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("AFTER_FOOD", "EN")).isEqualTo("After Food");
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("WITH_FOOD", "EN")).isEqualTo("With Food");

        // English + Marathi
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("BEFORE_FOOD", "EN_MR")).isEqualTo("Before Food / जेवणापूर्वी");
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("AFTER_FOOD", "EN_MR")).isEqualTo("After Food / जेवणानंतर");
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("WITH_FOOD", "EN_MR")).isEqualTo("With Food / जेवणासोबत");

        // English + Hindi
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("BEFORE_FOOD", "EN_HI")).isEqualTo("Before Food / भोजन से पहले");
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("AFTER_FOOD", "EN_HI")).isEqualTo("After Food / भोजन के बाद");
        assertThat(PatientInstructionFormatter.getFoodTimingLabel("WITH_FOOD", "EN_HI")).isEqualTo("With Food / भोजन के साथ");
    }

    @Test
    void testFormatBilingual() {
        assertThat(PatientInstructionFormatter.formatBilingual("Take with water", "पाण्यासोबत घ्या", "EN_MR"))
                .isEqualTo("Take with water / पाण्यासोबत घ्या");

        assertThat(PatientInstructionFormatter.formatBilingual("Take with water", "पानी के साथ लें", "EN_HI"))
                .isEqualTo("Take with water / पानी के साथ लें");

        assertThat(PatientInstructionFormatter.formatBilingual("Take with water", "पाण्यासोबत घ्या", "EN"))
                .isEqualTo("Take with water");
    }
}
