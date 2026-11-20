package com.hms.repository;

import com.hms.entity.HospitalSidebarSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HospitalSidebarSettingRepository extends JpaRepository<HospitalSidebarSetting, Long> {
    List<HospitalSidebarSetting> findByHospitalId(Long hospitalId);

    Optional<HospitalSidebarSetting> findByHospitalIdAndRole(Long hospitalId, String role);
}
