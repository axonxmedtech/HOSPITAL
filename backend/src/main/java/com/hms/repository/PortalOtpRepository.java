package com.hms.repository;

import com.hms.entity.PortalOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortalOtpRepository extends JpaRepository<PortalOtp, Long> {
    Optional<PortalOtp> findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(Long hospitalId, String mobile);
}
