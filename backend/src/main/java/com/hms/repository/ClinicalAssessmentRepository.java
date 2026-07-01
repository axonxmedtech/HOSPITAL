package com.hms.repository;

import com.hms.entity.ClinicalAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * ClinicalAssessmentRepository - Repository interface for ClinicalAssessment.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface ClinicalAssessmentRepository extends JpaRepository<ClinicalAssessment, Long> {

    /**
     * Find assessments for a specific patient, tenant-isolated.
     */
    List<ClinicalAssessment> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);

    /**
     * Find active assessment for admission.
     */
    Optional<ClinicalAssessment> findFirstByHospitalIdAndAdmissionIdAndStatusNotInOrderByVersionDesc(
            Long hospitalId, Long admissionId, List<String> excludedStatuses);
}
