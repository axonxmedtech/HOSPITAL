package com.hms.repository;

import com.hms.entity.DrugInteractionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DrugInteractionMasterRepository extends JpaRepository<DrugInteractionMaster, Long> {

    @Query("SELECT d FROM DrugInteractionMaster d WHERE d.hospitalId = :hospitalId " +
           "AND d.isActive = true " +
           "AND (LOWER(d.drugAName) LIKE LOWER(CONCAT('%',:medicine,'%')) " +
           "  OR LOWER(d.drugBName) LIKE LOWER(CONCAT('%',:medicine,'%')))")
    List<DrugInteractionMaster> findInteractionsInvolvingMedicine(
            @Param("hospitalId") Long hospitalId,
            @Param("medicine") String medicine);

    boolean existsByHospitalId(Long hospitalId);
}
