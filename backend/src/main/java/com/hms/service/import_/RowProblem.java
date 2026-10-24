package com.hms.service.import_;

/**
 * A row-level parse problem. The parse continues — one oversized cell must not cost the other
 * 99,999 rows — but the row is delivered with the problem attached and its offending value
 * replaced by the empty string, so a later phase can classify the row as FAILED with a stable
 * reason instead of silently importing a truncated value.
 */
public record RowProblem(Code code, String column, int columnIndex) {

    public enum Code {
        /** A cell exceeded {@link ParserLimits#maxCellChars()}. Never truncated: the value is dropped and the row flagged. */
        CELL_TOO_LONG,
        /** The row has a value in a column beyond the header row's width, so it cannot be named. */
        UNEXPECTED_CELL
    }
}
