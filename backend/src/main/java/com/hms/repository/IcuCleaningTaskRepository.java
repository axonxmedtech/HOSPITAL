package com.hms.repository;

import com.hms.entity.IcuCleaningTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IcuCleaningTaskRepository extends JpaRepository<IcuCleaningTask, Long> {
    List<IcuCleaningTask> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
    List<IcuCleaningTask> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status);
    /** Newest tasks first, at most one page: the task list grows with every ICU bed change. */
    List<IcuCleaningTask> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId, org.springframework.data.domain.Pageable limit);
    List<IcuCleaningTask> findByHospitalIdAndStatusOrderByCreatedAtDesc(Long hospitalId, String status, org.springframework.data.domain.Pageable limit);
    List<IcuCleaningTask> findByHospitalIdAndBedIdAndStatusIn(Long hospitalId, Long bedId, java.util.Collection<String> statuses);
}
