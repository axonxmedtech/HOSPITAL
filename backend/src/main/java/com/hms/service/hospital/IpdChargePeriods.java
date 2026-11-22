package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.IpdBillingSetting;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The "days" an inpatient is charged for, under the rule fixed at admission.
 *
 * <ul>
 *   <li>CALENDAR_DAY — day 1 is the admission date, day 2 the next date, and so on; a day is
 *       charged once it begins (midnight). In at 23:30 and out at 09:00 is two days.</li>
 *   <li>HOURS_24 — day 1 starts at the admission time, day 2 exactly 24 hours later, and so on;
 *       a new day is charged only once the stay runs past it by the grace hours. In at 23:30 and
 *       out at 09:00 is one day.</li>
 * </ul>
 *
 * <p>Every bed, nursing and doctor-visit line names its day in its description. That is what
 * makes the charges idempotent (a day already charged is never charged again) and lets a
 * package find the charges it covers.
 */
public final class IpdChargePeriods {

    public static final String BED_PRICE = "Bed Price";
    static final String DAILY_BED = "Daily Bed Charge - ";
    static final String BED_DAY = "Bed Charge - Day ";
    static final String NURSING = "Nursing Charge - ";
    static final String VISIT = "Doctor Visit - Day ";
    /** Never back-fill more than this many days for one admission in one run. */
    static final int MAX_PERIODS = 366;

    private static final Pattern DAY_N = Pattern.compile("Day (\\d+)");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    /** One chargeable day: its number (1 = admission day), when it starts, and its label. */
    public record Period(int number, LocalDateTime start, String label) {}

    private IpdChargePeriods() {}

    public static boolean hours24(IpdAdmission a) {
        return IpdBillingSetting.HOURS_24.equals(a.getBedChargeRule());
    }

    private static int grace(IpdAdmission a) {
        return a.getGraceHours() == null ? 0 : Math.max(0, a.getGraceHours());
    }

    /** The days that have begun by {@code now}, oldest first (capped at {@link #MAX_PERIODS}). */
    public static List<Period> started(IpdAdmission a, LocalDateTime now) {
        List<Period> out = new ArrayList<>();
        LocalDateTime admitAt = a.getAdmissionDatetime();
        if (admitAt == null) {
            // Legacy row with no admission time: only today can be placed.
            LocalDate today = now.toLocalDate();
            out.add(new Period(1, today.atStartOfDay(), today.format(DateTimeFormatter.ISO_DATE)));
            return out;
        }
        if (hours24(a)) {
            for (int k = 1; k <= MAX_PERIODS; k++) {
                LocalDateTime start = admitAt.plusHours(24L * (k - 1));
                if (k > 1 && now.isBefore(start.plusHours(grace(a)))) break;
                out.add(new Period(k, start, "Day " + k));
            }
        } else {
            LocalDate first = admitAt.toLocalDate();
            for (int k = 1; k <= MAX_PERIODS; k++) {
                LocalDate date = first.plusDays(k - 1L);
                if (date.isAfter(now.toLocalDate())) break;
                out.add(new Period(k, k == 1 ? admitAt : date.atStartOfDay(), date.format(DateTimeFormatter.ISO_DATE)));
            }
        }
        // Keep the most recent days when a very old stay exceeds the cap.
        return out.size() > MAX_PERIODS ? out.subList(out.size() - MAX_PERIODS, out.size()) : out;
    }

    /** The day a moment falls in (1 = admission day). */
    public static int periodAt(IpdAdmission a, LocalDateTime at) {
        LocalDateTime admitAt = a.getAdmissionDatetime();
        if (admitAt == null || at.isBefore(admitAt)) return 1;
        if (hours24(a)) {
            return (int) (ChronoUnit.HOURS.between(admitAt, at) / 24) + 1;
        }
        return (int) ChronoUnit.DAYS.between(admitAt.toLocalDate(), at.toLocalDate()) + 1;
    }

    /** Bed line of a day after the first (day 1 is the "Bed Price" line written at admission). */
    public static String bedLine(IpdAdmission a, Period p) {
        if (p.number() == 1) return BED_PRICE;
        return hours24(a) ? BED_DAY + p.number() : DAILY_BED + p.label();
    }

    public static String nursingLine(IpdAdmission a, Period p) {
        return NURSING + (hours24(a) ? "Day " + p.number() : p.label());
    }

    public static String visitLine(int period, String doctorName) {
        return VISIT + period + " - " + doctorName;
    }

    /**
     * The day a bed, nursing or doctor-visit line belongs to, or null for any other line
     * (medicines, consumables, upgrades, packages). Used to find what a package covers.
     */
    public static Integer periodOfLine(IpdAdmission a, String description) {
        if (description == null) return null;
        if (BED_PRICE.equals(description)) return 1;
        boolean coverable = description.startsWith(DAILY_BED) || description.startsWith(BED_DAY)
                || description.startsWith(NURSING) || description.startsWith(VISIT);
        if (!coverable) return null;
        Matcher day = DAY_N.matcher(description);
        if (day.find()) return Integer.valueOf(day.group(1));
        Matcher date = ISO_DATE.matcher(description);
        if (date.find() && a.getAdmissionDatetime() != null) {
            LocalDate d = LocalDate.parse(date.group(1));
            return (int) ChronoUnit.DAYS.between(a.getAdmissionDatetime().toLocalDate(), d) + 1;
        }
        return null;
    }
}
