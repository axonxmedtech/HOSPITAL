package com.hms.repository;

import com.hms.entity.ChargeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeMasterRepository extends JpaRepository<ChargeMaster, Long> {

    List<ChargeMaster> findByHospitalId(Long hospitalId);

    Optional<ChargeMaster> findByHospitalIdAndId(Long hospitalId, Long id);

    Optional<ChargeMaster> findByHospitalIdAndServiceCode(Long hospitalId, String serviceCode);

    Optional<ChargeMaster> findByHospitalIdAndServiceCodeAndIsActiveTrue(Long hospitalId, String serviceCode);

    Optional<ChargeMaster> findByHospitalIdAndNameAndIsActiveTrue(Long hospitalId, String name);
}
