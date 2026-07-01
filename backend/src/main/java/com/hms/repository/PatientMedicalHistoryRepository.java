package com.hms.repository;

import com.hms.entity.PatientMedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * PatientMedicalHistoryRepository - Repository interface for PatientMedicalHistory.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientMedicalHistoryRepository extends JpaRepository<PatientMedicalHistory, Long> {

    /**
     * Find history for a specific patient, tenant-isolated.
     */
    List<PatientMedicalHistory> findByHospitalIdAndPatientIdAndIsActiveTrue(Long hospitalId, Long patientId);
}
