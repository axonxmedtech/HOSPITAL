package com.hms.service.pdf;

import com.hms.entity.FoodTiming;
import java.util.Map;

/**
 * Multi-language bilingual formatting for patient-facing consultation instructions.
 *
 * Supported language modes:
 * - EN: English only (primary)
 * - EN_MR: English + Marathi (Devanagari)
 * - EN_HI: English + Hindi (Devanagari)
 */
public final class PatientInstructionFormatter {

    public static final String MODE_EN = "EN";
    public static final String MODE_EN_MR = "EN_MR";
    public static final String MODE_EN_HI = "EN_HI";

    private static final Map<String, String> EN_FOOD = Map.of(
            FoodTiming.BEFORE_FOOD, "Before Food",
            FoodTiming.AFTER_FOOD, "After Food",
            FoodTiming.WITH_FOOD, "With Food"
    );

    private static final Map<String, String> MR_FOOD = Map.of(
            FoodTiming.BEFORE_FOOD, "Before Food / जेवणापूर्वी",
            FoodTiming.AFTER_FOOD, "After Food / जेवणानंतर",
            FoodTiming.WITH_FOOD, "With Food / जेवणासोबत"
    );

    private static final Map<String, String> HI_FOOD = Map.of(
            FoodTiming.BEFORE_FOOD, "Before Food / भोजन से पहले",
            FoodTiming.AFTER_FOOD, "After Food / भोजन के बाद",
            FoodTiming.WITH_FOOD, "With Food / भोजन के साथ"
    );

    /**
     * Normalizes any input language code (e.g. "mr", "EN_MR", "hi", "EN_HI", null)
     * to one of the 3 supported modes: EN, EN_MR, EN_HI.
     */
    public static String normalizeMode(String lang) {
        if (lang == null || lang.isBlank()) {
            return MODE_EN;
        }
        String cleaned = lang.trim().toUpperCase().replace("-", "_");
        if ("MR".equals(cleaned) || "EN_MR".equals(cleaned) || "MARATHI".equals(cleaned)) {
            return MODE_EN_MR;
        }
        if ("HI".equals(cleaned) || "EN_HI".equals(cleaned) || "HINDI".equals(cleaned)) {
            return MODE_EN_HI;
        }
        return MODE_EN;
    }

    /**
     * Resolves the bilingual label for a given food timing and language mode.
     *
     * @param foodTiming stored foodTiming value (e.g. BEFORE_FOOD, AFTER_FOOD, WITH_FOOD)
     * @param lang       requested language mode ("EN", "EN_MR", "EN_HI", or legacy "en", "mr", "hi")
     * @return bilingual label, or English label, or null if NOT_SPECIFIED or empty
     */
    public static String getFoodTimingLabel(String foodTiming, String lang) {
        if (foodTiming == null || foodTiming.isBlank()) {
            return null;
        }

        String timingKey = foodTiming.trim().toUpperCase();
        if (FoodTiming.NOT_SPECIFIED.equals(timingKey)) {
            return null;
        }

        String mode = normalizeMode(lang);
        Map<String, String> table = switch (mode) {
            case MODE_EN_MR -> MR_FOOD;
            case MODE_EN_HI -> HI_FOOD;
            default -> EN_FOOD;
        };

        String label = table.get(timingKey);
        if (label != null) {
            return label;
        }

        // Unknown or custom food timing renders as entered
        return foodTiming;
    }

    /**
     * Formats an English statement with a second language translation into bilingual format:
     * English / Translation
     */
    public static String formatBilingual(String englishText, String translationText, String lang) {
        if (englishText == null || englishText.isBlank()) {
            return (translationText != null) ? translationText.trim() : "";
        }
        String mode = normalizeMode(lang);
        if (MODE_EN.equals(mode) || translationText == null || translationText.isBlank()) {
            return englishText.trim();
        }
        return englishText.trim() + " / " + translationText.trim();
    }

    private PatientInstructionFormatter() {
    }
}
