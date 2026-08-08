package com.hms.repository;

import com.hms.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {
    List<Ward> findByHospitalId(Long hospitalId);

    List<Ward> findByHospitalIdAndWardType(Long hospitalId, com.hms.entity.WardType wardType);

    List<Ward> findByHospitalIdAndWardTypeIn(
            Long hospitalId, java.util.Collection<com.hms.entity.WardType> wardTypes);

    java.util.List<Ward> findByHospitalIdAndInchargeNurseId(Long hospitalId, Long inchargeNurseId);
}
