package com.hms.service.hospital;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.entity.User;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fees are charged exactly as configured, so the settings endpoint must refuse a negative fee
 * and accept a free (0) one — for hospital and clinic tenants alike, which share this endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HospitalFeesValidationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;

    private String uniq() { return Long.toString(System.nanoTime()); }

    private ResponseEntity<String> putFees(String tenant, String consultation, String casePaper) {
        List<String> modules = List.of("BILLING", "OPD");
        Hospital h = new Hospital();
        h.setName("H-fees"); h.setCustomId("HID-" + uniq());
        h.setType(HospitalType.valueOf(tenant));
        h.setSubscriptionStatus("ACTIVE"); h.setIsActive(true);
        h.setModules(modules); h.setIsSingleDoctor(false);
        h.setConsultationFee(new BigDecimal("500.00"));
        h.setCasePaperFee(new BigDecimal("100.00"));
        Long hospitalId = hospitalRepository.save(h).getId();

        User u = new User();
        u.setEmail("fees-" + uniq() + "@t.test");
        u.setPassword("unused-in-this-test");
        u.setName("Fees Admin");
        u.setRole("HOSPITAL_ADMIN");
        u.setHospitalId(hospitalId);
        u.setIsActive(true);
        u = userRepository.save(u);

        String token = jwtUtil.generateToken(u.getId(), u.getEmail(), "HOSPITAL_ADMIN",
                hospitalId, modules, null, tenant, null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "10.210.0.12");
        String body = "{\"consultationFee\":" + consultation + ",\"casePaperFee\":" + casePaper + "}";
        String prefix = "CLINIC".equals(tenant) ? "/clinic" : "/hospital";
        return rest.exchange(prefix + "/settings/fees", HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    @ParameterizedTest
    @CsvSource({"HOSPITAL", "CLINIC"})
    void aNegativeFeeIsRefused(String tenant) {
        assertThat(putFees(tenant, "-1", "100").getStatusCode().value()).isEqualTo(400);
        assertThat(putFees(tenant, "500", "-50").getStatusCode().value()).isEqualTo(400);
    }

    @ParameterizedTest
    @CsvSource({"HOSPITAL", "CLINIC"})
    void aFreeConsultationIsAccepted(String tenant) {
        ResponseEntity<String> res = putFees(tenant, "0", "100");
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }
}
