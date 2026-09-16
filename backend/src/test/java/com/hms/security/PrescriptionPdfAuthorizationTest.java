package com.hms.security;

import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.support.NursingHttpFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-SEC-4 — role authorization on the two prescription PDF endpoints of {@code DoctorController}.
 *
 * <p>These were the only two handlers in that controller with no {@code @PreAuthorize}. Their own
 * JSON siblings — {@code /consultation/{appointmentId}} and {@code /consultation/opd/{opdId}} —
 * already answer the question "who may read this consultation": HOSPITAL_ADMIN, DOCTOR,
 * RECEPTIONIST. The PDF is the same clinical content in another format, so it takes the same rule.
 *
 * <p>Tenant scope is NOT what these annotations fix, and the tests below confirm it was never
 * broken: the appointment is loaded by {@code (publicId|id, hospitalId)} and the OPD through a
 * patient join carrying {@code p.hospitalId}, both from the authenticated token. A foreign id is
 * simply not found. That is asserted here so the role fix cannot quietly mask a regression in it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PrescriptionPdfAuthorizationTest {

    private static final List<String> ALLOWED = List.of("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST");
    private static final List<String> DENIED =
            List.of("NURSE", "NURSE_INCHARGE", "PHARMACIST", "OT_INCHARGE");

    @LocalServerPort int port;
    @Autowired JwtUtil jwt;
    @Autowired HospitalRepository hospitals;
    @Autowired HospitalSettingRepository settings;
    @Autowired UserRepository users;
    @Autowired NurseProfileRepository nurses;
    @Autowired WardRepository wards;
    @Autowired BedRepository beds;
    @Autowired PatientRepository patients;
    @Autowired DoctorRepository doctors;
    @Autowired IpdAdmissionRepository admissions;
    @Autowired MedicalRecordRepository records;
    @Autowired PrescriptionRepository prescriptions;
    @Autowired IpdBedHistoryRepository histories;
    @Autowired AppointmentRepository appointments;
    @Autowired OpdRepository opds;

    NursingHttpFixture f;
    Hospital tenantA, tenantB;
    String apptA, apptB;      // appointment publicIds
    Long opdA, opdB;          // opd ids
    String foreignPatientName;

    @BeforeEach
    void seed() {
        f = new NursingHttpFixture(jwt, hospitals, settings, users, nurses, wards, beds, patients,
                doctors, admissions, records, prescriptions, histories);
        tenantA = f.tenant("pdf-a");
        tenantB = f.tenant("pdf-b");
        apptA = appointmentWithPrescription(tenantA, "Anita Owner");
        opdA = opdWithPrescription(tenantA, "Anita Owner");
        foreignPatientName = "Bharat Foreign";
        apptB = appointmentWithPrescription(tenantB, foreignPatientName);
        opdB = opdWithPrescription(tenantB, foreignPatientName);
    }

    // ---------- role matrix ----------

    @Test
    void appointmentPdfIsReadableOnlyByTheRolesThatReadTheConsultationItself() {
        for (String role : ALLOWED) {
            Response r = get("/hospital/doctors/prescription/" + apptA + "/pdf", token(tenantA, role));
            assertThat(r.status).as("%s reads the appointment consultation, so it reads its PDF", role).isEqualTo(200);
        }
        for (String role : DENIED) {
            Response r = get("/hospital/doctors/prescription/" + apptA + "/pdf", token(tenantA, role));
            assertThat(r.status).as("%s cannot read the consultation JSON and must not read its PDF", role).isEqualTo(403);
            assertThat(r.bodyText()).as("a refused %s must receive no prescription content", role)
                    .doesNotContain("Fixture medicine");
        }
    }

    @Test
    void opdPdfIsReadableOnlyByTheRolesThatReadTheConsultationItself() {
        for (String role : ALLOWED) {
            Response r = get("/hospital/doctors/prescription/opd/" + opdA + "/pdf", token(tenantA, role));
            assertThat(r.status).as("%s must keep the OPD prescription PDF", role).isEqualTo(200);
        }
        for (String role : DENIED) {
            Response r = get("/hospital/doctors/prescription/opd/" + opdA + "/pdf", token(tenantA, role));
            assertThat(r.status).as("%s must be refused the OPD prescription PDF", role).isEqualTo(403);
            assertThat(r.bodyText()).doesNotContain("Fixture medicine");
        }
    }

    // ---------- the authorized response is a real PDF, not merely "not 403" ----------

    @Test
    void anAuthorizedRequestStillReturnsTheSamePdfResponse() {
        for (String path : List.of("/hospital/doctors/prescription/" + apptA + "/pdf",
                "/hospital/doctors/prescription/opd/" + opdA + "/pdf")) {
            Response r = get(path, token(tenantA, "DOCTOR"));
            assertThat(r.status).isEqualTo(200);
            assertThat(r.header("content-type")).startsWith("application/pdf");
            assertThat(r.header("content-disposition")).startsWith("inline; filename=prescription");
            assertThat(r.body.length).as("an empty body would pass a status-only assertion").isGreaterThan(500);
            assertThat(new String(r.body, 0, 5, StandardCharsets.ISO_8859_1))
                    .as("the bytes must actually be a PDF").isEqualTo("%PDF-");
        }
    }

    // ---------- foreign tenant ----------

    @Test
    void anAuthorizedUserCannotReachAnotherTenantsPrescriptionPdf() {
        Response appt = get("/hospital/doctors/prescription/" + apptB + "/pdf", token(tenantA, "HOSPITAL_ADMIN"));
        assertThat(appt.status).as("tenant A must not receive tenant B's appointment PDF").isNotEqualTo(200);
        assertLeakFree(appt);

        Response opd = get("/hospital/doctors/prescription/opd/" + opdB + "/pdf", token(tenantA, "HOSPITAL_ADMIN"));
        assertThat(opd.status).as("tenant A must not receive tenant B's OPD PDF").isNotEqualTo(200);
        assertLeakFree(opd);
    }

    @Test
    void aForeignResourceAnswersLikeAMissingOne() {
        String admin = token(tenantA, "HOSPITAL_ADMIN");
        // Recorded, not redesigned: the two handlers have different error contracts today.
        assertThat(get("/hospital/doctors/prescription/" + apptB + "/pdf", admin).status)
                .isEqualTo(get("/hospital/doctors/prescription/no-such-appointment/pdf", admin).status);
        assertThat(get("/hospital/doctors/prescription/opd/" + opdB + "/pdf", admin).status)
                .isEqualTo(get("/hospital/doctors/prescription/opd/99999999/pdf", admin).status);
        assertLeakFree(get("/hospital/doctors/prescription/no-such-appointment/pdf", admin));
        assertLeakFree(get("/hospital/doctors/prescription/opd/99999999/pdf", admin));
    }

    private void assertLeakFree(Response r) {
        assertThat(r.bodyText())
                .as("no foreign clinical content may appear in the response")
                .doesNotContain("Fixture medicine")
                .doesNotContain(foreignPatientName);
        assertThat(r.header("content-type")).as("a refusal must not be delivered as a PDF")
                .doesNotStartWith("application/pdf");
    }

    // ---------- fixtures ----------

    private Patient patient(Hospital hospital, String name) {
        Patient p = new Patient();
        p.setHospitalId(hospital.getId());
        p.setName(name);
        p.setPublicId("pdf-patient-" + System.nanoTime());
        p.setPhone("9900000001");
        p.setGender("MALE");
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        p.setIsActive(true);
        return patients.save(p);
    }

    private MedicalRecord consultation(Hospital hospital, Patient p, Doctor d) {
        MedicalRecord record = new MedicalRecord();
        record.setHospitalId(hospital.getId());
        record.setPatientId(p.getId());
        record.setDoctorId(d.getId());
        record.setVisitType("OPD");
        return records.save(record);
    }

    private void prescribe(Hospital hospital, MedicalRecord record) {
        Prescription rx = new Prescription();
        rx.setHospitalId(hospital.getId());
        rx.setMedicalRecordId(record.getId());
        rx.setMedicineName("Fixture medicine");
        rx.setDosage("500mg");
        rx.setFrequency("1-0-1");
        rx.setDuration("3 days");
        rx.setDurationDays(3);
        rx.setStartDate(LocalDate.now());
        rx.setStatus("ACTIVE");
        rx.setType("TABLET");
        rx.setRoute("ORAL");
        prescriptions.save(rx);
    }

    private String appointmentWithPrescription(Hospital hospital, String patientName) {
        Doctor d = f.doctor(hospital, "Appt");
        Patient p = patient(hospital, patientName);
        Appointment a = new Appointment();
        a.setHospitalId(hospital.getId());
        a.setPatientId(p.getId());
        a.setDoctorId(d.getId());
        a.setPublicId("appt-" + System.nanoTime());
        a.setAppointmentDate(LocalDate.now());
        a.setAppointmentTime(LocalTime.of(10, 0));
        a.setStatus("COMPLETED");
        a.setIsActive(true);
        a = appointments.save(a);
        MedicalRecord record = consultation(hospital, p, d);
        record.setAppointmentId(a.getId());
        record = records.save(record);
        prescribe(hospital, record);
        return a.getPublicId();
    }

    private Long opdWithPrescription(Hospital hospital, String patientName) {
        Doctor d = f.doctor(hospital, "Opd");
        Patient p = patient(hospital, patientName);
        Opd opd = new Opd();
        opd.setCaseId("CASE-" + System.nanoTime());
        opd.setPatient(p);
        opd.setDoctor(d);
        opd.setVisitType(Opd.VisitType.NEW);
        opd.setStatus(Opd.Status.COMPLETED);
        opd = opds.save(opd);
        MedicalRecord record = consultation(hospital, p, d);
        record.setOpdId(opd.getId());
        record = records.save(record);
        prescribe(hospital, record);
        return opd.getId();
    }

    private String token(Hospital hospital, String role) {
        return f.tokenFor(f.user(hospital, role, role.toLowerCase() + "-" + System.nanoTime()));
    }

    // ---------- http ----------

    private record Response(int status, byte[] body, java.net.http.HttpHeaders headers) {
        String header(String name) { return headers.firstValue(name).orElse(""); }
        String bodyText() { return new String(body, StandardCharsets.ISO_8859_1); }
    }

    private Response get(String path, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<byte[]> response =
                    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), response.body(), response.headers());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
