package com.hms.security;

import com.hms.entity.*;
import com.hms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Admission selection regressions through authenticated HTTP and real persistence. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdmissionSelectionValidationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired UserRepository userRepository;
    @Autowired WardRepository wardRepository;
    @Autowired BedRepository bedRepository;
    @Autowired OpdRepository opdRepository;
    @Autowired IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired BillingRepository billingRepository;

    private static final List<String> MODULES = List.of("OPD", "IPD", "PHARMACY", "BILLING");

    private String tokenB;
    private Long aOpdId, aPatientId;
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
    void seedSyntheticTenants() {
        long hidA = seedHospital("alpha");
        long hidB = seedHospital("bravo");

        long[] a = seedJourney(hidA, "alpha", "admin@alpha.com");
        aOpdId = a[0]; aPatientId = a[3];

        long[] b = seedJourney(hidB, "bravo", "admin@bravo.com");
        bOpdId = b[0]; bWardId = b[1]; bBedId = b[2];

        User adminB = new User();
        adminB.setEmail("admin@bravo-" + uniq() + ".com");
        adminB.setPassword("test-password-hash");
        adminB.setName("Admin bravo");
        adminB.setRole("HOSPITAL_ADMIN");
        adminB.setHospitalId(hidB);
        adminB.setIsActive(true);
        adminB.setTokenVersion(0);
        adminB = userRepository.save(adminB);
        tokenB = jwtUtil.generateToken(adminB.getId(), adminB.getEmail(), adminB.getRole(), hidB,
                MODULES, null, "HOSPITAL", null, adminB.getTokenVersion());
    }

    private ResponseEntity<String> admit(String path, Long opd, Long patient, Long doctor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenB);
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("opdId", opd);
        body.put("patientId", patient);
        body.put("doctorId", doctor);
        body.put("wardId", bWardId);
        body.put("bedId", bBedId);
        return rest.exchange(path + "/ipd/admit", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private void assertRefused(String path, Long opd, Long patient, Long doctor, int status) {
        long admissions = ipdAdmissionRepository.count();
        long bills = billingRepository.count();
        var before = opdRepository.findById(bOpdId).orElseThrow().getStatus();
        ResponseEntity<String> response = admit(path, opd, patient, doctor);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        assertThat(ipdAdmissionRepository.count()).isEqualTo(admissions);
        assertThat(billingRepository.count()).isEqualTo(bills);
        assertThat(bedRepository.findById(bBedId).orElseThrow().getStatus()).isEqualTo("available");
        assertThat(opdRepository.findById(bOpdId).orElseThrow().getStatus()).isEqualTo(before);
    }

    private Long ownPatient() { return opdRepository.findById(bOpdId).orElseThrow().getPatient().getId(); }
    private Long ownDoctor() { return opdRepository.findById(bOpdId).orElseThrow().getDoctor().getId(); }

    @Test
    void missingPatientAndOpdNeverSelectsFirstPatient() {
        assertRefused("/hospital", null, null, null, 400);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {0, -1})
    void nonPositivePatientSelectionIsInvalid(long id) {
        assertRefused("/hospital", null, id, null, 400);
    }

    @Test
    void foreignPatientIsNotFound() {
        assertRefused("/hospital", null, aPatientId, ownDoctor(), 404);
    }

    @Test
    void nonexistentPatientIsNotFound() {
        assertRefused("/hospital", null, Long.MAX_VALUE, ownDoctor(), 404);
    }

    @Test
    void foreignOpdIsNotFound() {
        assertRefused("/hospital", aOpdId, ownPatient(), ownDoctor(), 404);
    }

    @Test
    void anotherPatientFromSameHospitalMustNotOverrideOpdIdentity() {
        Patient patient = new Patient();
        patient.setName("Synthetic other patient");
        patient.setHospitalId(patientRepository.findById(ownPatient()).orElseThrow().getHospitalId());
        patient.setPublicId("ppub-" + uniq());
        patient.setPhone("9900000002");
        patient.setGender("MALE");
        patientRepository.save(patient);
        assertRefused("/hospital", bOpdId, patient.getId(), ownDoctor(), 400);
    }

    @Test
    void foreignDoctorAlreadyLinkedToOpdCannotBypassValidation() {
        Opd opd = opdRepository.findById(bOpdId).orElseThrow();
        opd.setDoctor(opdRepository.findById(aOpdId).orElseThrow().getDoctor());
        opdRepository.save(opd);
        assertRefused("/hospital", bOpdId, null, null, 404);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {0, -1})
    void nonPositiveOpdIsInvalidEvenWithValidPatient(long id) {
        assertRefused("/hospital", id, ownPatient(), ownDoctor(), 400);
    }

    @Test
    void suppliedPatientMustMatchSelectedOpd() {
        assertRefused("/hospital", bOpdId, aPatientId, ownDoctor(), 400);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void inactivePatientCannotBeAdmitted(boolean fromOpd) {
        Patient patient = patientRepository.findById(ownPatient()).orElseThrow();
        patient.setIsActive(false);
        patientRepository.save(patient);
        assertRefused("/hospital", fromOpd ? bOpdId : null, patient.getId(), ownDoctor(), 400);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void foreignDoctorCannotBeSuppliedDirectlyOrOverrideOpdDoctor(boolean fromOpd) {
        Long foreignDoctor = opdRepository.findById(aOpdId).orElseThrow().getDoctor().getId();
        assertRefused("/hospital", fromOpd ? bOpdId : null, ownPatient(), foreignDoctor, 404);
    }

    @Test
    void nonexistentDoctorIsNotFound() {
        assertRefused("/hospital", null, ownPatient(), Long.MAX_VALUE, 404);
    }

    @Test
    void inactiveDoctorCannotBeExplicitlySelected() {
        Doctor doctor = doctorRepository.findById(ownDoctor()).orElseThrow();
        doctor.setIsActive(false);
        doctorRepository.save(doctor);
        assertRefused("/hospital", null, ownPatient(), doctor.getId(), 404);
    }

    @Test
    void inactiveOpdDoctorCannotBypassValidation() {
        Doctor doctor = doctorRepository.findById(ownDoctor()).orElseThrow();
        doctor.setIsActive(false);
        doctorRepository.save(doctor);
        assertRefused("/hospital", bOpdId, null, null, 404);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {0, -1})
    void nonPositiveDoctorSelectionIsInvalid(long id) {
        assertRefused("/hospital", null, ownPatient(), id, 400);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/clinic", "/pharmacy"})
    void admissionAliasesCannotBypassDoctorValidation(String path) {
        assertRefused(path, bOpdId, null, Long.MAX_VALUE, 404);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void validExplicitPatientAndDoctorPreserveAdmissionAndBilling(boolean fromOpd) {
        Long patient = ownPatient();
        Long doctor = ownDoctor();
        ResponseEntity<String> response = admit("/hospital", fromOpd ? bOpdId : null, patient, doctor);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        IpdAdmission admission = ipdAdmissionRepository.findAll().stream()
                .filter(a -> a.getPatientId().equals(patient)).findFirst().orElseThrow();
        assertThat(admission.getDoctorId()).isEqualTo(doctor);
        assertThat(admission.getSourceOpdId()).isEqualTo(fromOpd ? bOpdId : null);
        assertThat(bedRepository.findById(bBedId).orElseThrow().getStatus()).isEqualTo("occupied");
        assertThat(billingRepository.findAll()).anySatisfy(bill -> {
            assertThat(bill.getPatientId()).isEqualTo(patient);
            assertThat(bill.getDoctorId()).isEqualTo(doctor);
        });
    }
}
