package com.hms.repository.ot;

import com.hms.entity.ot.ImplantUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImplantUsageRepository extends JpaRepository<ImplantUsage, Long> {
    List<ImplantUsage> findByHospitalIdAndOtBookingId(Long hospitalId, Long otBookingId);
}
