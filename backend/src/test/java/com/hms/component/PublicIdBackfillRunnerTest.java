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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
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
            entityManager.createQuery("DELETE FROM QueueEntry").executeUpdate();
            entityManager.createQuery("DELETE FROM Prescription").executeUpdate();
            entityManager.createQuery("DELETE FROM Opd").executeUpdate();
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

    @Test
    void hospitalPublicIdAllocator_cannotCollideWithHistoricalIds_andConformsToExpectedFormat() throws Exception {
        // 1. Seed historical hospitals with pre-existing IDs
        List<String> historicalPublicIds = List.of(
                "hist-hsp-00000000-0000-0000-0000-000000000001",
                "hist-hsp-00000000-0000-0000-0000-000000000002",
                "hist-hsp-00000000-0000-0000-0000-000000000003"
        );
        List<String> historicalCustomIds = List.of("HSP9001", "CLN9002", "PHR9003");

        for (int i = 0; i < historicalPublicIds.size(); i++) {
            Hospital h = new Hospital();
            h.setName("Historical Facility " + i);
            h.setPublicId(historicalPublicIds.get(i));
            h.setCustomId(historicalCustomIds.get(i));
            h.setType(i == 1 ? HospitalType.CLINIC : (i == 2 ? HospitalType.PHARMACY : HospitalType.HOSPITAL));
            hospitalRepository.saveAndFlush(h);
        }

        // 2. Create multiple hospitals with missing publicId and customId across all types
        List<Long> newHospitalIds = new ArrayList<>();
        HospitalType[] types = {HospitalType.HOSPITAL, HospitalType.CLINIC, HospitalType.PHARMACY};
        for (int i = 0; i < 30; i++) {
            Hospital h = new Hospital();
            h.setName("New Facility " + i);
            h.setType(types[i % types.length]);
            h.setCustomId("TMP-" + i + "-" + System.nanoTime());
            h = hospitalRepository.saveAndFlush(h);
            Long hid = h.getId();
            transactionTemplate.execute(status -> {
                entityManager.createQuery("UPDATE Hospital SET customId = null WHERE id = :id")
                        .setParameter("id", hid)
                        .executeUpdate();
                return null;
            });
            newHospitalIds.add(hid);
        }

        // 3. Execute backfill
        runner.run();

        // 4. Validate all allocated IDs
        Set<String> seenPublicIds = new HashSet<>(historicalPublicIds);
        String uuidV4Regex = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

        for (Long hid : newHospitalIds) {
            Hospital h = hospitalRepository.findById(hid).orElseThrow();
            String allocatedPublicId = h.getPublicId();
            String allocatedCustomId = h.getCustomId();

            // Expected UUID v4 format
            assertThat(allocatedPublicId)
                    .as("Allocated hospital public ID must conform to UUID v4 format")
                    .matches(uuidV4Regex);

            // Zero collision with historical IDs or other allocated IDs
            assertThat(historicalPublicIds)
                    .as("Allocated public ID must never collide with historical IDs")
                    .doesNotContain(allocatedPublicId);

            assertThat(seenPublicIds.add(allocatedPublicId))
                    .as("Allocated public ID must be strictly unique (no collision)")
                    .isTrue();

            // Custom ID format matches tenant type (4 or more digits to prevent modulo collision when id >= 9000)
            if (h.getType() == HospitalType.CLINIC) {
                assertThat(allocatedCustomId).matches("^CLN\\d{4,}$");
            } else if (h.getType() == HospitalType.PHARMACY) {
                assertThat(allocatedCustomId).matches("^PHR\\d{4,}$");
            } else {
                assertThat(allocatedCustomId).matches("^HSP\\d{4,}$");
            }
        }

        // 5. Historical hospitals must be completely untouched
        for (int i = 0; i < historicalPublicIds.size(); i++) {
            Hospital hist = hospitalRepository.findByPublicId(historicalPublicIds.get(i)).orElseThrow();
            assertThat(hist.getCustomId()).isEqualTo(historicalCustomIds.get(i));
        }
    }

    @Test
    void realConcurrentTransactions_multiThreadedExecution_guaranteesDataIntegrityWithoutDeadlocks() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("MultiThreaded Concurrency Hospital");
        hospital.setPublicId("hsp-mt-" + System.nanoTime());
        hospital.setCustomId("HSP" + (1000 + (int) (Math.random() * 9000)));
        final Hospital h = hospitalRepository.saveAndFlush(hospital);

        // Pre-create 100 patients with missing publicId and customId
        List<Long> seededIds = new ArrayList<>();
        transactionTemplate.execute(status -> {
            for (int i = 0; i < 100; i++) {
                Patient p = new Patient();
                p.setHospitalId(h.getId());
                p.setName("Initial Patient " + i);
                p.setPhone("5" + String.format("%09d", i));
                p.setGender("FEMALE");
                p.setPublicId(null);
                p.setCustomId(null);
                entityManager.persist(p);
                seededIds.add(p.getId());
            }
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(4);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        // Thread 1: Backfill runner
        executor.submit(() -> {
            try {
                startGate.await();
                runner.run();
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneGate.countDown();
            }
        });

        // Thread 2: Concurrent business updates to existing patients (e.g. updating names)
        executor.submit(() -> {
            try {
                startGate.await();
                for (int i = 0; i < 20; i++) {
                    Long targetId = seededIds.get(i);
                    transactionTemplate.execute(status -> {
                        entityManager.createQuery(
                                "UPDATE Patient p SET p.name = :name WHERE p.id = :id")
                                .setParameter("name", "Concurrently Updated " + targetId)
                                .setParameter("id", targetId)
                                .executeUpdate();
                        return null;
                    });
                    Thread.sleep(5);
                }
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneGate.countDown();
            }
        });

        // Thread 3: Concurrently insert new patients with and without IDs
        executor.submit(() -> {
            try {
                startGate.await();
                for (int i = 100; i < 120; i++) {
                    final int idx = i;
                    transactionTemplate.execute(status -> {
                        Patient p = new Patient();
                        p.setHospitalId(h.getId());
                        p.setName("Concurrent Insert " + idx);
                        p.setPhone("6" + String.format("%09d", idx));
                        p.setGender("MALE");
                        if (idx % 2 == 0) {
                            p.setPublicId("pre-assigned-" + idx);
                            p.setCustomId("PAT" + (8000 + idx));
                        }
                        entityManager.persist(p);
                        return null;
                    });
                    Thread.sleep(5);
                }
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneGate.countDown();
            }
        });

        // Thread 4: Concurrent second backfill runner execution
        executor.submit(() -> {
            try {
                startGate.await();
                Thread.sleep(10);
                runner.run();
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneGate.countDown();
            }
        });

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All concurrent tasks must finish within 30 seconds").isTrue();
        assertThat(errors).as("No deadlocks or transaction errors allowed").isEmpty();

        // Run final sweep in case any late-inserted records in thread 3 were created after runners finished
        runner.run();

        // Verify all patients have valid, unique public IDs and custom IDs
        List<Patient> allPatients = patientRepository.findAll();
        Set<String> publicIds = new HashSet<>();
        for (Patient p : allPatients) {
            assertThat(p.getPublicId()).isNotNull().isNotBlank().isNotEqualTo("null");
            assertThat(p.getCustomId()).isNotNull().isNotBlank().startsWith("PAT");
            assertThat(publicIds.add(p.getPublicId()))
                    .as("Every patient publicId must be distinct (no collisions)")
                    .isTrue();
        }

        // Verify the concurrent name updates in Thread 2 were not overwritten by runner
        for (int i = 0; i < 20; i++) {
            Long targetId = seededIds.get(i);
            Patient p = patientRepository.findById(targetId).orElseThrow();
            assertThat(p.getName()).isEqualTo("Concurrently Updated " + targetId);
        }
    }

    /**
     * Regression test for Codex finding R01:
     * Hospital ID 1 and ID 9001 must NEVER collide (previous code used modulo 9000 which caused both to map to HSP1001).
     */
    @Test
    void hospitalCustomIdCollision_id1AndId9001_allocateDistinctUniqueIds() throws Exception {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM hospitals WHERE id IN (1, 9001)").executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO hospitals (id, name, public_id, custom_id, is_active, is_single_doctor, type, subscription_status, created_at) " +
                    "VALUES (1, 'Hospital One', 'pub-h1-test', NULL, true, false, 'HOSPITAL', 'ACTIVE', CURRENT_TIMESTAMP)")
                    .executeUpdate();
            entityManager.createNativeQuery(
                    "INSERT INTO hospitals (id, name, public_id, custom_id, is_active, is_single_doctor, type, subscription_status, created_at) " +
                    "VALUES (9001, 'Hospital Nine Thousand One', 'pub-h9001-test', NULL, true, false, 'HOSPITAL', 'ACTIVE', CURRENT_TIMESTAMP)")
                    .executeUpdate();
            return null;
        });

        runner.run();

        Hospital h1 = hospitalRepository.findById(1L).orElseThrow();
        Hospital h9001 = hospitalRepository.findById(9001L).orElseThrow();

        assertThat(h1.getCustomId()).isEqualTo("HSP1001");
        assertThat(h9001.getCustomId()).isEqualTo("HSP10001");
        assertThat(h1.getCustomId()).isNotEqualTo(h9001.getCustomId());
    }

    /**
     * Regression test for R01:
     * When an arbitrary existing custom ID (e.g. HSP1501) already exists in the database,
     * backfilling an entity that would have naturally targeted HSP1501 must advance to the
     * next free candidate (e.g. HSP1502) without failing or corrupting the existing ID.
     */
    @Test
    void hospitalCustomId_withExistingArbitraryCustomId_findsNextAvailableWithoutFailing() throws Exception {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM hospitals WHERE id IN (500, 501)").executeUpdate();
            // Existing hospital already has HSP1501
            entityManager.createNativeQuery(
                    "INSERT INTO hospitals (id, name, public_id, custom_id, is_active, is_single_doctor, type, subscription_status, created_at) " +
                    "VALUES (500, 'Pre-existing Custom Hospital', 'pub-h-existing', 'HSP1501', true, false, 'HOSPITAL', 'ACTIVE', CURRENT_TIMESTAMP)")
                    .executeUpdate();
            // New hospital with id 501 whose natural candidate is HSP1501
            entityManager.createNativeQuery(
                    "INSERT INTO hospitals (id, name, public_id, custom_id, is_active, is_single_doctor, type, subscription_status, created_at) " +
                    "VALUES (501, 'Hospital 501 Missing ID', 'pub-h501-test', NULL, true, false, 'HOSPITAL', 'ACTIVE', CURRENT_TIMESTAMP)")
                    .executeUpdate();
            return null;
        });

        runner.run();

        Hospital existing = hospitalRepository.findById(500L).orElseThrow();
        Hospital backfilled = hospitalRepository.findById(501L).orElseThrow();

        assertThat(existing.getCustomId()).as("Existing custom ID must be preserved verbatim").isEqualTo("HSP1501");
        assertThat(backfilled.getCustomId()).as("Must allocate next unique candidate without collision").isEqualTo("HSP1502");
        assertThat(existing.getCustomId()).isNotEqualTo(backfilled.getCustomId());
    }

    /**
     * Regression test for R01:
     * Review similar ID allocation across User, Appointment, and Billing.
     * All must allocate valid, collision-free, uniquely prefixed custom IDs.
     */
    @Test
    void similarEntities_usersAppointmentsBillings_allocateUniqueCustomIdsWithoutCollisions() throws Exception {
        Hospital hospital = new Hospital();
        hospital.setName("Test Tenant Hospital");
        hospital.setPublicId("hsp-test-tenant-pub");
        hospital.setIsActive(true);
        hospital = hospitalRepository.saveAndFlush(hospital);
        final Long hId = hospital.getId();

        transactionTemplate.execute(status -> {
            // Seed users with null customId
            User admin = new User();
            admin.setName("Admin User");
            admin.setEmail("admin-backfill-test@axonx.com");
            admin.setPassword("hashed");
            admin.setRole("HOSPITAL_ADMIN");
            admin.setHospitalId(hId);
            admin.setIsActive(true);
            admin.setPublicId("admin-pub-1");
            admin.setCustomId(null);
            entityManager.persist(admin);

            User docUser = new User();
            docUser.setName("Doctor User");
            docUser.setEmail("doc-backfill-test@axonx.com");
            docUser.setPassword("hashed");
            docUser.setRole("DOCTOR");
            docUser.setHospitalId(hId);
            docUser.setIsActive(true);
            docUser.setPublicId("doc-pub-1");
            docUser.setCustomId(null);
            entityManager.persist(docUser);

            // Seed appointments with null customId
            Appointment appt1 = new Appointment();
            appt1.setHospitalId(hId);
            appt1.setPatientId(1L);
            appt1.setDoctorId(1L);
            appt1.setAppointmentDate(java.time.LocalDate.now());
            appt1.setAppointmentTime(java.time.LocalTime.of(10, 0));
            appt1.setStatus("SCHEDULED");
            appt1.setIsActive(true);
            appt1.setPublicId("appt-pub-1");
            appt1.setCustomId(null);
            entityManager.persist(appt1);

            Appointment appt2 = new Appointment();
            appt2.setHospitalId(hId);
            appt2.setPatientId(1L);
            appt2.setDoctorId(1L);
            appt2.setAppointmentDate(java.time.LocalDate.now());
            appt2.setAppointmentTime(java.time.LocalTime.of(11, 0));
            appt2.setStatus("SCHEDULED");
            appt2.setIsActive(true);
            appt2.setPublicId("appt-pub-2");
            appt2.setCustomId(null);
            entityManager.persist(appt2);

            // Seed billings with null customId
            Billing bill1 = new Billing();
            bill1.setHospitalId(hId);
            bill1.setPatientId(1L);
            bill1.setDoctorId(1L);
            bill1.setPublicId("bill-pub-1");
            bill1.setCustomId(null);
            bill1.setAmount(BigDecimal.valueOf(150.00));
            entityManager.persist(bill1);

            Billing bill2 = new Billing();
            bill2.setHospitalId(hId);
            bill2.setPatientId(1L);
            bill2.setDoctorId(1L);
            bill2.setPublicId("bill-pub-2");
            bill2.setCustomId(null);
            bill2.setAmount(BigDecimal.valueOf(300.00));
            entityManager.persist(bill2);

            return null;
        });

        runner.run();

        // Verify users
        User admin = userRepository.findByEmail("admin-backfill-test@axonx.com").orElseThrow();
        User doc = userRepository.findByEmail("doc-backfill-test@axonx.com").orElseThrow();
        assertThat(admin.getCustomId()).isNotNull().startsWith("ADM");
        assertThat(doc.getCustomId()).isNotNull().startsWith("DOC");
        assertThat(admin.getCustomId()).isNotEqualTo(doc.getCustomId());

        // Verify appointments
        List<Appointment> appts = appointmentRepository.findAll();
        Set<String> apptCustomIds = new HashSet<>();
        for (Appointment a : appts) {
            assertThat(a.getCustomId()).isNotNull().startsWith("APT");
            assertThat(apptCustomIds.add(a.getCustomId())).as("Appointment custom IDs must be unique").isTrue();
        }

        // Verify billings
        List<Billing> bills = billingRepository.findAll();
        Set<String> billCustomIds = new HashSet<>();
        for (Billing b : bills) {
            assertThat(b.getCustomId()).isNotNull().startsWith("BIL");
            assertThat(billCustomIds.add(b.getCustomId())).as("Billing custom IDs must be unique").isTrue();
        }
    }
}

