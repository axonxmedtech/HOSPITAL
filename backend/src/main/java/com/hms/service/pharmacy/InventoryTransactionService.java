package com.hms.service.pharmacy;

import com.hms.entity.pharmacy.InventoryTransaction;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.repository.pharmacy.InventoryTransactionRepository;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class InventoryTransactionService {

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Transactional
    public InventoryTransaction recordAdjustment(Long batchId, BigDecimal adjustmentQty, String remarks) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        
        // Use Pessimistic Locking to prevent race conditions during manual adjustment
        MedicineBatch batch = batchRepository.findByIdAndHospitalIdForUpdate(batchId, hospitalId)
                .orElseThrow(() -> new RuntimeException("Medicine batch not found or unauthorized"));

        BigDecimal qtyBefore = batch.getCurrentQuantity() != null ? batch.getCurrentQuantity() : BigDecimal.ZERO;
        BigDecimal qtyAfter = qtyBefore.add(adjustmentQty);

        if (qtyAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Inventory cannot be negative");
        }

        batch.setCurrentQuantity(qtyAfter);
        batchRepository.save(batch);

        InventoryTransaction tx = new InventoryTransaction();
        tx.setHospitalId(hospitalId);
        tx.setMedicineBatchId(batchId);
        tx.setTransactionType("ADJUSTMENT");
        tx.setQuantity(adjustmentQty);
        tx.setQuantityBefore(qtyBefore);
        tx.setQuantityAfter(qtyAfter);
        tx.setReferenceType("STOCK_ADJUSTMENT");
        tx.setRemarks(remarks);
        tx.setCreatedBy(securityHelper.getCurrentUserId());

        return transactionRepository.save(tx);
    }

    public Page<InventoryTransaction> getTransactionHistory(Long batchId, Pageable pageable) {
        // Verify hospital access
        Long hospitalId = securityHelper.getCurrentHospitalId();
        MedicineBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Medicine batch not found"));
        
        if (!batch.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Unauthorized access to medicine batch");
        }

        return transactionRepository.findByMedicineBatchIdOrderByCreatedAtDesc(batchId, pageable);
    }
}
