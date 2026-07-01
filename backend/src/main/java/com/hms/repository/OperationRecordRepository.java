package com.hms.repository;

import com.hms.entity.OperationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperationRecordRepository extends JpaRepository<OperationRecord, Long> {
    Optional<OperationRecord> findByOtBookingIdAndHospitalId(Long otBookingId, Long hospitalId);
}
