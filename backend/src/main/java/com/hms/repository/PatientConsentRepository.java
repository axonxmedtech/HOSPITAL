package com.hms.repository;

import com.hms.entity.PatientConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * PatientConsentRepository - Repository interface for database CRUD operations on PatientConsent.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientConsentRepository extends JpaRepository<PatientConsent, Long> {

    /**
     * Find active consents for a specific patient, tenant-isolated.
     */
    List<PatientConsent> findByHospitalIdAndPatientIdAndIsDeletedFalse(Long hospitalId, Long patientId);

    /**
     * Find consents for a specific admission, tenant-isolated.
     */
    List<PatientConsent> findByHospitalIdAndAdmissionIdAndIsDeletedFalse(Long hospitalId, Long admissionId);

    /**
     * Find the active consent by type and admission, tenant-isolated.
     */
    Optional<PatientConsent> findFirstByHospitalIdAndAdmissionIdAndConsentTypeAndStatusNotInAndIsDeletedFalse(
            Long hospitalId, Long admissionId, String consentType, List<String> excludedStatuses);
}
