package com.hms.api;

import com.hms.entity.Hospital;
import com.hms.entity.User;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Audit tab browses any period in full: server-side pages with totals, filtered by date in the
 * database, and never another tenant's rows. Without `page` the endpoint keeps returning the
 * newest entries as a plain list (the activity feed).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:audit_paging_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "management.health.redis.enabled=false"})
@ActiveProfiles("test")
class AuditLogPagingApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private HttpEntity<Void> admin;
    private Long hid;

    private Long hospital() {
        Hospital h = new Hospital();
        h.setName("Audit Paging " + System.nanoTime());
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD"));
        h.setIsSingleDoctor(false);
        return hospitalRepository.save(h).getId();
    }

    /** Inserted with JDBC: the entity stamps (and never updates) its own timestamp. */
    private void log(Long hospitalId, String action, LocalDateTime at) {
        jdbc.update("INSERT INTO audit_logs (public_id, action, details, performed_by, hospital_id, timestamp) "
                + "VALUES (?, ?, ?, 'admin@paging.test', ?, ?)",
                java.util.UUID.randomUUID().toString(), action, action, hospitalId, java.sql.Timestamp.valueOf(at));
    }

    @BeforeEach
    void setUp() {
        hid = hospital();
        Long other = hospital();
        User u = new User();
        u.setEmail("admin-" + System.nanoTime() + "@paging.test");
        u.setPassword("{noop}x");
        u.setName("Admin");
        u.setRole("HOSPITAL_ADMIN");
        u.setHospitalId(hid);
        u.setIsActive(true);
        u = userRepository.saveAndFlush(u);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hid,
                List.of("OPD"), null, "HOSPITAL", null, u.getTokenVersion()));
        admin = new HttpEntity<>(h);

        LocalDate jan = LocalDate.of(2026, 1, 10);
        for (int i = 0; i < 5; i++) log(hid, "BILL_DISCOUNTED", jan.atTime(10, i));       // 5 in January
        for (int i = 0; i < 3; i++) log(hid, "PATIENT_UPDATED", jan.plusMonths(1).atTime(9, i)); // 3 in February
        log(other, "BILL_DISCOUNTED", jan.atTime(11, 0)); // another tenant's
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(String query) {
        ResponseEntity<Map> r = rest.exchange("/hospital/audit-logs?" + query, HttpMethod.GET, admin, Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as(String.valueOf(r.getBody())).isTrue();
        return r.getBody();
    }

    @Test
    void pagesThroughTheWholeHistoryWithTotals_onlyThisTenant() {
        Map<String, Object> first = page("page=0&size=3");
        assertThat(first.get("totalElements")).isEqualTo(8); // 5 + 3, not the other tenant's
        assertThat(first.get("totalPages")).isEqualTo(3);
        assertThat((List<?>) first.get("content")).hasSize(3);

        Map<String, Object> last = page("page=2&size=3");
        assertThat((List<?>) last.get("content")).hasSize(2);
    }

    @Test
    void filtersByDateRangeInTheDatabase_withTheToDayIncluded() {
        Map<String, Object> january = page("page=0&size=25&from=2026-01-01&to=2026-01-10");
        assertThat(january.get("totalElements")).isEqualTo(5);

        Map<String, Object> february = page("page=0&size=25&from=2026-02-01&to=2026-02-28&search=patient");
        assertThat(february.get("totalElements")).isEqualTo(3);
    }

    @Test
    void withoutPage_theActivityFeedStillGetsAPlainList() {
        ResponseEntity<List> r = rest.exchange("/hospital/audit-logs?limit=4", HttpMethod.GET, admin, List.class);
        assertThat(r.getBody()).hasSize(4);
    }
}
