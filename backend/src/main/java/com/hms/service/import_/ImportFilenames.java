package com.hms.service.import_;

/**
 * The client's filename is display metadata, never a path. {@link SpooledUpload} names its own
 * temp file; this only decides what the batch record shows the administrator later.
 */
public final class ImportFilenames {

    public static final int MAX_LENGTH = 120;
    public static final String FALLBACK = "import";

    private ImportFilenames() {}

    /** Basename only, control characters removed, bounded, never blank. */
    public static String sanitize(String original) {
        if (original == null) return FALLBACK;
        String s = original;
        int cut = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (cut >= 0) s = s.substring(cut + 1);
        s = s.replaceAll("[\\p{Cntrl}]", "").strip();
        if (s.isEmpty() || s.equals(".") || s.equals("..")) return FALLBACK;
        if (s.length() > MAX_LENGTH) s = s.substring(0, MAX_LENGTH);
        return s;
    }

    /** The upload format from the (sanitized) name's extension, or null when it is not one V1 accepts. */
    public static ImportFormat formatOf(String sanitizedName) {
        String lower = sanitizedName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".csv")) return ImportFormat.CSV;
        if (lower.endsWith(".xlsx")) return ImportFormat.XLSX;
        return null;
    }
}
