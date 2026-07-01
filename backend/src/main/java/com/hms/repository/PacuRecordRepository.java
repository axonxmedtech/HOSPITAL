package com.hms.repository;

import com.hms.entity.PacuRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PacuRecordRepository extends JpaRepository<PacuRecord, Long> {
    Optional<PacuRecord> findByOtBookingIdAndHospitalId(Long otBookingId, Long hospitalId);
}
