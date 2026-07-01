package com.hms.repository;

import com.hms.entity.ShiftHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * ShiftHandoverRepository - Repository interface for ShiftHandover.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface ShiftHandoverRepository extends JpaRepository<ShiftHandover, Long> {

    /**
     * Find handovers logged for admission.
     */
    List<ShiftHandover> findByHospitalIdAndAdmissionIdOrderByCreatedAtDesc(Long hospitalId, Long admissionId);
}
