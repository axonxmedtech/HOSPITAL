package com.hms.repository;

import com.hms.entity.FluidOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FluidOutputRepository - Repository interface for FluidOutput.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface FluidOutputRepository extends JpaRepository<FluidOutput, Long> {

    /**
     * Find output logs for admission.
     */
    List<FluidOutput> findByHospitalIdAndAdmissionIdOrderByRecordedTimeDesc(Long hospitalId, Long admissionId);

    /**
     * Find output logs for admission in a time window.
     */
    List<FluidOutput> findByHospitalIdAndAdmissionIdAndRecordedTimeBetween(Long hospitalId, Long admissionId, LocalDateTime start, LocalDateTime end);

    List<FluidOutput> findByAdmissionIdAndRecordedTimeBetween(Long admissionId, LocalDateTime start, LocalDateTime end);
}
