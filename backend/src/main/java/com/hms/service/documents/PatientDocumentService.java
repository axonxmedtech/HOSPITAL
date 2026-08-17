package com.hms.service.documents;

import com.hms.dto.PatientDocumentResponse;
import com.hms.entity.DocumentType;
import com.hms.entity.Patient;
import com.hms.entity.PatientDocument;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.PatientDocumentRepository;
import com.hms.repository.PatientNurseAssignmentRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Permissions, metadata and the patient-level nurse rule for records attached to a patient.
 *
 * <p>Reception, doctors and nurses attach records; only {@code HOSPITAL_ADMIN} removes one, and a
 * staff nurse may only act on a patient currently assigned to them, matching every other nursing
 * write in this codebase.
 */
@Service
public class PatientDocumentService {

    private static final Logger log = LoggerFactory.getLogger(PatientDocumentService.class);

    private static final Set<String> UPLOAD_ROLES =
            Set.of("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "NURSE", "NURSE_INCHARGE");

    private final PatientDocumentRepository documentRepository;
    private final PatientRepository patientRepository;
    private final PatientNurseAssignmentRepository assignmentRepository;
    private final IpdAdmissionRepository admissionRepository;
    private final DocumentStorage storage;
    private final UploadedFileValidator validator;
    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;

    public PatientDocumentService(PatientDocumentRepository documentRepository,
                                  PatientRepository patientRepository,
                                  PatientNurseAssignmentRepository assignmentRepository,
                                  IpdAdmissionRepository admissionRepository,
                                  DocumentStorage storage,
                                  UploadedFileValidator validator,
                                  SecurityContextHelper securityHelper,
                                  AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.patientRepository = patientRepository;
        this.assignmentRepository = assignmentRepository;
        this.admissionRepository = admissionRepository;
        this.storage = storage;
        this.validator = validator;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
    }

    public static boolean canUpload(String role) {
        return role != null && UPLOAD_ROLES.contains(role);
    }

    /** A removed lab report is a lost clinical record, so removal is the admin's call alone. */
    public static boolean canDelete(String role) {
        return "HOSPITAL_ADMIN".equals(role);
    }

    /**
     * Staff nurses may only touch patients assigned to them, matching vitals, notes and medication.
     * A nurse incharge runs the ward rather than a caseload, so the restriction does not apply.
     */
    public static boolean isAssignmentScoped(String role) {
        return "NURSE".equals(role);
    }

    public List<PatientDocumentResponse> list(String patientPublicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Patient patient = requirePatient(patientPublicId, hospitalId);
        return documentRepository
                .findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(hospitalId, patient.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PatientDocumentResponse upload(String patientPublicId, MultipartFile file,
                                          String title, String type, LocalDate documentDate) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        String role = securityHelper.getCurrentUserRole();
        if (!canUpload(role)) {
            throw new AccessDeniedException("Your role cannot attach records to a patient.");
        }

        Patient patient = requirePatient(patientPublicId, hospitalId);
        if (isAssignmentScoped(role)) {
            assertNurseIsAssignedTo(patient.getId());
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Give the record a title so it can be found later.");
        }

        validator.validate(file);
        String stored = storage.store(hospitalId, file, validator.extensionOf(file.getOriginalFilename()));

        PatientDocument doc = new PatientDocument();
        doc.setHospitalId(hospitalId);
        doc.setPatientId(patient.getId());
        doc.setTitle(title.trim());
        doc.setDocumentType(parseType(type));
        doc.setDocumentDate(documentDate);
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setStoredFilename(stored);
        doc.setContentType(file.getContentType());
        doc.setSizeBytes(file.getSize());
        doc.setUploadedBy(securityHelper.getCurrentUserEmail());

        try {
            PatientDocument saved = documentRepository.save(doc);
            audit("PATIENT_DOCUMENT_UPLOADED",
                    "Attached \"" + saved.getTitle() + "\" to patient " + patient.getCustomId(),
                    hospitalId, saved.getPublicId());
            return toResponse(saved);
        } catch (RuntimeException e) {
            // The file landed but the row did not. Leaving it would accumulate unreferenced
            // patient data on disk that nothing can ever show, delete or account for.
            storage.delete(hospitalId, stored);
            throw e;
        }
    }

    /** Opens the file for streaming. The caller's hospital is re-checked here, not just at the URL. */
    public DownloadHandle download(String documentPublicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PatientDocument doc = requireDocument(documentPublicId, hospitalId);
        return new DownloadHandle(doc.getOriginalFilename(), doc.getContentType(),
                storage.read(hospitalId, doc.getStoredFilename()));
    }

    /**
     * Soft delete. The row and the file both stay: a record that was attached to a patient and then
     * removed is itself a fact worth keeping, and a hard delete makes the gap unexplainable.
     */
    @Transactional
    public void softDelete(String documentPublicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (!canDelete(securityHelper.getCurrentUserRole())) {
            throw new AccessDeniedException("Only a hospital admin can remove an attached record.");
        }
        PatientDocument doc = requireDocument(documentPublicId, hospitalId);
        doc.setIsActive(false);
        doc.setDeletedBy(securityHelper.getCurrentUserEmail());
        doc.setDeletedAt(LocalDateTime.now());
        documentRepository.save(doc);
        audit("PATIENT_DOCUMENT_REMOVED",
                "Removed \"" + doc.getTitle() + "\"", hospitalId, doc.getPublicId());
    }

    /**
     * The patient-level form of the nursing rule.
     *
     * <p>NurseAccessGuard checks an admission, but a document hangs off the patient, so this asks
     * whether the nurse is assigned to any of that patient's admissions. A patient with no
     * admission has nothing to be assigned to, which is why a nurse cannot attach records for a
     * walk-in — reception or the doctor does that.
     */
    private void assertNurseIsAssignedTo(Long patientId) {
        Long nurseUserId = securityHelper.getCurrentUserId();
        boolean assigned = admissionRepository.findByPatientIdOrderByAdmissionDatetimeDesc(patientId)
                .stream()
                .anyMatch(a -> assignmentRepository
                        .existsByIpdAdmissionIdAndNurseUserIdAndIsActiveTrue(a.getId(), nurseUserId));
        if (!assigned) {
            throw new AccessDeniedException(
                    "You can only attach records for patients assigned to you.");
        }
    }

    private DocumentType parseType(String type) {
        if (type == null || type.isBlank()) return DocumentType.OTHER;
        try {
            return DocumentType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DocumentType.OTHER;
        }
    }

    private Patient requirePatient(String publicId, Long hospitalId) {
        return patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(publicId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
    }

    private PatientDocument requireDocument(String publicId, Long hospitalId) {
        PatientDocument doc = documentRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!Boolean.TRUE.equals(doc.getIsActive())) {
            throw new IllegalArgumentException("This record has been removed.");
        }
        return doc;
    }

    private PatientDocumentResponse toResponse(PatientDocument d) {
        return new PatientDocumentResponse(d.getPublicId(), d.getTitle(), d.getDocumentType(),
                d.getDocumentDate(), d.getOriginalFilename(), d.getContentType(),
                d.getSizeBytes(), d.getUploadedBy(), d.getUploadedAt());
    }

    private void audit(String action, String detail, Long hospitalId, String entityId) {
        try {
            auditLogService.logAction(action, detail, securityHelper.getCurrentUserEmail(),
                    hospitalId, "PATIENT_DOCUMENT", entityId, null);
        } catch (Exception e) {
            log.warn("Failed to audit {}: {}", action, e.getMessage());
        }
    }

    /** An open stream plus what the browser needs to name and render it. */
    public record DownloadHandle(String filename, String contentType, InputStream content) { }
}
