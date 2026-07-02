package com.hms.controller.hospital;

import com.hms.entity.DepartmentIndent;
import com.hms.entity.StockTransaction;
import com.hms.service.hospital.GeneralInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/hospital/inventory")
public class GeneralInventoryController {

    @Autowired
    private GeneralInventoryService inventoryService;

    @GetMapping("/indent")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getIndents() {
        return ResponseEntity.ok(inventoryService.getIndents());
    }

    @PostMapping("/indent")
    @PreAuthorize("hasAnyRole('NURSE', 'LAB_TECHNICIAN', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> raiseIndent(@RequestBody Map<String, Object> req) {
        String fromDepartment = (String) req.get("fromDepartment");
        Long itemId = ((Number) req.get("inventoryItemId")).longValue();
        BigDecimal quantity = new BigDecimal(req.get("requestedQty").toString());

        return ResponseEntity.ok(inventoryService.raiseIndent(fromDepartment, itemId, quantity));
    }

    @PostMapping("/indent/{id}/approve")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> approveIndent(@PathVariable Long id, @RequestBody Map<String, String> req) {
        String signature = req.get("approvedBySig");
        return ResponseEntity.ok(inventoryService.approveIndent(id, signature));
    }

    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('STOREKEEPER', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> issueStock(@RequestBody Map<String, Object> req) {
        Long indentId = ((Number) req.get("indentId")).longValue();
        Long batchId = ((Number) req.get("batchId")).longValue();
        BigDecimal quantity = new BigDecimal(req.get("issuedQty").toString());

        return ResponseEntity.ok(inventoryService.issueStock(indentId, batchId, quantity));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> transferStock(@RequestBody Map<String, Object> req) {
        Long itemId = ((Number) req.get("itemId")).longValue();
        Long batchId = ((Number) req.get("batchId")).longValue();
        BigDecimal quantity = new BigDecimal(req.get("quantity").toString());
        String fromStore = (String) req.get("fromStore");
        String toStore = (String) req.get("toStore");

        return ResponseEntity.ok(inventoryService.transferStock(itemId, batchId, quantity, fromStore, toStore));
    }

    @PostMapping("/audit")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> auditStock(@RequestBody Map<String, Object> req) {
        Long batchId = ((Number) req.get("batchId")).longValue();
        BigDecimal physicalQty = new BigDecimal(req.get("physicalQty").toString());
        String reason = (String) req.get("reason");

        return ResponseEntity.ok(inventoryService.auditStock(batchId, physicalQty, reason));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> getTransactions() {
        return ResponseEntity.ok(inventoryService.getTransactions());
    }

    @GetMapping("/suggest-fefo")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'NURSE')")
    public ResponseEntity<?> suggestFefo(@RequestParam Long itemId) {
        return ResponseEntity.ok(inventoryService.suggestFefo(itemId));
    }
}
