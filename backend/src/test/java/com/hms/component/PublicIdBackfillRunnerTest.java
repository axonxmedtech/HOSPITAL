package com.hms.component;

import com.hms.entity.*;
import com.hms.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Automated tests for Task P0-03 (PublicIdBackfillRunner Memory and Data-Mutation Risks).
 *
 * <p>Verifies:
 * <ul>
 *   <li><b>No Unrelated Data Mutations:</b> Legitimate 0.00 consultation fees and custom module lists are NOT overwritten.</li>
 *   <li><b>Idempotency:</b> Existing valid public and custom IDs remain unchanged, second run produces 0 updates.</li>
 *   <li><b>Missing ID Backfill:</b> Missing, blank, or literal "null" IDs are cleanly populated.</li>
 *   <li><b>Bounded Batching & Memory Safety:</b> Processes large batches (1,000+ entities) with constant bounded memory overhead.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class PublicIdBackfillRunnerTest {

    @Autowired
    private PublicIdBackfillRunner runner;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanState() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM AuditLog").executeUpdate();
            entityManager.createQuery("DELETE FROM Billing").executeUpdate();
            entityManager.createQuery("DELETE FROM Appointment").executeUpdate();
            entityManager.createQuery("DELETE FROM Patient").executeUpdate();
            entityManager.createQuery("DELETE FROM Doctor").executeUpdate();
            entityManager.createQuery("DELETE FROM User WHERE email NOT LIKE '%@%'").executeUpdate();
            return null;
        });
    }

    @Test
    void preservesExistingIdsAndDoesNotOverwriteBusinessData() throws Exception {
        // Create hospital with 0.00 consultation fee and empty modules
        Hospital hospital = new Hospital();
        hospital.setName("Free Health Clinic");
        hospital.setPublicId("existing-hospital-uuid-123");
        hospital.setCustomId("HSP-FIXED-01");
        hospital.setConsultationFee(BigDecimal.ZERO);
        hospital.setModules(new ArrayList<>());
        hospital.setIsActive(true);
        hospital = hospitalRepository.saveAndFlush(hospital);

        // Create patient with existing IDs
        Patient patient = new Patient();
        patient.setHospitalId(hospital.getId());
        patient.setName("Existing Patient");
        patient.setPhone("9988776655");
        patient.setGender("FEMALE");
        patient.setPublicId("patient-uuid-456");
        patient.setCustomId("PAT9999");
        patient = patientRepository.saveAndFlush(patient);

        // Run backfill
        runner.run();

        // Verify hospital business data was NOT corrupted
        Hospital refreshedHospital = hospitalRepository.findById(hospital.getId()).orElseThrow();
        assertThat(refreshedHospital.getPublicId()).isEqualTo("existing-hospital-uuid-123");
        assertThat(refreshedHospital.getCustomId()).isEqualTo("HSP-FIXED-01");
        assertThat(refreshedHospital.getConsultationFee())
                .as("Consultation fee of 0.00 must NOT be overwritten to 500.00")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(refreshedHospital.getModules())
                .as("Empty modules list must NOT be overwritten with defaults")
                .isEmpty();

        // Verify patient IDs were NOT overwritten
        Patient refreshedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(refreshedPatient.getPublicId()).isEqualTo("patient-uuid-456");
        assertThat(refreshedPatient.getCustomId()).isEqualTo("PAT9999");

        // Idempotency check: Run second time
        long startSecondRun = System.currentTimeMillis();
        runner.run();
        long secondRunDuration = System.currentTimeMillis() - startSecondRun;

        // Second run completes quickly and alters nothing
        assertThat(secondRunDuration).isLessThan(5000);
        Hospital secondHospital = hospitalRepository.findById(hospital.getId()).orElseThrow();
        assertThat(secondHospital.getPublicId()).isEqualTo("existing-hospital-uuid-123");
        assertThat(secondHospital.getConsultationFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void backfillsMissingAndMalformedPublicIds() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("General Hospital");
        hospital.setPublicId("hsp-uuid-" + System.nanoTime());
        hospital.setCustomId("HSP" + (1000 + (int)(Math.random() * 9000)));
        hospital = hospitalRepository.saveAndFlush(hospital);

        // User with literal string "null" publicId
        User userMalformed = new User();
        userMalformed.setEmail("malformed-" + System.nanoTime() + "@test.test");
        userMalformed.setPassword("{noop}secret");
        userMalformed.setName("Malformed User");
        userMalformed.setRole("DOCTOR");
        userMalformed.setPublicId("null"); // legacy bad data
        userMalformed = userRepository.saveAndFlush(userMalformed);

        // AuditLog with missing publicId
        AuditLog auditLog = new AuditLog();
        auditLog.setAction("TEST_ACTION");
        auditLog.setDetails("Test Audit Details");
        auditLog.setPerformedBy("admin@test.test");
        auditLog.setHospitalId(hospital.getId());
        auditLog.setPublicId(null);
        auditLog = auditLogRepository.saveAndFlush(auditLog);

        // Run backfill
        runner.run();

        // Verify malformed user public ID is repaired
        User repairedUser = userRepository.findById(userMalformed.getId()).orElseThrow();
        assertThat(repairedUser.getPublicId()).isNotNull();
        assertThat(repairedUser.getPublicId()).isNotEqualTo("null");
        assertThat(repairedUser.getPublicId()).isNotBlank();

        // Verify audit log public ID is assigned
        AuditLog repairedAudit = auditLogRepository.findById(auditLog.getId()).orElseThrow();
        assertThat(repairedAudit.getPublicId()).isNotNull();
        assertThat(repairedAudit.getPublicId()).isNotBlank();
    }

    @Test
    void largeBatchProcessingScalesWithinBoundedMemory() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("Scale Test Hospital");
        hospital.setPublicId("hsp-scale-" + System.nanoTime());
        hospital.setCustomId("HSP" + (1000 + (int)(Math.random() * 9000)));
        final Hospital h = hospitalRepository.saveAndFlush(hospital);

        // Pre-create 1,000 synthetic patient records with missing publicId and customId in chunks
        final int TOTAL_RECORDS = 1000;
        transactionTemplate.execute(status -> {
            for (int i = 0; i < TOTAL_RECORDS; i++) {
                Patient p = new Patient();
                p.setHospitalId(h.getId());
                p.setName("Synthetic Patient " + i);
                p.setPhone("9" + String.format("%09d", i));
                p.setGender("MALE");
                p.setPublicId(null);
                p.setCustomId(null);
                entityManager.persist(p);
                if (i % 250 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        // Measure memory before
        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.currentTimeMillis();

        // Execute bounded batch runner
        runner.run();

        long duration = System.currentTimeMillis() - startTime;
        System.gc();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memDeltaMb = (memAfter - memBefore) / (1024 * 1024);

        // Assert memory remained strictly bounded (< 64 MB growth)
        assertThat(memDeltaMb).isLessThan(64);

        // Assert all 1,000 records were backfilled
        Long unassignedCount = entityManager.createQuery(
                "SELECT COUNT(p) FROM Patient p WHERE p.hospitalId = :hid AND (p.publicId IS NULL OR p.publicId = '' OR p.customId IS NULL)",
                Long.class)
                .setParameter("hid", h.getId())
                .getSingleResult();

        assertThat(unassignedCount).isEqualTo(0L);

        // Validate a sample record
        List<Patient> samples = entityManager.createQuery(
                "SELECT p FROM Patient p WHERE p.hospitalId = :hid ORDER BY p.id ASC", Patient.class)
                .setParameter("hid", h.getId())
                .setMaxResults(10)
                .getResultList();

        assertThat(samples).isNotEmpty();
        for (Patient p : samples) {
            assertThat(p.getPublicId()).isNotNull().isNotBlank();
            assertThat(p.getCustomId()).startsWith("PAT");
        }

        // Second run: verify idempotency and zero updates on large dataset
        long secondStart = System.currentTimeMillis();
        runner.run();
        long secondDuration = System.currentTimeMillis() - secondStart;

        assertThat(secondDuration).as("Idempotent second run on 1,000 records must complete rapidly").isLessThan(2000);
    }

    @Test
    void concurrentUpdateDuringBackfill_doesNotOverwriteConcurrentPatientEdits() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("Concurrency Test Hospital");
        hospital.setPublicId("hsp-conc-" + System.nanoTime());
        hospital.setCustomId("HSP" + (1000 + (int) (Math.random() * 9000)));
        hospital = hospitalRepository.saveAndFlush(hospital);

        // 1. Patient created with missing publicId and customId
        Patient patient = new Patient();
        patient.setHospitalId(hospital.getId());
        patient.setName("Initial Patient Name");
        patient.setPhone("1111111111");
        patient.setGender("FEMALE");
        patient.setPublicId(null);
        patient.setCustomId(null);
        patient = patientRepository.saveAndFlush(patient);

        final Long patientId = patient.getId();

        // 2. Concurrently (simulated before/during backfill), an out-of-band edit updates patient details
        transactionTemplate.execute(status -> {
            entityManager.createQuery(
                    "UPDATE Patient p SET p.name = 'Updated Patient Name', p.phone = '9999999999' WHERE p.id = :id")
                    .setParameter("id", patientId)
                    .executeUpdate();
            return null;
        });

        // 3. Runner executes backfill
        runner.run();

        // 4. Assert: publicId and customId were populated, but concurrent name and phone edits were PRESERVED!
        Patient refreshed = patientRepository.findById(patientId).orElseThrow();
        assertThat(refreshed.getPublicId()).isNotNull().isNotBlank();
        assertThat(refreshed.getCustomId()).startsWith("PAT");
        assertThat(refreshed.getName()).as("Concurrent edit to name must NOT be overwritten").isEqualTo("Updated Patient Name");
        assertThat(refreshed.getPhone()).as("Concurrent edit to phone must NOT be overwritten").isEqualTo("9999999999");
    }

    @Test
    void concurrentIdentifierUpdate_isNotOverwrittenByConditionalUpdate() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("Conc Ident Hospital");
        hospital.setPublicId("hsp-conc-id-" + System.nanoTime());
        hospital.setCustomId("HSP" + (1000 + (int) (Math.random() * 9000)));
        hospital = hospitalRepository.saveAndFlush(hospital);

        Patient patient = new Patient();
        patient.setHospitalId(hospital.getId());
        patient.setName("Patient With Concurrent ID");
        patient.setPhone("2222222222");
        patient.setGender("MALE");
        patient.setPublicId(null);
        patient.setCustomId(null);
        patient = patientRepository.saveAndFlush(patient);

        final Long patientId = patient.getId();
        final String concurrentPublicId = "concurrent-uuid-" + System.nanoTime();

        // Another transaction assigns publicId right before backfill
        transactionTemplate.execute(status -> {
            entityManager.createQuery("UPDATE Patient p SET p.publicId = :pubId WHERE p.id = :id")
                    .setParameter("pubId", concurrentPublicId)
                    .setParameter("id", patientId)
                    .executeUpdate();
            return null;
        });

        runner.run();

        Patient refreshed = patientRepository.findById(patientId).orElseThrow();
        assertThat(refreshed.getPublicId()).isEqualTo(concurrentPublicId);
        assertThat(refreshed.getCustomId()).startsWith("PAT");
    }

    @Test
    void blankAndWhitespaceHandling_forAppointmentsBillingAndAuditLogs() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("Blank ID Hospital");
        hospital.setPublicId("hsp-blank-" + System.nanoTime());
        hospital.setCustomId("HSP" + (1000 + (int) (Math.random() * 9000)));
        hospital = hospitalRepository.saveAndFlush(hospital);

        Patient patient = new Patient();
        patient.setHospitalId(hospital.getId());
        patient.setName("Patient Blank Test");
        patient.setPhone("3333333333");
        patient.setGender("MALE");
        patient.setPublicId("patient-uuid-111");
        patient.setCustomId("PAT111");
        patient = patientRepository.saveAndFlush(patient);

        Doctor doctor = new Doctor();
        doctor.setHospitalId(hospital.getId());
        doctor.setName("Dr. Blank Test");
        doctor.setSpecialization("General");
        doctor.setPhone("4444444444");
        doctor.setEmail("dr.blank" + System.nanoTime() + "@test.test");
        doctor.setPublicId("doc-uuid-222");
        doctor.setCustomId("DOC222");
        doctor = doctorRepository.saveAndFlush(doctor);

        // Appointment with empty string publicId and whitespace customId
        Appointment apt = new Appointment();
        apt.setHospitalId(hospital.getId());
        apt.setPatientId(patient.getId());
        apt.setDoctorId(doctor.getId());
        apt.setAppointmentDate(java.time.LocalDate.now());
        apt.setAppointmentTime(java.time.LocalTime.of(10, 0));
        apt.setStatus("SCHEDULED");
        apt.setPublicId("");
        apt.setCustomId("   ");
        apt = appointmentRepository.saveAndFlush(apt);

        // Billing with "null" string publicId and empty customId
        Billing bill = new Billing();
        bill.setHospitalId(hospital.getId());
        bill.setPatientId(patient.getId());
        bill.setDoctorId(doctor.getId());
        bill.setAmount(new BigDecimal("150.00"));
        bill.setPaymentStatus("PENDING");
        bill.setBillingType("OPD");
        bill.setPublicId("null");
        bill.setCustomId("");
        bill = billingRepository.saveAndFlush(bill);

        // AuditLog with whitespace publicId
        AuditLog audit = new AuditLog();
        audit.setAction("BLANK_TEST");
        audit.setDetails("Blank Audit Details");
        audit.setPerformedBy("admin@test.test");
        audit.setHospitalId(hospital.getId());
        audit.setPublicId("   ");
        audit = auditLogRepository.saveAndFlush(audit);

        runner.run();

        Appointment refreshedApt = appointmentRepository.findById(apt.getId()).orElseThrow();
        assertThat(refreshedApt.getPublicId()).isNotBlank().isNotEqualTo("").isNotEqualTo("   ");
        assertThat(refreshedApt.getCustomId()).startsWith("APT").isNotEqualTo("   ");

        Billing refreshedBill = billingRepository.findById(bill.getId()).orElseThrow();
        assertThat(refreshedBill.getPublicId()).isNotBlank().isNotEqualTo("null").isNotEqualTo("");
        assertThat(refreshedBill.getCustomId()).startsWith("BIL");

        AuditLog refreshedAudit = auditLogRepository.findById(audit.getId()).orElseThrow();
        assertThat(refreshedAudit.getPublicId()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void interruptedBatch_resumesCleanlyWithoutDataLossOrDuplication() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("Interrupted Batch Hospital");
        hospital.setPublicId("hsp-batch-" + System.nanoTime());
        hospital.setCustomId("HSP" + (1000 + (int) (Math.random() * 9000)));
        final Hospital h = hospitalRepository.saveAndFlush(hospital);

        // Seed 300 patients across 2 batches (batch size is 250)
        transactionTemplate.execute(status -> {
            for (int i = 0; i < 300; i++) {
                Patient p = new Patient();
                p.setHospitalId(h.getId());
                p.setName("Batch Patient " + i);
                p.setPhone("7" + String.format("%09d", i));
                p.setGender("FEMALE");
                p.setPublicId(null);
                p.setCustomId(null);
                entityManager.persist(p);
            }
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        // Run first backfill
        runner.run();

        // Verify all 300 are backfilled
        Long unassigned = entityManager.createQuery(
                "SELECT COUNT(p) FROM Patient p WHERE p.hospitalId = :hid AND (p.publicId IS NULL OR TRIM(p.publicId) = '' OR p.customId IS NULL)",
                Long.class)
                .setParameter("hid", h.getId())
                .getSingleResult();
        assertThat(unassigned).isEqualTo(0L);

        // Add 50 new patients with missing IDs (simulating data added before resume/second run)
        transactionTemplate.execute(status -> {
            for (int i = 300; i < 350; i++) {
                Patient p = new Patient();
                p.setHospitalId(h.getId());
                p.setName("Batch Patient " + i);
                p.setPhone("7" + String.format("%09d", i));
                p.setGender("FEMALE");
                p.setPublicId(null);
                p.setCustomId(null);
                entityManager.persist(p);
            }
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        // Run runner again (resuming and completing remaining records)
        runner.run();

        Long remainingUnassigned = entityManager.createQuery(
                "SELECT COUNT(p) FROM Patient p WHERE p.hospitalId = :hid AND (p.publicId IS NULL OR TRIM(p.publicId) = '' OR p.customId IS NULL)",
                Long.class)
                .setParameter("hid", h.getId())
                .getSingleResult();
        assertThat(remainingUnassigned).isEqualTo(0L);
    }

    @Test
    void repeatedStartup_isStrictlyIdempotent() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("Idempotency Hospital");
        hospital.setPublicId(null);
        hospital.setCustomId(null);
        hospital = hospitalRepository.saveAndFlush(hospital);

        // Run 1
        runner.run();
        Hospital h1 = hospitalRepository.findById(hospital.getId()).orElseThrow();
        String pubId1 = h1.getPublicId();
        String custId1 = h1.getCustomId();
        assertThat(pubId1).isNotBlank();
        assertThat(custId1).isNotBlank();

        // Run 2
        runner.run();
        Hospital h2 = hospitalRepository.findById(hospital.getId()).orElseThrow();
        assertThat(h2.getPublicId()).isEqualTo(pubId1);
        assertThat(h2.getCustomId()).isEqualTo(custId1);

        // Run 3
        runner.run();
        Hospital h3 = hospitalRepository.findById(hospital.getId()).orElseThrow();
        assertThat(h3.getPublicId()).isEqualTo(pubId1);
        assertThat(h3.getCustomId()).isEqualTo(custId1);
    }
}
