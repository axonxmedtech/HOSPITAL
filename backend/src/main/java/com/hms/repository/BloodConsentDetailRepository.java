package com.hms.repository;

import com.hms.entity.BloodConsentDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * BloodConsentDetailRepository - Repository interface for BloodConsentDetail.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface BloodConsentDetailRepository extends JpaRepository<BloodConsentDetail, Long> {

    /**
     * Find detail by parent consent ID.
     */
    Optional<BloodConsentDetail> findByConsentId(Long consentId);
}
