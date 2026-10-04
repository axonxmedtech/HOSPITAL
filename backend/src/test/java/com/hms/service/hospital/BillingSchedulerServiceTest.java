package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Ward;
import com.hms.repository.BillingItemRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.WardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingSchedulerServiceTest {

    @Mock IpdAdmissionRepository ipdAdmissionRepository;
    @Mock WardRepository wardRepository;
    @Mock BillingRepository billingRepository;
    @Mock BillingItemRepository billingItemRepository;
    @Mock BillingService billingService;
    @Mock PlatformTransactionManager transactionManager;

    @InjectMocks BillingSchedulerService scheduler;

    @BeforeEach
    void realTransactionTemplate() {
        // A real template over a mock manager: each callback runs, one "transaction" per call.
        ReflectionTestUtils.setField(scheduler, "transactionTemplate", new TransactionTemplate(transactionManager));
    }

    private IpdAdmission admission(long id) {
        IpdAdmission a = new IpdAdmission();
        a.setId(id);
        a.setStatus("ADMITTED");
        a.setWardId(3L);
        return a;
    }

    private Billing bill(long id, String status) {
        Billing b = new Billing();
        b.setId(id);
        b.setHospitalId(1L);
        b.setPaymentStatus(status);
        return b;
    }

    private void wardPriced(String price) {
        Ward ward = new Ward();
        ward.setBedPrice(new BigDecimal(price));
        when(wardRepository.findById(3L)).thenReturn(Optional.of(ward));
    }

    @Test
    void aBedChargeReDerivesTheStatus_soAPaidBillShowsTheNewBalance() {
        when(ipdAdmissionRepository.findAll()).thenReturn(List.of(admission(1L)));
        when(billingRepository.findByIpdAdmissionId(1L)).thenReturn(List.of(bill(20L, "PAID")));
        when(billingItemRepository.findByBillingId(20L)).thenReturn(List.of());
        wardPriced("800.00");

        scheduler.processDailyBedCharges();

        verify(billingItemRepository).save(any(BillingItem.class));
        // recalculateTotal re-derives PAID -> PARTIAL from the ledger; bumping only the amount did not.
        verify(billingService).recalculateTotal(20L);
    }

    @Test
    void aClosedBillTakesNoFurtherCharges() {
        when(ipdAdmissionRepository.findAll()).thenReturn(List.of(admission(1L)));
        when(billingRepository.findByIpdAdmissionId(1L)).thenReturn(List.of(bill(21L, "CLOSED")));
        // A priced bed and no charge yet today, so only the CLOSED rule can stop the charge.
        lenient().when(billingItemRepository.findByBillingId(21L)).thenReturn(List.of());
        Ward ward = new Ward();
        ward.setBedPrice(new BigDecimal("800.00"));
        lenient().when(wardRepository.findById(3L)).thenReturn(Optional.of(ward));

        scheduler.processDailyBedCharges();

        verify(billingItemRepository, never()).save(any());
        verify(billingService, never()).recalculateTotal(any());
    }

    @Test
    void oneFailingAdmissionDoesNotStopTheOthers() {
        when(ipdAdmissionRepository.findAll()).thenReturn(List.of(admission(1L), admission(2L)));
        when(billingRepository.findByIpdAdmissionId(1L)).thenThrow(new RuntimeException("db blip"));
        when(billingRepository.findByIpdAdmissionId(2L)).thenReturn(List.of(bill(22L, "PENDING")));
        when(billingItemRepository.findByBillingId(22L)).thenReturn(List.of());
        wardPriced("500.00");

        scheduler.processDailyBedCharges();

        verify(billingService).recalculateTotal(22L);
        // Each admission is its own transaction: the failure rolled back only its own.
        verify(transactionManager, times(1)).rollback(any());
        verify(transactionManager, times(1)).commit(any());
    }
}
