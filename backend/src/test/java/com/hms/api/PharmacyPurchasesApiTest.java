package com.hms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.entity.User;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.pharmacy.MedicineBatchRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pharmacy Phase C through the real API: no duplicate masters; purchases checked and totalled on
 * the server; a re-used batch number cannot change expiry or revive a disposed batch; drafts
 * edited or discarded, posted purchases cancelled only while their stock is intact; supplier
 * payments; unblocking a batch; numbered supplier return notes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:pharmacy_purchases_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class PharmacyPurchasesApiTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final List<String> MODULES = List.of("PHARMACY");

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitals;
    @Autowired UserRepository users;
    @Autowired MedicineBatchRepository batches;

    private final ObjectMapper json = new ObjectMapper();
    private Hospital pharmacy;
    private String ph;
    private long supplierId;

    private static long uniq() { return System.nanoTime() + SEQ.incrementAndGet(); }

    private Hospital newPharmacy() {
        Hospital h = new Hospital();
        h.setName("Purchase Pharmacy " + uniq());
        h.setCustomId("PID-" + uniq());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        h.setType(HospitalType.PHARMACY);
        return hospitals.save(h);
    }

    private String token(Hospital h) {
        User u = new User();
        u.setEmail("ph." + uniq() + "@buy.test");
        u.setPassword("{noop}x");
        u.setName("Pharmacist");
        u.setRole("PHARMACIST");
        u.setHospitalId(h.getId());
        u.setIsActive(true);
        u.setTokenVersion(0);
        u = users.saveAndFlush(u);
        return jwtUtil.generateToken(u.getId(), u.getEmail(), "PHARMACIST", h.getId(), MODULES, null, "PHARMACY", null,
                u.getTokenVersion());
    }

    @BeforeEach
    void setUp() throws Exception {
        rest.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        pharmacy = newPharmacy();
        ph = token(pharmacy);
        supplierId = supplier(ph, "Mahaveer Distributors " + uniq(), phone()).path("id").asLong();
    }

    private static String phone() {
        return String.format("9%09d", uniq() % 1_000_000_000L);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, String tok, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tok);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url, method, new HttpEntity<>(body, h), String.class);
    }

    private JsonNode ok(HttpMethod method, String url, Object body) throws Exception {
        ResponseEntity<String> res = call(method, url, ph, body);
        assertThat(res.getStatusCode()).as(url + " → " + res.getBody()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        return json.readTree(res.getBody());
    }

    private void refused(HttpMethod method, String url, Object body, String messagePart) {
        ResponseEntity<String> res = call(method, url, ph, body);
        assertThat(res.getStatusCode().is4xxClientError()).as(url + " → " + res.getBody()).isTrue();
        assertThat(res.getBody()).containsIgnoringCase(messagePart);
    }

    private JsonNode supplier(String tok, String name, String phone) throws Exception {
        ResponseEntity<String> res = call(HttpMethod.POST, "/pharmacy/suppliers", tok, Map.of("supplierName", name, "phone", phone));
        assertThat(res.getStatusCode().is2xxSuccessful()).as(res.getBody()).isTrue();
        return json.readTree(res.getBody());
    }

    private Map<String, Object> line(String medicine, String batch, LocalDate expiry, int qty) {
        Map<String, Object> l = new HashMap<>();
        l.put("medicineName", medicine);
        l.put("batchNumber", batch);
        l.put("expiryDate", expiry.toString());
        l.put("quantity", qty);
        l.put("freeQuantity", 0);
        l.put("purchaseRate", 60);
        l.put("mrp", 100);
        l.put("sellingPrice", 100);
        l.put("gstPercentage", 12);
        l.put("lineTotal", 1);
        return l;
    }

    private Map<String, Object> purchase(long supplier, String invoiceNo, String status, List<Map<String, Object>> lines) {
        Map<String, Object> b = new HashMap<>();
        b.put("supplierId", supplier);
        b.put("invoiceNumber", invoiceNo);
        b.put("invoiceDate", LocalDate.now().toString());
        b.put("subtotal", 1);
        b.put("discountAmount", 0);
        b.put("gstAmount", 0);
        b.put("totalAmount", 1); // deliberately wrong: the server works it out
        b.put("postingStatus", status);
        b.put("items", new ArrayList<>(lines));
        return b;
    }

    private final LocalDate expiry = LocalDate.now().plusYears(1);

    private MedicineBatch batchOf(String batchNumber) {
        return batches.findAll().stream()
                .filter(b -> pharmacy.getId().equals(b.getHospitalId()) && batchNumber.equals(b.getBatchNumber()))
                .findFirst().orElseThrow();
    }

    // ── masters ────────────────────────────────────────────────────────────────────────────

    @Test
    void duplicateSuppliersCategoriesAndManufacturersAreRefused() throws Exception {
        String name = "Shree Pharma " + uniq();
        String p = phone();
        supplier(ph, name, p);
        refused(HttpMethod.POST, "/pharmacy/suppliers", Map.of("supplierName", "  " + name.toUpperCase() + " ", "phone", phone()), "already exists");
        refused(HttpMethod.POST, "/pharmacy/suppliers", Map.of("supplierName", "Other " + uniq(), "phone", p), "already uses phone");

        String cat = "Antibiotics " + uniq();
        ok(HttpMethod.POST, "/pharmacy/categories", Map.of("categoryName", cat));
        refused(HttpMethod.POST, "/pharmacy/categories", Map.of("categoryName", cat.toLowerCase()), "already exists");

        String maker = "Cipla " + uniq();
        ok(HttpMethod.POST, "/pharmacy/manufacturers", Map.of("manufacturerName", maker));
        refused(HttpMethod.POST, "/pharmacy/manufacturers", Map.of("manufacturerName", maker), "already exists");
    }

    // ── purchase checks ────────────────────────────────────────────────────────────────────

    @Test
    void thePurchaseIsTotalledOnTheServer() throws Exception {
        JsonNode inv = ok(HttpMethod.POST, "/pharmacy/purchases",
                purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Pan 40", "P" + uniq(), expiry, 100))));
        assertThat(inv.path("subtotal").decimalValue()).isEqualByComparingTo("6000.00");
        assertThat(inv.path("gstAmount").decimalValue()).isEqualByComparingTo("720.00");
        assertThat(inv.path("totalAmount").decimalValue()).isEqualByComparingTo("6720.00");
    }

    @Test
    void badPurchasesAreRefused() throws Exception {
        String inv = "INV-" + uniq();
        ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, inv, "POSTED", List.of(line("Dolo", "D" + uniq(), expiry, 10))));
        refused(HttpMethod.POST, "/pharmacy/purchases",
                purchase(supplierId, " " + inv.toLowerCase() + " ", "POSTED", List.of(line("Dolo", "D" + uniq(), expiry, 10))), "already entered");
        long otherSupplier = supplier(ph, "Second Supplier " + uniq(), phone()).path("id").asLong();
        ok(HttpMethod.POST, "/pharmacy/purchases", purchase(otherSupplier, inv, "POSTED", List.of(line("Dolo", "D" + uniq(), expiry, 10))));

        refused(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Dolo", "", expiry, 10))), "batch number");
        refused(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Dolo", "UNBATCHED", expiry, 10))), "batch number");
        refused(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of()), "at least one medicine");

        Hospital other = newPharmacy();
        long foreignSupplier = supplier(token(other), "Foreign " + uniq(), phone()).path("id").asLong();
        refused(HttpMethod.POST, "/pharmacy/purchases", purchase(foreignSupplier, "INV-" + uniq(), "POSTED",
                List.of(line("Dolo", "D" + uniq(), expiry, 10))), "supplier of this pharmacy");
    }

    @Test
    void aReusedBatchNumberCannotChangeItsExpiryOrReviveADisposedBatch() throws Exception {
        String batch = "R" + uniq();
        ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Azee", batch, expiry, 10))));
        ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Azee", batch, expiry, 5))));
        assertThat(batchOf(batch).getCurrentQuantity()).isEqualByComparingTo("15");

        refused(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED",
                List.of(line("Azee", batch, expiry.plusMonths(6), 5))), "already in stock with expiry");
        assertThat(batchOf(batch).getExpiryDate()).isEqualTo(expiry);

        ok(HttpMethod.POST, "/pharmacy/inventory/batches/" + batchOf(batch).getId() + "/dispose", Map.of("remarks", "damaged"));
        refused(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED",
                List.of(line("Azee", batch, expiry, 5))), "disposed earlier");
    }

    // ── drafts and cancellation ────────────────────────────────────────────────────────────

    @Test
    void aDraftCanBeEditedAndDiscardedButAPostedOneOnlyCancelled() throws Exception {
        String batch = "E" + uniq();
        JsonNode draft = ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "DRAFT", List.of(line("Cef", batch, expiry, 10))));
        long id = draft.path("id").asLong();
        JsonNode edited = ok(HttpMethod.PUT, "/pharmacy/purchases/" + id,
                purchase(supplierId, "INV-" + uniq(), "DRAFT", List.of(line("Cef", batch, expiry, 20))));
        assertThat(edited.path("subtotal").decimalValue()).isEqualByComparingTo("1200.00");

        refused(HttpMethod.POST, "/pharmacy/purchases/" + id + "/cancel", Map.of("reason", ""), "reason");
        JsonNode discarded = ok(HttpMethod.POST, "/pharmacy/purchases/" + id + "/cancel", Map.of("reason", "Entered by mistake"));
        assertThat(discarded.path("postingStatus").asText()).isEqualTo("CANCELLED");
        assertThat(batches.findAll().stream().noneMatch(b -> batch.equals(b.getBatchNumber()))).isTrue();
        refused(HttpMethod.POST, "/pharmacy/purchases/" + id + "/post", null, "cancelled");

        JsonNode posted = ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Cef", "F" + uniq(), expiry, 10))));
        refused(HttpMethod.PUT, "/pharmacy/purchases/" + posted.path("id").asLong(),
                purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Cef", "F" + uniq(), expiry, 10))), "Only a draft");
    }

    @Test
    void cancellingAPostedPurchaseTakesItsStockBackOnlyWhileItIsAllThere() throws Exception {
        String full = "C" + uniq();
        JsonNode inv = ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Mox", full, expiry, 10))));
        ok(HttpMethod.POST, "/pharmacy/purchases/" + inv.path("id").asLong() + "/cancel", Map.of("reason", "Wrong supplier"));
        assertThat(batchOf(full).getCurrentQuantity()).isEqualByComparingTo("0");

        String sold = "S" + uniq();
        JsonNode inv2 = ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Mox", sold, expiry, 10))));
        MedicineBatch b = batchOf(sold);
        Map<String, Object> item = new HashMap<>(Map.of("medicineBatchId", b.getId(), "quantity", 3));
        ok(HttpMethod.POST, "/pharmacy/sales", Map.of("patientName", "Walk in", "paymentMethod", "CASH", "items", List.of(item)));
        refused(HttpMethod.POST, "/pharmacy/purchases/" + inv2.path("id").asLong() + "/cancel", Map.of("reason", "Wrong supplier"),
                "Use a supplier return");
        assertThat(batchOf(sold).getCurrentQuantity()).isEqualByComparingTo("7");
    }

    // ── supplier payments ──────────────────────────────────────────────────────────────────

    @Test
    void supplierPaymentsAreTrackedAndNeverExceedTheBalance() throws Exception {
        JsonNode inv = ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Zin", "Z" + uniq(), expiry, 10))));
        long id = inv.path("id").asLong(); // total 672.00
        JsonNode part = ok(HttpMethod.POST, "/pharmacy/purchases/" + id + "/payments",
                Map.of("amount", 500, "method", "UPI", "reference", "UTR1"));
        assertThat(part.path("paymentStatus").asText()).isEqualTo("PARTIAL");
        refused(HttpMethod.POST, "/pharmacy/purchases/" + id + "/payments", Map.of("amount", 200, "method", "UPI"), "172.00 is still due");
        refused(HttpMethod.POST, "/pharmacy/purchases/" + id + "/payments", Map.of("amount", 10, "method", "BARTER"), "Payment method");
        JsonNode paid = ok(HttpMethod.POST, "/pharmacy/purchases/" + id + "/payments",
                Map.of("amount", 172, "method", "BANK_TRANSFER", "paidOn", LocalDate.now().toString()));
        assertThat(paid.path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(paid.path("paidAmount").decimalValue()).isEqualByComparingTo("672.00");
        assertThat(ok(HttpMethod.GET, "/pharmacy/purchases/" + id + "/payments", null)).hasSize(2);
        refused(HttpMethod.POST, "/pharmacy/purchases/" + id + "/cancel", Map.of("reason", "Wrong"), "Payments are recorded");

        JsonNode draft = ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "DRAFT", List.of(line("Zin", "Z" + uniq(), expiry, 1))));
        refused(HttpMethod.POST, "/pharmacy/purchases/" + draft.path("id").asLong() + "/payments", Map.of("amount", 10, "method", "CASH"), "posted purchase");
    }

    // ── unblock and supplier returns ───────────────────────────────────────────────────────

    @Test
    void aBlockedBatchCanBePutBackOnSaleWithAReason() throws Exception {
        String batch = "U" + uniq();
        ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Lev", batch, expiry, 10))));
        long id = batchOf(batch).getId();
        refused(HttpMethod.POST, "/pharmacy/inventory/batches/" + id + "/unblock", Map.of("reason", "ok now"), "Only a blocked batch");
        ok(HttpMethod.POST, "/pharmacy/inventory/batches/" + id + "/block", null);
        refused(HttpMethod.POST, "/pharmacy/inventory/batches/" + id + "/unblock", Map.of("reason", ""), "reason");
        ok(HttpMethod.POST, "/pharmacy/inventory/batches/" + id + "/unblock", Map.of("reason", "Recall withdrawn by maker"));
        assertThat(batchOf(batch).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void aSupplierReturnIssuesANumberedNoteAndChecksTheSupplierAndRate() throws Exception {
        String batch = "W" + uniq();
        ok(HttpMethod.POST, "/pharmacy/purchases", purchase(supplierId, "INV-" + uniq(), "POSTED", List.of(line("Mont", batch, expiry, 10))));
        long id = batchOf(batch).getId();

        JsonNode note = ok(HttpMethod.POST, "/pharmacy/inventory/supplier-return",
                Map.of("supplierId", supplierId, "items", List.of(Map.of("medicineBatchId", id, "quantityToReturn", 4))));
        assertThat(note.path("noteNumber").asText()).matches("SR/\\d{2}-\\d{2}/00001");
        assertThat(note.path("totalClaimed").decimalValue()).isEqualByComparingTo("240.00");
        assertThat(batchOf(batch).getCurrentQuantity()).isEqualByComparingTo("6");

        refused(HttpMethod.POST, "/pharmacy/inventory/supplier-return", Map.of("supplierId", supplierId,
                "items", List.of(Map.of("medicineBatchId", id, "quantityToReturn", 1, "returnRate", 61))), "more than the purchase rate");
        refused(HttpMethod.POST, "/pharmacy/inventory/supplier-return", Map.of("supplierId", 987_654_321L,
                "items", List.of(Map.of("medicineBatchId", id, "quantityToReturn", 1))), "Supplier not found");
        refused(HttpMethod.POST, "/pharmacy/inventory/supplier-return", Map.of(
                "items", List.of(Map.of("medicineBatchId", id, "quantityToReturn", 1))), "Choose the supplier");
    }
}
