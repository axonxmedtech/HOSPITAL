package com.hms.controller.hospital;

import com.hms.entity.Billing;
import com.hms.service.hospital.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.repository.HospitalRepository;
import com.hms.service.hospital.PatientService;
import com.hms.service.PdfService;

@RestController
@RequestMapping("/hospital/billing")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class BillingController {

    @Autowired
    private BillingService billingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> getAllBills(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        // Fetch page of Billing
        org.springframework.data.domain.Page<com.hms.entity.Billing> billsPage = billingService.getAllBills(search, status, pageable);

        // Attach billing items to each billing as an `items` field
        java.util.List<java.util.Map<String, Object>> mapped = new java.util.ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // Register Java Time module so LocalDateTime (createdAt) serializes correctly
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        for (com.hms.entity.Billing b : billsPage.getContent()) {
            java.util.List<com.hms.entity.BillingItem> items = billingItemRepository.findByBillingId(b.getId());
            java.util.Map<String, Object> asMap = mapper.convertValue(b, java.util.Map.class);
            asMap.put("items", items);
            mapped.add(asMap);
        }

        org.springframework.data.domain.Page<java.util.Map<String, Object>> result = new org.springframework.data.domain.PageImpl<>(mapped, pageable, billsPage.getTotalElements());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String paymentReference) {
        try {
            Billing updated = billingService.updateStatus(id, status, paymentMethod, paymentReference);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Autowired
    private PdfService pdfService;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PatientService patientService;

    @Autowired
    private com.hms.repository.BillingRepository billingRepository;
    
    @Autowired
    private com.hms.repository.BillingItemRepository billingItemRepository;

    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> downloadReceipt(@PathVariable Long id) {
        try {
            Billing billing = billingRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Bill not found"));

            Hospital hospital = hospitalRepository.findById(billing.getHospitalId())
                    .orElseThrow(() -> new RuntimeException("Hospital not found"));

            Patient patient = patientService.getPatientById(billing.getPatientId());

            java.io.ByteArrayInputStream pdf = pdfService.generateBillingReceiptPdf(hospital, patient, billing);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "inline; filename=receipt_" + billing.getCustomId() + ".pdf");

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new InputStreamResource(pdf));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
