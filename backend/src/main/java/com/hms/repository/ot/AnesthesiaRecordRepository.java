package com.hms.repository.ot;

import com.hms.entity.ot.AnesthesiaRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnesthesiaRecordRepository extends JpaRepository<AnesthesiaRecord, Long> {
    List<AnesthesiaRecord> findByHospitalIdAndOtBookingIdOrderByCreatedAtDesc(Long hospitalId, Long otBookingId);
}
