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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(billingService.getAllBills(pageable));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String paymentMethod) {
        try {
            Billing updated = billingService.updateStatus(id, status, paymentMethod);
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
