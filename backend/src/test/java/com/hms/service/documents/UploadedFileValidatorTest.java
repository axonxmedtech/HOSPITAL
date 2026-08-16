package com.hms.service.documents;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

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

    @Test
    void rejectsSomethingTooLarge() {
        byte[] big = new byte[26 * 1024 * 1024];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThatThrownBy(() ->
                validator.validate(new MockMultipartFile("file", "big.pdf", "application/pdf", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("25 MB");
    }

    /** iPhones produce HEIC. Refusing it would reject a genuinely common upload. */
    @Test
    void acceptsHeicEvenThoughBrowsersCannotDisplayIt() {
        byte[] heic = new byte[]{0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63};
        validator.validate(new MockMultipartFile("file", "IMG_4471.heic", "image/heic", heic));
    }

    @Test
    void reportsTheExtensionItWillStoreUnder() {
        assertThat(validator.extensionOf("Blood Report FINAL.PDF")).isEqualTo("pdf");
        assertThat(validator.extensionOf("no-extension")).isEqualTo("bin");
    }
}
