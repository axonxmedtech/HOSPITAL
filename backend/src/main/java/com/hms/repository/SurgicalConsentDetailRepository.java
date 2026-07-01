package com.hms.repository;

import com.hms.entity.SurgicalConsentDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurgicalConsentDetailRepository extends JpaRepository<SurgicalConsentDetail, Long> {
    Optional<SurgicalConsentDetail> findByConsentId(Long consentId);
}
