package com.hms.service.pharmacy;

import com.hms.entity.pharmacy.InventoryTransaction;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.repository.pharmacy.InventoryTransactionRepository;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class InventoryTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryTransactionService.class);

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private PharmacyAuditTrail auditTrail;

    @Transactional
    public InventoryTransaction recordAdjustment(Long batchId, BigDecimal adjustmentQty, String remarks) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (adjustmentQty == null || adjustmentQty.signum() == 0) {
            throw new IllegalArgumentException("Enter how many units to add or remove");
        }
        if (remarks == null || remarks.trim().length() < 3) {
            throw new IllegalArgumentException("Give a reason for the stock correction (e.g. damaged, physical count)");
        }

        // Use Pessimistic Locking to prevent race conditions during manual adjustment
        MedicineBatch batch = batchRepository.findByIdAndHospitalIdForUpdate(batchId, hospitalId, securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine batch not found or unauthorized"));

        BigDecimal qtyBefore = batch.getCurrentQuantity() != null ? batch.getCurrentQuantity() : BigDecimal.ZERO;
        BigDecimal qtyAfter = qtyBefore.add(adjustmentQty);

        if (qtyAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Inventory cannot be negative");
        }

        batch.setCurrentQuantity(qtyAfter);
        batchRepository.save(batch);

        InventoryTransaction tx = new InventoryTransaction();
        tx.setHospitalId(hospitalId);
        tx.setBranchId(securityHelper.getCurrentBranchId());
        tx.setMedicineBatchId(batchId);
        tx.setTransactionType("ADJUSTMENT");
        tx.setQuantity(adjustmentQty);
        tx.setQuantityBefore(qtyBefore);
        tx.setQuantityAfter(qtyAfter);
        tx.setReferenceType("STOCK_ADJUSTMENT");
        tx.setRemarks(remarks);
        tx.setCreatedBy(securityHelper.getCurrentUserId());

        InventoryTransaction saved = transactionRepository.save(tx);
        auditTrail.record("STOCK_ADJUSTED", "MEDICINE_BATCH", batch.getBatchNumber(),
                "Batch " + batch.getBatchNumber() + ": " + qtyBefore.toPlainString() + " → " + qtyAfter.toPlainString()
                        + " (" + (adjustmentQty.signum() > 0 ? "+" : "") + adjustmentQty.toPlainString() + ")", remarks.trim());
        try { webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}"); } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after inventory adjustment", e);
        }
        return saved;
    }

    /** One batch's stock ledger; a branch login sees only its own batches. */
    public Page<InventoryTransaction> getTransactionHistory(Long batchId, Pageable pageable) {
        batchRepository.findScoped(batchId, securityHelper.getCurrentHospitalId(), securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine batch not found"));
        return transactionRepository.findByMedicineBatchIdOrderByCreatedAtDesc(batchId, pageable);
    }

    public Page<InventoryTransaction> getReturnsHistory(Pageable pageable) {
        return transactionRepository.findScopedByType(securityHelper.getCurrentHospitalId(),
                securityHelper.getCurrentBranchId(), "RETURN", pageable);
    }
}

