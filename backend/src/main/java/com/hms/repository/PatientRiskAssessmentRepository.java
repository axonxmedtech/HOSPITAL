package com.hms.repository;

import com.hms.entity.PatientRiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * PatientRiskAssessmentRepository - Repository interface for PatientRiskAssessment.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientRiskAssessmentRepository extends JpaRepository<PatientRiskAssessment, Long> {

    /**
     * Find risk assessments for a specific patient, tenant-isolated.
     */
    List<PatientRiskAssessment> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);

    /**
     * Find risk assessments for a specific admission, tenant-isolated.
     */
    List<PatientRiskAssessment> findByHospitalIdAndAdmissionIdOrderByCreatedAtDesc(Long hospitalId, Long admissionId);

    /**
     * Find latest risk assessment for admission.
     */
    Optional<PatientRiskAssessment> findFirstByHospitalIdAndAdmissionIdAndStatusOrderByCreatedAtDesc(Long hospitalId, Long admissionId, String status);
}
