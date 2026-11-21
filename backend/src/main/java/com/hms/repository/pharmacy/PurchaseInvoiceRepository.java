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

    // Branch-scoped (null branchId => whole hospital, for admin merged view).
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PurchaseInvoice p WHERE p.hospitalId = :hid AND (:branchId IS NULL OR p.branchId = :branchId) ORDER BY p.createdAt DESC")
    Page<PurchaseInvoice> findScopedHistory(@org.springframework.data.repository.query.Param("hid") Long hid, @org.springframework.data.repository.query.Param("branchId") Long branchId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PurchaseInvoice p LEFT JOIN FETCH p.items WHERE p.id = :id AND p.hospitalId = :hid AND (:branchId IS NULL OR p.branchId = :branchId)")
    Optional<PurchaseInvoice> findByIdScoped(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("hid") Long hid, @org.springframework.data.repository.query.Param("branchId") Long branchId);

    /** The same supplier invoice entered twice would add its stock twice. Cancelled ones don't count. */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(p) > 0 FROM PurchaseInvoice p WHERE p.hospitalId = :hid "
            + "AND p.supplierId = :supplierId AND LOWER(TRIM(p.invoiceNumber)) = LOWER(TRIM(:invoiceNumber)) "
            + "AND (p.postingStatus IS NULL OR p.postingStatus <> 'CANCELLED') AND (:exceptId IS NULL OR p.id <> :exceptId)")
    boolean invoiceNumberTaken(@org.springframework.data.repository.query.Param("hid") Long hid,
            @org.springframework.data.repository.query.Param("supplierId") Long supplierId,
            @org.springframework.data.repository.query.Param("invoiceNumber") String invoiceNumber,
            @org.springframework.data.repository.query.Param("exceptId") Long exceptId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PurchaseInvoice p WHERE p.id = :id AND p.hospitalId = :hid "
            + "AND (:branchId IS NULL OR p.branchId = :branchId)")
    java.util.Optional<PurchaseInvoice> lockScoped(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("hid") Long hid,
            @org.springframework.data.repository.query.Param("branchId") Long branchId);
}
