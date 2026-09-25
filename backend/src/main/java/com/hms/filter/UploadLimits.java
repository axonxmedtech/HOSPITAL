package com.hms.filter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * The upload boundaries, in exact bytes. Two classes of multipart route exist:
 *
 * <ul>
 *   <li><b>Patient import</b> ({@code POST} to exactly {@code /hospital/patients/import/preview},
 *       {@code /hospital/patients/import/commit} or their {@code /clinic} aliases): one file of at
 *       most 50 MiB, in a request of at most 51 MiB — the file plus the mapping part (≤64 KiB) and
 *       multipart framing.</li>
 *   <li><b>Everything else</b>: the limits the application had before the import existed — a
 *       5 MiB file in a 6 MiB request — unchanged.</li>
 * </ul>
 *
 * <p>The import routes are served by their own servlet whose multipart ceiling is the import
 * numbers ({@code ImportUploadServletConfig}); the default servlet, and so every other multipart
 * endpoint, keeps the application's 5 MiB / 6 MiB exactly as before. {@link UploadSizeGuardFilter}
 * refuses over-limit declared lengths before a byte is parsed, on top of that.
 */
public final class UploadLimits {

    private UploadLimits() {}

    public static final long MEBIBYTE = 1024L * 1024L;

    /** 52,428,800 — the import file itself. */
    public static final long IMPORT_FILE_BYTES = 50L * MEBIBYTE;
    /** 53,477,376 — the whole import request: the file, a mapping part of at most 64 KiB, and framing. */
    public static final long IMPORT_REQUEST_BYTES = 51L * MEBIBYTE;
    /** 5,242,880 — every other upload's file, as before. */
    public static final long DEFAULT_FILE_BYTES = 5L * MEBIBYTE;
    /** 6,291,456 — every other multipart request in total, as before. */
    public static final long DEFAULT_REQUEST_BYTES = 6L * MEBIBYTE;

    private static final Set<String> IMPORT_UPLOAD_PATHS = Set.of(
            "/hospital/patients/import/preview",
            "/hospital/patients/import/commit",
            "/clinic/patients/import/preview",
            "/clinic/patients/import/commit");

    /**
     * True only for a {@code POST} whose path, with the context path removed, is EXACTLY one of the
     * import upload routes. Anything odd — an encoded character, a dot segment, a double slash, a
     * path parameter — is not an import route, whatever it might decode to; it falls to the
     * default limit and lets the container answer it.
     */
    public static boolean isImportUploadRoute(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        String uri = request.getRequestURI();
        if (uri == null) return false;
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty()) {
            if (!uri.startsWith(ctx)) return false;
            uri = uri.substring(ctx.length());
        }
        if (uri.contains("%") || uri.contains("..") || uri.contains("//") || uri.contains(";") || uri.contains("\\")) return false;
        return IMPORT_UPLOAD_PATHS.contains(uri);
    }

    public static long requestLimitFor(HttpServletRequest request) {
        return isImportUploadRoute(request) ? IMPORT_REQUEST_BYTES : DEFAULT_REQUEST_BYTES;
    }
}
