package com.hms.security;

import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.PatientDocument;
import com.hms.entity.User;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientDocumentRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents a patient brought in: who may file one, who may read it, and the fact that knowing an
 * identifier is never enough.
 *
 * <p>The point of the whole design is that a blood report has no URL. These go through the real
 * endpoints, so a document uploaded here is only retrievable by a caller the server agrees should
 * see it -- and a caller from another facility gets the same answer as one asking for a document
 * that does not exist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PatientDocumentApiTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING");
    private static long uniq() { return System.nanoTime() + SEQ.incrementAndGet(); }

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitals;
    @Autowired UserRepository users;
    @Autowired PatientRepository patients;
    @Autowired PatientDocumentRepository documents;

    private Hospital hospital;
    private Patient patient;
    private String adminToken, doctorToken, receptionToken, nurseToken, pharmacistToken;

    private Hospital tenant(String label) {
        Hospital h = new Hospital();
        h.setName("Doc " + label);
        h.setCustomId("DOC-" + uniq());
        h.setIsActive(true);
        h.setSubscriptionStatus("ACTIVE");
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        h.setType(com.hms.entity.HospitalType.HOSPITAL);
        return hospitals.save(h);
    }

    private String tokenFor(Hospital h, String role) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "." + uniq() + "@doc.test");
        u.setPassword("{noop}fixture");
        u.setName("User " + role);
        u.setRole(role);
        u.setHospitalId(h.getId());
        u.setIsActive(true);
        u.setTokenVersion(0);
        u = users.save(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), role, h.getId(),
                MODULES, null, "HOSPITAL", null, 0);
    }

    private Patient patientIn(Hospital h, String name) {
        Patient p = new Patient();
        p.setHospitalId(h.getId());
        p.setName(name);
        p.setPublicId("ppub-" + uniq());
        p.setGender("MALE");
        p.setPhone("9" + String.format("%09d", Math.floorMod(uniq(), 1000000000L)));
        p.setIsActive(true);
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        return patients.save(p);
    }

    @BeforeEach
    void setUp() {
        hospital = tenant("Alpha");
        patient = patientIn(hospital, "Asha Rao");
        adminToken = tokenFor(hospital, "HOSPITAL_ADMIN");
        doctorToken = tokenFor(hospital, "DOCTOR");
        receptionToken = tokenFor(hospital, "RECEPTIONIST");
        nurseToken = tokenFor(hospital, "NURSE");
        pharmacistToken = tokenFor(hospital, "PHARMACIST");
    }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    /** A real multipart upload, as a browser would send one. */
    private ResponseEntity<String> upload(Long patientId, String token, String fileName,
                                          String contentType, byte[] content, String type,
                                          MultiValueMap<String, String> extra) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource file = new ByteArrayResource(content) {
            @Override public String getFilename() { return fileName; }
        };
        body.add("file", file);
        body.add("documentType", type);
        body.add("title", "Outside blood report");
        if (extra != null) extra.forEach((k, v) -> v.forEach(one -> body.add(k, one)));

        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // Spring needs the part's own content type to reach the server.
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        MultiValueMap<String, Object> typed = new LinkedMultiValueMap<>();
        typed.add("file", new HttpEntity<>(file, partHeaders));
        body.remove("file");
        body.forEach((k, v) -> v.forEach(one -> typed.add(k, one)));

        return rest.exchange("/hospital/patients/" + patientId + "/documents",
                HttpMethod.POST, new HttpEntity<>(typed, headers), String.class);
    }

    private ResponseEntity<String> uploadPdf(Long patientId, String token) {
        return upload(patientId, token, "report.pdf", "application/pdf",
                "%PDF-1.4 fake".getBytes(), PatientDocument.PATHOLOGY_REPORT, null);
    }

    private ResponseEntity<String> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(token)), String.class);
    }

    /** Scoped to this patient: the table is shared with every other test in the class. */
    private long documentsFor(Patient p) {
        return documents.findByHospitalIdAndPatientIdAndIsActiveTrueOrderByReportDateDescIdDesc(
                hospital.getId(), p.getId()).size();
    }

    private String publicIdOf(Patient p) {
        return documents.findByHospitalIdAndPatientIdAndIsActiveTrueOrderByReportDateDescIdDesc(
                hospital.getId(), p.getId()).get(0).getPublicId();
    }

    // -- uploading ---------------------------------------------------------------

    @Test
    void aPdfCanBeFiledAgainstAPatient() {
        ResponseEntity<String> res = uploadPdf(patient.getId(), receptionToken);
        assertThat(res.getStatusCode().value()).as("%s", res.getBody()).isEqualTo(200);

        var saved = documents.findByHospitalIdAndPatientIdAndIsActiveTrueOrderByReportDateDescIdDesc(
                hospital.getId(), patient.getId());
        assertThat(saved).hasSize(1);
        PatientDocument doc = saved.get(0);
        assertThat(doc.getMimeType()).isEqualTo("application/pdf");
        assertThat(doc.getOriginalFileName()).isEqualTo("report.pdf");
        assertThat(doc.getFileSizeBytes()).isPositive();
        assertThat(doc.getStorageKey()).isNotBlank();
        assertThat(doc.getStorageKey())
                .as("a storage handle, never a URL").doesNotContain("http").doesNotContain("://");
    }

    @Test
    void imagesFromAPhoneAreOrdinaryUploads() {
        for (String[] kind : new String[][]{
                {"scan.jpg", "image/jpeg"}, {"scan.png", "image/png"}, {"scan.webp", "image/webp"}}) {
            Patient p = patientIn(hospital, "P " + uniq());
            ResponseEntity<String> res = upload(p.getId(), receptionToken, kind[0], kind[1],
                    "image-bytes".getBytes(), PatientDocument.OTHER, null);
            assertThat(res.getStatusCode().value()).as("%s -> %s", kind[1], res.getBody()).isEqualTo(200);
        }
    }

    @Test
    void anExecutableIsRefused() {
        ResponseEntity<String> res = upload(patient.getId(), receptionToken, "evil.exe",
                "application/octet-stream", "MZ".getBytes(), PatientDocument.OTHER, null);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(documentsFor(patient)).isZero();
    }

    @Test
    void aRenamedFileWhoseTypeDisagreesWithItsNameIsRefused() {
        ResponseEntity<String> res = upload(patient.getId(), receptionToken, "report.exe",
                "application/pdf", "%PDF".getBytes(), PatientDocument.OTHER, null);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void anEmptyFileIsRefused() {
        ResponseEntity<String> res = upload(patient.getId(), receptionToken, "empty.pdf",
                "application/pdf", new byte[0], PatientDocument.OTHER, null);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void anUnknownDocumentTypeIsRefused() {
        ResponseEntity<String> res = upload(patient.getId(), receptionToken, "r.pdf",
                "application/pdf", "%PDF".getBytes(), "MADE_UP_TYPE", null);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    // -- roles -------------------------------------------------------------------

    @Test
    void theRolesThatMeetPatientsCanFileDocuments() {
        for (String token : new String[]{receptionToken, doctorToken, adminToken}) {
            Patient p = patientIn(hospital, "P " + uniq());
            assertThat(uploadPdf(p.getId(), token).getStatusCode().value()).isEqualTo(200);
        }
    }

    @Test
    void aNurseMayReadButNotFile() {
        assertThat(uploadPdf(patient.getId(), nurseToken).getStatusCode().value()).isEqualTo(403);

        uploadPdf(patient.getId(), receptionToken);
        assertThat(get("/hospital/patients/" + patient.getId() + "/documents", nurseToken)
                .getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void aPharmacistHasNoAccessAtAll() {
        uploadPdf(patient.getId(), receptionToken);
        assertThat(uploadPdf(patient.getId(), pharmacistToken).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/hospital/patients/" + patient.getId() + "/documents", pharmacistToken)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(get("/hospital/patient-documents/" + publicIdOf(patient) + "/content", pharmacistToken)
                .getStatusCode().value()).isEqualTo(403);
    }

    // -- reading back ------------------------------------------------------------

    @Test
    void theListDescribesTheDocumentWithoutRevealingWhereItLives() {
        uploadPdf(patient.getId(), receptionToken);
        ResponseEntity<String> res = get("/hospital/patients/" + patient.getId() + "/documents", doctorToken);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).contains("Outside blood report").contains("report.pdf");
        assertThat(res.getBody())
                .as("the storage handle is not the client's business")
                .doesNotContain("storageKey").doesNotContain("/var/").doesNotContain("tmp");
    }

    @Test
    void theContentComesBackThroughTheApplication() {
        uploadPdf(patient.getId(), receptionToken);
        ResponseEntity<String> res =
                get("/hospital/patient-documents/" + publicIdOf(patient) + "/content", doctorToken);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).contains("%PDF-1.4 fake");
        assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("report.pdf");
        assertThat(res.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    // -- tenancy: the whole point ------------------------------------------------

    @Test
    void anotherFacilityCannotFileAgainstThisPatient() {
        Hospital other = tenant("Bravo");
        String theirReception = tokenFor(other, "RECEPTIONIST");

        assertThat(uploadPdf(patient.getId(), theirReception).getStatusCode().value()).isEqualTo(404);
        assertThat(documentsFor(patient)).as("nothing was filed").isZero();
    }

    @Test
    void knowingTheDocumentIdIsNotEnoughFromAnotherFacility() {
        uploadPdf(patient.getId(), receptionToken);
        String id = publicIdOf(patient);

        Hospital other = tenant("Bravo");
        String theirDoctor = tokenFor(other, "DOCTOR");

        assertThat(get("/hospital/patient-documents/" + id + "/content", theirDoctor)
                .getStatusCode().value())
                .as("indistinguishable from a document that does not exist").isEqualTo(404);
        assertThat(get("/hospital/patients/" + patient.getId() + "/documents", theirDoctor)
                .getStatusCode().value()).isEqualTo(404);
    }

    /** The client never names a file, so there is nothing for it to tamper with. */
    @Test
    void aClientSuppliedStorageKeyIsIgnored() {
        MultiValueMap<String, String> extra = new LinkedMultiValueMap<>();
        extra.add("storageKey", "../../etc/passwd");
        ResponseEntity<String> res = upload(patient.getId(), receptionToken, "r.pdf",
                "application/pdf", "%PDF".getBytes(), PatientDocument.OTHER, extra);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(documents.findByHospitalIdAndPatientIdAndIsActiveTrueOrderByReportDateDescIdDesc(
                hospital.getId(), patient.getId()).get(0).getStorageKey())
                .as("the server chose the key, not the caller")
                .doesNotContain("..").doesNotContain("etc");
    }

    // -- archiving ---------------------------------------------------------------

    @Test
    void archivingHidesTheDocumentButKeepsIt() {
        uploadPdf(patient.getId(), receptionToken);
        String id = publicIdOf(patient);

        ResponseEntity<String> archived = rest.exchange(
                "/hospital/patient-documents/" + id + "/archive", HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"Filed against the wrong patient\"}", jsonAuth(doctorToken)),
                String.class);
        assertThat(archived.getStatusCode().value()).as("%s", archived.getBody()).isEqualTo(200);

        assertThat(get("/hospital/patients/" + patient.getId() + "/documents", doctorToken).getBody())
                .doesNotContain("Outside blood report");

        PatientDocument row = documents.findByPublicIdAndHospitalId(id, hospital.getId()).orElseThrow();
        assertThat(row.getIsActive()).isFalse();
        assertThat(row.getArchiveReason()).isEqualTo("Filed against the wrong patient");
        assertThat(row.getStorageKey()).as("the file itself is kept").isNotBlank();
    }

    @Test
    void archivingNeedsAReason() {
        uploadPdf(patient.getId(), receptionToken);
        ResponseEntity<String> res = rest.exchange(
                "/hospital/patient-documents/" + publicIdOf(patient) + "/archive", HttpMethod.POST,
                new HttpEntity<>("{}", jsonAuth(doctorToken)), String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void receptionCannotArchive() {
        uploadPdf(patient.getId(), receptionToken);
        ResponseEntity<String> res = rest.exchange(
                "/hospital/patient-documents/" + publicIdOf(patient) + "/archive", HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"x\"}", jsonAuth(receptionToken)), String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }

    private HttpHeaders jsonAuth(String token) {
        HttpHeaders h = auth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
