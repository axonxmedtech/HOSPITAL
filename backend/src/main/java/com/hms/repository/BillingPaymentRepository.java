package com.hms.repository;

import com.hms.entity.BillingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillingPaymentRepository extends JpaRepository<BillingPayment, Long> {
    List<BillingPayment> findByBillingId(Long billingId);
}
