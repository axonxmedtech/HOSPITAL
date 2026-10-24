package com.hms.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.ApiErrorResponse;
import com.hms.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The upload boundary, applied to every {@code multipart/*} request before Spring Security runs
 * and before any part is parsed. It reads request METADATA only — method, path, Content-Type,
 * Content-Length — and never touches the body: it does not call {@code getParts()},
 * {@code getParameter()} or the input stream, so the container has not begun materialising the
 * upload when this decides. (Multipart parsing happens later, in {@code DispatcherServlet}, and
 * {@code UploadBoundaryTest} proves that with a counting resolver rather than by assumption.)
 *
 * <p>Rules:
 * <ul>
 *   <li>No Content-Length (chunked, or a negative value) → {@code 411 Length Required}. The
 *       container's own limits would still apply, but a request whose size cannot be known
 *       up front is refused deterministically rather than partly read. Every browser, axios and
 *       the JDK client send Content-Length for multipart bodies.</li>
 *   <li>Content-Length over the route's request ceiling ({@link UploadLimits}) →
 *       {@code 413 Payload Too Large} with the project's error body.</li>
 * </ul>
 * Non-multipart requests pass straight through.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadSizeGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UploadSizeGuardFilter.class);

    private final ObjectMapper json;

    public UploadSizeGuardFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isMultipart(request)) {
            chain.doFilter(request, response);
            return;
        }
        long declared = request.getContentLengthLong();
        if (declared < 0) {
            if (UploadLimits.isImportUploadRoute(request)) {
                // An import whose size cannot be known up front is refused rather than partly read.
                refuse(response, ErrorCode.LENGTH_REQUIRED, "Import uploads must declare their size (Content-Length); chunked uploads are not accepted.");
                return;
            }
            // Every other route keeps the behaviour it always had: the container's own 5 MiB / 6 MiB
            // ceiling, applied while parsing. Nothing here changes an existing contract.
            chain.doFilter(request, response);
            return;
        }
        long limit = UploadLimits.requestLimitFor(request);
        if (declared > limit) {
            log.info("Upload refused before parsing: {} bytes declared on {} {} (limit {})",
                    declared, request.getMethod(), request.getRequestURI(), limit);
            refuse(response, ErrorCode.PAYLOAD_TOO_LARGE, UploadLimits.isImportUploadRoute(request)
                    ? "The import file is too large. The maximum is 50 MB."
                    : "The upload is too large. The maximum is 5 MB.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private void refuse(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = ApiErrorResponse.of(code, message, CorrelationIdFilter.currentId());
        json.writeValue(response.getOutputStream(), body);
        response.flushBuffer();
    }
}
