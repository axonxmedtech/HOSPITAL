package com.hms.controller.pharmacy;

import com.hms.security.RequireModule;
import com.hms.service.pharmacy.PharmacyReportsService;
import com.hms.entity.pharmacy.PharmacySale;
import com.hms.security.SecurityContextHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/pharmacy/reports")
@RequireModule("PHARMACY")
public class PharmacyReportsController {

    @Autowired
    private PharmacyReportsService reportsService;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private SecurityContextHelper securityHelper;

    /** Reports for an optional date range (ISO dates, both ends included); all time without one. */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getReportsDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportsService.getReportsDashboard(new PharmacyReportsService.Range(from, to)));
    }

    /**
     * Sales ledger as CSV for the same optional date range, for one branch when a branch is
     * signed in. Customer names are validated at entry (letters, spaces, . ' -), so no cell can
     * start a spreadsheet formula.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'HOSPITAL_ADMIN')")
    public ResponseEntity<byte[]> exportLedgerCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        PharmacyReportsService.requireValidRange(from, to);
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long branchId = securityHelper.getCurrentBranchId();

        List<PharmacySale> sales = em.createQuery(
            "SELECT s FROM PharmacySale s WHERE s.hospitalId = :hospitalId "
                + "AND (:branchId IS NULL OR s.branchId = :branchId) "
                + "AND s.createdAt >= :start AND s.createdAt < :end ORDER BY s.createdAt DESC", PharmacySale.class)
            .setParameter("hospitalId", hospitalId)
            .setParameter("branchId", branchId)
            .setParameter("start", from == null ? LocalDate.of(2000, 1, 1).atStartOfDay() : from.atStartOfDay())
            .setParameter("end", to == null ? LocalDate.of(2999, 1, 1).atStartOfDay() : to.plusDays(1).atStartOfDay())
            .getResultList();

        StringBuilder csv = new StringBuilder();
        csv.append("Date,Invoice Number,Customer,Gross (INR),Discount (INR),Net Paid (INR),GST Included (INR),Payment Method,Payment Status\n");
        for (PharmacySale sale : sales) {
            csv.append(sale.getCreatedAt() != null ? sale.getCreatedAt().toString().replace("T", " ") : "N/A").append(",");
            csv.append(sale.getBillNumber() != null ? sale.getBillNumber() : "N/A").append(",");
            csv.append(sale.getPatientName() != null ? sale.getPatientName().replace(",", " ") : "Walk-in").append(",");
            csv.append(amount(sale.getSubtotal())).append(",");
            csv.append(amount(sale.getDiscountAmount())).append(",");
            csv.append(amount(sale.getNetAmount())).append(",");
            csv.append(amount(sale.getTaxAmount())).append(",");
            csv.append(sale.getPaymentMethod() != null ? sale.getPaymentMethod() : "N/A").append(",");
            csv.append(sale.getPaymentStatus() != null ? sale.getPaymentStatus() : "N/A").append("\n");
        }

        byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", "pharmacy_sales"
                + (from != null ? "_" + from : "") + (to != null ? "_to_" + to : "") + ".csv");
        headers.setContentLength(csvBytes.length);
        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    private static String amount(java.math.BigDecimal v) {
        return v == null ? "0" : v.toPlainString();
    }
}
