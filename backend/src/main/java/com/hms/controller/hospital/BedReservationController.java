package com.hms.controller.hospital;

import com.hms.entity.BedReservation;
import com.hms.security.RequireModule;
import com.hms.service.hospital.BedReservationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Beds held for planned admissions. Hospital tenants only. */
@RestController
@RequireModule("IPD")
@RequestMapping("/hospital/beds/reservations")
public class BedReservationController {

    private final BedReservationService service;

    public BedReservationController(BedReservationService service) {
        this.service = service;
    }

    /** Body of a reservation. */
    public record ReserveBody(Long bedId, Long patientId, String patientName, String patientPhone,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedAt,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime reservedUntil,
                              String notes) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR', 'NURSE_INCHARGE')")
    public ResponseEntity<List<Map<String, Object>>> active() {
        return ResponseEntity.ok(service.active());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody ReserveBody b) {
        BedReservation r = service.reserve(new BedReservationService.ReserveRequest(b.bedId(), b.patientId(),
                b.patientName(), b.patientPhone(), b.expectedAt(), b.reservedUntil(), b.notes()));
        return ResponseEntity.ok(BedReservationService.view(r));
    }

    @PostMapping("/{publicId}/cancel")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable("publicId") String publicId,
                                                      @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(BedReservationService.view(service.cancel(publicId, body == null ? null : body.get("reason"))));
    }
}
