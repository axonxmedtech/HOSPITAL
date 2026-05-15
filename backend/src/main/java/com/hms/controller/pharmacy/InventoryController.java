package com.hms.controller.pharmacy;

import com.hms.service.pharmacy.MedicineBatchService;
import com.hms.service.pharmacy.InventoryTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/pharmacy/inventory")
@CrossOrigin
public class InventoryController {

    @Autowired
    private MedicineBatchService service;

    @Autowired
    private InventoryTransactionService transactionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getInventory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(service.getInventory(q, categoryId, pageable));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getLowStock(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.getLowStockInventory(PageRequest.of(page, size)));
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getExpiring(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.getExpiringInventory(days, PageRequest.of(page, size)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createBatch(@RequestBody com.hms.entity.pharmacy.MedicineBatch batch) {
        return ResponseEntity.ok(service.createManualBatch(batch));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> adjustStock(@RequestBody java.util.Map<String, Object> payload) {
        Long batchId = Long.valueOf(payload.get("batchId").toString());
        BigDecimal qty = new BigDecimal(payload.get("quantity").toString());
        String remarks = (String) payload.get("remarks");
        return ResponseEntity.ok(transactionService.recordAdjustment(batchId, qty, remarks));
    }

    @GetMapping("/transactions/{batchId}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getTransactions(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(batchId, PageRequest.of(page, size)));
    }
}
