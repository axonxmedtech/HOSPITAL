package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Client feedback #6 and #10: the admin chooses each role's sidebar tabs; doctors start without the front-desk ones. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:sidebar_settings_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class SidebarSettingsApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;

    private static final List<String> MODULES = List.of("OPD", "IPD", "ICU");
    private final ObjectMapper json = new ObjectMapper();
    private Hospital hospital;

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("Sidebar Hospital");
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        hospital = hospitalRepository.save(h);
    }

    private HttpHeaders as(String role) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "-" + System.nanoTime() + "@side.test");
        u.setPassword("{noop}x");
        u.setName("Side " + role);
        u.setRole(role);
        u.setHospitalId(hospital.getId());
        u.setIsActive(true);
        u = userRepository.saveAndFlush(u);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hospital.getId(),
                MODULES, null, "HOSPITAL", null, u.getTokenVersion()));
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> call(HttpMethod m, String url, String body, HttpHeaders h) {
        return rest.exchange(url, m, new HttpEntity<>(body, h), String.class);
    }

    private List<String> hidden(ResponseEntity<String> res) throws Exception {
        List<String> out = new ArrayList<>();
        json.readTree(res.getBody()).path("hidden").forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void doctorsStartWithoutTheFrontDeskTabsAndTheAdminCanChangeIt() throws Exception {
        HttpHeaders doctor = as("DOCTOR");
        HttpHeaders admin = as("HOSPITAL_ADMIN");
        assertThat(hidden(call(HttpMethod.GET, "/hospital/settings/sidebar/mine", null, doctor)))
                .containsExactlyInAnyOrder("follow-ups", "inventory", "hospital-inventory", "icu-beds");

        ResponseEntity<String> put = call(HttpMethod.PUT, "/hospital/settings/sidebar/DOCTOR",
                "{\"hidden\":[\"icu-beds\",\"ot\"]}", admin);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hidden(call(HttpMethod.GET, "/hospital/settings/sidebar/mine", null, doctor)))
                .containsExactly("icu-beds", "ot");

        call(HttpMethod.DELETE, "/hospital/settings/sidebar/DOCTOR", null, admin);
        assertThat(hidden(call(HttpMethod.GET, "/hospital/settings/sidebar/mine", null, doctor)))
                .contains("follow-ups");
    }

    @Test
    void onlyTheAdminChangesItAndTheWayBackToSettingsCannotBeHidden() throws Exception {
        assertThat(call(HttpMethod.PUT, "/hospital/settings/sidebar/DOCTOR", "{\"hidden\":[]}", as("DOCTOR"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PUT, "/hospital/settings/sidebar/HOSPITAL_ADMIN",
                "{\"hidden\":[\"settings\"]}", as("HOSPITAL_ADMIN")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PUT, "/hospital/settings/sidebar/SUPER_ADMIN",
                "{\"hidden\":[]}", as("HOSPITAL_ADMIN")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theAdminSeesEveryRole() throws Exception {
        JsonNode all = json.readTree(call(HttpMethod.GET, "/hospital/settings/sidebar", null, as("HOSPITAL_ADMIN")).getBody());
        assertThat(all.path("roles").has("RECEPTIONIST")).isTrue();
        assertThat(all.path("roles").path("DOCTOR").path("defaultHidden")).hasSize(4);
    }
}
