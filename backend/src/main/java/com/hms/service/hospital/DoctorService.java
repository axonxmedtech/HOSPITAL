package com.hms.service.hospital;

import com.hms.entity.Doctor;
import com.hms.entity.User;
import com.hms.repository.DoctorRepository;
import com.hms.repository.UserRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * DoctorService - Service for managing doctors
 * 
 * This service handles doctor-related operations:
 * - Adding new doctors (creates both Doctor record and User account)
 * - Listing doctors for a hospital
 * - Getting doctor details
 * 
 * All operations are automatically filtered by hospital_id for multi-tenant
 * isolation.
 * Only Hospital Admin can add doctors.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class DoctorService {

    private static final Logger logger = LoggerFactory.getLogger(DoctorService.class);

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    /**
     * Add a new doctor
     * Creates both Doctor record and User account for login
     * Automatically sets hospital_id from the authenticated user's context
     * 
     * @param doctor   Doctor entity to create
     * @param password Password for doctor's user account
     * @return Created Doctor entity
     */
    @Transactional
    public Doctor addDoctor(Doctor doctor, String password) {
        // Get hospital_id from security context (multi-tenant isolation)
        Long hospitalId = securityHelper.getCurrentHospitalId();

        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        // Check if doctor email already exists in this hospital
        if (doctorRepository.findByEmailAndHospitalId(doctor.getEmail(), hospitalId).isPresent()) {
            throw new RuntimeException("Doctor with this email already exists in your hospital");
        }

        // Check if email is already used as a user account
        if (userRepository.existsByEmail(doctor.getEmail())) {
            throw new RuntimeException("Email already exists in the system");
        }

        // Set hospital_id to ensure multi-tenant isolation
        doctor.setHospitalId(hospitalId);

        // Save doctor record
        doctor = doctorRepository.save(doctor);

        // Create user account for doctor login
        User doctorUser = new User();
        doctorUser.setEmail(doctor.getEmail());
        doctorUser.setPassword(passwordEncoder.encode(password));
        doctorUser.setName(doctor.getName());
        doctorUser.setRole("DOCTOR");
        doctorUser.setHospitalId(hospitalId);
        userRepository.save(doctorUser);

        logger.info("Hospital {} created new doctor: {}", hospitalId, doctor.getEmail());

        // Log Audit
        try {
            auditLogService.logAction(
                    "DOCTOR_CREATED",
                    "Doctor " + doctor.getName() + " was added.",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "DOCTOR",
                    doctor.getPublicId() != null ? doctor.getPublicId() : doctor.getId().toString(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for doctor creation", e);
        }

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after doctor creation", e);
        }

        return doctor;
    }

    /**
     * Get all active doctors for the current hospital
     * Automatically filters by hospital_id from security context
     * 
     * @return List of active doctors for the hospital
     */
    /**
     * Get all active doctors for the current hospital with pagination
     * Automatically filters by hospital_id from security context
     * 
     * @return Page of active doctors for the hospital
     */
    public org.springframework.data.domain.Page<Doctor> getAllDoctors(
            org.springframework.data.domain.Pageable pageable) {
        // Get hospital_id from security context (multi-tenant isolation)
        Long hospitalId = securityHelper.getCurrentHospitalId();

        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        // Return only active doctors belonging to this hospital
        return doctorRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospitalId, pageable);
    }

    /**
     * Search active doctors by name or specialization
     * 
     * @param query Search term
     * @return List of matching active doctors
     */
    public List<Doctor> searchDoctors(String query) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        if (query == null || query.trim().isEmpty()) {
            // Return empty list or basic list, but better to use pagination if possible
            // For now, returning top 100 or similarly limited list to avoid crashing
            return getAllDoctors(org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
        }

        return doctorRepository
                .findByHospitalIdAndIsActiveTrueAndNameContainingIgnoreCaseOrHospitalIdAndIsActiveTrueAndSpecializationContainingIgnoreCase(
                        hospitalId, query, hospitalId, query);
    }

    /**
     * Update an active doctor's details (Name, Spec, Phone)
     * Email/Password updates are restricted for safety
     * 
     * @param id          Doctor ID
     * @param updatedData New doctor data
     * @return Updated Doctor entity
     */
    /**
     * Update an active doctor's details (Name, Spec, Phone)
     * Email/Password updates are restricted for safety
     * 
     * @param publicId    Doctor Public ID
     * @param updatedData New doctor data
     * @return Updated Doctor entity
     */
    public Doctor updateDoctor(String publicId, Doctor updatedData) {
        // Ensure doctor exists and belongs to this hospital
        Doctor existingDoctor = getDoctorByPublicId(publicId);

        existingDoctor.setName(updatedData.getName());
        existingDoctor.setSpecialization(updatedData.getSpecialization());
        existingDoctor.setPhone(updatedData.getPhone());
        // Note: Email and Password updates are not allowed here to prevent auth
        // potential issues

        Doctor saved = doctorRepository.save(existingDoctor);

        // Broadcast real-time refresh
        try {
            Long hid = securityHelper.getCurrentHospitalId();
            if (hid != null) webSocketHandler.broadcast(hid, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after doctor update", e);
        }

        return saved;
    }

    /**
     * Get an active doctor by ID
     * Ensures the doctor belongs to the current hospital (multi-tenant isolation)
     * 
     * @param id Doctor ID
     * @return Doctor entity
     * @throws RuntimeException if doctor not found, inactive, or doesn't belong to
     *                          the
     *                          hospital
     */
    /**
     * Get an active doctor by Public ID
     * Ensures the doctor belongs to the current hospital (multi-tenant isolation)
     * 
     * @param publicId Doctor Public ID
     * @return Doctor entity
     * @throws RuntimeException if doctor not found, inactive, or doesn't belong to
     *                          the
     *                          hospital
     */
    public Doctor getDoctorByPublicId(String publicId) {
        // Get hospital_id from security context (multi-tenant isolation)
        Long hospitalId = securityHelper.getCurrentHospitalId();

        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        // Find doctor only if it belongs to this hospital and is active
        return doctorRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(publicId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
    }

    /**
     * Soft delete a doctor and their user account
     * 
     * @param id Doctor ID
     */
    @Transactional
    public void deleteDoctor(String publicId, String reason) {
        // Check if doctor exists and belongs to this hospital
        Doctor doctor = getDoctorByPublicId(publicId);

        // Soft delete doctor
        doctor.setIsActive(false);
        doctorRepository.save(doctor);

        // Soft delete associated user account to prevent login
        Optional<User> userOpt = userRepository.findByEmail(doctor.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setIsActive(false);
            userRepository.save(user);
        }

        // Log the deletion
        logger.info("Doctor soft-deleted: ID={}, Name={}, Email={}. Reason: {}", publicId, doctor.getName(),
                doctor.getEmail(), reason);

        auditLogService.logAction(
                "DOCTOR_DELETED",
                "Doctor " + doctor.getName() + " was deleted. Reason: "
                        + (reason != null ? reason : "No reason provided"),
                securityHelper.getCurrentUserEmail(),
                doctor.getHospitalId(),
                "DOCTOR",
                publicId,
                reason);

        // Broadcast real-time refresh
        try {
            webSocketHandler.broadcast(doctor.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after doctor deletion", e);
        }
    }

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private com.hms.repository.PrescriptionRepository prescriptionRepository;

    @Autowired
    private com.hms.repository.AppointmentRepository appointmentRepository;

    @Autowired
    private com.hms.repository.PatientRepository patientRepository;

    @Autowired
    private com.hms.service.hospital.BillingService billingService;

    // auditLogService is already injected at class level, removing duplicate

    @Autowired
    private MedicineService medicineService;

    @Autowired
    private com.hms.repository.MedicineListRepository medicineListRepository;

    @Autowired
    private com.hms.repository.MedicineRepository medicineRepository;

    @Autowired
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;

    @Autowired
    private com.hms.repository.BillingRepository billingRepository;

    @Autowired
    private com.hms.repository.BillingItemRepository billingItemRepository;

    @Autowired
    private com.hms.repository.OpdRepository opdRepository;

    @Autowired
    private com.hms.repository.QueueEntryRepository queueEntryRepository;

    @Autowired
    private com.hms.repository.LabOrderRepository labOrderRepository;

    /**
     * Submit a consultation
     * Creates Medical Record, Prescriptions, Auto-generates Bill, and updates
     * Appointment
     */
    @Transactional
    public void submitConsultation(com.hms.dto.ConsultationRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long currentDoctorId = securityHelper.getCurrentUserId(); // Get current doctor's user ID

        // Get patient (required) - accept numeric id or publicId string
        com.hms.entity.Patient patient;
        if (request.getPatientId() != null && !request.getPatientId().isEmpty()) {
            String pid = request.getPatientId();
            if (pid.matches("^\\d+$")) {
                // numeric id
                Long numericId = Long.parseLong(pid);
                patient = patientRepository.findByIdAndHospitalIdAndIsActiveTrue(numericId, hospitalId)
                        .orElseThrow(() -> new RuntimeException("Patient not found"));
            } else {
                // treat as publicId
                patient = patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(pid, hospitalId)
                        .orElseThrow(() -> new RuntimeException("Patient not found"));
            }
        } else {
            throw new RuntimeException("Patient ID is required");
        }

        // Get appointment (optional - for appointment-based consultations)
        com.hms.entity.Appointment appointment = null;
        if (request.getAppointmentId() != null) {
            appointment = appointmentRepository.findById(request.getAppointmentId())
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            if (!appointment.getHospitalId().equals(hospitalId)) {
                throw new RuntimeException("Appointment does not belong to this hospital");
            }
        }

        // 1. Create Medical Record
        com.hms.entity.MedicalRecord record = new com.hms.entity.MedicalRecord();
        record.setHospitalId(hospitalId);
        record.setPatientId(patient.getId());
        // Resolve doctor id: prefer appointment.doctorId, otherwise map current user to Doctor entity
        Long resolvedDoctorId = null;
        if (appointment != null) {
            resolvedDoctorId = appointment.getDoctorId();
        } else {
            try {
                java.util.Optional<com.hms.entity.Doctor> dopt = doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId);
                if (dopt.isPresent()) {
                    resolvedDoctorId = dopt.get().getId();
                } else {
                    throw new RuntimeException("Doctor Not found");
                }
            } catch (Exception e) {
                throw new RuntimeException("Doctor Not found");
            }
        }
        record.setDoctorId(resolvedDoctorId);
        record.setAppointmentId(appointment != null ? appointment.getId() : null);
        record.setOpdId(request.getOpdId());
        record.setSymptoms(request.getSymptoms());
        record.setDiagnosis(request.getDiagnosis());
        record.setTreatmentNotes(request.getTreatmentNotes());
        record.setFollowUpDate(request.getFollowUpDate());

        com.hms.entity.MedicalRecord savedRecord = medicalRecordRepository.save(record);

        // 2. Create Prescriptions
        if (request.getPrescription() != null) {
            for (com.hms.dto.ConsultationRequest.PrescriptionItem item : request.getPrescription()) {
                com.hms.entity.Prescription p = new com.hms.entity.Prescription();
                p.setHospitalId(hospitalId);
                p.setMedicalRecordId(savedRecord.getId());
                p.setMedicineName(item.getMedicineName());
                p.setDosage(item.getDosage());
                p.setFrequency(item.getFrequency());
                p.setDuration(item.getDuration());
                p.setInstructions(item.getInstructions());
                p.setInstructions(item.getInstructions());
                prescriptionRepository.save(p);

                // --- Dynamic Learning: Auto-add to Master Catalog ---
                try {
                    if (!medicineListRepository.existsByNameAndHospitalId(item.getMedicineName(), hospitalId)) {
                        com.hms.entity.MedicineList newMed = new com.hms.entity.MedicineList();
                        newMed.setName(item.getMedicineName());
                        newMed.setType("Tablet"); // Default type
                        newMed.setDefaultDosage(item.getDosage());
                        newMed.setDefaultFrequency(item.getFrequency());
                        newMed.setDefaultDuration(item.getDuration());
                        newMed.setHospitalId(hospitalId);
                        newMed.setIsActive(true);
                        medicineListRepository.save(newMed);
                    }
                } catch (Exception e) {
                    // Ignore if already exists or fails - don't block consultation
                }
            }
        }

        // 2.b Create Lab Orders if requested
        try {
            if (request.getLabTests() != null && !request.getLabTests().isEmpty()) {
                for (String testName : request.getLabTests()) {
                    com.hms.entity.LabOrder order = new com.hms.entity.LabOrder();
                    order.setHospitalId(hospitalId);
                    order.setMedicalRecordId(savedRecord.getId());
                    order.setTestName(testName);
                    order.setStatus("ORDERED");
                    labOrderRepository.save(order);
                }
            }
        } catch (Exception e) {
            // don't fail the consultation for lab order persistence errors
            logger.warn("Failed to create lab orders", e);
        }

        // 3. Update Appointment Status (if appointment-based)
        if (appointment != null) {
            appointment.setStatus("COMPLETED");
            appointmentRepository.save(appointment);
        }

        // If consultation was for an OPD case, update OPD status to CONSULTED and remove queue entry
        if (request.getOpdId() != null) {
            try {
                java.util.Optional<com.hms.entity.Opd> opdOpt = opdRepository.findById(request.getOpdId());
                if (opdOpt.isPresent()) {
                    com.hms.entity.Opd opd = opdOpt.get();
                    opd.setStatus(com.hms.entity.Opd.Status.CONSULTED);
                    opdRepository.save(opd);

                    // Audit log for OPD status change
                    try {
                        auditLogService.logAction(
                                "OPD_STATUS_CHANGED",
                                "OPD " + (opd.getCaseId() != null ? opd.getCaseId() : opd.getId()) + " set to CONSULTED",
                                securityHelper.getCurrentUserEmail(),
                                hospitalId,
                                "OPD",
                                opd.getId().toString(),
                                null);
                    } catch (Exception ignored) {}
                }

                // Remove queue entries for this OPD so it doesn't appear again
                try {
                    queueEntryRepository.deleteByOpdId(request.getOpdId());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                // Don't fail consultation if OPD update fails
            }
        }

        // 4. Update Patient Status to COMPLETED
        patient.setStatus(com.hms.entity.PatientStatus.COMPLETED);
        patientRepository.save(patient);

        // 5. Auto-generate OPD Bill (unified flow)
        try {
            com.hms.entity.Billing bill = null;
            if (appointment != null) {
                bill = billingRepository.findByAppointmentId(appointment.getId()).orElse(null);
            }
            if (bill == null && request.getOpdId() != null) {
                bill = billingRepository.findByOpdId(request.getOpdId()).orElse(null);
            }
            if (bill == null) {
                // Always use OPD bill flow: create itemized bill (case paper + consultation)
                // If request.getOpdId() is null, createOpdBill will still create the bill but
                // without linking to an OPD; OPD completion on payment will only run when opdId is present.
                bill = billingService.createOpdBill(request.getOpdId(), patient.getId(), resolvedDoctorId);
            }

            if (bill != null && request.getCharges() != null && "PENDING".equalsIgnoreCase(bill.getPaymentStatus())) {
                java.util.List<com.hms.entity.BillingItem> existingItems = billingItemRepository.findByBillingId(bill.getId());
                billingItemRepository.deleteAll(existingItems);

                for (com.hms.dto.ConsultationRequest.ChargeItem charge : request.getCharges()) {
                    if (charge.getDescription() == null || charge.getDescription().trim().isEmpty()) {
                        continue;
                    }
                    com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
                    item.setBillingId(bill.getId());
                    item.setHospitalId(hospitalId);
                    item.setDescription(charge.getDescription().trim());
                    item.setAmount(charge.getAmount() != null ? charge.getAmount() : java.math.BigDecimal.ZERO);
                    billingItemRepository.save(item);
                }
                billingService.recalculateTotal(bill.getId());
            }
            
            // --- Process Administered Items (Stock Billing & Deductions) ---
            if (bill != null && request.getAdministeredItems() != null && !request.getAdministeredItems().isEmpty()) {
                for (com.hms.dto.ConsultationRequest.AdministeredItem item : request.getAdministeredItems()) {
                    if (item.getMedicineId() != null) {
                        // Secure lookup enforcing hospitalId to prevent cross-tenant writes
                        com.hms.entity.Medicine med = medicineRepository.findByIdAndHospitalId(item.getMedicineId(), hospitalId)
                            .orElseThrow(() -> new RuntimeException("Medicine not found in active inventory: ID " + item.getMedicineId()));
                        
                        // Strict active state check
                        if (med.getIsActive() == null || !med.getIsActive()) {
                            throw new RuntimeException("Medicine is currently inactive and cannot be administered: " + med.getName());
                        }

                        // Strict tenant match check for safety
                        if (med.getHospitalId() == null || !med.getHospitalId().equals(hospitalId)) {
                            throw new RuntimeException("Security violation: Tenant boundary mismatch for medicine ID " + item.getMedicineId());
                        }

                        // Robust validation to prevent NullPointerException and ensure positive unit prices
                        if (med.getUnitPrice() == null || med.getUnitPrice() <= 0.0) {
                            throw new RuntimeException("Medicine unit price is missing or invalid for: " + med.getName() + ". Please configure a valid unit price in inventory.");
                        }

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
                
                // Recalculate bill total (incorporates medicines + service charges)
                billingService.recalculateTotal(bill.getId());
            }
        } catch (Exception e) {
            logger.error("Failed to create OPD bill or administer stocks", e);
            if (e instanceof RuntimeException && e.getMessage().contains("Insufficient stock")) {
                throw (RuntimeException) e; // Prevent consultation completion if stock check fails
            }
        }

        // 6. Audit Log
        try {
            String description = appointment != null
                    ? "Consultation completed for Appointment ID: " + appointment.getCustomId()
                    : "Consultation completed for Patient: " + patient.getName();
            auditLogService.logAction(
                    "CONSULTATION_COMPLETED",
                    description,
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "APPOINTMENT",
                    appointment != null ? appointment.getPublicId() : "N/A",
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log", e);
        }

        // Broadcast WebSocket sync update to all clients in the hospital
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from submitConsultation", e);
        }
    }
}
