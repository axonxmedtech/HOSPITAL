package com.hms.service.hospital.ot;

import com.hms.entity.OtRoom;
import com.hms.entity.Ward;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.OtRoomRepository;
import com.hms.repository.WardRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * OtRoomService - operation theatres as real resources.
 *
 * Replaces the "ward whose name contains OT" heuristic, which was evaluated in the
 * browser and never validated by the server.
 *
 * Migration from those wards is SUGGESTED, never automatic: the heuristic that created
 * the mess also matches "FOOT WARD", so an admin confirms each room.
 */
@Service
public class OtRoomService {

    private static final Logger logger = LoggerFactory.getLogger(OtRoomService.class);

    @Autowired private OtRoomRepository roomRepository;
    @Autowired private WardRepository wardRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private AuditLogService auditLogService;

    public List<OtRoom> list() {
        return roomRepository.findByHospitalIdAndIsActiveTrueOrderByNameAsc(requireHospitalId());
    }

    @Transactional
    public OtRoom create(String name, Integer turnoverMinutes, Long sourceWardId,
                         java.math.BigDecimal chargeAmount) {
        Long hospitalId = requireHospitalId();
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Theatre name is required");
        String clean = name.trim();
        if (roomRepository.existsByHospitalIdAndName(hospitalId, clean)) {
            throw new IllegalArgumentException("A theatre with that name already exists");
        }
        OtRoom room = new OtRoom();
        room.setHospitalId(hospitalId);
        room.setName(clean);
        room.setTurnoverMinutes(turnoverMinutes == null || turnoverMinutes < 0 ? 15 : turnoverMinutes);
        room.setSourceWardId(sourceWardId);
        room.setChargeAmount(normaliseCharge(chargeAmount));
        OtRoom saved = roomRepository.save(room);
        audit("OT_ROOM_CREATED", "Theatre created: " + clean, hospitalId);
        return saved;
    }

    @Transactional
    public OtRoom update(String publicId, String name, Integer turnoverMinutes, String status,
                         java.math.BigDecimal chargeAmount) {
        Long hospitalId = requireHospitalId();
        OtRoom room = requireRoom(publicId, hospitalId);
        if (name != null && !name.trim().isEmpty()) room.setName(name.trim());
        if (turnoverMinutes != null && turnoverMinutes >= 0) room.setTurnoverMinutes(turnoverMinutes);
        if (status != null) room.setStatus(status);
        // Sent on every save from the theatre form, so clearing the field clears the charge.
        room.setChargeAmount(normaliseCharge(chargeAmount));
        OtRoom saved = roomRepository.save(room);
        audit("OT_ROOM_UPDATED", "Theatre updated: " + saved.getName(), hospitalId);
        return saved;
    }

    /**
     * A theatre fee of zero or less is stored as no fee at all.
     *
     * <p>Keeping a literal zero would put a "Theatre charge - OT-2: 0.00" line on every bill, which
     * reads like a mistake to whoever is handed it. A negative fee is refused rather than quietly
     * corrected, because it can only be a typo and silently making it positive would be worse.
     */
    private java.math.BigDecimal normaliseCharge(java.math.BigDecimal charge) {
        if (charge == null) return null;
        if (charge.signum() < 0) {
            throw new IllegalArgumentException("Theatre charge cannot be negative.");
        }
        return charge.signum() == 0 ? null : charge;
    }

    /** Soft delete: historic surgeries still reference the theatre they ran in. */
    @Transactional
    public void deactivate(String publicId) {
        Long hospitalId = requireHospitalId();
        OtRoom room = requireRoom(publicId, hospitalId);
        room.setIsActive(false);
        roomRepository.save(room);
        audit("OT_ROOM_DEACTIVATED", "Theatre deactivated: " + room.getName(), hospitalId);
    }

    /**
     * OT wards that have not been converted into theatres yet.
     *
     * <p>Selected by ward type. This used to match any ward whose name contained "OT", which is
     * true of "FOOT WARD" — the reason it was only ever a suggestion. An admin now says a ward is
     * a theatre by setting its type, so the guess is gone and the list is exact.
     */
    public List<Ward> suggestFromWards() {
        Long hospitalId = requireHospitalId();
        List<Ward> out = new ArrayList<>();
        for (Ward w : wardRepository.findByHospitalIdAndWardType(hospitalId, com.hms.entity.WardType.OT)) {
            if (roomRepository.findByHospitalIdAndSourceWardId(hospitalId, w.getWardId()).isPresent()) continue;
            out.add(w);
        }
        return out;
    }

    /** Resolves the room holding a legacy ward-scheduled surgery, if one was migrated from it. */
    public OtRoom findBySourceWard(Long hospitalId, Long wardId) {
        if (wardId == null) return null;
        return roomRepository.findByHospitalIdAndSourceWardId(hospitalId, wardId).orElse(null);
    }

    public OtRoom requireRoomById(Long roomId, Long hospitalId) {
        OtRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Theatre not found"));
        if (!hospitalId.equals(room.getHospitalId())) {
            throw new UnauthorizedException("Access denied: theatre belongs to another hospital");
        }
        return room;
    }

    private OtRoom requireRoom(String publicId, Long hospitalId) {
        OtRoom room = roomRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Theatre not found"));
        if (!hospitalId.equals(room.getHospitalId())) {
            throw new UnauthorizedException("Access denied: theatre belongs to another hospital");
        }
        return room;
    }

    private void audit(String action, String detail, Long hospitalId) {
        try {
            auditLogService.logAction(action, detail, securityHelper.getCurrentUserEmail(), hospitalId,
                    "OT_ROOM", null, null);
        } catch (Exception e) {
            logger.warn("Failed to audit {}: {}", action, e.getMessage());
        }
    }

    private Long requireHospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        return hospitalId;
    }
}
