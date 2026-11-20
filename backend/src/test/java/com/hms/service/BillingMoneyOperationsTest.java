package com.hms.service;

import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.BillingPayment;
import com.hms.entity.Hospital;
import com.hms.exception.ConflictException;
import com.hms.repository.*;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.BillMoney;
import com.hms.service.hospital.BillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Discount, part-payment (Cash / Card / UPI), refund, cancel, the daily cash-closing report and
 * the patient-wide outstanding check used at discharge — against an in-memory payment ledger.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingMoneyOperationsTest {

    @Mock BillingRepository billingRepository;
    @Mock BillingItemRepository billingItemRepository;
    @Mock BillingMedicineRepository billingMedicineRepository;
    @Mock BillingPaymentRepository billingPaymentRepository;
    @Mock HospitalRepository hospitalRepository;
    @Mock PatientRepository patientRepository;
    @Mock IpdAdmissionRepository ipdAdmissionRepository;
    @Mock WardRepository wardRepository;
    @Mock AuditLogService auditLogService;
    @Mock SecurityContextHelper securityHelper;
    @Mock HospitalWebSocketHandler webSocketHandler;
    @InjectMocks BillingService billingService;

    private Billing bill;
    private final List<BillingPayment> ledger = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setId(1L);
        h.setModules(List.of("BILLING"));
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(h));
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserRole()).thenReturn("RECEPTIONIST");
        when(securityHelper.getCurrentUserEmail()).thenReturn("desk@h.test");

        bill = new Billing();
        bill.setId(7L);
        bill.setHospitalId(1L);
        bill.setPatientId(50L);
        bill.setCustomId("BIL1007");
        bill.setBillingType("OPD");
        bill.setPaymentStatus("PENDING");
        bill.setAmount(new BigDecimal("600"));
        BillingItem consult = new BillingItem();
        consult.setAmount(new BigDecimal("500"));
        BillingItem paper = new BillingItem();
        paper.setAmount(new BigDecimal("100"));

        when(billingRepository.findByIdAndHospitalIdForUpdate(7L, 1L)).thenReturn(Optional.of(bill));
        when(billingRepository.save(any(Billing.class))).thenAnswer(i -> i.getArgument(0));
        when(billingItemRepository.findByBillingId(7L)).thenReturn(List.of(consult, paper));
        when(billingMedicineRepository.findByBillingId(7L)).thenReturn(List.of());
        when(billingPaymentRepository.findByBillingId(7L)).thenAnswer(i -> new ArrayList<>(ledger));
        when(billingPaymentRepository.save(any(BillingPayment.class))).thenAnswer(i -> {
            BillingPayment p = i.getArgument(0);
            ledger.add(p);
            return p;
        });
    }

    private void asAdmin() {
        when(securityHelper.getCurrentUserRole()).thenReturn("HOSPITAL_ADMIN");
    }

    // ── part-payment and payment modes ───────────────────────────────────────

    @Test
    void partPaymentLeavesABalance_thenTheRestSettlesIt() {
        Map<String, Object> first = billingService.recordPayment(7L, new BigDecimal("200"), "CASH", null);
        assertThat(first.get("balance")).isEqualTo(new BigDecimal("400"));
        assertThat(bill.getPaymentStatus()).isEqualTo("PARTIAL");

        billingService.recordPayment(7L, new BigDecimal("400"), "CARD", null);
        assertThat(bill.getPaymentStatus()).isEqualTo("PAID");
        assertThat(ledger).extracting(BillingPayment::getMode).containsExactly("CASH", "CARD");
        assertThat(ledger).allSatisfy(p -> assertThat(p.getRecordedBy()).contains("desk@h.test"));
    }

    @Test
    void upiNeedsAReference_andOnlineMeansUpi() {
        assertThatThrownBy(() -> billingService.recordPayment(7L, new BigDecimal("100"), "UPI", " "))
                .hasMessageContaining("UTR");
        billingService.recordPayment(7L, new BigDecimal("100"), "Online", "4321");
        assertThat(ledger.get(0).getMode()).isEqualTo("UPI");
    }

    @Test
    void cannotOverpay_orUseAnUnknownMode() {
        assertThatThrownBy(() -> billingService.recordPayment(7L, new BigDecimal("601"), "CASH", null))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> billingService.recordPayment(7L, new BigDecimal("10"), "CHEQUE", null))
                .hasMessageContaining("Cash, Card or UPI");
        assertThat(ledger).isEmpty();
    }

    // ── discount ─────────────────────────────────────────────────────────────

    @Test
    void discountLowersWhatIsOwed_butNotTheCharges() {
        Map<String, Object> m = billingService.applyDiscount(7L, new BigDecimal("100"), "Senior citizen");
        assertThat(m.get("grossAmount")).isEqualTo(new BigDecimal("600"));
        assertThat(m.get("totalAmount")).isEqualTo(new BigDecimal("500"));
        assertThat(bill.getDiscountReason()).isEqualTo("Senior citizen");

        assertThatThrownBy(() -> billingService.recordPayment(7L, new BigDecimal("501"), "CASH", null))
                .isInstanceOf(ConflictException.class);
        billingService.recordPayment(7L, new BigDecimal("500"), "CASH", null);
        assertThat(bill.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void discountNeedsAReason_andCannotGoBelowWhatWasCollected() {
        assertThatThrownBy(() -> billingService.applyDiscount(7L, new BigDecimal("50"), " "))
                .hasMessageContaining("reason");
        billingService.recordPayment(7L, new BigDecimal("400"), "CASH", null);
        assertThatThrownBy(() -> billingService.applyDiscount(7L, new BigDecimal("300"), "x"))
                .hasMessageContaining("at most 200");
    }

    @Test
    void aDiscountCoveringTheRestSettlesAPartlyPaidBill() {
        billingService.recordPayment(7L, new BigDecimal("500"), "CASH", null);
        billingService.applyDiscount(7L, new BigDecimal("100"), "Rounding off");
        assertThat(bill.getPaymentStatus()).isEqualTo("PAID");
    }

    // ── refund ───────────────────────────────────────────────────────────────

    @Test
    void onlyTheAdminCanRefund() {
        billingService.recordPayment(7L, new BigDecimal("600"), "CASH", null);
        assertThatThrownBy(() -> billingService.refund(7L, new BigDecimal("100"), "CASH", "x"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void refundIsANegativeLedgerRow_andReopensTheBalance() {
        billingService.recordPayment(7L, new BigDecimal("600"), "UPI", "999");
        asAdmin();
        billingService.refund(7L, new BigDecimal("100"), "UPI", "Charged twice");

        BillingPayment r = ledger.get(1);
        assertThat(r.getAmount()).isEqualByComparingTo("-100");
        assertThat(r.getKind()).isEqualTo("REFUND");
        assertThat(r.getNote()).isEqualTo("Charged twice");
        assertThat(bill.getPaymentStatus()).isEqualTo("PARTIAL");
        assertThatThrownBy(() -> billingService.refund(7L, new BigDecimal("501"), "UPI", "x"))
                .hasMessageContaining("Only 500");
    }

    @Test
    void overchargeRefundShrinksTheBill_soItStaysSettled() {
        billingService.recordPayment(7L, new BigDecimal("600"), "CASH", null);
        asAdmin();
        billingService.refund(7L, new BigDecimal("100"), "CASH", "Charged a wrong fee", true);

        assertThat(bill.getPaymentStatus()).isEqualTo("PAID");
        assertThat(bill.getDiscountAmount()).isEqualByComparingTo("100");
        assertThat(bill.getDiscountReason()).isEqualTo("Refund: Charged a wrong fee");
        BillMoney m = BillMoney.of(bill, List.of(), List.of(), ledger, () -> new BigDecimal("600"));
        assertThat(m.balance()).isEqualByComparingTo("0");
        assertThat(m.paid()).isEqualByComparingTo("500");
    }

    // ── cancel ───────────────────────────────────────────────────────────────

    @Test
    void cancellingRefundsWhatWasTaken_andLocksTheBill() {
        billingService.recordPayment(7L, new BigDecimal("250"), "CASH", null);
        asAdmin();
        billingService.cancel(7L, "Patient left before consultation", "CASH");

        assertThat(bill.getPaymentStatus()).isEqualTo("CANCELLED");
        assertThat(bill.getCancelReason()).isEqualTo("Patient left before consultation");
        assertThat(BillMoney.of(bill, List.of(), List.of(), ledger, null).paid()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> billingService.recordPayment(7L, new BigDecimal("10"), "CASH", null))
                .hasMessageContaining("cancelled");
        assertThatThrownBy(() -> billingService.applyDiscount(7L, new BigDecimal("10"), "x"))
                .hasMessageContaining("cancelled");
    }

    @Test
    void ipdBillsAreNotCancelled_andAReasonIsRequired() {
        asAdmin();
        assertThatThrownBy(() -> billingService.cancel(7L, " ", "CASH")).hasMessageContaining("reason");
        bill.setBillingType("IPD");
        assertThatThrownBy(() -> billingService.cancel(7L, "x", "CASH")).hasMessageContaining("IPD");
    }

    // ── outstanding (discharge) and daily collection ─────────────────────────

    @Test
    void outstandingListsUnpaidBills_butNotCancelledOnes() {
        when(billingRepository.findByPatientIdOrderByCreatedAtDesc(50L)).thenReturn(List.of(bill));
        assertThat(billingService.outstandingBillsForPatient(50L, 1L))
                .singleElement().satisfies(o -> assertThat(o.get("balance")).isEqualTo(new BigDecimal("600")));

        bill.setPaymentStatus("CANCELLED");
        assertThat(billingService.outstandingBillsForPatient(50L, 1L)).isEmpty();
    }

    @Test
    void dailyCollectionTotalsByMode_netOfRefunds() {
        billingService.recordPayment(7L, new BigDecimal("200"), "CASH", null);
        billingService.recordPayment(7L, new BigDecimal("400"), "CARD", null);
        asAdmin();
        billingService.refund(7L, new BigDecimal("50"), "CASH", "Change");
        when(billingPaymentRepository.findByHospitalIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                eq(1L), any(), any())).thenReturn(ledger);
        when(billingRepository.findAllById(any())).thenReturn(List.of(bill));
        when(patientRepository.findAllById(any())).thenReturn(List.of());

        Map<String, Object> r = billingService.dailyCollection(LocalDate.of(2026, 11, 17));

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> net = (Map<String, BigDecimal>) r.get("netByMode");
        assertThat(net.get("CASH")).isEqualByComparingTo("150");
        assertThat(net.get("CARD")).isEqualByComparingTo("400");
        assertThat((BigDecimal) r.get("netCollected")).isEqualByComparingTo("550");
        assertThat((BigDecimal) r.get("totalRefunded")).isEqualByComparingTo("50");
        assertThat(r.get("receiptCount")).isEqualTo(2);
    }
}
