package com.hms.controller.hospital;

import com.hms.entity.Patient;
import com.hms.service.hospital.PatientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * PatientController - REST controller for patient management
 * 
 * This controller provides endpoints for:
 * - Adding new patients (Hospital Admin only)
 * - Listing patients (Hospital Admin and Doctor)
 * - Getting patient details (Hospital Admin and Doctor)
 * 
 * All operations are automatically filtered by hospital_id.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/patients")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class PatientController {

    @Autowired
    private PatientService patientService;

    /**
     * Add a new patient
     * Only Hospital Admin can add patients
     * 
     * @param patient Patient entity to create
     * @return Created Patient entity
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> addPatient(@Valid @RequestBody Patient patient) {
        Patient createdPatient = patientService.addPatient(patient);
        return ResponseEntity.ok(createdPatient);
    }

    /**
     * Update an existing patient
     * Only Hospital Admin and Receptionist can update patients
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> updatePatient(@PathVariable Long id, @Valid @RequestBody Patient patient) {
        try {
            Patient updatedPatient = patientService.updatePatient(id, patient);
            return ResponseEntity.ok(updatedPatient);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get all patients for the current hospital
     * Accessible by Hospital Admin, Doctor, and Receptionist
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getAllPatients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(patientService.searchPatients(search));
        }
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(patientService.getAllPatients(null, view, pageable));
    }

    /**
     * Get patient by ID
     * Accessible by Hospital Admin, Doctor, and Receptionist
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getPatientById(@PathVariable String id) {
        try {
            Patient patient = patientService.getPatientByPublicId(id);
            return ResponseEntity.ok(patient);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Delete (Soft Delete) a patient
     * Only Hospital Admin can delete patients
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> deletePatient(@PathVariable String id, @RequestParam(required = false) String reason) {
        try {
            patientService.deletePatient(id, reason);
            return ResponseEntity.ok("Patient deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Update patient status
     * Doctors and Receptionists can update patient status during consultation
     * workflow
     * 
     * @param publicId Patient public ID
     * @param status   New status (REGISTERED, CONSULTING, COMPLETED)
     * @return Updated patient
     */
    @PutMapping("/{publicId}/status")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updatePatientStatus(
            @PathVariable String publicId,
            @RequestParam String status) {
        try {
            com.hms.entity.PatientStatus patientStatus = com.hms.entity.PatientStatus.valueOf(status.toUpperCase());
            Patient updatedPatient = patientService.updatePatientStatus(publicId, patientStatus);
            return ResponseEntity.ok(updatedPatient);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Start consultation for a patient
     * Changes patient status to CONSULTING
     * 
     * @param publicId Patient public ID
     * @return Updated patient
     */
    @PostMapping("/{publicId}/start-consultation")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> startConsultation(@PathVariable String publicId) {
        try {
            Patient updatedPatient = patientService.startConsultation(publicId);
            return ResponseEntity.ok(updatedPatient);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get complete patient consultation details
     * Includes demographics, medical history, and current visit info
     * Used by doctors during consultation
     * 
     * @param publicId Patient public ID
     * @return Patient consultation details
     */
    @GetMapping("/{publicId}/consultation-details")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getPatientConsultationDetails(@PathVariable Long publicId) {
        java.util.Map<String, Object> details = patientService.getPatientConsultationDetails(publicId);
        return ResponseEntity.ok(details);
    }

    /**
     * Get latest prescription for a patient
     */
    @GetMapping("/{publicId}/latest-prescription")
    @PreAuthorize("hasAnyRole('DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getLatestPrescription(@PathVariable String publicId) {
        try {
            return ResponseEntity.ok(patientService.getLatestPrescription(publicId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
