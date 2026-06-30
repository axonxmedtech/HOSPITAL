package com.hms.controller.hospital;

import com.hms.entity.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.MasterDataService;
import com.hms.repository.pharmacy.MedicineMasterRepository;
import com.hms.entity.pharmacy.MedicineMaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hospital/master")
public class MasterDataController {

    @Autowired private MasterDataService masterDataService;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private MedicineMasterRepository medicineMasterRepo;

    // ─── Lab Tests ────────────────────────────────────────────────────────────

    @GetMapping("/lab-tests/search")
    public ResponseEntity<List<LabTestMaster>> searchLabTests(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchLabTests(q));
    }

    @PostMapping("/lab-tests")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<LabTestMaster> createLabTest(@RequestBody LabTestMaster input) {
        return ResponseEntity.ok(masterDataService.createLabTest(input));
    }

    @PutMapping("/lab-tests/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<LabTestMaster> updateLabTest(@PathVariable Long id, @RequestBody LabTestMaster input) {
        return ResponseEntity.ok(masterDataService.updateLabTest(id, input));
    }

    @DeleteMapping("/lab-tests/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateLabTest(@PathVariable Long id) {
        masterDataService.deactivateLabTest(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Radiology Tests ──────────────────────────────────────────────────────

    @GetMapping("/radiology-tests/search")
    public ResponseEntity<List<RadiologyTestMaster>> searchRadiologyTests(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchRadiologyTests(q));
    }

    @PostMapping("/radiology-tests")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<RadiologyTestMaster> createRadiologyTest(@RequestBody RadiologyTestMaster input) {
        return ResponseEntity.ok(masterDataService.createRadiologyTest(input));
    }

    @PutMapping("/radiology-tests/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<RadiologyTestMaster> updateRadiologyTest(@PathVariable Long id, @RequestBody RadiologyTestMaster input) {
        return ResponseEntity.ok(masterDataService.updateRadiologyTest(id, input));
    }

    @DeleteMapping("/radiology-tests/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateRadiologyTest(@PathVariable Long id) {
        masterDataService.deactivateRadiologyTest(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Allergies ────────────────────────────────────────────────────────────

    @GetMapping("/allergies/search")
    public ResponseEntity<List<AllergyMaster>> searchAllergies(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchAllergies(q));
    }

    @PostMapping("/allergies")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<AllergyMaster> createAllergy(@RequestBody AllergyMaster input) {
        return ResponseEntity.ok(masterDataService.createAllergy(input));
    }

    @DeleteMapping("/allergies/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateAllergy(@PathVariable Long id) {
        masterDataService.deactivateAllergy(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Diagnoses ────────────────────────────────────────────────────────────

    @GetMapping("/diagnoses/search")
    public ResponseEntity<List<DiagnosisMaster>> searchDiagnoses(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchDiagnoses(q));
    }

    @PostMapping("/diagnoses")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<DiagnosisMaster> createDiagnosis(@RequestBody DiagnosisMaster input) {
        return ResponseEntity.ok(masterDataService.createDiagnosis(input));
    }

    @DeleteMapping("/diagnoses/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateDiagnosis(@PathVariable Long id) {
        masterDataService.deactivateDiagnosis(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Procedures ───────────────────────────────────────────────────────────

    @GetMapping("/procedures/search")
    public ResponseEntity<List<ProcedureMaster>> searchProcedures(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(masterDataService.searchProcedures(q));
    }

    @PostMapping("/procedures")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ProcedureMaster> createProcedure(@RequestBody ProcedureMaster input) {
        return ResponseEntity.ok(masterDataService.createProcedure(input));
    }

    @PutMapping("/procedures/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ProcedureMaster> updateProcedure(@PathVariable Long id, @RequestBody ProcedureMaster input) {
        return ResponseEntity.ok(masterDataService.updateProcedure(id, input));
    }

    @DeleteMapping("/procedures/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deactivateProcedure(@PathVariable Long id) {
        masterDataService.deactivateProcedure(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Medicines ────────────────────────────────────────────────────────────

    @GetMapping("/medicines/search")
    public ResponseEntity<List<MedicineMaster>> searchMedicines(@RequestParam(required = false) String q) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(medicineMasterRepo.findByHospitalIdAndIsActiveTrueOrderByMedicineNameAsc(hospitalId));
        }
        return ResponseEntity.ok(medicineMasterRepo.searchByHospitalAndName(hospitalId, q));
    }

    // ─── Seed ─────────────────────────────────────────────────────────────────

    @PostMapping("/seed")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<String> seedDefaults() {
        masterDataService.seedDefaultsForHospital(securityHelper.getCurrentHospitalId());
        return ResponseEntity.ok("Seed complete");
    }
}
