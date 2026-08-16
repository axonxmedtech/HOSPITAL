package com.hms.service.documents;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What may be uploaded, checked before anything touches the disk.
 *
 * <p>The extension and the browser's content type are both attacker-controlled, so neither is
 * trusted on its own: the file's leading bytes have to agree with what it claims to be. Otherwise
 * "report.pdf" can be anything at all, and we would store and later hand it back to a doctor.
 */
class UploadedFileValidatorTest {

    private final UploadedFileValidator validator = new UploadedFileValidator();

    /** %PDF- */
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
    /** JPEG SOI + APP0 */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    /** RIFF ---- WEBP */
    private static final byte[] WEBP =
            {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    /** size, "ftyp", major brand "heic" */
    private static final byte[] HEIC =
            {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};

    @Test
    void acceptsAPdf() {
        validator.validate(new MockMultipartFile("file", "report.pdf", "application/pdf", PDF));
    }

    @Test
    void acceptsPhotographs() {
        validator.validate(new MockMultipartFile("file", "scan.jpg", "image/jpeg", JPEG));
        validator.validate(new MockMultipartFile("file", "scan.png", "image/png", PNG));
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No file");
    }

    @Test
    void rejectsAnExtensionWeDoNotAccept() {
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "report.exe", "application/pdf", PDF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }

    /**
     * The important one. A .pdf whose bytes are an executable must be refused, or the extension is
     * the only thing standing between an attacker and a file we store and later serve back.
     */
    @Test
    void rejectsAFileWhoseContentDisagreesWithItsExtension() {
        byte[] executable = {0x4D, 0x5A, (byte) 0x90, 0x00}; // MZ — a Windows executable
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "report.pdf", "application/pdf", executable)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not look like");
    }

    /**
     * The configured value, not a constant, is what gets enforced and what gets reported.
     * {@code init()} is invoked explicitly to mirror what Spring does automatically: inject the
     * property, then run the {@code @PostConstruct} that caches its parsed value.
     */
    @Test
    void rejectsSomethingTooLarge() {
        ReflectionTestUtils.setField(validator, "maxFileSize", "25MB");
        ReflectionTestUtils.invokeMethod(validator, "init");
        byte[] big = new byte[26 * 1024 * 1024];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "big.pdf", "application/pdf", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("25 MB");
    }

    /**
     * A different configured limit must produce a different message. If the cap were still a
     * hardcoded constant, this file (11 MB) would be rejected against 25 MB with the wrong number
     * in the error, or not rejected at all.
     */
    @Test
    void aDifferentConfiguredLimitProducesADifferentMessage() {
        ReflectionTestUtils.setField(validator, "maxFileSize", "10MB");
        ReflectionTestUtils.invokeMethod(validator, "init");
        byte[] big = new byte[11 * 1024 * 1024];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "big.pdf", "application/pdf", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB")
                .hasMessageNotContaining("25 MB");
    }

    /** Exposed so the upload dialog can state the limit before the user picks an oversized file. */
    @Test
    void exposesTheConfiguredLimitInBytes() {
        ReflectionTestUtils.setField(validator, "maxFileSize", "10MB");
        ReflectionTestUtils.invokeMethod(validator, "init");
        assertThat(validator.maxBytes()).isEqualTo(10L * 1024 * 1024);
    }

    /**
     * A malformed property (a typo like "25MBx") must fail loudly when Spring builds the bean,
     * not silently on someone's first upload of the day.
     */
    @Test
    void aMalformedConfiguredLimitFailsEagerlyRatherThanOnFirstUpload() {
        ReflectionTestUtils.setField(validator, "maxFileSize", "25MBx");
        // DataSize.parse throws IllegalStateException; the point under test is that init() (which
        // Spring calls automatically via @PostConstruct, failing the context at boot) throws at
        // all for a malformed value, rather than deferring the failure to the first upload.
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(validator, "init"))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    /** iPhones produce HEIC. Refusing it would reject a genuinely common upload. */
    @Test
    void acceptsHeicEvenThoughBrowsersCannotDisplayIt() {
        validator.validate(new MockMultipartFile("file", "IMG_4471.heic", "image/heic", HEIC));
    }

    /**
     * "ftyp" alone only says the file is some ISO-BMFF container — MP4, MOV, M4A, 3GP and AVIF
     * all carry it too. A video renamed to .heic must still be refused; only the major brand at
     * offset 8 actually identifies HEIC/HEIF.
     */
    @Test
    void rejectsAnMp4RenamedToHeic() {
        byte[] mp4WithFtyp = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}; // MP4 brand "isom"
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "video.heic", "video/mp4", mp4WithFtyp)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not look like");
    }

    @Test
    void reportsTheExtensionItWillStoreUnder() {
        assertThat(validator.extensionOf("Blood Report FINAL.PDF")).isEqualTo("pdf");
        assertThat(validator.extensionOf("no-extension")).isEqualTo("bin");
    }

    /**
     * {@code InputStream.read(byte[])} only guarantees at least one byte, not a full buffer.
     * {@code MockMultipartFile}'s stream always satisfies a full read in one call, so it can never
     * exercise this — every current test using it would pass even if the validator compared a
     * short read against zero-padding. This stream deliberately starves the reader two bytes at a
     * time so a fix has to loop (or use {@code readNBytes}) to see the real WEBP marker at offset 8.
     */
    @Test
    void acceptsAFileWhoseHeaderArrivesAFewBytesAtATime() {
        MultipartFile slow = new TwoByteAtATimeMultipartFile("scan.webp", "image/webp", WEBP);
        validator.validate(slow);
    }

    /** A stub MultipartFile whose stream hands back at most two bytes per {@code read} call. */
    private static class TwoByteAtATimeMultipartFile implements MultipartFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;

        TwoByteAtATimeMultipartFile(String filename, String contentType, byte[] content) {
            this.filename = filename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new TwoByteAtATimeStream(content); }
        @Override public void transferTo(java.io.File dest) {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    private static class TwoByteAtATimeStream extends InputStream {
        private final byte[] data;
        private int pos = 0;

        TwoByteAtATimeStream(byte[] data) { this.data = data; }

        @Override
        public int read() {
            return pos >= data.length ? -1 : (data[pos++] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos >= data.length) return -1;
            int n = Math.min(2, Math.min(len, data.length - pos));
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
    }
}
