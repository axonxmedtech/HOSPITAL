package com.hms.integration;

import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.exception.DuplicatePhoneConflictException;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The database half of the patient duplicate-phone invariant, proven on real MySQL (S-PID-D).
 *
 * <p>Phase A asks a human whether a second registration on one number is a different person. Two
 * requests can both pass that check before either commits — this is the test that says what happens
 * then. It needs a real database because the thing under test IS a unique index over a generated
 * column: H2 would not evaluate the expression the same way, and a mocked repository would prove
 * nothing at all.
 *
 * <pre>
 * mvn verify -Dit.test=PatientDuplicatePhoneConcurrencyIT
 *   -Dhms.it.mysql.url="jdbc:mysql://localhost:3306/hms_it?createDatabaseIfNotExist=true&amp;useSSL=false&amp;allowPublicKeyRetrieval=true"
 *   -Dhms.it.mysql.username=root -Dhms.it.mysql.password=****
 * </pre>
 *
 * <p>Point it at a THROWAWAY schema: the context runs {@code ddl-auto=create-drop}. Migrations are
 * left ENABLED so the generated column and unique index are created by the same
 * {@code DatabaseMigrationRunner} path a developer's machine uses — the constraint being tested is
 * therefore the one the application actually ships, not one the test invented.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "hms.it.mysql.url", matches = ".+")
class PatientDuplicatePhoneConcurrencyIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("hms.it.mysql.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("hms.it.mysql.username", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("hms.it.mysql.password", ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("hms.migrations.enabled", () -> "true");
        registry.add("spring.cache.type", () -> "simple");
    }

    @Autowired PatientService patientService;
    @Autowired PatientRepository patientRepository;
    @Autowired HospitalRepository hospitalRepository;

    @MockBean SecurityContextHelper securityHelper;

    private Long hospitalA;
    private Long hospitalB;

    private String uniq() { return Long.toString(System.nanoTime()); }

    /** Ten digits, matching Patient's @Pattern, and unique per test so cases cannot collide. */
    private String freshPhone() {
        String tail = uniq();
        return "99" + tail.substring(tail.length() - 8);
    }

    @BeforeEach
    void setUp() {
        hospitalA = tenant();
        hospitalB = tenant();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalA);
        when(securityHelper.getCurrentUserEmail()).thenReturn("reception@qa.test");
    }

    private Long tenant() {
        Hospital h = new Hospital();
        h.setName("H-" + uniq());
        h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD", "IPD", "BILLING"));
        h.setIsSingleDoctor(false);
        return hospitalRepository.save(h).getId();
    }

    private Patient newPatient(String name, String phone) {
        Patient p = new Patient();
        p.setName(name);
        p.setPhone(phone);
        p.setGender("MALE");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p.setAddress("QA");
        p.setIsActive(true);
        return p;
    }

    private long activeUnacknowledged(Long hospitalId, String phone) {
        return patientRepository.findActiveByPhoneOrdered(phone, hospitalId).stream()
                .filter(p -> p.getDuplicatePhoneAckFor() == null
                        || !p.getDuplicatePhoneAckFor().equals(p.getPhone()))
                .count();
    }

    // ── the race ──────────────────────────────────────────────────────────────

    @Test
    void twoConcurrentUnacknowledgedRegistrationsProduceExactlyOnePatient() throws Exception {
        String phone = freshPhone();
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger structuredConflicts = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?>[] runs = new Future<?>[2];
            for (int i = 0; i < 2; i++) {
                final String name = "Racer " + i;
                runs[i] = pool.submit(() -> {
                    bothReady.countDown();
                    go.await(10, TimeUnit.SECONDS); // release both at the same instant
                    try {
                        patientService.addPatient(newPatient(name, phone), false);
                        created.incrementAndGet();
                    } catch (DuplicatePhoneConflictException expected) {
                        structuredConflicts.incrementAndGet();
                    } catch (RuntimeException unexpected) {
                        other.incrementAndGet();
                    }
                    return null;
                });
            }
            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();
            for (Future<?> run : runs) run.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(created.get()).as("exactly one registration may win").isEqualTo(1);
        assertThat(structuredConflicts.get())
                .as("the loser must get the SAME structured conflict a detected duplicate gets, "
                        + "not a 500 and not a generic constraint error").isEqualTo(1);
        assertThat(other.get()).as("no other failure mode is acceptable").isZero();
        assertThat(activeUnacknowledged(hospitalA, phone))
                .as("the database must hold exactly one active unacknowledged patient on this number")
                .isEqualTo(1);
    }

    // ── everything the constraint must still allow ────────────────────────────

    @Test
    void thesamePhoneInAnotherHospitalIsUnaffected() {
        String phone = freshPhone();
        patientService.addPatient(newPatient("Tenant A", phone), false);

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalB);
        patientService.addPatient(newPatient("Tenant B", phone), false);

        assertThat(activeUnacknowledged(hospitalA, phone)).isEqualTo(1);
        assertThat(activeUnacknowledged(hospitalB, phone))
                .as("uniqueness is tenant-local; another hospital's number is none of its business")
                .isEqualTo(1);
    }

    @Test
    void acknowledgedSharersAreAllowed_andThereIsNoCapOnThem() {
        String phone = freshPhone();
        patientService.addPatient(newPatient("Parent", phone), false);
        patientService.addPatient(newPatient("Child", phone), true);
        patientService.addPatient(newPatient("Third family member", phone), true);

        assertThat(patientRepository.findActiveByPhoneOrdered(phone, hospitalA))
                .as("a family on one mobile is a real thing and must keep working").hasSize(3);
        assertThat(activeUnacknowledged(hospitalA, phone))
                .as("only the first holds the number; the rest are acknowledged sharers").isEqualTo(1);
    }

    @Test
    void twoSimultaneousAcknowledgedRegistrationsMayBothSucceed() throws Exception {
        String phone = freshPhone();
        patientService.addPatient(newPatient("Holder", phone), false);

        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?>[] runs = new Future<?>[2];
            for (int i = 0; i < 2; i++) {
                final String name = "Ack " + i;
                runs[i] = pool.submit(() -> {
                    go.await(10, TimeUnit.SECONDS);
                    patientService.addPatient(newPatient(name, phone), true);
                    created.incrementAndGet();
                    return null;
                });
            }
            go.countDown();
            for (Future<?> run : runs) run.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(created.get())
                .as("two humans each said 'different person' — product policy allows both")
                .isEqualTo(2);
    }

    @Test
    void aDeactivatedPatientReleasesTheNumber() {
        String phone = freshPhone();
        Patient first = patientService.addPatient(newPatient("Leaver", phone), false);
        first.setIsActive(false);
        patientRepository.save(first);

        patientService.addPatient(newPatient("Newcomer", phone), false);

        assertThat(activeUnacknowledged(hospitalA, phone))
                .as("a soft-deleted patient competes for nothing").isEqualTo(1);
    }

    @Test
    void aStaleAcknowledgementCannotBypassTheConstraint() {
        String phoneA = freshPhone();
        String phoneB = freshPhone();

        // Acknowledged for phoneA, then moved onto phoneB behind the application's back, leaving
        // the acknowledgement pointing at a number this patient no longer holds.
        patientService.addPatient(newPatient("Holder A", phoneA), false);
        Patient sharer = patientService.addPatient(newPatient("Sharer", phoneA), true);
        sharer.setPhone(phoneB);
        patientRepository.saveAndFlush(sharer);

        // phoneB is now held by an effectively unacknowledged row, so a second one must be refused
        // by the DATABASE even though no application check ran on this path.
        Patient intruder = newPatient("Intruder", phoneB);
        intruder.setHospitalId(hospitalA);
        assertThat(catchViolation(intruder))
                .as("the key tests ack_for = phone, so a stale acknowledgement exempts nothing")
                .isTrue();
    }

    private boolean catchViolation(Patient patient) {
        try {
            patientRepository.saveAndFlush(patient);
            return false;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return com.hms.service.hospital.DuplicatePhoneConstraint.isViolation(e);
        }
    }
}
