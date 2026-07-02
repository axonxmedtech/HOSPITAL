package com.hms.repository;

import com.hms.entity.EmergencyVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmergencyVisitRepository extends JpaRepository<EmergencyVisit, Long> {
    Optional<EmergencyVisit> findByIdAndHospitalId(Long id, Long hospitalId);

    List<EmergencyVisit> findByHospitalIdAndStatusInOrderByArrivalTimeDesc(Long hospitalId, List<String> statuses);

    long countByHospitalId(Long hospitalId);

    List<EmergencyVisit> findByPatientIdAndHospitalIdOrderByArrivalTimeDesc(Long patientId, Long hospitalId);
}
