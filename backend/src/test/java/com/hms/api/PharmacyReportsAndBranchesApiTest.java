package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.AuditLog;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.entity.User;
import com.hms.entity.pharmacy.InventoryTransaction;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.entity.pharmacy.MedicineMaster;
import com.hms.entity.pharmacy.PharmacyBranch;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.pharmacy.InventoryTransactionRepository;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.repository.pharmacy.MedicineMasterRepository;
import com.hms.repository.pharmacy.PharmacyBranchRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pharmacy Phase B through the real API: reports that count refunds and exclude GST, with a date
 * range; branches that see only their own figures; no doctor / receptionist / patient screens for
 * a pharmacy; an audit trail for money and stock changes; low-stock levels that actually alert.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:pharmacy_reports_branches_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class PharmacyReportsAndBranchesApiTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final List<String> MODULES = List.of("PHARMACY", "MULTI_PHARMACY");

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitals;
    @Autowired UserRepository users;
    @Autowired MedicineMasterRepository medicines;
    @Autowired MedicineBatchRepository batches;
    @Autowired PharmacyBranchRepository branches;
    @Autowired InventoryTransactionRepository transactions;
    @Autowired AuditLogRepository auditLogs;

    private final ObjectMapper json = new ObjectMapper();
    private Hospital pharmacy;
    private String admin, pharmacist;

    private static long uniq() { return System.nanoTime() + SEQ.incrementAndGet(); }

    private Hospital newPharmacy() {
        Hospital h = new Hospital();
        h.setName("Chain Pharmacy " + uniq());
        h.setCustomId("PID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        h.setType(HospitalType.PHARMACY);
        return hospitals.save(h);
    }

    private String token(String role, Long branchId) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "." + uniq() + "@chain.test");
        u.setPassword("{noop}x");
        u.setName("Staff " + role);
        u.setRole(role);
        u.setHospitalId(pharmacy.getId());
        u.setBranchId(branchId);
        u.setIsActive(true);
        u.setTokenVersion(0);
        u = users.saveAndFlush(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), role, pharmacy.getId(), MODULES, branchId, "PHARMACY", null,
                u.getTokenVersion());
    }

    private Long branch(String name) {
        PharmacyBranch b = new PharmacyBranch();
        b.setHospitalId(pharmacy.getId());
        b.setName(name);
        b.setIsActive(true);
        return branches.save(b).getId();
    }

    private MedicineBatch stock(Long branchId, String qty, LocalDate expiry, String status, int minLevel) {
        MedicineMaster m = new MedicineMaster();
        m.setHospitalId(pharmacy.getId());
        m.setBranchId(branchId);
        m.setMedicineName("Azithro " + uniq());
        m.setMinStockLevel(minLevel);
        m = medicines.save(m);
        return batch(m, branchId, qty, expiry, status);
    }

    private MedicineBatch batch(MedicineMaster m, Long branchId, String qty, LocalDate expiry, String status) {
        MedicineBatch b = new MedicineBatch();
        b.setHospitalId(pharmacy.getId());
        b.setBranchId(branchId);
        b.setMedicineId(m.getId());
        b.setBatchNumber("B" + uniq());
        b.setExpiryDate(expiry);
        b.setMrp(new BigDecimal("100"));
        b.setPurchaseRate(new BigDecimal("60"));
        b.setSellingPrice(new BigDecimal("100"));
        b.setGstPercentage(new BigDecimal("12"));
        b.setCurrentQuantity(new BigDecimal(qty));
        b.setStatus(status);
        return batches.save(b);
    }

    private MedicineBatch goodStock(Long branchId) {
        return stock(branchId, "50", LocalDate.now().plusYears(1), "ACTIVE", 0);
    }

    @BeforeEach
    void setUp() {
        // The default JDK connection cannot send PATCH; the java.net.http client can.
        rest.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        pharmacy = newPharmacy();
        admin = token("HOSPITAL_ADMIN", null);
        pharmacist = token("PHARMACIST", null);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, String tok, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tok);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url, method, new HttpEntity<>(body, h), String.class);
    }

    private JsonNode ok(HttpMethod method, String url, String tok, Object body) throws Exception {
        ResponseEntity<String> res = call(method, url, tok, body);
        assertThat(res.getStatusCode()).as(url + " → " + res.getBody()).isEqualTo(HttpStatus.OK);
        return json.readTree(res.getBody());
    }

    private JsonNode sell(String tok, MedicineBatch b, int qty, String discount) throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("medicineBatchId", b.getId());
        item.put("quantity", qty);
        Map<String, Object> body = new HashMap<>();
        body.put("patientName", "Walk in");
        body.put("paymentMethod", "CASH");
        body.put("discountAmount", discount);
        body.put("items", new ArrayList<>(List.of(item)));
        return ok(HttpMethod.POST, "/pharmacy/sales", tok, body);
    }

    private JsonNode refund(String tok, JsonNode sale, MedicineBatch b, int qty) throws Exception {
        return ok(HttpMethod.POST, "/pharmacy/sales/" + sale.path("id").asLong() + "/return", tok,
                List.of(Map.of("medicineBatchId", b.getId(), "quantityToReturn", qty)));
    }

    private static BigDecimal kpi(JsonNode report, String name) {
        return report.path("kpis").path(name).decimalValue();
    }

    // ── #7 reports ─────────────────────────────────────────────────────────────────────────

    @Test
    void reportsCountRefundsAndReportRevenueAndProfitWithoutGst() throws Exception {
        MedicineBatch b = goodStock(null);
        JsonNode sale = sell(pharmacist, b, 2, "26"); // paid 174
        refund(pharmacist, sale, b, 1);               // gave back 87

        JsonNode r = ok(HttpMethod.GET, "/pharmacy/reports/dashboard", admin, null);
        assertThat(kpi(r, "totalSales")).isEqualByComparingTo("174.00");
        assertThat(kpi(r, "totalRefunds")).isEqualByComparingTo("87.00");
        assertThat(kpi(r, "netSales")).isEqualByComparingTo("87.00");
        // GST is not income: 87 paid incl. 12% GST = 77.68 revenue; cost of the 2 sold = 120
        assertThat(kpi(r, "netRevenue")).isEqualByComparingTo("77.68");
        assertThat(kpi(r, "grossProfit")).isEqualByComparingTo("-42.32");
        assertThat(r.path("taxSummary").path("outputGst").decimalValue()).isEqualByComparingTo("9.32");
    }

    @Test
    void theProfitMarginIsWorkedOutWithoutGst() throws Exception {
        sell(pharmacist, goodStock(null), 1, "0"); // 100 incl. GST 10.71, cost 60

        JsonNode r = ok(HttpMethod.GET, "/pharmacy/reports/dashboard", admin, null);
        assertThat(kpi(r, "netRevenue")).isEqualByComparingTo("89.29");
        assertThat(kpi(r, "grossProfit")).isEqualByComparingTo("29.29");
        assertThat(kpi(r, "profitMargin")).isEqualByComparingTo("32.80");
    }

    @Test
    void reportsAndTheCsvFollowTheDateRange() throws Exception {
        sell(pharmacist, goodStock(null), 1, "0");
        String today = LocalDate.now().toString();
        String lastWeek = LocalDate.now().minusDays(7).toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        assertThat(kpi(ok(HttpMethod.GET, "/pharmacy/reports/dashboard?from=" + today + "&to=" + today, admin, null),
                "totalSales")).isEqualByComparingTo("100.00");
        assertThat(kpi(ok(HttpMethod.GET, "/pharmacy/reports/dashboard?from=" + lastWeek + "&to=" + yesterday, admin, null),
                "totalSales")).isEqualByComparingTo("0");
        assertThat(call(HttpMethod.GET, "/pharmacy/reports/dashboard?from=" + today + "&to=" + yesterday, admin, null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String csvToday = call(HttpMethod.GET, "/pharmacy/reports/export?from=" + today, admin, null).getBody();
        String csvOld = call(HttpMethod.GET, "/pharmacy/reports/export?to=" + yesterday, admin, null).getBody();
        assertThat(csvToday).contains("PH/");
        assertThat(csvOld).doesNotContain("PH/");
    }

    // ── #8 branches ────────────────────────────────────────────────────────────────────────

    @Test
    void aBranchSeesOnlyItsOwnFiguresLedgersAndReturns() throws Exception {
        Long north = branch("North");
        Long south = branch("South");
        String northPh = token("PHARMACIST", north);
        String southPh = token("PHARMACIST", south);
        MedicineBatch nb = goodStock(north);
        JsonNode sale = sell(northPh, nb, 3, "0");
        refund(northPh, sale, nb, 1);

        JsonNode southStats = ok(HttpMethod.GET, "/pharmacy/sales/stats", southPh, null);
        assertThat(southStats.path("todaySalesTotal").decimalValue()).isEqualByComparingTo("0");
        assertThat(ok(HttpMethod.GET, "/pharmacy/sales/stats", northPh, null).path("todaySalesTotal").decimalValue())
                .isEqualByComparingTo("300.00");

        assertThat(call(HttpMethod.GET, "/pharmacy/reports/export", southPh, null).getBody())
                .doesNotContain(sale.path("billNumber").asText());
        assertThat(call(HttpMethod.GET, "/pharmacy/inventory/transactions/" + nb.getId(), southPh, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/pharmacy/inventory/returns-history", southPh, null).getBody())
                .doesNotContain(sale.path("billNumber").asText());
        assertThat(call(HttpMethod.GET, "/pharmacy/inventory/returns-history", northPh, null).getBody())
                .contains(sale.path("billNumber").asText());

        ok(HttpMethod.POST, "/pharmacy/inventory/adjust", northPh,
                Map.of("batchId", nb.getId(), "quantity", -1, "remarks", "damaged strip"));
        List<InventoryTransaction> ledger = transactions.findByMedicineBatchIdOrderByCreatedAtDesc(nb.getId(), Pageable.unpaged()).getContent();
        assertThat(ledger).filteredOn(t -> "ADJUSTMENT".equals(t.getTransactionType()))
                .singleElement().satisfies(t -> assertThat(t.getBranchId()).isEqualTo(north));
    }

    // ── #9 people screens ──────────────────────────────────────────────────────────────────

    @Test
    void aPharmacyHasNoDoctorReceptionistOrPatientScreens() {
        assertThat(call(HttpMethod.GET, "/pharmacy/doctors", admin, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/pharmacy/doctors", admin, Map.of("name", "X", "email", "x" + uniq() + "@x.test",
                "phone", "9712345678", "specialization", "General", "password", "Staff#Pass2026!")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/pharmacy/receptionists", admin, Map.of("name", "Y", "email", "y" + uniq() + "@x.test",
                "phone", "9612345678", "password", "Staff#Pass2026!")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/pharmacy/patients", admin, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/hospital/patients", admin, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // the pharmacy's own work is untouched
        assertThat(call(HttpMethod.GET, "/pharmacy/inventory", admin, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── #10 audit ──────────────────────────────────────────────────────────────────────────

    @Test
    void moneyAndStockChangesLeaveAnAuditTrail() throws Exception {
        MedicineBatch b = goodStock(null);
        JsonNode sale = sell(pharmacist, b, 2, "0");
        refund(pharmacist, sale, b, 1);
        ok(HttpMethod.POST, "/pharmacy/inventory/adjust", pharmacist, Map.of("batchId", b.getId(), "quantity", 2, "remarks", "physical count"));
        ok(HttpMethod.POST, "/pharmacy/inventory/batches/" + b.getId() + "/block", pharmacist, null);
        ok(HttpMethod.POST, "/pharmacy/inventory/batches/" + b.getId() + "/dispose", pharmacist, Map.of("remarks", "damaged"));

        List<String> actions = auditLogs.findByHospitalIdOrderByTimestampDesc(pharmacy.getId()).stream()
                .map(AuditLog::getAction).toList();
        assertThat(actions).contains("PHARMACY_REFUND", "STOCK_ADJUSTED", "BATCH_BLOCKED", "BATCH_DISPOSED");
        AuditLog adjust = auditLogs.findByHospitalIdOrderByTimestampDesc(pharmacy.getId()).stream()
                .filter(a -> "STOCK_ADJUSTED".equals(a.getAction())).findFirst().orElseThrow();
        assertThat(adjust.getReason()).isEqualTo("physical count");
        assertThat(adjust.getPerformedByRole()).isEqualTo("PHARMACIST");
    }

    @Test
    void aStockCorrectionNeedsAReason() {
        MedicineBatch b = goodStock(null);
        assertThat(call(HttpMethod.POST, "/pharmacy/inventory/adjust", pharmacist,
                Map.of("batchId", b.getId(), "quantity", -2)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/pharmacy/inventory/adjust", pharmacist,
                Map.of("batchId", b.getId(), "quantity", 0, "remarks", "count")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── #11 low stock ──────────────────────────────────────────────────────────────────────

    @Test
    void aLowStockLevelAlertsOnSellableStockOnly() throws Exception {
        MedicineBatch good = goodStock(null); // 50 sellable
        MedicineMaster m = medicines.findById(good.getMedicineId()).orElseThrow();
        batch(m, null, "100", LocalDate.now().minusDays(2), "ACTIVE"); // expired: does not count
        batch(m, null, "100", LocalDate.now().plusYears(1), "BLOCKED"); // blocked: does not count

        assertThat(ok(HttpMethod.GET, "/pharmacy/inventory/low-stock", pharmacist, null).path("content")).isEmpty();

        ok(HttpMethod.PATCH, "/pharmacy/medicines/" + m.getId() + "/stock-levels", pharmacist, Map.of("minStockLevel", 60));
        JsonNode low = ok(HttpMethod.GET, "/pharmacy/inventory/low-stock", pharmacist, null).path("content");
        assertThat(low).hasSize(1);
        assertThat(low.get(0).path("currentQuantity").decimalValue()).isEqualByComparingTo("50");

        assertThat(call(HttpMethod.PATCH, "/pharmacy/medicines/" + m.getId() + "/stock-levels", pharmacist,
                Map.of("minStockLevel", -1)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Hospital other = hospitals.save(newPharmacyLike());
        MedicineMaster foreign = new MedicineMaster();
        foreign.setHospitalId(other.getId());
        foreign.setMedicineName("Foreign " + uniq());
        foreign = medicines.save(foreign);
        assertThat(call(HttpMethod.PATCH, "/pharmacy/medicines/" + foreign.getId() + "/stock-levels", pharmacist,
                Map.of("minStockLevel", 5)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Hospital newPharmacyLike() {
        Hospital h = new Hospital();
        h.setName("Other " + uniq());
        h.setCustomId("PID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        h.setType(HospitalType.PHARMACY);
        return h;
    }
}
