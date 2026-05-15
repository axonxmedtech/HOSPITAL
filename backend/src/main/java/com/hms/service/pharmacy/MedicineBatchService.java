package com.hms.service.pharmacy;

import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

import com.hms.repository.pharmacy.InventoryTransactionRepository;
import com.hms.entity.pharmacy.InventoryTransaction;

@Service
public class MedicineBatchService {

    @Autowired
    private MedicineBatchRepository repository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    public Page<MedicineBatch> getInventory(String query, Long categoryId, Pageable pageable) {
        Long hid = securityHelper.getCurrentHospitalId();
        
        if (categoryId != null) {
            if (query != null && !query.trim().isEmpty()) {
                return repository.searchInventoryWithCategory(hid, query, categoryId, pageable);
            }
            return repository.findByHospitalIdAndMedicine_CategoryId(hid, categoryId, pageable);
        }

        if (query != null && !query.trim().isEmpty()) {
            return repository.searchInventory(hid, query, pageable);
        }
        return repository.findByHospitalId(hid, pageable);
    }

    public Page<MedicineBatch> getLowStockInventory(Pageable pageable) {
        return repository.findLowStock(securityHelper.getCurrentHospitalId(), pageable);
    }

    public Page<MedicineBatch> getExpiringInventory(Integer daysThreshold, Pageable pageable) {
        LocalDate dateLimit = LocalDate.now().plusDays(daysThreshold != null ? daysThreshold : 30);
        return repository.findExpiringSoon(securityHelper.getCurrentHospitalId(), dateLimit, pageable);
    }

    @org.springframework.transaction.annotation.Transactional
    public MedicineBatch createManualBatch(MedicineBatch batch) {
        Long hid = securityHelper.getCurrentHospitalId();
        batch.setHospitalId(hid);
        batch.setStatus("ACTIVE");
        
        // Ensure we don't have a duplicate batch for the same medicine and number
        java.util.Optional<MedicineBatch> existing = repository.findByHospitalIdAndMedicineIdAndBatchNumber(
            hid, batch.getMedicineId(), batch.getBatchNumber());
        
        if (existing.isPresent()) {
            throw new RuntimeException("Batch with this number already exists for this medicine. Please use adjustment instead.");
        }
        
        MedicineBatch saved = repository.save(batch);

        // Create audit transaction
        InventoryTransaction tx = new InventoryTransaction();
        tx.setHospitalId(hid);
        tx.setMedicineBatchId(saved.getId());
        tx.setTransactionType("OPENING_STOCK");
        tx.setQuantity(saved.getCurrentQuantity());
        tx.setQuantityBefore(java.math.BigDecimal.ZERO);
        tx.setQuantityAfter(saved.getCurrentQuantity());
        tx.setRemarks(batch.getRemarks() != null ? batch.getRemarks() : "Opening Stock");
        tx.setCreatedBy(securityHelper.getCurrentUserId());
        transactionRepository.save(tx);

        return saved;
    }
}
