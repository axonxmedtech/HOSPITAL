package com.hms.security;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalSetting;
import com.hms.entity.User;
import com.hms.repository.*;
import com.hms.support.NursingHttpFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-SEC-2 — authorization on the tenant settings endpoints of {@code HospitalAuthController}.
 *
 * <p>Two things are proven here, and they are not the same thing:
 *
 * <ol>
 *   <li><b>The reads are role-scoped.</b> Fees and operations settings were readable by every
 *       tenant role, including PHARMACIST and OT_INCHARGE, which have no screen that needs them.
 *   <li><b>The writes are refused AND inert.</b> A 403 that still changed the row would be worse
 *       than no check at all, so every unauthorized mutation reloads the authoritative row and
 *       asserts it is byte-identical to the snapshot taken before the request.
 * </ol>
 *
 * <p>The legitimate readers are derived from the code, not from role names: ConsultationModal,
 * DoctorDashboard and ReceptionistDashboard read fees; the nurse panels (via
 * {@code nurseService.getSeparateNurseLogin}) read operations, and they are mounted both from
 * NursePatientDetail (NURSE, NURSE_INCHARGE) and from IpdDetails, whose route admits
 * RECEPTIONIST, DOCTOR and HOSPITAL_ADMIN.
 *
 * <p>Tenant scope is not a parameter on any of these endpoints: the hospital is resolved from the
 * authenticated principal's user row. There is no foreign-hospital identifier to tamper with, so
 * the strongest meaningful isolation assertion is that a write by tenant A leaves tenant B's row
 * untouched — which is asserted below.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HospitalSettingsAuthorizationTest {

    private static final List<String> NON_ADMIN_ROLES =
            List.of("DOCTOR", "RECEPTIONIST", "NURSE", "NURSE_INCHARGE", "PHARMACIST", "OT_INCHARGE");

    /** Every settings mutation, with a body that would change the row if it were accepted. */
    private static final List<String[]> WRITES = List.of(
            new String[] {"/hospital/settings/fees", "{\"consultationFee\":999,\"casePaperFee\":99}"},
            new String[] {"/hospital/settings/operations", "{\"receptionMode\":\"SOLO\",\"billingHandler\":\"DOCTOR\"}"},
            new String[] {"/hospital/settings/print-payment", "{\"billPaymentTiming\":\"FIRST\"}"},
            new String[] {"/hospital/settings/barcode", "{\"barcodeEnabled\":false}"},
            new String[] {"/hospital/settings/nurse-login", "{\"separateNurseLogin\":false}"},
            new String[] {"/hospital/settings/ot-incharge", "{\"otInchargeEnabled\":true}"});

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

    NursingHttpFixture f;
    Hospital tenantA;
    Hospital tenantB;

    @BeforeEach
    void seed() {
        f = new NursingHttpFixture(jwt, hospitals, settings, users, nurses, wards, beds, patients,
                doctors, admissions, records, prescriptions, histories);
        tenantA = billingEnabled(f.tenant("settings-a"));
        tenantB = billingEnabled(f.tenant("settings-b"));
    }

    /** The fees endpoints are @RequireModule("BILLING"); without it every role gets 403 for the wrong reason. */
    private Hospital billingEnabled(Hospital h) {
        h.setModules(List.of("OPD", "IPD", "NURSING", "BILLING"));
        h.setConsultationFee(new java.math.BigDecimal("500.00"));
        h.setCasePaperFee(new java.math.BigDecimal("50.00"));
        return hospitals.save(h);
    }

    // ---------- reads ----------

    @Test
    void feesAreReadableByTheRolesThatRaiseBills_andNobodyElse() {
        for (String role : List.of("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST")) {
            assertThat(status(HttpMethod.GET, "/hospital/settings/fees", tokenFor(role), null))
                    .as("%s must keep reading fees: the consultation modal prefills the bill from it", role)
                    .isEqualTo(200);
        }
        for (String role : List.of("NURSE", "NURSE_INCHARGE", "PHARMACIST", "OT_INCHARGE")) {
            assertThat(status(HttpMethod.GET, "/hospital/settings/fees", tokenFor(role), null))
                    .as("%s has no screen that reads fees", role)
                    .isEqualTo(403);
        }
    }

    @Test
    void operationsAreReadableByEveryRoleThatOpensAClinicalChart_andNobodyElse() {
        // NURSE/NURSE_INCHARGE reach it from NursePatientDetail; RECEPTIONIST/DOCTOR/ADMIN from
        // /ipd/:id, whose panels ask whether Separate Nurse Login is on.
        for (String role : List.of("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "NURSE", "NURSE_INCHARGE")) {
            assertThat(status(HttpMethod.GET, "/hospital/settings/operations", tokenFor(role), null))
                    .as("%s opens a chart panel that reads separateNurseLogin", role)
                    .isEqualTo(200);
        }
        for (String role : List.of("PHARMACIST", "OT_INCHARGE")) {
            assertThat(status(HttpMethod.GET, "/hospital/settings/operations", tokenFor(role), null))
                    .as("%s mounts no panel that reads operations settings", role)
                    .isEqualTo(403);
        }
    }

    // ---------- writes ----------

    @Test
    void everySettingsWriteIsRefusedForEveryNonAdminRole_andChangesNothing() {
        for (String[] write : WRITES) {
            for (String role : NON_ADMIN_ROLES) {
                String before = snapshot();
                int code = status(HttpMethod.PUT, write[0], tokenFor(role), write[1]);
                assertThat(code).as("%s must not write %s", role, write[0]).isEqualTo(403);
                assertThat(snapshot())
                        .as("%s was refused on %s but the configuration moved anyway", role, write[0])
                        .isEqualTo(before);
            }
        }
    }

    @Test
    void theAdminCanStillWriteEverySetting() {
        String admin = tokenFor("HOSPITAL_ADMIN");
        for (String[] write : WRITES) {
            assertThat(status(HttpMethod.PUT, write[0], admin, write[1]))
                    .as("the admin must still be able to write %s", write[0])
                    .isEqualTo(200);
        }
        // Not just 200: the row actually moved, so the refusals above prove something.
        assertThat(snapshot()).isNotEqualTo(defaultSnapshot());
    }

    // ---------- tenant scope ----------

    @Test
    void anAdminWriteNeverReachesAnotherTenantsConfiguration() {
        String beforeB = snapshotOf(tenantB);
        assertThat(status(HttpMethod.PUT, "/hospital/settings/barcode", tokenFor("HOSPITAL_ADMIN"),
                "{\"barcodeEnabled\":false}")).isEqualTo(200);
        assertThat(snapshotOf(tenantB))
                .as("tenant B's settings must be untouched by tenant A's admin")
                .isEqualTo(beforeB);
        // The endpoints take no hospital identifier at all, so there is nothing to tamper with:
        // the hospital comes from the authenticated user's row.
        assertThat(snapshotOf(tenantA)).isNotEqualTo(beforeB);
    }

    // ---------- helpers ----------

    private String tokenFor(String role) {
        User user = f.user(tenantA, role, role.toLowerCase() + "-" + System.nanoTime());
        return f.tokenFor(user);
    }

    private String snapshot() { return snapshotOf(tenantA); }

    private String defaultSnapshot() {
        return "HAS_RECEPTIONIST|RECEPTIONIST|true|true|false|LAST|500.00|50.00";
    }

    /** The authoritative configuration, re-read from the database, as one comparable string. */
    private String snapshotOf(Hospital hospital) {
        HospitalSetting s = settings.findByHospital_Id(hospital.getId()).orElseThrow();
        Hospital h = hospitals.findById(hospital.getId()).orElseThrow();
        return s.getReceptionMode() + "|" + s.getBillingHandler() + "|" + s.getBarcodeEnabled() + "|"
                + s.getSeparateNurseLogin() + "|" + s.getOtInchargeEnabled() + "|" + s.getBillPaymentTiming()
                + "|" + h.getConsultationFee() + "|" + h.getCasePaperFee();
    }

    private int status(HttpMethod method, String path, String token, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .method(method.name(), HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                    .build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
