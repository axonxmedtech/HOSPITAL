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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The IPD admission form is commonly filled and signed at the admission desk.
 *
 * <p>Only a logged-in, assigned staff nurse could save or confirm it. With Separate Nurse Login
 * off (the default) staff nurses have no login at all, so every admission stayed "pending"
 * forever. Reception and admin may now complete it; the tenant boundary still holds, and a
 * doctor is still only a reader. These drive the real endpoints against a real database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdmissionFormDeskAccessTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;
    @Autowired WardRepository wardRepository;
    @Autowired IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired AdmissionFormRepository admissionFormRepository;

    private static final List<String> MODULES = List.of("OPD", "IPD");

    private long hospitalId;
    private long otherHospitalId;
    private Long admissionId;

    private String uniq() { return Long.toString(System.nanoTime()); }

    private long hospital(String slug) {
        Hospital h = new Hospital();
        h.setName("H-" + slug); h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE"); h.setIsActive(true);
        h.setModules(MODULES); h.setIsSingleDoctor(false);
        return hospitalRepository.save(h).getId();
    }

    @BeforeEach
    void setUp() {
        hospitalId = hospital("desk");
        otherHospitalId = hospital("other");

        Ward w = new Ward();
        w.setWardName("W-" + uniq()); w.setHospitalId(hospitalId);
        w.setBedPrice(java.math.BigDecimal.ZERO); w.setTotalBeds(1);
        Long wardId = wardRepository.save(w).getWardId();

        IpdAdmission a = new IpdAdmission();
        a.setHospitalId(hospitalId);
        a.setPatientId(1L);
        a.setWardId(wardId);
        a.setStatus("ADMITTED");
        // Not "IPD-…": the next-number query casts the suffix, and a nanotime one overflows it.
        a.setIpdNumber("TST-" + uniq());
        a.setDoctorId(1L);
        a.setAdmissionType("ELECTIVE");
        a.setAdmissionDatetime(LocalDateTime.now());
        a.setBedId(1L);
        a.setAdmissionConfirmed(false);
        admissionId = ipdAdmissionRepository.save(a).getId();
    }

    private String token(String role, long hid) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "-" + uniq() + "@desk.test");
        u.setPassword("test-password-hash");
        u.setName(role);
        u.setRole(role);
        u.setHospitalId(hid);
        u.setIsActive(true);
        u.setTokenVersion(0);
        u = userRepository.save(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hid,
                MODULES, null, "HOSPITAL", null, u.getTokenVersion());
    }

    private ResponseEntity<String> post(String path, String tok) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tok);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/hospital/nurse/admission-form/admission/" + admissionId + path,
                HttpMethod.POST, new HttpEntity<>("{\"relativeName\":\"Sita\"}", h), String.class);
    }

    private boolean confirmed() {
        return Boolean.TRUE.equals(ipdAdmissionRepository.findById(admissionId).orElseThrow().getAdmissionConfirmed());
    }

    @Test
    void receptionCanFillAndConfirmTheFormAtTheDesk() {
        String tok = token("RECEPTIONIST", hospitalId);

        assertThat(post("", tok).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/confirm", tok).getStatusCode().value()).isEqualTo(200);

        assertThat(admissionFormRepository.findByIpdAdmissionId(admissionId)).isPresent();
        assertThat(confirmed()).isTrue();
    }

    @Test
    void anAdminCanFillAndConfirmTheForm() {
        String tok = token("HOSPITAL_ADMIN", hospitalId);

        assertThat(post("", tok).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/confirm", tok).getStatusCode().value()).isEqualTo(200);
        assertThat(confirmed()).isTrue();
    }

    @Test
    void confirmingStillRequiresASavedForm() {
        ResponseEntity<String> res = post("/confirm", token("RECEPTIONIST", hospitalId));

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(confirmed()).isFalse();
    }

    @Test
    void aDoctorIsStillOnlyAReader() {
        String tok = token("DOCTOR", hospitalId);

        assertThat(post("", tok).getStatusCode().value()).isEqualTo(403);
        assertThat(admissionFormRepository.findByIpdAdmissionId(admissionId)).isEmpty();
    }

    @Test
    void anotherHospitalsReceptionCannotTouchIt() {
        String tok = token("RECEPTIONIST", otherHospitalId);

        // 404, as for a missing admission: not a 401, which would log the receptionist out.
        assertThat(post("", tok).getStatusCode().value()).isEqualTo(404);
        assertThat(post("/confirm", tok).getStatusCode().value()).isEqualTo(404);
        assertThat(admissionFormRepository.findByIpdAdmissionId(admissionId)).isEmpty();
        assertThat(confirmed()).isFalse();
    }
}
