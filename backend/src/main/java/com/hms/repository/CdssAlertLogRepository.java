package com.hms.repository;

import com.hms.entity.CdssAlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CdssAlertLogRepository extends JpaRepository<CdssAlertLog, Long> {
    List<CdssAlertLog> findByIpdAdmissionIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(Long ipdAdmissionId);
    List<CdssAlertLog> findByPatientIdAndHospitalIdOrderByCreatedAtDesc(Long patientId, Long hospitalId);

    boolean existsByHospitalIdAndIpdAdmissionIdAndAlertTypeAndCreatedAtAfter(Long hospitalId, Long ipdAdmissionId, String alertType, java.time.LocalDateTime after);
}
