package com.hms.repository;

import com.hms.entity.MonitoringVitals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * MonitoringVitalsRepository - Repository interface for database CRUD operations on MonitoringVitals.
 *
 * @author HMS Team
 * @version Phase-0.7
 */
@Repository
public interface MonitoringVitalsRepository extends JpaRepository<MonitoringVitals, Long> {

    /**
     * Find vitals logs by IPD admission and specific context (INTRAOP / PACU).
     */
    List<MonitoringVitals> findByIpdAdmissionIdAndContextOrderByRecordedAtAsc(Long ipdAdmissionId, String context);

    /**
     * Find all vitals logs by IPD admission.
     */
    List<MonitoringVitals> findByIpdAdmissionIdOrderByRecordedAtAsc(Long ipdAdmissionId);

    /**
     * Find vitals by hospital ID and IPD admission (security / multi-tenant audit safe).
     */
    List<MonitoringVitals> findByHospitalIdAndIpdAdmissionIdOrderByRecordedAtAsc(Long hospitalId, Long ipdAdmissionId);
}
