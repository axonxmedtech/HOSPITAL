package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The temp-file lifecycle. The upload is patient data on disk: it must have an unpredictable
 * name, be readable by nobody else, exist only between spool and close, and be gone after a
 * successful parse, an early stop and a parser failure alike. The parser itself must never leave
 * a file of its own behind.
 */
class SpooledUploadTest {

    private static List<Path> filesIn(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.toList();
        }
    }

    @Test
    void spoolsToAnUnpredictablyNamedOwnerOnlyFileAndDeletesItOnClose(@TempDir Path dir) throws Exception {
        Path created;
        try (SpooledUpload upload = SpooledUpload.spool(new ByteArrayInputStream("Name\nA\n".getBytes(StandardCharsets.UTF_8)), dir)) {
            List<Path> files = filesIn(dir);
            assertThat(files).hasSize(1);
            created = files.get(0);
            assertThat(created.getFileName().toString()).startsWith("hms-import-").endsWith(".upload");
            assertThat(created.getFileName().toString()).hasSizeGreaterThan("hms-import-.upload".length() + 4);
            assertThat(upload.size()).isEqualTo(7);
            assertThat(upload.isEmpty()).isFalse();
            if (dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(created);
                assertThat(perms).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            }
        }
        assertThat(Files.exists(created)).isFalse();
        assertThat(filesIn(dir)).isEmpty();
    }

    @Test
    void canBeReadMoreThanOnceAndFingerprinted(@TempDir Path dir) throws Exception {
        byte[] content = "Name,Phone\nTest,9000000001\n".getBytes(StandardCharsets.UTF_8);
        try (SpooledUpload upload = SpooledUpload.spool(new ByteArrayInputStream(content), dir)) {
            try (InputStream a = upload.open()) {
                assertThat(a.readAllBytes()).isEqualTo(content);
            }
            try (InputStream b = upload.open()) {
                assertThat(b.readAllBytes()).isEqualTo(content);
            }
            String sha = upload.sha256();
            assertThat(sha).hasSize(64).matches("[0-9a-f]+");
            assertThat(upload.sha256()).isEqualTo(sha); // deterministic, and reading it did not consume the file
            try (InputStream c = upload.open()) {
                assertThat(c.readAllBytes()).isEqualTo(content);
            }
        }
    }

    @Test
    void refusesUseAfterClose(@TempDir Path dir) throws Exception {
        SpooledUpload upload = SpooledUpload.spool(new ByteArrayInputStream(new byte[] {1}), dir);
        upload.close();
        upload.close(); // idempotent
        assertThatThrownBy(upload::open).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(upload::sha256).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nothingIsLeftBehindAfterASuccessfulParse(@TempDir Path dir) throws Exception {
        WorkbookParser parser = new WorkbookParser();
        byte[] wb = ImportTestFiles.xlsx(List.of(List.of("Name"), List.of("Test")));
        try (SpooledUpload upload = SpooledUpload.spool(new ByteArrayInputStream(wb), dir)) {
            parser.parse(upload, ImportFormat.XLSX, null, new ImportTestFiles.Collecting());
            assertThat(filesIn(dir)).hasSize(1); // only the spool; the parser made no file of its own
        }
        assertThat(filesIn(dir)).isEmpty();
    }

    @Test
    void nothingIsLeftBehindAfterAParserFailure(@TempDir Path dir) throws Exception {
        WorkbookParser parser = new WorkbookParser();
        byte[] junk = "definitely,not,a,workbook".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> {
                    try (SpooledUpload upload = SpooledUpload.spool(new ByteArrayInputStream(junk), dir)) {
                        parser.parse(upload, ImportFormat.XLSX, null, new ImportTestFiles.Collecting());
                    }
                })
                .isInstanceOf(ImportParseException.class);
        assertThat(filesIn(dir)).isEmpty();

        assertThatThrownBy(() -> {
                    try (SpooledUpload upload = SpooledUpload.spool(
                            new ByteArrayInputStream("Name,Name\nA,B\n".getBytes(StandardCharsets.UTF_8)), dir)) {
                        parser.parse(upload, ImportFormat.CSV, null, new ImportTestFiles.Collecting());
                    }
                })
                .isInstanceOf(ImportParseException.class);
        assertThat(filesIn(dir)).isEmpty();
    }

    @Test
    void nothingIsLeftBehindWhenTheSinkStopsEarly(@TempDir Path dir) throws Exception {
        WorkbookParser parser = new WorkbookParser();
        ImportTestFiles.Collecting sink = new ImportTestFiles.Collecting();
        sink.stopAfter = 1;
        byte[] wb = ImportTestFiles.xlsx(List.of(List.of("Name"), List.of("A"), List.of("B")));
        try (SpooledUpload upload = SpooledUpload.spool(new ByteArrayInputStream(wb), dir)) {
            parser.parse(upload, ImportFormat.XLSX, null, sink);
        }
        assertThat(sink.rows).hasSize(1);
        assertThat(filesIn(dir)).isEmpty();
    }

    @Test
    void aFailedSpoolLeavesNoFile(@TempDir Path dir) throws Exception {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("network dropped");
            }
        };
        assertThatThrownBy(() -> SpooledUpload.spool(failing, dir)).isInstanceOf(IOException.class);
        assertThat(filesIn(dir)).isEmpty();
    }
}
