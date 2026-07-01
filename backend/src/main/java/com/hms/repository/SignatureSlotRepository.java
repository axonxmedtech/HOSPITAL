package com.hms.repository;

import com.hms.entity.SignatureSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * SignatureSlotRepository - Repository interface for database CRUD operations on SignatureSlot.
 *
 * @author HMS Team
 * @version Phase-0.8
 */
@Repository
public interface SignatureSlotRepository extends JpaRepository<SignatureSlot, Long> {

    /**
     * Find all signatures for a specific document.
     */
    List<SignatureSlot> findByDocumentTypeAndDocumentIdOrderBySignedAtAsc(String documentType, String documentId);

    /**
     * Find all signatures for a specific document and hospital ID (multi-tenant safe).
     */
    List<SignatureSlot> findByHospitalIdAndDocumentTypeAndDocumentIdOrderBySignedAtAsc(Long hospitalId, String documentType, String documentId);
}
