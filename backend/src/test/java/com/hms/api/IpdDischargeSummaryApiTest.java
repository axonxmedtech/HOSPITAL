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
import com.hms.repository.AppointmentRepository;
import com.hms.repository.BedRepository;
import com.hms.repository.DischargeSummaryRepository;
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
 * IPD phase B: the discharge summary — what it must contain, editing it while the discharge is
 * planned, the printable PDF, and the follow-up visit booked when the patient goes home.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IpdDischargeSummaryApiTest {

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
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired DischargeSummaryRepository dischargeSummaryRepository;

    private static final List<String> MODULES = List.of("OPD", "IPD", "APPOINTMENTS");
    private final ObjectMapper json = new ObjectMapper();

    private long hospitalId;
    private Long doctorId;
    private Long wardId;
    private String receptionToken;
    private String doctorToken;

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
    }

    private String token(String role, String email) {
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

    private static final String FULL = "\"finalDiagnosis\":\"Dengue fever\",\"treatmentGiven\":\"IV fluids, monitoring\","
            + "\"conditionAtDischarge\":\"IMPROVED\",\"advice\":\"Plenty of fluids\","
            + "\"dischargeMedications\":[{\"name\":\"Paracetamol 500\",\"dose\":\"1 tab\",\"frequency\":\"1-0-1\",\"durationDays\":5}]";

    private ResponseEntity<String> plan(Long ipdId, String fields) {
        return post(doctorToken, "/hospital/ipd/" + ipdId + "/plan-discharge", "{" + fields + "}");
    }

    private ResponseEntity<byte[]> pdf(Long ipdId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(receptionToken);
        return rest.exchange("/hospital/ipd/" + ipdId + "/discharge-summary/pdf", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
    }

    @Test
    void theSummaryNeedsDiagnosisCourseAndCondition() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assertThat(plan(ipd.getId(), "\"treatmentGiven\":\"x\",\"conditionAtDischarge\":\"STABLE\"").getBody()).contains("final diagnosis");
        assertThat(plan(ipd.getId(), "\"finalDiagnosis\":\"x\",\"conditionAtDischarge\":\"STABLE\"").getBody()).contains("course in hospital");
        assertThat(plan(ipd.getId(), "\"finalDiagnosis\":\"x\",\"treatmentGiven\":\"x\"").getBody()).contains("condition at discharge");
        ResponseEntity<String> nameless = plan(ipd.getId(), FULL.replace("Paracetamol 500", " "));
        assertThat(nameless.getStatusCode().value()).isEqualTo(400);
        assertThat(ipdAdmissionRepository.findById(ipd.getId()).orElseThrow().getStatus()).isEqualTo("ADMITTED");

        // A death needs no "condition at discharge".
        ResponseEntity<String> expired = plan(ipd.getId(), "\"finalDiagnosis\":\"x\",\"treatmentGiven\":\"x\","
                + "\"dischargeType\":\"EXPIRED\",\"dischargeTypeNote\":\"10:40, cardiac arrest\"");
        assertThat(expired.getStatusCode().value()).as(expired.getBody()).isEqualTo(200);
    }

    @Test
    void aPlannedSummaryCanBeEditedAndPrinted() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assertThat(pdf(ipd.getId()).getStatusCode().value()).as("nothing to print before the plan").isEqualTo(400);

        assertThat(plan(ipd.getId(), FULL).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> edited = plan(ipd.getId(), FULL.replace("Plenty of fluids", "Rest for a week"));
        assertThat(edited.getStatusCode().value()).as("the doctor corrects it before reception confirms").isEqualTo(200);
        assertThat(dischargeSummaryRepository.findByIpdAdmissionId(ipd.getId()).orElseThrow().getAdvice()).isEqualTo("Rest for a week");

        ResponseEntity<String> details = rest.exchange("/hospital/ipd/" + ipd.getId(), HttpMethod.GET,
                new HttpEntity<>(authOnly(doctorToken)), String.class);
        JsonNode summary = json.readTree(details.getBody()).get("dischargeSummary");
        assertThat(summary.get("conditionAtDischarge").asText()).isEqualTo("IMPROVED");
        assertThat(summary.get("dischargeMedications").get(0).get("name").asText()).isEqualTo("Paracetamol 500");

        ResponseEntity<byte[]> printed = pdf(ipd.getId());
        assertThat(printed.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(printed.getBody(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void goingHomeBooksTheFollowUp_inTheNextFreeSlot() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        LocalDate date = LocalDate.now().plusDays(7);
        // 10:00 is already taken for this doctor, so the visit goes to 10:15.
        com.hms.entity.Appointment busy = new com.hms.entity.Appointment();
        busy.setHospitalId(hospitalId); busy.setPatientId(newPatient().getId()); busy.setDoctorId(doctorId);
        busy.setAppointmentDate(date); busy.setAppointmentTime(java.time.LocalTime.of(10, 0));
        busy.setStatus("SCHEDULED"); busy.setIsActive(true);
        appointmentRepository.save(busy);

        assertThat(plan(ipd.getId(), FULL + ",\"followUpDate\":\"" + date + "\",\"followUpTime\":\"10:00\"").getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> done = post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{}");
        assertThat(done.getStatusCode().value()).as(done.getBody()).isEqualTo(200);

        Long apptId = dischargeSummaryRepository.findByIpdAdmissionId(ipd.getId()).orElseThrow().getFollowUpAppointmentId();
        assertThat(apptId).as("the follow-up was booked").isNotNull();
        com.hms.entity.Appointment appt = appointmentRepository.findById(apptId).orElseThrow();
        assertThat(appt.getPatientId()).isEqualTo(ipd.getPatientId());
        assertThat(appt.getDoctorId()).isEqualTo(doctorId);
        assertThat(appt.getAppointmentDate()).isEqualTo(date);
        assertThat(appt.getAppointmentTime()).isEqualTo(java.time.LocalTime.of(10, 15));
        assertThat(pdf(ipd.getId()).getStatusCode().value()).as("still printable after discharge").isEqualTo(200);
    }

    @Test
    void aLamaDischargeBooksNothing() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assertThat(plan(ipd.getId(), FULL + ",\"dischargeType\":\"LAMA\",\"followUpDate\":\"" + LocalDate.now().plusDays(3) + "\"")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{}").getStatusCode().value()).isEqualTo(200);
        assertThat(dischargeSummaryRepository.findByIpdAdmissionId(ipd.getId()).orElseThrow().getFollowUpAppointmentId()).isNull();
    }

    @Test
    void theDeskPrintsTheSummaryAndBillOnce_andCanFreeTheBedFromTheCase() throws Exception {
        Long bedId = newBed();
        IpdAdmission ipd = admit(newPatient().getId(), null, bedId);
        assertThat(plan(ipd.getId(), FULL).getStatusCode().value()).isEqualTo(200);
        assertThat(post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{}").getStatusCode().value()).isEqualTo(200);

        ResponseEntity<byte[]> pack = rest.exchange("/hospital/ipd/" + ipd.getId() + "/discharge-pack/pdf", HttpMethod.GET,
                new HttpEntity<>(authOnly(receptionToken)), byte[].class);
        assertThat(pack.getStatusCode().value()).isEqualTo(200);
        int packPages = new com.lowagie.text.pdf.PdfReader(pack.getBody()).getNumberOfPages();
        int summaryPages = new com.lowagie.text.pdf.PdfReader(pdf(ipd.getId()).getBody()).getNumberOfPages();
        assertThat(packPages).as("no BILLING module here, so the pack is the summary alone").isEqualTo(summaryPages);

        JsonNode admission = json.readTree(rest.exchange("/hospital/ipd/" + ipd.getId(), HttpMethod.GET,
                new HttpEntity<>(authOnly(receptionToken)), String.class).getBody()).get("admission");
        assertThat(admission.get("bedId").asLong()).isEqualTo(bedId);
        assertThat(admission.get("bedStatus").asText()).as("the vacated bed waits for cleaning").isEqualTo("cleaning");
    }

    private HttpHeaders authOnly(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
