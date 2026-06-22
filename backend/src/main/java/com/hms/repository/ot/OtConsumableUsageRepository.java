package com.hms.repository.ot;

import com.hms.entity.ot.OtConsumableUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtConsumableUsageRepository extends JpaRepository<OtConsumableUsage, Long> {
    List<OtConsumableUsage> findByHospitalIdAndOtBookingId(Long hospitalId, Long otBookingId);
}
