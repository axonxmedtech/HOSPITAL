package com.hms.controller.hospital;

import com.hms.entity.Medicine;
import com.hms.service.hospital.MedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hospital/medicines")
@CrossOrigin(origins = "*")
public class MedicineController {

    @Autowired
    private MedicineService medicineService;

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<List<Medicine>> searchMedicines(@RequestParam String query) {
        return ResponseEntity.ok(medicineService.searchMedicines(query));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> addMedicine(@RequestBody Medicine medicine) {
        try {
            return ResponseEntity.ok(medicineService.addMedicine(medicine));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
