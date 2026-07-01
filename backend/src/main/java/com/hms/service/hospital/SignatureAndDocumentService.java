package com.hms.service.hospital;

import com.hms.entity.DocumentVersion;
import com.hms.entity.SignatureSlot;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.DocumentVersionRepository;
import com.hms.repository.SignatureSlotRepository;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SignatureAndDocumentService - Shared clinical service managing signature slot
 * captures, document history, and versions, with strict tenant-isolation validation.
 *
 * @author HMS Team
 * @version Phase-0.8
 */
@Service
public class SignatureAndDocumentService {

    private static final Logger log = LoggerFactory.getLogger(SignatureAndDocumentService.class);

    @Autowired
    private SignatureSlotRepository signatureSlotRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    /**
     * Persists a signature slot associated with general consent, witness/guardian logs,
     * or clinical document approvals.
     * Enforces saving under the current authenticated hospital tenant context.
     *
     * @param slot the SignatureSlot to save
     * @return the saved SignatureSlot
     */
    @Transactional
    public SignatureSlot saveSignatureSlot(SignatureSlot slot) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        // Force multi-tenant context safety
        slot.setHospitalId(hospitalId);
        slot.setSignedAt(LocalDateTime.now());

        SignatureSlot saved = signatureSlotRepository.save(slot);
        log.info("Saved signature slot ID: {} for document: {} (Role: {}) under hospitalId: {}",
                saved.getId(), slot.getDocumentId(), slot.getSignerRole(), hospitalId);
        return saved;
    }

    /**
     * Retrieves all signature captures linked to a document.
     * Enforces tenant-isolation lookup.
     *
     * @param documentType the category description
     * @param documentId the document primary lookup key
     * @return list of SignatureSlots matching parameters
     */
    @Transactional(readOnly = true)
    public List<SignatureSlot> getSignaturesForDocument(String documentType, String documentId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        return signatureSlotRepository.findByHospitalIdAndDocumentTypeAndDocumentIdOrderBySignedAtAsc(
                hospitalId, documentType, documentId);
    }

    /**
     * Increments the document's revision number and logs the update URL.
     * Verifies that the input hospitalId matches the caller's tenant context.
     *
     * @param hospitalId the targeted tenant
     * @param documentType the category description
     * @param documentId the logical document identifier
     * @param contentUrl the location URL
     * @param userId the updating user ID
     * @return the new DocumentVersion record
     */
    @Transactional
    public DocumentVersion incrementDocumentVersion(Long hospitalId, String documentType,
                                                     String documentId, String contentUrl,
                                                     Long userId) {
        Long currentHospitalId = securityHelper.getCurrentHospitalId();
        if (currentHospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }

        if (!currentHospitalId.equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        Optional<DocumentVersion> lastVersionOpt = documentVersionRepository
                .findFirstByHospitalIdAndDocumentTypeAndDocumentIdOrderByVersionDesc(
                        hospitalId, documentType, documentId);

        int nextVersion = lastVersionOpt.map(dv -> dv.getVersion() + 1).orElse(1);

        DocumentVersion dv = new DocumentVersion();
        dv.setHospitalId(hospitalId);
        dv.setDocumentType(documentType);
        dv.setDocumentId(documentId);
        dv.setVersion(nextVersion);
        dv.setContentUrl(contentUrl);
        dv.setUpdatedBy(userId);
        dv.setUpdatedAt(LocalDateTime.now());

        DocumentVersion saved = documentVersionRepository.save(dv);
        log.info("Incremented document version for type: {}, ID: {} to version: {} under hospitalId: {}",
                documentType, documentId, nextVersion, hospitalId);
        return saved;
    }
}
