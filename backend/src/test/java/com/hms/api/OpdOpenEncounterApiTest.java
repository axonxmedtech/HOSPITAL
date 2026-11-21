package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Doctor;
import com.hms.entity.Hospital;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Patient;
import com.hms.entity.User;
import com.hms.repository.DoctorRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One open visit at a time (client feedback #15): a patient admitted in IPD is never given a
 * parallel OPD visit, and the same patient is not queued twice for the same doctor on one day.
 * History never blocks: a discharged admission or another doctor is fine.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:opd_open_encounter_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class OpdOpenEncounterApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired IpdAdmissionRepository ipdAdmissionRepository;

    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING", "APPOINTMENTS");
    private final ObjectMapper json = new ObjectMapper();

    private Hospital hospital;
    private HttpHeaders auth;
    private Doctor drA;
    private Doctor drB;

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("Encounter Test Hospital");
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        hospital = hospitalRepository.save(h);

        User u = new User();
        u.setEmail("desk-" + System.nanoTime() + "@enc.test");
        u.setPassword("{noop}x");
        u.setName("Desk");
        u.setRole("RECEPTIONIST");
        u.setHospitalId(hospital.getId());
        u.setIsActive(true);
        u = userRepository.saveAndFlush(u);
        auth = new HttpHeaders();
        auth.setBearerAuth(jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hospital.getId(),
                MODULES, null, "HOSPITAL", null, u.getTokenVersion()));
        auth.setContentType(MediaType.APPLICATION_JSON);

        drA = doctor("Dr Asha");
        drB = doctor("Dr Bina");
    }

    private Doctor doctor(String name) {
        Doctor d = new Doctor();
        d.setName(name);
        d.setHospitalId(hospital.getId());
        d.setIsActive(true);
        d.setEmail("doc-" + System.nanoTime() + "@enc.test");
        d.setPublicId("dpub-" + System.nanoTime());
        d.setPhone("9800000001");
        d.setSpecialization("Gen");
        return doctorRepository.save(d);
    }

    private Patient patient(String name) {
        Patient p = new Patient();
        p.setName(name);
        p.setPhone(String.format("98%08d", System.nanoTime() % 100_000_000L));
        p.setGender("FEMALE");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p.setHospitalId(hospital.getId());
        p.setIsActive(true);
        p.setPublicId("ppub-" + System.nanoTime());
        return patientRepository.save(p);
    }

    private IpdAdmission admit(Patient p, String status) {
        IpdAdmission a = new IpdAdmission();
        a.setIpdNumber("IPD-T" + System.nanoTime());
        a.setPatientId(p.getId());
        a.setDoctorId(drA.getId());
        a.setHospitalId(hospital.getId());
        a.setAdmissionType("ELECTIVE");
        a.setStatus(status);
        a.setAdmissionDatetime(LocalDateTime.now().minusDays(1));
        a.setWardId(1L);
        a.setBedId(1L);
        return ipdAdmissionRepository.save(a);
    }

    private ResponseEntity<String> openOpd(Patient p, Doctor d) {
        String body = "{\"patientId\":\"" + p.getId() + "\",\"doctorId\":\"" + d.getId() + "\",\"visitType\":\"NEW\"}";
        return rest.exchange("/hospital/opd", HttpMethod.POST, new HttpEntity<>(body, auth), String.class);
    }

    @Test
    void anAdmittedPatientIsSentToTheirIpdCaseInsteadOfANewOpdVisit() throws Exception {
        Patient p = patient("Admitted Asha");
        IpdAdmission a = admit(p, "ADMITTED");

        ResponseEntity<String> res = openOpd(p, drA);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json.readTree(res.getBody());
        assertThat(body.path("error").asText()).contains("admitted in IPD").contains(a.getIpdNumber());
        assertThat(body.path("details").path("encounter").asText()).isEqualTo("IPD");
        assertThat(body.path("details").path("ipdAdmissionId").asText()).isEqualTo(String.valueOf(a.getId()));
    }

    @Test
    void dischargePlannedStillCountsAsInHospital() {
        Patient p = patient("Planned Priya");
        admit(p, "DISCHARGE_PLANNED");
        assertThat(openOpd(p, drA).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aDischargedAdmissionIsHistoryAndDoesNotBlock() {
        Patient p = patient("Discharged Deepa");
        admit(p, "DISCHARGED");
        assertThat(openOpd(p, drA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theSamePatientIsNotQueuedTwiceForTheSameDoctorToday() throws Exception {
        Patient p = patient("Twice Tara");
        assertThat(openOpd(p, drA).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> again = openOpd(p, drA);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json.readTree(again.getBody());
        assertThat(body.path("details").path("encounter").asText()).isEqualTo("OPD");
        assertThat(body.path("error").asText()).contains("Dr Asha");
    }

    @Test
    void seeingADifferentDoctorTheSameDayIsAllowed() {
        Patient p = patient("Two Doctors Tanvi");
        assertThat(openOpd(p, drA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openOpd(p, drB).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
