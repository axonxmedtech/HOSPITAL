package com.hms.controller.pharmacy;

import jakarta.validation.Valid;

import com.hms.security.RequireModule;
import com.hms.dto.pharmacy.PurchaseRequest;
import com.hms.entity.pharmacy.PurchaseInvoice;
import com.hms.service.pharmacy.PurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pharmacy/purchases")
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','PHARMACIST')")
@RequireModule("PHARMACY")
public class PurchaseController {

    @Autowired
    private PurchaseService purchaseService;

    @PostMapping
    public ResponseEntity<PurchaseInvoice> create(@Valid @RequestBody PurchaseRequest req) {
        return ResponseEntity.ok(purchaseService.createPurchase(req));
    }

    @GetMapping
    public ResponseEntity<Page<PurchaseInvoice>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(purchaseService.listInvoices(PageRequest.of(page, com.hms.util.Pages.cap(size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseInvoice> get(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.getInvoice(id));
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<PurchaseInvoice> post(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.postInvoice(id));
    }

    /** Edit a DRAFT purchase (header and lines). */
    @PutMapping("/{id}")
    public ResponseEntity<PurchaseInvoice> update(@PathVariable Long id, @Valid @RequestBody PurchaseRequest req) {
        return ResponseEntity.ok(purchaseService.updateDraft(id, req));
    }

    /** Body: { "reason": "Wrong supplier invoice" }. Discards a draft; reverses a posted purchase's stock. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PurchaseInvoice> cancel(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(purchaseService.cancel(id, body.get("reason")));
    }

    /** Body: { "amount": 5000, "method": "BANK_TRANSFER", "reference": "UTR123", "paidOn": "2026-11-22" }. */
    @PostMapping("/{id}/payments")
    public ResponseEntity<PurchaseInvoice> recordPayment(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        Object amount = body.get("amount");
        Object paidOn = body.get("paidOn");
        return ResponseEntity.ok(purchaseService.recordPayment(id,
                amount == null ? null : new java.math.BigDecimal(amount.toString()),
                body.get("method") == null ? null : body.get("method").toString(),
                body.get("reference") == null ? null : body.get("reference").toString(),
                paidOn == null || paidOn.toString().isBlank() ? null : java.time.LocalDate.parse(paidOn.toString())));
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<?> payments(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.payments(id));
    }
}
