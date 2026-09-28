package com.hms.api;

import com.hms.entity.Doctor;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalSetting;
import com.hms.entity.User;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.HospitalSettingRepository;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking an appointment is front-desk work, so a doctor may do it only where the hospital says
 * so — the same rule patient registration carries, enforced by the same
 * {@link com.hms.security.FrontDeskAccessGuard}.
 *
 * <p>Registration already has this proof over HTTP ({@code PatientApiTest}); booking did not, and
 * it is the more consequential of the two: creating an appointment can create a patient as a side
 * effect, so a refusal that arrived too late would leave a patient record behind. Each refusal
 * here is asserted against the row counts as well as the status code.
 */
// Shares the default test context deliberately: a bespoke datasource URL would be one more
// cached application context, and evicting one tears down the in-memory database the rest of the
// suite is still using. Nothing here needs an empty database — every assertion is a delta.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AppointmentFrontDeskApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired HospitalSettingRepository hospitalSettingRepository;
    @Autowired UserRepository userRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;

    private static final List<String> MODULES =
            List.of("OPD", "IPD", "PHARMACY", "BILLING", "NURSING", "APPOINTMENTS");

    private Hospital hospital;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("Front Desk Hospital");
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        hospital = hospitalRepository.save(h);

        Doctor d = new Doctor();
        d.setHospitalId(hospital.getId());
        d.setName("Dr Front Desk");
        d.setSpecialization("General Medicine");
        d.setPhone("9900000010");
        d.setEmail("dr-" + System.nanoTime() + "@frontdesk.test");
        d.setIsActive(true);
        doctor = doctorRepository.save(d);
    }

    @Test
    void aDoctorCannotBookWhereTheHospitalKeepsAReceptionDesk() {
        // No settings row at all: the transient default is HAS_RECEPTIONIST.
        long appointmentsBefore = appointmentRepository.count();
        long patientsBefore = patientRepository.count();

        ResponseEntity<String> res = book(doctorToken());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(appointmentRepository.count()).as("refused booking must write nothing")
                .isEqualTo(appointmentsBefore);
        assertThat(patientRepository.count()).as("refused booking must not register a patient")
                .isEqualTo(patientsBefore);
    }

    @Test
    void anExplicitHasReceptionistSettingRefusesJustTheSame() {
        receptionMode("HAS_RECEPTIONIST");
        long appointmentsBefore = appointmentRepository.count();
        long patientsBefore = patientRepository.count();

        ResponseEntity<String> res = book(doctorToken());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(appointmentRepository.count()).isEqualTo(appointmentsBefore);
        assertThat(patientRepository.count()).isEqualTo(patientsBefore);
    }

    @Test
    void aDoctorBooksUnderSolo() {
        receptionMode("SOLO");
        long before = appointmentRepository.count();

        ResponseEntity<String> res = book(doctorToken());

        assertThat(res.getStatusCode().is2xxSuccessful()).as(res.getBody()).isTrue();
        assertThat(appointmentRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void aDoctorBooksUnderBoth() {
        receptionMode("BOTH");
        long before = appointmentRepository.count();

        ResponseEntity<String> res = book(doctorToken());

        assertThat(res.getStatusCode().is2xxSuccessful()).as(res.getBody()).isTrue();
        assertThat(appointmentRepository.count()).isEqualTo(before + 1);
    }

    /** Reception mode is about doctors. It must never take the desk away from the desk. */
    @Test
    void aReceptionistIsUnaffectedByReceptionMode() {
        receptionMode("HAS_RECEPTIONIST");
        long before = appointmentRepository.count();

        ResponseEntity<String> res = book(tokenFor("RECEPTIONIST"));

        assertThat(res.getStatusCode().is2xxSuccessful()).as(res.getBody()).isTrue();
        assertThat(appointmentRepository.count()).isEqualTo(before + 1);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private ResponseEntity<String> book(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Shape mirrors CrossRoleGoldenJourneyTest's proven booking payload; the walk-in fields
        // are what make a booking able to create a patient, which is what the counts assert.
        String body = "{\"doctorId\":" + doctor.getId()
                + ",\"patientName\":\"Synthetic Walkin\""
                + ",\"patientPhone\":\"" + uniquePhone() + "\""
                + ",\"appointmentDate\":\"" + LocalDate.now() + "\""
                + ",\"appointmentTime\":\"11:30\"}";
        return rest.exchange("/hospital/appointments", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    /** Ten digits, unique per call: the shared database already holds other suites' patients. */
    private static String uniquePhone() {
        return "9" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
    }

    private void receptionMode(String mode) {
        HospitalSetting s = new HospitalSetting();
        s.setHospital(hospital);
        s.setReceptionMode(mode);
        if ("SOLO".equals(mode)) s.setBillingHandler("DOCTOR");
        hospitalSettingRepository.save(s);
    }

    private String doctorToken() {
        return tokenFor("DOCTOR");
    }

    /** A real, active user: the JWT filter checks the user's token version, so it must exist. */
    private String tokenFor(String role) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "-" + System.nanoTime() + "@frontdesk.test");
        u.setPassword("{noop}x");
        u.setName("Front Desk " + role);
        u.setRole(role);
        u.setHospitalId(hospital.getId());
        u.setIsActive(true);
        u = userRepository.saveAndFlush(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hospital.getId(),
                MODULES, null, "HOSPITAL", null, u.getTokenVersion());
    }
}
