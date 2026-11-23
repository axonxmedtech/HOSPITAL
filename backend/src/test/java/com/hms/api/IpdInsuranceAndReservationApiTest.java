package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Bed;
import com.hms.entity.BedReservation;
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
 * IPD phase E: bed reservations (only the reserved patient goes into a held bed; an expired hold
 * is ignored) and insurance / TPA claims (the claim steps, the patient paying only their share
 * at discharge, the bill kept open for the insurer, and the settlement posted to it).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IpdInsuranceAndReservationApiTest {

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
    @Autowired com.hms.repository.BedReservationRepository reservationRepository;

    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING");
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
    private IpdAdmission admit(Long patientId, Long bedId) throws Exception {
        ResponseEntity<String> res = admitRaw(patientId, bedId, "");
        assertThat(res.getStatusCode().value()).as(res.getBody()).isEqualTo(200);
        JsonNode node = json.readTree(res.getBody());
        return ipdAdmissionRepository.findById(node.get("id").asLong()).orElseThrow();
    }

    private ResponseEntity<String> admitRaw(Long patientId, Long bedId, String extra) {
        String body = "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId + ",\"wardId\":" + wardId
                + ",\"bedId\":" + bedId + ",\"admissionType\":\"ELECTIVE\",\"primaryDiagnosis\":\"Fever\"" + extra + "}";
        return post(receptionToken, "/hospital/ipd/admit", body);
    }

    private ResponseEntity<String> get(String token, String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private String reserve(Long bedId, String who) throws Exception {
        ResponseEntity<String> r = post(receptionToken, "/hospital/beds/reservations", "{\"bedId\":" + bedId + "," + who
                + ",\"expectedAt\":\"" + LocalDateTime.now().plusHours(2).withNano(0) + "\"}");
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        return json.readTree(r.getBody()).get("publicId").asText();
    }

    private Billing ipdBill(Long ipdId) {
        return billingRepository.findByIpdAdmissionId(ipdId).get(0);
    }

    // ── bed reservations ────────────────────────────────────────────────────

    @Test
    void aReservedBedTakesOnlyThePatientItIsHeldFor() throws Exception {
        Patient expected = newPatient();
        Long bed = newBed();
        String id = reserve(bed, "\"patientId\":" + expected.getId());

        JsonNode beds = json.readTree(get(receptionToken, "/hospital/wards/" + wardId + "/beds").getBody());
        assertThat(beds).anySatisfy(b -> {
            assertThat(b.get("bedId").asLong()).isEqualTo(bed);
            assertThat(b.get("reservedFor").asText()).isEqualTo(expected.getName());
        });

        ResponseEntity<String> other = admitRaw(newPatient().getId(), bed, "");
        assertThat(other.getStatusCode().value()).isEqualTo(409);
        assertThat(other.getBody()).contains("reserved for " + expected.getName());

        IpdAdmission ipd = admit(expected.getId(), bed);
        assertThat(reservationRepository.findByPublicIdAndHospitalId(id, hospitalId).orElseThrow())
                .satisfies(r -> {
                    assertThat(r.getStatus()).isEqualTo("USED");
                    assertThat(r.getIpdAdmissionId()).isEqualTo(ipd.getId());
                });
    }

    @Test
    void aHoldMadeByNameIsUsedWhenTheDeskSaysSo_andAnExpiredHoldIsIgnored() throws Exception {
        Long bed = newBed();
        String id = reserve(bed, "\"patientName\":\"Walk-in Ramesh\",\"patientPhone\":\"9000000000\"");
        assertThat(admitRaw(newPatient().getId(), bed, "").getStatusCode().value()).isEqualTo(409);
        assertThat(admitRaw(newPatient().getId(), bed, ",\"reservationId\":\"" + id + "\"").getStatusCode().value()).isEqualTo(200);

        Long bed2 = newBed();
        String id2 = reserve(bed2, "\"patientName\":\"No-show\"");
        BedReservation r = reservationRepository.findByPublicIdAndHospitalId(id2, hospitalId).orElseThrow();
        r.setReservedUntil(LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(r);
        assertThat(admitRaw(newPatient().getId(), bed2, "").getStatusCode().value()).as("an expired hold blocks nothing").isEqualTo(200);
    }

    @Test
    void onlyAFreeBedCanBeReserved_andTheDeskCanCancel() throws Exception {
        Long bed = newBed();
        admit(newPatient().getId(), bed);
        assertThat(post(receptionToken, "/hospital/beds/reservations", "{\"bedId\":" + bed + ",\"patientName\":\"X\",\"expectedAt\":\""
                + LocalDateTime.now().plusHours(1).withNano(0) + "\"}").getStatusCode().value()).isEqualTo(409);

        Long free = newBed();
        String id = reserve(free, "\"patientName\":\"Y\"");
        assertThat(post(receptionToken, "/hospital/beds/reservations/" + id + "/cancel", "{\"reason\":\"Surgery postponed\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(admitRaw(newPatient().getId(), free, "").getStatusCode().value()).isEqualTo(200);
    }

    // ── insurance / TPA ─────────────────────────────────────────────────────

    private String openClaim(Long ipdId) throws Exception {
        ResponseEntity<String> r = post(receptionToken, "/hospital/ipd/" + ipdId + "/insurance",
                "{\"payerType\":\"TPA\",\"insurerName\":\"Star Health\",\"tpaName\":\"Medi Assist\",\"policyNumber\":\"P-77\"}");
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        return json.readTree(r.getBody()).get("publicId").asText();
    }

    private ResponseEntity<String> step(String claim, String json) {
        return post(receptionToken, "/hospital/ipd/insurance/" + claim + "/step", json);
    }

    @Test
    void theClaimFollowsTheInsurersSteps() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), newBed());
        String claim = openClaim(ipd.getId());
        assertThat(post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/insurance", "{\"payerType\":\"TPA\",\"insurerName\":\"X\"}")
                .getStatusCode().value()).as("one open claim per stay").isEqualTo(400);
        assertThat(step(claim, "{\"status\":\"PREAUTH_APPROVED\",\"amount\":1000}").getStatusCode().value())
                .as("cannot approve before it is submitted").isEqualTo(400);
        assertThat(step(claim, "{\"status\":\"PREAUTH_SUBMITTED\",\"amount\":1500,\"claimNumber\":\"CL-1\"}").getStatusCode().value()).isEqualTo(200);
        assertThat(step(claim, "{\"status\":\"QUERY\"}").getStatusCode().value()).as("a query needs its text").isEqualTo(400);
        assertThat(step(claim, "{\"status\":\"PREAUTH_APPROVED\"}").getStatusCode().value()).as("an approval needs the amount").isEqualTo(400);
        assertThat(step(claim, "{\"status\":\"PREAUTH_APPROVED\",\"amount\":1000}").getStatusCode().value()).isEqualTo(200);

        JsonNode claims = json.readTree(get(doctorToken, "/hospital/ipd/" + ipd.getId() + "/insurance").getBody());
        assertThat(claims.get(0).get("approvedAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(claims.get(0).get("events")).hasSize(3);
        assertThat(json.readTree(get(receptionToken, "/hospital/ipd/insurance/open").getBody()))
                .anySatisfy(c -> assertThat(c.get("publicId").asText()).isEqualTo(claim));
    }

    @Test
    void cashless_thePatientPaysTheirShare_andTheInsurerSettlesLater() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), newBed()); // bill: 1500 bed
        String claim = openClaim(ipd.getId());
        step(claim, "{\"status\":\"PREAUTH_SUBMITTED\",\"amount\":1500}");
        step(claim, "{\"status\":\"PREAUTH_APPROVED\",\"amount\":1000}");

        // A 1000 deposit against a 500 share: 500 goes back at discharge.
        assertThat(post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/advance", "{\"amount\":1000,\"mode\":\"CASH\"}")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/plan-discharge",
                "{\"finalDiagnosis\":\"x\",\"treatmentGiven\":\"y\",\"conditionAtDischarge\":\"STABLE\"}").getStatusCode().value()).isEqualTo(200);
        assertThat(post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{}").getStatusCode().value())
                .as("the unused part of the deposit must be returned").isEqualTo(400);
        ResponseEntity<String> done = post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{\"refundMode\":\"CASH\"}");
        assertThat(done.getStatusCode().value()).as(done.getBody()).isEqualTo(200);

        Billing bill = ipdBill(ipd.getId());
        assertThat(bill.getPaymentStatus()).as("kept open for the insurer").isNotEqualTo("CLOSED");
        BigDecimal patientPaid = billingPaymentRepository.findByBillingId(bill.getId()).stream()
                .map(BillingPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(patientPaid).as("the patient paid exactly their share").isEqualByComparingTo("500");

        assertThat(step(claim, "{\"status\":\"FINAL_SUBMITTED\",\"amount\":1000}").getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> settled = post(receptionToken, "/hospital/ipd/insurance/" + claim + "/settle",
                "{\"amount\":900,\"deduction\":100,\"deductionReason\":\"Non-payable consumables\",\"reference\":\"NEFT-9\"}");
        assertThat(settled.getStatusCode().value()).as(settled.getBody()).isEqualTo(200);
        assertThat(billingPaymentRepository.findByBillingId(bill.getId()))
                .anySatisfy(p -> {
                    assertThat(p.getKind()).isEqualTo("INSURANCE");
                    assertThat(p.getAmount()).isEqualByComparingTo("900");
                });
        assertThat(ipdBill(ipd.getId()).getPaymentStatus()).as("the 100 deducted is now the patient's due").isNotEqualTo("CLOSED");
    }

    @Test
    void withoutAnApprovalTheBillMustBeSettledAsUsual() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), newBed());
        String claim = openClaim(ipd.getId());
        step(claim, "{\"status\":\"PREAUTH_SUBMITTED\"}");
        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/plan-discharge",
                "{\"finalDiagnosis\":\"x\",\"treatmentGiven\":\"y\",\"conditionAtDischarge\":\"STABLE\"}").getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> refused = post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{}");
        assertThat(refused.getStatusCode().value()).isEqualTo(400);
        assertThat(refused.getBody()).contains("Outstanding balance");
    }
}
