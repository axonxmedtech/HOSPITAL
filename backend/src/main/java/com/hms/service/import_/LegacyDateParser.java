package com.hms.service.import_;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * STRICT interpretation of a legacy date-of-birth cell.
 *
 * <p>Supported spellings, tried in this order, all with {@link ResolverStyle#STRICT} (so
 * 31/02/2020 is refused rather than clamped to the 29th) and the proleptic-year symbol
 * {@code uuuu} (STRICT cannot resolve {@code yyyy} without an era):
 * <ol>
 *   <li>{@code uuuu-MM-dd} — ISO; this is how a real .xlsx date cell arrives from the parser</li>
 *   <li>{@code uuuu-MM-dd'T'HH:mm:ss} — ISO with a time part, as the parser renders date-times;
 *       the time is discarded</li>
 *   <li>{@code d-M-uuuu} — day-first, one or two digit day and month</li>
 *   <li>{@code d/M/uuuu} — day-first, one or two digit day and month</li>
 * </ol>
 * The ordering cannot change a result: the ISO forms begin with a four-digit year and the
 * day-first forms end with one, so no string is accepted by two of them.
 *
 * <p><b>Day-first is the only reading of a slashed date.</b> The reference implementation also
 * tried {@code MM/dd/uuuu} after {@code dd/MM/uuuu}, which meant 12/31/2020 silently became
 * 31 December because the day-first reading happened to fail — month/day swapping decided by
 * what parses. That is exactly the guess this class refuses to make: a month-first legacy export
 * produces {@code INVALID} rows for every day above 12 and a plausible-but-wrong date for the
 * rest, and the second is worse than the first, so month-first is not supported at all. The
 * preview warns that dates are read day-first.
 *
 * <p>Plausibility mirrors the manual registration path ({@code PatientService.validateDateOfBirth}):
 * not in the future, not more than 120 years ago. The clock is {@code LocalDate.now(ZoneId.systemDefault())},
 * the same one that path uses; the server timezone is not touched.
 */
public final class LegacyDateParser {

    private LegacyDateParser() {}

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT));

    private static final DateTimeFormatter ISO_DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    public enum Kind {
        VALID,
        BLANK,
        INVALID
    }

    public record Result(Kind kind, LocalDate date) {
        public boolean isValid() {
            return kind == Kind.VALID;
        }
    }

    public static Result parse(String raw) {
        return parse(raw, LocalDate.now(java.time.ZoneId.systemDefault()));
    }

    /** {@code today} is injectable so the plausibility window can be tested deterministically. */
    public static Result parse(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return new Result(Kind.BLANK, null);
        }
        String s = raw.strip();
        LocalDate date = null;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                date = LocalDate.parse(s, f);
                break;
            } catch (DateTimeParseException ignored) {
                // try the next spelling
            }
        }
        if (date == null) {
            try {
                date = LocalDateTime.parse(s, ISO_DATE_TIME).toLocalDate();
            } catch (DateTimeParseException ignored) {
                return new Result(Kind.INVALID, null);
            }
        }
        if (date.isAfter(today) || date.isBefore(today.minusYears(120))) {
            return new Result(Kind.INVALID, null);
        }
        return new Result(Kind.VALID, date);
    }
}
