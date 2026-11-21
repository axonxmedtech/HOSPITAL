package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.PurchasePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchasePaymentRepository extends JpaRepository<PurchasePayment, Long> {
    List<PurchasePayment> findByPurchaseInvoiceIdAndHospitalIdOrderByPaidOnAscIdAsc(Long purchaseInvoiceId, Long hospitalId);
}
