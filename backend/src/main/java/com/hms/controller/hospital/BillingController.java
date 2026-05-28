package com.hms.controller.hospital;

import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.BillingPayment;
import com.hms.service.hospital.BillingService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    private void validateBillingAccess() {
        String role = securityHelper.getCurrentUserRole();
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid hospital context");
        }

        // Fetch settings
        com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId)
                .orElseGet(() -> {
                    Hospital hospital = hospitalRepository.findById(hospitalId)
                            .orElseThrow(() -> new RuntimeException("Hospital not found"));
                    com.hms.entity.HospitalSetting newSettings = new com.hms.entity.HospitalSetting();
                    newSettings.setHospital(hospital);
                    return hospitalSettingRepository.save(newSettings);
                });

        // Enforce settings
        if ("ROLE_DOCTOR".equalsIgnoreCase(role) || "DOCTOR".equalsIgnoreCase(role)) {
            if (!"DOCTOR".equalsIgnoreCase(settings.getBillingHandler())) {
                throw new org.springframework.security.access.AccessDeniedException("Billing management is restricted to receptionists.");
            }
        } else if ("ROLE_RECEPTIONIST".equalsIgnoreCase(role) || "RECEPTIONIST".equalsIgnoreCase(role)) {
            if (!"RECEPTIONIST".equalsIgnoreCase(settings.getBillingHandler())) {
                throw new org.springframework.security.access.AccessDeniedException("Billing management is restricted to doctors.");
            }
            if ("SOLO".equalsIgnoreCase(settings.getReceptionMode())) {
                throw new org.springframework.security.access.AccessDeniedException("Receptionist access is restricted under Solo Doctor mode.");
            }
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> getAllBills(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        validateBillingAccess();
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
            java.util.List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(b.getId());
            
            java.math.BigDecimal totalAmt = java.math.BigDecimal.ZERO;
            if (items != null && !items.isEmpty()) {
                for (com.hms.entity.BillingItem it : items) {
                    if (it.getAmount() != null) {
                        totalAmt = totalAmt.add(it.getAmount());
                    }
                }
            }
            if (medicines != null && !medicines.isEmpty()) {
                for (com.hms.entity.BillingMedicine med : medicines) {
                    if (med.getAmount() != null) {
                        totalAmt = totalAmt.add(med.getAmount());
                    }
                }
            }
            if (totalAmt.compareTo(java.math.BigDecimal.ZERO) == 0 && (items == null || items.isEmpty()) && (medicines == null || medicines.isEmpty())) {
                totalAmt = b.getAmount() != null ? b.getAmount() : java.math.BigDecimal.ZERO;
                if (totalAmt.compareTo(java.math.BigDecimal.ZERO) == 0 && "IPD".equalsIgnoreCase(b.getBillingType())) {
                    try {
                        if (b.getIpdAdmissionId() != null) {
                            com.hms.entity.IpdAdmission ipd = ipdAdmissionRepository.findById(b.getIpdAdmissionId()).orElse(null);
                            if (ipd != null && ipd.getWardId() != null) {
                                com.hms.entity.Ward ward = wardRepository.findById(ipd.getWardId()).orElse(null);
                                if (ward != null && ward.getBedPrice() != null) {
                                    totalAmt = totalAmt.add(ward.getBedPrice());
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            java.math.BigDecimal paidAmt = java.math.BigDecimal.ZERO;
            try {
                java.util.List<com.hms.entity.BillingPayment> payments = billingPaymentRepository.findByBillingId(b.getId());
                for (com.hms.entity.BillingPayment pay : payments) {
                    if (pay.getAmount() != null) {
                        paidAmt = paidAmt.add(pay.getAmount());
                    }
                }
            } catch (Exception ignored) {}

            java.util.Map<String, Object> asMap = mapper.convertValue(b, java.util.Map.class);
            asMap.put("items", items);
            asMap.put("medicines", medicines);
            asMap.put("amount", totalAmt);
            asMap.put("paidAmount", paidAmt);
            asMap.put("balance", totalAmt.subtract(paidAmt));
            // Enrich with patient details
            try {
                if (b.getPatientId() != null) {
                    com.hms.entity.Patient p = patientService.getPatientById(b.getPatientId());
                    if (p != null) {
                        asMap.put("patientName", p.getName());
                    }
                }
            } catch (Exception ignored) {}
            mapped.add(asMap);
        }

        org.springframework.data.domain.Page<java.util.Map<String, Object>> result = new org.springframework.data.domain.PageImpl<>(mapped, pageable, billsPage.getTotalElements());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String paymentReference) {
        try {
            validateBillingAccess();
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

    @Autowired
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;

    @Autowired
    private com.hms.repository.IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private com.hms.repository.WardRepository wardRepository;

    @Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> downloadReceipt(@PathVariable Long id) {
        try {
            validateBillingAccess();
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

    @GetMapping("/ipd/{ipdId}/bill")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> getIpdBill(@PathVariable Long ipdId) {
        try {
            validateBillingAccess();
            List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
            if (bills == null || bills.isEmpty()) return ResponseEntity.notFound().build();
            Billing bill = bills.get(0);

            List<BillingItem> items = billingItemRepository.findByBillingId(bill.getId());
            List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(bill.getId());
            List<BillingPayment> payments = billingPaymentRepository.findByBillingId(bill.getId());

            BigDecimal total = BigDecimal.ZERO;
            if (items != null && !items.isEmpty()) {
                for (BillingItem it : items) {
                    if (it.getAmount() != null) total = total.add(it.getAmount());
                }
            }
            if (medicines != null && !medicines.isEmpty()) {
                for (com.hms.entity.BillingMedicine med : medicines) {
                    if (med.getAmount() != null) total = total.add(med.getAmount());
                }
            }
            if (total.compareTo(BigDecimal.ZERO) == 0 && (items == null || items.isEmpty()) && (medicines == null || medicines.isEmpty())) {
                total = bill.getAmount() != null ? bill.getAmount() : BigDecimal.ZERO;
                if (total.compareTo(BigDecimal.ZERO) == 0) {
                    try {
                        com.hms.entity.IpdAdmission ipd = ipdAdmissionRepository.findById(ipdId).orElse(null);
                        if (ipd != null && ipd.getWardId() != null) {
                            com.hms.entity.Ward ward = wardRepository.findById(ipd.getWardId()).orElse(null);
                            if (ward != null && ward.getBedPrice() != null) {
                                total = total.add(ward.getBedPrice());
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            BigDecimal paid = BigDecimal.ZERO;
            for (BillingPayment pay : payments) {
                if (pay.getAmount() != null) paid = paid.add(pay.getAmount());
            }

            Map<String,Object> resp = new HashMap<>();
            resp.put("billingId", bill.getId());
            resp.put("totalAmount", total);
            resp.put("paidAmount", paid);
            resp.put("balance", total.subtract(paid));
            java.util.List<Map<String,Object>> list = new java.util.ArrayList<>();
            for (BillingItem it : items) {
                Map<String,Object> m = new HashMap<>();
                m.put("description", it.getDescription());
                m.put("amount", it.getAmount());
                list.add(m);
            }
            resp.put("items", list);

            java.util.List<Map<String,Object>> medList = new java.util.ArrayList<>();
            for (com.hms.entity.BillingMedicine med : medicines) {
                Map<String,Object> m = new HashMap<>();
                m.put("medicineName", med.getMedicineName());
                m.put("quantity", med.getQuantity());
                m.put("unitPrice", med.getUnitPrice());
                m.put("amount", med.getAmount());
                medList.add(m);
            }
            resp.put("medicines", medList);

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public static class PayRequest {
        public BigDecimal amount;
        public String mode;
        public String reference;
    }

    @PostMapping("/{billingId}/pay")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> payBilling(@PathVariable Long billingId, @RequestBody PayRequest req) {
        try {
            validateBillingAccess();
            String role = securityHelper.getCurrentUserRole();
            if (!"RECEPTIONIST".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role) && !"DOCTOR".equalsIgnoreCase(role)) {
                return ResponseEntity.status(403).body("Not allowed");
            }

            Billing bill = billingRepository.findById(billingId).orElse(null);
            if (bill == null) return ResponseEntity.notFound().build();

            if (req.amount == null || req.amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body("Invalid amount");
            }

            BillingPayment payment = new BillingPayment();
            payment.setBillingId(billingId);
            payment.setHospitalId(bill.getHospitalId());
            payment.setAmount(req.amount);
            payment.setMode(req.mode);
            payment.setReference(req.reference);
            billingPaymentRepository.save(payment);

            // recalc totals
            List<BillingItem> items = billingItemRepository.findByBillingId(billingId);
            List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(billingId);
            BigDecimal total = BigDecimal.ZERO;
            if (items != null && !items.isEmpty()) {
                for (BillingItem it : items) {
                    if (it.getAmount() != null) total = total.add(it.getAmount());
                }
            }
            if (medicines != null && !medicines.isEmpty()) {
                for (com.hms.entity.BillingMedicine med : medicines) {
                    if (med.getAmount() != null) total = total.add(med.getAmount());
                }
            }
            if (total.compareTo(BigDecimal.ZERO) == 0 && (items == null || items.isEmpty()) && (medicines == null || medicines.isEmpty())) {
                total = bill.getAmount() != null ? bill.getAmount() : BigDecimal.ZERO;
            }

            List<BillingPayment> payments = billingPaymentRepository.findByBillingId(billingId);
            BigDecimal paid = BigDecimal.ZERO;
            for (BillingPayment p : payments) if (p.getAmount() != null) paid = paid.add(p.getAmount());

            if (paid.compareTo(total) >= 0) {
                bill.setPaymentStatus("PAID");
                bill.setPaymentMethod(req.mode);
                bill.setPaymentReference(req.reference);
            } else {
                bill.setPaymentStatus("PARTIAL");
                bill.setPaymentMethod(req.mode);
            }
            billingRepository.save(bill);

            Map<String,Object> out = new HashMap<>();
            out.put("billingId", bill.getId());
            out.put("totalAmount", total);
            out.put("paidAmount", paid);
            out.put("balance", total.subtract(paid));
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
