package com.hms.security;

import com.hms.entity.Hospital;
import com.hms.entity.User;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform metrics are for the Super Admin only. A hospital login -- here a receptionist -- used
 * to be let in by anyRequest().authenticated(). Health stays public for the load balancer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:actuator_role_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "management.health.redis.enabled=false"})
@ActiveProfiles("test")
class ActuatorMetricsRoleTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;

    private HttpHeaders receptionist() {
        Hospital h = new Hospital();
        h.setName("Metrics Test Hospital");
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD"));
        h.setIsSingleDoctor(false);
        Long hid = hospitalRepository.save(h).getId();
        User u = new User();
        u.setEmail("desk-" + System.nanoTime() + "@metrics.test");
        u.setPassword("{noop}x");
        u.setName("Desk");
        u.setRole("RECEPTIONIST");
        u.setHospitalId(hid);
        u.setIsActive(true);
        u = userRepository.saveAndFlush(u);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hid,
                List.of("OPD"), null, "HOSPITAL", null, u.getTokenVersion()));
        return headers;
    }

    @Test
    void aHospitalLoginCannotReadPlatformMetrics() {
        HttpEntity<Void> asReceptionist = new HttpEntity<>(receptionist());
        for (String path : List.of("/actuator/metrics", "/actuator/prometheus")) {
            assertThat(rest.exchange(path, HttpMethod.GET, asReceptionist, String.class).getStatusCode())
                    .as(path).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void healthStaysPublicForTheLoadBalancer() {
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
