package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Bed;
import com.hms.entity.Billing;
import com.hms.entity.BillingPayment;
import com.hms.entity.Doctor;
import com.hms.entity.Hospital;
import com.hms.entity.IpdAdmission;
import com.hms.entity.IpdBedHistory;
import com.hms.entity.MedicationAdministration;
import com.hms.entity.Opd;
import com.hms.entity.Patient;
import com.hms.entity.User;
import com.hms.entity.Ward;
import com.hms.repository.BedRepository;
import com.hms.repository.BillingPaymentRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.IpdBedHistoryRepository;
import com.hms.repository.MedicationAdministrationRepository;
import com.hms.repository.OpdRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.WardRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IPD phase D: the doctor's rounds as one timeline the ward can read, investigations for
 * inpatients (order, sample, result, cancel), diet orders and the kitchen's diet sheet — and who
 * may do each (a staff nurse only for a patient assigned to them).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IpdCareApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired UserRepository userRepository;
    @Autowired WardRepository wardRepository;
    @Autowired BedRepository bedRepository;
    @Autowired IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired IpdBedHistoryRepository bedHistoryRepository;
    @Autowired BillingRepository billingRepository;
    @Autowired BillingPaymentRepository billingPaymentRepository;
    @Autowired MedicationAdministrationRepository medicationAdministrationRepository;
    @Autowired OpdRepository opdRepository;
    @Autowired com.hms.repository.PatientNurseAssignmentRepository assignmentRepository;

    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING");
    private final ObjectMapper json = new ObjectMapper();

    private long hospitalId;
    private Long doctorId;
    private Long wardId;
    private String receptionToken;
    private String doctorToken;
    private String nurseToken;
    private Long nurseUserId;

    private String uniq() { return Long.toString(System.nanoTime()); }

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("Cancel test " + uniq()); h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE"); h.setIsActive(true);
        h.setModules(MODULES); h.setIsSingleDoctor(false);
        hospitalId = hospitalRepository.save(h).getId();

        Doctor d = new Doctor();
        d.setName("Dr Cancel"); d.setHospitalId(hospitalId); d.setIsActive(true);
        d.setEmail("doc-" + uniq() + "@example.test"); d.setPublicId("dpub-" + uniq());
        d.setPhone("9800000001"); d.setSpecialization("Gen");
        d = doctorRepository.save(d);
        doctorId = d.getId();

        Ward w = new Ward();
        w.setWardName("General " + uniq()); w.setHospitalId(hospitalId);
        w.setBedPrice(new BigDecimal("1500")); w.setTotalBeds(3);
        wardId = wardRepository.save(w).getWardId();

        receptionToken = token("RECEPTIONIST", "rec-" + uniq() + "@example.test");
        doctorToken = token("DOCTOR", d.getEmail());
        nurseToken = token("NURSE", "nurse-" + uniq() + "@example.test");
        nurseUserId = userRepository.findByEmail(lastNurseEmail).orElseThrow().getId();
    }

    private String lastNurseEmail;

    private String token(String role, String email) {
        if ("NURSE".equals(role)) lastNurseEmail = email;
        User u = new User();
        u.setEmail(email); u.setPassword("test-password-hash"); u.setName(role);
        u.setRole(role); u.setHospitalId(hospitalId); u.setIsActive(true); u.setTokenVersion(0);
        u = userRepository.save(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), u.getRole(), hospitalId,
                MODULES, null, "HOSPITAL", null, u.getTokenVersion());
    }

    private Long newBed() {
        Bed b = new Bed();
        b.setHospitalId(hospitalId); b.setWardId(wardId); b.setBedCode("B-" + uniq()); b.setStatus("available");
        return bedRepository.save(b).getBedId();
    }

    private Patient newPatient() {
        Patient p = new Patient();
        p.setName("Patient " + uniq()); p.setHospitalId(hospitalId); p.setPublicId("ppub-" + uniq());
        p.setGender("MALE"); p.setPhone("9900000001"); p.setIsActive(true);
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        return patientRepository.save(p);
    }

    private ResponseEntity<String> post(String token, String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    /** Admits through the real endpoint and returns the admission it created. */
    private IpdAdmission admit(Long patientId, Long opdId, Long bedId) throws Exception {
        String body = "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId + ",\"wardId\":" + wardId
                + ",\"bedId\":" + bedId + (opdId == null ? "" : ",\"opdId\":" + opdId)
                + ",\"admissionType\":\"ELECTIVE\",\"primaryDiagnosis\":\"Fever\"}";
        ResponseEntity<String> res = post(receptionToken, "/hospital/ipd/admit", body);
        assertThat(res.getStatusCode().value()).as(res.getBody()).isEqualTo(200);
        JsonNode node = json.readTree(res.getBody());
        return ipdAdmissionRepository.findById(node.get("id").asLong()).orElseThrow();
    }

    private ResponseEntity<String> get(String token, String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private void assignNurse(IpdAdmission ipd) {
        com.hms.entity.PatientNurseAssignment asg = new com.hms.entity.PatientNurseAssignment();
        asg.setPublicId(java.util.UUID.randomUUID().toString());
        asg.setHospitalId(hospitalId);
        asg.setIpdAdmissionId(ipd.getId());
        asg.setPatientId(ipd.getPatientId());
        asg.setNurseUserId(nurseUserId);
        asg.setAssignedByUserId(nurseUserId);
        asg.setAssignedAt(LocalDateTime.now());
        asg.setIsActive(true);
        assignmentRepository.save(asg);
    }

    @Test
    void roundsAreOneTimelineWithTime_andTheAssignedNurseReadsIt() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/followup",
                "{\"diagnosis\":\"Dengue\",\"notes\":\"Platelets rising\"}").getStatusCode().value()).isEqualTo(200);

        assertThat(get(nurseToken, "/hospital/ipd/" + ipd.getId() + "/rounds").getStatusCode().value())
                .as("not her patient").isEqualTo(403);
        assignNurse(ipd);
        ResponseEntity<String> rounds = get(nurseToken, "/hospital/ipd/" + ipd.getId() + "/rounds");
        assertThat(rounds.getStatusCode().value()).isEqualTo(200);
        JsonNode first = json.readTree(rounds.getBody()).get(0);
        assertThat(first.get("notes").asText()).isEqualTo("Platelets rising");
        assertThat(first.get("doctor").asText()).contains("Cancel");
        assertThat(first.get("at").asText()).as("date and time").contains("T");
    }

    @Test
    void anInvestigationGoesFromOrderToSampleToResult() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assignNurse(ipd);
        assertThat(post(nurseToken, "/hospital/ipd/" + ipd.getId() + "/investigations", "{\"tests\":[\"CBC\"]}")
                .getStatusCode().value()).as("only the doctor orders").isEqualTo(403);
        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/investigations", "{\"tests\":[\" \"]}")
                .getStatusCode().value()).isEqualTo(400);

        ResponseEntity<String> ordered = post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/investigations",
                "{\"tests\":[\"CBC\",\"LFT\"],\"priority\":\"URGENT\",\"notes\":\"Fasting\"}");
        assertThat(ordered.getStatusCode().value()).as(ordered.getBody()).isEqualTo(200);
        JsonNode list = json.readTree(ordered.getBody());
        assertThat(list).hasSize(2);
        String cbc = list.get(0).get("publicId").asText();
        String lft = list.get(1).get("publicId").asText();

        assertThat(post(nurseToken, "/hospital/ipd/investigations/" + cbc + "/sample", "{}").getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> result = post(nurseToken, "/hospital/ipd/investigations/" + cbc + "/result",
                "{\"result\":\"Hb 11.2, platelets 1.4 lakh\"}");
        assertThat(result.getStatusCode().value()).as(result.getBody()).isEqualTo(200);
        assertThat(json.readTree(result.getBody()).get("status").asText()).isEqualTo("COMPLETED");

        assertThat(post(nurseToken, "/hospital/ipd/investigations/" + lft + "/cancel", "{\"reason\":\"x\"}").getStatusCode().value())
                .as("only the doctor cancels").isEqualTo(403);
        assertThat(post(doctorToken, "/hospital/ipd/investigations/" + lft + "/cancel", "{\"reason\":\"Not needed\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(post(doctorToken, "/hospital/ipd/investigations/" + cbc + "/cancel", "{\"reason\":\"x\"}")
                .getStatusCode().value()).as("a completed test stays").isEqualTo(400);

        JsonNode all = json.readTree(get(receptionToken, "/hospital/ipd/" + ipd.getId() + "/investigations").getBody());
        assertThat(all).hasSize(2);
    }

    @Test
    void aResultCanBeRecordedAfterDischarge() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        String id = json.readTree(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/investigations",
                "{\"tests\":[\"Culture\"]}").getBody()).get(0).get("publicId").asText();
        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/plan-discharge",
                "{\"finalDiagnosis\":\"x\",\"treatmentGiven\":\"y\",\"conditionAtDischarge\":\"STABLE\"}").getStatusCode().value()).isEqualTo(200);
        // The stay's bed charge is settled first, as any discharge requires.
        assertThat(post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/advance", "{\"amount\":1500,\"mode\":\"CASH\"}")
                .getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> discharged = post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{}");
        assertThat(discharged.getStatusCode().value()).as(discharged.getBody()).isEqualTo(200);

        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/investigations", "{\"tests\":[\"CBC\"]}")
                .getStatusCode().value()).as("no new orders after discharge").isEqualTo(400);
        assertThat(post(receptionToken, "/hospital/ipd/investigations/" + id + "/result", "{\"result\":\"No growth\"}")
                .getStatusCode().value()).as("the late report is still recorded").isEqualTo(200);
    }

    @Test
    void theDietKeepsItsHistory_andTheSheetShowsTheCurrentOne() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        String path = "/hospital/ipd/" + ipd.getId() + "/diet";
        assertThat(post(receptionToken, path, "{\"dietType\":\"SOFT\"}").getStatusCode().value()).isEqualTo(403);
        assertThat(post(doctorToken, path, "{\"dietType\":\"OTHER\"}").getStatusCode().value())
                .as("OTHER needs a description").isEqualTo(400);
        assertThat(post(doctorToken, path, "{\"dietType\":\"NBM\",\"instructions\":\"Surgery at 8\"}").getStatusCode().value()).isEqualTo(200);
        assertThat(post(doctorToken, path, "{\"dietType\":\"diabetic\",\"instructions\":\"1800 kcal\"}").getStatusCode().value()).isEqualTo(200);

        JsonNode history = json.readTree(get(doctorToken, path).getBody());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("dietType").asText()).isEqualTo("DIABETIC");
        assertThat(history.get(0).get("endedAt").isNull()).isTrue();
        assertThat(history.get(1).get("endedAt").isNull()).as("the NBM order was ended").isFalse();

        JsonNode sheet = json.readTree(get(receptionToken, "/hospital/ipd/diet-sheet?wardId=" + wardId).getBody());
        assertThat(sheet).anySatisfy(r -> {
            assertThat(r.get("ipdNumber").asText()).isEqualTo(ipd.getIpdNumber());
            assertThat(r.get("dietType").asText()).isEqualTo("DIABETIC");
        });
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(receptionToken);
        ResponseEntity<byte[]> pdf = rest.exchange("/hospital/ipd/diet-sheet/pdf", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(pdf.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(pdf.getBody(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(get(nurseToken, "/hospital/ipd/diet-sheet").getStatusCode().value()).as("staff nurse: no hospital-wide sheet").isEqualTo(403);
    }
}
