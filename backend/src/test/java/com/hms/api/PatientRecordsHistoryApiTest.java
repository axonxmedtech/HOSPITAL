package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Discharged, closed and finished records stay findable: the IPD list's Current / Discharged /
 * All views with date and search filters, the ICU and OT history lists, and the Patient File —
 * with each role seeing only what it is allowed to, and nothing crossing hospitals.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:records_history_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class PatientRecordsHistoryApiTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING", "ICU", "OT", "NURSING");

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitals;
    @Autowired UserRepository users;
    @Autowired DoctorRepository doctors;
    @Autowired PatientRepository patients;
    @Autowired WardRepository wards;
    @Autowired IcuWardRepository icuWards;
    @Autowired IpdAdmissionRepository admissions;
    @Autowired IcuStayRepository icuStays;
    @Autowired SurgeryRepository surgeries;
    @Autowired BillingRepository bills;
    @Autowired OpdRepository opds;
    @Autowired NurseProfileRepository nurseProfiles;

    private final ObjectMapper json = new ObjectMapper();
    private Hospital hospital;
    private Doctor drA, drB;
    private Ward ward, icu;
    private String admin, reception, doctorA, pharmacist, nurse;

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
        u.setName("User " + role);
        u.setRole(role);
        u.setHospitalId(h.getId());
        u.setIsActive(true);
        u.setTokenVersion(0);
        u = users.saveAndFlush(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), role, h.getId(), MODULES, null, "HOSPITAL", null,
                u.getTokenVersion());
    }

    private Doctor doctor(Hospital h, String name, String email) {
        Doctor d = new Doctor();
        d.setHospitalId(h.getId());
        d.setName(name);
        d.setEmail(email);
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
        p.setGender("FEMALE");
        p.setPhone(String.format("97%08d", uniq() % 100_000_000L));
        p.setDateOfBirth(LocalDate.of(1975, 5, 5));
        p.setIsActive(true);
        p.setMedicalHistory("Diabetic");
        return patients.save(p);
    }

    private Ward ward(Hospital h, String name) {
        Ward w = new Ward();
        w.setWardName(name);
        w.setHospitalId(h.getId());
        w.setBedPrice(new BigDecimal("1000"));
        w.setTotalBeds(2);
        return wards.save(w);
    }

    private IpdAdmission admission(Patient p, Doctor d, Ward w, String status, LocalDateTime in, LocalDateTime out) {
        IpdAdmission a = new IpdAdmission();
        a.setIpdNumber("IPD-H" + uniq());
        a.setPatientId(p.getId());
        a.setDoctorId(d.getId());
        a.setHospitalId(p.getHospitalId());
        a.setAdmissionType("PLANNED");
        a.setStatus(status);
        a.setAdmissionDatetime(in);
        a.setDischargeDatetime(out);
        a.setWardId(w.getWardId());
        a.setBedId(1L);
        return admissions.save(a);
    }

    @BeforeEach
    void setUp() {
        hospital = newHospital("Records Hospital");
        String emailA = "dra." + uniq() + "@rec.test";
        drA = doctor(hospital, "Dr Asha", emailA);
        drB = doctor(hospital, "Dr Bhide", "drb." + uniq() + "@rec.test");
        ward = ward(hospital, "General Ward");
        icu = ward(hospital, "Medical ICU");
        IcuWard iw = new IcuWard();
        iw.setHospitalId(hospital.getId());
        iw.setWardId(icu.getWardId());
        iw.setWardName("Medical ICU");
        iw.setPublicId("icu-" + uniq());
        iw.setBedPrice(new BigDecimal("3000"));
        iw.setTotalBeds(2);
        icuWards.save(iw);

        admin = token(hospital, "HOSPITAL_ADMIN", "admin." + uniq() + "@rec.test");
        reception = token(hospital, "RECEPTIONIST", "rec." + uniq() + "@rec.test");
        doctorA = token(hospital, "DOCTOR", emailA);
        pharmacist = token(hospital, "PHARMACIST", "ph." + uniq() + "@rec.test");
        nurse = token(hospital, "NURSE", "nu." + uniq() + "@rec.test");
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

    private static List<Long> ipdIds(JsonNode page) {
        List<Long> out = new ArrayList<>();
        page.path("content").forEach(n -> out.add(n.path("ipd").path("id").asLong()));
        return out;
    }

    // ───────────────────────────── IPD history ─────────────────────────────

    @Test
    void aDischargedPatientStaysInTheIpdListUnderDischargedAndAll() throws Exception {
        Patient p1 = patient(hospital, "Kavita Shinde");
        Patient p2 = patient(hospital, "Ramesh Gawde");
        IpdAdmission now = admission(p1, drA, ward, "ADMITTED", LocalDateTime.now().minusDays(1), null);
        IpdAdmission gone = admission(p2, drA, ward, "DISCHARGED",
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(3));

        assertThat(ipdIds(ok("/hospital/ipd?view=CURRENT", admin))).containsExactly(now.getId());
        assertThat(ipdIds(ok("/hospital/ipd?view=DISCHARGED", reception))).containsExactly(gone.getId());
        assertThat(ipdIds(ok("/hospital/ipd?view=ALL", admin))).containsExactlyInAnyOrder(now.getId(), gone.getId());

        // the discharged view's dates are the discharge dates
        String d = LocalDate.now().minusDays(3).toString();
        assertThat(ipdIds(ok("/hospital/ipd?view=DISCHARGED&from=" + d + "&to=" + d, admin))).containsExactly(gone.getId());
        String yesterday = LocalDate.now().minusDays(1).toString();
        assertThat(ipdIds(ok("/hospital/ipd?view=DISCHARGED&from=" + yesterday, admin))).isEmpty();

        // the search box now works: name, phone, IPD number
        assertThat(ipdIds(ok("/hospital/ipd?view=ALL&search=gawde", admin))).containsExactly(gone.getId());
        assertThat(ipdIds(ok("/hospital/ipd?view=ALL&search=" + p1.getPhone(), admin))).containsExactly(now.getId());
        assertThat(ipdIds(ok("/hospital/ipd?view=ALL&search=" + gone.getIpdNumber(), admin))).containsExactly(gone.getId());
    }

    @Test
    void aDoctorSeesOnlyTheirOwnPastAdmissions() throws Exception {
        IpdAdmission mine = admission(patient(hospital, "Mine One"), drA, ward, "DISCHARGED",
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(2));
        admission(patient(hospital, "Other One"), drB, ward, "DISCHARGED",
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(2));

        assertThat(ipdIds(ok("/hospital/ipd?view=DISCHARGED", doctorA))).containsExactly(mine.getId());
    }

    @Test
    void theIpdListRejectsAnUnknownViewAndABackwardsDateRange() {
        assertThat(call("/hospital/ipd?view=GONE", admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call("/hospital/ipd?from=2026-05-10&to=2026-05-01", admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call("/hospital/ipd?view=ALL", pharmacist).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherHospitalsAdmissionsNeverAppear() throws Exception {
        Hospital other = newHospital("Other Hospital");
        Doctor od = doctor(other, "Dr Other", "od." + uniq() + "@rec.test");
        admission(patient(other, "Foreign Patient"), od, ward(other, "W"), "DISCHARGED",
                LocalDateTime.now().minusDays(4), LocalDateTime.now().minusDays(1));

        assertThat(ok("/hospital/ipd?view=ALL&search=foreign", admin).path("content")).isEmpty();
    }

    // ───────────────────────────── ICU history ─────────────────────────────

    private IcuStay icuStay(IpdAdmission a, String status, LocalDateTime in, LocalDateTime out) {
        IcuStay s = new IcuStay();
        s.setHospitalId(hospital.getId());
        s.setIpdAdmissionId(a.getId());
        s.setPatientId(a.getPatientId());
        s.setWardId(icu.getWardId());
        s.setStatus(status);
        s.setSource(IcuStay.SRC_WARD);
        s.setAdmittedAt(in);
        s.setDischargedAt(out);
        s.setDisposition(out != null ? IcuStay.DISP_WARD : null);
        return icuStays.save(s);
    }

    @Test
    void aClosedIcuStayIsListedInIcuHistory() throws Exception {
        IpdAdmission a = admission(patient(hospital, "Icu Past"), drA, ward, "DISCHARGED",
                LocalDateTime.now().minusDays(8), LocalDateTime.now().minusDays(1));
        IcuStay closed = icuStay(a, IcuStay.CLOSED, LocalDateTime.now().minusDays(7), LocalDateTime.now().minusDays(4));

        JsonNode page = ok("/hospital/icu/history?view=CLOSED", reception);
        assertThat(page.path("content")).hasSize(1);
        JsonNode row = page.path("content").get(0);
        assertThat(row.path("publicId").asText()).isEqualTo(closed.getPublicId());
        assertThat(row.path("unitName").asText()).isEqualTo("Medical ICU");
        assertThat(row.path("patient").path("name").asText()).isEqualTo("Icu Past");
        assertThat(row.path("days").asLong()).isEqualTo(3);

        assertThat(ok("/hospital/icu/history?view=CURRENT", admin).path("content")).isEmpty();
        assertThat(ok("/hospital/icu/history?view=CLOSED&search=nobody", admin).path("content")).isEmpty();
        assertThat(call("/hospital/icu/history", nurse).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ───────────────────────────── OT history ─────────────────────────────

    private Surgery surgery(Patient p, String status, String procedure, LocalDateTime at) {
        Surgery s = new Surgery();
        s.setHospitalId(hospital.getId());
        s.setPatientId(p.getId());
        s.setProcedureName(procedure);
        s.setPriority("ELECTIVE");
        s.setStatus(status);
        s.setRequestedAt(at.minusDays(1));
        s.setScheduledAt(at);
        s.setSurgeonDoctorId(drA.getId());
        return surgeries.save(s);
    }

    @Test
    void finishedAndCancelledSurgeriesAreListedInOtHistory() throws Exception {
        Patient p = patient(hospital, "Ot Patient");
        Surgery closed = surgery(p, "CLOSED", "Appendectomy", LocalDateTime.now().minusDays(6));
        Surgery cancelled = surgery(p, "CANCELLED", "Hernia repair", LocalDateTime.now().minusDays(2));

        JsonNode done = ok("/hospital/surgeries/history?view=DONE", admin);
        assertThat(done.path("content")).hasSize(1);
        assertThat(done.path("content").get(0).path("publicId").asText()).isEqualTo(closed.getPublicId());
        assertThat(ok("/hospital/surgeries/history?view=CANCELLED", admin).path("content").get(0)
                .path("publicId").asText()).isEqualTo(cancelled.getPublicId());
        assertThat(ok("/hospital/surgeries/history?view=ALL&search=append", admin).path("content")).hasSize(1);
        String d = LocalDate.now().minusDays(2).toString();
        assertThat(ok("/hospital/surgeries/history?view=ALL&from=" + d, admin).path("content")).hasSize(1);
    }

    // ───────────────────────────── Patient File ─────────────────────────────

    private Patient patientWithHistory() {
        Patient p = patient(hospital, "Sunanda Joshi");
        Opd o = new Opd();
        o.setPatient(p);
        o.setDoctor(drA);
        o.setProblem("Fever and cough");
        o.setStatus(Opd.Status.COMPLETED);
        opds.save(o);
        IpdAdmission a = admission(p, drA, ward, "DISCHARGED",
                LocalDateTime.now().minusDays(12), LocalDateTime.now().minusDays(9));
        icuStay(a, IcuStay.CLOSED, LocalDateTime.now().minusDays(11), LocalDateTime.now().minusDays(10));
        surgery(p, "CLOSED", "Cholecystectomy", LocalDateTime.now().minusDays(10));
        Billing b = new Billing();
        b.setHospitalId(hospital.getId());
        b.setPatientId(p.getId());
        b.setDoctorId(drA.getId());
        b.setBillingType("IPD");
        b.setIpdAdmissionId(a.getId());
        b.setAmount(new BigDecimal("550"));
        b.setPaymentStatus("PENDING");
        bills.save(b);
        Billing cancelled = new Billing();
        cancelled.setHospitalId(hospital.getId());
        cancelled.setPatientId(p.getId());
        cancelled.setDoctorId(drA.getId());
        cancelled.setBillingType("OPD");
        cancelled.setAmount(new BigDecimal("300"));
        cancelled.setPaymentStatus("CANCELLED");
        bills.save(cancelled);
        return p;
    }

    @Test
    void thePatientFileHoldsEveryVisitStaySurgeryAndBill() throws Exception {
        Patient p = patientWithHistory();

        JsonNode file = ok("/hospital/patients/" + p.getPublicId() + "/file", admin);
        assertThat(file.path("patient").path("name").asText()).isEqualTo("Sunanda Joshi");
        assertThat(file.path("opdVisits")).hasSize(1);
        assertThat(file.path("opdVisits").get(0).path("problem").asText()).isEqualTo("Fever and cough");
        assertThat(file.path("ipdStays")).hasSize(1);
        assertThat(file.path("ipdStays").get(0).path("status").asText()).isEqualTo("DISCHARGED");
        assertThat(file.path("ipdStays").get(0).path("days").asLong()).isEqualTo(3);
        assertThat(file.path("icuStays")).hasSize(1);
        assertThat(file.path("surgeries")).hasSize(1);
        assertThat(file.path("bills")).hasSize(2);
        assertThat(file.path("billTotals").path("totalAmount").decimalValue()).isEqualByComparingTo("550");
        assertThat(file.path("access").path("clinical").asBoolean()).isTrue();
    }

    @Test
    void receptionSeesTheVisitsAndBillsButNoClinicalDetail() throws Exception {
        Patient p = patientWithHistory();

        JsonNode file = ok("/hospital/patients/" + p.getPublicId() + "/file", reception);
        assertThat(file.path("access").path("clinical").asBoolean()).isFalse();
        assertThat(file.path("opdVisits")).hasSize(1);
        assertThat(file.path("opdVisits").get(0).has("problem")).isFalse();
        assertThat(file.path("patient").has("medicalHistory")).isFalse();
        assertThat(file.path("documents")).isEmpty();
        assertThat(file.path("bills")).hasSize(2);
        assertThat(file.path("ipdStays")).hasSize(1);

        // and the clinical timeline stays closed to reception
        assertThat(call("/hospital/patients/" + p.getPublicId() + "/timeline", reception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rolesOutsideTheirScopeCannotOpenAFile() {
        Patient p = patientWithHistory();
        String url = "/hospital/patients/" + p.getPublicId() + "/file";
        assertThat(call(url, pharmacist).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // a staff nurse who is not caring for this (discharged) patient
        assertThat(call(url, nurse).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/hospital/patients/" + p.getPublicId() + "/timeline", nurse).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aDeletedPatientsFileIsKeptAndStillOpensForTheAdmin() throws Exception {
        Patient p = patientWithHistory();
        p.setIsActive(false);
        patients.save(p);

        JsonNode file = ok("/hospital/patients/" + p.getPublicId() + "/file", admin);
        assertThat(file.path("patient").path("isActive").asBoolean()).isFalse();
        assertThat(file.path("ipdStays")).hasSize(1);
        assertThat(file.path("bills")).hasSize(2);
        ok("/hospital/patients/" + p.getPublicId() + "/timeline", admin);

        assertThat(call("/hospital/patients/" + p.getPublicId() + "/file", doctorA).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherHospitalCannotOpenThisPatientsFile() {
        Patient p = patientWithHistory();
        Hospital other = newHospital("Snooping Hospital");
        String foreignAdmin = token(other, "HOSPITAL_ADMIN", "fa." + uniq() + "@rec.test");

        assertThat(call("/hospital/patients/" + p.getPublicId() + "/file", foreignAdmin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aNurseInchargeOpensTheFileOfAPatientAdmittedInTheirWard() throws Exception {
        String email = "inc." + uniq() + "@rec.test";
        String inchargeToken = token(hospital, "NURSE_INCHARGE", email);
        User u = users.findByEmail(email).orElseThrow();
        NurseProfile np = new NurseProfile();
        np.setPublicId("np-" + uniq());
        np.setUserId(u.getId());
        np.setHospitalId(hospital.getId());
        np.setName("Sister Incharge");
        np.setEmail(email);
        np.setIsIncharge(true);
        np.setCreatedAt(LocalDateTime.now());
        np = nurseProfiles.save(np);
        ward.setInchargeNurseId(np.getId());
        wards.save(ward);

        Patient inCare = patient(hospital, "In Care");
        admission(inCare, drA, ward, "ADMITTED", LocalDateTime.now().minusDays(1), null);
        Ward otherWard = ward(hospital, "Other Ward");
        Patient elsewhere = patient(hospital, "Elsewhere");
        admission(elsewhere, drA, otherWard, "ADMITTED", LocalDateTime.now().minusDays(1), null);

        JsonNode file = ok("/hospital/patients/" + inCare.getPublicId() + "/file", inchargeToken);
        assertThat(file.path("access").path("clinical").asBoolean()).isTrue();
        assertThat(file.path("access").path("billing").asBoolean()).isFalse();
        assertThat(file.path("bills")).isEmpty();
        ok("/hospital/patients/" + inCare.getPublicId() + "/timeline", inchargeToken);

        assertThat(call("/hospital/patients/" + elsewhere.getPublicId() + "/file", inchargeToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
