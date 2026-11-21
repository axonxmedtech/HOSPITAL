package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Doctor;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Opd;
import com.hms.entity.Patient;
import com.hms.entity.PatientAccessLog;
import com.hms.entity.User;
import com.hms.entity.Ward;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.OpdRepository;
import com.hms.repository.PatientAccessLogRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.WardRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The patient access log: opening, printing or downloading a patient's record leaves a row saying
 * who, when and what; the hospital admin can read it from the Patient File; refused requests and
 * other hospitals' requests leave nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:patient_access_log_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class PatientAccessLogApiTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING", "ICU", "OT", "NURSING");

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitals;
    @Autowired UserRepository users;
    @Autowired DoctorRepository doctors;
    @Autowired PatientRepository patients;
    @Autowired WardRepository wards;
    @Autowired IpdAdmissionRepository admissions;
    @Autowired OpdRepository opds;
    @Autowired PatientAccessLogRepository accessLogs;

    private final ObjectMapper json = new ObjectMapper();
    private Hospital hospital;
    private Doctor dr;
    private String admin, adminEmail, reception, receptionEmail, doctor, pharmacist;

    private static long uniq() { return System.nanoTime() + SEQ.incrementAndGet(); }

    private Hospital newHospital(String name) {
        Hospital h = new Hospital();
        h.setName(name);
        h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        h.setType(HospitalType.HOSPITAL);
        return hospitals.save(h);
    }

    private String token(Hospital h, String role, String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword("{noop}x");
        u.setName("Staff " + role);
        u.setRole(role);
        u.setHospitalId(h.getId());
        u.setIsActive(true);
        u.setTokenVersion(0);
        u = users.saveAndFlush(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), role, h.getId(), MODULES, null, "HOSPITAL", null,
                u.getTokenVersion());
    }

    private Doctor doctor(Hospital h) {
        Doctor d = new Doctor();
        d.setHospitalId(h.getId());
        d.setName("Dr Kulkarni");
        d.setEmail("dr." + uniq() + "@pal.test");
        d.setPublicId("dpub-" + uniq());
        d.setPhone(String.format("98%08d", uniq() % 100_000_000L));
        d.setSpecialization("General");
        d.setIsActive(true);
        return doctors.save(d);
    }

    private Patient patient(Hospital h, String name) {
        Patient p = new Patient();
        p.setHospitalId(h.getId());
        p.setName(name);
        p.setPublicId("ppub-" + uniq());
        p.setGender("MALE");
        p.setPhone(String.format("97%08d", uniq() % 100_000_000L));
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        p.setIsActive(true);
        return patients.save(p);
    }

    private IpdAdmission admission(Patient p) {
        Ward w = new Ward();
        w.setWardName("General Ward");
        w.setHospitalId(p.getHospitalId());
        w.setBedPrice(new BigDecimal("1000"));
        w.setTotalBeds(2);
        w = wards.save(w);
        IpdAdmission a = new IpdAdmission();
        a.setIpdNumber("IPD-A" + uniq());
        a.setPatientId(p.getId());
        a.setDoctorId(dr.getId());
        a.setHospitalId(p.getHospitalId());
        a.setAdmissionType("PLANNED");
        a.setStatus("ADMITTED");
        a.setAdmissionDatetime(LocalDateTime.now().minusDays(1));
        a.setWardId(w.getWardId());
        a.setBedId(1L);
        return admissions.save(a);
    }

    @BeforeEach
    void setUp() {
        hospital = newHospital("Access Log Hospital");
        dr = doctor(hospital);
        adminEmail = "admin." + uniq() + "@pal.test";
        receptionEmail = "rec." + uniq() + "@pal.test";
        admin = token(hospital, "HOSPITAL_ADMIN", adminEmail);
        reception = token(hospital, "RECEPTIONIST", receptionEmail);
        doctor = token(hospital, "DOCTOR", dr.getEmail());
        pharmacist = token(hospital, "PHARMACIST", "ph." + uniq() + "@pal.test");
    }

    private ResponseEntity<String> call(String url, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private JsonNode ok(String url, String token) throws Exception {
        ResponseEntity<String> res = call(url, token);
        assertThat(res.getStatusCode()).as(url + " → " + res.getBody()).isEqualTo(HttpStatus.OK);
        return json.readTree(res.getBody());
    }

    private List<PatientAccessLog> logsOf(Patient p) {
        return accessLogs.findByHospitalIdAndPatientIdOrderByAccessedAtDesc(p.getHospitalId(), p.getId(),
                Pageable.unpaged()).getContent();
    }

    @Test
    void openingThePatientFileIsLoggedWithWhoAndWhatAndTheAdminCanReadIt() throws Exception {
        Patient p = patient(hospital, "Vijay Patil");

        ok("/hospital/patients/" + p.getPublicId() + "/file", reception);

        List<PatientAccessLog> rows = logsOf(p);
        assertThat(rows).hasSize(1);
        PatientAccessLog row = rows.get(0);
        assertThat(row.getRecordType()).isEqualTo("PATIENT_FILE");
        assertThat(row.getAction()).isEqualTo("VIEW");
        assertThat(row.getUserEmail()).isEqualTo(receptionEmail);
        assertThat(row.getUserRole()).isEqualTo("RECEPTIONIST");
        assertThat(row.getAccessedAt()).isNotNull();

        JsonNode log = ok("/hospital/patients/" + p.getPublicId() + "/access-log", admin);
        assertThat(log.path("totalElements").asLong()).isEqualTo(1);
        JsonNode first = log.path("content").get(0);
        assertThat(first.path("userName").asText()).isEqualTo("Staff RECEPTIONIST");
        assertThat(first.path("userRole").asText()).isEqualTo("RECEPTIONIST");
        assertThat(first.path("recordType").asText()).isEqualTo("PATIENT_FILE");

        // reading the access log is not itself an access of the patient's record
        assertThat(logsOf(p)).hasSize(1);
    }

    @Test
    void reopeningTheSameRecordAMomentLaterIsOneEntry() throws Exception {
        Patient p = patient(hospital, "Repeat Viewer");
        ok("/hospital/patients/" + p.getPublicId() + "/file", doctor);
        ok("/hospital/patients/" + p.getPublicId() + "/file", doctor);
        ok("/hospital/patients/" + p.getPublicId() + "/file", doctor);
        assertThat(logsOf(p)).hasSize(1);

        // a different person, or a different record, is a new entry
        ok("/hospital/patients/" + p.getPublicId() + "/file", admin);
        ok("/hospital/patients/" + p.getPublicId(), doctor);
        assertThat(logsOf(p)).extracting(PatientAccessLog::getRecordType)
                .containsExactlyInAnyOrder("PATIENT_FILE", "PATIENT_FILE", "PATIENT_DETAILS");
    }

    @Test
    void recordsOpenedByOtherIdsAreTracedBackToThePatient() throws Exception {
        Patient p = patient(hospital, "Traced Patient");
        IpdAdmission a = admission(p);
        Opd o = new Opd();
        o.setPatient(p);
        o.setDoctor(dr);
        o.setProblem("Headache");
        o.setStatus(Opd.Status.COMPLETED);
        o = opds.save(o);

        ok("/hospital/ipd/" + a.getId(), doctor);
        ok("/hospital/opd/OPD-" + o.getId(), reception);

        assertThat(logsOf(p)).extracting(PatientAccessLog::getRecordType, PatientAccessLog::getRecordRef)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("IPD_CASE", String.valueOf(a.getId())),
                        org.assertj.core.groups.Tuple.tuple("OPD_VISIT", "OPD-" + o.getId()));
    }

    @Test
    void aRefusedRequestIsNotLoggedAsAnAccess() {
        Patient p = patient(hospital, "Refused Patient");
        assertThat(call("/hospital/patients/" + p.getPublicId() + "/file", pharmacist).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(logsOf(p)).isEmpty();
    }

    @Test
    void anotherHospitalCannotOpenTheRecordAndLeavesNoEntry() {
        Patient p = patient(hospital, "Home Patient");
        Hospital other = newHospital("Other Hospital");
        String otherAdmin = token(other, "HOSPITAL_ADMIN", "oadmin." + uniq() + "@pal.test");

        assertThat(call("/hospital/patients/" + p.getPublicId() + "/file", otherAdmin).getStatusCode().is2xxSuccessful())
                .isFalse();
        assertThat(call("/hospital/patients/" + p.getPublicId() + "/access-log", otherAdmin).getStatusCode()
                .is2xxSuccessful()).isFalse();
        assertThat(logsOf(p)).isEmpty();
        assertThat(accessLogs.findAll()).noneMatch(l -> other.getId().equals(l.getHospitalId()));
    }

    @Test
    void onlyTheHospitalAdminCanReadTheAccessLog() {
        Patient p = patient(hospital, "Private Log");
        assertThat(call("/hospital/patients/" + p.getPublicId() + "/access-log", reception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/hospital/patients/" + p.getPublicId() + "/access-log", doctor).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theAdminCanStillReadTheAccessLogOfADeletedPatient() throws Exception {
        Patient p = patient(hospital, "Deleted Later");
        ok("/hospital/patients/" + p.getPublicId() + "/file", doctor);
        p.setIsActive(false);
        patients.save(p);

        JsonNode log = ok("/hospital/patients/" + p.getPublicId() + "/access-log", admin);
        assertThat(log.path("content")).hasSize(1);
        assertThat(log.path("content").get(0).path("userRole").asText()).isEqualTo("DOCTOR");
    }
}
