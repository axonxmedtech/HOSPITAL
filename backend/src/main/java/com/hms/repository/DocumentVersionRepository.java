package com.hms.repository;

import com.hms.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * DocumentVersionRepository - Repository interface for database CRUD operations on DocumentVersion.
 *
 * @author HMS Team
 * @version Phase-0.8
 */
@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    /**
     * Find all versions for a specific document.
     */
    List<DocumentVersion> findByDocumentTypeAndDocumentIdOrderByVersionDesc(String documentType, String documentId);

    /**
     * Find all versions for a specific document and hospital ID (multi-tenant safe).
     */
    List<DocumentVersion> findByHospitalIdAndDocumentTypeAndDocumentIdOrderByVersionDesc(Long hospitalId, String documentType, String documentId);

    /**
     * Find the latest version of a document for a specific hospital (tenant isolation).
     */
    Optional<DocumentVersion> findFirstByHospitalIdAndDocumentTypeAndDocumentIdOrderByVersionDesc(Long hospitalId, String documentType, String documentId);
}
