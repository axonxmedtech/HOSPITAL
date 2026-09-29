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

    /** Upper bound on beds auto-created for one ward — a domain sanity cap and an overflow guard. */
    private static final int MAX_BEDS_PER_WARD = 2000;

    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final SecurityContextHelper securityHelper;
    private final HospitalWebSocketHandler webSocketHandler;
    private final WardWriteService wardWrites;
    private final com.hms.repository.IcuWardRepository icuWardRepository;

    public WardService(WardRepository wardRepository, BedRepository bedRepository,
                       SecurityContextHelper securityHelper,
                       HospitalWebSocketHandler webSocketHandler,
                       WardWriteService wardWrites,
                       com.hms.repository.IcuWardRepository icuWardRepository) {
        this.wardRepository = wardRepository;
        this.bedRepository = bedRepository;
        this.securityHelper = securityHelper;
        this.webSocketHandler = webSocketHandler;
        this.wardWrites = wardWrites;
        this.icuWardRepository = icuWardRepository;
    }

    @Transactional
    public void setIncharge(Long wardId, Long inchargeNurseProfileId) {
        requireGeneralWard(wardId);
        wardWrites.setIncharge(wardId, inchargeNurseProfileId);
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
        // Bound the user-supplied count to a sane maximum. Besides being a domain rule (no real
        // ward has thousands of beds), this stops a huge value from overflowing the bed-number
        // arithmetic below and from creating a runaway number of rows (CodeQL: user-controlled
        // data in arithmetic expression).
        if (total < 0 || total > MAX_BEDS_PER_WARD) {
            throw new IllegalArgumentException(
                    "Total beds must be between 0 and " + MAX_BEDS_PER_WARD);
        }
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

        for (int i = 0; i < total; i++) {
            long bedNumber = (long) startIndex + i;   // long so a high startIndex can never overflow
            Bed b = new Bed();
            b.setHospitalId(hospitalId);
            b.setWardId(saved.getWardId());
            b.setBedCode(String.format("%s-B%d", req.getWardName(), bedNumber));
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
        return wardRepository.findGeneralWardsByHospitalId(hospitalId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Wards eligible for IPD admission/bed selection. A ward with no Available bed is always
     * hidden (a bed awaiting cleaning or under maintenance does not count). Nursing assignment
     * metadata must not hide an otherwise usable ward from the admission workflow.
     */
    public List<WardResponse> getWardsForAdmission() {
        Long hospitalId = securityHelper.getCurrentHospitalId();

        return wardRepository.findGeneralWardsByHospitalId(hospitalId)
                .stream()
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
        requireGeneralWard(wardId);
        return toResponse(wardWrites.updateWard(wardId, req));
    }

    @Transactional
    public void deleteWard(Long wardId) {
        requireGeneralWard(wardId);
        wardWrites.deleteWard(wardId);
    }

    private void requireGeneralWard(Long wardId) {
        Ward ward = wardWrites.lockWard(wardId);
        if (icuWardRepository.findByWardIdAndHospitalIdForUpdate(wardId, ward.getHospitalId()).isPresent()) {
            throw new IllegalArgumentException("Use the dedicated ICU ward operation for this ward");
        }
    }

    /**
     * ICU Phase 2 — reclassify a ward, rejected while a bed of that ward holds a patient.
     *
     * The ICU board's core property is that an ICU bed and a critical-care patient are two
     * views of one fact. Re-typing a ward under its occupants would break that retroactively:
     * patients already lying in those beds would appear on (or vanish from) the ICU board with
     * no admission time, reason or consultant behind the change. The administrator empties the
     * ward first.
     *
     * Only OCCUPIED blocks the change — a bed awaiting cleaning or under maintenance holds no
     * patient, so reclassifying around it is safe. Setting the same type again is a no-op and
     * is never rejected.
     */
    private WardResponse toResponse(Ward w) {
        WardResponse r = new WardResponse();
        r.setWardId(w.getWardId());
        r.setWardName(w.getWardName());
        r.setBedPrice(w.getBedPrice());
        r.setTotalBeds(w.getTotalBeds());
        r.setFloorNumber(w.getFloorNumber());
        r.setInchargeNurseId(w.getInchargeNurseId());
        r.setStaffed(w.getInchargeNurseId() != null);
        return r;
    }
}
