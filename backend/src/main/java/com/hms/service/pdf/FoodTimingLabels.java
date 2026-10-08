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

    /**
     * Resolves the label for a given food timing and language mode.
     * Delegates to PatientInstructionFormatter to ensure bilingual format.
     */
    public static String getLabel(String foodTiming, String lang) {
        return PatientInstructionFormatter.getFoodTimingLabel(foodTiming, lang);
    }

    private FoodTimingLabels() {
    }
}
