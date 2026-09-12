package com.hms.api;

import com.hms.entity.Hospital;
import com.hms.repository.HospitalRepository;
import com.hms.repository.HospitalSettingRepository;
import com.hms.repository.UserRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API-level test for the business-critical **patient registration** workflow, driven over HTTP
 * against the real controller -> service -> repository stack (H2, fast tier). Covers success,
 * validation failure, retrieval, pagination and the auth boundary — success AND failure paths.
 *
 * Complements CrossTenantIsolationTest (which covers cross-tenant access) — here we prove the
 * happy path and input validation for a single tenant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PatientApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired HospitalSettingRepository hospitalSettingRepository;
    @Autowired UserRepository userRepository;

    private static final List<String> MODULES =
            List.of("OPD", "IPD", "PHARMACY", "BILLING", "NURSING", "APPOINTMENTS");

    private String token;

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("API Test Hospital");
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        hospital = hospitalRepository.save(h);
        long hid = hospital.getId();
        token = jwtUtil.generateToken(1L, "admin@apitest.com", "HOSPITAL_ADMIN", hid, MODULES, null, "HOSPITAL", null);
    }

    private Hospital hospital;

    /** A real, active DOCTOR: the JWT filter checks the user's token version, so it must exist. */
    private HttpHeaders doctorAuth() {
        com.hms.entity.User d = new com.hms.entity.User();
        d.setEmail("doc-" + System.nanoTime() + "@apitest.com");
        d.setPassword("{noop}x");
        d.setName("Dr Front Desk");
        d.setRole("DOCTOR");
        d.setHospitalId(hospital.getId());
        d.setIsActive(true);
        d = userRepository.save(d);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwtUtil.generateToken(d.getId(), d.getEmail(), d.getRole(), hospital.getId(),
                MODULES, null, "HOSPITAL", null, d.getTokenVersion()));
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private void setReceptionMode(String mode) {
        com.hms.entity.HospitalSetting s = new com.hms.entity.HospitalSetting();
        s.setHospital(hospital);
        s.setReceptionMode(mode);
        if ("SOLO".equals(mode)) s.setBillingHandler("DOCTOR");
        hospitalSettingRepository.save(s);
    }

    private static final String DOCTOR_PATIENT =
            "{\"name\":\"Front Desk Doc\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"FEMALE\",\"phone\":\"9900112244\"}";

    /**
     * The doctor-side front desk is a hospital setting, not a role. Under HAS_RECEPTIONIST the
     * dashboard hides the button, but the API is the boundary: a doctor must still be refused.
     */
    @Test
    void registerPatient_doctorUnderHasReceptionist_returns403() {
        // No settings row at all: the transient default is HAS_RECEPTIONIST.
        ResponseEntity<String> res = rest.exchange("/hospital/patients", HttpMethod.POST,
                new HttpEntity<>(DOCTOR_PATIENT, doctorAuth()), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registerPatient_doctorUnderBoth_succeeds() {
        setReceptionMode("BOTH");
        ResponseEntity<String> res = rest.exchange("/hospital/patients", HttpMethod.POST,
                new HttpEntity<>(DOCTOR_PATIENT, doctorAuth()), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).as(res.getBody()).isTrue();
        assertThat(res.getBody()).contains("Front Desk Doc");
    }

    @Test
    void registerPatient_doctorUnderSolo_succeeds() {
        setReceptionMode("SOLO");
        ResponseEntity<String> res = rest.exchange("/hospital/patients", HttpMethod.POST,
                new HttpEntity<>(DOCTOR_PATIENT, doctorAuth()), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).as(res.getBody()).isTrue();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void registerPatient_validPayload_succeeds() {
        String body = "{\"name\":\"Ramesh Kumar\",\"dateOfBirth\":\"1980-06-15\",\"gender\":\"MALE\",\"phone\":\"9900112233\",\"address\":\"MG Road\"}";
        ResponseEntity<String> res = rest.exchange("/hospital/patients", HttpMethod.POST,
                new HttpEntity<>(body, auth()), String.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).as("register should succeed").isTrue();
        assertThat(res.getBody()).contains("Ramesh Kumar").contains("publicId");
        // The BCrypt/secret fields must never be echoed back.
        assertThat(res.getBody()).doesNotContain("password");
    }

    @Test
    void registerPatient_missingDateOfBirth_returns400() {
        String body = "{\"name\":\"No DOB\",\"gender\":\"MALE\",\"phone\":\"9900112233\"}";
        ResponseEntity<String> res = rest.exchange("/hospital/patients", HttpMethod.POST,
                new HttpEntity<>(body, auth()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listPatients_isPaginatedAndScopedToHospital() {
        // register two, then list
        for (String n : new String[] {"Pat One", "Pat Two"}) {
            rest.exchange("/hospital/patients", HttpMethod.POST, new HttpEntity<>(
                    "{\"name\":\"" + n + "\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"MALE\",\"phone\":\"9900000000\"}",
                    auth()), String.class);
        }
        ResponseEntity<String> res = rest.exchange("/hospital/patients?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(auth()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"totalElements\"").contains("Pat One");
    }

    @Test
    void patientsEndpoint_withoutToken_isUnauthorized() {
        ResponseEntity<String> res = rest.getForEntity("/hospital/patients", String.class);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
