package com.hms.repository;

import com.hms.entity.IpdBillingSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IpdBillingSettingRepository extends JpaRepository<IpdBillingSetting, Long> {
    Optional<IpdBillingSetting> findByHospitalId(Long hospitalId);
}
