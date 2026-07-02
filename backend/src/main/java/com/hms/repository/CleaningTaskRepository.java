package com.hms.repository;

import com.hms.entity.CleaningTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CleaningTaskRepository extends JpaRepository<CleaningTask, Long> {
    Optional<CleaningTask> findByIdAndHospitalId(Long id, Long hospitalId);

    List<CleaningTask> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
