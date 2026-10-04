package com.hms.service;

import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.BillingMedicine;
import com.hms.entity.Hospital;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.BillingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock BillingRepository billingRepository;
    @Mock BillingItemRepository billingItemRepository;
    @Mock BillingMedicineRepository billingMedicineRepository;
    @Mock OpdRepository opdRepository;
    @Mock AuditLogService auditLogService;
    @Mock HospitalRepository hospitalRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock HospitalWebSocketHandler webSocketHandler;
    @Mock BillingPaymentRepository billingPaymentRepository;
    @Mock IpdAdmissionRepository ipdAdmissionRepository;
    @Mock com.hms.repository.WardRepository wardRepository;
    @Mock com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @InjectMocks BillingService billingService;

    // -----------------------------------------------------------------------
    // Test 1: the OPD bill charges case paper + consultation and starts unpaid.
    // (Replaces the old createConsultationBill test — that method had no callers and
    //  was deleted; this asserts the same thing against the creator actually in use.)
    // -----------------------------------------------------------------------
    @Test
    void createOpdBill_chargesCasePaperPlusConsultation_andStartsPending() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Hospital hospital = new Hospital();
        hospital.setId(1L);
        hospital.setModules(List.of("BILLING"));
        hospital.setConsultationFee(new BigDecimal("500.00"));
        hospital.setCasePaperFee(new BigDecimal("100.00"));
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(hospital));

        Billing savedBilling = new Billing();
        savedBilling.setId(10L);
        savedBilling.setHospitalId(1L);
        savedBilling.setAmount(new BigDecimal("600.00"));
        when(billingRepository.save(any(Billing.class))).thenReturn(savedBilling);

        Billing result = billingService.createOpdBill(300L, 100L, 200L);

        assertThat(result).isNotNull();

        ArgumentCaptor<Billing> captor = ArgumentCaptor.forClass(Billing.class);
        verify(billingRepository).save(captor.capture());
        // 100 case paper + 500 consultation
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("600.00"));
        // A bill is never born paid — it must be collected.
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo("PENDING");

        // Itemised breakdown: one line per fee.
        verify(billingItemRepository, times(2)).save(any(BillingItem.class));
    }

    private Hospital billingHospital(String consultationFee, String casePaperFee) {
        Hospital hospital = new Hospital();
        hospital.setId(1L);
        hospital.setModules(List.of("BILLING"));
        hospital.setConsultationFee(consultationFee == null ? null : new BigDecimal(consultationFee));
        hospital.setCasePaperFee(casePaperFee == null ? null : new BigDecimal(casePaperFee));
        return hospital;
    }

    @Test
    void createOpdBill_anExplicitZeroConsultationFee_isFree_notTheDefault() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(billingHospital("0", "100.00")));
        Billing saved = new Billing();
        saved.setId(10L);
        when(billingRepository.save(any(Billing.class))).thenReturn(saved);

        billingService.createOpdBill(300L, 100L, 200L);

        ArgumentCaptor<Billing> captor = ArgumentCaptor.forClass(Billing.class);
        verify(billingRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount())
                .as("case paper only; the consultation was configured free")
                .isEqualByComparingTo("100.00");
    }

    @Test
    void autoGenerateOpdBill_aFreeConsultationStillChargesTheCasePaperFee() {
        com.hms.entity.Appointment appt = new com.hms.entity.Appointment();
        appt.setId(7L);
        appt.setHospitalId(1L);
        appt.setPatientId(100L);
        appt.setDoctorId(200L);
        when(billingRepository.existsByAppointmentId(7L)).thenReturn(false);
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(billingHospital("0", "50.00")));
        Billing saved = new Billing();
        saved.setId(11L);
        when(billingRepository.save(any(Billing.class))).thenReturn(saved);

        billingService.autoGenerateOpdBill(appt);

        ArgumentCaptor<Billing> bill = ArgumentCaptor.forClass(Billing.class);
        verify(billingRepository).save(bill.capture());
        assertThat(bill.getValue().getAmount()).isEqualByComparingTo("50.00");
        ArgumentCaptor<BillingItem> item = ArgumentCaptor.forClass(BillingItem.class);
        verify(billingItemRepository).save(item.capture());
        assertThat(item.getValue().getDescription()).isEqualTo("Case Paper Fee");
    }

    @Test
    void getAllBills_searchWithStatus_filtersInTheQuery_soPagingStaysCorrect() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        Pageable pageable = PageRequest.of(0, 10);
        Billing b = new Billing();
        b.setId(1L);
        when(billingRepository.searchBillingsByStatus(1L, "ravi", "PENDING", pageable))
                .thenReturn(new PageImpl<>(List.of(b), pageable, 25));

        Page<Billing> result = billingService.getAllBills("ravi", "PENDING", pageable);

        assertThat(result.getTotalElements()).as("every matching bill counts, not one page's worth").isEqualTo(25);
        verify(billingRepository, never()).searchBillings(anyLong(), anyString(), any());
    }

    @Test
    void updateStatus_aClosedBillCannotBeReopened_andNoPaymentIsInvented() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(billingHospital("500", "100")));
        Billing closed = new Billing();
        closed.setId(5L);
        closed.setHospitalId(1L);
        closed.setPaymentStatus("CLOSED");
        when(billingRepository.findByIdAndHospitalIdForUpdate(5L, 1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> billingService.updateStatus(5L, "PAID", "CASH", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closed");

        assertThat(closed.getPaymentStatus()).isEqualTo("CLOSED");
        verify(billingRepository, never()).save(any());
        verify(billingPaymentRepository, never()).save(any());
    }

    private com.hms.dto.HospitalFeeDTO charge(String name, String amount) {
        com.hms.dto.HospitalFeeDTO dto = new com.hms.dto.HospitalFeeDTO();
        dto.setName(name);
        dto.setDefaultAmount(new BigDecimal(amount));
        return dto;
    }

    private Billing partialBill() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        Billing bill = new Billing();
        bill.setId(9L);
        bill.setHospitalId(1L);
        bill.setPaymentStatus("PARTIAL");
        when(billingRepository.findByIdAndHospitalIdForUpdate(9L, 1L)).thenReturn(Optional.of(bill));
        return bill;
    }

    @Test
    void replaceBillItems_aTotalBelowWhatWasCollected_isRefused_andNothingIsDeleted() {
        partialBill();
        com.hms.entity.BillingPayment paid = new com.hms.entity.BillingPayment();
        paid.setAmount(new BigDecimal("500.00"));
        when(billingPaymentRepository.findByBillingId(9L)).thenReturn(List.of(paid));

        assertThatThrownBy(() -> billingService.replaceBillItems(9L, List.of(charge("Consultation", "300.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already collected");

        verify(billingItemRepository, never()).deleteAll(any());
        verify(billingItemRepository, never()).saveAll(any());
    }

    @Test
    void replaceBillItems_aNegativeCharge_isRefused() {
        partialBill();

        assertThatThrownBy(() -> billingService.replaceBillItems(9L, List.of(charge("Discount", "-50.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(billingItemRepository, never()).deleteAll(any());
    }

    /** A new bill must default to PENDING — never silently "already paid". */
    @Test
    void newBilling_defaultsToPending_notPaid() {
        assertThat(new Billing().getPaymentStatus()).isEqualTo("PENDING");
    }

    // -----------------------------------------------------------------------
    // Test 2: updateStatus with invalid status throws IllegalArgumentException
    // (Closest analog to "payment exceeding balance" — validates bad status input
    //  before any repository work is done)
    // -----------------------------------------------------------------------
    @Test
    void updateStatus_invalidStatus_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> billingService.updateStatus(1L, "INVALID", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid billing status value");

        // Verify no repository interactions occurred
        verifyNoInteractions(billingRepository);
    }

    // -----------------------------------------------------------------------
    // Test 3: getAllBills returns page from repository for correct hospitalId
    // -----------------------------------------------------------------------
    @Test
    void getAllBills_noFilter_returnsPageFromRepository() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Billing b1 = new Billing();
        b1.setId(1L);
        b1.setHospitalId(1L);

        Billing b2 = new Billing();
        b2.setId(2L);
        b2.setHospitalId(1L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Billing> page = new PageImpl<>(List.of(b1, b2), pageable, 2);
        when(billingRepository.findByHospitalId(1L, pageable)).thenReturn(page);

        Page<Billing> result = billingService.getAllBills(null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(billingRepository).findByHospitalId(1L, pageable);
    }

    // -----------------------------------------------------------------------
    // Test 4: recalculateTotal sums BillingItems and BillingMedicines and saves
    // -----------------------------------------------------------------------
    @Test
    void recalculateTotal_withItemsAndMedicines_savesCorrectSum() {
        Billing bill = new Billing();
        bill.setId(5L);
        when(billingRepository.findById(5L)).thenReturn(Optional.of(bill));

        BillingItem item1 = new BillingItem();
        item1.setAmount(new BigDecimal("200.00"));
        BillingItem item2 = new BillingItem();
        item2.setAmount(new BigDecimal("150.00"));
        when(billingItemRepository.findByBillingId(5L)).thenReturn(List.of(item1, item2));

        BillingMedicine med1 = new BillingMedicine();
        med1.setAmount(new BigDecimal("50.00"));
        when(billingMedicineRepository.findByBillingId(5L)).thenReturn(List.of(med1));

        billingService.recalculateTotal(5L);

        ArgumentCaptor<Billing> captor = ArgumentCaptor.forClass(Billing.class);
        verify(billingRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
    }
}
