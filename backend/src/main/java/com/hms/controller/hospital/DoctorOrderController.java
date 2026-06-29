package com.hms.controller.hospital;

import com.hms.service.hospital.DoctorOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ipd/{admissionId}/orders")
public class DoctorOrderController {

    @Autowired private DoctorOrderService orderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> createOrder(@PathVariable Long admissionId,
                                          @RequestBody Map<String, Object> body) {
        if (body.get("orderType") == null || body.get("description") == null)
            return ResponseEntity.badRequest().body("orderType and description are required");
        return ResponseEntity.ok(orderService.createOrder(admissionId, body));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getOrders(@PathVariable Long admissionId) {
        return ResponseEntity.ok(orderService.getOrders(admissionId));
    }

    @PutMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateOrder(@PathVariable Long admissionId,
                                          @PathVariable String publicId,
                                          @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(orderService.updateOrder(publicId, body));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> cancelOrder(@PathVariable Long admissionId,
                                          @PathVariable String publicId) {
        orderService.cancelOrder(publicId);
        return ResponseEntity.ok(Map.of("message", "Order cancelled"));
    }
}
