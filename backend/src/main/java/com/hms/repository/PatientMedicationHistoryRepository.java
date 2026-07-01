package com.hms.repository;

import com.hms.entity.PatientMedicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * PatientMedicationHistoryRepository - Repository interface for PatientMedicationHistory.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientMedicationHistoryRepository extends JpaRepository<PatientMedicationHistory, Long> {

    /**
     * Find history for a specific patient, tenant-isolated.
     */
    List<PatientMedicationHistory> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);
}
