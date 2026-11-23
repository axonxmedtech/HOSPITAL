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
 * IPD phase A: cancelling an admission made by mistake, and IPD numbers that run per hospital.
 * Driven through the real admit and cancel endpoints, so the bed, the bill, the bed history and
 * the doctor's request are the ones the admission really created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IpdCancelAdmissionApiTest {

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

    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING");
    private final ObjectMapper json = new ObjectMapper();

    private long hospitalId;
    private Long doctorId;
    private Long wardId;
    private String receptionToken;
    private String adminToken;
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
        adminToken = token("HOSPITAL_ADMIN", "admin-" + uniq() + "@example.test");
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

    private ResponseEntity<String> cancel(String token, Long ipdId, String body) {
        return post(token, "/hospital/ipd/" + ipdId + "/cancel", body);
    }

    private Billing ipdBill(Long ipdId) {
        return billingRepository.findByIpdAdmissionId(ipdId).get(0);
    }

    @Test
    void receptionCancelsAMistakenAdmission_freeingTheBedAndVoidingTheBill() throws Exception {
        Long bedId = newBed();
        IpdAdmission ipd = admit(newPatient().getId(), null, bedId);

        ResponseEntity<String> res = cancel(receptionToken, ipd.getId(), "{\"reason\":\"Admitted under the wrong patient\"}");
        assertThat(res.getStatusCode().value()).as(res.getBody()).isEqualTo(200);

        IpdAdmission after = ipdAdmissionRepository.findById(ipd.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("CANCELLED");
        assertThat(after.getCancelReason()).isEqualTo("Admitted under the wrong patient");
        assertThat(after.getCancelledAt()).isNotNull();
        assertThat(after.getCancelledBy()).contains("RECEPTIONIST");

        assertThat(bedRepository.findById(bedId).orElseThrow().getStatus())
                .as("the bed goes for cleaning, then back into use").isEqualTo("cleaning");
        assertThat(ipdBill(ipd.getId()).getPaymentStatus()).isEqualTo("CANCELLED");
        List<IpdBedHistory> history = bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(ipd.getId());
        assertThat(history).allSatisfy(hh -> assertThat(hh.getReleasedAt()).isNotNull());
    }

    @Test
    void theDoctorsRequestComesBackUnderRequested() throws Exception {
        Patient patient = newPatient();
        Opd opd = new Opd();
        opd.setPatient(patient);
        opd.setDoctor(doctorRepository.findById(doctorId).orElseThrow());
        opd.setVisitType(Opd.VisitType.NEW);
        opd.setStatus(Opd.Status.COMPLETED);
        opd.setIpdAdmitRecommended(true);
        opd = opdRepository.save(opd);

        IpdAdmission ipd = admit(patient.getId(), opd.getId(), newBed());
        assertThat(opdRepository.findById(opd.getId()).orElseThrow().getStatus()).isEqualTo(Opd.Status.IN_IPD);

        assertThat(cancel(receptionToken, ipd.getId(), "{\"reason\":\"Wrong bed\"}").getStatusCode().value()).isEqualTo(200);
        assertThat(opdRepository.findById(opd.getId()).orElseThrow().getStatus())
                .as("the request is pending again, so the patient can be admitted correctly")
                .isEqualTo(Opd.Status.COMPLETED);

        // ... and the same patient can now be admitted again.
        IpdAdmission again = admit(patient.getId(), opd.getId(), newBed());
        assertThat(again.getStatus()).isEqualTo("ADMITTED");
    }

    @Test
    void aReasonIsRequired() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assertThat(cancel(receptionToken, ipd.getId(), "{\"reason\":\"  \"}").getStatusCode().value()).isEqualTo(400);
        assertThat(ipdAdmissionRepository.findById(ipd.getId()).orElseThrow().getStatus()).isEqualTo("ADMITTED");
    }

    @Test
    void moneyCollected_onlyTheAdminCancels_andItIsRefunded() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        Billing bill = ipdBill(ipd.getId());
        BillingPayment pay = new BillingPayment();
        pay.setBillingId(bill.getId()); pay.setHospitalId(hospitalId);
        pay.setAmount(new BigDecimal("500")); pay.setMode("CASH"); pay.setKind("PAYMENT");
        pay.setRecordedBy("test");
        billingPaymentRepository.save(pay);

        ResponseEntity<String> byReception = cancel(receptionToken, ipd.getId(), "{\"reason\":\"Duplicate\"}");
        assertThat(byReception.getStatusCode().value()).as(byReception.getBody()).isEqualTo(403);

        ResponseEntity<String> noMode = cancel(adminToken, ipd.getId(), "{\"reason\":\"Duplicate\"}");
        assertThat(noMode.getStatusCode().value()).as("the admin must say how the money goes back").isEqualTo(400);

        ResponseEntity<String> byAdmin = cancel(adminToken, ipd.getId(), "{\"reason\":\"Duplicate\",\"refundMode\":\"UPI\"}");
        assertThat(byAdmin.getStatusCode().value()).as(byAdmin.getBody()).isEqualTo(200);

        BigDecimal net = billingPaymentRepository.findByBillingId(bill.getId()).stream()
                .map(BillingPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(net).as("everything collected was refunded").isEqualByComparingTo("0");
        assertThat(ipdBill(ipd.getId()).getPaymentStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void aDoseGivenFromStockBlocksCancelling() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        MedicationAdministration dose = new MedicationAdministration();
        dose.setHospitalId(hospitalId); dose.setIpdAdmissionId(ipd.getId()); dose.setPatientId(ipd.getPatientId());
        dose.setPrescriptionId(1L); dose.setNurseUserId(1L); dose.setStatus("GIVEN"); dose.setOwnSupply(false);
        dose.setAdministeredTime(LocalDateTime.now()); dose.setIsActive(true);
        medicationAdministrationRepository.save(dose);

        ResponseEntity<String> res = cancel(receptionToken, ipd.getId(), "{\"reason\":\"Wrong patient\"}");
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody()).contains("medication chart");
    }

    @Test
    void aPlannedDischargeIsARealStay_andIsNotCancelled() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        ipd.setStatus("DISCHARGE_PLANNED");
        ipdAdmissionRepository.save(ipd);

        ResponseEntity<String> res = cancel(receptionToken, ipd.getId(), "{\"reason\":\"Oops\"}");
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody()).contains("Discharge them instead");
    }

    @Test
    void aDoctorOutsideSoloModeCannotCancel() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assertThat(cancel(doctorToken, ipd.getId(), "{\"reason\":\"Oops\"}").getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void aNewHospitalNumbersItsOwnAdmissionsFromOne() throws Exception {
        IpdAdmission first = admit(newPatient().getId(), null, newBed());
        IpdAdmission second = admit(newPatient().getId(), null, newBed());
        assertThat(first.getIpdNumber()).as("other hospitals' admissions don't count").isEqualTo("IPD-1");
        assertThat(second.getIpdNumber()).isEqualTo("IPD-2");
    }
}
