package com.hms.util;

/** Doctor names are stored with or without a title ("Dr Asha", "Asha"); show "Dr." exactly once. */
public final class DoctorNames {
    private DoctorNames() {}

    public static String withTitle(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "the doctor";
        return n.matches("(?i)^dr\\.?\\s.*") ? n : "Dr. " + n;
    }
}
