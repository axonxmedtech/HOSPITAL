package com.hms.service.import_;

import java.util.regex.Pattern;

/**
 * Turns a legacy phone cell into the canonical form the current {@code Patient} accepts —
 * {@code ^[0-9]{10}$}, exactly the entity's rule, no first-digit restriction — or says
 * deterministically why it cannot.
 *
 * <p>The algorithm is the frozen one and nothing more:
 * <ol>
 *   <li>null or blank → {@link Kind#BLANK}; so is a cell holding nothing but formatting characters</li>
 *   <li>trim; remove only formatting characters: space, hyphen, parentheses, dot, NBSP, tab.
 *       Letters and any other symbol stay, so they fail the final check instead of vanishing.</li>
 *   <li>{@code ^\+?91[0-9]{10}$} → drop the country prefix, ten digits remain. A plain ten-digit
 *       number that happens to start with 91 has no prefix to drop and is untouched.</li>
 *   <li>{@code ^0[0-9]{10}$} → drop the trunk zero.</li>
 *   <li>{@code ^[0-9]{10}$} → {@link Kind#CANONICAL}; anything else → {@link Kind#UNRECOVERABLE}.</li>
 * </ol>
 * No digits are ever invented and no placeholder is ever returned. The raw value is carried on
 * the result so a later phase can preserve it as "Phone (as imported)"; it is never logged here.
 */
public final class LegacyPhoneNormalizer {

    private LegacyPhoneNormalizer() {}

    private static final Pattern FORMATTING = Pattern.compile("[ \\-().\\u00A0\\t]");
    private static final Pattern COUNTRY_PREFIXED = Pattern.compile("^\\+?91[0-9]{10}$");
    private static final Pattern TRUNK_PREFIXED = Pattern.compile("^0[0-9]{10}$");
    private static final Pattern CANONICAL = Pattern.compile("^[0-9]{10}$");

    public enum Kind {
        CANONICAL,
        BLANK,
        UNRECOVERABLE
    }

    /**
     * @param kind      what the value turned out to be
     * @param canonical the ten digits, only when {@code kind == CANONICAL}; otherwise null
     * @param original  the trimmed value as it appeared in the file (null when BLANK)
     */
    public record Result(Kind kind, String canonical, String original) {

        public boolean isCanonical() {
            return kind == Kind.CANONICAL;
        }

        /** The value worth preserving as "Phone (as imported)": the original, only when it differs from the canonical form. */
        public String asImportedIfDifferent() {
            return isCanonical() && !canonical.equals(original) ? original : null;
        }
    }

    public static Result normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Result(Kind.BLANK, null, null);
        }
        String original = raw.strip();
        String s = FORMATTING.matcher(original).replaceAll("");
        if (s.isEmpty()) {
            // Only formatting characters (an NBSP-only cell, a lone hyphen): nothing was typed.
            return new Result(Kind.BLANK, null, null);
        }
        if (COUNTRY_PREFIXED.matcher(s).matches()) {
            s = s.substring(s.length() - 10);
        } else if (TRUNK_PREFIXED.matcher(s).matches()) {
            s = s.substring(1);
        }
        if (CANONICAL.matcher(s).matches()) {
            return new Result(Kind.CANONICAL, s, original);
        }
        return new Result(Kind.UNRECOVERABLE, null, original);
    }
}
