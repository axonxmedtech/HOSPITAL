package com.hms.repository;

import com.hms.entity.PatientSocialHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * PatientSocialHistoryRepository - Repository interface for PatientSocialHistory.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface PatientSocialHistoryRepository extends JpaRepository<PatientSocialHistory, Long> {

    /**
     * Find history for a specific patient, tenant-isolated.
     */
    List<PatientSocialHistory> findByHospitalIdAndPatientId(Long hospitalId, Long patientId);
}
