package com.hms.controller.pharmacy;

import com.hms.dto.pharmacy.PharmacySaleRequest;
import com.hms.service.pharmacy.PharmacySaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pharmacy/sales")
@CrossOrigin
public class PharmacySaleController {

    @Autowired
    private PharmacySaleService saleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createSale(@RequestBody PharmacySaleRequest request) {
        try {
            return ResponseEntity.ok(saleService.createSale(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new java.util.HashMap<String, String>() {{
                put("message", e.getMessage());
            }});
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getSalesHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(saleService.getSalesHistory(PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getSaleDetails(@PathVariable Long id) {
        return ResponseEntity.ok(saleService.getSaleDetails(id));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getDashboardStats() {
        return ResponseEntity.ok(saleService.getDashboardStats());
    }
}
