package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.entity.User;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.entity.pharmacy.MedicineMaster;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.repository.pharmacy.MedicineMasterRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pharmacy counter through the real API: the server prices every bill itself (prices include
 * GST, never above MRP), refuses expired / blocked stock and nonsense bills, numbers invoices
 * consecutively per facility, refunds no more than was paid, and prints a tax invoice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:pharmacy_counter_sale_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class PharmacyCounterSaleApiTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final List<String> MODULES = List.of("PHARMACY");
    private static final Pattern SERIAL = Pattern.compile("PH/\\d{2}-\\d{2}/(\\d{5})");

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitals;
    @Autowired UserRepository users;
    @Autowired MedicineMasterRepository medicines;
    @Autowired MedicineBatchRepository batches;

    private final ObjectMapper json = new ObjectMapper();
    private Hospital pharmacy;
    private String pharmacist, admin;

    private static long uniq() { return System.nanoTime() + SEQ.incrementAndGet(); }

    private Hospital newPharmacy(String name) {
        Hospital h = new Hospital();
        h.setName(name);
        h.setCustomId("PID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        h.setType(HospitalType.PHARMACY);
        return hospitals.save(h);
    }

    private String token(Hospital h, String role) {
        User u = new User();
        u.setEmail(role.toLowerCase() + "." + uniq() + "@ph.test");
        u.setPassword("{noop}x");
        u.setName("Staff " + role);
        u.setRole(role);
        u.setHospitalId(h.getId());
        u.setIsActive(true);
        u.setTokenVersion(0);
        u = users.saveAndFlush(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), role, h.getId(), MODULES, null, "PHARMACY", null,
                u.getTokenVersion());
    }

    private MedicineBatch stock(Hospital h, String price, String mrp, String gst, LocalDate expiry, String status) {
        MedicineMaster m = new MedicineMaster();
        m.setHospitalId(h.getId());
        m.setMedicineName("Paracetamol " + uniq());
        m.setHsnCode("3004");
        m = medicines.save(m);
        MedicineBatch b = new MedicineBatch();
        b.setHospitalId(h.getId());
        b.setMedicineId(m.getId());
        b.setBatchNumber("B" + uniq());
        b.setExpiryDate(expiry);
        b.setMrp(new BigDecimal(mrp));
        b.setPurchaseRate(new BigDecimal("60"));
        b.setSellingPrice(new BigDecimal(price));
        b.setGstPercentage(new BigDecimal(gst));
        b.setCurrentQuantity(new BigDecimal("50"));
        b.setStatus(status);
        return batches.save(b);
    }

    private MedicineBatch goodStock() {
        return stock(pharmacy, "100", "100", "12", LocalDate.now().plusYears(1), "ACTIVE");
    }

    @BeforeEach
    void setUp() {
        pharmacy = newPharmacy("Counter Pharmacy");
        pharmacist = token(pharmacy, "PHARMACIST");
        admin = token(pharmacy, "HOSPITAL_ADMIN");
    }

    private ResponseEntity<String> call(HttpMethod method, String url, String tok, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tok);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url, method, new HttpEntity<>(body, h), String.class);
    }

    /** A sale request; the browser-side amounts are deliberately wrong to prove they are ignored. */
    private Map<String, Object> saleBody(MedicineBatch b, int qty, String discount, String paymentMethod) {
        Map<String, Object> item = new HashMap<>();
        item.put("medicineId", b.getMedicineId());
        item.put("medicineBatchId", b.getId());
        item.put("quantity", qty);
        item.put("unitPrice", 1);
        item.put("taxPercentage", 0);
        item.put("taxAmount", 0);
        item.put("totalAmount", 1);
        Map<String, Object> body = new HashMap<>();
        body.put("patientName", "Walk in");
        body.put("paymentMethod", paymentMethod);
        body.put("subtotal", 1);
        body.put("taxAmount", 0);
        body.put("discountAmount", discount);
        body.put("netAmount", 1);
        body.put("items", new ArrayList<>(List.of(item)));
        return body;
    }

    private JsonNode sell(MedicineBatch b, int qty, String discount) throws Exception {
        ResponseEntity<String> res = call(HttpMethod.POST, "/pharmacy/sales", pharmacist, saleBody(b, qty, discount, "CASH"));
        assertThat(res.getStatusCode()).as(res.getBody()).isEqualTo(HttpStatus.OK);
        return json.readTree(res.getBody());
    }

    private void refused(Map<String, Object> body, String messagePart) {
        ResponseEntity<String> res = call(HttpMethod.POST, "/pharmacy/sales", pharmacist, body);
        assertThat(res.getStatusCode().is4xxClientError()).as(res.getBody()).isTrue();
        assertThat(res.getBody()).containsIgnoringCase(messagePart);
    }

    @Test
    void theServerPricesTheBillFromTheShelfAndNeverAboveMrp() throws Exception {
        JsonNode sale = sell(goodStock(), 1, "0");

        assertThat(sale.path("netAmount").decimalValue()).isEqualByComparingTo("100.00"); // not 112, not 1
        assertThat(sale.path("taxAmount").decimalValue()).isEqualByComparingTo("10.71");
        assertThat(sale.path("subtotal").decimalValue()).isEqualByComparingTo("100.00");
        JsonNode line = sale.path("items").get(0);
        assertThat(line.path("unitPrice").decimalValue()).isEqualByComparingTo("100");
        assertThat(line.path("totalAmount").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void aBillDiscountIsAppliedOnTheServerAndBounded() throws Exception {
        MedicineBatch b = goodStock();
        JsonNode sale = sell(b, 2, "26");
        assertThat(sale.path("netAmount").decimalValue()).isEqualByComparingTo("174.00");

        refused(saleBody(b, 1, "100.01", "CASH"), "cannot be more than the bill");
        refused(saleBody(b, 1, "-5", "CASH"), "negative");
    }

    @Test
    void nonsenseBillsAreRefused() {
        MedicineBatch b = goodStock();
        Map<String, Object> empty = saleBody(b, 1, "0", "CASH");
        empty.put("items", new ArrayList<>());
        refused(empty, "at least one medicine");
        refused(saleBody(b, 1, "0", "MONOPOLY MONEY"), "Payment method");
        refused(saleBody(b, 51, "0", "CASH"), "Insufficient stock");
    }

    @Test
    void expiredAndBlockedStockCannotBeSold() {
        MedicineBatch expired = stock(pharmacy, "100", "100", "12", LocalDate.now().minusDays(1), "ACTIVE");
        MedicineBatch expiresToday = stock(pharmacy, "100", "100", "12", LocalDate.now(), "ACTIVE");
        MedicineBatch blocked = stock(pharmacy, "100", "100", "12", LocalDate.now().plusYears(1), "BLOCKED");
        MedicineBatch disposed = stock(pharmacy, "100", "100", "12", LocalDate.now().plusYears(1), "DISPOSED");

        refused(saleBody(expired, 1, "0", "CASH"), "expired");
        refused(saleBody(expiresToday, 1, "0", "CASH"), "expired");
        refused(saleBody(blocked, 1, "0", "CASH"), "blocked");
        refused(saleBody(disposed, 1, "0", "CASH"), "disposed");
        assertThat(batches.findById(expired.getId()).orElseThrow().getCurrentQuantity()).isEqualByComparingTo("50");
    }

    @Test
    void invoiceNumbersAreConsecutivePerFacility() throws Exception {
        MedicineBatch b = goodStock();
        String first = sell(b, 1, "0").path("billNumber").asText();
        String second = sell(b, 1, "0").path("billNumber").asText();

        var m1 = SERIAL.matcher(first);
        var m2 = SERIAL.matcher(second);
        assertThat(m1.matches()).as(first).isTrue();
        assertThat(m2.matches()).as(second).isTrue();
        assertThat(Integer.parseInt(m2.group(1))).isEqualTo(Integer.parseInt(m1.group(1)) + 1);
        assertThat(first.length()).isLessThanOrEqualTo(16);

        // Another pharmacy starts its own series; the same number is allowed there.
        Hospital other = newPharmacy("Other Pharmacy");
        String otherPharmacist = token(other, "PHARMACIST");
        MedicineBatch ob = stock(other, "50", "50", "5", LocalDate.now().plusYears(1), "ACTIVE");
        ResponseEntity<String> res = call(HttpMethod.POST, "/pharmacy/sales", otherPharmacist, saleBody(ob, 1, "0", "UPI"));
        assertThat(json.readTree(res.getBody()).path("billNumber").asText()).endsWith("/00001");
    }

    @Test
    void aRefundIsNeverMoreThanWhatWasPaid() throws Exception {
        MedicineBatch b = goodStock();
        JsonNode sale = sell(b, 2, "26"); // paid 174 for 2

        ResponseEntity<String> res = call(HttpMethod.POST, "/pharmacy/sales/" + sale.path("id").asLong() + "/return",
                pharmacist, List.of(Map.of("medicineBatchId", b.getId(), "quantityToReturn", 2)));
        assertThat(res.getStatusCode()).as(res.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(res.getBody()).path("refundAmount").decimalValue()).isEqualByComparingTo("174.00");
    }

    @Test
    void theInvoiceIsATaxInvoiceWithTheRegistrationDetails() throws Exception {
        ResponseEntity<String> put = call(HttpMethod.PUT, "/pharmacy/settings/invoice-details", admin,
                Map.of("gstin", "27abcde1234f1z5", "drugLicenseNumber", "20B/MH/123, 21B/MH/124"));
        assertThat(put.getStatusCode()).as(put.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(put.getBody()).path("gstin").asText()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(hospitals.findById(pharmacy.getId()).orElseThrow().getDrugLicenseNumber()).isEqualTo("20B/MH/123, 21B/MH/124");

        JsonNode sale = sell(goodStock(), 1, "0");
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(pharmacist);
        ResponseEntity<byte[]> pdf = rest.exchange("/pharmacy/sales/" + sale.path("id").asLong() + "/pdf",
                HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void onlyTheAdminChangesTheInvoiceDetailsAndTheGstinIsChecked() throws Exception {
        assertThat(call(HttpMethod.PUT, "/pharmacy/settings/invoice-details", admin,
                Map.of("gstin", "12345", "drugLicenseNumber", "X")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PUT, "/pharmacy/settings/invoice-details", pharmacist,
                Map.of("gstin", "", "drugLicenseNumber", "X")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> get = call(HttpMethod.GET, "/pharmacy/settings/invoice-details", pharmacist, null);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(get.getBody()).path("complete").asBoolean()).isFalse();
    }
}
