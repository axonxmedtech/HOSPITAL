package com.hms.repository.ot;

import com.hms.entity.ot.OtBilling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface OtBillingRepository extends JpaRepository<OtBilling, Long> {
    List<OtBilling> findByHospitalIdAndOtBookingId(Long hospitalId, Long otBookingId);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM OtBilling b WHERE b.hospitalId = :hospitalId")
    BigDecimal sumRevenue(Long hospitalId);
}
