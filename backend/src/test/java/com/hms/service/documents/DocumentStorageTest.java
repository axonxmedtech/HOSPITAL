package com.hms.service.documents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where a document lives and how it is read back.
 *
 * <p>Knows nothing about patients or permissions on purpose: this is the only class that touches
 * the filesystem, so path safety is provable by reading one file rather than auditing every caller.
 */
class DocumentStorageTest {

    @TempDir Path root;

    private DocumentStorage storage() {
        DocumentStorage s = new DocumentStorage();
        ReflectionTestUtils.setField(s, "baseDir", root.toString());
        return s;
    }

    @Test
    void storesTheFileUnderItsHospitalAndReadsItBack() throws Exception {
        byte[] content = "a lab report".getBytes();
        String stored = storage().store(7L,
                new MockMultipartFile("file", "report.pdf", "application/pdf", content), "pdf");

        assertThat(Files.exists(root.resolve("7").resolve(stored))).isTrue();
        assertThat(storage().read(7L, stored).readAllBytes()).isEqualTo(content);
    }

    /**
     * The stored name is generated, never the uploader's. Two people attaching "report.pdf" on the
     * same day must not overwrite one another.
     */
    @Test
    void generatesAUniqueNameRatherThanUsingTheUploadersFilename() throws Exception {
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", "x".getBytes());

        String first = storage().store(7L, file, "pdf");
        String second = storage().store(7L, file, "pdf");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("report");
        assertThat(first).endsWith(".pdf");
    }

    /** One hospital's directory must not be reachable from another's identifier. */
    @Test
    void keepsHospitalsInSeparateDirectories() throws Exception {
        String a = storage().store(7L,
                new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes()), "pdf");

        assertThat(Files.exists(root.resolve("7").resolve(a))).isTrue();
        assertThat(Files.exists(root.resolve("8").resolve(a))).isFalse();
    }

    /**
     * The stored name reaches this class from the database, but a crafted row — or a future caller
     * that passes something through from a request — must not be able to climb out of the folder.
     */
    @Test
    void refusesAStoredNameThatTriesToEscapeTheDirectory() {
        assertThatThrownBy(() -> storage().read(7L, "../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage().read(7L, "sub/dir/file.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * "." normalises to the hospital directory itself, which trivially satisfies a plain
     * {@code startsWith} check. Left unguarded, {@code delete(id, ".")} deletes the whole
     * hospital directory when it happens to be empty, and {@code read(id, ".")} opens it as a
     * stream. This cannot arise from {@code store()}, which always builds its own UUID name, but
     * a crafted or corrupted {@code stored_filename} row must not be able to reach either.
     */
    @Test
    void refusesADotAsTheStoredName() throws Exception {
        Path hospitalDir = root.resolve("7");
        Files.createDirectories(hospitalDir);

        assertThatThrownBy(() -> storage().delete(7L, "."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(hospitalDir))
                .as("the hospital directory itself must never be deleted")
                .isTrue();

        assertThatThrownBy(() -> storage().read(7L, "."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesBlankOrWhitespaceOnlyStoredNames() {
        assertThatThrownBy(() -> storage().read(7L, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage().read(7L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readingSomethingThatIsNotThereIsAClearError() {
        assertThatThrownBy(() -> storage().read(7L, "nope.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer");
    }

    @Test
    void deleteRemovesTheFileAndToleratesItAlreadyBeingGone() throws Exception {
        String stored = storage().store(7L,
                new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes()), "pdf");

        storage().delete(7L, stored);
        assertThat(Files.exists(root.resolve("7").resolve(stored))).isFalse();

        storage().delete(7L, stored); // must not throw
    }

    /** A NUL survives every contains() check and would otherwise fail inside Path.resolve. */
    @Test
    void refusesANulByteInTheName() {
        // Built with a char cast so the NUL is visible in source rather than an invisible byte
        // that makes this whole file read as binary to grep, diffs and review tools.
        String withNul = "report" + ((char) 0) + ".pdf";
        assertThatThrownBy(() -> storage().read(7L, withNul))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document reference");
    }

    @Test
    void aNullStoredNameIsRefused() {
        assertThatThrownBy(() -> storage().read(7L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The message a user sees must not carry the server's filesystem layout: GlobalExceptionHandler
     * puts IllegalArgumentException messages straight into the response the browser renders.
     */
    @Test
    void aFailureDoesNotLeakTheServerPathToTheUser() {
        DocumentStorage storage = new DocumentStorage();
        ReflectionTestUtils.setField(storage, "baseDir", root.resolve("nope").toString());

        assertThatThrownBy(() -> storage.read(7L, "missing.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(root.toString());
                    assertThat(e.getMessage()).doesNotContain("nope");
                });
    }
}
