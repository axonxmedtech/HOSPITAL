package com.hms.repository;

import com.hms.entity.AllergyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AllergyMasterRepository extends JpaRepository<AllergyMaster, Long> {
    List<AllergyMaster> findByHospitalIdAndIsActiveTrueOrderByAllergyNameAsc(Long hospitalId);

    @Query("SELECT a FROM AllergyMaster a WHERE a.hospitalId = :hospitalId AND a.isActive = true " +
           "AND LOWER(a.allergyName) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<AllergyMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);

    boolean existsByHospitalIdAndIsActiveTrue(Long hospitalId);
}
