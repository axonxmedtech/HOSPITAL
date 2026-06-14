package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.PurchaseInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {
    Page<PurchaseInvoice> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId, Pageable pageable);
    Optional<PurchaseInvoice> findByIdAndHospitalId(Long id, Long hospitalId);
}
