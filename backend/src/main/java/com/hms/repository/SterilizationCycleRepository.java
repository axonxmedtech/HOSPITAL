package com.hms.repository;

import com.hms.entity.SterilizationCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SterilizationCycleRepository extends JpaRepository<SterilizationCycle, Long> {
    Optional<SterilizationCycle> findByIdAndHospitalId(Long id, Long hospitalId);

    long countByHospitalId(Long hospitalId);

    List<SterilizationCycle> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
