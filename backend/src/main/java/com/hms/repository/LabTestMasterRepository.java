package com.hms.repository;

import com.hms.entity.LabTestMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface LabTestMasterRepository extends JpaRepository<LabTestMaster, Long> {
    List<LabTestMaster> findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(Long hospitalId);

    @Query("SELECT t FROM LabTestMaster t WHERE t.hospitalId = :hospitalId AND t.isActive = true " +
           "AND (LOWER(t.testName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.testCode) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<LabTestMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);

    boolean existsByHospitalIdAndIsActiveTrue(Long hospitalId);
}
