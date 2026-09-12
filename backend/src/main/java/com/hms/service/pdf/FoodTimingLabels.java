package com.hms.service.pdf;

import com.hms.entity.FoodTiming;
import java.util.Map;

/**
 * Multi-language translations for prescription food-timing instructions.
 *
 * <p>Strictly bounded to BEFORE_FOOD and AFTER_FOOD in English, Marathi, and Hindi.
 * All other prescription fields (medicines, notes, instructions, dosages) remain untranslated.
 */
public final class FoodTimingLabels {

    private static final Map<String, String> EN = Map.of(
            FoodTiming.BEFORE_FOOD, "Before Food",
            FoodTiming.AFTER_FOOD, "After Food"
    );

    private static final Map<String, String> MR = Map.of(
            FoodTiming.BEFORE_FOOD, "जेवणापूर्वी",
            FoodTiming.AFTER_FOOD, "जेवणानंतर"
    );

    private static final Map<String, String> HI = Map.of(
            FoodTiming.BEFORE_FOOD, "भोजन से पहले",
            FoodTiming.AFTER_FOOD, "भोजन के बाद"
    );

    private static final Map<String, Map<String, String>> TRANSLATIONS = Map.of(
            "en", EN,
            "mr", MR,
            "hi", HI
    );

    /**
     * Resolves the label for a given food timing and language.
     *
     * @param foodTiming the stored foodTiming value (e.g. BEFORE_FOOD, AFTER_FOOD)
     * @param lang       the requested language ("en", "mr", "hi"), defaulting to "en"
     * @return translated label, or English label, or null if no food timing stated
     */
    public static String getLabel(String foodTiming, String lang) {
        if (foodTiming == null || foodTiming.isBlank()) {
            return null;
        }

        String timingKey = foodTiming.trim().toUpperCase();
        if (FoodTiming.NOT_SPECIFIED.equals(timingKey)) {
            return null;
        }

        String normalizedLang = (lang == null || lang.isBlank()) ? "en" : lang.trim().toLowerCase();
        Map<String, String> langMap = TRANSLATIONS.getOrDefault(normalizedLang, EN);

        String label = langMap.get(timingKey);
        if (label != null) {
            return label;
        }

        // WITH_FOOD stays as English
        if (FoodTiming.WITH_FOOD.equals(timingKey)) {
            return "With food";
        }

        // Unknown or historical values render as written
        return foodTiming;
    }

    private FoodTimingLabels() {
    }
}
