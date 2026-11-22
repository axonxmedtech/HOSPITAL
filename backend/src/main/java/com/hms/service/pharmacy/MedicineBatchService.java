package com.hms.service.pharmacy;

import com.hms.entity.pharmacy.InventoryTransaction;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.repository.pharmacy.InventoryTransactionRepository;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class MedicineBatchService {

    private static final String BATCH_NOT_FOUND = "Batch not found";
    private static final String AUDIT_ENTITY = "MEDICINE_BATCH";
    private static final String BATCH = "Batch ";
    private static final String RETURN_NOTE = "Return note ";

    @Autowired private com.hms.service.RealtimeNotifier notifier;

    @Autowired
    private MedicineBatchRepository repository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private PharmacyAuditTrail auditTrail;

    @Autowired
    private com.hms.repository.pharmacy.SupplierRepository supplierRepository;

    @Autowired
    private com.hms.repository.pharmacy.SupplierReturnNoteRepository returnNoteRepository;

    @Autowired
    private PharmacyInvoiceNumberService numberService;

    public Page<MedicineBatch> getInventory(String query, Long categoryId, Pageable pageable) {
        Long hid = securityHelper.getCurrentHospitalId();
        Long branchId = securityHelper.getCurrentBranchId();

        if (categoryId != null) {
            if (query != null && !query.trim().isEmpty()) {
                return repository.searchInventoryWithCategory(hid, branchId, query, categoryId, pageable);
            }
            return repository.findScopedByCategory(hid, branchId, categoryId, pageable);
        }

        if (query != null && !query.trim().isEmpty()) {
            return repository.searchInventory(hid, branchId, query, pageable);
        }
        return repository.findScopedInventory(hid, branchId, pageable);
    }

    public Page<MedicineBatch> getLowStockInventory(Pageable pageable) {
        Long hid = securityHelper.getCurrentHospitalId();
        Long branchId = securityHelper.getCurrentBranchId();
        Page<MedicineBatch> page = repository.findLowStock(hid, branchId, pageable);
        for (MedicineBatch b : page.getContent()) {
            java.math.BigDecimal totalQty = repository.sumCurrentQuantityByMedicineId(hid, branchId, b.getMedicineId());
            if (totalQty == null) totalQty = java.math.BigDecimal.ZERO;
            b.setCurrentQuantity(totalQty);
        }
        return page;
    }

    public Page<MedicineBatch> getExpiringInventory(Integer daysThreshold, Pageable pageable) {
        LocalDate dateLimit = LocalDate.now().plusDays(daysThreshold != null ? daysThreshold : 30);
        return repository.findExpiringSoon(securityHelper.getCurrentHospitalId(),
                securityHelper.getCurrentBranchId(), dateLimit, pageable);
    }

    @org.springframework.transaction.annotation.Transactional
    public MedicineBatch createManualBatch(MedicineBatch batch) {
        if (batch.getMedicineId() == null) {
            throw new IllegalArgumentException("Medicine ID is required");
        }
        if (batch.getBatchNumber() == null || batch.getBatchNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Batch number is required");
        }
        if (batch.getExpiryDate() == null) {
            throw new IllegalArgumentException("Expiry date is required");
        }
        if (batch.getExpiryDate().isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Expiry date cannot be in the past");
        }
        if (batch.getManufacturingDate() != null && batch.getManufacturingDate().isAfter(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Manufacturing date cannot be in the future");
        }
        if (batch.getManufacturingDate() != null && batch.getExpiryDate() != null && batch.getManufacturingDate().isAfter(batch.getExpiryDate())) {
            throw new IllegalArgumentException("Manufacturing date cannot be after expiry date");
        }
        if (batch.getMrp() == null || batch.getMrp().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("MRP must be greater than zero");
        }
        if (batch.getPurchaseRate() == null || batch.getPurchaseRate().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase rate must be greater than zero");
        }
        if (batch.getSellingPrice() == null || batch.getSellingPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Selling price must be greater than zero");
        }
        if (batch.getSellingPrice().compareTo(batch.getMrp()) > 0) {
            throw new IllegalArgumentException("Selling price cannot exceed MRP");
        }
        if (batch.getPurchaseRate().compareTo(batch.getMrp()) > 0) {
            throw new IllegalArgumentException("Purchase rate cannot exceed MRP");
        }
        if (batch.getCurrentQuantity() == null || batch.getCurrentQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Current quantity must be greater than zero");
        }
        if (batch.getGstPercentage() != null && (batch.getGstPercentage().compareTo(java.math.BigDecimal.ZERO) < 0 || batch.getGstPercentage().compareTo(java.math.BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("GST percentage must be between 0% and 100%");
        }
 
        Long hid = securityHelper.getCurrentHospitalId();
        batch.setHospitalId(hid);
        batch.setBranchId(securityHelper.getCurrentBranchId());
        batch.setStatus("ACTIVE");
        
        // Ensure we don't have a duplicate batch for the same medicine and number
        java.util.Optional<MedicineBatch> existing = repository.findByHospitalIdAndMedicineIdAndBatchNumber(
            hid, securityHelper.getCurrentBranchId(), batch.getMedicineId(), batch.getBatchNumber());
        
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Batch with this number already exists for this medicine. Please use adjustment instead.");
        }
        
        MedicineBatch saved = repository.save(batch);

        // Create audit transaction
        InventoryTransaction tx = new InventoryTransaction();
        tx.setHospitalId(hid);
        tx.setBranchId(securityHelper.getCurrentBranchId());
        tx.setMedicineBatchId(saved.getId());
        tx.setTransactionType("OPENING_STOCK");
        tx.setQuantity(saved.getCurrentQuantity());
        tx.setQuantityBefore(java.math.BigDecimal.ZERO);
        tx.setQuantityAfter(saved.getCurrentQuantity());
        tx.setRemarks(batch.getRemarks() != null ? batch.getRemarks() : "Opening Stock");
        tx.setCreatedBy(securityHelper.getCurrentUserId());
        transactionRepository.save(tx);

        // New stock: every inventory screen in the pharmacy should show it without a reload.
        notifier.refresh(hid);
        return saved;
    }

    public java.util.List<MedicineBatch> searchAvailableBatchesFEFO(String query) {
        Long hid = securityHelper.getCurrentHospitalId();
        return repository.searchAvailableBatchesFEFO(hid, securityHelper.getCurrentBranchId(), query != null ? query.trim() : "");
    }

    @org.springframework.transaction.annotation.Transactional
    public MedicineBatch blockBatch(Long id) {
        Long hid = securityHelper.getCurrentHospitalId();
        MedicineBatch batch = repository.findByIdAndHospitalIdForUpdate(id, hid, securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(BATCH_NOT_FOUND));
        batch.setStatus("BLOCKED");
        MedicineBatch saved = repository.save(batch);
        auditTrail.write("BATCH_BLOCKED", AUDIT_ENTITY, batch.getBatchNumber(),
                BATCH + batch.getBatchNumber() + " blocked from sale (" + plain(batch.getCurrentQuantity()) + " in stock)");
        notifier.refresh(hid);
        return saved;
    }

    /** Puts a blocked batch back on sale, with the reason recorded in the audit log. */
    @org.springframework.transaction.annotation.Transactional
    public MedicineBatch unblockBatch(Long id, String reason) {
        if (reason == null || reason.trim().length() < 3) {
            throw new IllegalArgumentException("Give a reason for putting this batch back on sale");
        }
        Long hid = securityHelper.getCurrentHospitalId();
        MedicineBatch batch = repository.findByIdAndHospitalIdForUpdate(id, hid, securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(BATCH_NOT_FOUND));
        if (!"BLOCKED".equalsIgnoreCase(batch.getStatus())) {
            throw new IllegalArgumentException("Only a blocked batch can be unblocked (this one is "
                    + (batch.getStatus() == null ? "active" : batch.getStatus().toLowerCase(java.util.Locale.ROOT)) + ")");
        }
        batch.setStatus("ACTIVE");
        MedicineBatch saved = repository.save(batch);
        auditTrail.write("BATCH_UNBLOCKED", AUDIT_ENTITY, batch.getBatchNumber(),
                BATCH + batch.getBatchNumber() + " put back on sale (" + plain(batch.getCurrentQuantity()) + " in stock)",
                reason.trim());
        notifier.refresh(hid);
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public MedicineBatch disposeBatch(Long id, String remarks) {
        Long hid = securityHelper.getCurrentHospitalId();
        MedicineBatch batch = repository.findByIdAndHospitalIdForUpdate(id, hid, securityHelper.getCurrentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(BATCH_NOT_FOUND));
        
        java.math.BigDecimal qtyBefore = batch.getCurrentQuantity();
        if (qtyBefore == null) qtyBefore = java.math.BigDecimal.ZERO;

        if (qtyBefore.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // Write off the remaining stock to 0
            batch.setCurrentQuantity(java.math.BigDecimal.ZERO);
            
            InventoryTransaction tx = new InventoryTransaction();
            tx.setHospitalId(hid);
            tx.setBranchId(securityHelper.getCurrentBranchId());
            tx.setMedicineBatchId(batch.getId());
            tx.setTransactionType("ADJUSTMENT");
            tx.setQuantity(qtyBefore.negate());
            tx.setQuantityBefore(qtyBefore);
            tx.setQuantityAfter(java.math.BigDecimal.ZERO);
            tx.setReferenceType("STOCK_DISPOSAL");
            tx.setRemarks(remarks != null ? remarks : "Disposed due to expiry");
            tx.setCreatedBy(securityHelper.getCurrentUserId());
            transactionRepository.save(tx);
        }

        batch.setStatus("DISPOSED");
        MedicineBatch saved = repository.save(batch);
        auditTrail.write("BATCH_DISPOSED", AUDIT_ENTITY, batch.getBatchNumber(),
                BATCH + batch.getBatchNumber() + " disposed, " + plain(qtyBefore) + " units written off",
                remarks != null ? remarks : "Disposed due to expiry");
        notifier.refresh(securityHelper.getCurrentHospitalId());
        return saved;
    }

    /**
     * Sends medicine back to a supplier and issues a numbered return note (SR/26-27/00001) listing
     * what went back and the amount claimed. The supplier must be one of this pharmacy's; the
     * claim rate may be lower than the purchase rate (e.g. near-expiry) but never higher.
     */
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> processSupplierReturn(Long supplierId, java.util.List<java.util.Map<String, Object>> items) {
        Long hid = securityHelper.getCurrentHospitalId();
        Long branchId = securityHelper.getCurrentBranchId();
        Long userId = securityHelper.getCurrentUserId();
        if (supplierId == null) {
            throw new IllegalArgumentException("Choose the supplier the medicine goes back to");
        }
        com.hms.entity.pharmacy.Supplier supplier = supplierRepository.findByIdScoped(supplierId, hid, branchId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found in this pharmacy"));
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Add at least one batch to return");
        }

        com.hms.entity.pharmacy.SupplierReturnNote note = new com.hms.entity.pharmacy.SupplierReturnNote();
        note.setHospitalId(hid);
        note.setBranchId(branchId);
        note.setSupplierId(supplier.getId());
        note.setCreatedBy(userId);
        note.setNoteNumber(numberService.nextReturnNote(hid, branchId));
        java.math.BigDecimal totalClaimed = java.math.BigDecimal.ZERO;

        for (java.util.Map<String, Object> item : items) {
            Long batchId = Long.valueOf(item.get("medicineBatchId").toString());
            java.math.BigDecimal qtyToReturn = new java.math.BigDecimal(item.get("quantityToReturn").toString());
            if (qtyToReturn.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Return quantity must be positive");
            }
            MedicineBatch batch = repository.findByIdAndHospitalIdForUpdate(batchId, hid, branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found or unauthorized"));
            if (batch.getCurrentQuantity().compareTo(qtyToReturn) < 0) {
                throw new IllegalArgumentException("Insufficient stock in batch " + batch.getBatchNumber() + " to return. Available: " + batch.getCurrentQuantity());
            }
            java.math.BigDecimal rate = claimRate(item.get("returnRate"), batch);
            java.math.BigDecimal amount = qtyToReturn.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
            totalClaimed = totalClaimed.add(amount);

            java.math.BigDecimal qtyBefore = batch.getCurrentQuantity();
            batch.setCurrentQuantity(qtyBefore.subtract(qtyToReturn));
            repository.save(batch);

            com.hms.entity.pharmacy.SupplierReturnNoteItem line = new com.hms.entity.pharmacy.SupplierReturnNoteItem();
            line.setMedicineBatchId(batch.getId());
            line.setBatchNumber(batch.getBatchNumber());
            line.setMedicineName(batch.getMedicine() != null ? batch.getMedicine().getMedicineName() : null);
            line.setQuantity(qtyToReturn);
            line.setRate(rate);
            line.setAmount(amount);
            note.getItems().add(line);

            InventoryTransaction tx = new InventoryTransaction();
            tx.setHospitalId(hid);
            tx.setBranchId(branchId);
            tx.setMedicineBatchId(batch.getId());
            tx.setTransactionType("RETURN");
            tx.setQuantity(qtyToReturn.negate()); // Negative because stock leaves inventory
            tx.setQuantityBefore(qtyBefore);
            tx.setQuantityAfter(batch.getCurrentQuantity());
            tx.setReferenceType("SUPPLIER_RETURN");
            tx.setRemarks(RETURN_NOTE + note.getNoteNumber() + " to " + supplier.getSupplierName() + " @ rate " + rate);
            tx.setCreatedBy(userId);
            transactionRepository.save(tx);
        }
        note.setTotalClaimed(totalClaimed);
        com.hms.entity.pharmacy.SupplierReturnNote saved = returnNoteRepository.save(note);

        auditTrail.write("SUPPLIER_RETURN", AUDIT_ENTITY, saved.getNoteNumber(),
                RETURN_NOTE + saved.getNoteNumber() + " to " + supplier.getSupplierName() + ": " + items.size()
                        + " line(s), claim ₹" + totalClaimed.toPlainString());
        notifier.refresh(hid);

        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("status", "SUCCESS");
        res.put("noteNumber", saved.getNoteNumber());
        res.put("totalClaimed", totalClaimed);
        res.put("message", RETURN_NOTE + saved.getNoteNumber() + " issued to " + supplier.getSupplierName());
        return res;
    }

    /** The batch's purchase rate, or a lower agreed rate; never more than was paid for it. */
    private static java.math.BigDecimal claimRate(Object requested, MedicineBatch batch) {
        java.math.BigDecimal purchaseRate = batch.getPurchaseRate() != null ? batch.getPurchaseRate() : java.math.BigDecimal.ZERO;
        if (requested == null || requested.toString().trim().isEmpty()) {
            return purchaseRate;
        }
        java.math.BigDecimal rate = new java.math.BigDecimal(requested.toString());
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("Return rate cannot be negative");
        }
        if (rate.compareTo(purchaseRate) > 0) {
            throw new IllegalArgumentException("Return rate cannot be more than the purchase rate (₹" + purchaseRate.toPlainString() + ")");
        }
        return rate;
    }

    private static String plain(java.math.BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}

