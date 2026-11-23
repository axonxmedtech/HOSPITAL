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
import com.hms.repository.BillingItemRepository;
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
 * IPD phase C: the bed-charge rule (calendar day / 24 hours with grace), nursing charges, the
 * minimum advance, advances and their refund at discharge, the doctor-visit fee and packages.
 * Time is moved by shifting the admission (and its bed history) into the past, then bringing the
 * bill up to date exactly as the night job and the bill screen do.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IpdBillingRulesApiTest {

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
    @Autowired BillingItemRepository billingItemRepository;
    @Autowired com.hms.service.hospital.BillingSchedulerService scheduler;

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
        w.setBedPrice(new BigDecimal("1500")); w.setNursingCharge(new BigDecimal("300")); w.setTotalBeds(3);
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
        return admit(patientId, opdId, bedId, "");
    }

    private ResponseEntity<String> admitRaw(String extra) {
        String body = "{\"patientId\":" + newPatient().getId() + ",\"doctorId\":" + doctorId + ",\"wardId\":" + wardId
                + ",\"bedId\":" + newBed() + ",\"primaryDiagnosis\":\"Fever\"" + extra + "}";
        return post(receptionToken, "/hospital/ipd/admit", body);
    }

    private IpdAdmission admit(Long patientId, Long opdId, Long bedId, String extra) throws Exception {
        String body = "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId + ",\"wardId\":" + wardId
                + ",\"bedId\":" + bedId + (opdId == null ? "" : ",\"opdId\":" + opdId)
                + ",\"admissionType\":\"ELECTIVE\",\"primaryDiagnosis\":\"Fever\"" + extra + "}";
        ResponseEntity<String> res = post(receptionToken, "/hospital/ipd/admit", body);
        assertThat(res.getStatusCode().value()).as(res.getBody()).isEqualTo(200);
        JsonNode node = json.readTree(res.getBody());
        return ipdAdmissionRepository.findById(node.get("id").asLong()).orElseThrow();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void rules(String json) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(adminToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = rest.exchange("/hospital/settings/ipd-billing", HttpMethod.PUT, new HttpEntity<>(json, h), String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
    }

    private Long newPackage(String name, String price, int days) throws Exception {
        ResponseEntity<String> r = post(adminToken, "/hospital/ipd/packages",
                "{\"name\":\"" + name + "\",\"price\":" + price + ",\"includedDays\":" + days + "}");
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        return json.readTree(r.getBody()).get("id").asLong();
    }

    /** Moves the admission (and its bed history) back in time, then brings its bill up to date. */
    private IpdAdmission shiftBack(IpdAdmission ipd, java.time.Duration ago) {
        IpdAdmission a = ipdAdmissionRepository.findById(ipd.getId()).orElseThrow();
        LocalDateTime at = LocalDateTime.now().minus(ago);
        a.setAdmissionDatetime(at);
        ipdAdmissionRepository.save(a);
        for (IpdBedHistory hh : bedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(a.getId())) {
            hh.setAssignedAt(at);
            bedHistoryRepository.save(hh);
        }
        scheduler.catchUpAdmission(a);
        return a;
    }

    private List<String> lines(Long ipdId) {
        return billingItemRepository.findByBillingId(ipdBill(ipdId).getId()).stream()
                .map(com.hms.entity.BillingItem::getDescription).toList();
    }

    private BigDecimal gross(Long ipdId) {
        return billingItemRepository.findByBillingId(ipdBill(ipdId).getId()).stream()
                .map(com.hms.entity.BillingItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Billing ipdBill(Long ipdId) {
        return billingRepository.findByIpdAdmissionId(ipdId).get(0);
    }

    private String plan(Long ipdId) {
        return "/hospital/ipd/" + ipdId + "/plan-discharge";
    }

    private static final String SUMMARY = "{\"finalDiagnosis\":\"Recovered\",\"treatmentGiven\":\"Treated\",\"conditionAtDischarge\":\"IMPROVED\"}";

    // ── bed-charge rule and nursing ─────────────────────────────────────────

    @Test
    void calendarDays_chargeEachDateTheStayTouches_withNursing() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assertThat(lines(ipd.getId())).as("day 1 at admission: bed and nursing")
                .containsExactlyInAnyOrder("Bed Price", "Nursing Charge - " + LocalDate.now());

        // In late yesterday evening: today is day 2 under the calendar rule.
        IpdAdmission a = shiftBack(ipd, java.time.Duration.ofHours(LocalDateTime.now().getHour() + 2L));
        LocalDate day1 = a.getAdmissionDatetime().toLocalDate();
        assertThat(lines(ipd.getId())).contains(
                "Daily Bed Charge - " + LocalDate.now(), "Nursing Charge - " + LocalDate.now(), "Nursing Charge - " + day1);
        int before = lines(ipd.getId()).size();
        scheduler.catchUpAdmission(a);
        assertThat(lines(ipd.getId())).as("a day is never charged twice").hasSize(before);
    }

    @Test
    void hours24_countsFromAdmission_withGrace() throws Exception {
        rules("{\"bedChargeRule\":\"HOURS_24\",\"graceHours\":4}");
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        assertThat(ipdAdmissionRepository.findById(ipd.getId()).orElseThrow().getBedChargeRule()).isEqualTo("HOURS_24");

        shiftBack(ipd, java.time.Duration.ofHours(26));
        assertThat(lines(ipd.getId())).as("26 h is within day 1 + 4 h grace").doesNotContain("Bed Charge - Day 2");

        shiftBack(ipd, java.time.Duration.ofHours(29));
        assertThat(lines(ipd.getId())).contains("Bed Charge - Day 2", "Nursing Charge - Day 2").doesNotContain("Bed Charge - Day 3");
    }

    @Test
    void changingTheRuleNeverRepricesAPatientAlreadyInBed() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        rules("{\"bedChargeRule\":\"HOURS_24\"}");
        assertThat(ipdAdmissionRepository.findById(ipd.getId()).orElseThrow().getBedChargeRule()).isEqualTo("CALENDAR_DAY");
    }

    // ── advances ────────────────────────────────────────────────────────────

    @Test
    void aPlannedAdmissionNeedsTheMinimumAdvance_anEmergencyDoesNot() throws Exception {
        rules("{\"minAdvance\":5000}");
        ResponseEntity<String> none = admitRaw(",\"admissionType\":\"ELECTIVE\"");
        assertThat(none.getStatusCode().value()).isEqualTo(400);
        assertThat(none.getBody()).contains("5000");
        assertThat(admitRaw(",\"admissionType\":\"ELECTIVE\",\"advanceAmount\":4999,\"advanceMode\":\"CASH\"").getStatusCode().value()).isEqualTo(400);
        assertThat(admitRaw(",\"admissionType\":\"EMERGENCY\"").getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> paid = admitRaw(",\"admissionType\":\"ELECTIVE\",\"advanceAmount\":5000,\"advanceMode\":\"CASH\"");
        assertThat(paid.getStatusCode().value()).as(paid.getBody()).isEqualTo(200);
        Long ipdId = json.readTree(paid.getBody()).get("id").asLong();
        List<BillingPayment> pays = billingPaymentRepository.findByBillingId(ipdBill(ipdId).getId());
        assertThat(pays).singleElement().satisfies(p -> {
            assertThat(p.getKind()).isEqualTo("ADVANCE");
            assertThat(p.getAmount()).isEqualByComparingTo("5000");
        });
    }

    @Test
    void anAdvanceMayExceedTheCharges_andTheRestIsReturnedAtDischarge() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        ResponseEntity<String> adv = post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/advance",
                "{\"amount\":10000,\"mode\":\"UPI\",\"reference\":\"UTR123\"}");
        assertThat(adv.getStatusCode().value()).as(adv.getBody()).isEqualTo(200);
        long paymentId = json.readTree(adv.getBody()).get("paymentId").asLong();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(receptionToken);
        ResponseEntity<byte[]> receipt = rest.exchange("/hospital/ipd/advances/" + paymentId + "/receipt/pdf",
                HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(receipt.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(receipt.getBody(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        assertThat(post(doctorToken, plan(ipd.getId()), SUMMARY).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> noMode = post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{}");
        assertThat(noMode.getStatusCode().value()).isEqualTo(400);
        assertThat(noMode.getBody()).contains("unused");

        ResponseEntity<String> done = post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/confirm-discharge", "{\"refundMode\":\"CASH\"}");
        assertThat(done.getStatusCode().value()).as(done.getBody()).isEqualTo(200);
        BigDecimal net = billingPaymentRepository.findByBillingId(ipdBill(ipd.getId()).getId()).stream()
                .map(BillingPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(net).as("kept exactly what the stay cost").isEqualByComparingTo(gross(ipd.getId()));
    }

    // ── doctor visits ───────────────────────────────────────────────────────

    @Test
    void aRoundIsChargedOncePerDoctorPerDay() throws Exception {
        rules("{\"doctorVisitFee\":500}");
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        String round = "{\"diagnosis\":\"Review\",\"notes\":\"Stable\"}";
        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/followup", round).getStatusCode().value()).isEqualTo(200);
        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/followup", round).getStatusCode().value()).isEqualTo(200);
        assertThat(lines(ipd.getId()).stream().filter(l -> l.startsWith("Doctor Visit - Day 1"))).hasSize(1);
    }

    // ── packages ────────────────────────────────────────────────────────────

    @Test
    void onlyTheAdminManagesPackages_andNamesAreUnique() throws Exception {
        assertThat(post(receptionToken, "/hospital/ipd/packages", "{\"name\":\"X\",\"price\":100,\"includedDays\":1}")
                .getStatusCode().value()).isEqualTo(403);
        newPackage("Cataract", "15000", 1);
        assertThat(post(adminToken, "/hospital/ipd/packages", "{\"name\":\"cataract\",\"price\":100,\"includedDays\":1}")
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void aPackageAtAdmissionCoversBedNursingAndVisitsForItsDays() throws Exception {
        rules("{\"doctorVisitFee\":500}");
        Long pkg = newPackage("Delivery " + uniq(), "20000", 3);
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed(), ",\"packageId\":" + pkg);
        assertThat(lines(ipd.getId())).singleElement().satisfies(l -> assertThat(l).startsWith("Package: Delivery"));

        assertThat(post(doctorToken, "/hospital/ipd/" + ipd.getId() + "/followup", "{\"diagnosis\":\"x\",\"notes\":\"y\"}")
                .getStatusCode().value()).isEqualTo(200);
        IpdAdmission a = shiftBack(ipd, java.time.Duration.ofDays(2));
        assertThat(lines(ipd.getId())).as("days 1-3 are inside the package").hasSize(1);

        shiftBack(a, java.time.Duration.ofDays(3));
        LocalDate day4 = LocalDate.now();
        assertThat(lines(ipd.getId())).contains("Daily Bed Charge - " + day4, "Nursing Charge - " + day4)
                .noneMatch(l -> l.startsWith("Daily Bed Charge - " + day4.minusDays(1)));
    }

    @Test
    void aPackageAppliedLaterCreditsWhatItCovers_andOnlyTheAdminRemovesIt() throws Exception {
        IpdAdmission ipd = admit(newPatient().getId(), null, newBed());
        shiftBack(ipd, java.time.Duration.ofDays(1));
        BigDecimal charged = gross(ipd.getId());
        assertThat(charged).isEqualByComparingTo("3600"); // 2 x (1500 bed + 300 nursing)

        Long pkg = newPackage("Surgery " + uniq(), "25000", 3);
        ResponseEntity<String> applied = post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/package", "{\"packageId\":" + pkg + "}");
        assertThat(applied.getStatusCode().value()).as(applied.getBody()).isEqualTo(200);
        assertThat(gross(ipd.getId())).as("the bill is now exactly the package").isEqualByComparingTo("25000");

        assertThat(post(receptionToken, "/hospital/ipd/" + ipd.getId() + "/package/remove", "{\"reason\":\"x\"}")
                .getStatusCode().value()).isEqualTo(403);
        ResponseEntity<String> removed = post(adminToken, "/hospital/ipd/" + ipd.getId() + "/package/remove", "{\"reason\":\"Wrong package\"}");
        assertThat(removed.getStatusCode().value()).as(removed.getBody()).isEqualTo(200);
        assertThat(gross(ipd.getId())).as("back to the day charges").isEqualByComparingTo(charged);
    }
}
