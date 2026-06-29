package com.hms.repository;

import com.hms.entity.LabTechnician;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/**
 * LabTechnicianRepository — Data access for LabTechnician profile records.
 * The custom_id sequence query drives the LT1, LT2, ... pattern.
 */
public interface LabTechnicianRepository extends JpaRepository<LabTechnician, Long> {

    Optional<LabTechnician> findByPublicId(String publicId);

    Page<LabTechnician> findByHospitalIdAndIsActiveTrue(Long hospitalId, Pageable pageable);

    Optional<LabTechnician> findByEmailAndIsActiveTrue(String email);

    /**
     * Returns the highest sequential number already used by this hospital's lab technicians.
     * Used to derive the next LT{n} custom_id.
     */
    @Query("SELECT MAX(CAST(SUBSTRING(lt.customId, 3) AS int)) FROM LabTechnician lt " +
           "WHERE lt.hospitalId = :hospitalId AND lt.customId IS NOT NULL")
    Integer findMaxLabTechSequence(@Param("hospitalId") Long hospitalId);
}
