package com.hms.entitlement;

import java.util.Locale;

/**
 * The capacity a plan can cap. A plan stores a number per key; no number means unlimited.
 *
 * <p>Adding a limit is one constant here, one count in {@code PlanLimitService#used}, and a
 * {@code PlanLimitService#requireRoom} call wherever such a thing is created.
 */
public enum PlanLimit {
    BEDS("Beds", "bed", "beds"),
    ICU_BEDS("ICU beds", "ICU bed", "ICU beds"),
    DOCTORS("Doctors", "doctor", "doctors"),
    STAFF_LOGINS("Staff logins", "staff login", "staff logins"),
    OT_THEATRES("Operation theatres", "operation theatre", "operation theatres");

    private final String label;
    private final String one;
    private final String many;

    PlanLimit(String label, String one, String many) {
        this.label = label;
        this.one = one;
        this.many = many;
    }

    public String label() {
        return label;
    }

    /** "1 bed", "10 beds". */
    public String count(long n) {
        return n + " " + (n == 1 ? one : many);
    }

    /** "Beds", "ICU beds" — for "ICU beds are not included in your plan". */
    public String pluralTitle() {
        return Character.toUpperCase(many.charAt(0)) + many.substring(1);
    }

    /** "Bed", "ICU bed" — for "Bed limit reached". */
    public String singularTitle() {
        return Character.toUpperCase(one.charAt(0)) + one.substring(1);
    }

    /** The key, or null for anything that is not a known limit (ignored, never stored). */
    public static PlanLimit fromKey(String key) {
        if (key == null) return null;
        try {
            return valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
