package com.hms.repository;

import com.hms.entity.TrainingCertification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingCertificationRepository extends JpaRepository<TrainingCertification, Long> {
    Optional<TrainingCertification> findByHospitalIdAndEmployeeIdAndTrainingMasterId(Long hospitalId, Long employeeId, Long trainingMasterId);

    List<TrainingCertification> findByHospitalIdAndEmployeeId(Long hospitalId, Long employeeId);

    List<TrainingCertification> findByHospitalIdOrderByIdDesc(Long hospitalId);
}
