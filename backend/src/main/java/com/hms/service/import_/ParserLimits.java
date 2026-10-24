package com.hms.service.import_;

/**
 * Resource-safety bounds for one parse. These are not business rules: a 2,001-character cell is
 * not "too long for the address column", it is more than the parser is willing to hold for any
 * one cell of untrusted input. Business-field length handling belongs to the importer.
 *
 * <p>{@link #DEFAULT} is what production uses. Tests may construct smaller limits to prove the
 * boundaries without generating a 100,000-row workbook; nothing in production code reads any
 * value but {@code DEFAULT}.
 */
public record ParserLimits(int maxRows, int maxColumns, int maxCellChars) {

    public static final int MAX_ROWS = 100_000;
    public static final int MAX_COLUMNS = 100;
    public static final int MAX_CELL_CHARS = 2_000;

    public static final ParserLimits DEFAULT = new ParserLimits(MAX_ROWS, MAX_COLUMNS, MAX_CELL_CHARS);

    public ParserLimits {
        if (maxRows < 1 || maxColumns < 1 || maxCellChars < 1) {
            throw new IllegalArgumentException("Parser limits must be positive");
        }
    }
}
