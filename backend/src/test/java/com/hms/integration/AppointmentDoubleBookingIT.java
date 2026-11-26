package com.hms.integration;

import com.hms.entity.Appointment;
import com.hms.entity.Doctor;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.LocalTime;
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
 * Two desks book the same doctor, date and time at the same instant, on real MySQL. Before the
 * fix the slot check was a plain read followed by an insert, so both could pass. Now the doctor's
 * row is locked and the day is read with FOR UPDATE: exactly one booking wins, the other gets the
 * ordinary "already booked" refusal.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "hms.it.mysql.url", matches = ".+")
class AppointmentDoubleBookingIT {

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

    @Autowired AppointmentService appointmentService;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired PatientRepository patientRepository;

    @MockBean SecurityContextHelper securityHelper;

    private Long hospitalId;
    private Long doctorId;
    private Long patientA;
    private Long patientB;

    private String uniq() { return Long.toString(System.nanoTime()); }

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("H-" + uniq());
        h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD", "APPOINTMENTS", "BILLING"));
        h.setIsSingleDoctor(false);
        hospitalId = hospitalRepository.save(h).getId();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("reception@qa.test");

        Doctor d = new Doctor();
        d.setName("Doctor Slot");
        d.setHospitalId(hospitalId);
        d.setIsActive(true);
        d.setEmail("doc-" + uniq() + "@slot.test");
        d.setPublicId("dpub-" + uniq());
        d.setPhone("9800000002");
        d.setSpecialization("Gen");
        doctorId = doctorRepository.save(d).getId();

        patientA = patient("Patient A", "9900000011");
        patientB = patient("Patient B", "9900000012");
    }

    private Long patient(String name, String phone) {
        Patient p = new Patient();
        p.setName(name);
        p.setHospitalId(hospitalId);
        p.setPublicId("ppub-" + uniq());
        p.setGender("MALE");
        p.setPhone(phone);
        p.setIsActive(true);
        p.setDateOfBirth(LocalDate.of(1985, 1, 1));
        return patientRepository.save(p).getId();
    }

    private Appointment raw(Long patientId, LocalDate day, LocalTime slot, String status) {
        Appointment a = new Appointment();
        a.setHospitalId(hospitalId);
        a.setPatientId(patientId);
        a.setDoctorId(doctorId);
        a.setAppointmentDate(day);
        a.setAppointmentTime(slot);
        a.setStatus(status);
        a.setIsActive(true);
        return a;
    }

    /**
     * V49: even a write path that skipped the application's lock cannot double-book -- the
     * database refuses the second active booking. A cancelled booking has released the slot.
     */
    @Test
    void theDatabaseItselfRefusesASecondActiveBookingInTheSlot() {
        LocalDate day = LocalDate.now().plusDays(5);
        LocalTime slot = LocalTime.of(9, 0);
        appointmentRepository.saveAndFlush(raw(patientA, day, slot, "SCHEDULED"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> appointmentRepository.saveAndFlush(raw(patientB, day, slot, "SCHEDULED")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        LocalTime other = LocalTime.of(9, 30);
        appointmentRepository.saveAndFlush(raw(patientA, day, other, "CANCELLED"));
        appointmentRepository.saveAndFlush(raw(patientB, day, other, "SCHEDULED")); // slot was released
    }

    @Test
    void twoDesksBookingTheSameSlotAtOnce_exactlyOneWins() throws Exception {
        LocalDate day = LocalDate.now().plusDays(3);
        LocalTime slot = LocalTime.of(11, 30);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Long[] patients = {patientA, patientB};
            Future<?>[] runs = new Future<?>[2];
            for (int i = 0; i < 2; i++) {
                final Long patientId = patients[i];
                runs[i] = pool.submit(() -> {
                    Appointment a = new Appointment();
                    a.setPatientId(patientId);
                    a.setDoctorId(doctorId);
                    a.setAppointmentDate(day);
                    a.setAppointmentTime(slot);
                    bothReady.countDown();
                    go.await(10, TimeUnit.SECONDS); // release both at the same instant
                    try {
                        appointmentService.createAppointment(a);
                        booked.incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        if (e.getMessage() != null && e.getMessage().contains("already booked")) refused.incrementAndGet();
                        else other.incrementAndGet();
                    } catch (RuntimeException e) {
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

        assertThat(booked.get()).as("exactly one booking may take the slot").isEqualTo(1);
        assertThat(refused.get()).as("the other gets the ordinary 'already booked' refusal").isEqualTo(1);
        assertThat(other.get()).as("no deadlock or 500").isZero();
        long inSlot = appointmentRepository.findByDoctorIdAndAppointmentDateAndIsActiveTrue(doctorId, day).stream()
                .filter(x -> slot.equals(x.getAppointmentTime())).count();
        assertThat(inSlot).as("the database holds one appointment in that slot").isEqualTo(1);
    }
}
