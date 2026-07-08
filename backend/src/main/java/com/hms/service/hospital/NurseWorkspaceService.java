package com.hms.service.hospital;

import com.hms.dto.MyPatientDTO;
import com.hms.dto.NurseDashboardDTO;
import com.hms.dto.NursePatientDetailDTO;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.NurseAccessGuard;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * NurseWorkspaceService - nurse-facing reads (Phase 1): dashboard aggregates,
 * "my patients" (active assignments), and the composite read-only bedside view.
 * Every patient read is gated by an active assignment via {@link NurseAccessGuard}.
 */
@Service
public class NurseWorkspaceService {

    private static final int RECENT_LIMIT = 5;

    @Autowired private PatientNurseAssignmentRepository assignmentRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private WardRepository wardRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private BillingRepository billingRepository;
    @Autowired private BillingPaymentRepository billingPaymentRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private NurseAccessGuard nurseAccessGuard;
    @Autowired private com.hms.repository.NurseProfileRepository nurseProfileRepository;
    @Autowired private SurgeryRepository surgeryRepository;
    @Autowired private com.hms.security.NurseInchargeGuard nurseInchargeGuard;
    @Autowired private NurseAssignmentService nurseAssignmentService;
    @Autowired private NurseShiftScheduleService nurseShiftScheduleService;

    private static final List<String> ACTIVE_SURGERY_STATUSES =
            List.of(Surgery.REQUESTED, Surgery.SCHEDULED, Surgery.IN_PROGRESS);

    /** Whether the current nurse is on shift NOW, derived from today's schedule. */
    public boolean getShiftStatus() {
        return nurseShiftScheduleService.isOnShiftNow(currentProfile().getId());
    }

    /** @deprecated Shifts are scheduled (Phase B); this no longer toggles anything. */
    @Deprecated
    public boolean startShift() { return getShiftStatus(); }

    /** @deprecated Shifts are scheduled (Phase B); this no longer toggles anything. */
    @Deprecated
    public boolean endShift() { return getShiftStatus(); }

    private com.hms.entity.NurseProfile currentProfile() {
        requireHospitalId();
        Long userId = securityHelper.getCurrentUserId();
        return nurseProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Nurse profile not found"));
    }

    public NurseDashboardDTO getDashboard() {
        List<MyPatientDTO> patients = getMyPatients();
        NurseDashboardDTO dto = new NurseDashboardDTO();
        dto.setAssignedPatientCount(patients.size());
        dto.setRecentPatients(patients.size() > RECENT_LIMIT ? patients.subList(0, RECENT_LIMIT) : patients);
        return dto;
    }

    /**
     * Active admissions assigned to the current nurse, enriched for display.
     */
    public List<MyPatientDTO> getMyPatients() {
        Long hospitalId = requireHospitalId();
        Long nurseId = securityHelper.getCurrentUserId();

        List<PatientNurseAssignment> assignments = assignmentRepository.findByNurseUserIdAndIsActiveTrue(nurseId);
        List<MyPatientDTO> result = new ArrayList<>();
        for (PatientNurseAssignment a : assignments) {
            if (!hospitalId.equals(a.getHospitalId())) continue; // defensive scope
            IpdAdmission ipd = ipdAdmissionRepository.findById(a.getIpdAdmissionId()).orElse(null);
            if (ipd == null || "DISCHARGED".equalsIgnoreCase(ipd.getStatus())) continue;

            MyPatientDTO dto = new MyPatientDTO();
            dto.setIpdAdmissionId(ipd.getId());
            dto.setIpdNumber(ipd.getIpdNumber());
            dto.setPrimaryDiagnosis(ipd.getPrimaryDiagnosis());
            dto.setAdmissionDateTime(ipd.getAdmissionDatetime());
            dto.setStatus(ipd.getStatus());
            dto.setAdmissionConfirmed(Boolean.TRUE.equals(ipd.getAdmissionConfirmed()));
            patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
                dto.setPatientName(p.getName());
                dto.setAge(p.getAge());
                dto.setGender(p.getGender());
            });
            doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.setDoctorName(d.getName()));
            if (ipd.getWardId() != null) {
                wardRepository.findById(ipd.getWardId()).ifPresent(w -> dto.setWardName(w.getWardName()));
            }
            if (ipd.getBedId() != null) {
                bedRepository.findById(ipd.getBedId()).ifPresent(b -> dto.setBedCode(b.getBedCode()));
            }
            surgeryRepository.findByIpdAdmissionIdAndStatusIn(ipd.getId(), ACTIVE_SURGERY_STATUSES).stream()
                    .findFirst().ifPresent(s -> dto.setSurgeryStatus(s.getStatus()));
            result.add(dto);
        }
        return result;
    }

    /**
     * Composite read-only bedside view. 403 unless the nurse is assigned.
     */
    public NursePatientDetailDTO getPatientDetail(Long ipdAdmissionId) {
        Long hospitalId = requireHospitalId();
        nurseAccessGuard.assertAssigned(ipdAdmissionId);

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new IllegalArgumentException("IPD admission not found"));
        if (!hospitalId.equals(ipd.getHospitalId())) {
            throw new UnauthorizedException("Access denied: admission belongs to another hospital");
        }

        NursePatientDetailDTO dto = new NursePatientDetailDTO();
        dto.setIpdAdmissionId(ipd.getId());
        dto.setIpdNumber(ipd.getIpdNumber());
        dto.setAdmissionType(ipd.getAdmissionType());
        dto.setStatus(ipd.getStatus());
        dto.setAdmissionDateTime(ipd.getAdmissionDatetime());
        dto.setPrimaryDiagnosis(ipd.getPrimaryDiagnosis());

        patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
            dto.setPatientPublicId(p.getPublicId());
            dto.setPatientName(p.getName());
            dto.setAge(p.getAge());
            dto.setGender(p.getGender());
            dto.setPhone(p.getPhone());
        });
        doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.setDoctorName(d.getName()));
        if (ipd.getWardId() != null) {
            wardRepository.findById(ipd.getWardId()).ifPresent(w -> dto.setWardName(w.getWardName()));
        }
        if (ipd.getBedId() != null) {
            bedRepository.findById(ipd.getBedId()).ifPresent(b -> dto.setBedCode(b.getBedCode()));
        }

        // Latest doctor medical record for this admission
        List<MedicalRecord> records = medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(ipdAdmissionId);
        if (records != null && !records.isEmpty()) {
            MedicalRecord latest = records.get(0);
            dto.setDiagnosis(latest.getDiagnosis());
            dto.setTreatmentNotes(latest.getTreatmentNotes());
            dto.setFollowUpDate(latest.getFollowUpDate());
        }

        // Current (ACTIVE) prescriptions
        List<NursePatientDetailDTO.PrescriptionLite> meds = new ArrayList<>();
        List<Prescription> active = prescriptionRepository.findByIpdAdmissionIdAndStatus(ipdAdmissionId, "ACTIVE");
        if (active != null) {
            for (Prescription pr : active) {
                NursePatientDetailDTO.PrescriptionLite lite = new NursePatientDetailDTO.PrescriptionLite();
                lite.setMedicineName(pr.getMedicineName());
                lite.setDosage(pr.getDosage());
                lite.setFrequency(pr.getFrequency());
                lite.setDuration(pr.getDuration());
                lite.setRoute(pr.getRoute());
                lite.setType(pr.getType());
                lite.setStatus(pr.getStatus());
                meds.add(lite);
            }
        }
        dto.setPrescriptions(meds);

        dto.setBilling(buildBillingSummary(ipdAdmissionId));
        return dto;
    }

    private NursePatientDetailDTO.BillingSummary buildBillingSummary(Long ipdAdmissionId) {
        NursePatientDetailDTO.BillingSummary summary = new NursePatientDetailDTO.BillingSummary();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdAdmissionId);
        if (bills != null) {
            for (Billing b : bills) {
                if (b.getAmount() != null) total = total.add(b.getAmount());
                try {
                    List<BillingPayment> pays = billingPaymentRepository.findByBillingId(b.getId());
                    if (pays != null) {
                        for (BillingPayment p : pays) {
                            if (p.getAmount() != null) paid = paid.add(p.getAmount());
                        }
                    }
                } catch (Exception ignored) { /* best-effort summary */ }
            }
        }
        summary.setTotal(total);
        summary.setPaid(paid);
        summary.setBalance(total.subtract(paid));
        return summary;
    }

    /** Patients across the incharge's wards (or all wards for admin). */
    public List<MyPatientDTO> getWardPatients() {
        List<Long> wardIds = nurseInchargeGuard.myWardIds();
        List<MyPatientDTO> out = new ArrayList<>();
        if (wardIds.isEmpty()) return out;
        for (IpdAdmission ipd :
                ipdAdmissionRepository.findByHospitalIdAndStatus(securityHelper.getCurrentHospitalId(), "ADMITTED")) {
            if (!wardIds.contains(ipd.getWardId())) continue;
            MyPatientDTO dto = new MyPatientDTO();
            dto.setIpdAdmissionId(ipd.getId());
            dto.setIpdNumber(ipd.getIpdNumber());
            dto.setStatus(ipd.getStatus());
            dto.setWardId(ipd.getWardId());
            patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
                dto.setPatientName(p.getName()); dto.setAge(p.getAge()); dto.setGender(p.getGender());
            });
            if (ipd.getWardId() != null)
                wardRepository.findById(ipd.getWardId()).ifPresent(w -> dto.setWardName(w.getWardName()));
            out.add(dto);
        }
        return out;
    }

    @org.springframework.transaction.annotation.Transactional
    public void assignPatientNurse(Long ipdAdmissionId, Long nurseProfileId) {
        nurseInchargeGuard.assertAdmissionInMyWard(ipdAdmissionId);
        com.hms.entity.NurseProfile p = nurseProfileRepository.findById(nurseProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Nurse not found"));
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (!hospitalId.equals(p.getHospitalId()) || !Boolean.TRUE.equals(p.getIsActive())
                || Boolean.TRUE.equals(p.getIsIncharge()) || p.getUserId() == null) {
            throw new IllegalArgumentException("Select an active staff nurse with a login");
        }
        // NurseAssignmentService.assignNurse already closes any prior active
        // assignment for this admission before opening the new one.
        nurseAssignmentService.assignNurse(ipdAdmissionId, p.getUserId(), "Assigned by incharge");
    }

    /** Active, non-incharge staff nurses in a ward the caller may access (for the assign dropdown). */
    public List<java.util.Map<String, Object>> getWardStaffNurses(Long wardId) {
        nurseInchargeGuard.assertWardAccess(wardId);
        Long hospitalId = requireHospitalId();
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (com.hms.entity.NurseProfile p :
                nurseProfileRepository.findByWardIdAndIsInchargeFalseAndIsActiveTrue(wardId)) {
            if (!hospitalId.equals(p.getHospitalId()) || p.getUserId() == null) continue;
            out.add(java.util.Map.of("id", p.getId(), "name", p.getName()));
        }
        return out;
    }

    /** The wards the caller is incharge of (all hospital wards for admin), with their beds. */
    public List<java.util.Map<String, Object>> getMyWards() {
        List<Long> wardIds = nurseInchargeGuard.myWardIds();
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (Long id : wardIds) {
            wardRepository.findById(id).ifPresent(w -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("wardId", w.getWardId());
                m.put("wardName", w.getWardName());
                m.put("beds", bedRepository.findByWardIdAndHospitalId(w.getWardId(), w.getHospitalId()));
                out.add(m);
            });
        }
        return out;
    }

    private Long requireHospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        return hospitalId;
    }
}
