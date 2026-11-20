package com.hms.service.hospital;

import com.hms.entity.Bed;
import com.hms.entity.Billing;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Opd;
import com.hms.repository.BedRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.OpdRepository;
import com.hms.security.SecurityContextHelper;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.exception.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class IpdAdmissionService {

    private static final String STATUS_ADMITTED = "ADMITTED";
    private static final String STATUS_DISCHARGE_PLANNED = "DISCHARGE_PLANNED";
    private static final String STATUS_DISCHARGED = "DISCHARGED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final Logger logger = LoggerFactory.getLogger(IpdAdmissionService.class);

    /** Admissions whose patient is still in a bed. */
    static final java.util.List<String> IN_HOSPITAL = java.util.List.of(STATUS_ADMITTED, STATUS_DISCHARGE_PLANNED);

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private com.hms.repository.PatientRepository patientRepository;

    // Nurse module (Phase 1): close active nurse assignments on discharge.
    @Autowired
    private com.hms.repository.PatientNurseAssignmentRepository patientNurseAssignmentRepository;

    // Nurse module: auto-assign the admitted patient to a ward nurse.
    @Autowired
    private NurseAssignmentService nurseAssignmentService;

    @Autowired
    private PatientAssignmentService patientAssignmentService;

    @Autowired
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.hms.repository.WardRepository wardRepository;

    @Autowired
    private OpdRepository opdRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private BillingService billingService;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private com.hms.repository.PrescriptionRepository prescriptionRepository;
    @Autowired
    private com.hms.repository.MedicineRepository medicineRepository;
    @Autowired
    private com.hms.repository.BillingItemRepository billingItemRepository;
    @Autowired
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;
    @Autowired
    private com.hms.repository.DischargeSummaryRepository dischargeSummaryRepository;
    @Autowired
    private com.hms.service.hospital.NotificationService notificationService;
    @Autowired
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.AppointmentRepository appointmentRepository;

    @Autowired
    private com.hms.repository.HospitalRepository hospitalRepository;

    @Autowired
    private com.hms.repository.IpdBedHistoryRepository ipdBedHistoryRepository;

    @Autowired
    private com.hms.repository.IcuWardRepository icuWardRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.QueueEntryRepository queueEntryRepository;

    @Autowired
    private com.hms.repository.HospitalInventoryRepository hospitalInventoryRepository;

    @Autowired
    private HospitalInventoryService hospitalInventoryService;

    @Autowired
    private com.hms.repository.HospitalServiceRepository hospitalServiceRepository;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private com.hms.repository.IcuCleaningTaskRepository icuCleaningTaskRepository;

    // Nursing Mgmt Phase C2: all bed status writes go through the audited service.
    @Autowired
    private BedStatusService bedStatusService;

    /**
     * ICU Phase 3. MANDATORY propagation — it joins the transaction opened here and never starts
     * one, so an ICU stay can never commit apart from the bed movement that caused it.
     */
    @Autowired
    private com.hms.service.hospital.icu.IcuStayService icuStayService;

    @Autowired
    private IpdClinicalState ipdClinicalState;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private BillingSchedulerService billingSchedulerService;

    @Transactional
    public IpdAdmission admitFromOpd(Long opdId, Long wardId, Long bedId, String admissionType, String primaryDiagnosis) {
        return admitFromOpd(opdId, null, null, wardId, bedId, admissionType, primaryDiagnosis);
    }

    @Transactional
    public IpdAdmission admitFromOpd(Long opdId, Long directPatientId, Long directDoctorId, Long wardId, Long bedId, String admissionType, String primaryDiagnosis) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        validateAdmissionSelectionIds(opdId, directPatientId, directDoctorId);

        // IPD numbers are globally unique today. Lock one durable global hospital row before
        // reading MAX+1 so admissions from different tenants cannot allocate the same number.
        hospitalRepository.findGlobalIpdNumberLock()
                .orElseThrow(() -> new IllegalStateException("No hospital available for IPD number allocation"));

        Opd opd = resolveAdmissionOpd(opdId, hospitalId);
        com.hms.entity.Patient patient = resolveAdmissionPatient(opd, directPatientId, hospitalId);
        Long patientId = patient.getId();
        Long doctorId = resolveAdmissionDoctor(opd, directDoctorId, hospitalId);

        if (ipdAdmissionRepository.existsByHospitalIdAndPatientIdAndStatusIn(hospitalId, patient.getId(),
                IN_HOSPITAL)) {
            throw new ConflictException("Patient already has an active IPD admission");
        }

        // Lock the selected tenant-scoped bed before checking availability. The lock remains
        // held through the admission and status audit, so exactly one concurrent request wins.
        Bed bed = bedRepository.findByBedIdAndHospitalIdForUpdate(bedId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        if (!bed.getStatus().equalsIgnoreCase("available")) {
            throw new ConflictException("Bed is no longer available");
        }

        // Resolve the selected ward tenant-scoped before creating an admission.
        com.hms.entity.Ward ward = wardRepository.findByWardIdAndHospitalId(wardId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Ward not found"));
        if (!wardId.equals(bed.getWardId())) {
            throw new IllegalArgumentException("Selected bed does not belong to the selected ward");
        }

        // Create IPD admission with sequential IPD-1, IPD-2, IPD-3...
        IpdAdmission ipd = new IpdAdmission();
        long maxIpd = java.util.Optional.ofNullable(ipdAdmissionRepository.findMaxIpdSequence()).orElse(0L);
        if (maxIpd == Long.MAX_VALUE) {
            throw new IllegalStateException("IPD number sequence is exhausted");
        }
        long nextIpd = maxIpd + 1;
        ipd.setIpdNumber("IPD-" + nextIpd);
        ipd.setPatientId(patientId);
        ipd.setDoctorId(doctorId);
        ipd.setHospitalId(hospitalId);
        ipd.setSourceOpdId(opd != null ? opd.getId() : null);
        ipd.setAdmissionType(admissionType != null ? admissionType : "ELECTIVE");
        ipd.setStatus(STATUS_ADMITTED);
        ipd.setAdmissionDatetime(LocalDateTime.now());
        ipd.setWardId(wardId);
        ipd.setBedId(bedId);
        ipd.setPrimaryDiagnosis(primaryDiagnosis != null ? primaryDiagnosis : "");
        ipd.setAdmittedByUserId(securityHelper.getCurrentUserId());

        IpdAdmission saved = ipdAdmissionRepository.save(ipd);

        // Bed history is part of the admission record, not best-effort telemetry.
        com.hms.entity.IpdBedHistory initialHist = new com.hms.entity.IpdBedHistory();
        initialHist.setIpdAdmissionId(saved.getId());
        initialHist.setWardId(wardId);
        initialHist.setBedId(bedId);
        initialHist.setAssignedAt(LocalDateTime.now());
        ipdBedHistoryRepository.save(initialHist);

        // ICU Phase 3: a direct admission into a critical-care ward opens an ICU stay. Critical
        // state, deliberately NOT best-effort — a patient in an ICU bed with no stay record breaks
        // the board's central invariant silently and loses the admission time for good.
        // EMERGENCY vs OPD is derived from the admission type rather than asked again.
        icuStayService.onWardSettled(saved, null,
                "EMERGENCY".equalsIgnoreCase(saved.getAdmissionType())
                        ? com.hms.entity.IcuStay.SRC_EMERGENCY
                        : com.hms.entity.IcuStay.SRC_OPD,
                null, null);

        // Mark bed occupied (Nursing Mgmt Phase C2: audited bed status change)
        Bed occupiedBed = bedStatusService.changeLocked(bed, com.hms.entity.BedStatus.OCCUPIED, "IPD admission");
        occupiedBed.setCurrentIpdAdmissionId(saved.getId());
        bedRepository.save(occupiedBed);

        // Primary assignment is optional, but an attempted persisted assignment must not be
        // silently lost. With zero or multiple eligible nurses this is a no-op.
        patientAssignmentService.onAdmission(saved);

        closeOpdForAdmission(opd);
        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        createAdmissionBill(saved, opd, ward, hospitalId, hospital);

        logger.info("Created IPD admission {} for OPD {}", saved.getIpdNumber(), opdId);

        // Audit log
        try {
            auditLogService.logAction(
                    "IPD_ADMISSION_CREATED",
                    "Patient was admitted to IPD (Case: " + saved.getIpdNumber() + ").",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "IPD",
                    saved.getId().toString(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for IPD admission", e);
        }

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from admitFromOpd", e);
        }

        return saved;
    }

    private void validateAdmissionSelectionIds(Long opdId, Long patientId, Long doctorId) {
        // Enforce selection in the shared service so every admission entry point fails closed.
        if ((opdId != null && opdId <= 0) || (patientId != null && patientId <= 0)
                || (doctorId != null && doctorId <= 0)) {
            throw new IllegalArgumentException("Patient, OPD and doctor IDs must be positive");
        }
        if (opdId == null && patientId == null) {
            throw new IllegalArgumentException("Please select a patient or OPD to admit");
        }
    }

    private Opd resolveAdmissionOpd(Long opdId, Long hospitalId) {
        if (opdId == null) return null;
        Opd opd = opdRepository.findByIdAndHospitalIdWithPatientAndDoctorForUpdate(opdId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("OPD not found"));
        if (Opd.Status.IN_IPD.equals(opd.getStatus())) {
            throw new ConflictException("OPD is already admitted to IPD");
        }
        return opd;
    }

    private com.hms.entity.Patient resolveAdmissionPatient(Opd opd, Long patientId, Long hospitalId) {
        if (opd != null) {
            if (opd.getPatient() == null) {
                throw new IllegalArgumentException("Selected OPD has no patient");
            }
            Long opdPatientId = opd.getPatient().getId();
            if (patientId != null && !patientId.equals(opdPatientId)) {
                throw new IllegalArgumentException("Selected patient does not match the selected OPD");
            }
            patientId = opdPatientId;
        }
        com.hms.entity.Patient patient = patientRepository.findByIdAndHospitalIdForUpdate(patientId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        if (!Boolean.TRUE.equals(patient.getIsActive())) {
            throw new IllegalArgumentException("Selected patient is inactive");
        }
        return patient;
    }

    private Long resolveAdmissionDoctor(Opd opd, Long doctorId, Long hospitalId) {
        if (doctorId == null && opd != null && opd.getDoctor() != null) {
            doctorId = opd.getDoctor().getId();
        }
        if (doctorId == null) {
            java.util.List<com.hms.entity.Doctor> doctors = doctorRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospitalId);
            if (doctors != null && !doctors.isEmpty()) {
                doctorId = doctors.get(0).getId();
            }
        }
        // Validate every source before persistence; foreign and missing doctors are indistinguishable.
        if (doctorId != null && doctorRepository
                .findByIdAndHospitalIdAndIsActiveTrue(doctorId, hospitalId).isEmpty()) {
            throw new ResourceNotFoundException("Doctor not found");
        }
        return doctorId;
    }

    private void closeOpdForAdmission(Opd opd) {
        // Mark OPD as completed/closed
        if (opd != null) {
            try {
                opd.setStatus(Opd.Status.IN_IPD);
            } catch (Exception ex) {
                // fallback if OPD uses enum type
                opd.setStatus(Opd.Status.COMPLETED);
            }
            opdRepository.save(opd);

            // Remove from doctor's active queue
            try {
                queueEntryRepository.deleteByOpdId(opd.getId());
            } catch (Exception e) {
                logger.warn("Failed to delete queue entry for OPD ID during IPD admission", e);
            }
        }
    }

    private void createAdmissionBill(IpdAdmission saved, Opd opd, com.hms.entity.Ward ward,
            Long hospitalId, com.hms.entity.Hospital hospital) {
        boolean hasBillingModule = hospital != null && hospital.getModules() != null && hospital.getModules().contains("BILLING");

        if (!hasBillingModule) {
            return;
        }
        // Create initial IPD billing (empty / started)
        java.math.BigDecimal bedPrice = java.math.BigDecimal.ZERO;
        if (ward.getBedPrice() != null) {
            bedPrice = ward.getBedPrice();
        }

        Long appointmentId = null;
        try {
            java.util.List<com.hms.entity.Appointment> appointments = appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(saved.getPatientId(), hospitalId);
            if (appointments != null && !appointments.isEmpty()) {
                appointmentId = appointments.get(0).getId();
            }
        } catch (Exception e) {
            logger.debug("Could not resolve latest appointment for patient during IPD admission billing", e);
        }

        Billing bill = new Billing();
        bill.setHospitalId(hospitalId);
        bill.setPatientId(saved.getPatientId());
        bill.setDoctorId(saved.getDoctorId());
        bill.setIpdAdmissionId(saved.getId());
        bill.setOpdId(opd != null ? opd.getId() : null);
        bill.setAppointmentId(appointmentId);
        bill.setBillingType("IPD");
        bill.setAmount(bedPrice);
        bill.setDescription("Bed Price");
        bill.setPaymentStatus("PENDING");
        Billing savedBill = null;
        if (billingService != null) {
            savedBill = billingService.saveBillWithCollisionRetry(bill);
        }
        if (savedBill == null) {
            savedBill = billingRepository.save(bill);
        }

        // Create billing item for bed price
        if (bedPrice != null) {
            com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
            item.setBillingId(savedBill.getId());
            item.setHospitalId(hospitalId);
            item.setDescription("Bed Price");
            item.setAmount(bedPrice);
            billingItemRepository.save(item);
        }
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<java.util.Map<String, Object>> listIpdAdmissions(int page, int size, String search) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "admissionDatetime"));
        org.springframework.data.domain.Page<IpdAdmission> p = ipdAdmissionRepository.findByHospitalId(hospitalId, pageable);

        java.util.List<java.util.Map<String,Object>> rows = new java.util.ArrayList<>();
        for (IpdAdmission ipd : p.getContent()) {
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("ipd", ipd);
            patientRepository.findById(ipd.getPatientId()).ifPresent(patient -> m.put("patient", patient));
            doctorRepository.findById(ipd.getDoctorId()).ifPresent(doc -> m.put("doctor", doc));
            wardRepository.findById(ipd.getWardId()).ifPresent(ward -> m.put("ward", ward));
            bedRepository.findById(ipd.getBedId()).ifPresent(bed -> m.put("bed", bed));
            rows.add(m);
        }

        return new org.springframework.data.domain.PageImpl<>(rows, pageable, p.getTotalElements());
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> listMyIpdAdmissionsForDoctor() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        // Resolve current doctor's entity using authenticated user's email
        String email = securityHelper.getCurrentUserEmail();
        com.hms.entity.Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
            .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));
        Long doctorId = doctor.getId();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 100, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "admissionDatetime"));
        org.springframework.data.domain.Page<IpdAdmission> p = ipdAdmissionRepository.findByHospitalIdAndDoctorIdAndStatusIn(hospitalId, doctorId, IN_HOSPITAL, pageable);

        java.util.List<java.util.Map<String,Object>> rows = new java.util.ArrayList<>();
        for (IpdAdmission ipd : p.getContent()) {
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("ipd", ipd);
            patientRepository.findById(ipd.getPatientId()).ifPresent(patient -> m.put("patient", patient));
            doctorRepository.findById(ipd.getDoctorId()).ifPresent(doc -> m.put("doctor", doc));
            wardRepository.findById(ipd.getWardId()).ifPresent(ward -> m.put("ward", ward));
            bedRepository.findById(ipd.getBedId()).ifPresent(bed -> m.put("bed", bed));
            rows.add(m);
        }

        return rows;
    }

    /**
     * Role-aware fetch of currently ADMITTED IPD admissions.
     * Returns only ADMITTED patients. Receptionist sees all hospital admissions,
     * Doctor sees only their assigned admissions.
     */
    @Transactional(readOnly = true)
    public java.util.List<com.hms.dto.IpdAdmissionSummaryDTO> getAdmittedIpdSummariesForCurrentUser() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        String role = securityHelper.getCurrentUserRole();
        java.util.List<IpdAdmission> admissions;

        if ("DOCTOR".equalsIgnoreCase(role)) {
            String email = securityHelper.getCurrentUserEmail();
            com.hms.entity.Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));
            Long doctorId = doctor.getId();
            // A planned discharge is still the doctor's patient, in the bed until reception confirms.
            admissions = ipdAdmissionRepository.findByHospitalIdAndDoctorIdAndStatusIn(hospitalId, doctorId, IN_HOSPITAL);
        } else if ("RECEPTIONIST".equalsIgnoreCase(role) || "HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            admissions = ipdAdmissionRepository.findByHospitalIdAndStatusIn(hospitalId, IN_HOSPITAL);
        } else {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed");
        }

        java.util.Set<Long> icuWardIds = icuWardRepository.findByHospitalId(hospitalId).stream()
                .map(com.hms.entity.IcuWard::getWardId)
                .collect(java.util.stream.Collectors.toSet());

        java.util.List<com.hms.dto.IpdAdmissionSummaryDTO> result = new java.util.ArrayList<>();
        for (IpdAdmission ipd : admissions) {
            if (ipd.getWardId() != null && icuWardIds.contains(ipd.getWardId())) {
                continue;
            }
            com.hms.dto.IpdAdmissionSummaryDTO dto = new com.hms.dto.IpdAdmissionSummaryDTO();
            dto.setIpdId(ipd.getId());
            dto.setIpdNumber(ipd.getIpdNumber());
            // patient
            patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
                dto.setPatientName(p.getName());
                try { dto.setAge(p.getAge()); } catch (Exception e) { logger.debug("Could not map patient age to DTO", e); }
                dto.setGender(p.getGender());
            });
            // ward/bed
            wardRepository.findById(ipd.getWardId()).ifPresent(w -> dto.setWardName(w.getWardName()));
            bedRepository.findById(ipd.getBedId()).ifPresent(b -> dto.setBedNumber(b.getBedCode()));
            // doctor
            doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.setDoctorName(d.getName()));
            dto.setAdmissionConfirmed(Boolean.TRUE.equals(ipd.getAdmissionConfirmed()));
            dto.setAdmissionDateTime(ipd.getAdmissionDatetime());
            dto.setStatus(ipd.getStatus());
            result.add(dto);
        }

        // sort by admissionDatetime desc
        result.sort((a,b) -> b.getAdmissionDateTime().compareTo(a.getAdmissionDateTime()));
        return result;
    }

    @Transactional(readOnly = true)
    /**
     * Load an admission by id and prove it belongs to the caller's hospital. Every
     * user-facing method here takes a raw numeric ipdId from the URL; using this instead
     * of a bare findById is what stops one hospital reading or mutating another's admission
     * (and, in the discharge/bed paths, silently adopting the victim's tenant context via
     * hospitalId = ipd.getHospitalId()). A cross-tenant id is reported as not-found.
     */
    private IpdAdmission requireOwnedAdmission(Long ipdId) {
        Long callerHospitalId = securityHelper.getCurrentHospitalId();
        if (callerHospitalId == null) throw new ResourceNotFoundException("IPD admission not found");
        return ipdAdmissionRepository.findByIdAndHospitalId(ipdId, callerHospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("IPD admission not found"));
    }

    /** Re-reads the admission under lock before a lifecycle or bed mutation. */
    private IpdAdmission requireOwnedAdmissionForUpdate(Long ipdId) {
        Long callerHospitalId = securityHelper.getCurrentHospitalId();
        if (callerHospitalId == null) throw new ResourceNotFoundException("IPD admission not found");
        return ipdAdmissionRepository.findByIdAndHospitalIdForUpdate(ipdId, callerHospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("IPD admission not found"));
    }

    public com.hms.dto.IpdAdmissionDetailsDTO getIpdAdmissionDetails(Long ipdId) {
        IpdAdmission ipd = requireOwnedAdmission(ipdId);

        com.hms.dto.IpdAdmissionDetailsDTO dto = new com.hms.dto.IpdAdmissionDetailsDTO();
        dto.setIpdNumber(ipd.getIpdNumber());
        dto.setStatus(ipd.getStatus());
        dto.setDischargeType(ipd.getDischargeType());
        dto.setDischargeNote(ipd.getDischargeNote());
        dto.setDischargeDateTime(ipd.getDischargeDatetime());

        // patient
        com.hms.entity.Patient patient = patientRepository.findById(ipd.getPatientId()).orElse(null);
        if (patient != null) {
            com.hms.dto.IpdAdmissionDetailsDTO.PatientDTO p = new com.hms.dto.IpdAdmissionDetailsDTO.PatientDTO();
            p.id = patient.getId();
            p.name = patient.getName();
            try { p.age = patient.getAge(); } catch (Exception e) { logger.debug("Could not map patient age to details DTO", e); }
            p.gender = patient.getGender();
            dto.setPatient(p);
        }

        // admission info
        com.hms.dto.IpdAdmissionDetailsDTO.AdmissionDTO adm = new com.hms.dto.IpdAdmissionDetailsDTO.AdmissionDTO();
        adm.admissionDateTime = ipd.getAdmissionDatetime();
        adm.admissionType = ipd.getAdmissionType();
        adm.primaryDiagnosis = ipd.getPrimaryDiagnosis();
        adm.admissionConfirmed = Boolean.TRUE.equals(ipd.getAdmissionConfirmed());
        wardRepository.findById(ipd.getWardId()).ifPresent(w -> adm.ward = w.getWardName());
        bedRepository.findById(ipd.getBedId()).ifPresent(b -> adm.bed = b.getBedCode());
        doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> adm.doctor = d.getName());
        adm.doctorId = ipd.getDoctorId();
        dto.setAdmission(adm);

        // medical records for this IPD
        java.util.List<com.hms.entity.MedicalRecord> mrs = new java.util.ArrayList<>();
        try {
            mrs = medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(ipdId);
        } catch (Exception ex) {
            // fallback to empty
            mrs = java.util.Collections.emptyList();
        }
        java.util.List<com.hms.dto.IpdAdmissionDetailsDTO.MedicalRecordDTO> mrDtos = new java.util.ArrayList<>();
        for (com.hms.entity.MedicalRecord mr : mrs) {
            com.hms.dto.IpdAdmissionDetailsDTO.MedicalRecordDTO mrd = new com.hms.dto.IpdAdmissionDetailsDTO.MedicalRecordDTO();
            mrd.date = mr.getCreatedAt() != null ? mr.getCreatedAt().toLocalDate().toString() : null;
            doctorRepository.findById(mr.getDoctorId()).ifPresent(d -> mrd.doctor = d.getName());
            mrd.diagnosis = mr.getDiagnosis();
            mrd.notes = mr.getTreatmentNotes();
            mrDtos.add(mrd);
        }
        dto.setMedicalRecords(mrDtos);

        // active prescriptions -> fetch by IPD and status
        java.util.List<com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO> active = new java.util.ArrayList<>();
        try {
            java.util.List<com.hms.entity.Prescription> activeList = prescriptionRepository.findByIpdAdmissionIdAndStatus(ipdId, "ACTIVE");
            for (com.hms.entity.Prescription p : activeList) {
                com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO pd = new com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO();
                pd.id = p.getId();
                pd.name = p.getMedicineName();
                pd.type = p.getType();
                pd.route = p.getRoute();
                pd.frequency = p.getFrequency();
                pd.status = p.getStatus();
                pd.startDate = p.getStartDate() != null ? p.getStartDate().toString() : null;
                pd.dosage = p.getDosage();
                pd.durationDays = p.getDurationDays();
                active.add(pd);
            }
        } catch (Exception e) {
            logger.warn("Failed to load active prescriptions for IPD {}", ipdId, e);
        }
        dto.setActivePrescriptions(active);

        // all prescriptions history for this IPD (ordered)
        java.util.List<com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO> all = new java.util.ArrayList<>();
        try {
            java.util.List<com.hms.entity.Prescription> allList = prescriptionRepository.findByIpdAdmissionIdOrderByStartDate(ipdId);
            for (com.hms.entity.Prescription p : allList) {
                com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO pd = new com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO();
                pd.id = p.getId();
                pd.name = p.getMedicineName();
                pd.type = p.getType();
                pd.route = p.getRoute();
                pd.frequency = p.getFrequency();
                pd.status = p.getStatus();
                pd.startDate = p.getStartDate() != null ? p.getStartDate().toString() : null;
                pd.dosage = p.getDosage();
                pd.durationDays = p.getDurationDays();
                all.add(pd);
            }
        } catch (Exception e) {
            logger.warn("Failed to load all prescription history for IPD {}", ipdId, e);
        }
        dto.setAllPrescriptions(all);

        // billing summary (aggregate) — discount- and refund-aware, same figures as the bill screens
        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
        if (bills != null && !bills.isEmpty()) {
            java.math.BigDecimal net = java.math.BigDecimal.ZERO;
            java.math.BigDecimal paid = java.math.BigDecimal.ZERO;
            java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
            for (Billing b : bills) {
                if (STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus())) continue;
                BillMoney m = billingService.moneyFor(b);
                net = net.add(m.net());
                paid = paid.add(m.paid());
                discount = discount.add(m.discount());
            }
            com.hms.dto.IpdAdmissionDetailsDTO.BillingDTO bd = new com.hms.dto.IpdAdmissionDetailsDTO.BillingDTO();
            bd.totalAmount = net;
            bd.paidAmount = paid;
            bd.balance = net.subtract(paid);
            bd.discountAmount = discount;
            try {
                bd.outstandingBills = billingService.outstandingBillsForPatient(ipd.getPatientId(), ipd.getHospitalId());
            } catch (Exception e) {
                logger.warn("Failed to list outstanding bills for IPD {}", ipdId, e);
            }
            dto.setBilling(bd);
        } else {
            dto.setBilling(null);
        }

        // administered stock items — sourced from BillingMedicine rows for this IPD
        java.util.List<com.hms.dto.IpdAdmissionDetailsDTO.AdministeredItemDTO> administeredDtos = new java.util.ArrayList<>();
        try {
            if (bills != null) {
                for (Billing b : bills) {
                    java.util.List<com.hms.entity.BillingMedicine> bMeds = billingMedicineRepository.findByBillingId(b.getId());
                    if (bMeds != null) {
                        for (com.hms.entity.BillingMedicine bm : bMeds) {
                            com.hms.dto.IpdAdmissionDetailsDTO.AdministeredItemDTO ad = new com.hms.dto.IpdAdmissionDetailsDTO.AdministeredItemDTO();
                            ad.name = bm.getMedicineName();
                            ad.quantity = bm.getQuantity();
                            ad.administeredAt = bm.getCreatedAt() != null ? bm.getCreatedAt().toLocalDate().toString() : null;
                            administeredDtos.add(ad);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load administered billing medicines for IPD {}", ipdId, e);
        }
        dto.setAdministeredItems(administeredDtos);

        return dto;
    }

    @Transactional
    public com.hms.entity.MedicalRecord addIpdFollowup(Long ipdId, String diagnosis, String notes, java.util.List<com.hms.dto.ConsultationRequest.AdministeredItem> administeredItems) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can add follow-ups");
        }

        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!IpdClinicalState.isActive(ipd)) {
            throw new IllegalArgumentException("Cannot add follow-up to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        String email = securityHelper.getCurrentUserEmail();
        com.hms.entity.Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));

        com.hms.entity.MedicalRecord mr = new com.hms.entity.MedicalRecord();
        mr.setHospitalId(hospitalId);
        mr.setPatientId(ipd.getPatientId());
        mr.setDoctorId(doctor.getId());
        mr.setIpdAdmissionId(ipdId);
        mr.setVisitType("IPD");
        mr.setDiagnosis(diagnosis);
        mr.setTreatmentNotes(notes);

        if (administeredItems != null && !administeredItems.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mr.setAdministeredItemsJson(mapper.writeValueAsString(administeredItems));
            } catch (Exception e) {
                logger.warn("Failed to serialize administered items JSON for IPD follow-up", e);
            }
        }

        com.hms.entity.MedicalRecord saved = medicalRecordRepository.save(mr);

        // IPD follow-ups do NOT add a consultation fee – doctors visit their own admitted patients.

        // --- Process Administered Items (Stock Deductions & Billing) ---
        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        boolean hasBillingModule = hospital != null && hospital.getModules() != null && hospital.getModules().contains("BILLING");

        // Resolve billing record once (not per-item) to avoid N+1 queries
        Billing ipdBill = null;
        if (hasBillingModule) {
            java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
            ipdBill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
        }

        if (administeredItems != null && !administeredItems.isEmpty()) {
            for (com.hms.dto.ConsultationRequest.AdministeredItem item : administeredItems) {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Administered item quantity must be positive");
                }
                if (item.getMedicineId() != null) {
                    // Tenant-scoped: medicineId arrives from the request body, so an unscoped
                    // findById let a caller in one hospital decrement another hospital's stock.
                    // DoctorService's copy of this block always carried the check; these two did not.
                    com.hms.entity.Medicine med = medicineRepository
                            .findByIdAndHospitalId(item.getMedicineId(), hospitalId)
                            .orElseThrow(() -> new ResourceNotFoundException("Medicine not found in active inventory: ID " + item.getMedicineId()));

                    if (med.getStockQuantity() < item.getQuantity()) {
                        throw new IllegalArgumentException("Insufficient stock for: " + med.getName() + " (Requested: " + item.getQuantity() + ", Available: " + med.getStockQuantity() + ")");
                    }

                    // Deduct Stock
                    int oldStock = med.getStockQuantity();
                    med.setStockQuantity(oldStock - item.getQuantity());
                    medicineRepository.save(med);

                    // Audit Log for Stock deduction
                    try {
                        auditLogService.logAction(
                                "INVENTORY_DEDUCTED",
                                "Deducted " + item.getQuantity() + " units of " + med.getName() + " for patient. Stock: " + oldStock + " -> " + med.getStockQuantity(),
                                securityHelper.getCurrentUserEmail(),
                                hospitalId,
                                "MEDICINE",
                                med.getId().toString(),
                                null
                        );
                    } catch (Exception e) {
                        logger.warn("Failed to write audit log for IPD follow-up medicine deduction", e);
                    }

                    if (hasBillingModule && ipdBill != null) {
                        // Create BillingMedicine charge
                        com.hms.entity.BillingMedicine bm = new com.hms.entity.BillingMedicine();
                        bm.setBillingId(ipdBill.getId());
                        bm.setHospitalId(hospitalId);
                        bm.setMedicineId(med.getId());
                        bm.setMedicineName(med.getName());
                        bm.setQuantity(item.getQuantity());
                        bm.setUnitPrice(java.math.BigDecimal.valueOf(med.getUnitPrice()));
                        bm.setAmount(bm.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())));
                        billingMedicineRepository.save(bm);
                    }
                }
            }
        }

        // Recalculate bill total (incorporates consultation fee + medicines + bed fees)
        if (hasBillingModule && ipdBill != null) {
            try {
                billingService.recalculateTotal(ipdBill.getId());
            } catch (Exception e) {
                logger.warn("Failed to recalculate billing total after IPD follow-up", e);
            }
        }

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from addIpdFollowup", e);
        }

        return saved;
    }

    @Transactional
    public void administerItems(Long ipdId, java.util.List<com.hms.dto.ConsultationRequest.AdministeredItem> administeredItems) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can administer items");
        }

        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!IpdClinicalState.isActive(ipd)) {
            throw new IllegalArgumentException("Cannot administer items to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        boolean hasBillingModule = hospital != null && hospital.getModules() != null && hospital.getModules().contains("BILLING");

        // Resolve billing record once (not per-item) to avoid N+1 queries
        Billing ipdBill = null;
        if (hasBillingModule) {
            java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
            ipdBill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
        }

        if (administeredItems != null && !administeredItems.isEmpty()) {
            for (com.hms.dto.ConsultationRequest.AdministeredItem item : administeredItems) {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Administered item quantity must be positive");
                }
                if (item.getMedicineId() != null) {
                    // Tenant-scoped: medicineId arrives from the request body, so an unscoped
                    // findById let a caller in one hospital decrement another hospital's stock.
                    // DoctorService's copy of this block always carried the check; these two did not.
                    com.hms.entity.Medicine med = medicineRepository
                            .findByIdAndHospitalId(item.getMedicineId(), hospitalId)
                            .orElseThrow(() -> new ResourceNotFoundException("Medicine not found in active inventory: ID " + item.getMedicineId()));

                    if (med.getStockQuantity() < item.getQuantity()) {
                        throw new IllegalArgumentException("Insufficient stock for: " + med.getName() + " (Requested: " + item.getQuantity() + ", Available: " + med.getStockQuantity() + ")");
                    }

                    // Deduct Stock
                    int oldStock = med.getStockQuantity();
                    med.setStockQuantity(oldStock - item.getQuantity());
                    medicineRepository.save(med);

                    // Audit Log for Stock deduction
                    try {
                        auditLogService.logAction(
                                "INVENTORY_DEDUCTED",
                                "Deducted " + item.getQuantity() + " units of " + med.getName() + " for patient. Stock: " + oldStock + " -> " + med.getStockQuantity(),
                                securityHelper.getCurrentUserEmail(),
                                hospitalId,
                                "MEDICINE",
                                med.getId().toString(),
                                null
                        );
                    } catch (Exception e) {
                        logger.warn("Failed to write audit log for IPD medicine administration deduction", e);
                    }

                    if (hasBillingModule && ipdBill != null) {
                        // Create BillingMedicine charge
                        com.hms.entity.BillingMedicine bm = new com.hms.entity.BillingMedicine();
                        bm.setBillingId(ipdBill.getId());
                        bm.setHospitalId(hospitalId);
                        bm.setMedicineId(med.getId());
                        bm.setMedicineName(med.getName());
                        bm.setQuantity(item.getQuantity());
                        bm.setUnitPrice(java.math.BigDecimal.valueOf(med.getUnitPrice()));
                        bm.setAmount(bm.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())));
                        billingMedicineRepository.save(bm);
                    }
                }
            }

            if (hasBillingModule && ipdBill != null) {
                // Recalculate bill total (incorporates consultation fee + medicines + bed fees)
                try {
                    billingService.recalculateTotal(ipdBill.getId());
                } catch (Exception e) {
                    logger.warn("Failed to recalculate billing total after IPD medicine administration", e);
                }
            }
        }

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from administerItems", e);
        }
    }

    @Transactional
    public void administerHospitalItems(Long ipdId, java.util.List<com.hms.dto.AdministerHospitalItemsRequest.HospitalItem> items) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can administer items");
        }

        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (ipd.getStatus() == null || (!ipd.getStatus().equalsIgnoreCase(STATUS_ADMITTED) && !ipd.getStatus().equalsIgnoreCase(STATUS_DISCHARGE_PLANNED))) {
            throw new IllegalArgumentException("Cannot administer items to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        boolean hasBillingModule = hospital != null && hospital.getModules() != null && hospital.getModules().contains("BILLING");

        // Resolve billing record once (not per-item) to avoid N+1 queries
        Billing ipdBill = null;
        if (hasBillingModule) {
            java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
            ipdBill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
        }

        if (items != null && !items.isEmpty()) {
            for (com.hms.dto.AdministerHospitalItemsRequest.HospitalItem item : items) {
                administerSingleHospitalItem(item, hospitalId, hasBillingModule, ipdBill);
            }
            if (hasBillingModule && ipdBill != null) {
                recalcBillTotalQuietly(ipdBill.getId());
            }
        }

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from administerHospitalItems", e);
        }
    }

    /** Consumes one hospital service and, when billing is active, appends a billing line for it. */
    private void administerSingleHospitalItem(com.hms.dto.AdministerHospitalItemsRequest.HospitalItem item,
            Long hospitalId, boolean hasBillingModule, Billing ipdBill) {
        java.math.BigDecimal serviceCharge = hospitalInventoryService.consumeService(
                item.getServiceId(), item.getQuantity(), hospitalId);
        if (hasBillingModule && ipdBill != null) {
            com.hms.entity.HospitalServiceEntity svc = hospitalServiceRepository.findByIdAndHospitalId(item.getServiceId(), hospitalId).orElse(null);
            String svcName = svc != null ? svc.getName() : ("Service #" + item.getServiceId());
            com.hms.entity.BillingItem bi = new com.hms.entity.BillingItem();
            bi.setBillingId(ipdBill.getId());
            bi.setHospitalId(hospitalId);
            bi.setDescription(svcName + " (Qty: " + item.getQuantity() + ")");
            bi.setAmount(serviceCharge);
            billingItemRepository.save(bi);
        }
    }

    /** Recalculates a bill's total (consultation + medicines + bed fees + hospital items); best-effort. */
    private void recalcBillTotalQuietly(Long billingId) {
        try {
            billingService.recalculateTotal(billingId);
        } catch (Exception e) {
            logger.warn("Failed to recalculate billing total after hospital item administration", e);
        }
    }

    @Transactional
    public com.hms.entity.Prescription addIpdPrescription(Long ipdId, com.hms.dto.AddIpdPrescriptionRequest req) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can add prescriptions");
        }

        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!IpdClinicalState.isActive(ipd)) {
            throw new IllegalArgumentException("Cannot add prescription to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        // Resolve latest medical record for this IPD
        java.util.List<com.hms.entity.MedicalRecord> mrs = medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(ipdId);
        com.hms.entity.MedicalRecord latest;
        if (mrs == null || mrs.isEmpty()) {
            latest = new com.hms.entity.MedicalRecord();
            latest.setHospitalId(hospitalId);
            latest.setPatientId(ipd.getPatientId());
            Long resolvedDocId = ipd.getDoctorId();
            if (resolvedDocId == null) {
                try {
                    java.util.Optional<com.hms.entity.Doctor> dopt = doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId);
                    if (dopt.isPresent()) {
                        resolvedDocId = dopt.get().getId();
                    }
                } catch (Exception e) {
                    logger.debug("Could not resolve doctor during IPD prescription creation", e);
                }
            }
            latest.setDoctorId(resolvedDocId);
            latest.setIpdAdmissionId(ipdId);
            latest.setVisitType("IPD");
            latest.setDiagnosis(ipd.getPrimaryDiagnosis() != null && !ipd.getPrimaryDiagnosis().trim().isEmpty() ? ipd.getPrimaryDiagnosis() : "IPD Admission");
            latest.setTreatmentNotes("Initial IPD Admission Medical Record");
            latest = medicalRecordRepository.save(latest);
        } else {
            latest = mrs.get(0);
        }

        String medicineName = req.getMedicineName() != null && !req.getMedicineName().trim().isEmpty()
                ? req.getMedicineName().trim()
                : null;
        if ((req.getMedicineName() == null || req.getMedicineName().trim().isEmpty()) && req.getMedicineId() == null
                && (req.getHospitalItemName() == null || req.getHospitalItemName().isBlank())) {
            throw new IllegalArgumentException("Either Medicine Name or Medicine ID is required");
        }
        if (req.getDose() == null || req.getDose().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription dose is required");
        }
        if (req.getFrequency() == null || req.getFrequency().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription frequency is required");
        }
        if (req.getType() == null || req.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription type is required");
        }
        if (req.getRoute() == null || req.getRoute().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription route is required");
        }
        if (req.getStartDate() == null || req.getStartDate().isBefore(java.time.LocalDate.now(java.time.ZoneId.systemDefault()))) {
            throw new IllegalArgumentException("Prescription start date cannot be in the past");
        }
        if (req.getDurationDays() == null || req.getDurationDays() <= 0) {
            throw new IllegalArgumentException("Prescription duration must be at least 1 day");
        }

        // Resolve the medicine, and with it the optional link to stock. After validation, so a
        // badly-formed order is reported as badly formed rather than as a lookup failure.
        //
        // A medicineId means the prescriber picked a row out of this facility's inventory, and
        // keeping that link is what later lets the nurse's chart show real stock instead of
        // guessing. It is scoped to the caller's facility because the id arrives in the request
        // body: unscoped, it read another hospital's medicine and copied its name onto this order.
        //
        // No medicineId means the doctor typed a name. That is a valid order and is stored as
        // typed; nothing here goes looking for an inventory row that might match the text.
        // Saline, IV fluids and consumables come from the hospital inventory, by item name.
        String hospitalItem = req.getHospitalItemName() == null || req.getHospitalItemName().isBlank()
                ? null : req.getHospitalItemName().trim();
        if (hospitalItem != null && hospitalInventoryRepository
                .findByNameAndHospitalIdAndIsActiveTrue(hospitalItem, hospitalId).isEmpty()) {
            throw new ResourceNotFoundException("Item not found in this hospital's inventory: " + hospitalItem);
        }
        com.hms.entity.Medicine linkedMedicine = null;
        if (hospitalItem == null && req.getMedicineId() != null) {
            linkedMedicine = medicineRepository.findByIdAndHospitalId(req.getMedicineId(), hospitalId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Medicine not found in this facility's inventory: ID " + req.getMedicineId()));
        }
        if (medicineName == null) {
            medicineName = hospitalItem != null ? hospitalItem : linkedMedicine.getName();
        }

        com.hms.entity.Prescription p = new com.hms.entity.Prescription();
        p.setHospitalId(hospitalId);
        p.setMedicalRecordId(latest.getId());
        p.setMedicineName(medicineName);
        p.setMedicineId(linkedMedicine == null ? null : linkedMedicine.getId());
        if (hospitalItem != null) {
            p.setStockSource(MedicationStockService.SOURCE_HOSPITAL_ITEM);
            p.setHospitalItemName(hospitalItem);
        } else if (linkedMedicine != null) {
            p.setStockSource(MedicationStockService.SOURCE_MEDICINE);
        }
        p.setQuantityPerDose(req.getQuantityPerDose() == null || req.getQuantityPerDose() < 1 ? 1 : req.getQuantityPerDose());
        p.setType(req.getType() != null ? req.getType() : "TABLET");
        p.setRoute(req.getRoute() != null ? req.getRoute() : "ORAL");
        p.setFoodTiming(com.hms.entity.FoodTiming.normalize(req.getFoodTiming()));
        p.setDosage(req.getDose());
        p.setFrequency(req.getFrequency());
        p.setDurationDays(req.getDurationDays());
        p.setStartDate(req.getStartDate());
        p.setStatus("ACTIVE");

        com.hms.entity.Prescription saved = prescriptionRepository.save(p);

        // Trigger notification to the assigned nurse, if any
        try {
            patientNurseAssignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(ipdId)
                .ifPresent(assignment -> {
                    notificationService.create(
                        assignment.getNurseUserId(),
                        hospitalId,
                        "PRESCRIPTION_ACTIVE",
                        "New Active Prescription",
                        "A new prescription for " + saved.getDosage() + " of " + saved.getMedicineName() + " has been added.",
                        "PRESCRIPTION",
                        saved.getId()
                    );
                });
        } catch (Exception e) {
            logger.error("Failed to trigger prescription add notification: {}", e.getMessage(), e);
        }

        // Standard prescriptions are now strictly informative (no auto-deduction/billing)
        return saved;
    }

    @Transactional
    public com.hms.entity.Prescription stopPrescription(Long prescriptionId) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can stop prescriptions");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        com.hms.entity.Prescription pres = prescriptionRepository.findByIdAndHospitalId(prescriptionId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));

        // Verify it belongs to an IPD by looking up medical record
        com.hms.entity.MedicalRecord mr = medicalRecordRepository.findByIdAndHospitalId(pres.getMedicalRecordId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Related medical record not found"));
        if (mr.getIpdAdmissionId() == null) throw new IllegalArgumentException("Prescription is not linked to an IPD admission");

        pres.setStatus("STOPPED");
        com.hms.entity.Prescription saved = prescriptionRepository.save(pres);

        // Trigger notification to the assigned nurse, if any
        try {
            patientNurseAssignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(mr.getIpdAdmissionId())
                .ifPresent(assignment -> {
                    notificationService.create(
                        assignment.getNurseUserId(),
                        saved.getHospitalId(),
                        "PRESCRIPTION_STOPPED",
                        "Prescription Stopped",
                        "The prescription for " + saved.getMedicineName() + " has been stopped.",
                        "PRESCRIPTION",
                        saved.getId()
                    );
                });
        } catch (Exception e) {
            logger.error("Failed to trigger stop prescription notification: {}", e.getMessage(), e);
        }

        return saved;
    }

    @Transactional
    public com.hms.entity.DischargeSummary planDischarge(Long ipdId, com.hms.dto.PlanDischargeRequest req) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can plan discharge");
        }

        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase(STATUS_ADMITTED)) {
            throw new IllegalArgumentException("Can only plan discharge for ADMITTED patients");
        }

        // One summary per admission: planning again after a cancelled plan rewrites it.
        com.hms.entity.DischargeSummary ds = dischargeSummaryRepository.findByIpdAdmissionId(ipdId)
                .orElseGet(com.hms.entity.DischargeSummary::new);
        ds.setIpdAdmissionId(ipdId);
        ds.setFinalDiagnosis(req.getFinalDiagnosis());
        ds.setTreatmentGiven(req.getTreatmentGiven());
        ds.setDischargeNotes(req.getDischargeNotes());
        ds.setFollowUpDate(req.getFollowUpDate());
        dischargeSummaryRepository.save(ds);

        // The doctor decides how the patient leaves; reception confirms it at discharge.
        ipd.setDischargeType(IpdClinicalState.normaliseDischargeType(req.getDischargeType()));
        String planNote = req.getDischargeTypeNote();
        ipd.setDischargeNote(planNote == null || planNote.isBlank() ? null
                : planNote.trim().substring(0, Math.min(500, planNote.trim().length())));
        ipd.setStatus(STATUS_DISCHARGE_PLANNED);
        ipdAdmissionRepository.save(ipd);

        // Audit log
        try {
            auditLogService.logAction(
                    "IPD_DISCHARGE_PLANNED",
                    "Planned discharge for IPD Case: " + ipd.getIpdNumber() + ".",
                    securityHelper.getCurrentUserEmail(),
                    ipd.getHospitalId(),
                    "IPD",
                    ipd.getId().toString(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for IPD planned discharge", e);
        }

        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from planDischarge", e);
        }

        return ds;
    }

    /**
     * Takes back a planned discharge — the patient got worse, or the plan was a mistake. The
     * admission returns to ADMITTED; the discharge summary already written is kept and is
     * replaced when the discharge is planned again.
     */
    @Transactional
    public IpdAdmission cancelDischargePlan(Long ipdId, String reason) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can cancel a planned discharge");
        }
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException("This patient has no planned discharge to cancel");
        }
        revertDischargePlan(ipd, reason == null || reason.isBlank() ? "Discharge plan cancelled" : reason.trim());
        return ipd;
    }

    private void revertDischargePlan(IpdAdmission ipd, String why) {
        ipd.setStatus(STATUS_ADMITTED);
        ipd.setDischargeType(null);
        ipd.setDischargeNote(null);
        ipdAdmissionRepository.save(ipd);
        try {
            auditLogService.logAction("IPD_DISCHARGE_PLAN_CANCELLED",
                    why + " for IPD Case: " + ipd.getIpdNumber() + ".",
                    securityHelper.getCurrentUserEmail(), ipd.getHospitalId(), "IPD", ipd.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit discharge plan cancellation", e);
        }
        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast refresh after discharge plan cancellation", e);
        }
    }

    @Transactional
    public IpdAdmission confirmDischarge(Long ipdId) {
        return confirmDischarge(ipdId, null, null);
    }

    /**
     * Discharges the patient.
     *
     * @param dischargeType NORMAL (default), LAMA, REFERRED, EXPIRED or ABSCONDED. When null, the
     *                      type the doctor chose while planning the discharge is used.
     * @param dischargeNote referral hospital, cause / time of death, etc.
     */
    @Transactional
    public IpdAdmission confirmDischarge(Long ipdId, String dischargeType, String dischargeNote) {
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        String type = IpdClinicalState.normaliseDischargeType(
                dischargeType != null && !dischargeType.isBlank() ? dischargeType : ipd.getDischargeType());
        String note = dischargeNoteFor(type, dischargeNote, ipd.getDischargeNote());
        Long hospitalId = ipd.getHospitalId();
        if (!isFrontDesk(hospitalId)) {
            throw new org.springframework.security.access.AccessDeniedException("Only receptionists (or doctors under Solo / Both mode) can confirm discharge");
        }
        if (!IpdClinicalState.isActive(ipd)) {
            throw new IllegalArgumentException("Cannot discharge non-admitted patient");
        }
        // Open surgery / ventilator checks come before any write.
        ipdClinicalState.assertDischargeAllowed(ipd, type);

        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        boolean hasBillingModule = hospital != null && hospital.getModules() != null && hospital.getModules().contains("BILLING");
        // Bring bed-day charges up to date first, so the balance checked below is the final one.
        if (hasBillingModule) {
            billingSchedulerService.catchUpAdmission(ipd);
        }
        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);

        // A patient who died or absconded leaves regardless of dues; the bill stays open (not
        // CLOSED) so the balance is still visible and collectable from the family.
        boolean leavesWithDues = IpdClinicalState.dischargeAllowedWithDues(type);
        if (hasBillingModule && !leavesWithDues) {
            assertNothingOwing(ipd);
        }

        completeActivePrescriptions(ipdId);
        ipdClinicalState.closeOutForDischarge(ipd, type);
        sendBedForCleaning(ipd, hospitalId);
        if (hasBillingModule && bills != null) {
            finaliseBills(bills, leavesWithDues);
        }

        // ICU Phase 3: discharge ends any open ICU stay in the same transaction.
        icuStayService.onDischarged(ipd, IpdClinicalState.icuDisposition(type));

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.systemDefault());
        ipd.setStatus(STATUS_DISCHARGED);
        ipd.setDischargeDatetime(now);
        ipd.setDischargeType(type);
        ipd.setDischargeNote(note);
        ipdAdmissionRepository.save(ipd);

        closeNurseAssignment(ipd, now);
        releaseBedHistory(ipd, now);
        auditDischarge(ipd, type);
        return ipd;
    }

    /** The discharge note: required for a death or a referral, kept from the plan when not given. */
    private static String dischargeNoteFor(String type, String given, String planned) {
        String note = given != null && !given.isBlank() ? given.trim() : planned;
        boolean required = IpdClinicalState.EXPIRED.equals(type) || IpdClinicalState.REFERRED.equals(type);
        if (required && (note == null || note.isBlank())) {
            throw new IllegalArgumentException(IpdClinicalState.EXPIRED.equals(type)
                    ? "Enter the time and cause of death"
                    : "Enter the hospital the patient is referred to");
        }
        return note != null && note.length() > 500 ? note.substring(0, 500) : note;
    }

    /** Reception, the admin, or a doctor where doctors run the desk (Solo / Both mode). */
    private boolean isFrontDesk(Long hospitalId) {
        String role = securityHelper.getCurrentUserRole();
        if ("RECEPTIONIST".equalsIgnoreCase(role) || "HOSPITAL_ADMIN".equalsIgnoreCase(role)) return true;
        if (!"DOCTOR".equalsIgnoreCase(role)) return false;
        com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId).orElse(null);
        return settings != null && ("SOLO".equalsIgnoreCase(settings.getReceptionMode())
                || "BOTH".equalsIgnoreCase(settings.getReceptionMode()));
    }

    /**
     * Every unpaid bill of this patient blocks discharge — the admission's IPD bill and also the
     * OPD bill from the consultation that led to it. Balances are discount- and refund-aware
     * (BillMoney), the same figures the billing screens show.
     */
    private void assertNothingOwing(IpdAdmission ipd) {
        java.util.List<java.util.Map<String, Object>> owing =
                billingService.outstandingBillsForPatient(ipd.getPatientId(), ipd.getHospitalId());
        if (owing.isEmpty()) return;
        String detail = owing.stream()
                .map(o -> o.get("customId") + " (" + o.get("billingType") + ") \u20B9"
                        + ((java.math.BigDecimal) o.get("balance")).stripTrailingZeros().toPlainString())
                .collect(java.util.stream.Collectors.joining(", "));
        throw new IllegalArgumentException("Outstanding balance must be settled before discharge: " + detail);
    }

    private void completeActivePrescriptions(Long ipdId) {
        java.util.List<com.hms.entity.Prescription> active = prescriptionRepository.findByIpdAdmissionIdAndStatus(ipdId, "ACTIVE");
        if (active == null) return;
        for (com.hms.entity.Prescription pr : active) {
            pr.setStatus("COMPLETED");
            prescriptionRepository.save(pr);
        }
    }

    /** Vacated beds await cleaning before reuse; BedStatusService queues the cleaning task. */
    private void sendBedForCleaning(IpdAdmission ipd, Long hospitalId) {
        if (ipd.getBedId() == null) {
            throw new IllegalStateException("Active IPD admission has no bed to release");
        }
        Bed bed = bedRepository.findByBedIdAndHospitalIdForUpdate(ipd.getBedId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        if (!com.hms.entity.BedStatus.OCCUPIED.equalsIgnoreCase(bed.getStatus())
                || !ipd.getId().equals(bed.getCurrentIpdAdmissionId())) {
            throw new ConflictException("IPD bed is not occupied by this admission");
        }
        bedStatusService.changeLocked(bed, com.hms.entity.BedStatus.CLEANING, "IPD discharge");
    }

    /** Closes the admission's bills; one left with dues (death / absconded) stays open to collect. */
    private void finaliseBills(java.util.List<Billing> bills, boolean leavesWithDues) {
        for (Billing b : bills) {
            boolean keepOpen = STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus())
                    || (leavesWithDues && billingService.moneyFor(b).balance().signum() > 0);
            if (!keepOpen) {
                b.setPaymentStatus("CLOSED");
                billingRepository.save(b);
            }
        }
    }

    /** The patient drops off the nurse's "My Patients" list. */
    private void closeNurseAssignment(IpdAdmission ipd, LocalDateTime now) {
        patientNurseAssignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(ipd.getId())
                .ifPresent(assignment -> {
                    assignment.setIsActive(false);
                    assignment.setUnassignedAt(now);
                    patientNurseAssignmentRepository.save(assignment);
                });
    }

    private void releaseBedHistory(IpdAdmission ipd, LocalDateTime now) {
        com.hms.entity.IpdBedHistory activeHist = ipdBedHistoryRepository
                .findByIpdAdmissionIdAndReleasedAtIsNull(ipd.getId())
                .orElseThrow(() -> new IllegalStateException("IPD admission has no active bed history to release"));
        activeHist.setReleasedAt(now);
        ipdBedHistoryRepository.save(activeHist);
    }

    private void auditDischarge(IpdAdmission ipd, String type) {
        try {
            auditLogService.logAction("IPD_DISCHARGED",
                    "Confirmed discharge (" + type + ") for IPD Case: " + ipd.getIpdNumber() + ".",
                    securityHelper.getCurrentUserEmail(), ipd.getHospitalId(), "IPD", ipd.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for IPD discharge confirmation", e);
        }
        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from confirmDischarge", e);
        }
    }

    private boolean isAttendingDoctor(IpdAdmission ipd) {
        if (!"DOCTOR".equalsIgnoreCase(securityHelper.getCurrentUserRole())) return false;
        return doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), ipd.getHospitalId())
                .map(me -> me.getId().equals(ipd.getDoctorId())).orElse(false);
    }

    /**
     * Hands an inpatient over to another doctor: the attending doctor is on leave, has left, or
     * the patient was admitted under the wrong name. Without this the patient stayed on the old
     * doctor's list, and no other doctor could find them.
     *
     * <p>The front desk (reception, the admin, a doctor covering the desk) may move any patient; a
     * doctor may hand over their own.
     */
    @org.springframework.transaction.annotation.Transactional
    public IpdAdmission changeDoctor(Long ipdId, Long newDoctorId) {
        if (newDoctorId == null) throw new IllegalArgumentException("Select the doctor who takes over the patient.");
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        Long hospitalId = ipd.getHospitalId();
        if (!isFrontDesk(hospitalId) && !isAttendingDoctor(ipd)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the patient's own doctor, reception or the admin can hand this patient over.");
        }
        IpdClinicalState.requireActive(ipd);
        com.hms.entity.Doctor doctor = doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(newDoctorId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("That doctor is not available in this hospital. Select another doctor."));
        if (doctor.getId().equals(ipd.getDoctorId())) {
            throw new IllegalArgumentException(com.hms.util.DoctorNames.withTitle(doctor.getName()) + " is already this patient's doctor.");
        }
        String from = ipd.getDoctorId() == null ? "no doctor"
                : doctorRepository.findById(ipd.getDoctorId()).map(com.hms.entity.Doctor::getName).orElse("doctor #" + ipd.getDoctorId());
        ipd.setDoctorId(doctor.getId());
        IpdAdmission saved = ipdAdmissionRepository.save(ipd);
        // The IPD bill names the attending doctor on its print-out.
        billingRepository.findByIpdAdmissionId(ipd.getId()).stream()
                .filter(b -> java.util.Objects.equals(b.getHospitalId(), hospitalId))
                .filter(b -> !STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus()))
                .forEach(b -> {
                    b.setDoctorId(doctor.getId());
                    billingRepository.save(b);
                });
        try {
            auditLogService.logAction("IPD_DOCTOR_CHANGED", "IPD " + ipd.getIpdNumber() + " handed over from " + from
                    + " to " + doctor.getName(), securityHelper.getCurrentUserEmail(), hospitalId, "IPD", String.valueOf(ipd.getId()), null);
        } catch (Exception e) {
            logger.warn("Failed to audit IPD doctor change", e);
        }
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast after IPD doctor change", e);
        }
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public IpdAdmission changeBed(Long ipdId, Long newBedId) {
        return changeBed(ipdId, newBedId, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public IpdAdmission changeBed(Long ipdId, Long newBedId, Long expectedWardId) {
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);

        String role = securityHelper.getCurrentUserRole();
        Long hospitalId = ipd.getHospitalId();
        com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId).orElse(null);
        boolean isSoloOrBoth = settings != null &&
            ("SOLO".equalsIgnoreCase(settings.getReceptionMode()) || "BOTH".equalsIgnoreCase(settings.getReceptionMode()));

        if (!"RECEPTIONIST".equalsIgnoreCase(role) && 
            !"HOSPITAL_ADMIN".equalsIgnoreCase(role) && 
            !("DOCTOR".equalsIgnoreCase(role) && isSoloOrBoth)) {
            throw new org.springframework.security.access.AccessDeniedException("Only receptionists (or doctors under Solo / Both mode) can change beds");
        }

        if (!STATUS_ADMITTED.equalsIgnoreCase(ipd.getStatus()) && !STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException("Bed change allowed only for active admissions");
        }

        Long oldBedId = ipd.getBedId();
        if (oldBedId == null) throw new IllegalStateException("Active IPD admission has no current bed");
        if (oldBedId.equals(newBedId)) throw new IllegalArgumentException("Patient is already assigned to the selected bed");

        // A stable order prevents two opposing transfers from deadlocking. The admission row
        // was locked first, followed by its bed resources.
        Long firstBedId = Math.min(oldBedId, newBedId);
        Long secondBedId = Math.max(oldBedId, newBedId);
        Bed first = bedRepository.findByBedIdAndHospitalIdForUpdate(firstBedId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        Bed second = bedRepository.findByBedIdAndHospitalIdForUpdate(secondBedId, hospitalId)
                // Same wording as the first lookup on purpose. The two beds are locked in id
                // order, so which of them is "the new one" depends on the ids involved; a
                // distinct message here would let a caller tell a foreign bed from a missing one
                // by watching which sentence comes back (C4).
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        Bed oldBed = oldBedId.equals(firstBedId) ? first : second;
        Bed newBed = newBedId.equals(firstBedId) ? first : second;
        if (expectedWardId != null && !expectedWardId.equals(newBed.getWardId())) {
            throw new IllegalArgumentException("Selected bed does not belong to the destination ward");
        }
        if (!com.hms.entity.BedStatus.AVAILABLE.equalsIgnoreCase(newBed.getStatus())) {
            throw new ConflictException("Requested bed is no longer available");
        }
        if (!com.hms.entity.BedStatus.OCCUPIED.equalsIgnoreCase(oldBed.getStatus())
                || !ipd.getId().equals(oldBed.getCurrentIpdAdmissionId())) {
            throw new ConflictException("Current IPD bed is not occupied by this admission");
        }

        boolean intoCriticalCare = icuStayService.isCriticalCare(newBed.getWardId(), hospitalId);
        if (!intoCriticalCare && ipdClinicalState.isVentilated(ipd.getId(), hospitalId)) {
            throw new IllegalArgumentException("The patient is on a ventilator and can only move to another "
                    + "ICU / HDU bed. Record ventilation as OFF before moving them to a ward.");
        }
        String inSurgery = ipdClinicalState.surgeryInProgress(ipd.getId());
        if (inSurgery != null) {
            throw new IllegalArgumentException("The patient is in surgery (" + inSurgery
                    + "). Change the bed after the surgery is completed.");
        }

        String oldBedCode = oldBed.getBedCode();
        com.hms.entity.Ward oldWard = wardRepository.findByWardIdAndHospitalId(oldBed.getWardId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Ward not found"));
        String oldWardName = oldWard.getWardName();
        bedStatusService.changeLocked(oldBed, com.hms.entity.BedStatus.CLEANING, "Bed transfer (vacated)");

        Bed occupiedNewBed = bedStatusService.changeLocked(newBed, com.hms.entity.BedStatus.OCCUPIED, "Bed transfer");
        occupiedNewBed.setCurrentIpdAdmissionId(ipd.getId());
        bedRepository.save(occupiedNewBed);

        String newBedCode = newBed.getBedCode();
        com.hms.entity.Ward newWardEntity = wardRepository.findByWardIdAndHospitalId(newBed.getWardId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Ward not found"));
        String newWardName = newWardEntity.getWardName();

        // A chargeable upgrade must post together with the transfer; silently losing it is a
        // financial inconsistency, not an optional notification.
        {
            java.math.BigDecimal oldRate = oldWard.getBedPrice() != null ? oldWard.getBedPrice() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal newRate = newWardEntity.getBedPrice() != null ? newWardEntity.getBedPrice() : java.math.BigDecimal.ZERO;

            java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipd.getId());
            Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;

            // Charge only the part of the new rate not already billed for this admission. Using
            // newRate - oldRate charged a ward -> ICU -> ward -> ICU patient the full upgrade on
            // every return to ICU, because stepping down never credits it back. The admission
            // Bed Price plus earlier adjustments is what has been charged so far; the old ward's
            // rate is the floor, so a legacy bill without those lines keeps the old behaviour.
            java.math.BigDecimal alreadyCharged = oldRate;
            if (bill != null) {
                java.math.BigDecimal billed = java.math.BigDecimal.ZERO;
                for (com.hms.entity.BillingItem it : billingItemRepository.findByBillingId(bill.getId())) {
                    if (it.getAmount() != null && ("Bed Price".equals(it.getDescription())
                            || "Bed Upgrade Price Adjustment".equals(it.getDescription()))) {
                        billed = billed.add(it.getAmount());
                    }
                }
                alreadyCharged = alreadyCharged.max(billed);
            }

            java.math.BigDecimal diff = newRate.subtract(alreadyCharged);
            // If positive difference (upgrade), add to bill immediately
            if (diff.compareTo(java.math.BigDecimal.ZERO) > 0) {
                if (bill != null) {
                    com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
                    item.setBillingId(bill.getId());
                    item.setHospitalId(bill.getHospitalId());
                    item.setDescription("Bed Upgrade Price Adjustment");
                    item.setAmount(diff);
                    billingItemRepository.save(item);

                    billingService.recalculateTotal(bill.getId());
                }
            }
        }

        ipd.setBedId(newBed.getBedId());
        ipd.setWardId(newBed.getWardId());
        
        IpdAdmission saved = ipdAdmissionRepository.save(ipd);

        // ICU Phase 3: one call covers every case, because they are one question — is the patient
        // in critical care now, and were they before? Ward -> ICU opens, ICU -> ward closes,
        // ICU -> a different ICU closes and reopens (readmission is real), and a bed change inside
        // the same unit does nothing, since the stay is bounded by the ward and ipd_bed_history
        // already records the bed. Same transaction as the transfer.
        icuStayService.onWardSettled(saved, oldWard.getWardId(), null, oldWard.getWardId(), null);
        // A patient moved into critical care is no longer going home: take back a planned discharge.
        if (intoCriticalCare && STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(saved.getStatus())) {
            revertDischargePlan(saved, "Discharge plan cancelled: patient moved to critical care");
        }

        // A cross-ward transfer ends primary ownership; destination visibility is derived
        // from the admission's new ward and therefore needs no manufactured row.
        if (!java.util.Objects.equals(oldWard.getWardId(), newWardEntity.getWardId())) {
            patientNurseAssignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(ipd.getId()).ifPresent(assignment -> {
                assignment.setIsActive(false);
                assignment.setUnassignedAt(LocalDateTime.now());
                patientNurseAssignmentRepository.save(assignment);
            });
        }

        // Transfer history is required for a defensible inpatient bed record.
        java.util.Optional<com.hms.entity.IpdBedHistory> activeHistOpt = ipdBedHistoryRepository
                .findByIpdAdmissionIdAndReleasedAtIsNull(ipd.getId());
        if (activeHistOpt.isEmpty()) {
            throw new IllegalStateException("IPD admission has no active bed history to transfer");
        }
        com.hms.entity.IpdBedHistory activeHist = activeHistOpt.get();
        activeHist.setReleasedAt(LocalDateTime.now());
        ipdBedHistoryRepository.save(activeHist);

        com.hms.entity.IpdBedHistory newHist = new com.hms.entity.IpdBedHistory();
        newHist.setIpdAdmissionId(ipd.getId());
        newHist.setWardId(newBed.getWardId());
        newHist.setBedId(newBed.getBedId());
        newHist.setAssignedAt(LocalDateTime.now());
        ipdBedHistoryRepository.save(newHist);

        try {
            String details = "Transferred from Bed " + oldBedCode + " (" + oldWardName + ") to Bed " + newBedCode + " (" + newWardName + ").";
            auditLogService.logAction(
                    "BED_CHANGED",
                    details,
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "IPD",
                    ipd.getId().toString(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for bed change", e);
        }

        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from changeBed", e);
        }
        return saved;
    }
}
