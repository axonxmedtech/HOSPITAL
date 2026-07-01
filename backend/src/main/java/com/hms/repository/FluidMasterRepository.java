package com.hms.repository;

import com.hms.entity.FluidMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * FluidMasterRepository - Repository interface for FluidMaster.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface FluidMasterRepository extends JpaRepository<FluidMaster, Long> {

    /**
     * Find fluid definitions for a specific hospital (tenant-isolated).
     */
    List<FluidMaster> findByHospitalId(Long hospitalId);
}
