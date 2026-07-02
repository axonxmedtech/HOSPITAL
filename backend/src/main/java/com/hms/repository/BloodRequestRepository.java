package com.hms.repository;

import com.hms.entity.BloodRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {
    Optional<BloodRequest> findByIdAndHospitalId(Long id, Long hospitalId);

    List<BloodRequest> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
}
