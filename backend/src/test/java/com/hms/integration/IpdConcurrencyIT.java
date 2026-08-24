package com.hms.integration;

import com.hms.entity.Bed;
import com.hms.entity.BedStatus;
import com.hms.entity.Doctor;
import com.hms.entity.Hospital;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Opd;
import com.hms.entity.Patient;
import com.hms.entity.Ward;
import com.hms.repository.BedRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.OpdRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.WardRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.IpdAdmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * E1 (C2, C3) — the concurrency gates, against a REAL MySQL.
 *
 * <p>These cannot run on H2. The defects are InnoDB row-lock and unique-index behaviour under
 * genuine parallelism; an in-memory database will happily let both threads through and report
 * green, which is worse than no test at all. Hence {@code AbstractMySqlIT} (Testcontainers) —
 * and a skip because Docker is absent is <b>not</b> a pass.
 *
 * <p>Before E1 the same scenarios produced: two admissions holding one bed (C3), and a
 * duplicate-key 500 for a perfectly legitimate second admission (C2).
 */
class IpdConcurrencyIT extends AbstractMySqlIT {

    @Autowired IpdAdmissionService service;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired WardRepository wardRepository;
    @Autowired BedRepository bedRepository;
    @Autowired OpdRepository opdRepository;
    @Autowired IpdAdmissionRepository ipdAdmissionRepository;

    /** The tenant of every thread in these tests. */
    @MockBean SecurityContextHelper securityHelper;

    private Long hospitalId;
    private Long wardId;

    private String uniq() { return Long.toString(System.nanoTime()); }

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("H-" + uniq());
        h.setCustomId("HID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD", "IPD", "BILLING"));
        h.setIsSingleDoctor(false);
        hospitalId = hospitalRepository.save(h).getId();

        Ward w = new Ward();
        w.setWardName("W-" + uniq());
        w.setHospitalId(hospitalId);
        w.setBedPrice(new BigDecimal("1500"));
        w.setTotalBeds(4);
        wardId = wardRepository.save(w).getWardId();

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserId()).thenReturn(1L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@e1.test");
        when(securityHelper.getCurrentUserRole()).thenReturn("HOSPITAL_ADMIN");
    }

    private Long newBed() {
        Bed b = new Bed();
        b.setHospitalId(hospitalId);
        b.setWardId(wardId);
        b.setBedCode("BED-" + uniq());
        b.setStatus(BedStatus.AVAILABLE);
        return bedRepository.save(b).getBedId();
    }

    private Long newOpdCase() {
        Doctor d = new Doctor();
        d.setName("Doctor Who");
        d.setHospitalId(hospitalId);
        d.setIsActive(true);
        d.setEmail("doc-" + uniq() + "@e1.test");
        d.setPublicId("dpub-" + uniq());
        d.setPhone("9800000001");
        d.setSpecialization("Gen");
        doctorRepository.save(d);

        Patient p = new Patient();
        p.setName("Test Patient");
        p.setHospitalId(hospitalId);
        p.setPublicId("ppub-" + uniq());
        p.setGender("MALE");
        p.setPhone("9900000001");
        p.setIsActive(true);
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        patientRepository.save(p);

        Opd o = new Opd();
        o.setCaseId("OPD-" + uniq());
        o.setIpdAdmitRecommended(true);
        o.setPatient(p);
        o.setDoctor(d);
        return opdRepository.save(o).getId();
    }

    /** Result of one racing call: the admission, or the exception it failed with. */
    private record Outcome(IpdAdmission admission, Throwable error) {
        boolean succeeded() { return admission != null; }
    }

    /** Runs the given calls truly in parallel, released together by one latch. */
    private List<Outcome> race(List<Callable<IpdAdmission>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        try {
            for (Callable<IpdAdmission> call : calls) {
                futures.add(pool.submit(() -> {
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        return new Outcome(call.call(), null);
                    } catch (Throwable t) {
                        return new Outcome(null, t);
                    }
                }));
            }
            go.countDown();
            List<Outcome> out = new ArrayList<>();
            for (Future<Outcome> f : futures) out.add(f.get(60, TimeUnit.SECONDS));
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    // ── T4: two admissions, one bed ──────────────────────────────────────────

    @Test
    void twoSimultaneousAdmissionsToTheSameBed_exactlyOneWins() throws Exception {
        Long bedId = newBed();
        Long opdA = newOpdCase();
        Long opdB = newOpdCase();

        List<Outcome> results = race(List.of(
                () -> service.admitFromOpd(opdA, wardId, bedId, "ELECTIVE", "race A"),
                () -> service.admitFromOpd(opdB, wardId, bedId, "ELECTIVE", "race B")));

        long wins = results.stream().filter(Outcome::succeeded).count();
        assertThat(wins).as("exactly one admission may take the bed").isEqualTo(1);

        Outcome loser = results.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow();
        assertThat(loser.error()).as("the loser is told the bed is gone, not handed a 500")
                .isInstanceOf(com.hms.exception.ConflictException.class);

        List<IpdAdmission> onThatBed = ipdAdmissionRepository.findAll().stream()
                .filter(a -> bedId.equals(a.getBedId()))
                .toList();
        assertThat(onThatBed).as("one bed, one admission").hasSize(1);

        Bed bed = bedRepository.findById(bedId).orElseThrow();
        assertThat(bed.getStatus()).isEqualToIgnoringCase(BedStatus.OCCUPIED);
        assertThat(bed.getCurrentIpdAdmissionId())
                .as("the bed points at the winner")
                .isEqualTo(onThatBed.get(0).getId());
    }

    // ── T5: two admissions, different beds, distinct IPD numbers ─────────────

    @Test
    void twoSimultaneousAdmissionsToDifferentBeds_bothSucceedWithDistinctIpdNumbers() throws Exception {
        Long bedA = newBed();
        Long bedB = newBed();
        Long opdA = newOpdCase();
        Long opdB = newOpdCase();

        List<Outcome> results = race(List.of(
                () -> service.admitFromOpd(opdA, wardId, bedA, "ELECTIVE", "seq A"),
                () -> service.admitFromOpd(opdB, wardId, bedB, "ELECTIVE", "seq B")));

        assertThat(results).allSatisfy(o ->
                assertThat(o.succeeded())
                        .as("both admissions are legitimate; neither may fail: %s", o.error())
                        .isTrue());

        String n1 = results.get(0).admission().getIpdNumber();
        String n2 = results.get(1).admission().getIpdNumber();
        assertThat(n1).as("IPD numbers must be distinct").isNotEqualTo(n2);
        assertThat(ipdAdmissionRepository.findAll().stream().map(IpdAdmission::getIpdNumber).distinct().count())
                .as("no duplicate IPD number exists anywhere")
                .isEqualTo(ipdAdmissionRepository.count());
    }

    // ── T6: two transfers onto one target bed ────────────────────────────────

    @Test
    void twoSimultaneousBedChangesOntoTheSameTarget_exactlyOneWins() throws Exception {
        Long bedA = newBed();
        Long bedB = newBed();
        Long target = newBed();

        IpdAdmission a = service.admitFromOpd(newOpdCase(), wardId, bedA, "ELECTIVE", "A");
        IpdAdmission b = service.admitFromOpd(newOpdCase(), wardId, bedB, "ELECTIVE", "B");

        List<Outcome> results = race(List.of(
                () -> service.changeBed(a.getId(), target),
                () -> service.changeBed(b.getId(), target)));

        long wins = results.stream().filter(Outcome::succeeded).count();
        assertThat(wins).as("exactly one transfer may take the target bed").isEqualTo(1);

        Outcome loser = results.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow();
        assertThat(loser.error()).isInstanceOf(com.hms.exception.ConflictException.class);

        List<IpdAdmission> onTarget = ipdAdmissionRepository.findAll().stream()
                .filter(x -> target.equals(x.getBedId()))
                .toList();
        assertThat(onTarget).as("one target bed, one occupant").hasSize(1);

        // The loser must still be where it started.
        Long winnerId = onTarget.get(0).getId();
        Long loserId = winnerId.equals(a.getId()) ? b.getId() : a.getId();
        Long loserOriginalBed = winnerId.equals(a.getId()) ? bedB : bedA;
        assertThat(ipdAdmissionRepository.findById(loserId).orElseThrow().getBedId())
                .as("the loser keeps its original bed")
                .isEqualTo(loserOriginalBed);
    }
}
