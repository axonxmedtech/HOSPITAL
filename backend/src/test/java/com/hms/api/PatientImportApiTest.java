package com.hms.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Hospital;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportStatus;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.security.JwtUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * The HTTP contract of the patient import endpoints, following PatientApiTest's conventions
 * (full context, real JWTs, the H2 test profile). MySQL-specific behaviour — V21 races, V24
 * concurrency, chunk transactions — is proven in the Phase 4/5 ITs; this suite proves who may
 * call, what the tenant is, what the client sees, and what it never sees.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PatientImportApiTest {

    private static Path spoolDir;

    @DynamicPropertySource
    static void spool(DynamicPropertyRegistry r) throws IOException {
        spoolDir = Files.createTempDirectory("hms-import-api-test-");
        r.add("hms.import.spool-dir", () -> spoolDir.toString());
    }

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwt;
    @Autowired HospitalRepository hospitals;
    @Autowired ImportBatchRepository batches;
    @Autowired PatientRepository patients;
    @Autowired com.hms.repository.UserRepository users;
    @Autowired ObjectMapper json;

    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING", "APPOINTMENTS");
    private static final String BASE = "/hospital/patients/import";
    private static final String MAPPING = "{\"MRN\":\"legacyId\",\"Name\":\"name\",\"Phone\":\"phone\",\"Gender\":\"gender\",\"DOB\":\"dateOfBirth\"}";
    private static final String HEADER = "MRN,Name,Phone,Gender,DOB\n";

    private long hospitalA;
    private long hospitalB;

    @BeforeEach
    void tenants() {
        hospitalA = hospital();
        hospitalB = hospital();
        // HttpURLConnection cannot deliver a 401/403 that the server sends before the multipart
        // body has been streamed; buffer the body so the status comes back like any other.
        org.springframework.http.client.SimpleClientHttpRequestFactory f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setOutputStreaming(false);
        rest.getRestTemplate().setRequestFactory(f);
    }

    private long patientsIn(long hospitalId) {
        return patients.findAll().stream().filter(p -> hospitalId == p.getHospitalId()).count();
    }

    private long hospital() {
        Hospital h = new Hospital();
        h.setName("Import API " + System.nanoTime());
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        return hospitals.save(h).getId();
    }

    private String token(String role, Long hospitalId) {
        return jwt.generateToken(1L, role.toLowerCase() + "@import.test", role, hospitalId, MODULES, null, "HOSPITAL", null);
    }

    private String admin(long hospitalId) {
        return token("HOSPITAL_ADMIN", hospitalId);
    }

    private static String tag() {
        return String.valueOf(System.nanoTime() % 100_000_000L);
    }

    private static String csvRow(String t, int i) {
        return "M" + t + "-" + i + ",Person " + i + ",98" + String.format("%08d", (Long.parseLong(t) + i) % 100_000_000L) + ",F,1990-01-01\n";
    }

    /**
     * For requests the server refuses BEFORE reading the body (401/403), the JDK's
     * HttpURLConnection cannot report the status; java.net.http can. Status only.
     */
    private int rawStatus(String path, String token, byte[] bytes, String filename, String mapping) throws Exception {
        String boundary = "----hms" + System.nanoTime();
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        java.io.PrintStream out = new java.io.PrintStream(body, true, StandardCharsets.UTF_8);
        if (bytes != null) {
            out.print("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
            out.flush();
            body.write(bytes);
            out.print("\r\n");
        }
        if (mapping != null) out.print("--" + boundary + "\r\nContent-Disposition: form-data; name=\"mapping\"\r\n\r\n" + mapping + "\r\n");
        out.print("--" + boundary + "--\r\n");
        out.flush();
        java.net.http.HttpRequest.Builder req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(rest.getRootUri() + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return java.net.http.HttpClient.newHttpClient().send(req.build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private ResponseEntity<String> upload(String path, String token, byte[] bytes, String filename, String mapping, String sheet) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        if (bytes != null) {
            form.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
        }
        if (mapping != null) form.add("mapping", mapping);
        if (sheet != null) form.add("sheetName", sheet);
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(form, h), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private JsonNode body(ResponseEntity<String> r) throws IOException {
        return json.readTree(r.getBody());
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private long spoolFiles() throws IOException {
        try (Stream<Path> s = Files.list(spoolDir)) {
            return s.count();
        }
    }

    // ── authentication / authorization ──────────────────────────────────────

    @Test
    void unauthenticatedCallsAre401() throws Exception {
        assertThat(rawStatus(BASE + "/preview", null, bytes(HEADER), "a.csv", MAPPING)).isEqualTo(401);
        assertThat(rawStatus(BASE + "/commit", null, bytes(HEADER), "a.csv", MAPPING)).isEqualTo(401);
        assertThat(get(BASE + "/anything", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onlyHospitalAdminMayImport() throws Exception {
        String t = tag();
        for (String role : List.of("RECEPTIONIST", "DOCTOR", "NURSE", "PHARMACIST", "NURSE_INCHARGE", "OT_INCHARGE")) {
            String tok = token(role, hospitalA);
            assertThat(rawStatus(BASE + "/preview", tok, bytes(HEADER + csvRow(t, 1)), "a.csv", MAPPING)).as(role + " preview").isEqualTo(403);
            assertThat(rawStatus(BASE + "/commit", tok, bytes(HEADER + csvRow(t, 1)), "a.csv", MAPPING)).as(role + " commit").isEqualTo(403);
            assertThat(get(BASE + "/some-id", tok).getStatusCode()).as(role + " status").isEqualTo(HttpStatus.FORBIDDEN);
        }
        // SUPER_ADMIN has no tenant and is not a hospital role: refused at the /hospital/** boundary.
        // The seeded platform super admin (session revalidation requires a real user row).
        com.hms.entity.User su = users.findByEmail("admin123@gmail.com").orElseThrow();
        String sa = jwt.generateToken(su.getId(), su.getEmail(), "SUPER_ADMIN", null, List.of(), null, null, List.of(), su.getTokenVersion());
        assertThat(rawStatus(BASE + "/preview", sa, bytes(HEADER + csvRow(t, 1)), "a.csv", MAPPING)).isEqualTo(403);
        assertThat(get(BASE + "/some-id", sa).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patientsIn(hospitalA)).isZero();
    }

    @Test
    void hospitalAdminCanPreviewCommitAndReadItsOwnBatch() throws Exception {
        String t = tag();
        String csv = HEADER + csvRow(t, 1) + csvRow(t, 2) + "M" + t + "-3,Blank Phone,,F,1990-01-01\n";

        ResponseEntity<String> preview = upload(BASE + "/preview", admin(hospitalA), bytes(csv), "legacy.csv", MAPPING, null);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode p = body(preview).get("data");
        assertThat(p.get("counts").get("created").asInt()).isEqualTo(2);
        assertThat(p.get("counts").get("needsReview").asInt()).isEqualTo(1);
        assertThat(p.get("samples")).hasSize(1);
        assertThat(p.get("samples").get(0).get("reasonCode").asText()).isEqualTo("PHONE_MISSING");
        assertThat(p.get("previousImport").isNull()).isTrue();
        assertThat(patientsIn(hospitalA)).isZero(); // preview wrote nothing

        ResponseEntity<String> commit = upload(BASE + "/commit", admin(hospitalA), bytes(csv), "legacy.csv", MAPPING, null);
        assertThat(commit.getStatusCode()).isEqualTo(HttpStatus.OK); // synchronous: the batch is finished
        JsonNode c = body(commit).get("data");
        assertThat(c.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(c.get("counts").get("created").asInt()).isEqualTo(2);
        String publicId = c.get("batchPublicId").asText();
        assertThat(publicId).hasSize(36);
        assertThat(patientsIn(hospitalA)).isEqualTo(2);

        ResponseEntity<String> status = get(BASE + "/" + publicId, admin(hospitalA));
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode s = body(status).get("data");
        assertThat(s.get("publicId").asText()).isEqualTo(publicId);
        assertThat(s.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(s.get("sourceFilename").asText()).isEqualTo("legacy.csv");
        assertThat(s.get("createdBy").asText()).isEqualTo("hospital_admin@import.test"); // the authenticated actor
        assertThat(s.get("counts").get("total").asInt()).isEqualTo(3);
        // and nothing internal
        String raw = status.getBody();
        assertThat(raw).doesNotContain("\"id\"").doesNotContain("hospitalId").doesNotContain("fileSha256").doesNotContain("mappingJson")
                .doesNotContain("activeMarker").doesNotContain("rawRowJson");
    }

    // ── tenancy ─────────────────────────────────────────────────────────────

    @Test
    void anotherHospitalsBatchIsNotFoundAndNothingInTheRequestCanChooseTheTenant() throws Exception {
        String t = tag();
        // A file whose columns try to name a hospital and an actor.
        String csv = "MRN,Name,Phone,Gender,DOB,hospital_id,created_by\n" + "M" + t + "-1,Person 1,98" + String.format("%08d", Long.parseLong(t) % 100_000_000L) + ",F,1990-01-01," + hospitalB + ",evil@x.test\n";
        ResponseEntity<String> commit = upload(BASE + "/commit?hospitalId=" + hospitalB, admin(hospitalA), bytes(csv), "legacy.csv", MAPPING, null);
        assertThat(commit.getStatusCode()).isEqualTo(HttpStatus.OK);
        String publicId = body(commit).get("data").get("batchPublicId").asText();

        ImportBatch b = batches.findByPublicIdAndHospitalId(publicId, hospitalA).orElseThrow();
        assertThat(b.getHospitalId()).isEqualTo(hospitalA);
        assertThat(b.getCreatedBy()).isEqualTo("hospital_admin@import.test");
        assertThat(patientsIn(hospitalB)).isZero();
        assertThat(patientsIn(hospitalA)).isEqualTo(1);

        assertThat(get(BASE + "/" + publicId, admin(hospitalB)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(BASE + "/" + publicId, admin(hospitalA)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aMappingThatTriesToWriteTenantOrIdentityFieldsIsRefused() throws Exception {
        String t = tag();
        for (String bad : List.of(
                "{\"Name\":\"name\",\"hospital_id\":\"hospitalId\"}",
                "{\"Name\":\"name\",\"H\":\"hospital_id\"}",
                "{\"Name\":\"name\",\"A\":\"duplicate_phone_ack_for\"}",
                "{\"Name\":\"name\",\"A\":\"duplicatePhoneAckBy\"}",
                "{\"Name\":\"name\",\"I\":\"id\"}",
                "{\"Name\":\"name\",\"P\":\"publicId\"}",
                "{\"Name\":\"name\",\"C\":\"customId\"}",
                "{\"Name\":\"name\",\"S\":\"status\"}",
                "{\"Name\":\"name\",\"X\":\"nickname\"}",
                "{\"Name\":\"name\",\"Full Name\":\"name\"}",
                "{\"Name\":\"name\",\"Phone\":\"phone\",\" phone \":\"email\"}",
                "{}", "", "[1,2]", "{\"Name\":{\"nested\":\"name\"}}", "{\"Phone\":\"phone\"}")) {
            ResponseEntity<String> r = upload(BASE + "/preview", admin(hospitalA), bytes(HEADER + csvRow(t, 1)), "a.csv", bad, null);
            assertThat(r.getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(r.getBody()).as(bad).contains("VALIDATION_ERROR").doesNotContain("Exception");
        }
        StringBuilder over = new StringBuilder("{\"Name\":\"name\"");
        for (int i = 0; i < 100; i++) over.append(",\"C").append(i).append("\":\"name\"");
        over.append("}");
        assertThat(upload(BASE + "/preview", admin(hospitalA), bytes(HEADER), "a.csv", over.toString(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patientsIn(hospitalA)).isZero();
    }

    // ── file validation ─────────────────────────────────────────────────────

    @Test
    void badFilesAreSafe400sWithoutInternals() throws Exception {
        String a = admin(hospitalA);
        assertThat(upload(BASE + "/preview", a, null, null, MAPPING, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); // missing file
        assertThat(upload(BASE + "/preview", a, new byte[0], "a.csv", MAPPING, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); // empty
        assertThat(upload(BASE + "/preview", a, bytes(HEADER), "a.xls", MAPPING, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); // xls
        assertThat(upload(BASE + "/preview", a, bytes(HEADER), "a.txt", MAPPING, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> random = upload(BASE + "/preview", a, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, "a.xlsx", MAPPING, null);
        assertThat(random.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(random.getBody()).contains("NOT_AN_XLSX").doesNotContain("Exception").doesNotContain("hms-import-").doesNotContain(spoolDir.toString());

        ResponseEntity<String> csvAsXlsx = upload(BASE + "/preview", a, bytes(HEADER + csvRow(tag(), 1)), "a.xlsx", MAPPING, null);
        assertThat(csvAsXlsx.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(csvAsXlsx.getBody()).contains("NOT_AN_XLSX").doesNotContain("Person 1");

        byte[] wb = com.hms.service.import_.ImportTestFilesBridge.xlsx(List.of(List.of("MRN", "Name", "Phone", "Gender", "DOB"), List.of("M1", "Person", "9000000001", "F", "1990-01-01")));
        ResponseEntity<String> noSheet = upload(BASE + "/preview", a, wb, "a.xlsx", MAPPING, "Nope");
        assertThat(noSheet.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noSheet.getBody()).contains("SHEET_NOT_FOUND");
        ResponseEntity<String> ok = upload(BASE + "/preview", a, wb, "a.xlsx", MAPPING, null);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(ok).get("data").get("sheetNames").get(0).asText()).isEqualTo("Sheet1");

        // A malformed CSV with a secret-looking cell: the error names the row, never the cell.
        ResponseEntity<String> malformed = upload(BASE + "/preview", a, bytes(HEADER + "M1,\"SECRET-CELL,9000000001,F,1990-01-01\n"), "a.csv", MAPPING, null);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody()).contains("MALFORMED_CSV").doesNotContain("SECRET-CELL").doesNotContain("SQL");
    }

    @Test
    void aTraversalFilenameIsStoredAsASafeBasenameAndCreatesNoPath() throws Exception {
        String t = tag();
        ResponseEntity<String> commit = upload(BASE + "/commit", admin(hospitalA), bytes(HEADER + csvRow(t, 1)), "../../patients.xlsx.csv", MAPPING, null);
        assertThat(commit.getStatusCode()).isEqualTo(HttpStatus.OK);
        String publicId = body(commit).get("data").get("batchPublicId").asText();
        assertThat(batches.findByPublicIdAndHospitalId(publicId, hospitalA).orElseThrow().getSourceFilename()).isEqualTo("patients.xlsx.csv");
        assertThat(Files.exists(spoolDir.resolve("..").resolve("..").resolve("patients.xlsx.csv").normalize())).isFalse();
        assertThat(spoolFiles()).isZero();
    }

    // ── privacy ─────────────────────────────────────────────────────────────

    @Test
    void previewNeverEchoesRawPhonesOrPatientIds() throws Exception {
        String t = tag();
        String phone = "98" + String.format("%08d", Long.parseLong(t) % 100_000_000L);
        // Register the number first so the preview reports a duplicate — the most phone-heavy outcome.
        upload(BASE + "/commit", admin(hospitalA), bytes(HEADER + "M" + t + "-0,Existing," + phone + ",F,1990-01-01\n"), "first.csv", MAPPING, null);
        ResponseEntity<String> preview = upload(BASE + "/preview", admin(hospitalA), bytes(HEADER + "M" + t + "-9,Newcomer," + phone + ",F,1990-01-01\n"), "second.csv", MAPPING, null);

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sample = body(preview).get("data").get("samples").get(0);
        assertThat(sample.get("reasonCode").asText()).isEqualTo("DUPLICATE_PHONE_REQUIRES_REVIEW");
        assertThat(sample.get("phoneMasked").asText()).matches("98\\*{6}\\d{2}");
        assertThat(preview.getBody()).doesNotContain(phone).doesNotContain("relatedPatientIds").doesNotContain("matchedPatientId");
    }

    // ── already imported (409) ──────────────────────────────────────────────

    @Test
    void reimportingACompletedFileIs409WithTheRealBatchId() throws Exception {
        String t = tag();
        byte[] csv = bytes(HEADER + csvRow(t, 1));
        String first = body(upload(BASE + "/commit", admin(hospitalA), csv, "a.csv", MAPPING, null)).get("data").get("batchPublicId").asText();

        ResponseEntity<String> again = upload(BASE + "/commit", admin(hospitalA), csv, "a.csv", MAPPING, null);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode d = body(again).get("details");
        assertThat(d.get("condition").asText()).isEqualTo("ALREADY_IMPORTED");
        assertThat(d.get("detailsAvailable").asText()).isEqualTo("true");
        assertThat(d.get("batchPublicId").asText()).isEqualTo(first);
        assertThat(d.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(d.has("committedAt")).isTrue();
        assertThat(again.getBody()).doesNotContain("unknown");
        // and the preview reports it, unblocked
        JsonNode prev = body(upload(BASE + "/preview", admin(hospitalA), csv, "a.csv", MAPPING, null)).get("data").get("previousImport");
        assertThat(prev.get("batchPublicId").asText()).isEqualTo(first);
        // a different hospital may import the same bytes
        assertThat(upload(BASE + "/commit", admin(hospitalB), csv, "a.csv", MAPPING, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(spoolFiles()).isZero();
    }

    // ── status + stale running ──────────────────────────────────────────────

    @Test
    void statusOfARunningBatchAppliesTheStaleRuleAndOtherTenantsCannotTouchIt() throws Exception {
        ImportBatch fresh = runningBatch(hospitalA, LocalDateTime.now().minusMinutes(29).minusSeconds(59));
        ImportBatch stale = runningBatch(hospitalA, LocalDateTime.now().minusMinutes(30).minusSeconds(5));
        ImportBatch theirs = runningBatch(hospitalB, LocalDateTime.now().minusMinutes(45));

        assertThat(body(get(BASE + "/" + fresh.getPublicId(), admin(hospitalA))).get("data").get("status").asText()).isEqualTo("RUNNING");
        JsonNode s = body(get(BASE + "/" + stale.getPublicId(), admin(hospitalA))).get("data");
        assertThat(s.get("status").asText()).isEqualTo("FAILED");
        assertThat(s.get("failureReason").asText()).isEqualTo("SYSTEM: abandoned import");
        // resolving A's stale batches did not reach B's, and B's cannot be read by A
        assertThat(batches.findById(theirs.getId()).orElseThrow().getStatus()).isEqualTo(ImportStatus.RUNNING);
        assertThat(get(BASE + "/" + theirs.getPublicId(), admin(hospitalA)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(BASE + "/no-such-batch", admin(hospitalA)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ImportBatch runningBatch(long hospitalId, LocalDateTime heartbeat) {
        ImportBatch b = new ImportBatch();
        b.setHospitalId(hospitalId);
        b.setFileSha256(("0".repeat(64) + System.nanoTime()).substring(String.valueOf(System.nanoTime()).length()));
        b.setStatus(ImportStatus.RUNNING);
        b.setHeartbeatAt(heartbeat);
        return batches.save(b);
    }

    // ── temp files ──────────────────────────────────────────────────────────

    @Test
    void theSpoolIsGoneAfterEveryOutcome() throws Exception {
        String t = tag();
        String a = admin(hospitalA);
        byte[] csv = bytes(HEADER + csvRow(t, 1));
        upload(BASE + "/preview", a, csv, "a.csv", MAPPING, null); // success
        upload(BASE + "/preview", a, new byte[] {9, 9, 9}, "a.xlsx", MAPPING, null); // parse failure
        upload(BASE + "/commit", a, csv, "a.csv", MAPPING, null); // success
        upload(BASE + "/commit", a, csv, "a.csv", MAPPING, null); // already imported
        upload(BASE + "/commit", a, bytes(HEADER + "M,\"broken,9000000001,F,1990-01-01\n"), "b.csv", MAPPING, null); // parse failure on commit
        assertThat(spoolFiles()).isZero();
    }
}
