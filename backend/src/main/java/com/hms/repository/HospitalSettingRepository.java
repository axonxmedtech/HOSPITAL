package com.hms.repository;

import com.hms.entity.HospitalSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * HospitalSettingRepository - Data access layer for HospitalSetting entity
 * 
 * Provides database query operations for managing hospital operational settings.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface HospitalSettingRepository extends JpaRepository<HospitalSetting, Long> {

    /**
     * Find settings for a specific hospital by its database ID
     * 
     * @param hospitalId database ID of the hospital
     * @return Optional containing the settings if found
     */
    Optional<HospitalSetting> findByHospitalId(Long hospitalId);
}
