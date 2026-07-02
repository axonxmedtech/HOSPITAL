package com.hms.repository;

import com.hms.entity.VendorInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorInvoiceRepository extends JpaRepository<VendorInvoice, Long> {
    List<VendorInvoice> findByHospitalId(Long hospitalId);
    Optional<VendorInvoice> findByIdAndHospitalId(Long id, Long hospitalId);
    Optional<VendorInvoice> findByHospitalIdAndVendorIdAndInvoiceNumber(Long hospitalId, Long vendorId, String invoiceNumber);
}
