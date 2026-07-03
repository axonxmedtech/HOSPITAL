package com.hms.repository;

import com.hms.entity.PrescriptionPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrescriptionPresetRepository extends JpaRepository<PrescriptionPreset, Long> {
    List<PrescriptionPreset> findByHospitalIdAndIsActiveTrueOrderByDisplayOrderAsc(Long hospitalId);
    Optional<PrescriptionPreset> findByIdAndHospitalId(Long id, Long hospitalId);
}
