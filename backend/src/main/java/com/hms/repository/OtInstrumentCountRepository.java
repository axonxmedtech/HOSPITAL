package com.hms.repository;

import com.hms.entity.OtInstrumentCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtInstrumentCountRepository extends JpaRepository<OtInstrumentCount, Long> {
    Optional<OtInstrumentCount> findByOtBookingIdAndHospitalId(Long otBookingId, Long hospitalId);
}
