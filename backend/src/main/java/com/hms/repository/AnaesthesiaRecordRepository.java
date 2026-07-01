package com.hms.repository;

import com.hms.entity.AnaesthesiaRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnaesthesiaRecordRepository extends JpaRepository<AnaesthesiaRecord, Long> {
    Optional<AnaesthesiaRecord> findByOtBookingIdAndHospitalId(Long otBookingId, Long hospitalId);
}
