package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.MrdRecord;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.MrdRecordRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MrdService {

    @Autowired
    private com.hms.repository.PatientRepository patientRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.hms.repository.UserRepository userRepository;

    @Autowired
    private MrdRecordRepository mrdRecordRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    public List<com.hms.dto.MrdPendingDTO> listPendingArchive() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<IpdAdmission> discharged = ipdAdmissionRepository.findByHospitalIdAndStatus(hospitalId, "DISCHARGED");
        List<com.hms.dto.MrdPendingDTO> pending = new ArrayList<>();
        for (IpdAdmission ipd : discharged) {
            if (!mrdRecordRepository.findByIpdAdmissionId(ipd.getId()).isPresent()) {
                com.hms.dto.MrdPendingDTO dto = new com.hms.dto.MrdPendingDTO();
                dto.ipdAdmissionId = ipd.getId();
                dto.ipdNumber = ipd.getIpdNumber();
                dto.admissionDateTime = ipd.getAdmissionDatetime();
                dto.dischargeDateTime = ipd.getDischargeDatetime();
                
                patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
                    dto.patientName = p.getName();
                    dto.patientGender = p.getGender();
                    try { dto.patientAge = p.getAge(); } catch (Exception ignored) {}
                });

                if (ipd.getDoctorId() != null) {
                    doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.doctorName = d.getName());
                }
                pending.add(dto);
            }
        }
        return pending;
    }

    public List<com.hms.dto.MrdArchivedDTO> listArchived() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        List<MrdRecord> archived = mrdRecordRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
        List<com.hms.dto.MrdArchivedDTO> list = new ArrayList<>();
        for (MrdRecord m : archived) {
            com.hms.dto.MrdArchivedDTO dto = new com.hms.dto.MrdArchivedDTO();
            dto.id = m.getId();
            dto.ipdAdmissionId = m.getIpdAdmissionId();
            dto.mrdNumber = m.getMrdNumber();
            dto.rackLocation = m.getRackLocation();
            dto.archivedAt = m.getArchivedAt();

            ipdAdmissionRepository.findById(m.getIpdAdmissionId()).ifPresent(ipd -> {
                dto.ipdNumber = ipd.getIpdNumber();
                patientRepository.findById(ipd.getPatientId()).ifPresent(p -> dto.patientName = p.getName());
                if (ipd.getDoctorId() != null) {
                    doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.doctorName = d.getName());
                }
            });

            userRepository.findById(m.getArchivedById()).ifPresent(u -> dto.archivedByName = u.getName());
            list.add(dto);
        }
        return list;
    }

    @Transactional
    public MrdRecord archiveAdmission(Long ipdAdmissionId, String rackLocation) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        Long userId = securityHelper.getCurrentUserId();
        if (userId == null) throw new UnauthorizedException("User ID not found");

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD admission not found"));

        if (!ipd.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        if (!"DISCHARGED".equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException("Admission must be DISCHARGED before it can be archived in MRD");
        }

        if (mrdRecordRepository.findByIpdAdmissionId(ipdAdmissionId).isPresent()) {
            throw new IllegalArgumentException("Admission is already archived in MRD");
        }

        if (rackLocation == null || rackLocation.trim().isEmpty()) {
            throw new IllegalArgumentException("Shelf/Rack location is required");
        }

        // Generate sequential MRD number
        Integer maxSeq = mrdRecordRepository.findMaxMrdSequence();
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        String mrdNumber = "MRD-" + nextSeq;

        MrdRecord mrd = new MrdRecord();
        mrd.setHospitalId(hospitalId);
        mrd.setIpdAdmissionId(ipdAdmissionId);
        mrd.setMrdNumber(mrdNumber);
        mrd.setRackLocation(rackLocation.trim());
        mrd.setStatus("ARCHIVED");
        mrd.setArchivedAt(LocalDateTime.now());
        mrd.setArchivedById(userId);

        return mrdRecordRepository.save(mrd);
    }

    public void validateAdmissionActive(Long ipdAdmissionId) {
        if (ipdAdmissionId == null) return;

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new RuntimeException("IPD admission not found"));

        if ("DISCHARGED".equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalStateException("Clinical modifications are blocked: patient is already discharged.");
        }

        if (mrdRecordRepository.findByIpdAdmissionId(ipdAdmissionId).isPresent()) {
            throw new IllegalStateException("Clinical record is locked and archived in MRD.");
        }
    }

    public boolean isAdmissionArchived(Long ipdAdmissionId) {
        if (ipdAdmissionId == null) return false;
        return mrdRecordRepository.findByIpdAdmissionId(ipdAdmissionId).isPresent();
    }
}
