package com.hms.repository;

import com.hms.entity.PatientDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * PatientDiagnosisRepository - Repository interface for PatientDiagnosis.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientDiagnosisRepository extends JpaRepository<PatientDiagnosis, Long> {

    /**
     * Find diagnoses for a specific admission, tenant-isolated.
     */
    List<PatientDiagnosis> findByHospitalIdAndAdmissionIdOrderByRecordedAtDesc(Long hospitalId, Long admissionId);

    /**
     * Find diagnoses for a specific patient, tenant-isolated.
     */
    List<PatientDiagnosis> findByHospitalIdAndPatientIdOrderByRecordedAtDesc(Long hospitalId, Long patientId);
}
