package com.hms.service.import_;

/**
 * A file-level parse failure. Carries a {@link ParseErrorCode}, and where it is known, the
 * 1-based row and the column the problem was found at — never the content of any cell: the
 * message is shown to the administrator and written to logs, and cells are patient data.
 */
public class ImportParseException extends RuntimeException {

    private final ParseErrorCode code;
    private final Integer rowNum;
    private final String column;

    public ImportParseException(ParseErrorCode code, String message) {
        this(code, message, null, null, null);
    }

    public ImportParseException(ParseErrorCode code, String message, Integer rowNum, String column) {
        this(code, message, rowNum, column, null);
    }

    public ImportParseException(ParseErrorCode code, String message, Integer rowNum, String column, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.rowNum = rowNum;
        this.column = column;
    }

    public ParseErrorCode getCode() {
        return code;
    }

    /** 1-based row as the administrator sees it (the header row is 1), or null when not row-specific. */
    public Integer getRowNum() {
        return rowNum;
    }

    /** Display header (or column letter for a header problem), or null. */
    public String getColumn() {
        return column;
    }
}
