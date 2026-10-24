package com.hms.exception;

import com.hms.dto.ApiErrorResponse;
import com.hms.filter.CorrelationIdFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * The container's own multipart ceiling, reached despite the pre-parse guard (a lying
 * Content-Length, or a part over the per-file ceiling inside an otherwise small request): a
 * 413 with the project's error body, not the catch-all 500. Ordered ahead of the global handler
 * so its RuntimeException net never sees this. Carries no container internals.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MultipartLimitAdvice {

    @ExceptionHandler(UploadTooLargeException.class)
    public ResponseEntity<ApiErrorResponse> tooLargeFile(UploadTooLargeException ex) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.status())
                .body(ApiErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE, ex.getMessage(), CorrelationIdFilter.currentId()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> tooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.status())
                .body(ApiErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE, "The upload is too large.", CorrelationIdFilter.currentId()));
    }
}
