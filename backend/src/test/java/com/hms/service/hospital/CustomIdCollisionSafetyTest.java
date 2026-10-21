package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.Patient;
import com.hms.repository.BillingRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PatientRegistrar.class, BillingService.class})
class CustomIdCollisionSafetyTest {

    private static final long MINE = 1L;

    @Autowired PatientRegistrar patientRegistrar;
    @Autowired BillingService billingService;
    @Autowired PatientRepository patientRepository;
    @Autowired BillingRepository billingRepository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;

    @MockBean SecurityContextHelper securityHelper;
    @MockBean com.hms.repository.HospitalRepository hospitalRepository;
    @MockBean com.hms.repository.BillingItemRepository billingItemRepository;
    @MockBean com.hms.repository.BillingMedicineRepository billingMedicineRepository;
    @MockBean com.hms.repository.BillingPaymentRepository billingPaymentRepository;
    @MockBean com.hms.repository.IpdAdmissionRepository ipdAdmissionRepository;
    @MockBean com.hms.repository.MedicalRecordRepository medicalRecordRepository;
    @MockBean com.hms.service.AuditLogService auditLogService;
    @MockBean com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @BeforeEach
    void setUp() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(MINE);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@hospital.test");
        when(securityHelper.getCurrentUserRole()).thenReturn("ADMIN");
    }

    @Test
    void patientRegistration_skipsPreExistingCustomId_andAssignsUniqueId() {
        // Pre-create patient with PAT1
        Patient pre = new Patient();
        pre.setName("Existing Patient");
        pre.setPhone("9000000001");
        pre.setGender("MALE");
        pre.setDateOfBirth(LocalDate.of(1985, 1, 1));
        pre.setHospitalId(MINE);
        pre.setCustomId("PAT1");
        patientRepository.saveAndFlush(pre);

        // Register new patient via registrar
        Patient newP = new Patient();
        newP.setName("New Patient");
        newP.setPhone("9000000002");
        newP.setGender("FEMALE");
        newP.setDateOfBirth(LocalDate.of(1992, 5, 10));
        newP.setHospitalId(MINE);

        Patient registered = patientRegistrar.persistNewPatient(newP);
        em.flush();
        em.clear();

        assertThat(registered.getCustomId()).isNotNull();
        assertThat(registered.getCustomId()).isNotEqualTo("PAT1");
        assertThat(registered.getCustomId()).startsWith("PAT");

        // Verify uniqueness in database
        List<Patient> all = patientRepository.findByHospitalIdAndIsActiveTrue(MINE);
        Set<String> customIds = all.stream().map(Patient::getCustomId).collect(Collectors.toSet());
        assertThat(customIds).hasSize(all.size());
    }

    @Test
    void billingService_skipsPreExistingCustomId_andAssignsUniqueId() {
        // Pre-create a bill with BIL1001 (e.g. BIL3100 in production)
        Billing pre = new Billing();
        pre.setHospitalId(MINE);
        pre.setPatientId(10L);
        pre.setDoctorId(20L);
        pre.setBillingType("GENERAL");
        pre.setAmount(new BigDecimal("500.00"));
        pre.setPaymentStatus("PENDING");
        pre.setCreatedAt(LocalDateTime.now());
        pre.setCustomId("BIL1001");
        billingRepository.saveAndFlush(pre);

        Billing newBill = new Billing();
        newBill.setHospitalId(MINE);
        newBill.setPatientId(11L);
        newBill.setDoctorId(20L);
        newBill.setBillingType("OPD");
        newBill.setAmount(new BigDecimal("250.00"));
        newBill.setPaymentStatus("PENDING");
        newBill.setCreatedAt(LocalDateTime.now());

        Billing saved = billingService.saveBillWithCollisionRetry(newBill);
        em.flush();
        em.clear();

        assertThat(saved.getCustomId()).isNotNull();
        assertThat(saved.getCustomId()).isNotEqualTo("BIL1001");
        assertThat(saved.getCustomId()).startsWith("BIL");
    }
}
