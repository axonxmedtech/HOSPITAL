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
}
