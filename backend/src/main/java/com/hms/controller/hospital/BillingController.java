package com.hms.controller.hospital;

import com.hms.audit.PatientRecordAccess;
import com.hms.exception.ResourceNotFoundException;

import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.BillingPayment;
import com.hms.service.hospital.BillingService;

import com.hms.validation.NoEmoji;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
@RequestMapping({"/hospital/billing", "/clinic/billing", "/pharmacy/billing"})
public class BillingController {
    private static final String DOCTOR_ROLE = "DOCTOR";
    private static final String TOTAL_AMOUNT = "totalAmount";
    private static final String AMOUNT = "amount";

    /** The money figures every bill response carries, all from the server's own arithmetic. */
    private static void putMoney(Map<String, Object> out, com.hms.service.hospital.BillMoney money) {
        out.put("grossAmount", money.gross());
        out.put("discountAmount", money.discount());
        out.put("paidAmount", money.paid());
        out.put("balance", money.balance());
    }


    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BillingController.class);

    @Autowired
    private BillingService billingService;

    @Autowired
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    @Autowired
    private com.hms.security.BillingAccessPolicy billingAccessPolicy;

    /** Admin always; doctor / reception as the hospital's Billing Handler setting allows. */
    private void validateBillingAccess() {
        billingAccessPolicy.check();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> getAllBills(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        validateBillingAccess();
        Pageable pageable = PageRequest.of(page, com.hms.util.Pages.cap(size), org.springframework.data.domain.Sort.by("createdAt").descending());
        // Fetch page of Billing
        org.springframework.data.domain.Page<com.hms.entity.Billing> billsPage =
                billingService.getAllBills(search, status, category, from, to, pageable);

        java.util.List<com.hms.entity.Billing> content = billsPage.getContent();
        java.util.List<Long> billIds = content.stream()
                .map(com.hms.entity.Billing::getId)
                .collect(java.util.stream.Collectors.toList());

        java.util.List<Long> patientIds = content.stream()
                .map(com.hms.entity.Billing::getPatientId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        // Batch fetch
        java.util.List<com.hms.entity.BillingItem> allItems = billIds.isEmpty() ? 
                java.util.Collections.emptyList() : billingItemRepository.findByBillingIdIn(billIds);
        java.util.List<com.hms.entity.BillingMedicine> allMedicines = billIds.isEmpty() ? 
                java.util.Collections.emptyList() : billingMedicineRepository.findByBillingIdIn(billIds);
        java.util.List<com.hms.entity.BillingPayment> allPayments = billIds.isEmpty() ? 
                java.util.Collections.emptyList() : billingPaymentRepository.findByBillingIdIn(billIds);
        java.util.List<com.hms.entity.Patient> allPatients = patientIds.isEmpty() ? 
                java.util.Collections.emptyList() : patientService.getPatientsByIds(patientIds);

        // Group by billing ID in memory
        java.util.Map<Long, java.util.List<com.hms.entity.BillingItem>> itemsByBillId = allItems.stream()
                .collect(java.util.stream.Collectors.groupingBy(com.hms.entity.BillingItem::getBillingId));
        java.util.Map<Long, java.util.List<com.hms.entity.BillingMedicine>> medicinesByBillId = allMedicines.stream()
                .collect(java.util.stream.Collectors.groupingBy(com.hms.entity.BillingMedicine::getBillingId));
        java.util.Map<Long, java.util.List<com.hms.entity.BillingPayment>> paymentsByBillId = allPayments.stream()
                .collect(java.util.stream.Collectors.groupingBy(com.hms.entity.BillingPayment::getBillingId));
        java.util.Map<Long, com.hms.entity.Patient> patientById = allPatients.stream()
                .collect(java.util.stream.Collectors.toMap(com.hms.entity.Patient::getId, java.util.function.Function.identity(), (a, b) -> a));

        // Group IPD Admissions and Wards if any
        java.util.List<Long> ipdAdmissionIds = content.stream()
                .map(com.hms.entity.Billing::getIpdAdmissionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        java.util.Map<Long, com.hms.entity.IpdAdmission> ipdByAdmissionId = java.util.Collections.emptyMap();
        java.util.Map<Long, com.hms.entity.Ward> wardById = java.util.Collections.emptyMap();
        if (!ipdAdmissionIds.isEmpty()) {
            java.util.List<com.hms.entity.IpdAdmission> admissions = ipdAdmissionRepository.findAllById(ipdAdmissionIds);
            ipdByAdmissionId = admissions.stream()
                    .collect(java.util.stream.Collectors.toMap(com.hms.entity.IpdAdmission::getId, java.util.function.Function.identity(), (a, b) -> a));
            java.util.List<Long> wardIds = admissions.stream()
                    .map(com.hms.entity.IpdAdmission::getWardId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            if (!wardIds.isEmpty()) {
                wardById = wardRepository.findAllById(wardIds).stream()
                        .collect(java.util.stream.Collectors.toMap(com.hms.entity.Ward::getWardId, java.util.function.Function.identity(), (a, b) -> a));
            }
        }

        // Map list
        java.util.List<java.util.Map<String, Object>> mapped = new java.util.ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        for (com.hms.entity.Billing b : content) {
            java.util.List<com.hms.entity.BillingItem> items = itemsByBillId.getOrDefault(b.getId(), java.util.Collections.emptyList());
            java.util.List<com.hms.entity.BillingMedicine> medicines = medicinesByBillId.getOrDefault(b.getId(), java.util.Collections.emptyList());
            
            java.util.List<com.hms.entity.BillingPayment> payments = paymentsByBillId.getOrDefault(b.getId(), java.util.Collections.emptyList());
            final com.hms.entity.Billing bill0 = b;
            final java.util.Map<Long, com.hms.entity.IpdAdmission> ipdMap = ipdByAdmissionId;
            final java.util.Map<Long, com.hms.entity.Ward> wardMap = wardById;
            com.hms.service.hospital.BillMoney money = com.hms.service.hospital.BillMoney.of(b, items, medicines, payments, () -> {
                if (!"IPD".equalsIgnoreCase(bill0.getBillingType()) || bill0.getIpdAdmissionId() == null) return null;
                com.hms.entity.IpdAdmission ipd = ipdMap.get(bill0.getIpdAdmissionId());
                if (ipd == null || ipd.getWardId() == null) return null;
                com.hms.entity.Ward ward = wardMap.get(ipd.getWardId());
                return ward != null ? ward.getBedPrice() : null;
            });

            java.util.Map<String, Object> asMap = mapper.convertValue(b, java.util.Map.class);
            asMap.put("items", items);
            asMap.put("medicines", medicines);
            // amount = net payable (after discount); grossAmount = the charges before it.
            asMap.put(AMOUNT, money.net());
            putMoney(asMap, money);
            asMap.put("refundedAmount", com.hms.service.hospital.BillMoney.refunded(payments));
            
            if (b.getPatientId() != null) {
                com.hms.entity.Patient p = patientById.get(b.getPatientId());
                if (p != null) {
                    asMap.put("patientName", p.getName());
                }
            }
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
        validateBillingAccess();
        Billing updated = billingService.updateStatus(id, status, paymentMethod, paymentReference);
        return ResponseEntity.ok(updated);
    }

    /**
     * Replace a bill's line items.
     *
     * <p>Transactional and lock-taking. This method empties the bill and rebuilds it: without a
     * transaction, a failure part-way through -- a rejected value, a dropped connection -- left
     * the bill stripped of the items it had and never rebuilt, so charges simply vanished. The
     * lock additionally serialises editing against taking payment, so a payment can never be
     * accepted against a total that is mid-rewrite.
     */
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> updateBillItems(@PathVariable Long id, @RequestBody java.util.List<com.hms.dto.HospitalFeeDTO> items) {
        validateBillingAccess();
        Long hospitalId = securityHelper.getCurrentHospitalId();
        // Lock, validation, line replacement and the total/status re-derivation are one
        // transaction in the service. Here they were separate auto-commits, so the row lock was
        // released at once and a failure part-way left the bill with some or none of its lines.
        Billing billing = billingService.replaceBillItems(id, items);

        // Audit logging
        try {
            auditLogService.logAction(
                    "BILLING_ITEMS_UPDATED",
                    "Billing items updated for bill " + billing.getCustomId() + ".",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "BILLING",
                    billing.getPublicId(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for billing items update", e);
        }

        // Broadcast refresh
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh after billing items update", e);
        }

        return ResponseEntity.ok("Bill items updated successfully");
    }

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

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
    private com.hms.service.hospital.BillingSchedulerService billingSchedulerService;

    @Autowired
    private com.hms.repository.WardRepository wardRepository;

    @Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    @PatientRecordAccess(kind = PatientRecordAccess.Kind.BILL, action = PatientRecordAccess.Action.PRINT, label = "RECEIPT")
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> downloadReceipt(@PathVariable Long id) {
        Billing billing = billingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null || !hospitalId.equals(billing.getHospitalId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        Hospital hospital = hospitalRepository.findById(billing.getHospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));

        Patient patient = patientService.getPatientById(billing.getPatientId());

        java.io.ByteArrayInputStream pdf = pdfService.generateBillingReceiptPdf(hospital, patient, billing);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "inline; filename=receipt_" + billing.getCustomId() + ".pdf");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(pdf));
    }

    /**
     * Everything the patient owes for this hospital stay in one place: the IPD bill and the bill of
     * the OPD visit that led to the admission (e.g. OPD ₹500 + IPD ₹550 = ₹1,050). Read-only — each
     * bill keeps its own items and payments; nothing is merged or rewritten. Cancelled bills are
     * listed for reference but left out of the totals.
     */
    @GetMapping("/ipd/{ipdId}/related-bills")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> getIpdRelatedBills(@PathVariable Long ipdId) {
        validateBillingAccess();
        Long hospitalId = securityHelper.getCurrentHospitalId();
        com.hms.entity.IpdAdmission admission = ipdAdmissionRepository.findByIdAndHospitalId(ipdId, hospitalId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Admission not found"));

        java.util.LinkedHashMap<Long, Billing> bills = new java.util.LinkedHashMap<>();
        if (admission.getSourceOpdId() != null) {
            billingRepository.findByOpdIdAndHospitalId(admission.getSourceOpdId(), hospitalId)
                    .stream().filter(b -> b.getIpdAdmissionId() == null)
                    .forEach(b -> bills.put(b.getId(), b));
        }
        billingRepository.findByIpdAdmissionId(ipdId).stream()
                .filter(b -> hospitalId.equals(b.getHospitalId()))
                .forEach(b -> bills.put(b.getId(), b));

        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        java.math.BigDecimal paid = java.math.BigDecimal.ZERO;
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Billing bill : bills.values()) {
            com.hms.service.hospital.BillMoney money = com.hms.service.hospital.BillMoney.of(bill,
                    billingItemRepository.findByBillingId(bill.getId()),
                    billingMedicineRepository.findByBillingId(bill.getId()),
                    billingPaymentRepository.findByBillingId(bill.getId()),
                    () -> billingService.legacyIpdBedPrice(bill));
            boolean cancelled = "CANCELLED".equalsIgnoreCase(bill.getPaymentStatus());
            Map<String, Object> row = new HashMap<>();
            row.put("billingId", bill.getId());
            row.put("customId", bill.getCustomId());
            row.put("category", bill.getIpdAdmissionId() != null ? "IPD" : "OPD");
            row.put("status", bill.getPaymentStatus());
            row.put("createdAt", bill.getCreatedAt());
            row.put(TOTAL_AMOUNT, money.net());
            putMoney(row, money);
            rows.add(row);
            if (!cancelled) {
                total = total.add(money.net());
                paid = paid.add(money.paid());
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("bills", rows);
        resp.put(TOTAL_AMOUNT, total);
        resp.put("paidAmount", paid);
        resp.put("balance", total.subtract(paid));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/ipd/{ipdId}/bill")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> getIpdBill(@PathVariable Long ipdId) {
        validateBillingAccess();
        // findByIpdAdmissionId is not tenant-scoped, so without this check any hospital could
        // read another hospital's IPD bill — items, payments and patient — by admission id.
        Long ipdBillHospitalId = securityHelper.getCurrentHospitalId();
        List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
        if (bills == null || bills.isEmpty()) return ResponseEntity.notFound().build();
        Billing found = bills.get(0);
        if (found.getHospitalId() == null || !found.getHospitalId().equals(ipdBillHospitalId)) {
            return ResponseEntity.notFound().build();
        }
        // Bed days missed while the server was down are added before the bill is shown.
        ipdAdmissionRepository.findByIdAndHospitalId(ipdId, ipdBillHospitalId)
                .ifPresent(billingSchedulerService::catchUpAdmission);
        final Billing bill = billingRepository.findById(found.getId()).orElse(found);

        List<BillingItem> items = billingItemRepository.findByBillingId(bill.getId());
        List<com.hms.entity.BillingMedicine> medicines = billingMedicineRepository.findByBillingId(bill.getId());
        List<BillingPayment> payments = billingPaymentRepository.findByBillingId(bill.getId());

        com.hms.service.hospital.BillMoney money = com.hms.service.hospital.BillMoney.of(
                bill, items, medicines, payments, () -> billingService.legacyIpdBedPrice(bill));

        Map<String,Object> resp = new HashMap<>();
        resp.put("billingId", bill.getId());
        resp.put("customId", bill.getCustomId());
        resp.put("status", bill.getPaymentStatus());
        putMoney(resp, money);
        resp.put("discountReason", bill.getDiscountReason());
        resp.put(TOTAL_AMOUNT, money.net());
        java.util.List<Map<String,Object>> list = new java.util.ArrayList<>();
        for (BillingItem it : items) {
            Map<String,Object> m = new HashMap<>();
            m.put("description", it.getDescription());
            m.put(AMOUNT, it.getAmount());
            list.add(m);
        }
        resp.put("items", list);

        java.util.List<Map<String,Object>> medList = new java.util.ArrayList<>();
        for (com.hms.entity.BillingMedicine med : medicines) {
            Map<String,Object> m = new HashMap<>();
            m.put("medicineName", med.getMedicineName());
            m.put("quantity", med.getQuantity());
            m.put("unitPrice", med.getUnitPrice());
            m.put(AMOUNT, med.getAmount());
            medList.add(m);
        }
        resp.put("medicines", medList);

        return ResponseEntity.ok(resp);
    }

    public static class PayRequest {
        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.0", message = "Payment amount cannot be negative")
        public BigDecimal amount;

        @Size(max = 30, message = "Payment mode is too long")
        @NoEmoji
        public String mode;

        @Size(max = 100, message = "Payment reference is too long")
        @NoEmoji
        public String reference;

        /** Optional: the same value on every retry of one act of paying (see BillingPayment). */
        @Size(max = 64, message = "Idempotency key is too long")
        @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Invalid idempotency key")
        public String idempotencyKey;
    }

    /**
     * Record a payment against a bill.
     *
     * <p>Transactional and taken under a row lock on the bill. Reading what has already been
     * collected and inserting the new payment must be one serialised step: previously they were
     * not, so two concurrent calls -- a double-clicked "Paid" button, or a retry after a timeout
     * -- both read the same collected figure, both found the amount fitted inside the outstanding
     * balance, and both inserted. The patient was charged twice with no error shown.
     */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{billingId}/pay")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> payBilling(@PathVariable Long billingId, @Valid @RequestBody PayRequest req) {
        validateBillingAccess();
        String role = securityHelper.getCurrentUserRole();
        if (!"RECEPTIONIST".equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role) && !DOCTOR_ROLE.equalsIgnoreCase(role)) {
            return ResponseEntity.status(403).body("Not allowed");
        }

        // Full or part payment, Cash / Card / UPI. The service scopes the bill to the caller's
        // hospital, locks it, checks the amount fits the (discount-aware) balance and records
        // who took the money.
        return ResponseEntity.ok(billingService.recordPayment(billingId, req.amount, req.mode, req.reference,
                req.idempotencyKey));
    }

    public static class DiscountRequest {
        @NotNull(message = "Discount amount is required")
        @DecimalMin(value = "0.0", message = "Discount cannot be negative")
        public BigDecimal amount;
        @Size(max = 255, message = "Reason is too long")
        public String reason;
    }

    public static class RefundRequest {
        @NotNull(message = "Refund amount is required")
        @DecimalMin(value = "0.01", message = "Refund must be greater than zero")
        public BigDecimal amount;
        @Size(max = 30, message = "Mode is too long")
        public String mode;
        @Size(max = 255, message = "Reason is too long")
        public String reason;
        /** The patient was overcharged: also reduce the bill by this amount. */
        public Boolean reduceBill;
    }

    public static class CancelRequest {
        @Size(max = 255, message = "Reason is too long")
        public String reason;
        @Size(max = 30, message = "Mode is too long")
        public String refundMode;
    }

    /** Discount / concession on a bill (any billing user; reason required and audited). */
    @PostMapping("/{billingId}/discount")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> discount(@PathVariable Long billingId, @Valid @RequestBody DiscountRequest req) {
        validateBillingAccess();
        return ResponseEntity.ok(billingService.applyDiscount(billingId, req.amount, req.reason));
    }

    /** Refund money on a bill. Hospital admin only. */
    @PostMapping("/{billingId}/refund")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> refund(@PathVariable Long billingId, @Valid @RequestBody RefundRequest req) {
        return ResponseEntity.ok(billingService.refund(billingId, req.amount, req.mode, req.reason,
                Boolean.TRUE.equals(req.reduceBill)));
    }

    /** Cancel (void) a bill, refunding anything collected. Hospital admin only. */
    @PostMapping("/{billingId}/cancel")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> cancel(@PathVariable Long billingId, @Valid @RequestBody CancelRequest req) {
        return ResponseEntity.ok(billingService.cancel(billingId, req.reason, req.refundMode));
    }

    /** Daily cash-closing report: collections and refunds for one day, by payment mode. */
    @GetMapping("/reports/daily-collection")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> dailyCollection(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        validateBillingAccess();
        return ResponseEntity.ok(billingService.dailyCollection(date != null ? date : java.time.LocalDate.now(java.time.ZoneId.systemDefault())));
    }

    @PatientRecordAccess(kind = PatientRecordAccess.Kind.PATIENT, action = PatientRecordAccess.Action.VIEW, label = "BILLS")
    @GetMapping("/patient/{patientPublicId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> getPatientBills(@PathVariable String patientPublicId) {
        validateBillingAccess();
        Long hospitalId = securityHelper.getCurrentHospitalId();
        com.hms.entity.Patient patient = patientService.getPatientByPublicId(patientPublicId);
        if (patient == null || !patient.getHospitalId().equals(hospitalId)) {
            throw new com.hms.exception.ResourceNotFoundException("Patient not found or unauthorized");
        }

        java.util.List<Billing> patientBills = billingRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId());
        java.util.List<Long> billIds = patientBills.stream().map(Billing::getId).collect(java.util.stream.Collectors.toList());

        java.util.List<com.hms.entity.BillingItem> allItems = billIds.isEmpty() ? java.util.Collections.emptyList() : billingItemRepository.findByBillingIdIn(billIds);
        java.util.List<com.hms.entity.BillingMedicine> allMedicines = billIds.isEmpty() ? java.util.Collections.emptyList() : billingMedicineRepository.findByBillingIdIn(billIds);
        java.util.List<BillingPayment> allPayments = billIds.isEmpty() ? java.util.Collections.emptyList() : billingPaymentRepository.findByBillingIdIn(billIds);

        java.util.Map<Long, java.util.List<com.hms.entity.BillingItem>> itemsByBillId = allItems.stream().collect(java.util.stream.Collectors.groupingBy(com.hms.entity.BillingItem::getBillingId));
        java.util.Map<Long, java.util.List<com.hms.entity.BillingMedicine>> medicinesByBillId = allMedicines.stream().collect(java.util.stream.Collectors.groupingBy(com.hms.entity.BillingMedicine::getBillingId));
        java.util.Map<Long, java.util.List<com.hms.entity.BillingPayment>> paymentsByBillId = allPayments.stream().collect(java.util.stream.Collectors.groupingBy(com.hms.entity.BillingPayment::getBillingId));

        java.util.List<java.util.Map<String, Object>> mapped = new java.util.ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        for (Billing b : patientBills) {
            java.util.Map<String, Object> asMap = mapper.convertValue(b, java.util.Map.class);

            java.util.List<com.hms.entity.BillingItem> items = itemsByBillId.getOrDefault(b.getId(), java.util.Collections.emptyList());
            java.util.List<com.hms.entity.BillingMedicine> medicines = medicinesByBillId.getOrDefault(b.getId(), java.util.Collections.emptyList());
            java.util.List<com.hms.entity.BillingPayment> payments = paymentsByBillId.getOrDefault(b.getId(), java.util.Collections.emptyList());

            final Billing billP = b;
            com.hms.service.hospital.BillMoney money = com.hms.service.hospital.BillMoney.of(
                    b, items, medicines, payments, () -> billingService.legacyIpdBedPrice(billP));

            asMap.put("items", items);
            asMap.put("medicines", medicines);
            asMap.put("payments", payments);
            // amount = net payable (after discount); grossAmount = the charges before it.
            asMap.put(AMOUNT, money.net());
            putMoney(asMap, money);
            asMap.put("refundedAmount", com.hms.service.hospital.BillMoney.refunded(payments));
            asMap.put("patientName", patient.getName());
            mapped.add(asMap);
        }
        return ResponseEntity.ok(mapped);
    }

    public static class CreateBillRequest {
        public Long patientId;
        public Long doctorId;
        public String billingType;
        public BigDecimal amount;
        public String description;
        public String paymentStatus;
        public String paymentMethod;
        public String paymentReference;
        public java.util.List<CreateBillItemDto> items;
    }

    public static class CreateBillItemDto {
        public String description;
        public BigDecimal amount;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<?> createBill(@RequestBody CreateBillRequest req) {
        validateBillingAccess();
        if (req.patientId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Patient ID is required"));
        }
        java.util.List<BillingItem> items = new java.util.ArrayList<>();
        if (req.items != null) {
            for (CreateBillItemDto itemDto : req.items) {
                BillingItem item = new BillingItem();
                item.setDescription(itemDto.description);
                item.setAmount(itemDto.amount != null ? itemDto.amount : BigDecimal.ZERO);
                items.add(item);
            }
        }
        Billing created = billingService.createManualBill(
                req.patientId,
                req.doctorId,
                req.billingType,
                req.amount,
                req.description,
                req.paymentStatus,
                req.paymentMethod,
                req.paymentReference,
                items
        );
        return ResponseEntity.ok(created);
    }
}

