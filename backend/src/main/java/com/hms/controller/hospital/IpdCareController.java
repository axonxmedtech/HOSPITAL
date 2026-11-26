package com.hms.controller.hospital;

import com.hms.entity.DietOrder;
import com.hms.security.RequireModule;
import com.hms.service.hospital.DietOrderService;
import com.hms.service.hospital.IpdInvestigationService;
import com.hms.service.hospital.IpdRoundsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** An inpatient's care orders: the doctor's rounds, investigations and diet. Hospital tenants only. */
@RestController
@RequireModule("IPD")
@RequestMapping("/hospital/ipd")
public class IpdCareController {

    private static final String READERS = "hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN', 'RECEPTIONIST', 'NURSE', 'NURSE_INCHARGE')";

    private final IpdRoundsService roundsService;
    private final IpdInvestigationService investigationService;
    private final DietOrderService dietService;
    private final com.hms.service.pdf.ClinicalPdfService clinicalPdfService;
    private final com.hms.repository.HospitalRepository hospitalRepository;
    private final com.hms.security.SecurityContextHelper securityHelper;

    public IpdCareController(IpdRoundsService roundsService, IpdInvestigationService investigationService,
                             DietOrderService dietService, com.hms.service.pdf.ClinicalPdfService clinicalPdfService,
                             com.hms.repository.HospitalRepository hospitalRepository,
                             com.hms.security.SecurityContextHelper securityHelper) {
        this.roundsService = roundsService;
        this.investigationService = investigationService;
        this.dietService = dietService;
        this.clinicalPdfService = clinicalPdfService;
        this.hospitalRepository = hospitalRepository;
        this.securityHelper = securityHelper;
    }

    @GetMapping("/{id}/rounds")
    @PreAuthorize(READERS)
    public ResponseEntity<List<Map<String, Object>>> rounds(@PathVariable("id") Long id) {
        return ResponseEntity.ok(roundsService.rounds(id));
    }

    @GetMapping("/{id}/investigations")
    @PreAuthorize(READERS)
    public ResponseEntity<List<Map<String, Object>>> investigations(@PathVariable("id") Long id) {
        return ResponseEntity.ok(investigationService.list(id));
    }

    /** Body: {"tests": ["CBC", "LFT"], "priority": "URGENT", "notes": "..."}. */
    @PostMapping("/{id}/investigations")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<Map<String, Object>>> order(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object tests = body.get("tests");
        List<String> names = tests instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        return ResponseEntity.ok(investigationService.order(id, names, (String) body.get("priority"), (String) body.get("notes"),
                body.get("idempotencyKey") == null ? null : String.valueOf(body.get("idempotencyKey"))));
    }

    @PostMapping("/investigations/{publicId}/sample")
    @PreAuthorize(READERS)
    public ResponseEntity<Map<String, Object>> sample(@PathVariable("publicId") String publicId) {
        return ResponseEntity.ok(investigationService.collectSample(publicId));
    }

    /** Body: {"result": "..."}. */
    @PostMapping("/investigations/{publicId}/result")
    @PreAuthorize(READERS)
    public ResponseEntity<Map<String, Object>> result(@PathVariable("publicId") String publicId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(investigationService.recordResult(publicId, body.get("result")));
    }

    /** Body: {"reason": "..."}. */
    @PostMapping("/investigations/{publicId}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable("publicId") String publicId,
                                                      @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(investigationService.cancel(publicId, body == null ? null : body.get("reason")));
    }

    @GetMapping("/{id}/diet")
    @PreAuthorize(READERS)
    public ResponseEntity<List<DietOrder>> diet(@PathVariable("id") Long id) {
        return ResponseEntity.ok(dietService.history(id));
    }

    /** Body: {"dietType": "DIABETIC", "instructions": "..."}. */
    @PostMapping("/{id}/diet")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<DietOrder> setDiet(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(dietService.set(id, body.get("dietType"), body.get("instructions")));
    }

    @GetMapping("/diet-sheet")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN', 'RECEPTIONIST', 'NURSE_INCHARGE')")
    public ResponseEntity<List<Map<String, Object>>> dietSheet(@RequestParam(value = "wardId", required = false) Long wardId) {
        return ResponseEntity.ok(dietService.dietSheet(wardId));
    }

    @GetMapping("/diet-sheet/pdf")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN', 'RECEPTIONIST', 'NURSE_INCHARGE')")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> dietSheetPdf(
            @RequestParam(value = "wardId", required = false) Long wardId) {
        List<Map<String, Object>> rows = dietService.dietSheet(wardId);
        com.hms.entity.Hospital hospital = hospitalRepository.findById(securityHelper.getCurrentHospitalId()).orElse(null);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=diet_sheet.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new org.springframework.core.io.InputStreamResource(clinicalPdfService.generateDietSheetPdf(hospital, rows)));
    }
}
