package com.hms.repository;

import com.hms.entity.TrainingMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingMasterRepository extends JpaRepository<TrainingMaster, Long> {
    Optional<TrainingMaster> findByIdAndHospitalId(Long id, Long hospitalId);

    List<TrainingMaster> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
