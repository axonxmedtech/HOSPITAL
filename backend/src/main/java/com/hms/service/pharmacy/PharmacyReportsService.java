package com.hms.service.pharmacy;

import com.hms.security.SecurityContextHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pharmacy reports for a date range (all time when no range is given), for one branch when a
 * branch is signed in.
 *
 * <p>Money, as an accountant reads it:
 * <ul>
 *   <li>Gross sales = what customers paid (GST included).</li>
 *   <li>Refunds = what was given back (GST included), recorded per refund in
 *       {@code pharmacy_sale_returns}.</li>
 *   <li>Net sales = gross sales - refunds.</li>
 *   <li>Output GST = GST in sales - GST in refunds. GST is collected for the government and is
 *       not income, so revenue and profit are reported excluding it.</li>
 *   <li>Revenue (excl. GST) = net sales - output GST. Gross profit = revenue - cost of the
 *       medicine sold (refunded medicine is not resold, so its cost stays a cost).</li>
 * </ul>
 * Stock value and expiry risk describe the shelf now, so they ignore the date range.
 */
@Service
@Transactional(readOnly = true)
public class PharmacyReportsService {

    private static final String BRANCH_FILTER = " AND s.branchId = :branchId";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private SecurityContextHelper securityHelper;

    /** The requested window; either end may be open. */
    public record Range(LocalDate from, LocalDate to) {
        public Range {
            requireValidRange(from, to);
        }

        LocalDateTime start() {
            return from == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : from.atStartOfDay();
        }

        LocalDateTime endExclusive() {
            return to == null ? LocalDateTime.of(2999, 1, 1, 0, 0) : to.plusDays(1).atStartOfDay();
        }
    }

    /** Refuses a backwards range (400) before any work is done. */
    public static void requireValidRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The 'from' date is after the 'to' date");
        }
    }

    public Map<String, Object> getReportsDashboard() {
        return getReportsDashboard(new Range(null, null));
    }

    public Map<String, Object> getReportsDashboard(Range range) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long branchId = securityHelper.getCurrentBranchId();
        Map<String, Object> report = new HashMap<>();

        Object[] sales = salesTotals(hospitalId, branchId, range);
        BigDecimal grossSales = (BigDecimal) sales[0];
        BigDecimal salesGst = (BigDecimal) sales[1];
        Object[] refunds = refundTotals(hospitalId, branchId, range);
        BigDecimal refundAmount = (BigDecimal) refunds[0];
        BigDecimal refundGst = (BigDecimal) refunds[1];

        BigDecimal netSales = grossSales.subtract(refundAmount);
        BigDecimal outputGst = salesGst.subtract(refundGst);
        BigDecimal revenueExGst = netSales.subtract(outputGst);
        BigDecimal cogs = costOfGoodsSold(hospitalId, branchId, range);
        BigDecimal grossProfit = revenueExGst.subtract(cogs);
        BigDecimal margin = revenueExGst.signum() > 0
                ? grossProfit.multiply(BigDecimal.valueOf(100)).divide(revenueExGst, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Object[] shelf = shelfSummary(hospitalId, branchId, today);

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalSales", grossSales);
        kpis.put("totalRefunds", refundAmount);
        kpis.put("netSales", netSales);
        kpis.put("netRevenue", revenueExGst);
        kpis.put("cogs", cogs);
        kpis.put("grossProfit", grossProfit);
        kpis.put("profitMargin", margin);
        kpis.put("inventoryValue", shelf[0]);
        kpis.put("expiredValue", shelf[1]);
        report.put("kpis", kpis);

        BigDecimal inputGst = purchaseGst(hospitalId, branchId, range);
        Map<String, Object> taxSummary = new HashMap<>();
        taxSummary.put("inputGst", inputGst);
        taxSummary.put("outputGst", outputGst);
        taxSummary.put("netGstPayable", outputGst.subtract(inputGst));
        report.put("taxSummary", taxSummary);

        Map<String, Object> expiryRisk = new HashMap<>();
        expiryRisk.put("next30Days", shelf[2]);
        expiryRisk.put("next60Days", shelf[3]);
        expiryRisk.put("next90Days", shelf[4]);
        report.put("expiryRisk", expiryRisk);

        report.put("fastMoving", fastMoving(hospitalId, branchId, range));
        report.put("categoryValuation", categoryValuation(hospitalId, branchId, today));
        report.put("salesTrend", salesTrend(hospitalId, branchId, today));
        Map<String, Object> period = new HashMap<>();
        period.put("from", range.from());
        period.put("to", range.to());
        report.put("period", period);
        return report;
    }

    // ── money in the window ─────────────────────────────────────────────────────────────────

    private Object[] salesTotals(Long hospitalId, Long branchId, Range range) {
        String q = "SELECT COALESCE(SUM(s.netAmount), 0), COALESCE(SUM(s.taxAmount), 0) FROM PharmacySale s "
                + "WHERE s.hospitalId = :hospitalId AND s.paymentStatus = 'PAID' "
                + "AND s.createdAt >= :start AND s.createdAt < :end" + (branchId != null ? BRANCH_FILTER : "");
        return scoped(em.createQuery(q, Object[].class), hospitalId, branchId, range).getSingleResult();
    }

    private Object[] refundTotals(Long hospitalId, Long branchId, Range range) {
        String q = "SELECT COALESCE(SUM(s.refundAmount), 0), COALESCE(SUM(s.gstAmount), 0) FROM PharmacySaleReturn s "
                + "WHERE s.hospitalId = :hospitalId AND s.createdAt >= :start AND s.createdAt < :end"
                + (branchId != null ? BRANCH_FILTER : "");
        return scoped(em.createQuery(q, Object[].class), hospitalId, branchId, range).getSingleResult();
    }

    private BigDecimal costOfGoodsSold(Long hospitalId, Long branchId, Range range) {
        String q = "SELECT COALESCE(SUM(si.quantity * b.purchaseRate), 0) FROM PharmacySaleItem si "
                + "JOIN si.pharmacySale s JOIN MedicineBatch b ON si.medicineBatchId = b.id "
                + "WHERE s.hospitalId = :hospitalId AND s.paymentStatus = 'PAID' "
                + "AND s.createdAt >= :start AND s.createdAt < :end" + (branchId != null ? BRANCH_FILTER : "");
        return scoped(em.createQuery(q, BigDecimal.class), hospitalId, branchId, range).getSingleResult();
    }

    private BigDecimal purchaseGst(Long hospitalId, Long branchId, Range range) {
        String q = "SELECT COALESCE(SUM(s.gstAmount), 0) FROM PurchaseInvoice s WHERE s.hospitalId = :hospitalId "
                + "AND s.postingStatus = 'POSTED' AND s.createdAt >= :start AND s.createdAt < :end"
                + (branchId != null ? BRANCH_FILTER : "");
        return scoped(em.createQuery(q, BigDecimal.class), hospitalId, branchId, range).getSingleResult();
    }

    private List<Map<String, Object>> fastMoving(Long hospitalId, Long branchId, Range range) {
        String q = "SELECT m.medicineName, SUM(si.quantity), SUM(si.totalAmount) FROM PharmacySaleItem si "
                + "JOIN si.pharmacySale s JOIN MedicineBatch b ON si.medicineBatchId = b.id "
                + "JOIN MedicineMaster m ON b.medicineId = m.id "
                + "WHERE s.hospitalId = :hospitalId AND s.paymentStatus = 'PAID' "
                + "AND s.createdAt >= :start AND s.createdAt < :end" + (branchId != null ? BRANCH_FILTER : "")
                + " GROUP BY m.medicineName ORDER BY SUM(si.quantity) DESC";
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : scoped(em.createQuery(q, Object[].class), hospitalId, branchId, range)
                .setMaxResults(5).getResultList()) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", row[0]);
            item.put("quantity", row[1]);
            item.put("revenue", row[2]);
            out.add(item);
        }
        return out;
    }

    // ── the shelf today ─────────────────────────────────────────────────────────────────────

    /**
     * [stock value, expired value, expiring in 1-30 days, 31-60, 61-90]. Each window starts the day
     * after the previous one ends, so no day is counted twice. Disposed stock has no quantity.
     */
    private Object[] shelfSummary(Long hospitalId, Long branchId, LocalDate today) {
        String q = "SELECT "
                + "COALESCE(SUM(CASE WHEN b.expiryDate > :today THEN b.currentQuantity * b.purchaseRate ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN b.expiryDate <= :today THEN b.currentQuantity * b.purchaseRate ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN b.expiryDate > :today AND b.expiryDate <= :d30 THEN b.currentQuantity * b.purchaseRate ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN b.expiryDate > :d30 AND b.expiryDate <= :d60 THEN b.currentQuantity * b.purchaseRate ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN b.expiryDate > :d60 AND b.expiryDate <= :d90 THEN b.currentQuantity * b.purchaseRate ELSE 0 END), 0) "
                + "FROM MedicineBatch b WHERE b.hospitalId = :hospitalId AND b.currentQuantity > 0"
                + (branchId != null ? " AND b.branchId = :branchId" : "");
        TypedQuery<Object[]> query = em.createQuery(q, Object[].class)
                .setParameter("hospitalId", hospitalId)
                .setParameter("today", today)
                .setParameter("d30", today.plusDays(30))
                .setParameter("d60", today.plusDays(60))
                .setParameter("d90", today.plusDays(90));
        if (branchId != null) {
            query.setParameter("branchId", branchId);
        }
        return query.getSingleResult();
    }

    private List<Map<String, Object>> categoryValuation(Long hospitalId, Long branchId, LocalDate today) {
        String q = "SELECT COALESCE(c.categoryName, 'Other'), SUM(b.currentQuantity * b.purchaseRate), COUNT(b.id) "
                + "FROM MedicineBatch b JOIN MedicineMaster m ON b.medicineId = m.id "
                + "LEFT JOIN MedicineCategory c ON m.categoryId = c.id "
                + "WHERE b.hospitalId = :hospitalId AND b.currentQuantity > 0 AND b.expiryDate > :today"
                + (branchId != null ? " AND b.branchId = :branchId" : "") + " GROUP BY c.categoryName";
        TypedQuery<Object[]> query = em.createQuery(q, Object[].class)
                .setParameter("hospitalId", hospitalId)
                .setParameter("today", today);
        if (branchId != null) {
            query.setParameter("branchId", branchId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            Map<String, Object> cat = new HashMap<>();
            cat.put("category", row[0]);
            cat.put("value", row[1]);
            cat.put("count", row[2]);
            out.add(cat);
        }
        return out;
    }

    /** The last 7 days, always (a short trend under the totals). */
    private List<Map<String, Object>> salesTrend(Long hospitalId, Long branchId, LocalDate today) {
        Range week = new Range(today.minusDays(6), today);
        Map<String, BigDecimal> salesByDay = byDay(em.createQuery(
                "SELECT s.createdAt, s.netAmount FROM PharmacySale s WHERE s.hospitalId = :hospitalId "
                        + "AND s.paymentStatus = 'PAID' AND s.createdAt >= :start AND s.createdAt < :end"
                        + (branchId != null ? BRANCH_FILTER : ""), Object[].class), hospitalId, branchId, week);
        Map<String, BigDecimal> refundsByDay = byDay(em.createQuery(
                "SELECT s.createdAt, s.refundAmount FROM PharmacySaleReturn s WHERE s.hospitalId = :hospitalId "
                        + "AND s.createdAt >= :start AND s.createdAt < :end"
                        + (branchId != null ? BRANCH_FILTER : ""), Object[].class), hospitalId, branchId, week);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String day = today.minusDays(i).toString();
            Map<String, Object> d = new HashMap<>();
            d.put("date", day);
            d.put("sales", salesByDay.getOrDefault(day, BigDecimal.ZERO));
            d.put("refunds", refundsByDay.getOrDefault(day, BigDecimal.ZERO));
            trend.add(d);
        }
        return trend;
    }

    private Map<String, BigDecimal> byDay(TypedQuery<Object[]> query, Long hospitalId, Long branchId, Range range) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (Object[] row : scoped(query, hospitalId, branchId, range).getResultList()) {
            if (row[0] instanceof LocalDateTime when && row[1] instanceof BigDecimal amount) {
                out.merge(when.toLocalDate().toString(), amount, BigDecimal::add);
            }
        }
        return out;
    }

    private static <T> TypedQuery<T> scoped(TypedQuery<T> query, Long hospitalId, Long branchId, Range range) {
        query.setParameter("hospitalId", hospitalId)
                .setParameter("start", range.start())
                .setParameter("end", range.endExclusive());
        if (branchId != null) {
            query.setParameter("branchId", branchId);
        }
        return query;
    }
}
