package com.hms.repository;

import com.hms.entity.BillingRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingRefundRepository extends JpaRepository<BillingRefund, Long> {
    Optional<BillingRefund> findByIdAndHospitalId(Long id, Long hospitalId);

    List<BillingRefund> findByHospitalIdOrderByRequestedAtDesc(Long hospitalId);

    List<BillingRefund> findByHospitalIdAndBillingId(Long hospitalId, Long billingId);
}
