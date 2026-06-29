package com.hms.repository;

import com.hms.entity.RadiologyTechnician;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface RadiologyTechnicianRepository extends JpaRepository<RadiologyTechnician, Long> {

    Optional<RadiologyTechnician> findByPublicId(String publicId);

    Page<RadiologyTechnician> findByHospitalIdAndIsActiveTrue(Long hospitalId, Pageable pageable);

    Optional<RadiologyTechnician> findByEmailAndIsActiveTrue(String email);

    @Query("SELECT MAX(CAST(SUBSTRING(rt.customId, 3) AS int)) FROM RadiologyTechnician rt " +
           "WHERE rt.hospitalId = :hospitalId AND rt.customId IS NOT NULL")
    Integer findMaxRadiologyTechSequence(@Param("hospitalId") Long hospitalId);
}
