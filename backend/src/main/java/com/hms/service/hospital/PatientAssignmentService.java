package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.NurseProfile;
import com.hms.repository.HospitalSettingRepository;
import com.hms.repository.NurseProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PatientAssignmentService - decides staff-nurse assignment at admission
 * (Nursing Mgmt Phase A). Replaces least-loaded auto-assign:
 *  - Separate Nurse Login OFF -> no assignment (incharge handles the patient).
 *  - ON + exactly one non-incharge active staff nurse in the ward -> auto-assign.
 *  - ON + more than one -> incharge assigns manually.
 *  - ON + none -> incharge only.
 * Incharge visibility is derived from ward membership, not an assignment row.
 */
@Service
public class PatientAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(PatientAssignmentService.class);

    @Autowired private HospitalSettingRepository hospitalSettingRepository;
    @Autowired private NurseProfileRepository nurseProfileRepository;
    @Autowired private NurseAssignmentService nurseAssignmentService;
    @Autowired private NurseCoverageService coverageService;

    public void onAdmission(IpdAdmission admission) {
        try {
            boolean separateLogin = hospitalSettingRepository.findByHospital_Id(admission.getHospitalId())
                    .map(s -> Boolean.TRUE.equals(s.getSeparateNurseLogin())).orElse(false);
            if (!separateLogin) return; // incharge handles the patient; no staff assignment

            List<NurseProfile> staff = coverageService
                    .effectiveWardNurses(admission.getWardId(), java.time.LocalDate.now())
                    .stream().filter(p -> p.getUserId() != null).toList();
            if (staff.size() == 1) {
                nurseAssignmentService.assignNurse(admission.getId(), staff.get(0).getUserId(),
                        "Auto-assigned (sole staff nurse in ward)");
            }
            // 0 or >1 -> incharge assigns manually (no auto-assignment)
        } catch (Exception e) {
            logger.warn("PatientAssignmentService.onAdmission failed for admission {}: {}",
                    admission.getId(), e.getMessage());
        }
    }
}
