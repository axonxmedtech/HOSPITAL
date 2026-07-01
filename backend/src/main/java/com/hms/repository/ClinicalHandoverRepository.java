package com.hms.repository;

import com.hms.entity.ClinicalHandover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicalHandoverRepository extends JpaRepository<ClinicalHandover, Long> {
    Optional<ClinicalHandover> findByOtBookingIdAndHospitalId(Long otBookingId, Long hospitalId);
}
