package com.hms.repository;

import com.hms.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface WardRepository extends JpaRepository<Ward, Long> {
    List<Ward> findByHospitalId(Long hospitalId);
    Optional<Ward> findByWardIdAndHospitalId(Long wardId, Long hospitalId);

    List<Ward> findByHospitalIdAndInchargeNurseId(Long hospitalId, Long inchargeNurseId);

    /**
     * General wards only: excludes wards that have been registered as dedicated ICU wards in icu_wards.
     */
    @Query("SELECT w FROM Ward w WHERE w.hospitalId = :hospitalId AND NOT EXISTS (SELECT 1 FROM IcuWard iw WHERE iw.wardId = w.wardId AND iw.hospitalId = :hospitalId)")
    List<Ward> findGeneralWardsByHospitalId(@Param("hospitalId") Long hospitalId);
}
