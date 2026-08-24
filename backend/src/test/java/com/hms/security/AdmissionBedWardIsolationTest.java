package com.hms.security;

import com.hms.entity.*;
import com.hms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An IPD admission takes three client-supplied ids — opdId, wardId, bedId. Phase 1 (G-01b)
 * scoped the OPD to the caller's hospital, but the bed and the ward were still loaded by raw
 * id with no tenant filter. The later BedStatusService call is tenant-scoped and refused the
 * foreign bed, so no foreign row was ever mutated — but by then admitFromOpd had already
 * written the admission row, and it has no transaction to undo it.
 *
 * Runtime evidence before this fix: status=404, admissionsDelta=1. The attacker got a 404
 * and a permanently committed IPD admission pointing at another hospital's ward and bed.
 *
 * These tests drive the real endpoint against a real database — the defect is invisible to a
 * mocked repository, which is exactly how it survived the earlier passes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdmissionBedWardIsolationTest {

    @LocalServerPort int port;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired WardRepository wardRepository;
    @Autowired BedRepository bedRepository;
    @Autowired OpdRepository opdRepository;
    @Autowired IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired BillingRepository billingRepository;
    @Autowired UserRepository userRepository;

    private static final List<String> MODULES = List.of("OPD", "IPD", "PHARMACY", "BILLING");

    private String tokenB;
    private Long aOpdId, aWardId, aBedId, aPatientId;
    private Long bOpdId, bWardId, bBedId;

    private String uniq() { return Long.toString(System.nanoTime()); }

    private long seedHospital(String slug) {
        Hospital h = new Hospital();
        h.setName("H-" + slug); h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE"); h.setIsActive(true);
        h.setModules(MODULES); h.setIsSingleDoctor(false);
        return hospitalRepository.save(h).getId();
    }

    /** A full admissible journey for one tenant: doctor, patient, ward, available bed, OPD. */
    private long[] seedJourney(long hid, String slug, String email) {
        Doctor d = new Doctor();
        d.setName("Dr " + slug); d.setHospitalId(hid); d.setIsActive(true);
        d.setEmail(email); d.setPublicId("dpub-" + uniq());
        d.setPhone("9800000001"); d.setSpecialization("Gen");
        doctorRepository.save(d);

        Patient p = new Patient();
        p.setName("Pat " + slug); p.setHospitalId(hid); p.setPublicId("ppub-" + uniq());
        p.setGender("MALE"); p.setPhone("9900000001"); p.setIsActive(true);
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        long pid = patientRepository.save(p).getId();

        Ward w = new Ward();
        w.setWardName("W-" + slug); w.setHospitalId(hid);
        w.setBedPrice(new BigDecimal("1500")); w.setTotalBeds(2);
        long wid = wardRepository.save(w).getWardId();

        Bed b = new Bed();
        b.setHospitalId(hid); b.setWardId(wid); b.setBedCode("BED-" + uniq()); b.setStatus("available");
        long bid = bedRepository.save(b).getBedId();

        Opd o = new Opd();
        o.setCaseId("OPD-" + uniq()); o.setIpdAdmitRecommended(true);
        o.setPatient(p); o.setDoctor(d);
        long oid = opdRepository.save(o).getId();

        return new long[]{oid, wid, bid, pid};
    }

    @BeforeEach
    void setUp() {
        long hidA = seedHospital("alpha");
        long hidB = seedHospital("bravo");

        long[] a = seedJourney(hidA, "alpha", "admin@alpha.com");
        aOpdId = a[0]; aWardId = a[1]; aBedId = a[2]; aPatientId = a[3];

        long[] b = seedJourney(hidB, "bravo", "admin@bravo.com");
        bOpdId = b[0]; bWardId = b[1]; bBedId = b[2];

        // A real, active User row. Since 233b66e, JwtAuthenticationFilter revalidates every
        // request against userRepository.findActiveTokenVersion(userId): a token minted for an
        // id that has no user row is denied with 401 before it reaches the controller, so the
        // refusal this class exists to assert was never reached.
        User admin = new User();
        admin.setEmail("admin-bravo-" + uniq() + "@x.test");
        admin.setPassword("{noop}x");
        admin.setName("Admin bravo");
        admin.setRole("HOSPITAL_ADMIN");
        admin.setHospitalId(hidB);
        admin.setIsActive(true);
        admin = userRepository.save(admin);

        tokenB = jwtUtil.generateToken(admin.getId(), admin.getEmail(), admin.getRole(), hidB,
                MODULES, null, "HOSPITAL", null, admin.getTokenVersion());
    }

    /**
     * Transport only -- the assertions, endpoint and tenant semantics below are unchanged.
     *
     * <p>TestRestTemplate cannot be used here. Its SimpleClientHttpRequestFactory streams the
     * request body, and 233b66e made the unauthenticated path answer 401 with a body
     * (SecurityConfig -> SecurityErrorResponder). HttpURLConnection reacts to the 401 by trying
     * to replay the request, which a streamed POST cannot do, and throws "cannot retry due to
     * server authentication, in streaming mode" before any status is observed -- so every case
     * in this class errored instead of asserting. Same fix, same reason, as
     * CrossTenantIsolationTest.
     *
     * <p>java.net.http.HttpClient does not retry on 401, so the status is returned as sent.
     */
    private ResponseEntity<String> admit(Long opd, Long ward, Long bed) {
        String body = "{\"opdId\":" + opd + ",\"wardId\":" + ward + ",\"bedId\":" + bed
                + ",\"admissionType\":\"ELECTIVE\",\"primaryDiagnosis\":\"obs\"}";
        try {
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/hospital/ipd/admit"))
                    .header("Authorization", "Bearer " + tokenB)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();

            java.net.http.HttpResponse<String> res = java.net.http.HttpClient.newHttpClient()
                    .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

            return ResponseEntity.status(res.statusCode()).body(res.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("HTTP call failed: POST /hospital/ipd/admit", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: POST /hospital/ipd/admit", e);
        }
    }

    /** Snapshot of everything that must be untouched by a refused admission. */
    private record State(long admissions, long bills, String aBed, String bBed,
                         Opd.Status aOpd, Opd.Status bOpd, int aWardBeds) {}

    private State snapshot() {
        return new State(
                ipdAdmissionRepository.count(),
                billingRepository.count(),
                bedRepository.findById(aBedId).orElseThrow().getStatus(),
                bedRepository.findById(bBedId).orElseThrow().getStatus(),
                opdRepository.findById(aOpdId).orElseThrow().getStatus(),
                opdRepository.findById(bOpdId).orElseThrow().getStatus(),
                wardRepository.findById(aWardId).orElseThrow().getTotalBeds());
    }

    private void assertRefusedAndInert(String label, Long opd, Long ward, Long bed) {
        State before = snapshot();

        ResponseEntity<String> res = admit(opd, ward, bed);
        State after = snapshot();

        System.out.println("### " + label + " status=" + res.getStatusCode().value()
                + " admissionsDelta=" + (after.admissions() - before.admissions())
                + " body=" + res.getBody());

        assertThat(res.getStatusCode().value()).as("%s: status", label).isEqualTo(404);
        assertThat(res.getBody()).as("%s: canonical code", label).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
        assertThat(res.getBody()).as("%s: no tenant disclosure", label)
                .doesNotContainIgnoringCase("another hospital")
                .doesNotContainIgnoringCase("belongs to")
                .doesNotContainIgnoringCase("foreign")
                .doesNotContainIgnoringCase("tenant");

        assertThat(after.admissions()).as("%s: NO admission row written", label).isEqualTo(before.admissions());
        assertThat(after.bills()).as("%s: no billing", label).isEqualTo(before.bills());
        assertThat(after.aBed()).as("%s: tenant A bed unchanged", label).isEqualTo(before.aBed());
        assertThat(after.bBed()).as("%s: tenant B bed unchanged", label).isEqualTo(before.bBed());
        assertThat(after.aOpd()).as("%s: tenant A OPD unchanged", label).isEqualTo(before.aOpd());
        assertThat(after.bOpd()).as("%s: tenant B OPD unchanged", label).isEqualTo(before.bOpd());
        assertThat(after.aWardBeds()).as("%s: tenant A ward unchanged", label).isEqualTo(before.aWardBeds());
    }

    @Test
    void test1_foreignOpd_withOwnWard_andForeignBed() {
        assertRefusedAndInert("T1 foreign OPD + own ward + foreign bed", aOpdId, bWardId, aBedId);
    }

    @Test
    void test2_ownOpd_foreignWard_ownBed() {
        assertRefusedAndInert("T2 own OPD + foreign ward + own bed", bOpdId, aWardId, bBedId);
    }

    @Test
    void test3_ownOpd_ownWard_foreignBed() {
        assertRefusedAndInert("T3 own OPD + own ward + foreign bed", bOpdId, bWardId, aBedId);
    }

    @Test
    void test5_ownOpd_foreignWard_foreignBed() {
        assertRefusedAndInert("T5 own OPD + foreign ward + foreign bed", bOpdId, aWardId, aBedId);
    }

    @Test
    void test4_allOwnResources_admissionSucceeds() {
        long admissionsBefore = ipdAdmissionRepository.count();
        long billsBefore = billingRepository.count();

        ResponseEntity<String> res = admit(bOpdId, bWardId, bBedId);

        assertThat(res.getStatusCode().value()).as("positive control").isEqualTo(200);
        assertThat(ipdAdmissionRepository.count()).as("admission created").isEqualTo(admissionsBefore + 1);
        assertThat(billingRepository.count()).as("bed-price bill created").isEqualTo(billsBefore + 1);
        assertThat(bedRepository.findById(bBedId).orElseThrow().getStatus())
                .as("the caller's own bed is occupied").isEqualToIgnoringCase("occupied");
        assertThat(opdRepository.findById(bOpdId).orElseThrow().getStatus())
                .as("OPD moves to IN_IPD").isEqualTo(Opd.Status.IN_IPD);
        IpdAdmission created = ipdAdmissionRepository.findAll().stream()
                .max(java.util.Comparator.comparing(IpdAdmission::getId)).orElseThrow();
        assertThat(created.getWardId()).as("references the caller's own ward").isEqualTo(bWardId);
        assertThat(created.getBedId()).as("references the caller's own bed").isEqualTo(bBedId);
    }

    @Test
    void aForeignBedAnswersExactlyLikeAMissingOne() {
        String foreign = strip(admit(bOpdId, bWardId, aBedId).getBody());
        String missing = strip(admit(bOpdId, bWardId, 99999999L).getBody());

        assertThat(foreign).as("foreign and missing must be indistinguishable").isEqualTo(missing);
    }

    private String strip(String body) {
        return body == null ? null : body.replaceAll("\"requestId\":\"[^\"]*\"", "\"requestId\":\"X\"");
    }
}
