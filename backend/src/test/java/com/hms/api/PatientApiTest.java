package com.hms.api;

import com.hms.entity.Hospital;
import com.hms.entity.User;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
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
// Keep the live HTTP context's authentication fixtures independent of other JPA contexts.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:patient_api_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class PatientApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

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
        long hid = hospitalRepository.save(h).getId();
        User user = new User();
        user.setEmail("admin-" + hid + "@apitest.com");
        user.setPassword(passwordEncoder.encode("ApiTestPassword!123"));
        user.setName("API Test Admin");
        user.setRole("HOSPITAL_ADMIN");
        user.setHospitalId(hid);
        user.setIsActive(true);
        user = userRepository.saveAndFlush(user);
        token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole(), hid,
                MODULES, null, "HOSPITAL", null, user.getTokenVersion());
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
