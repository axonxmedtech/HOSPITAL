package com.hms.repository;

import com.hms.entity.ExecutiveAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExecutiveAlertRepository extends JpaRepository<ExecutiveAlert, Long> {
    Optional<ExecutiveAlert> findByIdAndHospitalId(Long id, Long hospitalId);

    long countByHospitalIdAndStatus(Long hospitalId, String status);

    long countByHospitalIdAndStatusAndSeverity(Long hospitalId, String status, String severity);

    List<ExecutiveAlert> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
