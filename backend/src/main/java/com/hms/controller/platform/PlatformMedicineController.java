package com.hms.controller.platform;

import com.hms.entity.MedicineList;
import com.hms.service.hospital.MedicineService;
import com.hms.service.platform.PlatformMedicineListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/platform/medicines")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformMedicineController {

    @Autowired
    private MedicineService medicineService;

    @Autowired
    private PlatformMedicineListService platformMedicineListService;

    /**
     * Get medicines filtered by tenant type (HOSPITAL, CLINIC, PHARMACY).
     */
    @GetMapping
    public ResponseEntity<?> getMedicines(
            @RequestParam(required = false) String hospitalType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            // If hospitalType is provided, use isolated service
            if (hospitalType != null && !hospitalType.isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
                Page<MedicineList> result = platformMedicineListService.searchMedicinesByType(hospitalType, search, pageable);
                return ResponseEntity.ok(result);
            }

            // Otherwise, get all medicines (backward compatibility)
            Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
            return ResponseEntity.ok(medicineService.getPlatformMedicines(search, pageable));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createMedicine(
            @RequestParam String hospitalType,
            @RequestBody MedicineList medicine) {
        try {
            medicine.setHospitalType(hospitalType);
            MedicineList result = platformMedicineListService.createMedicine(
                hospitalType,
                medicine.getName(),
                medicine.getType()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMedicine(
            @PathVariable Long id,
            @RequestParam String hospitalType,
            @RequestBody MedicineList request) {
        try {
            MedicineList result = platformMedicineListService.updateMedicine(
                id,
                hospitalType,
                request.getName(),
                request.getType()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMedicine(
            @PathVariable Long id,
            @RequestParam String hospitalType) {
        try {
            platformMedicineListService.deleteMedicine(id, hospitalType);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/import-csv")
    public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = medicineService.importCatalogCsv(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
