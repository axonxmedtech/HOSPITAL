package com.hms.controller.hospital;

import com.hms.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import com.hms.service.hospital.WardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wards")
@Validated
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','PHARMACIST')")
public class WardController {

    private final WardService wardService;

    public WardController(WardService wardService) {
        this.wardService = wardService;
    }

    @PostMapping
    public ResponseEntity<WardResponse> createWard(@Valid @RequestBody CreateWardRequest req) {
        return ResponseEntity.ok(wardService.createWard(req));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<WardResponse>> bulkCreate(@Valid @RequestBody BulkCreateWardsRequest req) {
        return ResponseEntity.ok(wardService.bulkCreate(req));
    }

    @GetMapping
    public ResponseEntity<List<WardResponse>> getAll() {
        return ResponseEntity.ok(wardService.getAllWards());
    }

    @GetMapping("/{wardId}/beds")
    public ResponseEntity<List<BedResponse>> getBeds(@PathVariable("wardId") Long wardId) {
        return ResponseEntity.ok(wardService.getBedsForWard(wardId));
    }

    @PutMapping("/{wardId}")
    public ResponseEntity<WardResponse> updateWard(@PathVariable("wardId") Long wardId, @Valid @RequestBody UpdateWardRequest req) {
        return ResponseEntity.ok(wardService.updateWard(wardId, req));
    }

    @DeleteMapping("/{wardId}")
    public ResponseEntity<Void> deleteWard(@PathVariable("wardId") Long wardId) {
        wardService.deleteWard(wardId);
        return ResponseEntity.noContent().build();
    }
}
