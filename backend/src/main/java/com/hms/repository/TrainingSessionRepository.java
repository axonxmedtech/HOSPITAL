package com.hms.repository;

import com.hms.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    Optional<TrainingSession> findByIdAndHospitalId(Long id, Long hospitalId);

    List<TrainingSession> findByHospitalIdOrderBySessionDateDesc(Long hospitalId);
}
