package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.entity.ChargeMaster;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.ChargeMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hospital/charge-master")
public class ChargeMasterController {

    @Autowired
    private ChargeMasterService chargeMasterService;

    @Autowired
    private SecurityContextHelper securityHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<ApiResponse<List<ChargeMaster>>> getAll() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return ResponseEntity.ok(ApiResponse.ok(chargeMasterService.getAll(hospitalId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<ApiResponse<ChargeMaster>> getById(@PathVariable Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return ResponseEntity.ok(ApiResponse.ok(chargeMasterService.getById(hospitalId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<ChargeMaster>> create(@RequestBody ChargeMaster request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return ResponseEntity.ok(ApiResponse.ok("Charge master entry created successfully", chargeMasterService.create(hospitalId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<ApiResponse<ChargeMaster>> update(@PathVariable Long id, @RequestBody ChargeMaster request) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return ResponseEntity.ok(ApiResponse.ok("Charge master entry updated successfully", chargeMasterService.update(hospitalId, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        chargeMasterService.delete(hospitalId, id);
        return ResponseEntity.ok(ApiResponse.ok("Charge master entry deleted successfully", null));
    }
}
