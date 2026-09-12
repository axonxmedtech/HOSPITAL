package com.hms.service.pdf;

import com.lowagie.text.pdf.BaseFont;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FoodTimingLabelsTest {

    @Test
    void englishLabelsMatchExactStrings() {
        assertThat(FoodTimingLabels.getLabel("BEFORE_FOOD", "en")).isEqualTo("Before Food");
        assertThat(FoodTimingLabels.getLabel("AFTER_FOOD", "en")).isEqualTo("After Food");
    }

    @Test
    void marathiLabelsMatchExactStrings() {
        assertThat(FoodTimingLabels.getLabel("BEFORE_FOOD", "mr")).isEqualTo("जेवणापूर्वी");
        assertThat(FoodTimingLabels.getLabel("AFTER_FOOD", "mr")).isEqualTo("जेवणानंतर");
    }

    @Test
    void hindiLabelsMatchExactStrings() {
        assertThat(FoodTimingLabels.getLabel("BEFORE_FOOD", "hi")).isEqualTo("भोजन से पहले");
        assertThat(FoodTimingLabels.getLabel("AFTER_FOOD", "hi")).isEqualTo("भोजन के बाद");
    }

    @Test
    void withFoodAndNotSpecifiedBehavior() {
        assertThat(FoodTimingLabels.getLabel("WITH_FOOD", "en")).isEqualTo("With food");
        assertThat(FoodTimingLabels.getLabel("WITH_FOOD", "mr")).isEqualTo("With food");
        assertThat(FoodTimingLabels.getLabel("WITH_FOOD", "hi")).isEqualTo("With food");

        assertThat(FoodTimingLabels.getLabel("NOT_SPECIFIED", "en")).isNull();
        assertThat(FoodTimingLabels.getLabel("NOT_SPECIFIED", "mr")).isNull();
        assertThat(FoodTimingLabels.getLabel("NOT_SPECIFIED", "hi")).isNull();
    }

    @Test
    void nullAndBlankHandling() {
        assertThat(FoodTimingLabels.getLabel(null, "en")).isNull();
        assertThat(FoodTimingLabels.getLabel("", "en")).isNull();
        assertThat(FoodTimingLabels.getLabel("   ", "mr")).isNull();
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        assertThat(FoodTimingLabels.getLabel("BEFORE_FOOD", null)).isEqualTo("Before Food");
        assertThat(FoodTimingLabels.getLabel("BEFORE_FOOD", "fr")).isEqualTo("Before Food");
        assertThat(FoodTimingLabels.getLabel("AFTER_FOOD", "unknown")).isEqualTo("After Food");
    }

    @Test
    void unknownFoodTimingReturnsAsWritten() {
        assertThat(FoodTimingLabels.getLabel("CUSTOM_TIMING", "mr")).isEqualTo("CUSTOM_TIMING");
        assertThat(FoodTimingLabels.getLabel("take at bedtime", "en")).isEqualTo("take at bedtime");
    }

    @Test
    void fontLoadsFromClasspathResource() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fonts/NotoSansDevanagari-Regular.ttf")) {
            assertThat(is).isNotNull();
            byte[] fontBytes = is.readAllBytes();
            assertThat(fontBytes.length).isGreaterThan(100000);
            BaseFont bf = BaseFont.createFont("NotoSansDevanagari-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
            assertThat(bf).isNotNull();
            assertThat(bf.charExists('\u091C')).isTrue();
            assertThat(bf.charExists('B')).isTrue();
        }
    }
}
