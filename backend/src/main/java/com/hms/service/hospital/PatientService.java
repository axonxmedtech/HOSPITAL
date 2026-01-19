package com.hms.service.hospital;

import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class PatientService {

    private static final Logger logger = LoggerFactory.getLogger(PatientService.class);

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private com.hms.repository.BillingRepository billingRepository;

    @Autowired
    private com.hms.repository.PrescriptionRepository prescriptionRepository;

    /**
     * Add a new patient
     * Automatically sets hospital_id from the authenticated user's context
     * 
     * @param patient Patient entity to create
     * @return Created Patient entity
     */
    public Patient addPatient(Patient patient) {
        // Get hospital_id from security context (multi-tenant isolation)
        Long hospitalId = securityHelper.getCurrentHospitalId();

        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        // Set hospital_id to ensure multi-tenant isolation
        patient.setHospitalId(hospitalId);

        logger.info("Hospital {} creating new patient: {}", hospitalId, patient.getName());
        Patient savedPatient = patientRepository.save(patient);

        // Create audit log
        try {
            String performedBy = securityHelper.getCurrentUserEmail();
            auditLogService.logAction(
                    "PATIENT_CREATED",
                    "Patient " + savedPatient.getName() + " was created.",
                    performedBy,
                    hospitalId,
                    "PATIENT",
                    savedPatient.getPublicId(),
                    null); // No reason for creation
        } catch (Exception e) {
            logger.warn("Failed to create audit log for patient creation", e);
        }

        return savedPatient;
    }

    /**
     * Update an active patient's details
     * Validates hospital ownership before updating
     * 
     * @param publicId    Patient Public ID
     * @param updatedData New patient data
     * @return Updated Patient entity
     */
    public Patient updatePatient(String publicId, Patient updatedData) {
        // Ensure patient exists and belongs to this hospital
        Patient existingPatient = getPatientByPublicId(publicId);

        existingPatient.setName(updatedData.getName());
        existingPatient.setAge(updatedData.getAge());
        existingPatient.setGender(updatedData.getGender());
        existingPatient.setPhone(updatedData.getPhone());
        existingPatient.setAddress(updatedData.getAddress());

        return patientRepository.save(existingPatient);
    }

    /**
     * Get all active patients for the current hospital with optional filter
     * 
     * @param view Optional filter view ('today', 'history')
     * @return Page of active patients
     */
    public org.springframework.data.domain.Page<Patient> getAllPatients(String search, String view,
            org.springframework.data.domain.Pageable pageable) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        org.springframework.data.domain.Page<Patient> patients;

        // Handle 'today' view
        if ("today".equalsIgnoreCase(view)) {
            java.time.LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
            java.time.LocalDateTime endOfDay = java.time.LocalDate.now().atTime(java.time.LocalTime.MAX);

            patients = patientRepository.findByHospitalIdAndIsActiveTrueAndCreatedAtBetweenOrderByCreatedAtDesc(
                    hospitalId, startOfDay, endOfDay, pageable);
        } else {
            // Default / History
            patients = patientRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospitalId, pageable);
        }

        // Populate latest bill for each patient
        patients.forEach(patient -> {
            billingRepository.findTopByPatientIdOrderByCreatedAtDesc(patient.getId())
                    .ifPresent(patient::setLatestBill);
        });

        return patients;
    }

    public org.springframework.data.domain.Page<Patient> getAllPatients(
            org.springframework.data.domain.Pageable pageable) {
        return getAllPatients(null, null, pageable);
    }

    // Kept for backward compatibility/internal use (e.g. stats)
    public List<Patient> getAllPatients() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null)
            throw new RuntimeException("Hospital ID not found in context");
        List<Patient> patients = patientRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospitalId);

        // Populate latest bill
        patients.forEach(patient -> {
            billingRepository.findTopByPatientIdOrderByCreatedAtDesc(patient.getId())
                    .ifPresent(patient::setLatestBill);
        });

        return patients;
    }

    /**
     * Search active patients by name or phone
     * 
     * @param query Search term
     * @return List of matching active patients
     */
    public List<Patient> searchPatients(String query) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        if (query == null || query.trim().isEmpty()) {
            return getAllPatients();
        }

        List<Patient> patients = patientRepository
                .findByHospitalIdAndIsActiveTrueAndNameContainingIgnoreCaseOrHospitalIdAndIsActiveTrueAndPhoneContaining(
                        hospitalId, query, hospitalId, query);

        // Populate latest bill
        patients.forEach(patient -> {
            billingRepository.findTopByPatientIdOrderByCreatedAtDesc(patient.getId())
                    .ifPresent(patient::setLatestBill);
        });

        return patients;
    }

    public Patient getPatientByPublicId(String publicId) {
        // Get hospital_id from security context (multi-tenant isolation)
        Long hospitalId = securityHelper.getCurrentHospitalId();

        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        // Find patient only if it belongs to this hospital and is active
        return patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(publicId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
    }

    public Patient getPatientById(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }
        return patientRepository.findById(id)
                .filter(p -> p.getHospitalId().equals(hospitalId))
                .orElseThrow(() -> new RuntimeException("Patient not found"));
    }

    /**
     * Soft delete a patient
     * 
     * @param id Patient ID
     */
    public void deletePatient(String publicId, String reason) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null)
            throw new RuntimeException("Hospital ID not found in context");

        Patient patient = patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(publicId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        logger.info("Hospital {} soft deleting patient ID: {}. Reason: {}", hospitalId, publicId, reason);
        patient.setIsActive(false);
        patientRepository.save(patient);

        try {
            auditLogService.logAction(
                    "PATIENT_DELETED",
                    "Patient " + patient.getName() + " was deleted. Reason: "
                            + (reason != null ? reason : "No reason provided"),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "PATIENT",
                    publicId,
                    reason);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for patient deletion", e);
        }
    }

    /**
     * Update patient status
     * 
     * @param publicId Patient public ID
     * @param status   New status
     * @return Updated patient
     */
    public Patient updatePatientStatus(String publicId, com.hms.entity.PatientStatus status) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        Patient patient = patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(publicId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        logger.info("Hospital {} updating patient {} status from {} to {}",
                hospitalId, publicId, patient.getStatus(), status);

        patient.setStatus(status);
        return patientRepository.save(patient);
    }

    /**
     * Start consultation for a patient
     * Changes status from REGISTERED to CONSULTING
     * 
     * @param publicId Patient public ID
     * @return Updated patient
     */
    public Patient startConsultation(String publicId) {
        return updatePatientStatus(publicId, com.hms.entity.PatientStatus.CONSULTING);
    }

    /**
     * Mark consultation as completed
     * Changes status to COMPLETED
     * 
     * @param publicId Patient public ID
     * @return Updated patient
     */
    public Patient completeConsultation(String publicId) {
        return updatePatientStatus(publicId, com.hms.entity.PatientStatus.COMPLETED);
    }

    /**
     * Get complete patient consultation details
     * Includes demographics, medical history, and current visit info
     * 
     * @param publicId Patient public ID
     * @return Map containing patient details and medical history
     */
    public java.util.Map<String, Object> getPatientConsultationDetails(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new RuntimeException("Hospital ID not found in context");
        }

        // Get patient
        Patient patient = getPatientByPublicId(publicId);

        // Get medical history (last 5 consultations)
        List<com.hms.entity.MedicalRecord> medicalHistory = medicalRecordRepository
                .findTop5ByPatientIdOrderByCreatedAtDesc(patient.getId());

        // Build response
        java.util.Map<String, Object> response = new java.util.HashMap<>();

        // Patient demographics
        java.util.Map<String, Object> patientData = new java.util.HashMap<>();
        patientData.put("publicId", patient.getPublicId());
        patientData.put("name", patient.getName());
        patientData.put("age", patient.getAge());
        patientData.put("gender", patient.getGender());
        patientData.put("phone", patient.getPhone());
        patientData.put("address", patient.getAddress());
        patientData.put("status", patient.getStatus() != null ? patient.getStatus().toString() : "REGISTERED");
        response.put("patient", patientData);

        // Medical history
        List<java.util.Map<String, Object>> historyList = new java.util.ArrayList<>();
        for (com.hms.entity.MedicalRecord record : medicalHistory) {
            java.util.Map<String, Object> historyItem = new java.util.HashMap<>();
            historyItem.put("date", record.getCreatedAt());
            historyItem.put("symptoms", record.getSymptoms());
            historyItem.put("diagnosis", record.getDiagnosis());
            historyItem.put("treatment", record.getTreatmentNotes());
            // You can add doctor name if you have doctor repository
            historyList.add(historyItem);
        }
        response.put("medicalHistory", historyList);

        return response;
    }

    /**
     * Get latest consultation details including prescription
     */
    public java.util.Map<String, Object> getLatestPrescription(String publicId) {
        com.hms.entity.Patient patient = patientRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        com.hms.entity.MedicalRecord record = medicalRecordRepository
                .findTopByPatientIdOrderByCreatedAtDesc(patient.getId())
                .orElseThrow(() -> new RuntimeException("No consultation records found"));

        List<com.hms.entity.Prescription> prescriptions = prescriptionRepository.findByMedicalRecordId(record.getId());

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("medicalRecord", record);
        result.put("prescriptions", prescriptions);
        return result;
    }
}
