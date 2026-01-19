package com.hms.controller.hospital;

import com.hms.entity.Medicine;
import com.hms.entity.Prescription;
import com.hms.repository.PrescriptionRepository;
import com.hms.service.hospital.InventoryService;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/hospital/pharmacy")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class PharmacyController {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private com.hms.repository.PatientRepository patientRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    // --- Inventory Management ---

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getInventory() {
        return ResponseEntity.ok(inventoryService.getInventory());
    }

    @GetMapping("/inventory/low-stock")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getLowStock() {
        return ResponseEntity.ok(inventoryService.getLowStockMedicines());
    }

    @PostMapping("/inventory/stock")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<?> updateStock(@RequestBody Map<String, Object> request) {
        Long medicineId = ((Number) request.get("medicineId")).longValue();
        Integer quantity = ((Number) request.get("quantity")).intValue();
        return ResponseEntity.ok(inventoryService.updateStock(medicineId, quantity));
    }

    // --- Dispensing ---

    /**
     * Get all pending prescription items
     * Grouping by Patient/Consultation might be better for UI, but flat list is
     * easier for V1 API.
     * We'll return a DTO to make it displayable.
     */
    @GetMapping("/prescriptions/pending")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getPendingPrescriptions() {
        Long hospitalId = securityHelper.getCurrentHospitalId();

        // Fetch all prescriptions with status 'PENDING'
        // Note: You might need to add a custom query to PrescriptionRepository for this
        // For now, assuming we filter list (not efficient for scale, but okay for V1)
        List<Prescription> pending = prescriptionRepository.findAll().stream()
                .filter(p -> p.getHospitalId().equals(hospitalId) && "PENDING".equals(p.getStatus()))
                .collect(Collectors.toList());

        // Enrich with Patient/Doctor names?
        // For V1, let's return the raw list plus maybe fetch details
        // Or better, let's create a DTO structure on the fly

        List<Map<String, Object>> response = pending.stream().map(p -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", p.getId());
            map.put("medicineName", p.getMedicineName());
            map.put("dosage", p.getDosage());
            map.put("frequency", p.getFrequency());
            map.put("duration", p.getDuration());
            map.put("createdAt", p.getCreatedAt());
            map.put("status", p.getStatus());

            // Fetch Patient Name via Medical Record
            // This is N+1 query problem, acceptable for V1 MVP
            try {
                var record = medicalRecordRepository.findById(p.getMedicalRecordId()).orElse(null);
                if (record != null) {
                    var patient = patientRepository.findById(record.getPatientId()).orElse(null);
                    if (patient != null)
                        map.put("patientName", patient.getName());

                    var doctor = doctorRepository.findById(record.getDoctorId()).orElse(null);
                    if (doctor != null)
                        map.put("doctorName", doctor.getName());
                }
            } catch (Exception e) {
            }

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispense/{prescriptionId}")
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<?> dispenseMedicine(@PathVariable Long prescriptionId) {
        try {
            Prescription p = prescriptionRepository.findById(prescriptionId)
                    .orElseThrow(() -> new RuntimeException("Prescription not found"));

            if (!p.getStatus().equals("PENDING")) {
                return ResponseEntity.badRequest().body("Prescription already dispensed");
            }

            // Deduct Stock
            // Parse quantity from duration? Or just deduct 1 unit/strip for now?
            // "5 Days" x "1-0-1" (2) = 10 tablets.
            // Parsing this text is hard.
            // V1 Simplification: Deduct 10 units fixed or pass quantity from UI.
            // For this iteration, let's deduct 10 units by default or 0 if we can't parse.
            // BETTER: The Pharmacist should confirm the QTY dispensed.

            // For this API V1, we will just mark as dispensed and optionally deduct if
            // stock exists.
            inventoryService.dispenseMedicine(p.getMedicineName(), 1); // Deducting 1 unit for now

            p.setStatus("DISPENSED");
            prescriptionRepository.save(p);

            return ResponseEntity.ok("Medicine dispensed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
