package com.hms.controller.hospital;

import com.hms.dto.BedResponse;
import com.hms.dto.UpdateBedStatusRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import com.hms.service.hospital.BedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/beds")
@Validated
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','PHARMACIST')")
public class BedController {

    private final BedService bedService;

    public BedController(BedService bedService) {
        this.bedService = bedService;
    }

    @PutMapping("/{bedId}")
    public ResponseEntity<BedResponse> updateStatus(@PathVariable("bedId") Long bedId, @Valid @RequestBody UpdateBedStatusRequest req) {
        return ResponseEntity.ok(bedService.updateStatus(bedId, req.getStatus()));
    }

    @GetMapping("/available")
    public ResponseEntity<List<BedResponse>> getAvailable(@RequestParam(value = "ward_id", required = false) Long wardId) {
        return ResponseEntity.ok(bedService.getAvailableBeds(wardId));
    }
}
