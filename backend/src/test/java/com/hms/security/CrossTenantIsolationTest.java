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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The permanent guard against cross-tenant authorization regressions.
 *
 * This audit found that several endpoints loaded a tenant-owned resource by its raw numeric
 * id and never checked the owning hospital, so hospital B could read hospital A's IPD record
 * and prescription PDFs and could administer medication against A's admission. Sequential ids
 * made the resources trivially enumerable.
 *
 * The test seeds two hospitals directly, mints a real JWT for each (the filter is stateless
 * and trusts the token's hospitalId claim), then fires hospital B's token at hospital A's ids.
 * Every cross-tenant read, write, download and delete must be refused (401/403/404); hospital
 * A must still reach its own resources. If any endpoint regresses to a bare findById, the
 * corresponding assertion here flips to 200 and CI fails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CrossTenantIsolationTest {

    @LocalServerPort int port;
    @Autowired JwtUtil jwtUtil;

    @Autowired HospitalRepository hospitalRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired WardRepository wardRepository;
    @Autowired BedRepository bedRepository;
    @Autowired IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired BillingRepository billingRepository;
    @Autowired MedicalRecordRepository medicalRecordRepository;
    @Autowired OpdRepository opdRepository;
    @Autowired com.hms.repository.pharmacy.PharmacySaleRepository pharmacySaleRepository;

    private static final List<String> MODULES = List.of(
            "OPD", "IPD", "PHARMACY", "BILLING", "NURSING", "OT",
            "APPOINTMENTS", "REPORTS", "HOSPITAL_INVENTORY", "MEDICAL_INVENTORY");

    private String tokenA, tokenB;
    private Long aIpdId, aBillId, aOpdId, aBedId, aDoctorId, aPharmacySaleId;
    private String aPatientPublicId;

    private String uniq() { return Long.toString(System.nanoTime()); }

    /** Seed one hospital and return its id. If withJourney, also create a patient/OPD/IPD/bill. */
    private long seedHospital(String slug, boolean withJourney) {
        Hospital h = new Hospital();
        h.setName("H-" + slug);
        h.setCustomId("HID-" + uniq());
       
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        long hid = hospitalRepository.save(h).getId();

        if (!withJourney) return hid;

        Doctor doc = new Doctor();
        doc.setName("Dr " + slug); doc.setHospitalId(hid); doc.setIsActive(true);
        doc.setEmail("doc-" + uniq() + "@x.com"); doc.setPublicId("dpub-" + uniq());
        doc.setPhone("9800000001"); doc.setSpecialization("Gen");
        aDoctorId = doctorRepository.save(doc).getId();

        Patient p = new Patient();
        aPatientPublicId = "ppub-" + uniq();
        p.setName("Pat " + slug); p.setHospitalId(hid); p.setPublicId(aPatientPublicId);
        p.setGender("MALE"); p.setPhone("9900000001"); p.setIsActive(true);
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        long pid = patientRepository.save(p).getId();

        Ward w = new Ward();
        w.setWardName("W"); w.setHospitalId(hid); w.setBedPrice(new BigDecimal("1000")); w.setTotalBeds(1);
        long wid = wardRepository.save(w).getWardId();

        Bed bed = new Bed();
        bed.setHospitalId(hid); bed.setWardId(wid); bed.setBedCode("B1"); bed.setStatus("occupied");
        aBedId = bedRepository.save(bed).getBedId();

        Opd opd = new Opd();
        opd.setCaseId("OPD-" + uniq()); opd.setIpdAdmitRecommended(false);
        opd.setPatient(p);
        aOpdId = opdRepository.save(opd).getId();

        MedicalRecord mr = new MedicalRecord();
        mr.setPublicId("mrpub-" + uniq()); mr.setHospitalId(hid); mr.setPatientId(pid);
        mr.setDoctorId(aDoctorId); mr.setVisitType("OPD"); mr.setOpdId(aOpdId);
        medicalRecordRepository.save(mr);

        IpdAdmission ipd = new IpdAdmission();
        ipd.setIpdNumber("IPD-" + uniq()); ipd.setPatientId(pid); ipd.setDoctorId(aDoctorId);
        ipd.setHospitalId(hid); ipd.setAdmissionType("ELECTIVE"); ipd.setStatus("ADMITTED");
        ipd.setAdmissionDatetime(LocalDateTime.now()); ipd.setWardId(wid); ipd.setBedId(aBedId);
        ipd.setAdmissionConfirmed(true);
        aIpdId = ipdAdmissionRepository.save(ipd).getId();

        Billing b = new Billing();
        b.setCustomId("BIL-" + uniq()); b.setHospitalId(hid); b.setPatientId(pid); b.setDoctorId(aDoctorId);
        b.setBillingType("IPD"); b.setAmount(new BigDecimal("1000")); b.setPaymentStatus("PENDING");
        aBillId = billingRepository.save(b).getId();

        com.hms.entity.pharmacy.PharmacySale sale = new com.hms.entity.pharmacy.PharmacySale();
        sale.setHospitalId(hid); sale.setBillNumber("PHB-" + uniq());
        sale.setNetAmount(new BigDecimal("100")); sale.setPatientName("Walk-in");
        aPharmacySaleId = pharmacySaleRepository.save(sale).getId();

        return hid;
    }

    @BeforeEach
    void setUp() {
        long hidA = seedHospital("alpha", true);
        long hidB = seedHospital("bravo", false);
        tokenA = jwtUtil.generateToken(1L, "admin@alpha.com", "HOSPITAL_ADMIN", hidA, MODULES, null, "HOSPITAL", null);
        tokenB = jwtUtil.generateToken(2L, "admin@bravo.com", "HOSPITAL_ADMIN", hidB, MODULES, null, "HOSPITAL", null);
    }

    /**
     * Transport only -- the assertions, endpoints and tenant semantics below are unchanged.
     *
     * <p>TestRestTemplate cannot be used here any more. Its SimpleClientHttpRequestFactory
     * streams the request body, and 233b66e made the unauthenticated path answer 401 with a
     * body (SecurityConfig -> SecurityErrorResponder). HttpURLConnection reacts to the 401 by
     * trying to replay the request, which a streamed PUT/DELETE cannot do, and throws
     * "cannot retry due to server authentication, in streaming mode" before any status is
     * observed -- so the refusal this class exists to assert became unobservable.
     *
     * <p>java.net.http.HttpClient does not retry on 401, so the status is returned as sent.
     */
    private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
        try {
            java.net.http.HttpRequest.BodyPublisher publisher = body == null
                    ? java.net.http.HttpRequest.BodyPublishers.noBody()
                    : java.net.http.HttpRequest.BodyPublishers.ofString(body);

            java.net.http.HttpRequest.Builder req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + path))
                    .header("Authorization", "Bearer " + token)
                    .method(method.name(), publisher);
            if (body != null) req.header("Content-Type", MediaType.APPLICATION_JSON_VALUE);

            java.net.http.HttpResponse<String> res = java.net.http.HttpClient.newHttpClient()
                    .send(req.build(), java.net.http.HttpResponse.BodyHandlers.ofString());

            return ResponseEntity.status(res.statusCode()).body(res.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("HTTP call failed: " + method + " " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: " + method + " " + path, e);
        }
    }

    private void assertRefused(HttpMethod m, String path, String body) {
        HttpStatusCode s = call(m, path, tokenB, body).getStatusCode();
        assertThat(s.value())
                .as("cross-tenant %s %s must be refused, got %s", m, path, s)
                .isIn(401, 403, 404);
    }

    @Test
    void crossTenant_reads_areRefused() {
        assertRefused(HttpMethod.GET, "/hospital/ipd/" + aIpdId, null);
        assertRefused(HttpMethod.GET, "/hospital/patients/opd/" + aOpdId + "/medicines/pdf", null);
        assertRefused(HttpMethod.GET, "/hospital/patients/ipd/" + aIpdId + "/medicines/pdf", null);
        assertRefused(HttpMethod.GET, "/hospital/patients/ipd/" + aIpdId + "/prescription/pdf", null);
        assertRefused(HttpMethod.GET, "/hospital/doctors/prescription/opd/" + aOpdId + "/pdf", null);
        assertRefused(HttpMethod.GET, "/hospital/billing/" + aBillId + "/pdf", null);
        assertRefused(HttpMethod.GET, "/hospital/billing/ipd/" + aIpdId + "/bill", null);
        assertRefused(HttpMethod.GET, "/hospital/opd/queue/doctor/" + aDoctorId, null);
        // Pharmacy ERP surface: a sale invoice must be scoped to its owning pharmacy tenant.
        assertRefused(HttpMethod.GET, "/pharmacy/sales/" + aPharmacySaleId, null);
        assertRefused(HttpMethod.GET, "/pharmacy/sales/" + aPharmacySaleId + "/pdf", null);
    }

    @Test
    void crossTenant_writes_areRefused() {
        assertRefused(HttpMethod.POST, "/hospital/billing/" + aBillId + "/pay", "{\"amount\":1,\"mode\":\"CASH\"}");
        // Empty list keeps @Valid happy so the request reaches the service's tenant check
        // (a non-empty item would 400 on body validation before the check is exercised).
        assertRefused(HttpMethod.POST, "/hospital/ipd/" + aIpdId + "/administer", "{\"administeredItems\":[]}");
        assertRefused(HttpMethod.POST, "/hospital/ipd/" + aIpdId + "/confirm-discharge", "{}");
        assertRefused(HttpMethod.POST, "/hospital/beds/" + aBedId + "/maintenance", "{}");
        assertRefused(HttpMethod.POST, "/hospital/beds/" + aBedId + "/available", "{}");
    }

    @Test
    void crossTenant_deletes_areRefused() {
        // Patient delete is by public id and uses a tenant-scoped finder; a foreign tenant
        // must not be able to soft-delete another hospital's patient record.
        assertRefused(HttpMethod.DELETE, "/hospital/patients/" + aPatientPublicId, null);
    }

    @Test
    void sameTenant_ownIpd_isReachable() {
        // Positive control: the owner is not locked out by the tenant checks above.
        HttpStatusCode s = call(HttpMethod.GET, "/hospital/ipd/" + aIpdId, tokenA, null).getStatusCode();
        assertThat(s.value()).as("owner reading its own IPD").isEqualTo(200);
    }
}
