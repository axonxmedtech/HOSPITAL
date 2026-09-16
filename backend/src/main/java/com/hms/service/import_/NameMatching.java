package com.hms.service.import_;

import java.util.Locale;

/**
 * Normalises a name FOR MATCHING ONLY. The stored name is never altered by this.
 *
 * <p>Exactly: trim, casefold under {@link Locale#ROOT}, turn hyphen, dot, comma, apostrophe and
 * slash into spaces, collapse runs of whitespace. So {@code Ramesh Kumar}, {@code ramesh kumar},
 * {@code Ramesh   Kumar} and {@code Ramesh-Kumar} compare equal; {@code Ramesh Kumaar} does not.
 * There is no fuzzy step of any kind — no edit distance, no phonetics, no partial matching —
 * because "a bit similar" is how two different people get merged.
 */
public final class NameMatching {

    private NameMatching() {}

    public static String normalize(String name) {
        if (name == null) return "";
        return name.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\-.,'/]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    public static boolean sameName(String a, String b) {
        String na = normalize(a);
        return !na.isEmpty() && na.equals(normalize(b));
    }
}
