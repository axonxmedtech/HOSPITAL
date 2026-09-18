package com.hms.controller.hospital;

import com.hms.dto.ApiErrorResponse;
import com.hms.exception.ErrorCode;
import com.hms.filter.CorrelationIdFilter;
import com.hms.service.import_.AlreadyImportedException;
import com.hms.service.import_.ImportParseException;
import com.hms.service.import_.ImportRunFailedException;
import com.hms.service.import_.InvalidImportMappingException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error contract for the import endpoints only (scoped by {@code assignableTypes}, ordered ahead
 * of the global handler so its catch-alls never see these). Reuses the project's
 * {@link ApiErrorResponse} and {@link ErrorCode}; adds nothing to the global handler.
 *
 * <p>Parse failures are answered from a fixed table keyed by {@code ParseErrorCode}: the
 * exception's own message may name a row and a column, which are safe, but this layer never
 * echoes it wholesale. Nothing here carries exception class names, SQL, temp paths or cell text.
 */
@RestControllerAdvice(assignableTypes = PatientImportController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PatientImportExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(PatientImportExceptionAdvice.class);

    /** A multipart request without the {@code file} part (or the mapping) is a client error, not a server one. */
    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<ApiErrorResponse> missingPart(Exception ex) {
        return respond(ErrorCode.VALIDATION_ERROR, "The request must be multipart/form-data with a \"file\" part and a \"mapping\" part.", null);
    }

    @ExceptionHandler(InvalidImportMappingException.class)
    public ResponseEntity<ApiErrorResponse> mapping(InvalidImportMappingException ex) {
        return respond(ErrorCode.VALIDATION_ERROR, ex.getMessage(), null);
    }

    @ExceptionHandler(ImportParseException.class)
    public ResponseEntity<ApiErrorResponse> parse(ImportParseException ex) {
        String message = switch (ex.getCode()) {
            case EMPTY_FILE -> "The file is empty.";
            case NO_HEADER_ROW -> "The file has no header row.";
            case BLANK_HEADER -> "A column has a blank header" + where(ex) + ". Every column needs a name.";
            case HEADER_TOO_LONG -> "A column header is too long" + where(ex) + ".";
            case DUPLICATE_HEADER -> "A column header repeats an earlier one" + where(ex) + ". Rename or remove one of them.";
            case TOO_MANY_COLUMNS -> "The file has more columns than the import supports.";
            case TOO_MANY_ROWS -> "The file has more rows than one import supports. Split it and import the parts separately.";
            case MALFORMED_CSV -> "The CSV file has malformed quoting" + where(ex) + ".";
            case NOT_AN_XLSX -> "The file is not a readable Excel (.xlsx) workbook. Save it as .xlsx or .csv and upload again.";
            case SHEET_NOT_FOUND -> "The workbook has no sheet with that name.";
            case UNREADABLE -> "The file could not be read. It may be damaged or not a supported format.";
        };
        Map<String, String> details = new LinkedHashMap<>();
        details.put("parseError", ex.getCode().name());
        if (ex.getRowNum() != null) details.put("rowNum", String.valueOf(ex.getRowNum()));
        if (ex.getColumn() != null) details.put("column", ex.getColumn());
        return respond(ErrorCode.VALIDATION_ERROR, message, details);
    }

    @ExceptionHandler(AlreadyImportedException.class)
    public ResponseEntity<ApiErrorResponse> alreadyImported(AlreadyImportedException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("condition", ex.getCondition().name());
        details.put("detailsAvailable", String.valueOf(ex.isDetailsAvailable()));
        details.put("status", ex.getStatus().name());
        ex.getBatchPublicId().ifPresent(id -> details.put("batchPublicId", id));
        ex.getCommittedAt().ifPresent(t -> details.put("committedAt", t.toString()));
        return respond(ErrorCode.CONFLICT, ex.getMessage(), details);
    }

    @ExceptionHandler(ImportRunFailedException.class)
    public ResponseEntity<ApiErrorResponse> runFailed(ImportRunFailedException ex) {
        log.error("Import {} failed: {}", ex.getBatchPublicId(), ex.getCause() == null ? "?" : ex.getCause().getClass().getSimpleName());
        Map<String, String> details = new LinkedHashMap<>();
        details.put("batchPublicId", ex.getBatchPublicId());
        return respond(ErrorCode.UNEXPECTED_ERROR,
                "The import stopped because of a system problem. Rows already imported were kept; check the import's status and retry the file.", details);
    }

    private static ResponseEntity<ApiErrorResponse> respond(ErrorCode code, String message, Map<String, String> details) {
        ApiErrorResponse body = new ApiErrorResponse(false, code, message, message, details, null, CorrelationIdFilter.currentId(), null);
        return ResponseEntity.status(code.status()).body(body);
    }

    private static String where(ImportParseException ex) {
        StringBuilder sb = new StringBuilder();
        if (ex.getRowNum() != null) sb.append(" (row ").append(ex.getRowNum());
        if (ex.getColumn() != null) sb.append(sb.length() == 0 ? " (column " : ", column ").append(ex.getColumn());
        if (sb.length() > 0) sb.append(")");
        return sb.toString();
    }
}
