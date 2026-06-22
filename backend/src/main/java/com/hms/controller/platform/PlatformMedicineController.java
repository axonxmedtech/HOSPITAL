package com.hms.controller.platform;

import com.hms.entity.MedicineList;
import com.hms.service.hospital.MedicineService;
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

    @GetMapping
    public ResponseEntity<Page<MedicineList>> getMedicines(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return ResponseEntity.ok(medicineService.getPlatformMedicines(search, pageable));
    }

    @PostMapping
    public ResponseEntity<?> createMedicine(@RequestBody MedicineList medicine) {
        try {
            return ResponseEntity.ok(medicineService.addCatalogMedicine(medicine));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMedicine(@PathVariable Long id, @RequestBody MedicineList request) {
        try {
            return ResponseEntity.ok(medicineService.updateCatalogMedicine(id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMedicine(@PathVariable Long id) {
        try {
            medicineService.deleteCatalogMedicine(id);
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
