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
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.QueueEntryRepository queueEntryRepository;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Transactional
    public IpdAdmission admitFromOpd(Long opdId, Long wardId, Long bedId, String admissionType, String primaryDiagnosis) {
        // Load OPD
        Opd opd = opdRepository.findById(opdId).orElseThrow(() -> new RuntimeException("OPD not found"));

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new RuntimeException("Hospital ID not found in security context");

        // Validate bed availability
        Bed bed = bedRepository.findById(bedId).orElseThrow(() -> new RuntimeException("Bed not found"));
        if (!bed.getStatus().equalsIgnoreCase("available")) {
            throw new RuntimeException("Bed is not available");
        }

        // Create IPD admission
        IpdAdmission ipd = new IpdAdmission();
        ipd.setIpdNumber("IPD" + System.currentTimeMillis());
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

        logger.info("Created IPD admission {} for OPD {}", saved.getIpdNumber(), opdId);

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
        if (hospitalId == null) throw new RuntimeException("Hospital ID not found in context");

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
        if (hospitalId == null) throw new RuntimeException("Hospital ID not found in context");

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
        if (hospitalId == null) throw new RuntimeException("Hospital ID not found in context");

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
            throw new RuntimeException("Cannot add follow-up to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new RuntimeException("Hospital ID not found in context");

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

        try {
            com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
            if (hospital != null && hospital.getConsultationFee() != null && hospital.getConsultationFee().compareTo(BigDecimal.ZERO) > 0) {
                java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
                Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
                if (bill != null) {
                    com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
                    item.setBillingId(bill.getId());
                    item.setHospitalId(bill.getHospitalId());
                    item.setDescription("Doctor Consultation Fee (IPD Follow-up)");
                    item.setAmount(hospital.getConsultationFee());
                    billingItemRepository.save(item);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to auto-add consultation fee billing item for follow-up", e);
        }

        // --- Process Administered Items (Stock Deductions & Billing) ---
        if (administeredItems != null && !administeredItems.isEmpty()) {
            java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
            Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
            if (bill != null) {
                for (com.hms.dto.ConsultationRequest.AdministeredItem item : administeredItems) {
                    if (item.getMedicineId() != null) {
                        com.hms.entity.Medicine med = medicineRepository.findById(item.getMedicineId())
                                .orElseThrow(() -> new RuntimeException("Medicine not found in active inventory: ID " + item.getMedicineId()));

                        if (med.getStockQuantity() < item.getQuantity()) {
                            throw new RuntimeException("Insufficient stock for: " + med.getName() + " (Requested: " + item.getQuantity() + ", Available: " + med.getStockQuantity() + ")");
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

                        // Create BillingMedicine charge
                        com.hms.entity.BillingMedicine bm = new com.hms.entity.BillingMedicine();
                        bm.setBillingId(bill.getId());
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
        try {
            java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
            Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
            if (bill != null) {
                billingService.recalculateTotal(bill.getId());
            }
        } catch (Exception ignored) {}

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from addIpdFollowup", e);
        }

        return saved;
    }

    @Transactional
    public com.hms.entity.Prescription addIpdPrescription(Long ipdId, com.hms.dto.AddIpdPrescriptionRequest req) {
        String role = securityHelper.getCurrentUserRole();
        if (!"DOCTOR".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can add prescriptions");
        }

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("ADMITTED")) {
            throw new RuntimeException("Cannot add prescription to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new RuntimeException("Hospital ID not found in context");

        // Resolve latest medical record for this IPD
        java.util.List<com.hms.entity.MedicalRecord> mrs = medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(ipdId);
        if (mrs == null || mrs.isEmpty()) {
            throw new RuntimeException("No medical record found for this IPD. Create a follow-up first.");
        }
        com.hms.entity.MedicalRecord latest = mrs.get(0);

        // Resolve medicine name if medicineId provided
        final String[] medicineNameArray = {null};
        if (req.getMedicineId() != null) {
            medicineRepository.findById(req.getMedicineId()).ifPresent(m -> medicineNameArray[0] = m.getName());
        }
        String medicineName = medicineNameArray[0];
        if (medicineName == null) medicineName = "MED-" + (req.getMedicineId() == null ? java.util.UUID.randomUUID().toString() : req.getMedicineId());

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
        if (mr.getIpdAdmissionId() == null) throw new RuntimeException("Prescription is not linked to an IPD admission");

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
            throw new RuntimeException("Can only plan discharge for ADMITTED patients");
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

        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from planDischarge", e);
        }

        return ds;
    }

    @Transactional
    public IpdAdmission confirmDischarge(Long ipdId) {
        String role = securityHelper.getCurrentUserRole();
        if (!"RECEPTIONIST".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only receptionists can confirm discharge");
        }

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD admission not found"));
        if (ipd.getStatus() == null || !ipd.getStatus().equalsIgnoreCase("DISCHARGE_PLANNED")) {
            throw new RuntimeException("Discharge is not planned for this IPD");
        }

        // Check billing balance across bills for this IPD
        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
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
            throw new RuntimeException("Outstanding balance: ₹" + balance + ". Please collect payment before discharge.");
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
        if (bills != null) {
            for (Billing b : bills) {
                b.setPaymentStatus("CLOSED");
                billingRepository.save(b);
            }
        }

        // Update IPD status and discharge datetime
        ipd.setStatus("DISCHARGED");
        ipd.setDischargeDatetime(LocalDateTime.now());
        ipdAdmissionRepository.save(ipd);

        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from confirmDischarge", e);
        }

        return ipd;
    }

    @org.springframework.transaction.annotation.Transactional
    public IpdAdmission changeBed(Long ipdId, Long newBedId) {
        String role = securityHelper.getCurrentUserRole();
        if (!"RECEPTIONIST".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only receptionists can change beds");
        }

        IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElseThrow(() -> new RuntimeException("IPD not found"));
        
        if (!"ADMITTED".equalsIgnoreCase(ipd.getStatus()) && !"DISCHARGE_PLANNED".equalsIgnoreCase(ipd.getStatus())) {
            throw new RuntimeException("Bed change allowed only for active admissions");
        }

        Bed newBed = bedRepository.findById(newBedId).orElseThrow(() -> new RuntimeException("New bed not found"));
        if ("occupied".equalsIgnoreCase(newBed.getStatus()) && !newBedId.equals(ipd.getBedId())) {
             throw new RuntimeException("Requested bed is already occupied");
        }

        Long oldBedId = ipd.getBedId();
        if (oldBedId != null && !oldBedId.equals(newBedId)) {
            Bed oldBed = bedRepository.findById(oldBedId).orElse(null);
            if (oldBed != null) {
                oldBed.setStatus("available");
                oldBed.setCurrentIpdAdmissionId(null);
                bedRepository.save(oldBed);
            }
        }

        newBed.setStatus("occupied");
        newBed.setCurrentIpdAdmissionId(ipd.getId());
        bedRepository.save(newBed);

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
        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from changeBed", e);
        }
        return saved;
    }
}
