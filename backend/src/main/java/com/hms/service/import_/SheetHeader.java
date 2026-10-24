package com.hms.service.import_;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The header row of a parsed sheet, validated: no blanks, no duplicates after normalisation,
 * within {@link ParserLimits#maxColumns()}.
 *
 * <p>Two views of every header are kept on purpose. {@code display} is the original text (trimmed)
 * that the administrator sees in the mapping UI and in review reports; {@code normalized} is the
 * case- and whitespace-insensitive form used for duplicate detection and for the column mapper.
 * "Phone" and " phone " are one column, and the second one is a refused duplicate, not a silent
 * overwrite of the first.
 */
public final class SheetHeader {

    private final String sheetName;
    private final List<String> display;
    private final List<String> normalized;
    private final Map<String, Integer> indexByNormalized;

    SheetHeader(String sheetName, List<String> display) {
        this.sheetName = sheetName;
        this.display = List.copyOf(display);
        this.normalized = display.stream().map(SheetHeader::normalize).toList();
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < normalized.size(); i++) {
            index.put(normalized.get(i), i);
        }
        this.indexByNormalized = Collections.unmodifiableMap(index);
    }

    /** Trim, collapse internal whitespace, casefold. Deterministic and locale-independent. */
    public static String normalize(String header) {
        if (header == null) return "";
        return header.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Sheet name for .xlsx; the constant {@code "csv"} for CSV. */
    public String sheetName() {
        return sheetName;
    }

    public List<String> display() {
        return display;
    }

    public List<String> normalized() {
        return normalized;
    }

    public int columnCount() {
        return display.size();
    }

    /** Column index of a header given in any spelling that normalises to it, or -1. */
    public int indexOf(String header) {
        Integer i = indexByNormalized.get(normalize(header));
        return i == null ? -1 : i;
    }
}
