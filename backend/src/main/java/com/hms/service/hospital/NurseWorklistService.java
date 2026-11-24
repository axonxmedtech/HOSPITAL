package com.hms.service.hospital;

import com.hms.dto.MyPatientDTO;
import com.hms.dto.NurseWorklistItemDTO;
import com.hms.entity.LabOrder;
import com.hms.entity.MedicationAdministration;
import com.hms.entity.Prescription;
import com.hms.entity.VitalsRecord;
import com.hms.repository.LabOrderRepository;
import com.hms.repository.MedicationAdministrationRepository;
import com.hms.repository.PrescriptionRepository;
import com.hms.repository.VitalsRecordRepository;
import com.hms.security.SecurityContextHelper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The nurse's "due now" list: every dose, vitals round and sample due today across the patients
 * the nurse can see (the same scope as My Patients), so a round is one list of one-tap jobs
 * instead of opening each patient's Medication, Vitals and Investigations tabs.
 *
 * <p>Read-only. Recording goes through the existing endpoints (medication, vitals, sample), so
 * their rules (who may record, stock and billing, duplicate protection) apply unchanged.
 *
 * <p>Doses come from the order's Morning-Afternoon-Night frequency ("1-0-1"), at the hospital's
 * dose times. A dose is DUE from an hour before its time, OVERDUE an hour after, DONE once
 * anything is recorded for it (given, refused, skipped ...). A record that names its dose time
 * closes that dose; one that does not (recorded on the patient's chart) closes the earliest open
 * dose of the day, so doses given the old way are not shown as still due.
 */
@Service
public class NurseWorklistService {

    public static final String OVERDUE = "OVERDUE";
    public static final String DUE = "DUE";
    public static final String UPCOMING = "UPCOMING";
    public static final String DONE = "DONE";
    public static final String AS_NEEDED = "AS_NEEDED";
    public static final String UNSCHEDULED = "UNSCHEDULED";

    /** Morning, afternoon and night dose times. */
    static final Map<String, LocalTime> SLOT_TIMES = slotTimes();
    /** How long either side of a dose time it counts as due now. */
    static final long WINDOW_MINUTES = 60;
    /** A patient is due for vitals once the last reading is this old. */
    static final long VITALS_EVERY_HOURS = 6;

    /** "As Per Required", the way normalise() writes it (lower case, no spaces). */
    private static final String AS_PER_REQUIRED = "asperrequired";
    private static final Set<String> RECORDED = Set.of("GIVEN", "DELAYED", "REFUSED", "SKIPPED", "NOT_AVAILABLE");
    private static final Map<String, String> LEGACY_FREQUENCIES = Map.of(
            "OD", "1-0-0", "BD", "1-0-1", "BID", "1-0-1", "TDS", "1-1-1", "TID", "1-1-1",
            "HS", "0-0-1", "SOS", AS_PER_REQUIRED, "PRN", AS_PER_REQUIRED);

    @Autowired private NurseWorkspaceService nurseWorkspaceService;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private MedicationAdministrationRepository marRepository;
    @Autowired private VitalsRecordRepository vitalsRepository;
    @Autowired private LabOrderRepository labOrderRepository;
    @Autowired private SecurityContextHelper securityHelper;

    private static Map<String, LocalTime> slotTimes() {
        Map<String, LocalTime> m = new LinkedHashMap<>();
        m.put("MORNING", LocalTime.of(8, 0));
        m.put("AFTERNOON", LocalTime.of(14, 0));
        m.put("NIGHT", LocalTime.of(20, 0));
        return m;
    }

    public List<NurseWorklistItemDTO> worklist() {
        return worklist(LocalDateTime.now());
    }

    List<NurseWorklistItemDTO> worklist(LocalDateTime now) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        List<MyPatientDTO> patients = nurseWorkspaceService.getMyPatients();
        if (patients.isEmpty()) return List.of();
        List<Long> ids = patients.stream().map(MyPatientDTO::getIpdAdmissionId).toList();

        Map<Long, List<MedicationAdministration>> marsByOrder = marRepository
                .findByIpdAdmissionIdInAndIsActiveTrueOrderByCreatedAtAsc(ids).stream()
                .filter(m -> isOn(m, now.toLocalDate()))
                .collect(Collectors.groupingBy(MedicationAdministration::getPrescriptionId));
        Map<Long, VitalsRecord> lastVitals = latestVitals(ids);

        List<NurseWorklistItemDTO> out = new ArrayList<>();
        for (MyPatientDTO p : patients) {
            Long admissionId = p.getIpdAdmissionId();
            for (Prescription rx : prescriptionRepository.findByIpdAdmissionIdAndStatus(admissionId, "ACTIVE")) {
                if (hospitalId != null && !hospitalId.equals(rx.getHospitalId())) continue;
                if (!onCourse(rx, now.toLocalDate())) continue;
                out.addAll(dosesFor(p, rx, marsByOrder.getOrDefault(rx.getId(), List.of()), now));
            }
            out.add(vitalsFor(p, lastVitals.get(admissionId), now));
            for (LabOrder o : labOrderRepository.findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDescIdDesc(hospitalId, admissionId)) {
                if ("ORDERED".equalsIgnoreCase(o.getStatus())) out.add(sampleFor(p, o));
            }
        }
        return out;
    }

    // ── doses ───────────────────────────────────────────────────────────────

    /** The dose slots a frequency asks for, in the order of the day; empty when unrecognised. */
    static List<String> slotsFor(String frequency) {
        String f = normalise(frequency);
        if (f == null || AS_PER_REQUIRED.equals(f)) return List.of();
        String[] parts = f.split("-");
        if (parts.length != 3) return List.of();
        List<String> slots = new ArrayList<>();
        String[] names = SLOT_TIMES.keySet().toArray(new String[0]);
        for (int i = 0; i < 3; i++) {
            String part = parts[i].trim();
            if (!part.matches("\\d+")) return List.of();
            if (Integer.parseInt(part) > 0) slots.add(names[i]);
        }
        return slots;
    }

    static boolean isAsNeeded(String frequency) {
        return AS_PER_REQUIRED.equals(normalise(frequency));
    }

    private static String normalise(String frequency) {
        if (frequency == null || frequency.isBlank()) return null;
        String f = frequency.trim();
        String legacy = LEGACY_FREQUENCIES.get(f.toUpperCase(Locale.ROOT));
        if (legacy != null) return legacy;
        return f.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    /** Where a dose at {@code time} stands at {@code now}, when nothing is recorded for it yet. */
    static String stateAt(LocalDateTime time, LocalDateTime now) {
        if (now.isAfter(time.plusMinutes(WINDOW_MINUTES))) return OVERDUE;
        if (!now.isBefore(time.minusMinutes(WINDOW_MINUTES))) return DUE;
        return UPCOMING;
    }

    private List<NurseWorklistItemDTO> dosesFor(MyPatientDTO p, Prescription rx,
                                                List<MedicationAdministration> today, LocalDateTime now) {
        if (isAsNeeded(rx.getFrequency())) {
            NurseWorklistItemDTO d = dose(p, rx);
            d.setState(AS_NEEDED);
            d.setGivenToday((int) today.stream().filter(m -> "GIVEN".equalsIgnoreCase(m.getStatus())).count());
            return List.of(d);
        }
        List<String> slots = slotsFor(rx.getFrequency());
        if (slots.isEmpty()) {
            NurseWorklistItemDTO d = dose(p, rx);
            d.setState(UNSCHEDULED);
            return List.of(d);
        }
        LocalDate date = now.toLocalDate();
        Map<LocalDateTime, MedicationAdministration> closed = matchRecords(slots, today, date);
        List<NurseWorklistItemDTO> doses = new ArrayList<>();
        for (String slot : slots) {
            LocalDateTime time = date.atTime(SLOT_TIMES.get(slot));
            if (beforeTheOrder(rx, time)) continue;
            NurseWorklistItemDTO d = dose(p, rx);
            d.setSlot(slot);
            d.setScheduledTime(time);
            MedicationAdministration rec = closed.get(time);
            if (rec != null) {
                d.setState(DONE);
                d.setRecordedStatus(rec.getStatus());
                d.setRecordedAt(rec.getAdministeredTime() != null ? rec.getAdministeredTime() : rec.getCreatedAt());
            } else {
                d.setState(stateAt(time, now));
            }
            doses.add(d);
        }
        return doses;
    }

    /**
     * Which of today's doses are closed, and by which record. A record naming a dose time closes
     * that dose; the rest close the earliest dose still open, in the order they were made.
     */
    static Map<LocalDateTime, MedicationAdministration> matchRecords(List<String> slots,
                                                                      Collection<MedicationAdministration> today,
                                                                      LocalDate date) {
        List<LocalDateTime> times = slots.stream().map(s -> date.atTime(SLOT_TIMES.get(s))).toList();
        Map<LocalDateTime, MedicationAdministration> closed = new HashMap<>();
        List<MedicationAdministration> loose = new ArrayList<>();
        for (MedicationAdministration m : today) {
            if (m.getStatus() == null || !RECORDED.contains(m.getStatus().toUpperCase(Locale.ROOT))) continue;
            LocalDateTime at = m.getScheduledTime() == null ? null : m.getScheduledTime().truncatedTo(ChronoUnit.MINUTES);
            if (at != null && times.contains(at) && !closed.containsKey(at)) closed.put(at, m);
            else loose.add(m);
        }
        for (MedicationAdministration m : loose) {
            times.stream().filter(t -> !closed.containsKey(t)).findFirst().ifPresent(t -> closed.put(t, m));
        }
        return closed;
    }

    private static NurseWorklistItemDTO dose(MyPatientDTO p, Prescription rx) {
        NurseWorklistItemDTO d = base(p, NurseWorklistItemDTO.DOSE);
        d.setPrescriptionId(rx.getId());
        d.setMedicineName(rx.getMedicineName());
        d.setDosage(rx.getDosage());
        d.setRoute(rx.getRoute());
        d.setType(rx.getType());
        d.setFoodTiming(rx.getFoodTiming());
        d.setQuantityPerDose(rx.getQuantityPerDose() == null ? 1 : rx.getQuantityPerDose());
        d.setStocked(rx.getStockSource() != null || rx.getMedicineId() != null);
        return d;
    }

    /**
     * A dose time that had passed before the order was written is not owed: a medicine ordered
     * at 3 pm starts with the night dose, not an "overdue" morning one.
     */
    static boolean beforeTheOrder(Prescription rx, LocalDateTime doseTime) {
        LocalDateTime ordered = rx.getCreatedAt();
        return ordered != null && doseTime.plusMinutes(WINDOW_MINUTES).isBefore(ordered);
    }

    /** Started, and not past its days. */
    static boolean onCourse(Prescription rx, LocalDate today) {
        if (rx.getStartDate() == null) return true;
        if (rx.getStartDate().isAfter(today)) return false;
        Integer days = rx.getDurationDays();
        if (days == null || days <= 0) return true;
        return ChronoUnit.DAYS.between(rx.getStartDate(), today) < days;
    }

    /** The day a record belongs to: its dose time, else when it was given, else when it was made. */
    private static boolean isOn(MedicationAdministration m, LocalDate date) {
        LocalDateTime when = m.getScheduledTime();
        if (when == null) when = m.getAdministeredTime();
        if (when == null) when = m.getCreatedAt();
        return when != null && when.toLocalDate().equals(date);
    }

    // ── vitals and samples ──────────────────────────────────────────────────

    private Map<Long, VitalsRecord> latestVitals(List<Long> ids) {
        Map<Long, VitalsRecord> latest = new HashMap<>();
        for (VitalsRecord v : vitalsRepository.findLatestForAdmissions(ids)) {
            latest.putIfAbsent(v.getIpdAdmissionId(), v);
        }
        return latest;
    }

    private static NurseWorklistItemDTO vitalsFor(MyPatientDTO p, VitalsRecord last, LocalDateTime now) {
        NurseWorklistItemDTO d = base(p, NurseWorklistItemDTO.VITALS);
        LocalDateTime at = last == null ? null : last.getRecordedAt();
        d.setLastRecordedAt(at);
        if (at == null) {
            d.setState(DUE);
            return d;
        }
        LocalDateTime dueAt = at.plusHours(VITALS_EVERY_HOURS);
        d.setScheduledTime(dueAt);
        d.setState(now.isBefore(dueAt) ? DONE : stateAt(dueAt, now));
        return d;
    }

    private static NurseWorklistItemDTO sampleFor(MyPatientDTO p, LabOrder o) {
        NurseWorklistItemDTO d = base(p, NurseWorklistItemDTO.SAMPLE);
        d.setState(DUE);
        d.setInvestigationPublicId(o.getPublicId());
        d.setTestName(o.getTestName());
        d.setPriority(o.getPriority());
        d.setOrderedAt(o.getCreatedAt());
        return d;
    }

    private static NurseWorklistItemDTO base(MyPatientDTO p, String kind) {
        NurseWorklistItemDTO d = new NurseWorklistItemDTO();
        d.setKind(kind);
        d.setIpdAdmissionId(p.getIpdAdmissionId());
        d.setPatientName(p.getPatientName());
        d.setIpdNumber(p.getIpdNumber());
        d.setWardId(p.getWardId());
        d.setWardName(p.getWardName());
        d.setBedCode(p.getBedCode());
        return d;
    }
}
