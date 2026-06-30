package com.hms.service.hospital;

import com.hms.dto.ShiftActivityDTO;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Nurse;
import com.hms.entity.NurseTask;
import com.hms.entity.NurseWardAssignment;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NurseDashboardService {

    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private NurseRepository nurseRepository;
    @Autowired private NurseWardAssignmentRepository wardAssignmentRepository;
    @Autowired private NurseTaskRepository nurseTaskRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private PatientRepository patientRepository;
    @Autowired private WardRepository wardRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private DoctorRepository doctorRepository;

    public List<com.hms.dto.IpdAdmissionSummaryDTO> getMyPatients() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        String email = securityHelper.getCurrentUserEmail();
        Nurse nurse = nurseRepository.findByEmailAndIsActiveTrue(email).orElse(null);

        List<IpdAdmission> allAdmitted = ipdAdmissionRepository
                .findByHospitalIdAndStatus(hospitalId, "ADMITTED");

        List<IpdAdmission> filtered;
        if (nurse == null) {
            filtered = allAdmitted;
        } else {
            List<NurseWardAssignment> assignments = wardAssignmentRepository.findByNurseId(nurse.getId());
            if (assignments.isEmpty()) {
                filtered = allAdmitted;
            } else {
                Set<Long> wardIds = assignments.stream()
                        .map(NurseWardAssignment::getWardId).collect(Collectors.toSet());
                filtered = allAdmitted.stream()
                        .filter(a -> a.getWardId() != null && wardIds.contains(a.getWardId()))
                        .collect(Collectors.toList());
            }
        }

        List<com.hms.dto.IpdAdmissionSummaryDTO> result = new java.util.ArrayList<>();
        for (IpdAdmission ipd : filtered) {
            com.hms.dto.IpdAdmissionSummaryDTO dto = new com.hms.dto.IpdAdmissionSummaryDTO();
            dto.setIpdId(ipd.getId());
            dto.setIpdNumber(ipd.getIpdNumber());
            // patient
            patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
                dto.setPatientName(p.getName());
                try { dto.setAge(p.getAge()); } catch (Exception ignored) {}
                dto.setGender(p.getGender());
                dto.setUhid(p.getCustomId());
            });
            // ward/bed
            wardRepository.findById(ipd.getWardId()).ifPresent(w -> dto.setWardName(w.getWardName()));
            bedRepository.findById(ipd.getBedId()).ifPresent(b -> dto.setBedNumber(b.getBedCode()));
            // doctor
            doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.setDoctorName(d.getName()));
            dto.setAdmissionDateTime(ipd.getAdmissionDatetime());
            dto.setStatus(ipd.getStatus());
            result.add(dto);
        }
        return result;
    }

    public List<NurseTask> getMyTasks() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<com.hms.dto.IpdAdmissionSummaryDTO> patients = getMyPatients();
        List<Long> admissionIds = patients.stream()
                .map(com.hms.dto.IpdAdmissionSummaryDTO::getIpdId).collect(Collectors.toList());
        if (admissionIds.isEmpty()) return List.of();

        return nurseTaskRepository.findByIpdAdmissionIdInAndStatus(admissionIds, "PENDING");
    }

    public ShiftActivityDTO getShiftActivity(LocalDateTime shiftStart, LocalDateTime shiftEnd) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        long completedTasks = nurseTaskRepository.countCompletedInShift(hospitalId, shiftStart, shiftEnd);
        long newAdmissions = ipdAdmissionRepository
                .findByHospitalIdAndAdmissionDatetimeBetween(hospitalId, shiftStart, shiftEnd)
                .size();

        return new ShiftActivityDTO(completedTasks, newAdmissions);
    }
}
