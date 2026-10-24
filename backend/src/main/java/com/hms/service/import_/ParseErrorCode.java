package com.hms.service.import_;

/**
 * Why a parse could not continue. File-level: the whole upload is refused. Stable names — the
 * later import phases and the UI classify on them, never on message text.
 */
public enum ParseErrorCode {
    EMPTY_FILE,
    NO_HEADER_ROW,
    BLANK_HEADER,
    HEADER_TOO_LONG,
    DUPLICATE_HEADER,
    TOO_MANY_COLUMNS,
    TOO_MANY_ROWS,
    MALFORMED_CSV,
    NOT_AN_XLSX,
    SHEET_NOT_FOUND,
    UNREADABLE
}
