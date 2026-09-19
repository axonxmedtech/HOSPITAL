package com.hms.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hms.entity.Hospital;
import com.hms.entity.import_.ImportStatus;
import com.hms.filter.UploadLimits;
import com.hms.repository.HospitalRepository;
import com.hms.security.JwtUtil;
import com.hms.service.hospital.PatientDocumentService;
import com.hms.service.import_.ImportCommitSummary;
import com.hms.service.import_.ImportCounters;
import com.hms.service.import_.ImportEngine;
import com.hms.service.import_.ImportPreview;
import com.hms.util.CsvUploads;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * The upload boundary, on real HTTP against the real filter chain.
 *
 * <p>Two instruments make the lifecycle observable rather than assumed. The multipart resolver is
 * wrapped so every parse is counted: a request the guard refuses must leave the count untouched,
 * and one it admits must advance it — that is the empirical answer to "does the guard run before
 * multipart parsing". And {@link ImportEngine} is a mock, so "never reached business processing"
 * is a Mockito verification, not an inference.
 *
 * <p>Bodies are streamed from a generator, never held as a 50 MiB array; the import limits are
 * exercised at their exact byte boundaries, once each.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(UploadBoundaryTest.CountingResolver.class)
class UploadBoundaryTest {

    /** Counts multipart parses. Registered under Spring's well-known bean name so it replaces the default. */
    @TestConfiguration
    static class CountingResolver {
        static final AtomicInteger PARSES = new AtomicInteger();

        @Bean(name = "multipartResolver")
        @Primary
        MultipartResolver multipartResolver() {
            return new StandardServletMultipartResolver() {
                @Override
                public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
                    PARSES.incrementAndGet();
                    return super.resolveMultipart(request);
                }
            };
        }
    }

    private static Path spoolDir;
    private static Path tomcatMultipartDir;

    @DynamicPropertySource
    static void dirs(DynamicPropertyRegistry r) throws IOException {
        spoolDir = Files.createTempDirectory("hms-boundary-spool-");
        tomcatMultipartDir = Files.createTempDirectory("hms-boundary-tomcat-");
        r.add("hms.import.spool-dir", () -> spoolDir.toString());
        r.add("spring.servlet.multipart.location", () -> tomcatMultipartDir.toString());
    }

    @LocalServerPort int port;
    @Autowired JwtUtil jwt;
    @Autowired HospitalRepository hospitals;
    @Autowired com.hms.repository.UserRepository users;
    @Autowired MultipartProperties multipartProperties;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("importUploadServlet")
    org.springframework.boot.web.servlet.ServletRegistrationBean<com.hms.config.ImportUploadServletConfig.ImportUploadServlet> importServlet;
    @MockBean ImportEngine engine;

    private static final String MAPPING = "{\"Name\":\"name\",\"Phone\":\"phone\",\"Gender\":\"gender\",\"DOB\":\"dateOfBirth\"}";
    private String admin;
    private String receptionist;

    @BeforeEach
    void tenant() throws Exception {
        Hospital h = new Hospital();
        h.setName("Boundary " + System.nanoTime());
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD"));
        h.setIsSingleDoctor(false);
        long id = hospitals.save(h).getId();
        // Real user rows: session revalidation denies tokens without a matching tokenVersion.
        com.hms.entity.User adm = new com.hms.entity.User();
        adm.setEmail("adm." + System.nanoTime() + "@boundary.test");
        adm.setPassword("{noop}fixture");
        adm.setName("Admin");
        adm.setRole("HOSPITAL_ADMIN");
        adm.setHospitalId(id);
        adm.setIsActive(true);
        adm.setTokenVersion(0);
        adm = users.save(adm);
        admin = jwt.generateToken(adm.getId(), adm.getEmail(), "HOSPITAL_ADMIN", id, List.of("OPD"), null, "HOSPITAL", null, adm.getTokenVersion());
        com.hms.entity.User rec = new com.hms.entity.User();
        rec.setEmail("rec." + System.nanoTime() + "@boundary.test");
        rec.setPassword("{noop}fixture");
        rec.setName("Reception");
        rec.setRole("RECEPTIONIST");
        rec.setHospitalId(id);
        rec.setIsActive(true);
        rec.setTokenVersion(0);
        rec = users.save(rec);
        receptionist = jwt.generateToken(rec.getId(), rec.getEmail(), "RECEPTIONIST", id, List.of("OPD"), null, "HOSPITAL", null, rec.getTokenVersion());
        when(engine.preview(any(), any(), any(), any(), any()))
                .thenReturn(new ImportPreview("csv", List.of("Name"), new ImportCounters.Snapshot(1, 1, 0, 0, 0, 0), List.of(), false, null));
        when(engine.commit(any(), any(), any()))
                .thenReturn(new ImportCommitSummary("id", ImportStatus.COMPLETED, new ImportCounters.Snapshot(1, 1, 0, 0, 0, 0), LocalDateTime.now()));
        when(engine.sheetNames(any())).thenReturn(List.of());
    }

    // ── a streamed multipart body of an exact size ──────────────────────────

    /** Multipart framing around a synthetic file of {@code fileBytes} bytes plus the given extra parts. Streamed. */
    record Body(byte[] head, long fileBytes, byte[] tail) {
        long length() {
            return head.length + fileBytes + tail.length;
        }

        InputStream stream() {
            return new SequenceInputStream(Collections.enumeration(List.of(
                    new java.io.ByteArrayInputStream(head), new RepeatingInputStream(fileBytes), new java.io.ByteArrayInputStream(tail))));
        }
    }

    /** {@code n} bytes of a CSV-shaped line pattern, produced on demand. */
    static final class RepeatingInputStream extends InputStream {
        private static final byte[] PATTERN = "Person X,9000000001,F,1990-01-01\n".getBytes(StandardCharsets.US_ASCII);
        private long remaining;
        private int i;

        RepeatingInputStream(long n) {
            this.remaining = n;
        }

        @Override
        public int read() {
            if (remaining <= 0) return -1;
            remaining--;
            return PATTERN[i++ % PATTERN.length];
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (remaining <= 0) return -1;
            int n = (int) Math.min(len, remaining);
            for (int k = 0; k < n; k++) b[off + k] = PATTERN[i++ % PATTERN.length];
            remaining -= n;
            return n;
        }
    }

    private static final String BOUNDARY = "----hmsBoundary4242";

    /** @param extraParts additional (name → value) parts; a name starting with "file:" becomes a second file part */
    private static Body body(String fileName, long fileBytes, String mapping, String... extraParts) {
        StringBuilder head = new StringBuilder();
        head.append("--").append(BOUNDARY).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"").append(fileName)
                .append("\"\r\nContent-Type: text/csv\r\n\r\nName,Phone,Gender,DOB\n");
        StringBuilder tail = new StringBuilder("\r\n");
        if (mapping != null) {
            tail.append("--").append(BOUNDARY).append("\r\nContent-Disposition: form-data; name=\"mapping\"\r\n\r\n").append(mapping).append("\r\n");
        }
        for (int i = 0; i + 1 < extraParts.length; i += 2) {
            String name = extraParts[i];
            if (name.startsWith("file:")) {
                tail.append("--").append(BOUNDARY).append("\r\nContent-Disposition: form-data; name=\"").append(name.substring(5))
                        .append("\"; filename=\"extra.csv\"\r\nContent-Type: text/csv\r\n\r\n").append(extraParts[i + 1]).append("\r\n");
            } else {
                tail.append("--").append(BOUNDARY).append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                        .append(extraParts[i + 1]).append("\r\n");
            }
        }
        tail.append("--").append(BOUNDARY).append("--\r\n");
        byte[] h = head.toString().getBytes(StandardCharsets.UTF_8);
        // fileBytes counts the whole file part content, header line included
        long synthetic = fileBytes - "Name,Phone,Gender,DOB\n".length();
        return new Body(h, Math.max(0, synthetic), tail.toString().getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(String path, String token, Body body, boolean declareLength) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .timeout(java.time.Duration.ofSeconds(120));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher publisher = declareLength
                ? HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(body::stream), body.length())
                : HttpRequest.BodyPublishers.ofInputStream(body::stream); // chunked: no Content-Length
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(b.POST(publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private long filesIn(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    private static final String IMPORT = "/hospital/patients/import/commit";
    private static final String CLINIC_IMPORT = "/clinic/patients/import/preview";
    private static final String DOCS = "/hospital/patients/1/documents";
    private static final String MEDICINES = "/platform/medicines/import-csv";

    // ── constants ───────────────────────────────────────────────────────────

    @Test
    void theBoundariesAreExactAndTheContainerCeilingMatchesThem() {
        assertThat(UploadLimits.IMPORT_FILE_BYTES).isEqualTo(52_428_800L);
        assertThat(UploadLimits.IMPORT_REQUEST_BYTES).isEqualTo(53_477_376L);
        assertThat(UploadLimits.DEFAULT_FILE_BYTES).isEqualTo(5_242_880L);
        assertThat(UploadLimits.DEFAULT_REQUEST_BYTES).isEqualTo(6_291_456L);
        // The DEFAULT servlet's ceiling is untouched: 5 MiB / 6 MiB, as on staging.
        assertThat(multipartProperties.getMaxFileSize().toBytes()).isEqualTo(5_242_880L);
        assertThat(multipartProperties.getMaxRequestSize().toBytes()).isEqualTo(6_291_456L);
        // The import servlet's ceiling is the import numbers.
        jakarta.servlet.MultipartConfigElement importConfig = importServlet.getMultipartConfig();
        assertThat(importConfig.getMaxFileSize()).isEqualTo(52_428_800L);
        assertThat(importConfig.getMaxRequestSize()).isEqualTo(53_477_376L);
        assertThat(importServlet.getUrlMappings()).containsExactlyInAnyOrder("/hospital/patients/import/*", "/clinic/patients/import/*");
        // the other endpoints' own per-file checks are unchanged
        assertThat(CsvUploads.MAX_BYTES).isEqualTo(5_242_880L);
        assertThat(PatientDocumentService.MAX_BYTES).isEqualTo(5_242_880L);
    }

    // ── import ──────────────────────────────────────────────────────────────

    @Test
    void aTinyImportStillReachesTheEngine() throws Exception {
        HttpResponse<String> r = send(IMPORT, admin, body("a.csv", 100, MAPPING), true);
        assertThat(r.statusCode()).isEqualTo(200);
        verify(engine).commit(any(), any(), any());
        assertThat(filesIn(spoolDir)).isZero();
    }

    @Test
    void exactlyFiftyMebibytesCrossesTheBoundaryAndOneByteMoreDoesNot() throws Exception {
        int before = CountingResolver.PARSES.get();
        HttpResponse<String> ok = send(IMPORT, admin, body("big.csv", UploadLimits.IMPORT_FILE_BYTES, MAPPING), true);
        assertThat(ok.statusCode()).as("50 MiB file: UPLOAD_SIZE_ALLOWED").isEqualTo(200);
        assertThat(CountingResolver.PARSES.get()).isEqualTo(before + 1);
        verify(engine).commit(any(), any(), any());
        assertThat(filesIn(spoolDir)).isZero();

        org.mockito.Mockito.clearInvocations(engine);
        int parsesBefore = CountingResolver.PARSES.get();
        HttpResponse<String> tooBig = send(IMPORT, admin, body("big.csv", UploadLimits.IMPORT_FILE_BYTES + 1, MAPPING), true);
        // 50 MiB + 1 file is inside the 51 MiB request ceiling, so the guard admits it; the
        // container's own 50 MiB per-file ceiling then refuses it during parsing
        // (MaxUploadSizeExceededException → MultipartLimitAdvice → 413). The controller's check is
        // the layer behind that one; the observable contract is the same 413.
        assertThat(tooBig.statusCode()).isEqualTo(413);
        assertThat(tooBig.body()).contains("PAYLOAD_TOO_LARGE").doesNotContain("Tomcat").doesNotContain("Exception").doesNotContain("/tmp").doesNotContain("MaxUpload");
        verify(engine, never()).commit(any(), any(), any());
        assertThat(filesIn(spoolDir)).isZero();
        assertThat(CountingResolver.PARSES.get()).isEqualTo(parsesBefore + 1);
    }

    @Test
    void aRequestOverTheImportCeilingIsRefusedBeforeAnyParsing() throws Exception {
        int before = CountingResolver.PARSES.get();
        // Well over the ceiling (~80 MiB) so this also shows the 413 is actually delivered while
        // Tomcat discards the rest of the body under its default maxSwallowSize.
        HttpResponse<String> r = send(IMPORT, admin, body("big.csv", 80L * UploadLimits.MEBIBYTE, MAPPING), true);
        assertThat(r.statusCode()).isEqualTo(413);
        assertThat(r.body()).contains("PAYLOAD_TOO_LARGE").doesNotContain("Tomcat").doesNotContain("Exception");
        assertThat(CountingResolver.PARSES.get()).as("the guard decided before the resolver ran").isEqualTo(before);
        verify(engine, never()).commit(any(), any(), any());
        assertThat(filesIn(spoolDir)).isZero();
        assertThat(filesIn(tomcatMultipartDir)).isZero();
    }

    @Test
    void extraPartsAreRefusedAndTheMappingIsBounded() throws Exception {
        HttpResponse<String> second = send(IMPORT, admin, body("a.csv", 100, MAPPING, "file:second", "Name\nX\n"), true);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(400);
        assertThat(send(IMPORT, admin, body("a.csv", 100, MAPPING, "surprise", "x"), true).statusCode()).isEqualTo(400);
        String hugeMapping = "{\"Name\":\"name\",\"Phone\":\"" + "p".repeat(70 * 1024) + "\"}";
        assertThat(send(IMPORT, admin, body("a.csv", 100, hugeMapping), true).statusCode()).isEqualTo(400);
        verify(engine, never()).commit(any(), any(), any());
        assertThat(filesIn(spoolDir)).isZero();
    }

    @Test
    void anUnauthenticatedOrUnauthorisedOversizedImportNeverReachesTheEngineOrTheParser() throws Exception {
        int before = CountingResolver.PARSES.get();
        // Under the ceiling, so only security stands between the body and the parser.
        HttpResponse<String> anon = send(IMPORT, null, body("big.csv", 8 * UploadLimits.MEBIBYTE, MAPPING), true);
        HttpResponse<String> rec = send(IMPORT, receptionist, body("big.csv", 8 * UploadLimits.MEBIBYTE, MAPPING), true);
        assertThat(anon.statusCode()).isEqualTo(401);
        assertThat(rec.statusCode()).isEqualTo(403);
        assertThat(CountingResolver.PARSES.get()).as("security answered before multipart materialisation").isEqualTo(before);
        verify(engine, never()).commit(any(), any(), any());
        verify(engine, never()).preview(any(), any(), any(), any(), any());
        assertThat(filesIn(spoolDir)).isZero();
        // and over the ceiling: refused by the guard, before even security
        assertThat(send(IMPORT, null, body("big.csv", UploadLimits.IMPORT_REQUEST_BYTES + 1, MAPPING), true).statusCode()).isEqualTo(413);
        assertThat(CountingResolver.PARSES.get()).isEqualTo(before);
    }

    // ── non-import ──────────────────────────────────────────────────────────

    @Test
    void nonImportUploadsKeepTheirSixMebibyteRequestBoundaryBeforeParsing() throws Exception {
        int before = CountingResolver.PARSES.get();
        // A 5 MiB file in a request under the 6 MiB ceiling: admitted by the guard and parsed —
        // whatever the endpoint then says about it is that endpoint's own contract, unchanged.
        HttpResponse<String> at = send(DOCS, admin, body("doc.pdf", UploadLimits.DEFAULT_FILE_BYTES, null, "documentType", "REPORT"), true);
        assertThat(at.statusCode()).isNotEqualTo(413).isNotEqualTo(411);
        assertThat(CountingResolver.PARSES.get()).isEqualTo(before + 1);
        // A 5 MiB + 1 file in a request still under 6 MiB: the guard admits it, and the container's
        // own (unchanged) 5 MiB per-file ceiling refuses it while parsing — as a 413 now, not a 500.
        HttpResponse<String> fileOver = send(DOCS, admin, body("doc.pdf", UploadLimits.DEFAULT_FILE_BYTES + 1, null, "documentType", "REPORT"), true);
        assertThat(fileOver.statusCode()).isEqualTo(413);
        assertThat(fileOver.body()).contains("PAYLOAD_TOO_LARGE").doesNotContain("Tomcat").doesNotContain("Exception");

        int parsesBefore = CountingResolver.PARSES.get();
        HttpResponse<String> over = send(DOCS, admin, body("doc.pdf", UploadLimits.DEFAULT_REQUEST_BYTES + 1, null, "documentType", "REPORT"), true);
        assertThat(over.statusCode()).isEqualTo(413);
        assertThat(over.body()).contains("PAYLOAD_TOO_LARGE").contains("5 MB");
        assertThat(CountingResolver.PARSES.get()).as("refused before parsing").isEqualTo(parsesBefore);

        // Many small parts cannot add up past the boundary either: the guard sees the whole request.
        String[] parts = new String[40];
        for (int i = 0; i < 40; i += 2) { parts[i] = "file:f" + i; parts[i + 1] = "x".repeat((int) (UploadLimits.MEBIBYTE / 2)); }
        HttpResponse<String> many = send(DOCS, admin, body("doc.pdf", 100, null, parts), true);
        assertThat(many.statusCode()).isEqualTo(413);
        assertThat(CountingResolver.PARSES.get()).isEqualTo(parsesBefore);
        assertThat(filesIn(tomcatMultipartDir)).isZero();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "/hospital/patients/1/documents",
        "/clinic/patients/1/documents",
        "/platform/medicines/import-csv",
        "/platform/inventory-master/import-csv",
    })
    void everyOtherMultipartEndpointIsBoundedAtSixMebibytesBeforeParsing(String path) throws Exception {
        int before = CountingResolver.PARSES.get();
        HttpResponse<String> r = send(path, admin, body("f.csv", UploadLimits.DEFAULT_REQUEST_BYTES + 1, null), true);
        assertThat(r.statusCode()).isEqualTo(413);
        assertThat(CountingResolver.PARSES.get()).isEqualTo(before);
    }

    @Test
    void theFileLimitOfAnotherEndpointIsStillItsOwnCheck() throws Exception {
        // 5 MiB + 1 file inside a 6 MiB request: the guard admits it, the endpoint's own MAX_BYTES refuses it (400 today).
        com.hms.entity.User su = users.findByEmail("admin123@gmail.com").orElseThrow();
        String sa = jwt.generateToken(su.getId(), su.getEmail(), "SUPER_ADMIN", null, List.of(), null, null, List.of(), su.getTokenVersion());
        HttpResponse<String> r = send(MEDICINES, sa, body("m.csv", UploadLimits.DEFAULT_FILE_BYTES + 1, null), true);
        // The container's unchanged 5 MiB per-file ceiling refuses it while parsing: 413, never 200, never 5xx.
        assertThat(r.statusCode()).isEqualTo(413);
        HttpResponse<String> ok = send(MEDICINES, sa, body("m.csv", UploadLimits.DEFAULT_FILE_BYTES, null), true);
        assertThat(ok.statusCode()).isNotEqualTo(413).isNotEqualTo(411).isLessThan(500); // parsed; the endpoint's own answer
    }

    // ── routing ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "/hospital/patients/importXYZ",
        "/hospital/patients/import/foo",
        "/hospital/patients/import",
        "/hospital/patients/import/commit/",
        "/hospital/patients/import/commit/../commit",
        "/hospital/patients//import/commit",
        "/hospital/patients/import/%63ommit",
        "/hospital/patients/import/commit;x=1",
        "/hospital/patients/import/status-id",
    })
    void onlyTheExactImportRoutesGetTheLargeAllowance(String path) throws Exception {
        int before = CountingResolver.PARSES.get();
        HttpResponse<String> r = send(path, admin, body("a.csv", 8 * UploadLimits.MEBIBYTE, MAPPING), true);
        assertThat(r.statusCode()).as(path).isEqualTo(413);
        assertThat(CountingResolver.PARSES.get()).isEqualTo(before);
        verify(engine, never()).commit(any(), any(), any());
    }

    @Test
    void theClinicAliasGetsTheSameImportLimitAndGetStatusGetsNoUploadAllowance() throws Exception {
        assertThat(send(CLINIC_IMPORT, admin, body("a.csv", 8 * UploadLimits.MEBIBYTE, MAPPING), true).statusCode()).isEqualTo(200);
        verify(engine).preview(any(), any(), any(), any(), any());
        // GET is not an upload route: an 8 MiB multipart GET is refused by the guard like any other route.
        HttpRequest get = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/hospital/patients/import/some-id"))
                .header("Authorization", "Bearer " + admin)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .method("GET", HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> new RepeatingInputStream(8 * UploadLimits.MEBIBYTE)), 8 * UploadLimits.MEBIBYTE))
                .build();
        assertThat(HttpClient.newHttpClient().send(get, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
    }

    // ── chunked / missing length ────────────────────────────────────────────

    @Test
    void aMultipartUploadWithoutContentLengthIs411BeforeAnyParsing() throws Exception {
        int before = CountingResolver.PARSES.get();
        HttpResponse<String> chunkedImport = send(IMPORT, admin, body("a.csv", 100, MAPPING), false);
        assertThat(chunkedImport.statusCode()).isEqualTo(411);
        assertThat(chunkedImport.body()).contains("LENGTH_REQUIRED").doesNotContain("Exception");
        assertThat(CountingResolver.PARSES.get()).isEqualTo(before);
        // A chunked NON-import upload keeps today's contract: it is parsed under the default
        // servlet's 5/6 MiB ceiling and answered by its endpoint, never 411.
        HttpResponse<String> chunkedDoc = send(DOCS, admin, body("doc.pdf", 100, null, "documentType", "REPORT"), false);
        assertThat(chunkedDoc.statusCode()).isNotEqualTo(411).isNotEqualTo(413);
        assertThat(CountingResolver.PARSES.get()).isEqualTo(before + 1);
        // and a chunked oversize non-import upload is still stopped by the container, as a 413 now rather than a 500
        HttpResponse<String> chunkedBig = send(DOCS, admin, body("doc.pdf", UploadLimits.DEFAULT_REQUEST_BYTES + 1, null, "documentType", "REPORT"), false);
        assertThat(chunkedBig.statusCode()).isEqualTo(413);
        assertThat(chunkedBig.body()).contains("PAYLOAD_TOO_LARGE").doesNotContain("Tomcat").doesNotContain("Exception");
        verify(engine, never()).commit(any(), any(), any());
        assertThat(filesIn(spoolDir)).isZero();
    }

    @Test
    void nonMultipartRequestsAreUntouchedByTheGuard() throws Exception {
        HttpRequest json = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/hospital/patients"))
                .header("Authorization", "Bearer " + admin)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        int status = HttpClient.newHttpClient().send(json, HttpResponse.BodyHandlers.ofString()).statusCode();
        assertThat(status).isNotEqualTo(411).isNotEqualTo(413);
    }
}
