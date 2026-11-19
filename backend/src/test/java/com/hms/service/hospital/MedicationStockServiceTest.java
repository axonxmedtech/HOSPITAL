package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.BillingItem;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalInventory;
import com.hms.entity.IpdAdmission;
import com.hms.entity.MedicationAdministration;
import com.hms.exception.ConflictException;
import com.hms.repository.BillingItemRepository;
import com.hms.repository.BillingMedicineRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.HospitalInventoryRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.MedicineRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Saline and other hospital items given on the ward: taken from stock, billed, and undone exactly. */
class MedicationStockServiceTest {

    HospitalInventoryRepository inventory = mock(HospitalInventoryRepository.class);
    BillingRepository bills = mock(BillingRepository.class);
    BillingItemRepository billItems = mock(BillingItemRepository.class);
    BillingService billingService = mock(BillingService.class);
    HospitalRepository hospitals = mock(HospitalRepository.class);
    SecurityContextHelper security = mock(SecurityContextHelper.class);
    MedicationStockService svc = new MedicationStockService(mock(MedicineStockService.class), mock(MedicineRepository.class),
            inventory, bills, billItems, mock(BillingMedicineRepository.class), billingService, hospitals, security);

    IpdAdmission admission = new IpdAdmission();
    HospitalInventory later;
    HospitalInventory sooner;

    private static HospitalInventory row(long id, int qty, double price, LocalDate expiry) {
        HospitalInventory r = new HospitalInventory();
        r.setId(id);
        r.setHospitalId(1L);
        r.setName("Saline NS 500ml");
        r.setStockQuantity(qty);
        r.setUnitPrice(price);
        r.setExpiryDate(expiry);
        return r;
    }

    @BeforeEach
    void setUp() {
        when(security.getCurrentHospitalId()).thenReturn(1L);
        admission.setId(7L);
        admission.setIpdNumber("IPD-7");
        Hospital h = new Hospital();
        h.setModules(new ArrayList<>(List.of("IPD", "BILLING")));
        when(hospitals.findById(1L)).thenReturn(Optional.of(h));
        Billing bill = new Billing();
        bill.setId(30L);
        bill.setHospitalId(1L);
        bill.setPaymentStatus("PENDING");
        when(bills.findByIpdAdmissionId(7L)).thenReturn(List.of(bill));
        later = row(1L, 5, 60, LocalDate.now().plusMonths(12));
        sooner = row(2L, 1, 55, LocalDate.now().plusMonths(2));
        when(inventory.lockActiveByName("Saline NS 500ml", 1L)).thenReturn(new ArrayList<>(List.of(later, sooner)));
        when(billItems.save(any(BillingItem.class))).thenAnswer(i -> {
            BillingItem bi = i.getArgument(0);
            bi.setId(99L);
            return bi;
        });
    }

    @Test
    void givingTakesTheEarliestExpiryFirstAndBillsWhatWasTaken() {
        var taken = svc.take(admission, MedicationStockService.SOURCE_HOSPITAL_ITEM, null, "Saline NS 500ml", 2, 11L);

        assertThat(sooner.getStockQuantity()).isZero();
        assertThat(later.getStockQuantity()).isEqualTo(4);
        assertThat(taken.detail()).isEqualTo("2:1,1:1");
        assertThat(taken.charge()).isEqualByComparingTo("115");
        ArgumentCaptor<BillingItem> line = ArgumentCaptor.forClass(BillingItem.class);
        verify(billItems).save(line.capture());
        assertThat(line.getValue().getDescription()).isEqualTo("Saline NS 500ml × 2");
        assertThat(line.getValue().getBillingId()).isEqualTo(30L);
        verify(billingService).recalculateTotal(30L);
    }

    @Test
    void notEnoughStockOffersThePatientsOwnSupply() {
        assertThatThrownBy(() -> svc.take(admission, MedicationStockService.SOURCE_HOSPITAL_ITEM, null, "Saline NS 500ml", 9, 11L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only 6 of Saline NS 500ml left")
                .hasMessageContaining("Patient's own supply");
        verify(billItems, never()).save(any());
    }

    @Test
    void undoPutsBackTheSameRowsAndRemovesTheCharge() {
        when(inventory.findByIdAndHospitalId(2L, 1L)).thenReturn(Optional.of(sooner));
        when(inventory.findByIdAndHospitalId(1L, 1L)).thenReturn(Optional.of(later));
        sooner.setStockQuantity(0);
        later.setStockQuantity(4);
        BillingItem charged = new BillingItem();
        charged.setId(99L);
        charged.setHospitalId(1L);
        charged.setBillingId(30L);
        when(billItems.findById(99L)).thenReturn(Optional.of(charged));

        MedicationAdministration mar = new MedicationAdministration();
        mar.setStockSource(MedicationStockService.SOURCE_HOSPITAL_ITEM);
        mar.setStockDetail("2:1,1:1");
        mar.setBillingItemId(99L);
        svc.putBack(mar);

        assertThat(sooner.getStockQuantity()).isEqualTo(1);
        assertThat(later.getStockQuantity()).isEqualTo(5);
        verify(billItems).delete(charged);
        verify(billingService).recalculateTotal(30L);
    }

    @Test
    void withoutTheBillingModuleStockIsStillTakenButNothingIsCharged() {
        when(hospitals.findById(1L)).thenReturn(Optional.of(new Hospital()));
        var taken = svc.take(admission, MedicationStockService.SOURCE_HOSPITAL_ITEM, null, "Saline NS 500ml", 1, 11L);
        assertThat(sooner.getStockQuantity()).isZero();
        assertThat(taken.charge()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(billItems, never()).save(any());
    }
}
