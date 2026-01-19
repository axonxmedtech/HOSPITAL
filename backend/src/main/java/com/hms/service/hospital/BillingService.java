package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.Hospital;
import com.hms.entity.Appointment;
import com.hms.repository.BillingRepository;
import com.hms.repository.HospitalRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

@Service
public class BillingService {

    private static final Logger logger = LoggerFactory.getLogger(BillingService.class);

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    /**
     * Auto-generate a bill for a completed appointment
     */
    public void autoGenerateOpdBill(Appointment appointment) {
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

            billingRepository.save(bill);
            logger.info("Auto-generated bill for appointment: {} with amount: {}", appointment.getId(), fee);
        } else {
            logger.warn("Skipped bill generation for appointment {}. Fee is null or zero. Hospital Fee: {}",
                    appointment.getId(), fee);
        }
    }

    /**
     * Get all bills for current hospital
     */
    public Page<Billing> getAllBills(Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return billingRepository.findByHospitalId(hospitalId, pageable);
    }

    /**
     * Update payment status
     */
    public Billing updateStatus(Long id, String status, String paymentMethod) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        validateBillingAccess(hospitalId);

        Billing bill = billingRepository.findById(id)
                .filter(b -> b.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        bill.setPaymentStatus(status);
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            bill.setPaymentMethod(paymentMethod);
        }

        Billing saved = billingRepository.save(bill);

        // Audit log?
        return saved;
    }

    private void validateBillingAccess(Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));
        if (hospital.getModules() == null || !hospital.getModules().contains("BILLING")) {
            throw new RuntimeException("BILLING module is disabled for your hospital.");
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
    public void createConsultationBill(Long patientId, Long doctorId, Long consultationId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        // Check module access
        if (hospital.getModules() == null || !hospital.getModules().contains("BILLING")) {
            logger.warn("Skipping bill generation for consultation {}. BILLING module disabled.", consultationId);
            return;
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

        billingRepository.save(bill);
        logger.info("Consultation bill generated for patient {} with amount {}", patientId, fee);
    }
}
