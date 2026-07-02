package com.hms.service.hospital;

import com.hms.entity.DepartmentIndent;
import com.hms.entity.HospitalInventory;
import com.hms.entity.InventoryItem;
import com.hms.entity.StockTransaction;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GeneralInventoryService {

    @Autowired
    private DepartmentIndentRepository indentRepository;

    @Autowired
    private StockTransactionRepository transactionRepository;

    @Autowired
    private HospitalInventoryRepository inventoryRepository;

    @Autowired
    private InventoryItemRepository itemRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private AuditLogService auditLogService;

    // --- Indent Management ---

    public List<DepartmentIndent> getIndents() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return indentRepository.findByHospitalId(hospitalId);
    }

    @Transactional
    public DepartmentIndent raiseIndent(String fromDepartment, Long itemId, BigDecimal quantity) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found: " + itemId));
        if (!item.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access denied: Tenant mismatch");
        }

        DepartmentIndent indent = new DepartmentIndent();
        indent.setHospitalId(hospitalId);
        indent.setFromDepartment(fromDepartment);
        indent.setInventoryItemId(itemId);
        indent.setRequestedQty(quantity);
        indent.setStatus("PENDING");
        indent.setCreatedAt(LocalDateTime.now());

        return indentRepository.save(indent);
    }

    @Transactional
    public DepartmentIndent approveIndent(Long indentId, String signature) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        DepartmentIndent indent = indentRepository.findByIdAndHospitalId(indentId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Indent not found: " + indentId));

        indent.setStatus("APPROVED");
        indent.setApprovedBySig(signature);
        return indentRepository.save(indent);
    }

    // --- Stock Dispensation & FEFO Logic ---

    public List<HospitalInventory> suggestFefo(Long itemId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found"));

        List<HospitalInventory> activeStocks = inventoryRepository.findByNameAndHospitalIdAndIsActiveTrue(item.getName(), hospitalId);

        // Sort by expiry date ascending (nulls last)
        activeStocks.sort((a, b) -> {
            if (a.getExpiryDate() == null && b.getExpiryDate() == null) return a.getId().compareTo(b.getId());
            if (a.getExpiryDate() == null) return 1;
            if (b.getExpiryDate() == null) return -1;
            return a.getExpiryDate().compareTo(b.getExpiryDate());
        });

        return activeStocks;
    }

    @Transactional
    public DepartmentIndent issueStock(Long indentId, Long batchId, BigDecimal quantity) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        DepartmentIndent indent = indentRepository.findByIdAndHospitalId(indentId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Indent not found: " + indentId));

        HospitalInventory batch = inventoryRepository.findByIdAndHospitalId(batchId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Batch stock not found: " + batchId));

        // BR-3: Expiry Gate
        if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Cannot issue stock from an expired batch");
        }

        // BR-1: No Negative Stock
        BigDecimal available = BigDecimal.valueOf(batch.getStockQuantity());
        if (available.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("Requested quantity " + quantity + " exceeds available stock " + available);
        }

        // Deduct stock
        batch.setStockQuantity(available.subtract(quantity).intValue());
        inventoryRepository.save(batch);

        // Update Indent status
        indent.setStatus("FILLED");
        indentRepository.save(indent);

        // Create Stock Transaction
        StockTransaction tx = new StockTransaction();
        tx.setHospitalId(hospitalId);
        tx.setInventoryItemId(indent.getInventoryItemId());
        tx.setBatchId(batchId);
        tx.setTransactionType("ISSUE");
        tx.setQuantity(quantity);
        tx.setFromStore("main_store");
        tx.setToStore(indent.getFromDepartment());
        tx.setPerformedBy(securityHelper.getCurrentUserEmail() != null ? securityHelper.getCurrentUserEmail() : "system");
        tx.setTransactionTime(LocalDateTime.now());
        transactionRepository.save(tx);

        try {
            auditLogService.logAction(
                    "INVENTORY_STOCK_ISSUED",
                    "Issued " + quantity + " units from batch ID: " + batchId + " to department: " + indent.getFromDepartment(),
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "INVENTORY",
                    tx.getId().toString(),
                    null
            );
        } catch (Exception ignored) {}

        return indent;
    }

    // --- Stock Movements & Transfers ---

    @Transactional
    public StockTransaction transferStock(Long itemId, Long batchId, BigDecimal quantity, String fromStore, String toStore) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        HospitalInventory sourceBatch = inventoryRepository.findByIdAndHospitalId(batchId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Source batch not found: " + batchId));

        BigDecimal available = BigDecimal.valueOf(sourceBatch.getStockQuantity());
        if (available.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("Transfer quantity exceeds available stock");
        }

        // Deduct from source
        sourceBatch.setStockQuantity(available.subtract(quantity).intValue());
        inventoryRepository.save(sourceBatch);

        // Add to target store room (mocking target batch update or create)
        List<HospitalInventory> targets = inventoryRepository.findByNameAndHospitalIdAndIsActiveTrue(sourceBatch.getName(), hospitalId);
        HospitalInventory targetBatch = null;
        for (HospitalInventory b : targets) {
            // Match same expiry
            if ((b.getExpiryDate() == null && sourceBatch.getExpiryDate() == null) ||
                (b.getExpiryDate() != null && b.getExpiryDate().equals(sourceBatch.getExpiryDate()))) {
                targetBatch = b;
                break;
            }
        }

        if (targetBatch == null) {
            targetBatch = new HospitalInventory();
            targetBatch.setHospitalId(hospitalId);
            targetBatch.setName(sourceBatch.getName());
            targetBatch.setType(sourceBatch.getType());
            targetBatch.setManufacturer(sourceBatch.getManufacturer());
            targetBatch.setExpiryDate(sourceBatch.getExpiryDate());
            targetBatch.setUnitPrice(sourceBatch.getUnitPrice());
            targetBatch.setStockQuantity(quantity.intValue());
        } else {
            targetBatch.setStockQuantity(targetBatch.getStockQuantity() + quantity.intValue());
        }
        inventoryRepository.save(targetBatch);

        // Write immutable StockTransaction
        StockTransaction tx = new StockTransaction();
        tx.setHospitalId(hospitalId);
        tx.setInventoryItemId(itemId);
        tx.setBatchId(batchId);
        tx.setTransactionType("TRANSFER");
        tx.setQuantity(quantity);
        tx.setFromStore(fromStore);
        tx.setToStore(toStore);
        tx.setPerformedBy(securityHelper.getCurrentUserEmail() != null ? securityHelper.getCurrentUserEmail() : "system");
        tx.setTransactionTime(LocalDateTime.now());
        
        return transactionRepository.save(tx);
    }

    // --- Physical Audits ---

    @Transactional
    public StockTransaction auditStock(Long batchId, BigDecimal physicalQty, String reason) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");

        HospitalInventory batch = inventoryRepository.findByIdAndHospitalId(batchId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        BigDecimal variance = physicalQty.subtract(BigDecimal.valueOf(batch.getStockQuantity()));

        // Update batch qty
        batch.setStockQuantity(physicalQty.intValue());
        inventoryRepository.save(batch);

        // Record physical audit adjustment transaction
        StockTransaction tx = new StockTransaction();
        tx.setHospitalId(hospitalId);
        tx.setInventoryItemId(0L); // special placeholder or lookup catalog item
        tx.setBatchId(batchId);
        tx.setTransactionType("ADJUSTMENT");
        tx.setQuantity(variance);
        tx.setFromStore("main_store");
        tx.setToStore("main_store");
        tx.setPerformedBy(securityHelper.getCurrentUserEmail() != null ? securityHelper.getCurrentUserEmail() : "system");
        tx.setTransactionTime(LocalDateTime.now());

        return transactionRepository.save(tx);
    }

    public List<StockTransaction> getTransactions() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found");
        return transactionRepository.findByHospitalIdOrderByTransactionTimeDesc(hospitalId);
    }
}
