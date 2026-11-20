package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Appointment;
import com.hms.entity.Doctor;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.User;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client feedback #7: a completed (or cancelled) appointment must leave Today and Upcoming and
 * be found under History — for the front desk's list and for the doctor's own list, with and
 * without a search term.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:appointment_views_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class AppointmentViewsApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;

    private static final List<String> MODULES = List.of("OPD", "APPOINTMENTS");
    private final ObjectMapper json = new ObjectMapper();

    private Hospital hospital;
    private Doctor doctor;
    private User doctorUser;
    private Patient patient;

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("Views Hospital");
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        hospital = hospitalRepository.save(h);

        doctorUser = user("DOCTOR");
        Doctor d = new Doctor();
        d.setHospitalId(hospital.getId());
        d.setName("Dr Views");
        d.setSpecialization("General Medicine");
        d.setPhone("9900000011");
        d.setEmail(doctorUser.getEmail());
        d.setPublicId("dpub-" + System.nanoTime());
        d.setIsActive(true);
        doctor = doctorRepository.save(d);

        Patient p = new Patient();
        p.setName("Meera Joshi");
        p.setPhone(String.format("97%08d", System.nanoTime() % 100_000_000L));
        p.setGender("FEMALE");
        p.setDateOfBirth(LocalDate.of(1985, 5, 5));
        p.setHospitalId(hospital.getId());
        p.setPublicId("ppub-" + System.nanoTime());
        p.setIsActive(true);
        patient = patientRepository.save(p);
    }

    private User user(String role) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "-" + System.nanoTime() + "@views.test");
        u.setPassword("{noop}x");
        u.setName("Views " + role);
        u.setRole(role);
        u.setHospitalId(hospital.getId());
        u.setIsActive(true);
        return userRepository.saveAndFlush(u);
    }

    private String token(User u) {
        return jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hospital.getId(),
                MODULES, null, "HOSPITAL", null, u.getTokenVersion());
    }

    private Appointment appt(LocalDate date, String status) {
        Appointment a = new Appointment();
        a.setHospitalId(hospital.getId());
        a.setPatientId(patient.getId());
        a.setDoctorId(doctor.getId());
        a.setAppointmentDate(date);
        a.setAppointmentTime(LocalTime.of(10, 0));
        a.setStatus(status);
        a.setIsActive(true);
        return appointmentRepository.save(a);
    }

    private List<Long> ids(String url, User who) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token(who));
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(res.getBody());
        JsonNode list = body.has("content") ? body.get("content") : body;
        List<Long> out = new ArrayList<>();
        list.forEach(n -> out.add(n.path("id").asLong()));
        return out;
    }

    @Test
    void completedAndCancelledLeaveTodayAndUpcomingAndAppearInHistory() throws Exception {
        LocalDate today = LocalDate.now();
        Appointment open = appt(today, "SCHEDULED");
        Appointment done = appt(today, "COMPLETED");
        Appointment cancelled = appt(today.plusDays(2), "CANCELLED");
        Appointment later = appt(today.plusDays(3), "SCHEDULED");
        User desk = user("RECEPTIONIST");

        for (String search : new String[] {"", "&search=Meera"}) {
            List<Long> todayIds = ids("/hospital/appointments?view=today" + search, desk);
            assertThat(todayIds).contains(open.getId()).doesNotContain(done.getId(), cancelled.getId());

            List<Long> upcoming = ids("/hospital/appointments?view=upcoming" + search, desk);
            assertThat(upcoming).contains(open.getId(), later.getId())
                    .doesNotContain(done.getId(), cancelled.getId());

            List<Long> history = ids("/hospital/appointments?view=history" + search, desk);
            assertThat(history).contains(done.getId(), cancelled.getId());
        }

        // The admin overview's "Today's appointments" panel.
        assertThat(ids("/hospital/appointments/today", desk)).contains(open.getId()).doesNotContain(done.getId());
    }

    @Test
    void theDoctorsOwnListFollowsTheSameRule() throws Exception {
        LocalDate today = LocalDate.now();
        Appointment open = appt(today, "SCHEDULED");
        Appointment done = appt(today, "COMPLETED");

        for (String search : new String[] {"", "&search=Meera"}) {
            List<Long> mine = ids("/hospital/appointments/my-appointments?view=today" + search, doctorUser);
            assertThat(mine).contains(open.getId()).doesNotContain(done.getId());
            List<Long> upcoming = ids("/hospital/appointments/my-appointments?view=upcoming" + search, doctorUser);
            assertThat(upcoming).contains(open.getId()).doesNotContain(done.getId());
        }
    }
}
