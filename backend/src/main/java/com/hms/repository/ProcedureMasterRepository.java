package com.hms.repository;

import com.hms.entity.ProcedureMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProcedureMasterRepository extends JpaRepository<ProcedureMaster, Long> {
    List<ProcedureMaster> findByHospitalIdAndIsActiveTrueOrderByProcedureNameAsc(Long hospitalId);

    @Query("SELECT p FROM ProcedureMaster p WHERE p.hospitalId = :hospitalId AND p.isActive = true " +
           "AND (LOWER(p.procedureName) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(p.department) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<ProcedureMaster> searchByHospital(@Param("hospitalId") Long hospitalId, @Param("q") String q);
}
