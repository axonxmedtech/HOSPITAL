package com.hms.service.hospital;

import com.hms.dto.*;
import com.hms.entity.Bed;
import com.hms.entity.Ward;
import com.hms.repository.BedRepository;
import com.hms.repository.WardRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Internal base-Ward mutations shared by the general and ICU services. Not an API entry point.
 * Callers own admission to their path and the surrounding transaction. ICU writes coordinate
 * their projection in IcuWardService; generic writes must first reject ICU-backed wards.
 */
@Service
public class WardWriteService {
    private static final Logger logger = LoggerFactory.getLogger(WardWriteService.class);

    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final SecurityContextHelper securityHelper;
    private final HospitalWebSocketHandler webSocketHandler;
    private final com.hms.repository.NurseProfileRepository nurseProfileRepository;
    private final com.hms.service.AuditLogService auditLogService;

    public WardWriteService(WardRepository wardRepository, BedRepository bedRepository,
                       SecurityContextHelper securityHelper,
                       HospitalWebSocketHandler webSocketHandler,
                       com.hms.repository.NurseProfileRepository nurseProfileRepository,
                       com.hms.service.AuditLogService auditLogService) {
        this.wardRepository = wardRepository;
        this.bedRepository = bedRepository;
        this.securityHelper = securityHelper;
        this.webSocketHandler = webSocketHandler;
        this.nurseProfileRepository = nurseProfileRepository;
        this.auditLogService = auditLogService;
    }

    /** The base row is always locked before the ICU row, for both entry paths. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Ward lockWard(Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        return wardRepository.findByWardIdAndHospitalIdForUpdate(wardId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Ward not found"));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void setIncharge(Long wardId, Long inchargeNurseProfileId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Ward ward = lockWard(wardId);
        Long previous = ward.getInchargeNurseId();
        if (inchargeNurseProfileId != null) {
            com.hms.entity.NurseProfile p = nurseProfileRepository.findByIdAndHospitalId(inchargeNurseProfileId, hospitalId)
                    .orElseThrow(() -> new IllegalArgumentException("Nurse not found"));
            if (!hospitalId.equals(p.getHospitalId()) || !Boolean.TRUE.equals(p.getIsActive())
                    || !Boolean.TRUE.equals(p.getIsIncharge())) {
                throw new IllegalArgumentException("Target must be an active Nurse Incharge in this hospital");
            }
        }
        ward.setInchargeNurseId(inchargeNurseProfileId);
        wardRepository.save(ward);
        auditLogService.logAction("WARD_INCHARGE_SET",
                "Ward " + ward.getWardName() + " incharge " + previous + " -> " + inchargeNurseProfileId,
                securityHelper.getCurrentUserEmail(), hospitalId, "WARD", String.valueOf(wardId), null);
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after ward incharge set", e);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Ward updateWard(Long wardId, UpdateWardRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Ward w = lockWard(wardId);

        if (req.getWardName() != null) w.setWardName(req.getWardName());
        if (req.getBedPrice() != null) w.setBedPrice(req.getBedPrice());
        if (req.getFloorNumber() != null) w.setFloorNumber(req.getFloorNumber());

        // Bed count is editable: resize the ward's bed list to match. Done after the rename
        // above so any newly created bed codes carry the ward's new name.
        if (req.getTotalBeds() != null) {
            resizeBeds(w, req.getTotalBeds(), hospitalId);
        }

        Ward saved = wardRepository.save(w);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after ward update", e);
        }

        return saved;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void deleteWard(Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Ward w = lockWard(wardId);

        List<Bed> beds = bedRepository.findByWardIdAndHospitalId(wardId, hospitalId);
        boolean hasOccupied = beds.stream().anyMatch(b -> !"available".equalsIgnoreCase(b.getStatus()));
        if (hasOccupied) throw new IllegalArgumentException("Cannot delete ward with occupied beds");

        // A ward with nurses assigned to it cannot be deleted — reassign those
        // nurses to another ward first.
        long assignedNurses = nurseProfileRepository.countByWardIdAndIsActiveTrue(wardId);
        if (assignedNurses > 0) {
            throw new IllegalArgumentException(
                "Cannot delete ward: " + assignedNurses + " nurse(s) are assigned to it. Reassign them to another ward first.");
        }

        bedRepository.deleteAll(beds);
        wardRepository.delete(w);

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after ward deletion", e);
        }
    }

    /** Trailing digits of a bed code, e.g. "ICU-B12" -> 12. */
    private static final java.util.regex.Pattern BED_INDEX = java.util.regex.Pattern.compile("(\\d+)$");

    private int bedIndex(Bed b) {
        if (b.getBedCode() == null) return 0;
        java.util.regex.Matcher m = BED_INDEX.matcher(b.getBedCode());
        try {
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Resizes a ward's bed list to {@code target}.
     *
     * Growing appends new available beds, numbered from the current highest index so codes
     * stay unique even after earlier beds were removed. Shrinking deletes only AVAILABLE
     * beds, highest-numbered first — a bed that is occupied, awaiting cleaning, or under
     * maintenance is never destroyed, so the request is rejected rather than silently
     * dropping a patient's bed.
     */
    private void resizeBeds(Ward ward, int target, Long hospitalId) {
        if (target < 0) throw new IllegalArgumentException("Total beds cannot be negative");

        List<Bed> beds = bedRepository.findByWardIdAndHospitalId(ward.getWardId(), hospitalId);
        int current = beds.size();

        if (target > current) {
            int next = beds.stream().mapToInt(this::bedIndex).max().orElse(0) + 1;
            for (int i = 0; i < target - current; i++) {
                Bed b = new Bed();
                b.setHospitalId(hospitalId);
                b.setWardId(ward.getWardId());
                b.setBedCode(String.format("%s-B%d", ward.getWardName(), next + i));
                b.setStatus(com.hms.entity.BedStatus.AVAILABLE);
                bedRepository.save(b);
            }
        } else if (target < current) {
            List<Bed> free = beds.stream()
                    .filter(b -> com.hms.entity.BedStatus.AVAILABLE.equalsIgnoreCase(b.getStatus()))
                    .sorted(java.util.Comparator.comparingInt(this::bedIndex).reversed())
                    .collect(Collectors.toList());

            int toRemove = current - target;
            int inUse = current - free.size();
            if (toRemove > free.size()) {
                throw new IllegalArgumentException(
                        "Cannot reduce to " + target + " bed(s): " + inUse
                                + " bed(s) are occupied or unavailable. The minimum for this ward is " + inUse + ".");
            }
            bedRepository.deleteAll(free.subList(0, toRemove));
        }

        ward.setTotalBeds(target);
    }

}
