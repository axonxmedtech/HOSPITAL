package com.hms.repository.ot;

import com.hms.entity.ot.SurgeryStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurgeryStatusLogRepository extends JpaRepository<SurgeryStatusLog, Long> {
    List<SurgeryStatusLog> findByHospitalIdAndOtBookingIdOrderByEventTime(Long hospitalId, Long otBookingId);
}
