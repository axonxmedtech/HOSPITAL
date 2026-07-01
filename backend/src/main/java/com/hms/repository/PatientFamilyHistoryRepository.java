package com.hms.repository;

import com.hms.entity.PatientFamilyHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * PatientFamilyHistoryRepository - Repository interface for PatientFamilyHistory.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientFamilyHistoryRepository extends JpaRepository<PatientFamilyHistory, Long> {

    /**
     * Find history for a specific patient, tenant-isolated.
     */
    List<PatientFamilyHistory> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);
}
