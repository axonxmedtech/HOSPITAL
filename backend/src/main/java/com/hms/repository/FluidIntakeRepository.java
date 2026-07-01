package com.hms.repository;

import com.hms.entity.FluidIntake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FluidIntakeRepository - Repository interface for FluidIntake.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Repository
public interface FluidIntakeRepository extends JpaRepository<FluidIntake, Long> {

    /**
     * Find intake logs for admission.
     */
    List<FluidIntake> findByHospitalIdAndAdmissionIdOrderByRecordedTimeDesc(Long hospitalId, Long admissionId);

    /**
     * Find intake logs for admission in a time window.
     */
    List<FluidIntake> findByHospitalIdAndAdmissionIdAndRecordedTimeBetween(Long hospitalId, Long admissionId, LocalDateTime start, LocalDateTime end);

    List<FluidIntake> findByAdmissionIdAndRecordedTimeBetween(Long admissionId, LocalDateTime start, LocalDateTime end);

    boolean existsByHospitalIdAndSourceRef(Long hospitalId, Long sourceRef);
}
