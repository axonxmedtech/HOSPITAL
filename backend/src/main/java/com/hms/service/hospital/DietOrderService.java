package com.hms.service.hospital;

import com.hms.entity.DietOrder;
import com.hms.entity.IpdAdmission;
import com.hms.repository.BedRepository;
import com.hms.repository.DietOrderRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.PatientNurseAssignmentRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.WardRepository;
import com.hms.security.NurseInchargeGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * The diet the doctor sets for an inpatient, its history, and the ward diet sheet the kitchen
 * works from. One current diet per admission; a new order ends the previous one.
 */
@Service
public class DietOrderService {

    public static final List<String> DIET_TYPES = List.of("NORMAL", "SOFT", "LIQUID", "CLEAR_LIQUID", "DIABETIC",
            "LOW_SALT", "RENAL", "HIGH_PROTEIN", "NBM", "OTHER");
    private static final Logger logger = LoggerFactory.getLogger(DietOrderService.class);

    private final DietOrderRepository diets;
    private final IpdAdmissionRepository admissions;
    private final PatientRepository patients;
    private final WardRepository wards;
    private final BedRepository beds;
    private final DoctorRepository doctors;
    private final PatientNurseAssignmentRepository nurseAssignments;
    private final NotificationService notificationService;
    private final NurseInchargeGuard inchargeGuard;
    private final IpdCareAccess access;
    private final com.hms.service.AuditLogService auditLogService;

    public DietOrderService(DietOrderRepository diets, IpdAdmissionRepository admissions, PatientRepository patients,
                            WardRepository wards, BedRepository beds, DoctorRepository doctors,
                            PatientNurseAssignmentRepository nurseAssignments, NotificationService notificationService,
                            NurseInchargeGuard inchargeGuard, IpdCareAccess access,
                            com.hms.service.AuditLogService auditLogService) {
        this.diets = diets;
        this.admissions = admissions;
        this.patients = patients;
        this.wards = wards;
        this.beds = beds;
        this.doctors = doctors;
        this.nurseAssignments = nurseAssignments;
        this.notificationService = notificationService;
        this.inchargeGuard = inchargeGuard;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    /** The current diet first, then the earlier ones. */
    @Transactional(readOnly = true)
    public List<DietOrder> history(Long ipdAdmissionId) {
        IpdAdmission a = access.requireReadable(ipdAdmissionId);
        return diets.findByHospitalIdAndIpdAdmissionIdOrderByStartedAtDescIdDesc(a.getHospitalId(), a.getId());
    }

    @Transactional
    public DietOrder set(Long ipdAdmissionId, String dietType, String instructions) {
        IpdAdmission a = access.requireOrderable(ipdAdmissionId);
        String type = dietType == null ? "" : dietType.trim().toUpperCase(Locale.ROOT);
        if (!DIET_TYPES.contains(type)) throw new IllegalArgumentException("Select a diet");
        String note = instructions == null || instructions.isBlank() ? null : instructions.trim();
        if (note != null && note.length() > 500) throw new IllegalArgumentException("Instructions are too long");
        if ("OTHER".equals(type) && note == null) throw new IllegalArgumentException("Describe the diet in the instructions");

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        diets.findFirstByHospitalIdAndIpdAdmissionIdAndEndedAtIsNullOrderByIdDesc(a.getHospitalId(), a.getId())
                .ifPresent(current -> {
                    current.setEndedAt(now);
                    diets.save(current);
                });
        DietOrder d = new DietOrder();
        d.setHospitalId(a.getHospitalId());
        d.setIpdAdmissionId(a.getId());
        d.setDietType(type);
        d.setInstructions(note);
        d.setStartedAt(now);
        d.setOrderedBy(access.actor());
        doctors.findByEmailAndHospitalId(access.email(), a.getHospitalId()).ifPresent(doc -> d.setOrderedByDoctorId(doc.getId()));
        DietOrder saved = diets.save(d);
        try {
            auditLogService.logAction("IPD_DIET_ORDERED", "Diet for " + a.getIpdNumber() + ": " + type
                    + (note == null ? "" : " (" + note + ")"), access.email(), a.getHospitalId(), "IPD", a.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit the diet order", e);
        }
        nurseAssignments.findByIpdAdmissionIdAndIsActiveTrue(a.getId()).ifPresent(asg ->
                notificationService.create(asg.getNurseUserId(), a.getHospitalId(), "DIET_ORDERED", "Diet changed",
                        a.getIpdNumber() + ": " + type.replace('_', ' ').toLowerCase(Locale.ROOT)
                                + (note == null ? "" : " — " + note), "IPD", a.getId()));
        return saved;
    }

    /** null = the whole hospital (desk roles); an incharge sees only their own wards. */
    private Set<Long> wardsVisibleToCaller() {
        String role = access.role();
        if ("NURSE_INCHARGE".equals(role)) return new HashSet<>(inchargeGuard.myWardIds());
        if ("DOCTOR".equals(role) || "HOSPITAL_ADMIN".equals(role) || "RECEPTIONIST".equals(role)) return null;
        throw new AccessDeniedException("Not allowed");
    }

    private static void putDiet(Map<String, Object> row, DietOrder d) {
        row.put("dietType", d == null ? null : d.getDietType());
        row.put("instructions", d == null ? null : d.getInstructions());
        row.put("since", d == null ? null : d.getStartedAt());
    }

    /**
     * The kitchen's list: every patient in a bed with their current diet, by ward and bed.
     * Reception, doctors and the admin see the whole hospital; an incharge their own wards.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> dietSheet(Long wardIdFilter) {
        Long hospitalId = access.hospitalId();
        Set<Long> allowedWards = wardsVisibleToCaller();
        List<IpdAdmission> list = new ArrayList<>();
        for (IpdAdmission a : admissions.findByHospitalIdAndStatusIn(hospitalId, IpdAdmissionService.IN_HOSPITAL)) {
            boolean inFilter = wardIdFilter == null || wardIdFilter.equals(a.getWardId());
            boolean allowed = allowedWards == null || allowedWards.contains(a.getWardId());
            if (inFilter && allowed) list.add(a);
        }
        Map<Long, DietOrder> current = new HashMap<>();
        if (!list.isEmpty()) {
            diets.findByHospitalIdAndIpdAdmissionIdInAndEndedAtIsNull(hospitalId,
                    list.stream().map(IpdAdmission::getId).toList()).forEach(d -> current.put(d.getIpdAdmissionId(), d));
        }
        Map<Long, String> wardNames = new HashMap<>();
        wards.findByHospitalId(hospitalId).forEach(w -> wardNames.put(w.getWardId(), w.getWardName()));
        Map<Long, String> bedCodes = new HashMap<>();
        Map<Long, String> patientNames = new HashMap<>();
        if (!list.isEmpty()) {
            beds.findByHospitalIdAndWardIdIn(hospitalId, new HashSet<>(wardNames.keySet()))
                    .forEach(b -> bedCodes.put(b.getBedId(), b.getBedCode()));
            patients.findByHospitalIdAndIdIn(hospitalId, list.stream().map(IpdAdmission::getPatientId).toList())
                    .forEach(p -> patientNames.put(p.getId(), p.getName()));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IpdAdmission a : list) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ipdId", a.getId());
            r.put("ipdNumber", a.getIpdNumber());
            r.put("ward", wardNames.getOrDefault(a.getWardId(), "-"));
            r.put("bed", bedCodes.getOrDefault(a.getBedId(), "-"));
            r.put("patientName", patientNames.getOrDefault(a.getPatientId(), "-"));
            putDiet(r, current.get(a.getId()));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("ward")))
                .thenComparing(r -> String.valueOf(r.get("bed"))));
        return rows;
    }
}
