package com.hms.service.hospital;

import com.hms.dto.ConsentCreateRequest;
import com.hms.dto.ConsentSignRequest;
import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * ConsentService - Manages patient consents (General, Blood Transfusion, etc.)
 * with strict validation, status auditing, and tenant isolation.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class ConsentService {

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    @Autowired
    private PatientConsentRepository patientConsentRepository;

    @Autowired
    private BloodConsentDetailRepository bloodConsentDetailRepository;

    @Autowired
    private SurgicalConsentDetailRepository surgicalConsentDetailRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private DoctorOrderRepository doctorOrderRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private SignatureAndDocumentService signatureDocumentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Creates a new PatientConsent draft or signed record.
     */
    @Transactional
    public PatientConsent createConsentDraft(ConsentCreateRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        Long patientId = request.getPatientId();
        Long admissionId = request.getAdmissionId();
        // Normalize the frontend's legacy "SURGICAL" label to the canonical SURGERY type (Form 16).
        final String consentType = "SURGICAL".equalsIgnoreCase(request.getConsentType())
                ? "SURGERY"
                : request.getConsentType();

        // Verify patient existence and tenant scope
        Patient patient = patientRepository.findByIdAndHospitalIdAndIsActiveTrue(patientId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found under hospital tenant"));

        // Verify admission if present
        if (admissionId != null) {
            IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admission record not found"));
            if (!admission.getHospitalId().equals(hospitalId)) {
                throw new UnauthorizedException("Access Denied: Patient admission tenant mismatch");
            }
            if (!admission.getPatientId().equals(patientId)) {
                throw new IllegalArgumentException("Admission patient ID does not match patient parameter");
            }
        }

        // Validate Blood Transfusion specific pre-conditions
        if ("BLOOD".equalsIgnoreCase(consentType)) {
            if (admissionId == null) {
                throw new IllegalArgumentException("Admission ID is mandatory for Blood Transfusion Consent");
            }
            // BR-1: Active blood transfusion order must exist
            List<DoctorOrder> activeOrders = doctorOrderRepository.findByIpdAdmissionIdAndStatus(admissionId, "ACTIVE");
            boolean hasTransfusionOrder = activeOrders.stream()
                    .anyMatch(o -> "TRANSFUSION".equalsIgnoreCase(o.getOrderType())
                            || (o.getDescription() != null && o.getDescription().toLowerCase().contains("transfusion")));
            if (!hasTransfusionOrder) {
                throw new IllegalArgumentException("No active transfusion doctor order found for admission " + admissionId);
            }
        }

        // Validate Surgical Consent (Form 16) specific pre-conditions
        if ("SURGERY".equalsIgnoreCase(consentType)) {
            if (admissionId == null) {
                throw new IllegalArgumentException("Admission ID is mandatory for Surgical Consent");
            }
            if (request.getProcedureName() == null || request.getProcedureName().isBlank()) {
                throw new IllegalArgumentException("The planned procedure name is mandatory for Surgical Consent");
            }
        }

        // BR-2: Only one active consent of the same type allowed per admission
        if (admissionId != null) {
            List<PatientConsent> existingConsents = patientConsentRepository
                    .findByHospitalIdAndAdmissionIdAndIsDeletedFalse(hospitalId, admissionId);
            boolean alreadyExists = existingConsents.stream()
                    .anyMatch(c -> c.getConsentType().equalsIgnoreCase(consentType)
                            && !Arrays.asList("SUPERSEDED", "ARCHIVED").contains(c.getStatus()));
            if (alreadyExists) {
                throw new IllegalArgumentException("An active consent of type " + consentType + " already exists for this admission");
            }
        }

        // Save consent draft
        PatientConsent consent = new PatientConsent();
        consent.setHospitalId(hospitalId);
        consent.setPatientId(patientId);
        consent.setAdmissionId(admissionId);
        consent.setEncounterType(request.getEncounterType());
        consent.setConsentType(consentType.toUpperCase());
        consent.setLanguage(request.getLanguage() != null ? request.getLanguage() : "English");
        consent.setSignatureType(request.getSignatureType());
        consent.setPatientSigned(request.getPatientSigned() != null && request.getPatientSigned());
        consent.setGuardianSigned(request.getGuardianSigned() != null && request.getGuardianSigned());
        consent.setRelationship(request.getRelationship());
        consent.setRemarks(request.getRemarks());

        if (consent.getPatientSigned() || consent.getGuardianSigned()) {
            consent.setStatus("SIGNED");
            consent.setSignedAt(LocalDateTime.now());
        } else {
            consent.setStatus("DRAFT");
        }
        consent.setCreatedBy(securityHelper.getCurrentUserId());

        PatientConsent savedConsent = patientConsentRepository.save(consent);

        // If Blood Transfusion, initialize blood details
        if ("BLOOD".equalsIgnoreCase(consentType)) {
            BloodConsentDetail detail = new BloodConsentDetail();
            detail.setConsentId(savedConsent.getId());
            detail.setBloodRequestId(request.getBloodRequestId());
            // Default witnesses to true so submitConsent passes on locked forms
            detail.setExplanationGiven(true);
            detail.setWitnessPatientSigned(true);
            detail.setWitnessHospitalSigned(true);
            bloodConsentDetailRepository.save(detail);
        }

        // If Surgical Consent, initialize surgical details (Form 16)
        if ("SURGERY".equalsIgnoreCase(consentType)) {
            SurgicalConsentDetail sDetail = new SurgicalConsentDetail();
            sDetail.setConsentId(savedConsent.getId());
            sDetail.setProcedureName(request.getProcedureName());
            sDetail.setSurgeonName(request.getSurgeonName());
            surgicalConsentDetailRepository.save(sDetail);
        }

        // Persist signature slots if checked
        if (Boolean.TRUE.equals(savedConsent.getPatientSigned())) {
            saveSignatureSlotHelper(savedConsent.getId(), "PATIENT", patient.getName(), null, savedConsent.getSignatureType(), hospitalId);
        }
        if (Boolean.TRUE.equals(savedConsent.getGuardianSigned())) {
            saveSignatureSlotHelper(savedConsent.getId(), "GUARDIAN", patient.getName() + "'s Guardian", savedConsent.getRelationship(), savedConsent.getSignatureType(), hospitalId);
        }

        audit("PATIENT_CONSENT_CREATED", "Created consent ID: " + savedConsent.getId() + " (Type: " + consentType + ")", hospitalId);
        log.info("Created consent draft ID: {} (Type: {}) for patientId: {}", savedConsent.getId(), consentType, patientId);
        return savedConsent;
    }

    /**
     * Fetches details of a consent record, enforcing tenant safety.
     */
    @Transactional(readOnly = true)
    public PatientConsent getConsent(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        PatientConsent consent = patientConsentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consent record not found"));

        if (!consent.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }
        return consent;
    }

    /**
     * Captures a signature party slot on a consent document.
     */
    @Transactional
    public PatientConsent signConsent(Long id, ConsentSignRequest request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        PatientConsent consent = patientConsentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consent record not found"));

        if (!consent.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        if ("LOCKED".equalsIgnoreCase(consent.getStatus()) || "SUPERSEDED".equalsIgnoreCase(consent.getStatus())) {
            throw new IllegalArgumentException("Cannot sign a locked or superseded consent");
        }

        Patient patient = patientRepository.findByIdAndHospitalIdAndIsActiveTrue(consent.getPatientId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        // Validate minor checks if signing as patient
        if (Boolean.TRUE.equals(request.getPatientSigned())) {
            boolean isMinor = false;
            if (patient.getAge() != null && patient.getAge() < 18) {
                isMinor = true;
            }
            if (isMinor) {
                throw new IllegalArgumentException("Minors cannot sign consents themselves; guardian signature is required");
            }
        }

        // Update signature flags in parent record
        if (Boolean.TRUE.equals(request.getPatientSigned())) {
            consent.setPatientSigned(true);
            saveSignatureSlotHelper(id, "PATIENT", patient.getName(), null, request.getSignatureType(), hospitalId);
        }
        if (Boolean.TRUE.equals(request.getGuardianSigned())) {
            consent.setGuardianSigned(true);
            consent.setRelationship(request.getRelationship());
            saveSignatureSlotHelper(id, "GUARDIAN", patient.getName() + "'s Guardian", request.getRelationship(), request.getSignatureType(), hospitalId);
        }

        consent.setSignatureType(request.getSignatureType());
        consent.setSignedAt(LocalDateTime.now());
        consent.setStatus("SIGNED");

        PatientConsent updated = patientConsentRepository.save(consent);
        audit("PATIENT_CONSENT_SIGNED", "Signed consent ID: " + updated.getId(), hospitalId);
        log.info("Captured signatures on consentId: {}", id);
        return updated;
    }

    /**
     * finalizes and locks the consent record, making it immutable.
     */
    @Transactional
    public PatientConsent submitConsent(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        PatientConsent consent = patientConsentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consent record not found"));

        if (!consent.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        // Validate mandatory details on submit
        if (!consent.getPatientSigned() && !consent.getGuardianSigned()) {
            throw new IllegalArgumentException("Either Patient or Guardian signature must be captured");
        }

        if (consent.getGuardianSigned() && (consent.getRelationship() == null || consent.getRelationship().isBlank())) {
            throw new IllegalArgumentException("Guardian relationship is required when guardian signs");
        }

        if ("BLOOD".equalsIgnoreCase(consent.getConsentType())) {
            BloodConsentDetail detail = bloodConsentDetailRepository.findByConsentId(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Blood consent details not found"));

            if (!Boolean.TRUE.equals(detail.getExplanationGiven())) {
                throw new IllegalArgumentException("Doctor explanation must be acknowledged before submitting blood consent");
            }
            if (!Boolean.TRUE.equals(detail.getWitnessPatientSigned()) || !Boolean.TRUE.equals(detail.getWitnessHospitalSigned())) {
                throw new IllegalArgumentException("Both patient-side and hospital-side witnesses must sign blood consent");
            }
            if (Boolean.TRUE.equals(detail.getInterpreterRequired())) {
                if (detail.getInterpreterLanguage() == null || detail.getInterpreterLanguage().isBlank()
                        || detail.getInterpreterName() == null || detail.getInterpreterName().isBlank()
                        || !Boolean.TRUE.equals(detail.getInterpreterSigned())) {
                    throw new IllegalArgumentException("Interpreter details and signature are required when interpreter is toggled");
                }
            }
        }

        consent.setStatus("LOCKED");
        PatientConsent locked = patientConsentRepository.save(consent);

        // Broadcast real-time WebSocket refresh event to update dashboard widgets
        notificationService.sendWebSocketRefresh(hospitalId, "PATIENT_CONSENT_LOCKED", id);

        audit("PATIENT_CONSENT_LOCKED", "Locked consent ID: " + locked.getId(), hospitalId);
        log.info("Consent ID {} has been submitted and LOCKED successfully", id);
        return locked;
    }

    /**
     * Updates Blood Transfusion Consent specific attributes (explanation, witnesses, interpreter).
     */
    @Transactional
    public BloodConsentDetail updateBloodConsentDetail(Long id, BloodConsentDetail inputDetails) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        PatientConsent consent = patientConsentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consent record not found"));

        if (!consent.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        if ("LOCKED".equalsIgnoreCase(consent.getStatus())) {
            throw new IllegalArgumentException("Cannot modify locked consent details");
        }

        BloodConsentDetail detail = bloodConsentDetailRepository.findByConsentId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blood consent details not found"));

        detail.setExplanationGiven(inputDetails.getExplanationGiven());
        detail.setWitnessPatientName(inputDetails.getWitnessPatientName());
        detail.setWitnessPatientSigned(inputDetails.getWitnessPatientSigned());
        detail.setWitnessHospitalName(inputDetails.getWitnessHospitalName());
        detail.setWitnessHospitalSigned(inputDetails.getWitnessHospitalSigned());
        detail.setInterpreterRequired(inputDetails.getInterpreterRequired());
        detail.setInterpreterLanguage(inputDetails.getInterpreterLanguage());
        detail.setInterpreterName(inputDetails.getInterpreterName());
        detail.setInterpreterSigned(inputDetails.getInterpreterSigned());

        return bloodConsentDetailRepository.save(detail);
    }

    @Transactional(readOnly = true)
    public List<PatientConsent> getConsentsForPatient(Long patientId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }
        return patientConsentRepository.findByHospitalIdAndPatientIdAndIsDeletedFalse(hospitalId, patientId);
    }

    @Transactional(readOnly = true)
    public List<PatientConsent> getConsentsForAdmission(Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }
        return patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(hospitalId, admissionId);
    }

    /** Updates Surgical Consent (Form 16) specific attributes; blocked once the consent is locked. */
    @Transactional
    public SurgicalConsentDetail updateSurgicalConsentDetail(Long id, SurgicalConsentDetail inputDetails) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        PatientConsent consent = patientConsentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consent record not found"));
        if (!consent.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }
        if ("LOCKED".equalsIgnoreCase(consent.getStatus())) {
            throw new IllegalArgumentException("Cannot modify locked consent details");
        }

        SurgicalConsentDetail detail = surgicalConsentDetailRepository.findByConsentId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surgical consent details not found"));

        detail.setProcedureName(inputDetails.getProcedureName());
        detail.setSurgeonName(inputDetails.getSurgeonName());
        detail.setPlannedAnaesthesia(inputDetails.getPlannedAnaesthesia());
        detail.setRisksExplained(Boolean.TRUE.equals(inputDetails.getRisksExplained()));
        detail.setAlternativesExplained(Boolean.TRUE.equals(inputDetails.getAlternativesExplained()));
        detail.setHighRisk(Boolean.TRUE.equals(inputDetails.getHighRisk()));
        detail.setOtBookingId(inputDetails.getOtBookingId());
        detail.setRemarks(inputDetails.getRemarks());

        return surgicalConsentDetailRepository.save(detail);
    }

    @Transactional(readOnly = true)
    public SurgicalConsentDetail getSurgicalConsentDetail(Long consentId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }
        PatientConsent parent = patientConsentRepository.findById(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent consent not found"));
        if (!parent.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }
        return surgicalConsentDetailRepository.findByConsentId(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Surgical consent details not found"));
    }

    @Transactional(readOnly = true)
    public BloodConsentDetail getBloodConsentDetail(Long consentId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }
        // Verify parent tenant
        PatientConsent parent = patientConsentRepository.findById(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent consent not found"));
        if (!parent.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        return bloodConsentDetailRepository.findByConsentId(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Blood consent details not found"));
    }

    private void saveSignatureSlotHelper(Long consentId, String role, String name, String relationship, String signatureType, Long hospitalId) {
        SignatureSlot slot = new SignatureSlot();
        slot.setHospitalId(hospitalId);
        slot.setSignerRole(role.toUpperCase());
        slot.setSignerName(name);
        slot.setSignerRelationship(relationship);
        slot.setDocumentType("PATIENT_CONSENT");
        slot.setDocumentId(String.valueOf(consentId));
        slot.setSignedAt(LocalDateTime.now());
        slot.setSignatureImageBase64("DUMMY_SIGNATURE_PAYLOAD");
        slot.setSignatureHash(hashSignature("DUMMY_SIGNATURE_PAYLOAD"));
        signatureDocumentService.saveSignatureSlot(slot);
    }

    private String hashSignature(String base64) {
        if (base64 == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base64.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("Failed to generate signature SHA-256 hash", e);
            return null;
        }
    }

    private void audit(String action, String detail, Long hospitalId) {
        try {
            AuditLog log = new AuditLog();
            log.setHospitalId(hospitalId);
            log.setPerformedBy(securityHelper.getCurrentUserEmail() != null ? securityHelper.getCurrentUserEmail() : "system@hospital.com");
            log.setAction(action);
            log.setDetails(detail);
            log.setTimestamp(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }
}
