package com.hms.service.hospital;

import com.hms.entity.Medicine;
import com.hms.repository.MedicineListRepository;
import com.hms.repository.MedicineRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These endpoints take the entity itself as the request body, so a client can send an "id". A
 * create must never turn into an update: with an id, save() merges into that existing row --
 * another hospital's stock included -- and moves it here.
 */
@ExtendWith(MockitoExtension.class)
class EntityBodyCreateIdTest {

    @Mock MedicineRepository medicineRepository;
    @Mock MedicineListRepository medicineListRepository;
    @Mock com.hms.repository.MedicinePurchaseRepository medicinePurchaseRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock com.hms.service.AuditLogService auditLogService;
    @Mock com.hms.security.HospitalWebSocketHandler webSocketHandler;
    @Mock MedicineStockService medicineStockService;
    @InjectMocks MedicineService service;

    @Test
    void addingStock_ignoresAClientSentId() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        lenient().when(medicineListRepository.existsByNameIgnoreCase(anyString())).thenReturn(true);
        java.util.concurrent.atomic.AtomicReference<Long> idAtSave = new java.util.concurrent.atomic.AtomicReference<>(-1L);
        when(medicineRepository.save(any(Medicine.class))).thenAnswer(inv -> {
            Medicine m = inv.getArgument(0);
            idAtSave.set(m.getId()); // what save() was handed, before the database assigns one
            m.setId(500L);
            return m;
        });

        Medicine body = new Medicine();
        body.setId(42L); // another hospital's stock row
        body.setName("Paracetamol 500");
        body.setStockQuantity(10);
        body.setUnitPrice(2.0);
        body.setExpiryDate(LocalDate.now().plusYears(1));

        service.addInventoryMedicine(body);

        ArgumentCaptor<Medicine> saved = ArgumentCaptor.forClass(Medicine.class);
        verify(medicineRepository).save(saved.capture());
        assertThat(idAtSave.get()).as("a create inserts, never merges into row 42").isNull();
        assertThat(saved.getValue().getHospitalId()).isEqualTo(7L);
    }
}
