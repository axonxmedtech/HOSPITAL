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

@Service
public class WardService {

    private static final Logger logger = LoggerFactory.getLogger(WardService.class);

    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final SecurityContextHelper securityHelper;
    private final HospitalWebSocketHandler webSocketHandler;
    private final com.hms.repository.NurseProfileRepository nurseProfileRepository;
    private final com.hms.service.AuditLogService auditLogService;

    public WardService(WardRepository wardRepository, BedRepository bedRepository,
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

    @Transactional
    public void setIncharge(Long wardId, Long inchargeNurseProfileId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Ward ward = wardRepository.findById(wardId)
                .orElseThrow(() -> new IllegalArgumentException("Ward not found"));
        if (!hospitalId.equals(ward.getHospitalId())) {
            throw new UnauthorizedException("Ward belongs to another hospital");
        }
        Long previous = ward.getInchargeNurseId();
        if (inchargeNurseProfileId != null) {
            com.hms.entity.NurseProfile p = nurseProfileRepository.findById(inchargeNurseProfileId)
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

    @Transactional
    public WardResponse createWard(CreateWardRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();

        Ward ward = new Ward();
        ward.setHospitalId(hospitalId);
        ward.setWardName(req.getWardName());
        ward.setBedPrice(req.getBedPrice());
        ward.setTotalBeds(req.getTotalBeds());
        ward.setFloorNumber(req.getFloorNumber());

        Ward saved = wardRepository.save(ward);

        // auto-create beds
        int total = req.getTotalBeds() == null ? 0 : req.getTotalBeds();
        // ensure unique bed codes within a ward by checking existing highest index
        int startIndex = 1;
        List<Bed> existing = bedRepository.findByWardIdAndHospitalId(saved.getWardId(), hospitalId);
        if (existing != null && !existing.isEmpty()) {
            int max = existing.stream().mapToInt(bd -> {
                String code = bd.getBedCode();
                try {
                    int idx = Integer.parseInt(code.replaceAll(".*[^0-9](?=\\d+$)", ""));
                    return idx;
                } catch (Exception ex) { return 0; }
            }).max().orElse(0);
            startIndex = max + 1;
        }

        for (int i = startIndex; i < startIndex + total; i++) {
            Bed b = new Bed();
            b.setHospitalId(hospitalId);
            b.setWardId(saved.getWardId());
            b.setBedCode(String.format("%s-B%d", req.getWardName(), i));
            b.setStatus("available");
            bedRepository.save(b);
        }

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after ward creation", e);
        }

        return toResponse(saved);
    }

    @Transactional
    public List<WardResponse> bulkCreate(BulkCreateWardsRequest req) {
        return req.getWards().stream().map(this::createWard).collect(Collectors.toList());
    }

    public List<WardResponse> getAllWards() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return wardRepository.findByHospitalId(hospitalId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Wards eligible for IPD admission/bed selection. A ward without a Nurse
     * Incharge cannot safely receive patients, so it is hidden from this list.
     * Used only by the admission flow — admin ward management still uses
     * {@link #getAllWards()} so every ward remains visible for incharge assignment.
     */
    // Nursing Mgmt: hide incharge-less wards, and wards with no Available bed,
    // from admission selection. A bed awaiting cleaning or under maintenance
    // does not count as available (Phase C).
    public List<WardResponse> getWardsForAdmission() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return wardRepository.findByHospitalId(hospitalId)
                .stream()
                .filter(w -> w.getInchargeNurseId() != null)
                .filter(w -> bedRepository.findByWardIdAndHospitalId(w.getWardId(), hospitalId).stream()
                        .anyMatch(b -> com.hms.entity.BedStatus.AVAILABLE.equalsIgnoreCase(b.getStatus())))
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<BedResponse> getBedsForWard(Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return bedRepository.findByWardIdAndHospitalId(wardId, hospitalId)
                .stream().map(b -> {
                    BedResponse br = new BedResponse();
                    br.setBedId(b.getBedId());
                    br.setBedCode(b.getBedCode());
                    br.setStatus(b.getStatus());
                    return br;
                }).collect(Collectors.toList());
    }

    @Transactional
    public WardResponse updateWard(Long wardId, UpdateWardRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Ward w = wardRepository.findById(wardId).orElseThrow(() -> new RuntimeException("Ward not found"));
        if (!w.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied");

        if (req.getWardName() != null) w.setWardName(req.getWardName());
        if (req.getBedPrice() != null) w.setBedPrice(req.getBedPrice());
        if (req.getFloorNumber() != null) w.setFloorNumber(req.getFloorNumber());

        Ward saved = wardRepository.save(w);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after ward update", e);
        }

        return toResponse(saved);
    }

    @Transactional
    public void deleteWard(Long wardId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Ward w = wardRepository.findById(wardId).orElseThrow(() -> new RuntimeException("Ward not found"));
        if (!w.getHospitalId().equals(hospitalId)) throw new UnauthorizedException("Access denied");

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

    private WardResponse toResponse(Ward w) {
        WardResponse r = new WardResponse();
        r.setWardId(w.getWardId());
        r.setWardName(w.getWardName());
        r.setBedPrice(w.getBedPrice());
        r.setTotalBeds(w.getTotalBeds());
        r.setFloorNumber(w.getFloorNumber());
        r.setInchargeNurseId(w.getInchargeNurseId());
        return r;
    }
}

