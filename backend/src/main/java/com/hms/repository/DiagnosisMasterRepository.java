package com.hms.repository;

import com.hms.entity.DiagnosisMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DiagnosisMasterRepository extends JpaRepository<DiagnosisMaster, Long> {
    List<DiagnosisMaster> findByHospitalIdAndIsActiveTrueOrderByIcdCodeAsc(Long hospitalId);

    @Query("SELECT d FROM DiagnosisMaster d WHERE d.hospitalId = :hospitalId AND d.isActive = true " +
           "AND (LOWER(d.icdCode) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(d.icdDescription) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<DiagnosisMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);

    boolean existsByHospitalIdAndIsActiveTrue(Long hospitalId);
}
