package com.hms.controller.hospital;

import com.hms.entity.*;
import com.hms.service.hospital.ProcurementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hospital/purchase")
public class ProcurementController {

    @Autowired
    private ProcurementService procurementService;

    // --- Requisitions ---

    @PostMapping("/requisition")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createRequisition(@RequestBody Map<String, Object> req) {
        String department = (String) req.get("department");
        LocalDate requiredDate = LocalDate.parse((String) req.get("requiredDate"));
        String priority = (String) req.get("priority");
        String itemsJson = (String) req.get("itemsJson");

        return ResponseEntity.ok(procurementService.createRequisition(department, requiredDate, priority, itemsJson));
    }

    @PostMapping("/requisition/{id}/approve")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> approveRequisition(@PathVariable Long id) {
        return ResponseEntity.ok(procurementService.approveRequisition(id));
    }

    @GetMapping("/requisitions")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getRequisitions() {
        return ResponseEntity.ok(procurementService.getRequisitions());
    }

    // --- Vendors ---

    @PostMapping("/vendor")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> createVendor(@RequestBody Vendor vendor) {
        return ResponseEntity.ok(procurementService.createVendor(vendor));
    }

    @GetMapping("/vendors")
    @PreAuthorize("hasAnyRole('PURCHASE_OFFICER', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getVendors() {
        return ResponseEntity.ok(procurementService.getVendors());
    }

    // --- Purchase Orders ---

    @PostMapping("/order")
    @PreAuthorize("hasAnyRole('PURCHASE_OFFICER', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createPurchaseOrder(@RequestBody Map<String, Object> req) {
        Long vendorId = ((Number) req.get("vendorId")).longValue();
        LocalDate expectedDelivery = LocalDate.parse((String) req.get("expectedDelivery"));
        String itemsJson = (String) req.get("itemsJson");

        return ResponseEntity.ok(procurementService.createPurchaseOrder(vendorId, expectedDelivery, itemsJson));
    }

    @PostMapping("/order/{id}/approve")
    @PreAuthorize("hasAnyRole('PURCHASE_MANAGER', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> approvePurchaseOrder(@PathVariable Long id, @RequestBody Map<String, String> req) {
        String signature = req.get("approvedBySig");
        return ResponseEntity.ok(procurementService.approvePurchaseOrder(id, signature));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('PURCHASE_OFFICER', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getPurchaseOrders() {
        return ResponseEntity.ok(procurementService.getPurchaseOrders());
    }

    // --- Goods Receipt (GRN) ---

    @PostMapping("/order/{id}/confirm-grn")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> confirmGrn(@PathVariable Long id, @RequestBody List<HospitalInventory> receivedItems) {
        return ResponseEntity.ok(procurementService.confirmGrn(id, receivedItems));
    }

    // --- Invoice & Payment ---

    @PostMapping("/invoice-verify")
    @PreAuthorize("hasAnyRole('FINANCE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> verifyInvoice(@RequestBody Map<String, Object> req) {
        Long vendorId = ((Number) req.get("vendorId")).longValue();
        String invoiceNumber = (String) req.get("invoiceNumber");
        BigDecimal amount = new BigDecimal(req.get("amount").toString());
        Long matchedPoId = ((Number) req.get("matchedPoId")).longValue();
        Long matchedGrnId = ((Number) req.get("matchedGrnId")).longValue();

        return ResponseEntity.ok(procurementService.verifyInvoice(vendorId, invoiceNumber, amount, matchedPoId, matchedGrnId));
    }

    @PostMapping("/payment")
    @PreAuthorize("hasAnyRole('ACCOUNTS', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> processPayment(@RequestBody Map<String, Object> req) {
        Long invoiceId = ((Number) req.get("invoiceId")).longValue();
        String mode = (String) req.get("paymentMode");
        BigDecimal amount = new BigDecimal(req.get("amount").toString());

        return ResponseEntity.ok(procurementService.processPayment(invoiceId, mode, amount));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('FINANCE', 'ACCOUNTS', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getInvoices() {
        return ResponseEntity.ok(procurementService.getInvoices());
    }
}
