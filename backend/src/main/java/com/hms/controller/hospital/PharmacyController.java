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

        // 1. Optimized fetch strictly targeting Active state under the hospital_id
        List<Prescription> active = prescriptionRepository.findByHospitalIdAndStatus(hospitalId, "ACTIVE");

        if (active.isEmpty()) {
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }

        // 2. Mass-fetch Medical Records to bypass O(N) Select latency
        java.util.Set<Long> recordIds = active.stream()
                .map(Prescription::getMedicalRecordId)
                .collect(Collectors.toSet());
        
        java.util.Map<Long, com.hms.entity.MedicalRecord> recordMap = medicalRecordRepository.findAllById(recordIds).stream()
                .collect(Collectors.toMap(com.hms.entity.MedicalRecord::getId, r -> r, (r1, r2) -> r1));

        // 3. Mass-fetch Patients & Doctors
        java.util.Set<Long> patientIds = recordMap.values().stream()
                .map(com.hms.entity.MedicalRecord::getPatientId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        java.util.Set<Long> doctorIds = recordMap.values().stream()
                .map(com.hms.entity.MedicalRecord::getDoctorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        java.util.Map<Long, com.hms.entity.Patient> patientMap = patientIds.isEmpty() ? new java.util.HashMap<>() :
                patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(com.hms.entity.Patient::getId, p -> p, (p1, p2) -> p1));

        java.util.Map<Long, com.hms.entity.Doctor> doctorMap = doctorIds.isEmpty() ? new java.util.HashMap<>() :
                doctorRepository.findAllById(doctorIds).stream()
                .collect(Collectors.toMap(com.hms.entity.Doctor::getId, d -> d, (d1, d2) -> d1));

        // 4. Rapid memory correlation mapping
        List<Map<String, Object>> response = active.stream().map(p -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", p.getId());
            map.put("medicalRecordId", p.getMedicalRecordId());
            map.put("medicineName", p.getMedicineName());
            map.put("dosage", p.getDosage());
            map.put("frequency", p.getFrequency());
            map.put("duration", p.getDuration());
            map.put("instructions", p.getInstructions());
            map.put("createdAt", p.getCreatedAt());
            map.put("status", p.getStatus());

            com.hms.entity.MedicalRecord record = recordMap.get(p.getMedicalRecordId());
            if (record != null) {
                com.hms.entity.Patient patient = patientMap.get(record.getPatientId());
                if (patient != null) {
                    map.put("patientName", patient.getName());
                    map.put("patientAge", patient.getAge());
                    map.put("patientGender", patient.getGender());
                }

                com.hms.entity.Doctor doctor = doctorMap.get(record.getDoctorId());
                if (doctor != null) {
                    map.put("doctorName", doctor.getName());
                }
                
                map.put("diagnosis", record.getDiagnosis());
                map.put("notes", record.getTreatmentNotes());
                map.put("symptoms", record.getSymptoms());
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
