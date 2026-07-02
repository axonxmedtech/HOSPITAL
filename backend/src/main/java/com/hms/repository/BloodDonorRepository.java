package com.hms.repository;

import com.hms.entity.BloodDonor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BloodDonorRepository extends JpaRepository<BloodDonor, Long> {
    Optional<BloodDonor> findByIdAndHospitalId(Long id, Long hospitalId);

    List<BloodDonor> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);

    long countByHospitalId(Long hospitalId);
}
