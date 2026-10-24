package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.service.import_.LegacyDateParser.Kind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyDateParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "1977-04-03            | 1977-04-03", // ISO — how an .xlsx date cell arrives
                "1977-04-03T00:00:00   | 1977-04-03", // ISO date-time from the parser; time discarded
                "2020-02-29T10:30:00   | 2020-02-29",
                "03-04-1977            | 1977-04-03", // day-first, hyphen
                "3-4-1977              | 1977-04-03",
                "03/04/1977            | 1977-04-03", // day-first, slash
                "3/4/1977              | 1977-04-03",
                "29/02/2020            | 2020-02-29", // a real leap day
                "  31/12/1999          | 1999-12-31",
            })
    void everySupportedSpellingParsesStrictly(String raw, String expected) {
        LegacyDateParser.Result r = LegacyDateParser.parse(raw, TODAY);
        assertThat(r.kind()).isEqualTo(Kind.VALID);
        assertThat(r.date()).isEqualTo(LocalDate.parse(expected));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is invalid")
    @ValueSource(
            strings = {
                "31/02/2020", "29/02/2021", "31-04-2020", "2020-02-30", // impossible calendar dates: refused, never clamped
                "12/31/2020", "04/31/1977", // month-first: day 31 cannot be a month, and no swap is attempted
                "1977/04/03", "03.04.1977", "3 April 1977", "1977-4-3", "77-04-03", "03/04/77", // unsupported spellings
                "abc", "0000-00-00", "1977-04-03 10:30",
            })
    void impossibleAndUnsupportedDatesAreInvalid(String raw) {
        assertThat(LegacyDateParser.parse(raw, TODAY).kind()).isEqualTo(Kind.INVALID);
    }

    @Test
    void aSlashedDateIsReadDayFirstEvenWhenMonthFirstWouldAlsoParse() {
        // The ambiguous case. The reference implementation would have produced 3 April too, but
        // only because day-first was tried first; here month-first is simply not a reading.
        assertThat(LegacyDateParser.parse("03/04/1977", TODAY).date()).isEqualTo(LocalDate.of(1977, 4, 3));
        assertThat(LegacyDateParser.parse("04/03/1977", TODAY).date()).isEqualTo(LocalDate.of(1977, 3, 4));
        // …and a month-first export shows itself immediately rather than silently mis-dating people.
        assertThat(LegacyDateParser.parse("12/25/1990", TODAY).kind()).isEqualTo(Kind.INVALID);
    }

    @Test
    void plausibilityMirrorsManualRegistration() {
        assertThat(LegacyDateParser.parse("2026-09-17", TODAY).kind()).isEqualTo(Kind.VALID); // today is allowed
        assertThat(LegacyDateParser.parse("2026-09-18", TODAY).kind()).isEqualTo(Kind.INVALID); // tomorrow is not
        assertThat(LegacyDateParser.parse("1906-09-17", TODAY).kind()).isEqualTo(Kind.VALID); // exactly 120 years
        assertThat(LegacyDateParser.parse("1906-09-16", TODAY).kind()).isEqualTo(Kind.INVALID); // older than 120 years
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void blankIsBlank(String raw) {
        assertThat(LegacyDateParser.parse(raw, TODAY).kind()).isEqualTo(Kind.BLANK);
    }
}
