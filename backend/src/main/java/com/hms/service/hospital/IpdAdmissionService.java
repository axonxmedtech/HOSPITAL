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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class IpdAdmissionService {

    private static final Logger logger = LoggerFactory.getLogger(IpdAdmissionService.class);

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private com.hms.repository.PatientRepository patientRepository;

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
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.AppointmentRepository appointmentRepository;

    @Autowired
    private com.hms.repository.HospitalRepository hospitalRepository;

    @Autowired
    private com.hms.repository.IpdBedHistoryRepository ipdBedHistoryRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.QueueEntryRepository queueEntryRepository;

    @Autowired
    private com.hms.repository.HospitalInventoryRepository hospitalInventoryRepository;

    @Autowired
    private HospitalInventoryService hospitalInventoryService;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Transactional
    public IpdAdmission admitFromOpd(Long opdId, Long wardId, Long bedId, String admissionType, String primaryDiagnosis) {
        // Load OPD
        Opd opd = opdRepository.findById(opdId).orElseThrow(() -> new RuntimeException("OPD not found"));

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        // Validate bed availability
        Bed bed = bedRepository.findById(bedId).orElseThrow(() -> new RuntimeException("Bed not found"));
        if (!bed.getStatus().equalsIgnoreCase("available")) {
            throw new IllegalArgumentException("Bed is not available");
        }

        // Create IPD admission with sequential IPD-1, IPD-2, IPD-3...
        IpdAdmission ipd = new IpdAdmission();
        int nextIpd = (ipdAdmissionRepository.findMaxIpdSequence() != null ? ipdAdmissionRepository.findMaxIpdSequence() : 0) + 1;
        ipd.setIpdNumber("IPD-" + nextIpd);
        ipd.setPatientId(opd.getPatient().getId());
        ipd.setDoctorId(opd.getDoctor() != null ? opd.getDoctor().getId() : null);
        ipd.setHospitalId(hospitalId);
        ipd.setSourceOpdId(opd.getId());
        ipd.setAdmissionType(admissionType != null ? admissionType : "ELECTIVE");
        ipd.setStatus("ADMITTED");
        ipd.setAdmissionDatetime(LocalDateTime.now());
        ipd.setWardId(wardId);
        ipd.setBedId(bedId);
        ipd.setPrimaryDiagnosis(primaryDiagnosis != null ? primaryDiagnosis : "");

        IpdAdmission saved = ipdAdmissionRepository.save(ipd);

        // Record initial bed assignment in IpdBedHistory
        try {
            com.hms.entity.IpdBedHistory initialHist = new com.hms.entity.IpdBedHistory();
            initialHist.setIpdAdmissionId(saved.getId());
            initialHist.setWardId(wardId);
            initialHist.setBedId(bedId);
            initialHist.setAssignedAt(LocalDateTime.now());
            ipdBedHistoryRepository.save(initialHist);
        } catch (Exception e) {
            logger.warn("Failed to save initial bed history", e);
        }

        // Mark bed occupied
        bed.setStatus("occupied");
        bed.setCurrentIpdAdmissionId(saved.getId());
        bedRepository.save(bed);

        // Mark OPD as completed/closed
        // OPD status is stored as a string in many places; set to string to avoid enum mismatch
        try {
            opd.setStatus(Opd.Status.IN_IPD);
        } catch (Exception ex) {
            // fallback if OPD uses enum type
            opd.setStatus(Opd.Status.COMPLETED);
        }
        opdRepository.save(opd);

        // Remove from doctor's active queue
        try {
            queueEntryRepository.deleteByOpdId(opdId);
        } catch (Exception ignored) {}

        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        boolean hasBillingModule = hospital != null && hospital.getModules() != null && hospital.getModules().contains("BILLING");

        if (hasBillingModule) {
            // Create initial IPD billing (empty / started)
            java.math.BigDecimal bedPrice = java.math.BigDecimal.ZERO;
            if (wardId != null) {
                java.util.Optional<com.hms.entity.Ward> wardOpt = wardRepository.findById(wardId);
                if (wardOpt.isPresent()) {
                    java.math.BigDecimal bp = wardOpt.get().getBedPrice();
                    if (bp != null) {
                        bedPrice = bp;
                    }
                }
            }

            Long appointmentId = null;
            try {
                java.util.List<com.hms.entity.Appointment> appointments = appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(saved.getPatientId(), hospitalId);
                if (appointments != null && !appointments.isEmpty()) {
                    appointmentId = appointments.get(0).getId();
                }
            } catch (Exception ignored) {}

            Billing bill = new Billing();
            bill.setHospitalId(hospitalId);
            bill.setPatientId(saved.getPatientId());
            bill.setDoctorId(saved.getDoctorId());
            bill.setIpdAdmissionId(saved.getId());
            bill.setOpdId(opd.getId());
            bill.setAppointmentId(appointmentId);
            bill.setBillingType("IPD");
            bill.setAmount(bedPrice);
            bill.setDescription("Bed Price");
            bill.setPaymentStatus("PENDING");
            Billing savedBill = billingRepository.save(bill);

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
            .orElseThrow(() -> new RuntimeException("Doctor profile not found for current user"));
        Long doctorId = doctor.getId();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 100, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "admissionDatetime"));
        org.springframework.data.domain.Page<IpdAdmission> p = ipdAdmissionRepository.findByHospitalIdAndDoctorIdAndStatus(hospitalId, doctorId, "ADMITTED", pageable);

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
                    .orElseThrow(() -> new RuntimeException("Doctor profile not found for current user"));
            Long doctorId = doctor.getId();
            admissions = ipdAdmissionRepository.findByHospitalIdAndDoctorIdAndStatus(hospitalId, doctorId, "ADMITTED");
        } else if ("RECEPTIONIST".equalsIgnoreCase(role) || "HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            admissions = ipdAdmissionRepository.findByHospitalIdAndStatusIn(hospitalId, java.util.Arrays.asList("ADMITTED", "DISCHARGE_PLANNED"));
        } else {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed");
        }

        java.util.List<com.hms.dto.IpdAdmissionSummaryDTO> result = new java.util.ArrayList<>();
        for (IpdAdmission ipd : admissions) {
            com.hms.dto.IpdAdmissionSummaryDTO dto = new com.hms.dto.IpdAdmissionSummaryDTO();
            dto.setIpdId(ipd.getId());
            dto.setIpdNumber(ipd.getIpdNumber());
            // patient
            patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
                dto.setPatientName(p.getName());
                try { dto.setAge(p.getAge()); } catch (Exception ignored) {}
                dto.setGender(p.getGender());
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

        // sort by admissionDatetime desc
        result.sort((a,b) -> b.getAdmissionDateTime().compareTo(a.getAdmissionDateTime()));
        return result;
    }

    @Transactional(readOnly = true)
    public com.hms.dto.IpdAdmissionDetailsDTO getIpdAdmissionDetails(Long ipdId) {
        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));

        com.hms.dto.IpdAdmissionDetailsDTO dto = new com.hms.dto.IpdAdmissionDetailsDTO();
        dto.setIpdNumber(ipd.getIpdNumber());
        dto.setStatus(ipd.getStatus());

        // patient
        com.hms.entity.Patient patient = patientRepository.findById(ipd.getPatientId()).orElse(null);
        if (patient != null) {
            com.hms.dto.IpdAdmissionDetailsDTO.PatientDTO p = new com.hms.dto.IpdAdmissionDetailsDTO.PatientDTO();
            p.id = patient.getId();
            p.name = patient.getName();
            try { p.age = patient.getAge(); } catch (Exception ignored) {}
            p.gender = patient.getGender();
            dto.setPatient(p);
        }

        // admission info
        com.hms.dto.IpdAdmissionDetailsDTO.AdmissionDTO adm = new com.hms.dto.IpdAdmissionDetailsDTO.AdmissionDTO();
        adm.admissionDateTime = ipd.getAdmissionDatetime();
        adm.admissionType = ipd.getAdmissionType();
        adm.primaryDiagnosis = ipd.getPrimaryDiagnosis();
        wardRepository.findById(ipd.getWardId()).ifPresent(w -> adm.ward = w.getWardName());
        bedRepository.findById(ipd.getBedId()).ifPresent(b -> adm.bed = b.getBedCode());
        doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> adm.doctor = d.getName());
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
        } catch (Exception ex) {
            // fallback empty
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
        } catch (Exception ex) {
            // fallback empty
        }
        dto.setAllPrescriptions(all);

        // billing summary (aggregate)
        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
        if (bills != null && !bills.isEmpty()) {
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            java.math.BigDecimal paid = java.math.BigDecimal.ZERO;
            for (Billing b : bills) {
                // Add BillingItems and BillingMedicines for this bill
                java.util.List<com.hms.entity.BillingItem> items = billingItemRepository.findByBillingId(b.getId());
                java.util.List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(b.getId());
                if ((items != null && !items.isEmpty()) || (medicines != null && !medicines.isEmpty())) {
                    if (items != null) {
                        for (com.hms.entity.BillingItem it : items) {
                            if (it.getAmount() != null) {
                                total = total.add(it.getAmount());
                            }
                        }
                    }
                    if (medicines != null) {
                        for (com.hms.entity.BillingMedicine med : medicines) {
                            if (med.getAmount() != null) {
                                total = total.add(med.getAmount());
                            }
                        }
                    }
                } else {
                    java.math.BigDecimal bAmt = b.getAmount() != null ? b.getAmount() : java.math.BigDecimal.ZERO;
                    total = total.add(bAmt);
                }

                // Sum all BillingPayments made for this bill
                java.util.List<com.hms.entity.BillingPayment> payments = billingPaymentRepository.findByBillingId(b.getId());
                if (payments != null) {
                    for (com.hms.entity.BillingPayment pay : payments) {
                        if (pay.getAmount() != null) {
                            paid = paid.add(pay.getAmount());
                        }
                    }
                }
            }
            com.hms.dto.IpdAdmissionDetailsDTO.BillingDTO bd = new com.hms.dto.IpdAdmissionDetailsDTO.BillingDTO();
            bd.totalAmount = total;
            bd.paidAmount = paid;
            bd.balance = total.subtract(paid);
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
        } catch (Exception ex) {
            // fallback empty
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

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("ADMITTED")) {
            throw new IllegalArgumentException("Cannot add follow-up to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        String email = securityHelper.getCurrentUserEmail();
        com.hms.entity.Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
                .orElseThrow(() -> new RuntimeException("Doctor profile not found for current user"));

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
            } catch (Exception ignored) {}
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
                if (item.getMedicineId() != null) {
                    com.hms.entity.Medicine med = medicineRepository.findById(item.getMedicineId())
                            .orElseThrow(() -> new RuntimeException("Medicine not found in active inventory: ID " + item.getMedicineId()));

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
                    } catch (Exception ignored) {}

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
            } catch (Exception ignored) {}
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

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("ADMITTED")) {
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
                if (item.getMedicineId() != null) {
                    com.hms.entity.Medicine med = medicineRepository.findById(item.getMedicineId())
                            .orElseThrow(() -> new RuntimeException("Medicine not found in active inventory: ID " + item.getMedicineId()));

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
                    } catch (Exception ignored) {}

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
                } catch (Exception ignored) {}
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

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (ipd.getStatus() == null || (!ipd.getStatus().equalsIgnoreCase("ADMITTED") && !ipd.getStatus().equalsIgnoreCase("DISCHARGE_PLANNED"))) {
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
                if (item.getStockId() != null) {
                    com.hms.entity.HospitalInventory stock = hospitalInventoryRepository.findById(item.getStockId())
                            .orElseThrow(() -> new RuntimeException("Hospital item not found in active inventory: ID " + item.getStockId()));

                    if (stock.getStockQuantity() < item.getQuantity()) {
                        throw new IllegalArgumentException("Insufficient stock for: " + stock.getName() + " (Requested: " + item.getQuantity() + ", Available: " + stock.getStockQuantity() + ")");
                    }

                    // Deduct Stock
                    int oldStock = stock.getStockQuantity();
                    stock.setStockQuantity(oldStock - item.getQuantity());
                    hospitalInventoryRepository.save(stock);

                    // Degrade relative items
                    try {
                        hospitalInventoryService.degradeRelativeItems(stock.getName(), item.getQuantity(), hospitalId);
                    } catch (Exception ignored) {}

                    // Audit Log for Stock deduction
                    try {
                        auditLogService.logAction(
                                "INVENTORY_DEDUCTED",
                                "Deducted " + item.getQuantity() + " units of " + stock.getName() + " for patient. Stock: " + oldStock + " -> " + stock.getStockQuantity(),
                                securityHelper.getCurrentUserEmail(),
                                hospitalId,
                                "INVENTORY",
                                stock.getId().toString(),
                                null
                        );
                    } catch (Exception ignored) {}

                    if (hasBillingModule && ipdBill != null) {
                        // Create BillingItem charge
                        com.hms.entity.BillingItem bi = new com.hms.entity.BillingItem();
                        bi.setBillingId(ipdBill.getId());
                        bi.setHospitalId(hospitalId);
                        bi.setDescription(stock.getName() + " (Qty: " + item.getQuantity() + ")");
                        java.math.BigDecimal unitPrice = item.getFeeAmount() != null ? item.getFeeAmount() :
                                (stock.getUnitPrice() != null ? java.math.BigDecimal.valueOf(stock.getUnitPrice()) : java.math.BigDecimal.ZERO);
                        bi.setAmount(unitPrice.multiply(java.math.BigDecimal.valueOf(item.getQuantity())));
                        billingItemRepository.save(bi);
                    }
                }
            }

            if (hasBillingModule && ipdBill != null) {
                // Recalculate bill total (incorporates consultation fee + medicines + bed fees + hospital items)
                try {
                    billingService.recalculateTotal(ipdBill.getId());
                } catch (Exception ignored) {}
            }
        }

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from administerHospitalItems", e);
        }
    }

    @Transactional
    public com.hms.entity.Prescription addIpdPrescription(Long ipdId, com.hms.dto.AddIpdPrescriptionRequest req) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can add prescriptions");
        }

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("ADMITTED")) {
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
                } catch (Exception ignored) {}
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

        // Resolve medicine name:
        // Priority 1: explicit name from request (doctor typed it manually)
        // Priority 2: look up from inventory by medicineId
        // Priority 3: fallback label using medicineId
        String medicineName = req.getMedicineName() != null && !req.getMedicineName().trim().isEmpty()
                ? req.getMedicineName().trim()
                : null;
        if (medicineName == null && req.getMedicineId() != null) {
            medicineName = medicineRepository.findById(req.getMedicineId())
                    .map(m -> m.getName())
                    .orElse(null);
        }
        if (medicineName == null) {
            medicineName = req.getMedicineId() != null ? "MED-" + req.getMedicineId() : "Unknown Medicine";
        }

        com.hms.entity.Prescription p = new com.hms.entity.Prescription();
        p.setHospitalId(hospitalId);
        p.setMedicalRecordId(latest.getId());
        p.setMedicineName(medicineName);
        p.setType(req.getType() != null ? req.getType() : "TABLET");
        p.setRoute(req.getRoute() != null ? req.getRoute() : "ORAL");
        p.setDosage(req.getDose());
        p.setFrequency(req.getFrequency());
        p.setDurationDays(req.getDurationDays());
        p.setStartDate(req.getStartDate());
        p.setStatus("ACTIVE");

        com.hms.entity.Prescription saved = prescriptionRepository.save(p);

        // Standard prescriptions are now strictly informative (no auto-deduction/billing)
        return saved;
    }

    @Transactional
    public com.hms.entity.Prescription stopPrescription(Long prescriptionId) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can stop prescriptions");
        }

        com.hms.entity.Prescription pres = prescriptionRepository.findById(prescriptionId).orElseThrow(() -> new RuntimeException("Prescription not found"));

        // Verify it belongs to an IPD by looking up medical record
        com.hms.entity.MedicalRecord mr = medicalRecordRepository.findById(pres.getMedicalRecordId()).orElseThrow(() -> new RuntimeException("Related medical record not found"));
        if (mr.getIpdAdmissionId() == null) throw new IllegalArgumentException("Prescription is not linked to an IPD admission");

        pres.setStatus("STOPPED");
        com.hms.entity.Prescription saved = prescriptionRepository.save(pres);
        return saved;
    }

    @Transactional
    public com.hms.entity.DischargeSummary planDischarge(Long ipdId, com.hms.dto.PlanDischargeRequest req) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can plan discharge");
        }

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("ADMITTED")) {
            throw new IllegalArgumentException("Can only plan discharge for ADMITTED patients");
        }

        com.hms.entity.DischargeSummary ds = new com.hms.entity.DischargeSummary();
        ds.setIpdAdmissionId(ipdId);
        ds.setFinalDiagnosis(req.getFinalDiagnosis());
        ds.setTreatmentGiven(req.getTreatmentGiven());
        ds.setDischargeNotes(req.getDischargeNotes());
        ds.setFollowUpDate(req.getFollowUpDate());
        dischargeSummaryRepository.save(ds);

        ipd.setStatus("DISCHARGE_PLANNED");
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

    @Transactional
    public IpdAdmission confirmDischarge(Long ipdId) {
        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));

        String role = securityHelper.getCurrentUserRole();
        Long hospitalId = ipd.getHospitalId();
        com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId).orElse(null);
        boolean isSolo = settings != null && "SOLO".equalsIgnoreCase(settings.getReceptionMode());

        if (!"RECEPTIONIST".equalsIgnoreCase(role) && 
            !"HOSPITAL_ADMIN".equalsIgnoreCase(role) && 
            !("DOCTOR".equalsIgnoreCase(role) && isSolo)) {
            throw new org.springframework.security.access.AccessDeniedException("Only receptionists (or doctors under Solo Doctor mode) can confirm discharge");
        }
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("DISCHARGE_PLANNED")) {
            throw new IllegalArgumentException("Discharge is not planned for this IPD");
        }

        com.hms.entity.Hospital hospital = hospitalRepository.findById(ipd.getHospitalId()).orElse(null);
        boolean hasBillingModule = hospital != null && hospital.getModules() != null && hospital.getModules().contains("BILLING");

        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);

        if (hasBillingModule) {
            // Check billing balance across bills for this IPD
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            java.math.BigDecimal paid = java.math.BigDecimal.ZERO;
            if (bills != null) {
                for (Billing b : bills) {
                    // include billing items and medicines
                    try {
                        java.util.List<com.hms.entity.BillingItem> items = billingItemRepository.findByBillingId(b.getId());
                        java.util.List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(b.getId());
                        if ((items != null && !items.isEmpty()) || (medicines != null && !medicines.isEmpty())) {
                            if (items != null) {
                                for (com.hms.entity.BillingItem it : items) {
                                    if (it.getAmount() != null) total = total.add(it.getAmount());
                                }
                            }
                            if (medicines != null) {
                                for (com.hms.entity.BillingMedicine med : medicines) {
                                    if (med.getAmount() != null) total = total.add(med.getAmount());
                                }
                            }
                        } else {
                            if (b.getAmount() != null) total = total.add(b.getAmount());
                        }
                    } catch (Exception ignored) {
                        if (b.getAmount() != null) total = total.add(b.getAmount());
                    }

                    // payments
                    try {
                        java.util.List<com.hms.entity.BillingPayment> pays = billingPaymentRepository.findByBillingId(b.getId());
                        for (com.hms.entity.BillingPayment p : pays) {
                            if (p.getAmount() != null) paid = paid.add(p.getAmount());
                        }
                    } catch (Exception ignored) {}
                }
            }

            java.math.BigDecimal balance = total.subtract(paid);
            if (balance.compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("Outstanding balance: ₹" + balance + ". Please collect payment before discharge.");
            }
        }

        // Stop all active prescriptions for this IPD
        try {
            java.util.List<com.hms.entity.Prescription> active = prescriptionRepository.findByIpdAdmissionIdAndStatus(ipdId, "ACTIVE");
            if (active != null) {
                for (com.hms.entity.Prescription pr : active) {
                    pr.setStatus("COMPLETED");
                    prescriptionRepository.save(pr);
                }
            }
        } catch (Exception ignored) {}

        // Update bed to available
        try {
            if (ipd.getBedId() != null) {
                Bed bed = bedRepository.findById(ipd.getBedId()).orElse(null);
                if (bed != null) {
                    bed.setStatus("available");
                    bed.setCurrentIpdAdmissionId(null);
                    bedRepository.save(bed);
                }
            }
        } catch (Exception ignored) {}

        // Finalize billing records for this IPD
        if (hasBillingModule && bills != null) {
            for (Billing b : bills) {
                b.setPaymentStatus("CLOSED");
                billingRepository.save(b);
            }
        }

        // Update IPD status and discharge datetime
        ipd.setStatus("DISCHARGED");
        ipd.setDischargeDatetime(LocalDateTime.now());
        ipdAdmissionRepository.save(ipd);

        // Release the active bed history record
        try {
            java.util.Optional<com.hms.entity.IpdBedHistory> activeHistOpt = ipdBedHistoryRepository
                    .findByIpdAdmissionIdAndReleasedAtIsNull(ipd.getId());
            if (activeHistOpt.isPresent()) {
                com.hms.entity.IpdBedHistory activeHist = activeHistOpt.get();
                activeHist.setReleasedAt(LocalDateTime.now());
                ipdBedHistoryRepository.save(activeHist);
            }
        } catch (Exception e) {
            logger.warn("Failed to close bed history logs in confirmDischarge", e);
        }

        // Audit log
        try {
            auditLogService.logAction(
                    "IPD_DISCHARGED",
                    "Confirmed discharge for IPD Case: " + ipd.getIpdNumber() + ".",
                    securityHelper.getCurrentUserEmail(),
                    ipd.getHospitalId(),
                    "IPD",
                    ipd.getId().toString(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for IPD discharge confirmation", e);
        }

        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from confirmDischarge", e);
        }

        return ipd;
    }

    @org.springframework.transaction.annotation.Transactional
    public IpdAdmission changeBed(Long ipdId, Long newBedId) {
        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD not found"));

        String role = securityHelper.getCurrentUserRole();
        Long hospitalId = ipd.getHospitalId();
        com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId).orElse(null);
        boolean isSolo = settings != null && "SOLO".equalsIgnoreCase(settings.getReceptionMode());

        if (!"RECEPTIONIST".equalsIgnoreCase(role) && 
            !"HOSPITAL_ADMIN".equalsIgnoreCase(role) && 
            !("DOCTOR".equalsIgnoreCase(role) && isSolo)) {
            throw new org.springframework.security.access.AccessDeniedException("Only receptionists (or doctors under Solo Doctor mode) can change beds");
        }
        
        if (!"ADMITTED".equalsIgnoreCase(ipd.getStatus()) && !"DISCHARGE_PLANNED".equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException("Bed change allowed only for active admissions");
        }

        Bed newBed = bedRepository.findById(newBedId).orElseThrow(() -> new RuntimeException("New bed not found"));
        if ("occupied".equalsIgnoreCase(newBed.getStatus()) && !newBedId.equals(ipd.getBedId())) {
             throw new IllegalArgumentException("Requested bed is already occupied");
        }

        Long oldBedId = ipd.getBedId();
        String oldBedCode = "Unknown Bed";
        String oldWardName = "Unknown Ward";
        if (oldBedId != null) {
            Bed oldBed = bedRepository.findById(oldBedId).orElse(null);
            if (oldBed != null) {
                oldBedCode = oldBed.getBedCode();
                com.hms.entity.Ward oldW = wardRepository.findById(oldBed.getWardId()).orElse(null);
                if (oldW != null) {
                    oldWardName = oldW.getWardName();
                }
                if (!oldBedId.equals(newBedId)) {
                    oldBed.setStatus("available");
                    oldBed.setCurrentIpdAdmissionId(null);
                    bedRepository.save(oldBed);
                }
            }
        }

        newBed.setStatus("occupied");
        newBed.setCurrentIpdAdmissionId(ipd.getId());
        bedRepository.save(newBed);

        String newBedCode = newBed.getBedCode();
        String newWardName = "Unknown Ward";
        com.hms.entity.Ward newWardEntity = wardRepository.findById(newBed.getWardId()).orElse(null);
        if (newWardEntity != null) {
            newWardName = newWardEntity.getWardName();
        }

        // Calculate price difference if new ward is more expensive
        try {
            java.math.BigDecimal oldRate = java.math.BigDecimal.ZERO;
            if (ipd.getWardId() != null) {
                com.hms.entity.Ward oldW = wardRepository.findById(ipd.getWardId()).orElse(null);
                if (oldW != null && oldW.getBedPrice() != null) oldRate = oldW.getBedPrice();
            }
            
            java.math.BigDecimal newRate = java.math.BigDecimal.ZERO;
            com.hms.entity.Ward newW = wardRepository.findById(newBed.getWardId()).orElse(null);
            if (newW != null && newW.getBedPrice() != null) newRate = newW.getBedPrice();

            java.math.BigDecimal diff = newRate.subtract(oldRate);
            // If positive difference (upgrade), add to bill immediately
            if (diff.compareTo(java.math.BigDecimal.ZERO) > 0) {
                java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipd.getId());
                Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
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
        } catch (Exception ex) {
            // Log but don't hard crash simple bed transfer on side-billing logic
        }

        ipd.setBedId(newBed.getBedId());
        ipd.setWardId(newBed.getWardId());
        
        IpdAdmission saved = ipdAdmissionRepository.save(ipd);

        // Update IpdBedHistory
        try {
            java.util.Optional<com.hms.entity.IpdBedHistory> activeHistOpt = ipdBedHistoryRepository
                    .findByIpdAdmissionIdAndReleasedAtIsNull(ipd.getId());
            if (activeHistOpt.isPresent()) {
                com.hms.entity.IpdBedHistory activeHist = activeHistOpt.get();
                activeHist.setReleasedAt(LocalDateTime.now());
                ipdBedHistoryRepository.save(activeHist);
            }
            
            com.hms.entity.IpdBedHistory newHist = new com.hms.entity.IpdBedHistory();
            newHist.setIpdAdmissionId(ipd.getId());
            newHist.setWardId(newBed.getWardId());
            newHist.setBedId(newBed.getBedId());
            newHist.setAssignedAt(LocalDateTime.now());
            ipdBedHistoryRepository.save(newHist);
        } catch (Exception e) {
            logger.warn("Failed to update bed history logs in changeBed", e);
        }

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

