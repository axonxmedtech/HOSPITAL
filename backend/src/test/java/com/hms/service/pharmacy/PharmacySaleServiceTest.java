package com.hms.service.pharmacy;

import com.hms.dto.pharmacy.PharmacySaleRequest;
import com.hms.entity.User;
import com.hms.entity.pharmacy.InventoryTransaction;
import com.hms.entity.pharmacy.MedicineBatch;
import com.hms.entity.pharmacy.MedicineMaster;
import com.hms.entity.pharmacy.NarcoticLog;
import com.hms.entity.pharmacy.PharmacySale;
import com.hms.repository.PrescriptionRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.pharmacy.InventoryTransactionRepository;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.repository.pharmacy.NarcoticLogRepository;
import com.hms.repository.pharmacy.PharmacySaleRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PharmacySaleServiceTest {

    @Mock
    PharmacySaleRepository saleRepository;

    @Mock
    MedicineBatchRepository batchRepository;

    @Mock
    InventoryTransactionRepository transactionRepository;

    @Mock
    SecurityContextHelper securityHelper;

    @Mock
    PrescriptionRepository prescriptionRepository;

    @Mock
    NarcoticLogRepository narcoticLogRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    PharmacySaleService saleService;

    private PharmacySaleRequest buildRequest(BigDecimal quantity) {
        PharmacySaleRequest request = new PharmacySaleRequest();
        request.setPatientName("Test Patient");
        request.setSubtotal(new BigDecimal("100.00"));
        request.setTaxAmount(new BigDecimal("0"));
        request.setDiscountAmount(new BigDecimal("0"));
        request.setNetAmount(new BigDecimal("100.00"));
        request.setPaymentMethod("CASH");

        PharmacySaleRequest.SaleItemRequest item = new PharmacySaleRequest.SaleItemRequest();
        item.setMedicineId(1L);
        item.setMedicineBatchId(1L);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("50"));
        item.setTaxPercentage(new BigDecimal("0"));
        item.setTaxAmount(new BigDecimal("0"));
        item.setDiscountPercentage(new BigDecimal("0"));
        item.setDiscountAmount(new BigDecimal("0"));
        item.setTotalAmount(new BigDecimal("100"));

        request.setItems(List.of(item));
        return request;
    }

    @Test
    void createSale_withZeroQuantity_throwsRuntimeException() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        PharmacySaleRequest request = buildRequest(BigDecimal.ZERO);

        assertThatThrownBy(() -> saleService.createSale(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Sale quantity must be positive");

        verify(batchRepository, never()).findByIdAndHospitalIdForUpdate(any(), any());
    }

    @Test
    void createSale_withInsufficientStock_throwsRuntimeException() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        PharmacySaleRequest request = buildRequest(BigDecimal.valueOf(10));

        MedicineBatch batch = new MedicineBatch();
        batch.setCurrentQuantity(BigDecimal.valueOf(5));
        batch.setBatchNumber("B001");

        when(batchRepository.findByIdAndHospitalIdForUpdate(1L, 1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> saleService.createSale(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock for batch: B001");
    }

    @Test
    void getSaleDetails_withUnknownId_throwsRuntimeException() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(saleRepository.findByIdAndHospitalId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saleService.getSaleDetails(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Sale record not found");
    }

    @Test
    void createSale_happyPath_savesAndReturnsSale() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        PharmacySaleRequest request = buildRequest(new BigDecimal("2"));

        MedicineBatch batch = new MedicineBatch();
        batch.setCurrentQuantity(BigDecimal.valueOf(10));
        batch.setBatchNumber("B001");

        when(batchRepository.findByIdAndHospitalIdForUpdate(1L, 1L)).thenReturn(Optional.of(batch));

        PharmacySale savedSale = new PharmacySale();
        savedSale.setId(5L);
        when(saleRepository.save(any(PharmacySale.class))).thenReturn(savedSale);
        when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArguments()[0]);

        PharmacySale result = saleService.createSale(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(5L);
        verify(batchRepository, times(1)).save(any());
    }

    // ===== BR-1 Expiry Gate =====

    @Test
    void createSale_expiredBatch_isRejected() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        PharmacySaleRequest request = buildRequest(new BigDecimal("2"));
        MedicineBatch batch = new MedicineBatch();
        batch.setCurrentQuantity(BigDecimal.valueOf(10));
        batch.setBatchNumber("B001");
        batch.setExpiryDate(LocalDate.now().minusDays(1));
        when(batchRepository.findByIdAndHospitalIdForUpdate(1L, 1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> saleService.createSale(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        verify(batchRepository, never()).save(any());
    }

    @Test
    void createSale_quarantinedBatch_isRejected() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        PharmacySaleRequest request = buildRequest(new BigDecimal("2"));
        MedicineBatch batch = new MedicineBatch();
        batch.setCurrentQuantity(BigDecimal.valueOf(10));
        batch.setBatchNumber("B001");
        batch.setExpiryDate(LocalDate.now().plusYears(1));
        batch.setStatus("QUARANTINED");
        when(batchRepository.findByIdAndHospitalIdForUpdate(1L, 1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> saleService.createSale(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QUARANTINED");
        verify(batchRepository, never()).save(any());
    }

    // ===== BR-4 Controlled Substance Dual Sign-off =====

    private MedicineBatch controlledBatch() {
        MedicineMaster narcoticMaster = new MedicineMaster();
        narcoticMaster.setScheduleType("X");
        MedicineBatch batch = new MedicineBatch();
        batch.setId(1L);
        batch.setCurrentQuantity(BigDecimal.valueOf(10));
        batch.setBatchNumber("NARC-1");
        batch.setExpiryDate(LocalDate.now().plusYears(1));
        batch.setMedicine(narcoticMaster);
        return batch;
    }

    @Test
    void createSale_controlledSubstance_rejectedWithoutWitness() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        PharmacySaleRequest request = buildRequest(new BigDecimal("2"));
        when(batchRepository.findByIdAndHospitalIdForUpdate(1L, 1L)).thenReturn(Optional.of(controlledBatch()));

        assertThatThrownBy(() -> saleService.createSale(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("witness");
        verify(narcoticLogRepository, never()).save(any());
    }

    @Test
    void createSale_controlledSubstance_rejectsSameUserAsWitness() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        PharmacySaleRequest request = buildRequest(new BigDecimal("2"));
        request.setWitnessUserId(10L); // same as dispensing pharmacist
        when(batchRepository.findByIdAndHospitalIdForUpdate(1L, 1L)).thenReturn(Optional.of(controlledBatch()));
        User selfWitness = new User();
        selfWitness.setId(10L);
        selfWitness.setHospitalId(1L);
        selfWitness.setIsActive(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(selfWitness));

        assertThatThrownBy(() -> saleService.createSale(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different staff member");
    }

    @Test
    void createSale_controlledSubstance_succeedsWithValidWitnessAndLogsNarcotic() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("pharmacist@hospital.com");

        PharmacySaleRequest request = buildRequest(new BigDecimal("2"));
        request.setWitnessUserId(20L);
        when(batchRepository.findByIdAndHospitalIdForUpdate(1L, 1L)).thenReturn(Optional.of(controlledBatch()));
        User witness = new User();
        witness.setId(20L);
        witness.setHospitalId(1L);
        witness.setIsActive(true);
        witness.setName("Nurse Witness");
        when(userRepository.findById(20L)).thenReturn(Optional.of(witness));

        PharmacySale savedSale = new PharmacySale();
        savedSale.setId(7L);
        when(saleRepository.save(any(PharmacySale.class))).thenReturn(savedSale);
        when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArguments()[0]);
        when(narcoticLogRepository.save(any(NarcoticLog.class))).thenAnswer(i -> i.getArguments()[0]);

        PharmacySale result = saleService.createSale(request);

        assertThat(result.getId()).isEqualTo(7L);
        org.mockito.ArgumentCaptor<NarcoticLog> captor = org.mockito.ArgumentCaptor.forClass(NarcoticLog.class);
        verify(narcoticLogRepository).save(captor.capture());
        assertThat(captor.getValue().getWitnessUserId()).isEqualTo(20L);
        assertThat(captor.getValue().getWitnessName()).isEqualTo("Nurse Witness");
        assertThat(captor.getValue().getPharmacySaleId()).isEqualTo(7L);
        assertThat(captor.getValue().getQuantityIssued()).isEqualByComparingTo("2");
    }
}
