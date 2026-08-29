package com.hms.security;

import com.hms.entity.Doctor;
import com.hms.entity.Hospital;
import com.hms.entity.MedicalRecord;
import com.hms.entity.Opd;
import com.hms.entity.Patient;
import com.hms.entity.User;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.MedicalRecordRepository;
import com.hms.repository.OpdRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.QueueEntryRepository;
import com.hms.repository.UserRepository;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three ways a follow-up ends without the patient walking in — and the races between them.
 *
 * <p>Rescheduling, completing and cancelling all compete with each other and with Patient
 * Arrived, and the combinations that matter are the ones that would otherwise produce a visit
 * for a follow-up somebody had just called off. Every transition is a conditional UPDATE, so
 * exactly one of any incompatible pair wins and the other is told so.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FollowUpLifecycleTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final List<String> MODULES = List.of("OPD", "IPD", "BILLING");

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitals;
    @Autowired UserRepository users;
    @Autowired DoctorRepository doctors;
    @Autowired PatientRepository patients;
    @Autowired MedicalRecordRepository records;
    @Autowired OpdRepository opds;
    @Autowired QueueEntryRepository queueEntries;
    @Autowired BillingRepository billings;
    @Autowired AuditLogRepository auditLogs;

    private static long uniq() { return System.nanoTime() + SEQ.incrementAndGet(); }

    private Hospital hospital;
    private Doctor doctor;
    private Patient patient;
    private String receptionToken, doctorToken, adminToken, nurseToken;

    private Hospital tenant(String label) {
        Hospital h = new Hospital();
        h.setName("Life " + label);
        h.setCustomId("LIFE-" + uniq());
        h.setIsActive(true);
        h.setSubscriptionStatus("ACTIVE");
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        h.setType(com.hms.entity.HospitalType.HOSPITAL);
        return hospitals.save(h);
    }

    private String tokenFor(Hospital h, String role, String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword("{noop}fixture");
        u.setName("User " + role);
        u.setRole(role);
        u.setHospitalId(h.getId());
        u.setIsActive(true);
        u.setTokenVersion(0);
        u = users.save(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), role, h.getId(),
                MODULES, null, "HOSPITAL", null, 0);
    }

    private Doctor doctorIn(Hospital h, String email) {
        Doctor d = new Doctor();
        d.setHospitalId(h.getId());
        d.setName("Dr Lifecycle");
        d.setEmail(email);
        d.setPublicId("dpub-" + uniq());
        d.setPhone("9800000092");
        d.setSpecialization("General");
        d.setIsActive(true);
        return doctors.save(d);
    }

    private Patient patientIn(Hospital h, String name) {
        Patient p = new Patient();
        p.setHospitalId(h.getId());
        p.setName(name);
        p.setPublicId("ppub-" + uniq());
        p.setGender("MALE");
        p.setPhone("9" + String.format("%09d", Math.floorMod(uniq(), 1_000_000_000L)));
        p.setIsActive(true);
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        return patients.save(p);
    }

    private MedicalRecord followUp(Hospital h, Patient p, LocalDate date, String status) {
        MedicalRecord m = new MedicalRecord();
        m.setHospitalId(h.getId());
        m.setPatientId(p.getId());
        m.setDoctorId(doctor.getId());
        m.setPublicId("mr-" + uniq());
        m.setVisitType("OPD");
        m.setDiagnosis("Hypertension");
        m.setFollowUpDate(date);
        m.setFollowUpStatus(status);
        m.setFollowUpInstructions("Bring the BP diary");
        return records.save(m);
    }

    @BeforeEach
    void setUp() {
        hospital = tenant("Alpha");
        String docEmail = "doc." + uniq() + "@life.test";
        doctor = doctorIn(hospital, docEmail);
        patient = patientIn(hospital, "Lifecycle Patient");
        receptionToken = tokenFor(hospital, "RECEPTIONIST", "rec." + uniq() + "@life.test");
        doctorToken = tokenFor(hospital, "DOCTOR", docEmail);
        adminToken = tokenFor(hospital, "HOSPITAL_ADMIN", "adm." + uniq() + "@life.test");
        nurseToken = tokenFor(hospital, "NURSE", "nur." + uniq() + "@life.test");
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> post(String path, String token, String body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), String.class);
    }

    private ResponseEntity<String> reschedule(Long id, LocalDate date, String token) {
        return post("/hospital/follow-ups/" + id + "/reschedule", token,
                "{\"newFollowUpDate\":\"" + date + "\"}");
    }

    private ResponseEntity<String> complete(Long id, String token) {
        return post("/hospital/follow-ups/" + id + "/complete", token, "{\"reason\":\"Improved\"}");
    }

    private ResponseEntity<String> cancel(Long id, String token) {
        return post("/hospital/follow-ups/" + id + "/cancel", token, "{\"reason\":\"Patient moved away\"}");
    }

    private ResponseEntity<String> arrive(Long id, String token) {
        return post("/hospital/follow-ups/" + id + "/arrive", token, "{}");
    }

    private String dueList() {
        return get("/hospital/follow-ups", receptionToken).getBody();
    }

    private long opdsFor(Patient p) {
        return opds.findAll().stream()
                .filter(o -> o.getPatient() != null && p.getId().equals(o.getPatient().getId())).count();
    }

    // ── reschedule ───────────────────────────────────────────────────────────

    @Test
    void anOverdueFollowUpCanBeMovedForward() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now().minusDays(5), null);
        assertThat(dueList()).contains("OVERDUE");

        LocalDate newDate = LocalDate.now().plusDays(7);
        assertThat(reschedule(m.getId(), newDate, receptionToken).getStatusCode().value()).isEqualTo(200);

        MedicalRecord after = records.findById(m.getId()).orElseThrow();
        assertThat(after.getFollowUpDate()).isEqualTo(newDate);
        assertThat(after.getFollowUpStatus()).as("rescheduling leaves it open").isNull();

        // Re-fetched cold: the derived bucket follows the new date with nothing to invalidate.
        String body = dueList();
        assertThat(body).contains("Lifecycle Patient").contains("UPCOMING");
        assertThat(body).doesNotContain("OVERDUE");
    }

    @Test
    void aFollowUpCanBeMovedToToday() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now().plusDays(9), null);
        assertThat(reschedule(m.getId(), LocalDate.now(), doctorToken).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/hospital/follow-ups?timing=DUE_TODAY", receptionToken).getBody())
                .contains("Lifecycle Patient");
    }

    @Test
    void aFutureFollowUpCanBeMovedToAnotherFutureDate() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now().plusDays(3), null);
        LocalDate later = LocalDate.now().plusDays(20);
        assertThat(reschedule(m.getId(), later, receptionToken).getStatusCode().value()).isEqualTo(200);
        assertThat(records.findById(m.getId()).orElseThrow().getFollowUpDate()).isEqualTo(later);
    }

    @Test
    void aFollowUpCannotBeMovedIntoThePast() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        assertThat(reschedule(m.getId(), LocalDate.now().minusDays(1), receptionToken)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(records.findById(m.getId()).orElseThrow().getFollowUpDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void reschedulingCanUpdateTheInstructionsAndLeavesTheDiagnosisAlone() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        assertThat(post("/hospital/follow-ups/" + m.getId() + "/reschedule", doctorToken,
                "{\"newFollowUpDate\":\"" + LocalDate.now().plusDays(4) + "\""
                        + ",\"instructions\":\"Fasting bloods first\"}")
                .getStatusCode().value()).isEqualTo(200);

        MedicalRecord after = records.findById(m.getId()).orElseThrow();
        assertThat(after.getFollowUpInstructions()).isEqualTo("Fasting bloods first");
        assertThat(after.getDiagnosis()).as("the consultation itself is untouched").isEqualTo("Hypertension");
    }

    @Test
    void reschedulingWithoutInstructionsKeepsTheOriginalOnes() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        reschedule(m.getId(), LocalDate.now().plusDays(2), receptionToken);
        assertThat(records.findById(m.getId()).orElseThrow().getFollowUpInstructions())
                .isEqualTo("Bring the BP diary");
    }

    /** The row holds only the current date, so the move itself has to live in the audit trail. */
    @Test
    void aRescheduleRecordsBothDates() {
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().plusDays(6);
        MedicalRecord m = followUp(hospital, patient, from, null);
        reschedule(m.getId(), to, receptionToken);

        assertThat(auditLogs.findAll().stream()
                .filter(a -> "FOLLOW_UP_RESCHEDULED".equals(a.getAction()))
                .anyMatch(a -> a.getDetails() != null
                        && a.getDetails().contains(from.toString())
                        && a.getDetails().contains(to.toString())))
                .as("the audit trail must carry the old date and the new one").isTrue();
    }

    // ── complete and cancel ──────────────────────────────────────────────────

    private void assertNothingClinicalHappened(long opdsBefore, long queuedBefore, long billsBefore) {
        assertThat(opdsFor(patient)).as("no visit").isEqualTo(opdsBefore);
        assertThat(queueEntries.count()).as("nobody queued").isEqualTo(queuedBefore);
        assertThat(billings.findAll().stream().filter(b -> hospital.getId().equals(b.getHospitalId())).count())
                .as("nothing billed").isEqualTo(billsBefore);
    }

    @Test
    void completingClosesTheFollowUpWithoutCreatingAVisit() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        long opdsBefore = opdsFor(patient), queuedBefore = queueEntries.count();
        long billsBefore = billings.findAll().stream()
                .filter(b -> hospital.getId().equals(b.getHospitalId())).count();

        assertThat(complete(m.getId(), doctorToken).getStatusCode().value()).isEqualTo(200);

        MedicalRecord after = records.findById(m.getId()).orElseThrow();
        assertThat(after.getFollowUpStatus()).isEqualTo(MedicalRecord.FOLLOW_UP_COMPLETED);
        assertThat(after.getFollowUpDate()).as("the clinical record is preserved").isEqualTo(LocalDate.now());
        assertThat(after.getFollowUpInstructions()).isEqualTo("Bring the BP diary");
        assertThat(dueList()).doesNotContain("Lifecycle Patient");
        assertNothingClinicalHappened(opdsBefore, queuedBefore, billsBefore);
    }

    @Test
    void cancellingClosesTheFollowUpWithoutCreatingAVisit() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        long opdsBefore = opdsFor(patient), queuedBefore = queueEntries.count();
        long billsBefore = billings.findAll().stream()
                .filter(b -> hospital.getId().equals(b.getHospitalId())).count();

        assertThat(cancel(m.getId(), receptionToken).getStatusCode().value()).isEqualTo(200);

        MedicalRecord after = records.findById(m.getId()).orElseThrow();
        assertThat(after.getFollowUpStatus()).isEqualTo(MedicalRecord.FOLLOW_UP_CANCELLED);
        assertThat(after.getFollowUpDate()).isEqualTo(LocalDate.now());
        assertThat(dueList()).doesNotContain("Lifecycle Patient");
        assertNothingClinicalHappened(opdsBefore, queuedBefore, billsBefore);
    }

    @Test
    void cancellingRequiresAReason() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        assertThat(post("/hospital/follow-ups/" + m.getId() + "/cancel", receptionToken, "{}")
                .getStatusCode().value()).isEqualTo(400);
        assertThat(post("/hospital/follow-ups/" + m.getId() + "/cancel", receptionToken,
                "{\"reason\":\"   \"}").getStatusCode().value()).isEqualTo(400);
        assertThat(records.findById(m.getId()).orElseThrow().getFollowUpStatus()).isNull();
    }

    @Test
    void aCancellationReasonIsRecorded() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        cancel(m.getId(), receptionToken);
        assertThat(auditLogs.findAll().stream()
                .filter(a -> "FOLLOW_UP_CANCELLED".equals(a.getAction()))
                .anyMatch(a -> "Patient moved away".equals(a.getReason())))
                .as("the reason must be retrievable afterwards").isTrue();
    }

    // ── terminal protection ──────────────────────────────────────────────────

    @Test
    void anActionedFollowUpCannotBeChanged() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        assertThat(arrive(m.getId(), receptionToken).getStatusCode().value()).isEqualTo(200);

        assertThat(reschedule(m.getId(), LocalDate.now().plusDays(3), receptionToken)
                .getStatusCode().value()).isEqualTo(409);
        assertThat(complete(m.getId(), doctorToken).getStatusCode().value()).isEqualTo(409);
        assertThat(cancel(m.getId(), receptionToken).getStatusCode().value()).isEqualTo(409);

        MedicalRecord after = records.findById(m.getId()).orElseThrow();
        assertThat(after.getFollowUpStatus()).isEqualTo(MedicalRecord.FOLLOW_UP_ACTIONED);
        assertThat(after.getActionedOpdId()).isNotNull();
    }

    @Test
    void aCompletedFollowUpIsNotReopened() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), MedicalRecord.FOLLOW_UP_COMPLETED);
        assertThat(arrive(m.getId(), receptionToken).getStatusCode().value()).isEqualTo(409);
        assertThat(reschedule(m.getId(), LocalDate.now().plusDays(2), receptionToken)
                .getStatusCode().value()).isEqualTo(409);
        assertThat(cancel(m.getId(), receptionToken).getStatusCode().value()).isEqualTo(409);
        assertThat(opdsFor(patient)).isZero();
    }

    @Test
    void aCancelledFollowUpIsNotReopened() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), MedicalRecord.FOLLOW_UP_CANCELLED);
        assertThat(arrive(m.getId(), receptionToken).getStatusCode().value()).isEqualTo(409);
        assertThat(reschedule(m.getId(), LocalDate.now().plusDays(2), receptionToken)
                .getStatusCode().value()).isEqualTo(409);
        assertThat(complete(m.getId(), doctorToken).getStatusCode().value()).isEqualTo(409);
        assertThat(opdsFor(patient)).isZero();
    }

    // ── authorisation ────────────────────────────────────────────────────────

    @Test
    void receptionMayRescheduleAndCancelButNotCompleteClinically() {
        MedicalRecord a = followUp(hospital, patient, LocalDate.now(), null);
        assertThat(reschedule(a.getId(), LocalDate.now().plusDays(1), receptionToken)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(cancel(a.getId(), receptionToken).getStatusCode().value()).isEqualTo(200);

        MedicalRecord b = followUp(hospital, patientIn(hospital, "Other"), LocalDate.now(), null);
        assertThat(complete(b.getId(), receptionToken).getStatusCode().value())
                .as("deciding a patient need not be seen is a clinical call").isEqualTo(403);
    }

    @Test
    void doctorsAndAdminsMayCompleteAFollowUp() {
        for (String token : new String[]{doctorToken, adminToken}) {
            MedicalRecord m = followUp(hospital, patientIn(hospital, "P" + uniq()), LocalDate.now(), null);
            assertThat(complete(m.getId(), token).getStatusCode().value()).isEqualTo(200);
        }
    }

    @Test
    void aNurseHasNoFollowUpWriteAuthority() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        assertThat(reschedule(m.getId(), LocalDate.now().plusDays(1), nurseToken).getStatusCode().value()).isEqualTo(403);
        assertThat(complete(m.getId(), nurseToken).getStatusCode().value()).isEqualTo(403);
        assertThat(cancel(m.getId(), nurseToken).getStatusCode().value()).isEqualTo(403);
        assertThat(records.findById(m.getId()).orElseThrow().getFollowUpStatus()).isNull();
    }

    // ── tenancy ──────────────────────────────────────────────────────────────

    @Test
    void anotherFacilitysFollowUpCannotBeTouched() {
        Hospital other = tenant("Bravo");
        Patient theirPatient = patientIn(other, "Bravo Patient");
        MedicalRecord theirs = followUp(other, theirPatient, LocalDate.now(), null);
        // Their own doctor: the tenant check must key on the record, not on anything borrowed
        // from our facility.
        theirs.setDoctorId(doctorIn(other, "doc." + uniq() + "@bravo.test").getId());
        records.save(theirs);

        assertThat(reschedule(theirs.getId(), LocalDate.now().plusDays(1), receptionToken)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(complete(theirs.getId(), doctorToken).getStatusCode().value()).isEqualTo(404);
        assertThat(cancel(theirs.getId(), receptionToken).getStatusCode().value()).isEqualTo(404);

        MedicalRecord after = records.findById(theirs.getId()).orElseThrow();
        assertThat(after.getFollowUpStatus()).isNull();
        assertThat(after.getFollowUpDate()).isEqualTo(LocalDate.now());
    }

    // ── races between incompatible transitions ───────────────────────────────

    /** Runs two different actions against one follow-up at the same instant. */
    private void race(String first, String second, java.util.function.BiFunction<Long, String, ResponseEntity<String>> a,
                      java.util.function.BiFunction<Long, String, ResponseEntity<String>> b,
                      String tokenA, String tokenB) throws Exception {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        long opdsBefore = opdsFor(patient);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> jobs = List.of(
                () -> a.apply(m.getId(), tokenA).getStatusCode().value(),
                () -> b.apply(m.getId(), tokenB).getStatusCode().value());
        List<Future<Integer>> results = pool.invokeAll(jobs);
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        int ok = 0, refused = 0;
        for (Future<Integer> f : results) {
            int code = f.get();
            if (code == 200) ok++;
            else if (code == 409) refused++;
        }

        assertThat(ok).as("%s vs %s: exactly one may win", first, second).isEqualTo(1);
        assertThat(ok + refused).as("and the loser is told, not errored").isEqualTo(2);

        MedicalRecord after = records.findById(m.getId()).orElseThrow();
        boolean visitCreated = opdsFor(patient) > opdsBefore;
        if (visitCreated) {
            assertThat(after.getFollowUpStatus())
                    .as("a visit exists, so the follow-up must read ACTIONED and nothing else")
                    .isEqualTo(MedicalRecord.FOLLOW_UP_ACTIONED);
        } else {
            assertThat(after.getFollowUpStatus())
                    .as("no visit was created, so the follow-up must not claim one")
                    .isNotEqualTo(MedicalRecord.FOLLOW_UP_ACTIONED);
        }
    }

    @Test
    void arriveRacingCancel() throws Exception {
        race("arrive", "cancel", this::arrive, this::cancel, receptionToken, receptionToken);
    }

    @Test
    void arriveRacingComplete() throws Exception {
        race("arrive", "complete", this::arrive, this::complete, receptionToken, doctorToken);
    }

    @Test
    void arriveRacingReschedule() throws Exception {
        race("arrive", "reschedule",
                this::arrive,
                (id, token) -> reschedule(id, LocalDate.now().plusDays(5), token),
                receptionToken, receptionToken);
    }

    @Test
    void completeRacingCancel() throws Exception {
        race("complete", "cancel", this::complete, this::cancel, doctorToken, receptionToken);
    }

    @Test
    void twoCompletesRaceToOne() throws Exception {
        race("complete", "complete", this::complete, this::complete, doctorToken, adminToken);
    }

    /**
     * Two reschedules are not mutually exclusive — both leave the follow-up open — so both may
     * succeed. What must hold is that the row ends on one of the two dates rather than a blend.
     */
    @Test
    void twoConcurrentReschedulesLeaveOneCoherentDate() throws Exception {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now(), null);
        LocalDate d1 = LocalDate.now().plusDays(4);
        LocalDate d2 = LocalDate.now().plusDays(9);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> jobs = List.of(
                () -> reschedule(m.getId(), d1, receptionToken).getStatusCode().value(),
                () -> reschedule(m.getId(), d2, doctorToken).getStatusCode().value());
        List<Future<Integer>> results = pool.invokeAll(jobs);
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        for (Future<Integer> f : results) {
            assertThat(f.get()).as("neither caller should error").isIn(200, 409);
        }

        MedicalRecord after = records.findById(m.getId()).orElseThrow();
        assertThat(after.getFollowUpDate()).isIn(d1, d2);
        assertThat(after.getFollowUpStatus()).as("still open either way").isNull();
    }

    // ── the connected lifecycle ──────────────────────────────────────────────

    /**
     * One follow-up carried through the whole non-visit lifecycle, re-fetched from the read model
     * at every step rather than trusted from the response that changed it.
     */
    @Test
    void aFollowUpMovesThroughItsLifecycleAndLeavesTheWorklistWhenClosed() {
        MedicalRecord m = followUp(hospital, patient, LocalDate.now().minusDays(2), null);

        assertThat(get("/hospital/follow-ups?timing=OVERDUE", receptionToken).getBody())
                .contains("Lifecycle Patient");

        LocalDate moved = LocalDate.now().plusDays(10);
        assertThat(reschedule(m.getId(), moved, receptionToken).getStatusCode().value()).isEqualTo(200);

        assertThat(get("/hospital/follow-ups?timing=OVERDUE", receptionToken).getBody())
                .as("it is no longer late").doesNotContain("Lifecycle Patient");
        assertThat(get("/hospital/follow-ups?timing=UPCOMING", receptionToken).getBody())
                .as("it is expected later").contains("Lifecycle Patient");

        // A second patient is closed off instead, and only that one disappears.
        Patient other = patientIn(hospital, "Closed Patient");
        MedicalRecord closed = followUp(hospital, other, LocalDate.now(), null);
        assertThat(complete(closed.getId(), doctorToken).getStatusCode().value()).isEqualTo(200);

        String body = get("/hospital/follow-ups", receptionToken).getBody();
        assertThat(body).doesNotContain("Closed Patient");
        assertThat(body).as("the rescheduled one is untouched").contains("Lifecycle Patient");

        assertThat(records.findById(m.getId()).orElseThrow().getFollowUpDate()).isEqualTo(moved);
    }
}
