package com.hms.service.documents;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>{@code validate} only reads the header, and Task 3's {@code DocumentStorage} reads the whole
 * upload again from the start afterwards. That is safe only because Spring's default
 * {@code StandardServletMultipartResolver} fully materialises each part before the controller
 * runs, so every call to {@link MultipartFile#getInputStream()} returns a fresh stream from byte
 * zero — the interface itself makes no such promise. A move to a genuinely single-pass streaming
 * multipart parser would break that silently; this comment is the flag to update this class and
 * {@code DocumentStorage.store} together if that ever happens.
 */
@Component
public class UploadedFileValidator {

    private static final Logger log = LoggerFactory.getLogger(UploadedFileValidator.class);

    private static final Set<String> ALLOWED =
            Set.of("pdf", "jpg", "jpeg", "png", "webp", "heic");

    /** ISO-BMFF major brands that actually mean HEIC/HEIF, as opposed to MP4, MOV, M4A, 3GP, AVIF. */
    private static final Set<String> HEIC_BRANDS =
            Set.of("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1");

    /**
     * Defaulted (not injected) so {@code new UploadedFileValidator()} in tests keeps this literal
     * default; Spring overwrites it via field injection from {@code hms.documents.max-file-size}
     * before calling {@link #init()}. A missing property must not silently mean "no limit", hence
     * the default here too.
     */
    @Value("${hms.documents.max-file-size:25MB}")
    private String maxFileSize = "25MB";

    /**
     * Parsed once and cached, rather than on every upload, so a malformed value like "25MBx"
     * fails the Spring context at boot — where it belongs — instead of making every upload throw
     * until someone connects the dots mid-shift. Defaulted here too so a validator built directly
     * (the way tests do) behaves sanely before {@link #init()} runs.
     */
    private long cachedMaxBytes = DataSize.parse("25MB").toBytes();

    @PostConstruct
    private void init() {
        cachedMaxBytes = DataSize.parse(maxFileSize).toBytes();
    }

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
        int read;
        try (var in = file.getInputStream()) {
            // InputStream.read(byte[]) only guarantees at least one byte, not a full buffer.
            // readNBytes loops until the buffer is full or EOF, so `read` reflects bytes genuinely
            // present rather than the array's fixed capacity — a short read must mean "not enough
            // data to tell", never "compared against zero-padding".
            read = in.readNBytes(head, 0, head.length);
            if (read < 4) {
                throw new IllegalArgumentException("The file appears to be empty or unreadable.");
            }
        } catch (IOException e) {
            // The raw message can carry server-side temp paths depending on the multipart
            // implementation, so it is logged, never handed to the caller.
            log.warn("Could not read uploaded file header: {}", e.getMessage());
            throw new IllegalArgumentException("The file could not be read. Please try again.");
        }

        if (!looksLike(ext, head, read)) {
            throw new IllegalArgumentException(
                    "This file does not look like a " + ext.toUpperCase(Locale.ROOT)
                    + ". Re-save it and try again.");
        }
    }

    /** The configured cap in bytes, so the UI can state a limit before a file is even picked. */
    public long maxBytes() {
        return cachedMaxBytes;
    }

    /** Lowercase extension, or "bin" when there is none. Never used to build a path. */
    public String extensionOf(String filename) {
        if (filename == null) return "bin";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "bin";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean looksLike(String ext, byte[] head, int read) {
        return switch (ext) {
            case "pdf" -> startsWith(head, read, 0x25, 0x50, 0x44, 0x46);            // %PDF
            case "jpg", "jpeg" -> startsWith(head, read, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, read, 0x89, 0x50, 0x4E, 0x47);
            // WEBP's form type sits at offset 8, not 0 — checking it (not just "RIFF") is what
            // actually identifies WEBP among RIFF containers.
            case "webp" -> matchesAt(head, read, 8, 'W', 'E', 'B', 'P');
            case "heic" -> isHeic(head, read);
            default -> false;
        };
    }

    /**
     * "ftyp" at offset 4 only says this is some ISO-BMFF container — MP4, MOV, M4A, 3GP and AVIF
     * all carry it too. The major brand at offset 8 is what actually distinguishes HEIC/HEIF, the
     * same way the WEBP check above looks at the form type rather than just "RIFF".
     */
    private boolean isHeic(byte[] head, int read) {
        if (!matchesAt(head, read, 4, 'f', 't', 'y', 'p')) return false;
        return HEIC_BRANDS.contains(brandAt(head, read, 8));
    }

    private String brandAt(byte[] head, int read, int offset) {
        if (read < offset + 4) return "";
        StringBuilder brand = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            brand.append((char) (head[offset + i] & 0xFF));
        }
        return brand.toString();
    }

    private boolean startsWith(byte[] head, int read, int... expected) {
        if (read < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private boolean matchesAt(byte[] head, int read, int offset, char... expected) {
        if (read < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((char) (head[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }
}
