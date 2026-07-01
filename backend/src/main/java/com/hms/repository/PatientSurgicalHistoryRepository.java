package com.hms.repository;

import com.hms.entity.PatientSurgicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * PatientSurgicalHistoryRepository - Repository interface for PatientSurgicalHistory.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientSurgicalHistoryRepository extends JpaRepository<PatientSurgicalHistory, Long> {

    /**
     * Find history for a specific patient, tenant-isolated.
     */
    List<PatientSurgicalHistory> findByHospitalIdAndPatientIdOrderBySurgeryYearDesc(Long hospitalId, Long patientId);
}
