package com.hms.service.documents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether an upload may be stored.
 *
 * <p>Extension and declared content type both come from the browser and are trivially forged, so
 * the file's leading bytes must agree with the extension. Without that check "report.pdf" can hold
 * anything, and we would keep it and hand it back to a doctor later.
 */
@Component
public class UploadedFileValidator {

    private static final Set<String> ALLOWED =
            Set.of("pdf", "jpg", "jpeg", "png", "webp", "heic");

    /**
     * Defaulted (not injected) so {@code new UploadedFileValidator()} in tests keeps this literal
     * default; Spring overwrites it via field injection from {@code hms.documents.max-file-size}.
     * A missing property must not silently mean "no limit", hence the default here too.
     */
    @Value("${hms.documents.max-file-size:25MB}")
    private String maxFileSize = "25MB";

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        // Message derived from the configured value rather than restated, so an environment that
        // overrides hms.documents.max-file-size cannot end up telling people the wrong number.
        long maxBytes = maxBytes();
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File is too large. Maximum size is "
                    + (maxBytes / (1024 * 1024)) + " MB.");
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED.contains(ext)) {
            throw new IllegalArgumentException(
                    "Only PDF and image files (JPG, PNG, WEBP, HEIC) can be attached.");
        }

        byte[] head = new byte[16];
        try (var in = file.getInputStream()) {
            int read = in.read(head);
            if (read < 4) {
                throw new IllegalArgumentException("The file appears to be empty or unreadable.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read: " + e.getMessage());
        }

        if (!looksLike(ext, head)) {
            throw new IllegalArgumentException(
                    "This file does not look like a " + ext.toUpperCase(Locale.ROOT)
                    + ". Re-save it and try again.");
        }
    }

    /** The configured cap in bytes, so the UI can state a limit before a file is even picked. */
    public long maxBytes() {
        return DataSize.parse(maxFileSize).toBytes();
    }

    /** Lowercase extension, or "bin" when there is none. Never used to build a path. */
    public String extensionOf(String filename) {
        if (filename == null) return "bin";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "bin";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean looksLike(String ext, byte[] head) {
        return switch (ext) {
            case "pdf" -> startsWith(head, 0x25, 0x50, 0x44, 0x46);                 // %PDF
            case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47);
            // WEBP and HEIC are container formats: the marker sits at offset 4/8, not 0.
            case "webp" -> matchesAt(head, 8, 'W', 'E', 'B', 'P');
            case "heic" -> matchesAt(head, 4, 'f', 't', 'y', 'p');
            default -> false;
        };
    }

    private boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private boolean matchesAt(byte[] head, int offset, char... expected) {
        if (head.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((char) (head[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }
}
