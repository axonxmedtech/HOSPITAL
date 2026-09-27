package com.hms.service.hospital.icu;

import com.hms.dto.CreateWardRequest;
import com.hms.dto.UpdateWardRequest;
import com.hms.dto.WardResponse;
import com.hms.dto.icu.IcuWardRequest;
import com.hms.dto.icu.IcuWardResponse;
import com.hms.entity.Bed;
import com.hms.entity.BedStatus;
import com.hms.entity.IcuWard;
import com.hms.entity.Ward;
import com.hms.exception.ConflictException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.BedRepository;
import com.hms.repository.IcuWardRepository;
import com.hms.repository.NurseProfileRepository;
import com.hms.repository.WardRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.RealtimeNotifier;
import com.hms.service.hospital.WardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing dedicated ICU wards. Coordinates with base Ward and Bed models
 * to preserve full integration with IPD admissions, daily billing, and nurse management.
 */
@Service
public class IcuWardService {

    private static final Logger logger = LoggerFactory.getLogger(IcuWardService.class);

    @Autowired private IcuWardRepository icuWardRepository;
    @Autowired private WardRepository wardRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private WardService wardService;
    @Autowired private NurseProfileRepository nurseProfileRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private RealtimeNotifier notifier;
    @Autowired private AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<IcuWardResponse> listIcuWards() {
        Long hospitalId = requireHospitalId();
        return icuWardRepository.findByHospitalId(hospitalId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IcuWardResponse getIcuWard(String publicId) {
        Long hospitalId = requireHospitalId();
        IcuWard icuWard = icuWardRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("ICU ward not found"));
        return toResponse(icuWard);
    }

    @Transactional
    public IcuWardResponse createIcuWard(IcuWardRequest req) {
        Long hospitalId = requireHospitalId();
        String normalizedUnitType = CareUnitRegistry.normalize(req.getUnitType());
        if (!CareUnitRegistry.isCriticalCare(normalizedUnitType)) {
            throw new IllegalArgumentException("Unit type must be a critical care unit type (e.g. ICU, MICU, SICU, NICU, PICU, CCU, HDU)");
        }

        if (icuWardRepository.existsByHospitalIdAndWardNameIgnoreCase(hospitalId, req.getWardName())) {
            throw new ConflictException("An ICU ward with this name already exists");
        }

        // 1. Create base Ward record so beds, admissions, billing, and nursing integrate seamlessly
        CreateWardRequest baseReq = new CreateWardRequest();
        baseReq.setWardName(req.getWardName());
        baseReq.setBedPrice(req.getBedPrice());
        baseReq.setTotalBeds(req.getTotalBeds());
        baseReq.setFloorNumber(req.getFloorNumber());

        WardResponse baseWard = wardService.createWard(baseReq);

        // 2. Set incharge nurse if provided
        if (req.getInchargeNurseId() != null) {
            wardService.setIncharge(baseWard.getWardId(), req.getInchargeNurseId());
        }

        // 3. Create dedicated IcuWard entry
        IcuWard icuWard = new IcuWard();
        icuWard.setHospitalId(hospitalId);
        icuWard.setWardId(baseWard.getWardId());
        icuWard.setWardName(req.getWardName());
        icuWard.setUnitType(normalizedUnitType);
        icuWard.setBedPrice(req.getBedPrice());
        icuWard.setTotalBeds(req.getTotalBeds());
        icuWard.setFloorNumber(req.getFloorNumber());
        icuWard.setInchargeNurseId(req.getInchargeNurseId());

        IcuWard saved = icuWardRepository.save(icuWard);

        auditLogService.logAction("ICU_WARD_CREATED",
                "Created ICU ward: " + saved.getWardName() + " (" + normalizedUnitType + ")",
                securityHelper.getCurrentUserEmail(), hospitalId, "ICU_WARD", String.valueOf(saved.getId()), null);

        notifier.refresh(hospitalId);
        return toResponse(saved);
    }

    @Transactional
    public IcuWardResponse updateIcuWard(String publicId, IcuWardRequest req) {
        Long hospitalId = requireHospitalId();
        IcuWard icuWard = icuWardRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("ICU ward not found"));

        String normalizedUnitType = CareUnitRegistry.normalize(req.getUnitType());
        if (!CareUnitRegistry.isCriticalCare(normalizedUnitType)) {
            throw new IllegalArgumentException("Unit type must be a critical care unit type");
        }

        if (icuWardRepository.existsByHospitalIdAndWardNameIgnoreCaseAndIdNot(hospitalId, req.getWardName(), icuWard.getId())) {
            throw new ConflictException("Another ICU ward with this name already exists");
        }

        // 1. Update base ward (handles bed resizing and renaming)
        UpdateWardRequest updateReq = new UpdateWardRequest();
        updateReq.setWardName(req.getWardName());
        updateReq.setBedPrice(req.getBedPrice());
        updateReq.setTotalBeds(req.getTotalBeds());
        updateReq.setFloorNumber(req.getFloorNumber());

        wardService.updateWard(icuWard.getWardId(), updateReq);

        if (req.getInchargeNurseId() != null || icuWard.getInchargeNurseId() != null) {
            wardService.setIncharge(icuWard.getWardId(), req.getInchargeNurseId());
        }

        // 2. Update IcuWard
        icuWard.setWardName(req.getWardName());
        icuWard.setUnitType(normalizedUnitType);
        icuWard.setBedPrice(req.getBedPrice());
        icuWard.setTotalBeds(req.getTotalBeds());
        icuWard.setFloorNumber(req.getFloorNumber());
        icuWard.setInchargeNurseId(req.getInchargeNurseId());

        IcuWard saved = icuWardRepository.save(icuWard);

        auditLogService.logAction("ICU_WARD_UPDATED",
                "Updated ICU ward: " + saved.getWardName(),
                securityHelper.getCurrentUserEmail(), hospitalId, "ICU_WARD", String.valueOf(saved.getId()), null);

        notifier.refresh(hospitalId);
        return toResponse(saved);
    }

    @Transactional
    public void deleteIcuWard(String publicId) {
        Long hospitalId = requireHospitalId();
        IcuWard icuWard = icuWardRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("ICU ward not found"));

        // Delete base ward first (enforces occupied bed guards and assigned nurse guards)
        wardService.deleteWard(icuWard.getWardId());

        // Delete dedicated icu_ward row
        icuWardRepository.delete(icuWard);

        auditLogService.logAction("ICU_WARD_DELETED",
                "Deleted ICU ward: " + icuWard.getWardName(),
                securityHelper.getCurrentUserEmail(), hospitalId, "ICU_WARD", String.valueOf(icuWard.getId()), null);

        notifier.refresh(hospitalId);
    }

    @Transactional
    public void setIncharge(String publicId, Long inchargeNurseProfileId) {
        Long hospitalId = requireHospitalId();
        IcuWard icuWard = icuWardRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("ICU ward not found"));

        wardService.setIncharge(icuWard.getWardId(), inchargeNurseProfileId);
        icuWard.setInchargeNurseId(inchargeNurseProfileId);
        icuWardRepository.save(icuWard);
        notifier.refresh(hospitalId);
    }

    private IcuWardResponse toResponse(IcuWard w) {
        IcuWardResponse resp = new IcuWardResponse();
        resp.setId(w.getId());
        resp.setPublicId(w.getPublicId());
        resp.setWardId(w.getWardId());
        resp.setWardName(w.getWardName());
        resp.setUnitType(w.getUnitType());
        resp.setUnitTypeLabel(CareUnitRegistry.labelOf(w.getUnitType()));
        resp.setBedPrice(w.getBedPrice());
        resp.setTotalBeds(w.getTotalBeds());
        resp.setFloorNumber(w.getFloorNumber());
        resp.setInchargeNurseId(w.getInchargeNurseId());
        resp.setStaffed(w.getInchargeNurseId() != null);

        if (w.getInchargeNurseId() != null) {
            nurseProfileRepository.findByIdAndHospitalId(w.getInchargeNurseId(), w.getHospitalId())
                    .ifPresent(p -> resp.setInchargeNurseName(p.getName()));
        }

        // Live bed counts
        List<Bed> beds = bedRepository.findByWardIdAndHospitalId(w.getWardId(), w.getHospitalId());
        int available = (int) beds.stream()
                .filter(b -> BedStatus.AVAILABLE.equalsIgnoreCase(b.getStatus()))
                .count();
        int occupied = (int) beds.stream()
                .filter(b -> BedStatus.OCCUPIED.equalsIgnoreCase(b.getStatus()))
                .count();

        resp.setAvailableBeds(available);
        resp.setOccupiedBeds(occupied);
        return resp;
    }

    private Long requireHospitalId() {
        Long id = securityHelper.getCurrentHospitalId();
        if (id == null) {
            throw new ResourceNotFoundException("Hospital not found");
        }
        return id;
    }
}
