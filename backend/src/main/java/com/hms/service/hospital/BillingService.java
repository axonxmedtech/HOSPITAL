package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.Hospital;
import com.hms.entity.Appointment;
import com.hms.event.ConsultationCompletedEvent;
import com.hms.repository.BillingRepository;
import com.hms.repository.HospitalRepository;
import com.hms.security.SecurityContextHelper;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

@Service
public class BillingService {

    private static final Logger logger = LoggerFactory.getLogger(BillingService.class);

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private com.hms.repository.BillingItemRepository billingItemRepository;

    @Autowired
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;

    @Autowired
    private com.hms.repository.OpdRepository opdRepository;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private com.hms.repository.WardRepository wardRepository;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * Auto-generate a bill for a completed appointment
     */
    @Transactional
    public void autoGenerateOpdBill(Appointment appointment) {
        if (billingRepository.existsByAppointmentId(appointment.getId())) {
            logger.warn("Skipping bill auto-generation: Bill already exists for appointment {}", appointment.getId());
            return;
        }

        Hospital hospital = hospitalRepository.findById(appointment.getHospitalId())
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        // Check module access
        if (hospital.getModules() == null || !hospital.getModules().contains("BILLING")) {
            logger.warn("Skipping bill generation for appointment {}. BILLING module disabled.", appointment.getId());
            return;
        }

        // Only generate if consultation fee is set and > 0
        BigDecimal fee = hospital.getConsultationFee();
        if (fee != null && fee.compareTo(BigDecimal.ZERO) > 0) {
            Billing bill = new Billing();
            bill.setHospitalId(hospital.getId());
            bill.setPatientId(appointment.getPatientId());
            bill.setDoctorId(appointment.getDoctorId());
            bill.setAppointmentId(appointment.getId());
            bill.setAmount(fee);
            bill.setPaymentStatus("PENDING"); // Default to pending until collected
            bill.setDescription("Consultation Fee - Auto Generated");

            // Attempt to resolve related opdId from MedicalRecord
            try {
                java.util.Optional<com.hms.entity.MedicalRecord> recordOpt = medicalRecordRepository.findByAppointmentId(appointment.getId());
                recordOpt.ifPresent(r -> bill.setOpdId(r.getOpdId()));
            } catch (Exception e) {
                logger.warn("Could not resolve related opdId for appointment {}", appointment.getId(), e);
            }

            Billing saved = billingRepository.save(bill);

            // Create a billing item for breakdown consistency in the UI
            try {
                com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
                item.setBillingId(saved.getId());
                item.setHospitalId(hospital.getId());
                item.setDescription("Consultation Fee");
                item.setAmount(fee);
                billingItemRepository.save(item);
            } catch (Exception e) {
                logger.warn("Failed to create Consultation Fee billing item for auto-bill {}", saved.getId(), e);
            }

            try {
                eventPublisher.publishEvent(new ConsultationCompletedEvent(
                        hospital.getId(), appointment.getPatientId(), appointment.getId()));
            } catch (Exception e) {
                logger.warn("Failed to publish ConsultationCompletedEvent", e);
            }

            logger.info("Auto-generated bill for appointment: {} with amount: {}", appointment.getId(), fee);
        } else {
            logger.warn("Skipped bill generation for appointment {}. Fee is null or zero. Hospital Fee: {}",
                    appointment.getId(), fee);
        }
    }

    /**
     * Get all bills for current hospital with optional search
     */
    public Page<Billing> getAllBills(String search, String status, Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (search != null && !search.isEmpty()) {
            Page<Billing> p = billingRepository.searchBillings(hospitalId, search, pageable);
            if (status != null && !status.isEmpty()) {
                java.util.List<Billing> filtered = new java.util.ArrayList<>();
                for (Billing b : p.getContent()) if (status.equalsIgnoreCase(b.getPaymentStatus())) filtered.add(b);
                return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
            }
            return p;
        }

        if (status != null && !status.isEmpty()) {
            return billingRepository.findByHospitalIdAndPaymentStatus(hospitalId, status, pageable);
        }

        return billingRepository.findByHospitalId(hospitalId, pageable);
    }

    /**
     * Update payment status
     */
    public Billing updateStatus(Long id, String status, String paymentMethod, String paymentReference) {
        if (!"PENDING".equalsIgnoreCase(status) && 
            !"PARTIAL".equalsIgnoreCase(status) && 
            !"PAID".equalsIgnoreCase(status) && 
            !"CLOSED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Invalid billing status value: " + status);
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        validateBillingAccess(hospitalId);

        Billing bill = billingRepository.findById(id)
                .filter(b -> b.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        bill.setPaymentStatus(status);
        if ("PAID".equalsIgnoreCase(status)) {
            try {
                String userEmail = securityHelper.getCurrentUserEmail();
                String userRole = securityHelper.getCurrentUserRole();
                bill.setMarkedPaidBy(userRole + " (" + userEmail + ")");
            } catch (Exception ignored) {}
        }
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            bill.setPaymentMethod(paymentMethod);
        }
        if (paymentReference != null && !paymentReference.isEmpty()) {
            bill.setPaymentReference(paymentReference);
        }

        Billing saved = billingRepository.save(bill);

        // Create audit log
        try {
            auditLogService.logAction(
                    "BILLING_STATUS_CHANGED",
                    "Bill " + saved.getCustomId() + " status updated to " + status + ".",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "BILLING",
                    saved.getPublicId(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for billing status update", e);
        }

        // If the bill is marked as PAID, ensure that corresponding BillingPayment is recorded so both sections are synchronized
        if ("PAID".equalsIgnoreCase(status)) {
            try {
                java.util.List<com.hms.entity.BillingItem> items = billingItemRepository.findByBillingId(saved.getId());
                java.util.List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(saved.getId());
                java.math.BigDecimal totalAmt = java.math.BigDecimal.ZERO;
                if ((items != null && !items.isEmpty()) || (medicines != null && !medicines.isEmpty())) {
                    if (items != null) {
                        for (com.hms.entity.BillingItem it : items) {
                            if (it.getAmount() != null) {
                                totalAmt = totalAmt.add(it.getAmount());
                            }
                        }
                    }
                    if (medicines != null) {
                        for (com.hms.entity.BillingMedicine med : medicines) {
                            if (med.getAmount() != null) {
                                totalAmt = totalAmt.add(med.getAmount());
                            }
                        }
                    }
                } else {
                    totalAmt = saved.getAmount() != null ? saved.getAmount() : java.math.BigDecimal.ZERO;
                    if (totalAmt.compareTo(java.math.BigDecimal.ZERO) == 0 && "IPD".equalsIgnoreCase(saved.getBillingType())) {
                        if (saved.getIpdAdmissionId() != null) {
                            com.hms.entity.IpdAdmission ipd = ipdAdmissionRepository.findById(saved.getIpdAdmissionId()).orElse(null);
                            if (ipd != null && ipd.getWardId() != null) {
                                com.hms.entity.Ward ward = wardRepository.findById(ipd.getWardId()).orElse(null);
                                if (ward != null && ward.getBedPrice() != null) {
                                    totalAmt = totalAmt.add(ward.getBedPrice());
                                }
                            }
                        }
                    }
                }

                java.util.List<com.hms.entity.BillingPayment> payments = billingPaymentRepository.findByBillingId(saved.getId());
                java.math.BigDecimal paidAmt = java.math.BigDecimal.ZERO;
                for (com.hms.entity.BillingPayment p : payments) {
                    if (p.getAmount() != null) {
                        paidAmt = paidAmt.add(p.getAmount());
                    }
                }

                if (paidAmt.compareTo(totalAmt) < 0) {
                    java.math.BigDecimal remaining = totalAmt.subtract(paidAmt);
                    com.hms.entity.BillingPayment payment = new com.hms.entity.BillingPayment();
                    payment.setBillingId(saved.getId());
                    payment.setHospitalId(saved.getHospitalId());
                    payment.setAmount(remaining);
                    payment.setMode(paymentMethod != null && !paymentMethod.isEmpty() ? paymentMethod : "CASH");
                    payment.setReference(paymentReference);
                    billingPaymentRepository.save(payment);
                }
            } catch (Exception e) {
                logger.warn("Failed to auto-create BillingPayment for PAID status update", e);
            }
        }

        // If this bill is linked to an OPD and payment marked PAID, mark OPD as COMPLETED
        try {
            if (saved.getOpdId() != null && status != null && status.equalsIgnoreCase("PAID")) {
                java.util.Optional<com.hms.entity.Opd> opdOpt = opdRepository.findById(saved.getOpdId());
                if (opdOpt.isPresent()) {
                    com.hms.entity.Opd opd = opdOpt.get();
                    opd.setStatus(com.hms.entity.Opd.Status.COMPLETED);
                    opdRepository.save(opd);
                    // Audit log
                    try {
                        auditLogService.logAction(
                                "OPD_STATUS_CHANGED",
                                "OPD " + (opd.getCaseId() != null ? opd.getCaseId() : opd.getId()) + " set to COMPLETED",
                                securityHelper.getCurrentUserEmail(),
                                hospitalId,
                                "OPD",
                                opd.getId().toString(),
                                null);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to update OPD status after bill payment", e);
        }

        try {
            webSocketHandler.broadcast(saved.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from updateStatus", e);
        }

        return saved;
    }

    private void validateBillingAccess(Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));
        if (hospital.getModules() == null || !hospital.getModules().contains("BILLING")) {
            throw new IllegalArgumentException("BILLING module is disabled for your hospital.");
        }
    }

    /**
     * Create a consultation bill for direct patient consultations (without
     * appointment)
     * 
     * @param patientId      Patient ID
     * @param doctorId       Doctor ID
     * @param consultationId Medical Record ID
     */
    public com.hms.entity.Billing createConsultationBill(Long patientId, Long doctorId, Long consultationId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        // Check module access
        if (hospital.getModules() == null || !hospital.getModules().contains("BILLING")) {
            logger.warn("Skipping bill generation for consultation {}. BILLING module disabled.", consultationId);
            return null;
        }

        // Use consultation fee from hospital settings or default
        BigDecimal fee = hospital.getConsultationFee();
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            fee = new BigDecimal("500.00"); // Default consultation fee
        }

        Billing bill = new Billing();
        bill.setHospitalId(hospitalId);
        bill.setPatientId(patientId);
        bill.setDoctorId(doctorId);
        bill.setAmount(fee);
        bill.setDescription("Consultation Fee");
        bill.setPaymentStatus("PENDING");
        bill.setAppointmentId(null); // No appointment for direct consultations

        Billing saved = billingRepository.save(bill);

        // Create single billing item
        com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
        item.setBillingId(saved.getId());
        item.setHospitalId(hospitalId);
        item.setDescription("Consultation Fee");
        item.setAmount(fee);
        billingItemRepository.save(item);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after consultation bill creation", e);
        }

        logger.info("Consultation bill generated for patient {} with amount {}", patientId, fee);
        return saved;
    }

    /**
     * Create a combined OPD bill (case paper fee + consultation fee) for an OPD case
     * @param opdId OPD id
     * @param patientId Patient id
     * @param doctorId Doctor id
     */
    public com.hms.entity.Billing createOpdBill(Long opdId, Long patientId, Long doctorId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        // Check module access
        if (hospital.getModules() == null || !hospital.getModules().contains("BILLING")) {
            logger.warn("Skipping OPD bill generation for OPD {}. BILLING module disabled.", opdId);
            return null;
        }

        // Fees: prefer hospital-configured values, fallback to previous defaults
        java.math.BigDecimal caseFee = hospital.getCasePaperFee();
        if (caseFee == null || caseFee.compareTo(java.math.BigDecimal.ZERO) < 0) {
            caseFee = new java.math.BigDecimal("100.00");
        }

        java.math.BigDecimal consultFee = hospital.getConsultationFee();
        if (consultFee == null || consultFee.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            consultFee = new java.math.BigDecimal("500.00");
        }

        java.math.BigDecimal total = caseFee.add(consultFee);

        Billing bill = new Billing();
        bill.setHospitalId(hospitalId);
        bill.setPatientId(patientId);
        bill.setDoctorId(doctorId != null ? doctorId : 0L);
        bill.setOpdId(opdId);
        bill.setAmount(total);
        bill.setDescription("OPD - Case Paper + Consultation");
        bill.setPaymentStatus("PENDING");
        bill.setAppointmentId(null);

        Billing saved = billingRepository.save(bill);

        // Create billing items for breakdown
        com.hms.entity.BillingItem item1 = new com.hms.entity.BillingItem();
        item1.setBillingId(saved.getId());
        item1.setHospitalId(hospitalId);
        item1.setDescription("Case Paper Fee");
        item1.setAmount(caseFee);
        billingItemRepository.save(item1);

        com.hms.entity.BillingItem item2 = new com.hms.entity.BillingItem();
        item2.setBillingId(saved.getId());
        item2.setHospitalId(hospitalId);
        item2.setDescription("Consultation Fee");
        item2.setAmount(consultFee);
        billingItemRepository.save(item2);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after OPD bill creation", e);
        }

        logger.info("OPD bill generated for OPD {} patient {} with amount {}", opdId, patientId, total);
        return saved;
    }

    /**
     * Centralized utility to recalculate top-level bill amount based on active items.
     * Prevents IPD denormalized totals divergence.
     */
    public void recalculateTotal(Long billingId) {
        Billing bill = billingRepository.findById(billingId).orElse(null);
        if (bill != null) {
            java.util.List<com.hms.entity.BillingItem> items = billingItemRepository.findByBillingId(billingId);
            BigDecimal total = BigDecimal.ZERO;
            for (com.hms.entity.BillingItem item : items) {
                if (item.getAmount() != null) {
                    total = total.add(item.getAmount());
                }
            }
            java.util.List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(billingId);
            for (com.hms.entity.BillingMedicine med : medicines) {
                if (med.getAmount() != null) {
                    total = total.add(med.getAmount());
                }
            }
            bill.setAmount(total);
            billingRepository.save(bill);
            logger.info("Recalculated total for bill {}: {}", billingId, total);
        }
    }

    @Transactional
    public void postIpdCharge(Long ipdAdmissionId, String description, BigDecimal amount) {
        Long hospitalId = null;
        try {
            hospitalId = securityHelper.getCurrentHospitalId();
        } catch (Exception ignored) {}

        if (hospitalId == null) {
            com.hms.entity.IpdAdmission admission = ipdAdmissionRepository.findById(ipdAdmissionId).orElse(null);
            if (admission != null) {
                hospitalId = admission.getHospitalId();
            }
        }
        if (hospitalId == null) return;

        Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        if (hospital == null || hospital.getModules() == null || !hospital.getModules().contains("BILLING")) {
            return;
        }

        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdAdmissionId);
        Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
        if (bill == null) {
            com.hms.entity.IpdAdmission admission = ipdAdmissionRepository.findById(ipdAdmissionId)
                    .orElseThrow(() -> new RuntimeException("IPD Admission not found: " + ipdAdmissionId));
            bill = new Billing();
            bill.setHospitalId(hospitalId);
            bill.setPatientId(admission.getPatientId());
            bill.setDoctorId(admission.getDoctorId());
            bill.setIpdAdmissionId(ipdAdmissionId);
            bill.setBillingType("IPD");
            bill.setAmount(BigDecimal.ZERO);
            bill.setPaymentStatus("PENDING");
            bill.setDescription("IPD Bill - Admission #" + admission.getIpdNumber());
            bill = billingRepository.save(bill);
        }

        com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
        item.setBillingId(bill.getId());
        item.setHospitalId(hospitalId);
        item.setDescription(description);
        item.setAmount(amount);
        billingItemRepository.save(item);

        recalculateTotal(bill.getId());
        
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after direct IPD charge posting", e);
        }
    }
}

