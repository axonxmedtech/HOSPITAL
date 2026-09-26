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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
    @Autowired com.hms.repository.import_.PatientImportLinkRepository links;

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

    /** A token for a real user row: session revalidation denies tokens without a matching tokenVersion. */
    private String token(String role, Long hospitalId) {
        String email = role.toLowerCase() + "@import.test";
        com.hms.entity.User u = users.findByEmail(email).orElseGet(() -> {
            com.hms.entity.User fresh = new com.hms.entity.User();
            fresh.setEmail(email);
            fresh.setPassword("{noop}fixture");
            fresh.setName(role);
            fresh.setRole(role);
            fresh.setIsActive(true);
            fresh.setTokenVersion(0);
            return fresh;
        });
        u.setHospitalId(hospitalId);
        u = users.save(u);
        String type = hospitals.findById(hospitalId).orElseThrow().getType().name();
        return jwt.generateToken(u.getId(), u.getEmail(), role, hospitalId, MODULES, null, type, null, u.getTokenVersion());
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
     * Multipart over java.net.http with an explicit Content-Length. TestRestTemplate (backed by
     * HttpComponents here) streams multipart bodies chunked, which the upload boundary refuses by
     * policy (411); browsers, axios and the JDK client all declare the length, as this does.
     */
    private ResponseEntity<String> upload(String path, String token, byte[] bytes, String filename, String mapping, String sheet) {
        return upload(path, token, bytes, filename, mapping, sheet, null);
    }

    private ResponseEntity<String> upload(String path, String token, byte[] bytes, String filename, String mapping, String sheet, String excluded) {
        try {
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
            if (excluded != null) out.print("--" + boundary + "\r\nContent-Disposition: form-data; name=\"excludedColumns\"\r\n\r\n" + excluded + "\r\n");
            if (sheet != null) out.print("--" + boundary + "\r\nContent-Disposition: form-data; name=\"sheetName\"\r\n\r\n" + sheet + "\r\n");
            out.print("--" + boundary + "--\r\n");
            out.flush();
            java.net.http.HttpRequest.Builder req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(rest.getRootUri() + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
            if (token != null) req.header("Authorization", "Bearer " + token);
            java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient().send(req.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(r.statusCode()).body(r.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int rawStatus(String path, String token, byte[] bytes, String filename, String mapping) {
        return upload(path, token, bytes, filename, mapping, null).getStatusCode().value();
    }

    private ResponseEntity<String> get(String path, String token) {
        try {
            java.net.http.HttpRequest.Builder req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(rest.getRootUri() + path)).GET();
            if (token != null) req.header("Authorization", "Bearer " + token);
            java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient().send(req.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(r.statusCode()).body(r.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    @Test
    void explicitExclusionsMatchPreviewAndCommitOnBothAliases() throws Exception {
        for (String base : List.of(BASE, "/clinic/patients/import")) {
            if (base.startsWith("/clinic")) {
                Hospital clinic = hospitals.findById(hospitalA).orElseThrow();
                clinic.setType(com.hms.entity.HospitalType.CLINIC);
                hospitals.save(clinic);
            }
            for (String excluded : List.of("[\"Extra\"]", "[\"Extra\",\"Other\"]")) {
                String t = tag();
                String csv = HEADER.strip() + ",Extra,Other\n" + csvRow(t, 1).strip() + ",discard-me,keep-unless-excluded\n";
                String tok = admin(hospitalA);
                var preview = upload(base + "/preview", tok, bytes(csv), "a.csv", MAPPING, null, excluded);
                assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
                long before = patientsIn(hospitalA);
                var commit = upload(base + "/commit", tok, bytes(csv), "a.csv", MAPPING, null, excluded);
                assertThat(commit.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(body(commit).at("/data/counts")).isEqualTo(body(preview).at("/data/counts"));
                assertThat(patientsIn(hospitalA)).isEqualTo(before + 1);
                var link = links.findByHospitalIdAndLegacyId(hospitalA, "M" + t + "-1").orElseThrow();
                if (excluded.contains("Other")) assertThat(link.getCustomFieldsJson()).isNull();
                else assertThat(json.readTree(link.getCustomFieldsJson())).isEqualTo(json.readTree("{\"Other\":\"keep-unless-excluded\"}"));
                // Original-file fingerprinting is unchanged even when the exclusions change.
                assertThat(upload(base + "/commit", tok, bytes(csv), "a.csv", MAPPING, null, "[]").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                String publicId = body(commit).at("/data/batchPublicId").asText();
                assertThat(get(base + "/" + publicId, admin(hospitalB)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            }
        }
    }

    @Test
    void exclusionsDoNotCauseUpdatesOrEraseExistingMetadata() throws Exception {
        String t = tag();
        String header = HEADER.strip() + ",Extra\n";
        String row = csvRow(t, 1).strip();
        String tok = admin(hospitalA);
        assertThat(upload(BASE + "/commit", tok, bytes(header + row + ",original\n"), "a.csv", MAPPING, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        var before = links.findByHospitalIdAndLegacyId(hospitalA, "M" + t + "-1").orElseThrow();
        String metadata = before.getCustomFieldsJson();
        String csv = header + row + ",changed-but-excluded\n";
        var preview = upload(BASE + "/preview", tok, bytes(csv), "b.csv", MAPPING, null, "[\"Extra\"]");
        var commit = upload(BASE + "/commit", tok, bytes(csv), "b.csv", MAPPING, null, "[\"Extra\"]");
        assertThat(commit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(commit).at("/data/counts")).isEqualTo(body(preview).at("/data/counts"));
        assertThat(body(commit).at("/data/counts/skipped").asInt()).isEqualTo(1);
        assertThat(body(commit).at("/data/counts/updated").asInt()).isZero();
        assertThat(links.findById(before.getId()).orElseThrow().getCustomFieldsJson()).isEqualTo(metadata);
        // A real mapped-field update still merges without deleting previously stored metadata.
        String updated = header + row.replace("Person 1", "Renamed Person") + ",another-excluded-value\n";
        var update = upload(BASE + "/commit", tok, bytes(updated), "c.csv", MAPPING, null, "[\"Extra\"]");
        assertThat(body(update).at("/data/counts/updated").asInt()).isEqualTo(1);
        assertThat(links.findById(before.getId()).orElseThrow().getCustomFieldsJson()).isEqualTo(metadata);
        String withoutGender = MAPPING.replace(",\"Gender\":\"gender\"", "");
        String excludedField = header + row.replace("Person 1", "Second Rename").replace(",F,", ",M,") + ",excluded\n";
        var fieldUpdate = upload(BASE + "/commit", tok, bytes(excludedField), "d.csv", withoutGender, null, "[\"Extra\",\"Gender\"]");
        assertThat(body(fieldUpdate).at("/data/counts/updated").asInt()).isEqualTo(1);
        assertThat(patients.findById(before.getPatientId()).orElseThrow().getGender()).isEqualTo("FEMALE");
        assertThat(links.findById(before.getId()).orElseThrow().getCustomFieldsJson()).isEqualTo(metadata);
    }

    @Test
    void invalidExclusionsAreRejectedBeforeAnyBatchOrPatientWrite() throws Exception {
        String tok = admin(hospitalA);
        String csv = HEADER.strip() + ",Extra\n" + csvRow(tag(), 1).strip() + ",value\n";
        long batchCount = batches.count();
        for (String excluded : List.of("[\"Name\"]", "[\"Unknown\"]", "[\"Extra\",\" extra \"]", "[\" \"]", "[null]", "[12]", "{}", "")) {
            for (String action : List.of("preview", "commit")) {
                assertThat(upload(BASE + "/" + action, tok, bytes(csv), "a.csv", MAPPING, null, excluded).getStatusCode())
                        .as(action + " " + excluded).isEqualTo(HttpStatus.BAD_REQUEST);
            }
        }
        assertThat(upload(BASE + "/commit", tok, bytes(csv), "a.csv", "{\"Phone\":\"phone\"}", null, "[\"Name\"]").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(batches.count()).isEqualTo(batchCount);
        assertThat(patientsIn(hospitalA)).isZero();
    }

    @Test
    void exclusionsPreserveDuplicatePhoneReviewAndAuthorization() throws Exception {
        String t = tag();
        String first = csvRow(t, 1).strip();
        String csv = HEADER.strip() + ",Extra\n" + first + ",one\n" + first.replace("-1,", "-2,").replace("Person 1", "Other Person") + ",two\n";
        String excluded = "[\"Extra\"]";
        for (String action : List.of("preview", "commit")) {
            assertThat(upload(BASE + "/" + action, token("DOCTOR", hospitalA), bytes(csv), "a.csv", MAPPING, null, excluded).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(upload(BASE + "/" + action, null, bytes(csv), "a.csv", MAPPING, null, excluded).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        var preview = upload(BASE + "/preview", admin(hospitalA), bytes(csv), "a.csv", MAPPING, null, excluded);
        var commit = upload(BASE + "/commit", admin(hospitalA), bytes(csv), "a.csv", MAPPING, null, excluded);
        assertThat(body(commit).at("/data/counts")).isEqualTo(body(preview).at("/data/counts"));
        assertThat(body(commit).at("/data/counts/needsReview").asInt()).isEqualTo(1);
        assertThat(body(commit).at("/data/counts/created").asInt()).isEqualTo(1);
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
