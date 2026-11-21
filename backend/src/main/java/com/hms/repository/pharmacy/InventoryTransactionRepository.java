package com.hms.repository.pharmacy;

import com.hms.entity.pharmacy.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    Page<InventoryTransaction> findByMedicineBatchIdOrderByCreatedAtDesc(Long medicineBatchId, Pageable pageable);

    /** Returns (patient and supplier) of one pharmacy, and of one branch when :branchId is set. */
    @org.springframework.data.jpa.repository.Query("SELECT t FROM InventoryTransaction t WHERE t.hospitalId = :hospitalId "
            + "AND t.transactionType = :type AND (:branchId IS NULL OR t.branchId = :branchId) ORDER BY t.createdAt DESC")
    Page<InventoryTransaction> findScopedByType(@org.springframework.data.repository.query.Param("hospitalId") Long hospitalId,
            @org.springframework.data.repository.query.Param("branchId") Long branchId,
            @org.springframework.data.repository.query.Param("type") String transactionType, Pageable pageable);
}
