package com.hms.repository;

import com.hms.entity.BloodUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BloodUnitRepository extends JpaRepository<BloodUnit, Long> {
    Optional<BloodUnit> findByIdAndHospitalId(Long id, Long hospitalId);

    List<BloodUnit> findByHospitalIdAndStatusOrderByExpiryDateAsc(Long hospitalId, String status);

    List<BloodUnit> findByHospitalIdAndBloodGroupAndRhTypeAndStatus(
            Long hospitalId, String bloodGroup, String rhType, String status);

    List<BloodUnit> findByHospitalIdAndDonorId(Long hospitalId, Long donorId);

    List<BloodUnit> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
