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
    @Mock com.hms.repository.PatientAdvanceRepository patientAdvanceRepository;
    @Mock com.hms.repository.BillingRefundRepository billingRefundRepository;

    @InjectMocks BillingService billingService;

    // -----------------------------------------------------------------------
    // Test 1: createConsultationBill saves a billing record with correct amount
    // (Exercises the "calculate bill" path via createConsultationBill)
    // -----------------------------------------------------------------------
    @Test
    void createConsultationBill_withHospitalFee_savesCorrectAmount() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Hospital hospital = new Hospital();
        hospital.setId(1L);
        hospital.setModules(List.of("BILLING"));
        hospital.setConsultationFee(new BigDecimal("500.00"));
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(hospital));

        Billing savedBilling = new Billing();
        savedBilling.setId(10L);
        savedBilling.setHospitalId(1L);
        savedBilling.setAmount(new BigDecimal("500.00"));
        when(billingRepository.save(any(Billing.class))).thenReturn(savedBilling);

        BillingItem savedItem = new BillingItem();
        when(billingItemRepository.save(any(BillingItem.class))).thenReturn(savedItem);

        Billing result = billingService.createConsultationBill(100L, 200L, 300L);

        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));

        ArgumentCaptor<Billing> captor = ArgumentCaptor.forClass(Billing.class);
        verify(billingRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo("PENDING");
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

    @Test
    void postIpdCharge_createsBillingItemAndRecalculates() {
        Long hospitalId = 1L;
        Long ipdAdmissionId = 100L;
        
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        Hospital hospital = new Hospital();
        hospital.setId(hospitalId);
        hospital.setModules(List.of("BILLING"));
        when(hospitalRepository.findById(hospitalId)).thenReturn(Optional.of(hospital));

        com.hms.entity.IpdAdmission admission = new com.hms.entity.IpdAdmission();
        admission.setId(ipdAdmissionId);
        admission.setPatientId(10L);
        admission.setDoctorId(20L);
        admission.setIpdNumber("IPD-999");
        admission.setHospitalId(hospitalId);

        // Mock returning empty list of bills initially
        when(billingRepository.findByIpdAdmissionId(ipdAdmissionId)).thenReturn(new ArrayList<>());
        when(ipdAdmissionRepository.findById(ipdAdmissionId)).thenReturn(Optional.of(admission));

        Billing savedBill = new Billing();
        savedBill.setId(500L);
        savedBill.setIpdAdmissionId(ipdAdmissionId);
        savedBill.setHospitalId(hospitalId);
        when(billingRepository.save(any(Billing.class))).thenReturn(savedBill);
        when(billingRepository.findById(500L)).thenReturn(Optional.of(savedBill));

        billingService.postIpdCharge(ipdAdmissionId, "Test Charge", new BigDecimal("150.00"));

        verify(billingRepository, times(2)).save(any(Billing.class));
        verify(billingItemRepository, times(1)).save(any(BillingItem.class));
    }

    // ===== Advance Deposits (Form 30 BR-7) =====

    @Test
    void recordAdvance_rejectsNonPositiveAmount() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        com.hms.dto.AdvanceRequest req = new com.hms.dto.AdvanceRequest();
        req.setIpdAdmissionId(100L);
        req.setAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> billingService.recordAdvance(req))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(patientAdvanceRepository);
    }

    @Test
    void recordAdvance_savesActiveAdvanceWithFullBalance() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("reception@hospital.com");
        com.hms.entity.IpdAdmission admission = new com.hms.entity.IpdAdmission();
        admission.setId(100L);
        admission.setHospitalId(1L);
        admission.setPatientId(55L);
        when(ipdAdmissionRepository.findById(100L)).thenReturn(Optional.of(admission));
        when(patientAdvanceRepository.save(any(com.hms.entity.PatientAdvance.class))).thenAnswer(i -> i.getArgument(0));

        com.hms.dto.AdvanceRequest req = new com.hms.dto.AdvanceRequest();
        req.setIpdAdmissionId(100L);
        req.setAmount(new BigDecimal("5000"));
        req.setPaymentMode("CASH");

        com.hms.entity.PatientAdvance saved = billingService.recordAdvance(req);

        assertThat(saved.getBalance()).isEqualByComparingTo("5000");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getPatientId()).isEqualTo(55L);
    }

    @Test
    void applyAdvanceToIpdBill_deductsUpToOutstandingAndConsumesAdvance() {
        Long hospitalId = 1L, ipdId = 100L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("reception@hospital.com");

        Billing bill = new Billing();
        bill.setId(5L);
        bill.setHospitalId(hospitalId);
        bill.setAmount(new BigDecimal("3000"));
        when(billingRepository.findByIpdAdmissionId(ipdId)).thenReturn(List.of(bill));
        when(billingPaymentRepository.findByBillingId(5L)).thenReturn(new ArrayList<>());

        com.hms.entity.PatientAdvance advance = new com.hms.entity.PatientAdvance();
        advance.setId(9L);
        advance.setBalance(new BigDecimal("5000"));
        when(patientAdvanceRepository.findByHospitalIdAndIpdAdmissionIdAndStatus(hospitalId, ipdId, "ACTIVE"))
                .thenReturn(List.of(advance));
        when(patientAdvanceRepository.save(any(com.hms.entity.PatientAdvance.class))).thenAnswer(i -> i.getArgument(0));
        when(billingPaymentRepository.save(any(com.hms.entity.BillingPayment.class))).thenAnswer(i -> i.getArgument(0));

        BigDecimal applied = billingService.applyAdvanceToIpdBill(ipdId);

        assertThat(applied).isEqualByComparingTo("3000"); // capped at outstanding, not full advance
        assertThat(advance.getBalance()).isEqualByComparingTo("2000"); // remainder kept
        assertThat(advance.getStatus()).isNull(); // not fully consumed, status untouched
        ArgumentCaptor<com.hms.entity.BillingPayment> captor = ArgumentCaptor.forClass(com.hms.entity.BillingPayment.class);
        verify(billingPaymentRepository).save(captor.capture());
        assertThat(captor.getValue().getMode()).isEqualTo("ADVANCE");
    }

    @Test
    void applyAdvanceToIpdBill_noActiveAdvance_returnsZero() {
        Long hospitalId = 1L, ipdId = 100L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        Billing bill = new Billing();
        bill.setId(5L);
        bill.setHospitalId(hospitalId);
        bill.setAmount(new BigDecimal("3000"));
        when(billingRepository.findByIpdAdmissionId(ipdId)).thenReturn(List.of(bill));
        when(patientAdvanceRepository.findByHospitalIdAndIpdAdmissionIdAndStatus(hospitalId, ipdId, "ACTIVE"))
                .thenReturn(new ArrayList<>());

        BigDecimal applied = billingService.applyAdvanceToIpdBill(ipdId);

        assertThat(applied).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(billingPaymentRepository);
    }

    // ===== Refund Sign-off (Form 30 BR-5) =====

    @Test
    void requestRefund_requiresReason() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        com.hms.dto.RefundRequest req = new com.hms.dto.RefundRequest();
        req.setBillingId(5L);
        req.setAmount(new BigDecimal("100"));
        req.setReason("");

        assertThatThrownBy(() -> billingService.requestRefund(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
        verifyNoInteractions(billingRefundRepository);
    }

    @Test
    void requestRefund_createsPendingRequest() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("cashier@hospital.com");
        Billing bill = new Billing();
        bill.setId(5L);
        bill.setHospitalId(1L);
        bill.setPatientId(55L);
        when(billingRepository.findById(5L)).thenReturn(Optional.of(bill));
        when(billingRefundRepository.save(any(com.hms.entity.BillingRefund.class))).thenAnswer(i -> i.getArgument(0));

        com.hms.dto.RefundRequest req = new com.hms.dto.RefundRequest();
        req.setBillingId(5L);
        req.setAmount(new BigDecimal("500"));
        req.setReason("Cancelled CT scan, patient refused");

        com.hms.entity.BillingRefund saved = billingService.requestRefund(req);

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRequestedByName()).isEqualTo("cashier@hospital.com");
    }

    @Test
    void approveRefund_rejectedForNonAdminRole() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserRole()).thenReturn("RECEPTIONIST");

        assertThatThrownBy(() -> billingService.approveRefund(1L))
                .isInstanceOf(com.hms.exception.UnauthorizedException.class)
                .hasMessageContaining("supervisor");
        verifyNoInteractions(billingRefundRepository);
    }

    @Test
    void approveRefund_adminApprovesPendingRefund() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserRole()).thenReturn("HOSPITAL_ADMIN");
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@hospital.com");
        com.hms.entity.BillingRefund refund = new com.hms.entity.BillingRefund();
        refund.setId(1L);
        refund.setStatus("PENDING");
        when(billingRefundRepository.findByIdAndHospitalId(1L, hospitalId)).thenReturn(Optional.of(refund));
        when(billingRefundRepository.save(any(com.hms.entity.BillingRefund.class))).thenAnswer(i -> i.getArgument(0));

        com.hms.entity.BillingRefund approved = billingService.approveRefund(1L);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getApprovedByName()).isEqualTo("admin@hospital.com");
    }

    @Test
    void approveRefund_rejectedWhenAlreadyDecided() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserRole()).thenReturn("HOSPITAL_ADMIN");
        com.hms.entity.BillingRefund refund = new com.hms.entity.BillingRefund();
        refund.setId(1L);
        refund.setStatus("APPROVED");
        when(billingRefundRepository.findByIdAndHospitalId(1L, hospitalId)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> billingService.approveRefund(1L))
                .isInstanceOf(IllegalStateException.class);
        verify(billingRefundRepository, never()).save(any());
    }
}
