package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Billing;
import com.hms.entity.Hospital;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Patient;
import com.hms.entity.User;
import com.hms.repository.BillingRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.UserRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client feedback #4 and #5: the bills list filters by category (OPD / IPD) alongside its other
 * filters, and an IPD case shows its own bill together with the bill of the OPD visit that led
 * to the admission, with one combined total — without merging or rewriting either bill.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:billing_category_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class BillingCategoryApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired BillingRepository billingRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING");
    private final ObjectMapper json = new ObjectMapper();

    private Hospital hospital;
    private HttpHeaders auth;
    private Patient patient;

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("Billing Category Hospital");
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        hospital = hospitalRepository.save(h);

        User u = new User();
        u.setEmail("admin-" + System.nanoTime() + "@bill.test");
        u.setPassword("{noop}x");
        u.setName("Admin");
        u.setRole("HOSPITAL_ADMIN");
        u.setHospitalId(hospital.getId());
        u.setIsActive(true);
        u = userRepository.saveAndFlush(u);
        auth = new HttpHeaders();
        auth.setBearerAuth(jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hospital.getId(),
                MODULES, null, "HOSPITAL", null, u.getTokenVersion()));

        Patient p = new Patient();
        p.setName("Sunita Pawar");
        p.setPhone(String.format("96%08d", System.nanoTime() % 100_000_000L));
        p.setGender("FEMALE");
        p.setDateOfBirth(LocalDate.of(1970, 2, 2));
        p.setHospitalId(hospital.getId());
        p.setPublicId("ppub-" + System.nanoTime());
        p.setIsActive(true);
        patient = patientRepository.save(p);
    }

    private Billing bill(String type, Long opdId, Long ipdId, String amount, String status) {
        Billing b = new Billing();
        b.setHospitalId(hospital.getId());
        b.setPatientId(patient.getId());
        b.setDoctorId(1L);
        b.setBillingType(type);
        b.setOpdId(opdId);
        b.setIpdAdmissionId(ipdId);
        b.setAmount(new BigDecimal(amount));
        b.setPaymentStatus(status);
        return billingRepository.save(b);
    }

    private IpdAdmission admission(Long sourceOpdId) {
        IpdAdmission a = new IpdAdmission();
        a.setIpdNumber("IPD-B" + System.nanoTime());
        a.setPatientId(patient.getId());
        a.setDoctorId(1L);
        a.setHospitalId(hospital.getId());
        a.setAdmissionType("EMERGENCY");
        a.setStatus("ADMITTED");
        a.setAdmissionDatetime(LocalDateTime.now());
        a.setWardId(1L);
        a.setBedId(1L);
        a.setSourceOpdId(sourceOpdId);
        return ipdAdmissionRepository.save(a);
    }

    private JsonNode get(String url) throws Exception {
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(auth), String.class);
        assertThat(res.getStatusCode()).as(res.getBody()).isEqualTo(HttpStatus.OK);
        return json.readTree(res.getBody());
    }

    private List<Long> ids(JsonNode page) {
        List<Long> out = new ArrayList<>();
        page.path("content").forEach(n -> out.add(n.path("id").asLong()));
        return out;
    }

    @Test
    void theBillsListFiltersByCategoryTogetherWithStatusAndSearch() throws Exception {
        Billing opd = bill("OPD", 9001L, null, "500", "PENDING");
        IpdAdmission a = admission(9001L);
        Billing ipd = bill("IPD", null, a.getId(), "550", "PENDING");
        Billing paidOpd = bill("OPD", 9002L, null, "300", "PAID");

        assertThat(ids(get("/hospital/billing?category=OPD"))).contains(opd.getId(), paidOpd.getId())
                .doesNotContain(ipd.getId());
        assertThat(ids(get("/hospital/billing?category=IPD"))).containsExactly(ipd.getId());
        assertThat(ids(get("/hospital/billing?category=OPD&status=PENDING"))).containsExactly(opd.getId());
        assertThat(ids(get("/hospital/billing?category=OPD&search=Sunita"))).hasSize(2);
        JsonNode one = get("/hospital/billing?category=OPD&size=1");
        assertThat(one.path("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void anIpdCaseShowsItsOpdAndIpdBillsWithOneTotal() throws Exception {
        bill("OPD", 9101L, null, "500", "PAID");
        IpdAdmission a = admission(9101L);
        bill("IPD", null, a.getId(), "550", "PENDING");
        bill("OPD", 9999L, null, "700", "PENDING"); // another visit: not part of this stay

        JsonNode stay = get("/hospital/billing/ipd/" + a.getId() + "/related-bills");

        assertThat(stay.path("bills")).hasSize(2);
        assertThat(stay.path("totalAmount").decimalValue()).isEqualByComparingTo("1050");
        List<String> categories = new ArrayList<>();
        stay.path("bills").forEach(b -> categories.add(b.path("category").asText()));
        assertThat(categories).containsExactly("OPD", "IPD");
    }

    @Test
    void aCancelledBillIsListedButNotCounted() throws Exception {
        bill("OPD", 9201L, null, "500", "CANCELLED");
        IpdAdmission a = admission(9201L);
        bill("IPD", null, a.getId(), "550", "PENDING");

        JsonNode stay = get("/hospital/billing/ipd/" + a.getId() + "/related-bills");
        assertThat(stay.path("bills")).hasSize(2);
        assertThat(stay.path("totalAmount").decimalValue()).isEqualByComparingTo("550");
    }

    @Test
    void theBillsListFiltersByBillDateAndRejectsABackwardsRange() throws Exception {
        Billing old = bill("OPD", 9301L, null, "200", "PAID");
        jdbc.update("UPDATE billing SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(40)), old.getId());
        Billing recent = bill("OPD", 9302L, null, "300", "PENDING");

        String from = LocalDate.now().minusDays(7).toString();
        assertThat(ids(get("/hospital/billing?from=" + from))).contains(recent.getId()).doesNotContain(old.getId());
        String d = LocalDate.now().minusDays(40).toString();
        assertThat(ids(get("/hospital/billing?from=" + d + "&to=" + d))).containsExactly(old.getId());
        assertThat(ids(get("/hospital/billing?category=OPD&status=PENDING&from=" + from))).containsExactly(recent.getId());

        ResponseEntity<String> bad = rest.exchange("/hospital/billing?from=2026-05-10&to=2026-05-01",
                HttpMethod.GET, new HttpEntity<>(auth), String.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
