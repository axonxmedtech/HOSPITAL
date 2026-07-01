package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.ConsentCreateRequest;
import com.hms.dto.ConsentSignRequest;
import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.PdfService;
import com.hms.service.hospital.ConsentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * ConsentController - Endpoints for managing Patient Consents (General, Blood Transfusion, etc.).
 * Includes tenant isolation verification and PDF print capabilities.
 *
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/consents")
public class ConsentController {

    private static final Logger log = LoggerFactory.getLogger(ConsentController.class);

    @Autowired
    private ConsentService consentService;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PdfService pdfService;

    /**
     * Creates a new consent draft. Only receptionists, doctors, or hospital admins can draft consents.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<PatientConsent>> createConsent(@Valid @RequestBody ConsentCreateRequest request) {
        PatientConsent consent = consentService.createConsentDraft(request);
        return ResponseEntity.ok(ApiResponse.ok("Consent draft created successfully", consent));
    }

    /**
     * Gets details of a single consent.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<PatientConsent>> getConsent(@PathVariable Long id) {
        PatientConsent consent = consentService.getConsent(id);
        return ResponseEntity.ok(ApiResponse.ok(consent));
    }

    /**
     * Fetches all consents linked to an admission.
     */
    @GetMapping("/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<List<PatientConsent>>> getConsentsForAdmission(@PathVariable Long admissionId) {
        List<PatientConsent> list = consentService.getConsentsForAdmission(admissionId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * Fetches all consents linked to a patient.
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<List<PatientConsent>>> getConsentsForPatient(@PathVariable Long patientId) {
        List<PatientConsent> list = consentService.getConsentsForPatient(patientId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * Captures a digital signature slot for a specific role (e.g. PATIENT, GUARDIAN, WITNESS).
     */
    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<PatientConsent>> signConsent(
            @PathVariable Long id,
            @Valid @RequestBody ConsentSignRequest request) {
        PatientConsent consent = consentService.signConsent(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Signature captured successfully", consent));
    }

    /**
     * Updates Blood Transfusion Consent specific attributes.
     */
    @PutMapping("/{id}/blood-details")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<BloodConsentDetail>> updateBloodConsentDetail(
            @PathVariable Long id,
            @RequestBody BloodConsentDetail details) {
        BloodConsentDetail updated = consentService.updateBloodConsentDetail(id, details);
        return ResponseEntity.ok(ApiResponse.ok("Blood consent details updated successfully", updated));
    }

    /**
     * Fetches Blood Transfusion Consent specific details.
     */
    @GetMapping("/{id}/blood-details")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<BloodConsentDetail>> getBloodConsentDetail(@PathVariable Long id) {
        BloodConsentDetail detail = consentService.getBloodConsentDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    /**
     * Updates Surgical Consent (Form 16) specific attributes.
     */
    @PutMapping("/{id}/surgical-details")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<SurgicalConsentDetail>> updateSurgicalConsentDetail(
            @PathVariable Long id,
            @RequestBody SurgicalConsentDetail details) {
        SurgicalConsentDetail updated = consentService.updateSurgicalConsentDetail(id, details);
        return ResponseEntity.ok(ApiResponse.ok("Surgical consent details updated successfully", updated));
    }

    /**
     * Fetches Surgical Consent (Form 16) specific details.
     */
    @GetMapping("/{id}/surgical-details")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<SurgicalConsentDetail>> getSurgicalConsentDetail(@PathVariable Long id) {
        SurgicalConsentDetail detail = consentService.getSurgicalConsentDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    /**
     * Locks the consent form, preventing any further signature updates.
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<PatientConsent>> submitConsent(@PathVariable Long id) {
        PatientConsent consent = consentService.submitConsent(id);
        return ResponseEntity.ok(ApiResponse.ok("Consent finalized and locked successfully", consent));
    }

    /**
     * Prints the consent form as an A4 PDF document. Valid only when status is LOCKED.
     */
    @GetMapping("/{id}/print")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<InputStreamResource> printConsent(@PathVariable Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        PatientConsent consent = consentService.getConsent(id);

        if (!"LOCKED".equalsIgnoreCase(consent.getStatus())) {
            throw new IllegalArgumentException("Consent form must be LOCKED/FINALIZED before printing");
        }

        // Fetch support entities
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital tenant not found"));

        Patient patient = patientRepository.findByIdAndHospitalIdAndIsActiveTrue(consent.getPatientId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient record not found"));

        IpdAdmission admission = null;
        if (consent.getAdmissionId() != null) {
            admission = ipdAdmissionRepository.findById(consent.getAdmissionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Admission record not found"));
        }

        BloodConsentDetail bloodDetail = null;
        if ("BLOOD".equalsIgnoreCase(consent.getConsentType())) {
            bloodDetail = consentService.getBloodConsentDetail(consent.getId());
        }

        Doctor doctor = null;
        if (admission != null && admission.getDoctorId() != null) {
            doctor = doctorRepository.findById(admission.getDoctorId()).orElse(null);
        }

        ByteArrayInputStream bis = pdfService.generateConsentPdf(hospital, patient, admission, consent, bloodDetail, doctor);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "inline; filename=consent_" + id + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(bis));
    }
}
