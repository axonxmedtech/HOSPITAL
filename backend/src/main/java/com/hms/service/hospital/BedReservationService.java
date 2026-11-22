package com.hms.service.hospital;

import com.hms.entity.Bed;
import com.hms.entity.BedReservation;
import com.hms.entity.BedStatus;
import com.hms.exception.ConflictException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.BedRepository;
import com.hms.repository.BedReservationRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.WardRepository;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Holds a free bed for a planned admission. While a hold is in force only the reserved patient
 * may be admitted (or moved) into the bed; past its time it is ignored, so a no-show never
 * blocks a bed for long.
 */
@Service
public class BedReservationService {

    static final int DEFAULT_HOLD_HOURS = 6;
    private static final Logger logger = LoggerFactory.getLogger(BedReservationService.class);

    private final BedReservationRepository reservations;
    private final BedRepository beds;
    private final WardRepository wards;
    private final PatientRepository patients;
    private final SecurityContextHelper securityHelper;
    private final FrontDeskAccess frontDesk;
    private final com.hms.service.AuditLogService auditLogService;

    public BedReservationService(BedReservationRepository reservations, BedRepository beds, WardRepository wards,
                                 PatientRepository patients, SecurityContextHelper securityHelper, FrontDeskAccess frontDesk,
                                 com.hms.service.AuditLogService auditLogService) {
        this.reservations = reservations;
        this.beds = beds;
        this.wards = wards;
        this.patients = patients;
        this.securityHelper = securityHelper;
        this.frontDesk = frontDesk;
        this.auditLogService = auditLogService;
    }

    /** What the desk sends to reserve a bed. */
    public record ReserveRequest(Long bedId, Long patientId, String patientName, String patientPhone,
                                 LocalDateTime expectedAt, LocalDateTime reservedUntil, String notes) {}

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private Long hospitalId() {
        Long id = securityHelper.getCurrentHospitalId();
        if (id == null) throw new UnauthorizedException("Hospital ID not found in context");
        return id;
    }

    /** Holds in force now, soonest first, with ward and bed names. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> active() {
        Long hospitalId = hospitalId();
        List<BedReservation> list = reservations.findByHospitalIdAndStatusAndReservedUntilAfterOrderByExpectedAtAsc(
                hospitalId, BedReservation.ACTIVE, now());
        Map<Long, String> wardNames = new HashMap<>();
        wards.findByHospitalId(hospitalId).forEach(w -> wardNames.put(w.getWardId(), w.getWardName()));
        Map<Long, String> bedCodes = new HashMap<>();
        if (!list.isEmpty()) {
            beds.findByHospitalIdAndWardIdIn(hospitalId, list.stream().map(BedReservation::getWardId).toList())
                    .forEach(b -> bedCodes.put(b.getBedId(), b.getBedCode()));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (BedReservation r : list) {
            Map<String, Object> m = view(r);
            m.put("ward", wardNames.get(r.getWardId()));
            m.put("bed", bedCodes.get(r.getBedId()));
            out.add(m);
        }
        return out;
    }

    /** bedId -> the hold in force on it, for bed lists and pickers. */
    @Transactional(readOnly = true)
    public Map<Long, BedReservation> holdsOn(Long hospitalId, Collection<Long> bedIds) {
        Map<Long, BedReservation> out = new HashMap<>();
        if (bedIds == null || bedIds.isEmpty()) return out;
        for (BedReservation r : reservations.findByHospitalIdAndBedIdInAndStatusAndReservedUntilAfter(
                hospitalId, bedIds, BedReservation.ACTIVE, now())) {
            out.put(r.getBedId(), r);
        }
        return out;
    }

    @Transactional
    public BedReservation reserve(ReserveRequest req) {
        Long hospitalId = hospitalId();
        frontDesk.require(hospitalId, "reserve a bed");
        if (req.bedId() == null) throw new IllegalArgumentException("Select the bed to reserve");
        Bed bed = beds.findByBedIdAndHospitalIdForUpdate(req.bedId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        if (!BedStatus.AVAILABLE.equalsIgnoreCase(bed.getStatus())) {
            throw new ConflictException("Only a free bed can be reserved");
        }
        if (!holdsOn(hospitalId, List.of(bed.getBedId())).isEmpty()) {
            throw new ConflictException("This bed is already reserved");
        }
        String name = req.patientName() == null ? "" : req.patientName().trim();
        String phone = req.patientPhone() == null || req.patientPhone().isBlank() ? null : req.patientPhone().trim();
        if (req.patientId() != null) {
            var p = patients.findByIdAndHospitalIdAndIsActiveTrue(req.patientId(), hospitalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
            name = p.getName();
            if (phone == null) phone = p.getPhone();
        }
        if (name.isEmpty()) throw new IllegalArgumentException("Enter who the bed is reserved for");
        if (name.length() > 150) throw new IllegalArgumentException("The name is too long");
        if (phone != null && phone.length() > 20) throw new IllegalArgumentException("The phone number is too long");
        LocalDateTime expected = req.expectedAt();
        if (expected == null) throw new IllegalArgumentException("Enter when the patient is expected");
        if (expected.isBefore(now().minusHours(1))) throw new IllegalArgumentException("The expected time has already passed");
        if (expected.isAfter(now().plusDays(30))) throw new IllegalArgumentException("A bed can be reserved at most 30 days ahead");
        LocalDateTime until = req.reservedUntil() != null ? req.reservedUntil() : expected.plusHours(DEFAULT_HOLD_HOURS);
        if (until.isBefore(expected)) throw new IllegalArgumentException("Hold the bed at least until the expected time");
        if (until.isAfter(expected.plusDays(2))) throw new IllegalArgumentException("Hold the bed for at most 2 days after the expected time");
        String notes = req.notes() == null || req.notes().isBlank() ? null : req.notes().trim();
        if (notes != null && notes.length() > 500) throw new IllegalArgumentException("The note is too long");

        BedReservation r = new BedReservation();
        r.setHospitalId(hospitalId);
        r.setWardId(bed.getWardId());
        r.setBedId(bed.getBedId());
        r.setPatientId(req.patientId());
        r.setPatientName(name);
        r.setPatientPhone(phone);
        r.setExpectedAt(expected);
        r.setReservedUntil(until);
        r.setNotes(notes);
        r.setCreatedBy(securityHelper.getCurrentUserRole() + " (" + securityHelper.getCurrentUserEmail() + ")");
        BedReservation saved = reservations.save(r);
        audit("BED_RESERVED", saved, "Bed " + bed.getBedCode() + " reserved for " + name + " until " + until);
        return saved;
    }

    @Transactional
    public BedReservation cancel(String publicId, String reason) {
        Long hospitalId = hospitalId();
        frontDesk.require(hospitalId, "cancel a reservation");
        BedReservation r = reservations.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        if (!BedReservation.ACTIVE.equals(r.getStatus())) throw new IllegalArgumentException("This reservation is no longer active");
        String why = reason == null || reason.isBlank() ? "Cancelled" : reason.trim();
        r.setStatus(BedReservation.CANCELLED);
        r.setCancelReason(why.length() > 255 ? why.substring(0, 255) : why);
        audit("BED_RESERVATION_CANCELLED", r, "Reservation for " + r.getPatientName() + " cancelled: " + why);
        return reservations.save(r);
    }

    /**
     * Checks a bed held for someone before a patient goes into it. Returns the hold this
     * admission uses (to be marked used), or null when the bed is not held. Refuses anyone else.
     *
     * @param reservationPublicId the desk said "this is the reserved patient" (a hold made by name)
     */
    @Transactional
    public BedReservation claimFor(Long hospitalId, Long bedId, Long patientId, String reservationPublicId) {
        BedReservation hold = holdsOn(hospitalId, List.of(bedId)).get(bedId);
        if (hold == null) return null;
        boolean samePatient = hold.getPatientId() != null && hold.getPatientId().equals(patientId);
        boolean confirmedByDesk = hold.getPatientId() == null && hold.getPublicId().equals(reservationPublicId);
        if (!samePatient && !confirmedByDesk) {
            throw new ConflictException("This bed is reserved for " + hold.getPatientName() + " until "
                    + hold.getReservedUntil().toLocalTime().withNano(0).withSecond(0) + " on " + hold.getReservedUntil().toLocalDate()
                    + ". Choose another bed, or cancel the reservation first.");
        }
        return hold;
    }

    @Transactional
    public void markUsed(BedReservation hold, Long ipdAdmissionId) {
        if (hold == null) return;
        hold.setStatus(BedReservation.USED);
        hold.setIpdAdmissionId(ipdAdmissionId);
        reservations.save(hold);
    }

    public static Map<String, Object> view(BedReservation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publicId", r.getPublicId());
        m.put("wardId", r.getWardId());
        m.put("bedId", r.getBedId());
        m.put("patientId", r.getPatientId());
        m.put("patientName", r.getPatientName());
        m.put("patientPhone", r.getPatientPhone());
        m.put("expectedAt", r.getExpectedAt());
        m.put("reservedUntil", r.getReservedUntil());
        m.put("notes", r.getNotes());
        m.put("status", r.getStatus());
        return m;
    }

    private void audit(String action, BedReservation r, String details) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(), r.getHospitalId(),
                    "BED", String.valueOf(r.getBedId()), null);
        } catch (Exception e) {
            logger.warn("Failed to audit {}", action, e);
        }
    }
}
