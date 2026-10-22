package com.hms.repository;

import com.hms.entity.IcuCleaningTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IcuCleaningTaskRepository extends JpaRepository<IcuCleaningTask, Long> {
    List<IcuCleaningTask> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
    List<IcuCleaningTask> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status);
}
