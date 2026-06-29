package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.Nurse;
import com.hms.entity.NurseTask;
import com.hms.entity.NurseWardAssignment;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NurseDashboardService {

    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private NurseRepository nurseRepository;
    @Autowired private NurseWardAssignmentRepository wardAssignmentRepository;
    @Autowired private NurseTaskRepository nurseTaskRepository;
    @Autowired private SecurityContextHelper securityHelper;

    public List<IpdAdmission> getMyPatients() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        String email = securityHelper.getCurrentUserEmail();
        Nurse nurse = nurseRepository.findByEmailAndIsActiveTrue(email).orElse(null);

        List<IpdAdmission> allAdmitted = ipdAdmissionRepository
                .findByHospitalIdAndStatus(hospitalId, "ADMITTED");

        if (nurse == null) return allAdmitted;

        List<NurseWardAssignment> assignments = wardAssignmentRepository.findByNurseId(nurse.getId());
        if (assignments.isEmpty()) return allAdmitted;

        List<Long> wardIds = assignments.stream()
                .map(NurseWardAssignment::getWardId).collect(Collectors.toList());
        return allAdmitted.stream()
                .filter(a -> a.getWardId() != null && wardIds.contains(a.getWardId()))
                .collect(Collectors.toList());
    }

    public List<NurseTask> getMyTasks() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<IpdAdmission> patients = getMyPatients();
        List<Long> admissionIds = patients.stream()
                .map(IpdAdmission::getId).collect(Collectors.toList());
        if (admissionIds.isEmpty()) return List.of();

        return nurseTaskRepository.findByIpdAdmissionIdInAndStatus(admissionIds, "PENDING");
    }
}
