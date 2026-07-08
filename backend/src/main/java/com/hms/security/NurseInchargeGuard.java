package com.hms.security;

import com.hms.entity.IpdAdmission;
import com.hms.entity.NurseProfile;
import com.hms.entity.Ward;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.NurseProfileRepository;
import com.hms.repository.WardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * NurseInchargeGuard - restricts a Nurse Incharge to their assigned wards.
 * HOSPITAL_ADMIN has access to all wards. The "only your wards" rule lives here.
 */
@Component
public class NurseInchargeGuard {

    @Autowired private NurseProfileRepository nurseProfileRepository;
    @Autowired private WardRepository wardRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private SecurityContextHelper securityHelper;

    private boolean isAdmin() {
        return "HOSPITAL_ADMIN".equals(securityHelper.getCurrentUserRole());
    }

    /** The NurseProfile id of the current incharge, or null. */
    private Long currentInchargeProfileId() {
        return nurseProfileRepository.findByUserId(securityHelper.getCurrentUserId())
                .map(NurseProfile::getId).orElse(null);
    }

    public void assertWardAccess(Long wardId) {
        if (isAdmin()) return;
        Ward ward = wardRepository.findById(wardId)
                .orElseThrow(() -> new IllegalArgumentException("Ward not found"));
        Long me = currentInchargeProfileId();
        if (me == null || !me.equals(ward.getInchargeNurseId())) {
            throw new AccessDeniedException("You are not the incharge of this ward");
        }
    }

    public void assertAdmissionInMyWard(Long ipdAdmissionId) {
        IpdAdmission a = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new IllegalArgumentException("IPD admission not found"));
        assertWardAccess(a.getWardId());
    }

    /** Ward ids the current user may see. Admin -> all hospital wards. */
    public List<Long> myWardIds() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (isAdmin()) {
            return wardRepository.findByHospitalId(hospitalId).stream()
                    .map(Ward::getWardId).collect(Collectors.toList());
        }
        Long me = currentInchargeProfileId();
        if (me == null) return List.of();
        return wardRepository.findByHospitalIdAndInchargeNurseId(hospitalId, me).stream()
                .map(Ward::getWardId).collect(Collectors.toList());
    }
}
