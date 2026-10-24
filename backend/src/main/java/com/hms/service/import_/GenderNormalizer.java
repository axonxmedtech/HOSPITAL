package com.hms.service.import_;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic legacy gender mapping, and nothing else. {@code M/male → MALE}, {@code F/female
 * → FEMALE}, {@code O/other/others → OTHER}, case-insensitive after trimming. Everything else —
 * blank, {@code U}, {@code Unknown}, a digit, a typo — is not guessed: the caller reports
 * {@code INVALID_GENDER} and a human decides. The walk-in path's own "Unknown" default elsewhere
 * in the system is untouched by this class.
 */
public final class GenderNormalizer {

    private GenderNormalizer() {}

    private static final Map<String, String> MAP = Map.of(
            "m", "MALE",
            "male", "MALE",
            "f", "FEMALE",
            "female", "FEMALE",
            "o", "OTHER",
            "other", "OTHER",
            "others", "OTHER");

    public static Optional<String> normalize(String raw) {
        if (raw == null) return Optional.empty();
        return Optional.ofNullable(MAP.get(raw.strip().toLowerCase(Locale.ROOT)));
    }
}
