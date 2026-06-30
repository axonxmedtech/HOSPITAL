package com.hms.service;

import com.hms.dto.CdssAlertDTO;
import com.hms.dto.EwsResultDTO;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.CdssEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class CdssEvaluationServiceTest {

    @InjectMocks private CdssEvaluationService service;
    @Mock private PatientAllergyRepository patientAllergyRepo;
    @Mock private PrescriptionRepository prescriptionRepo;
    @Mock private DrugInteractionMasterRepository drugInteractionRepo;
    @Mock private CdssAlertLogRepository alertLogRepo;
    @Mock private VitalSignsRepository vitalSignsRepo;
    @Mock private LabOrderRepository labOrderRepo;
    @Mock private RadiologyOrderRepository radiologyOrderRepo;
    @Mock private IpdAdmissionRepository ipdAdmissionRepo;
    @Mock private SecurityContextHelper securityHelper;

    private static final Long HOSPITAL_ID = 1L;
    private static final Long PATIENT_ID = 10L;
    private static final Long ADMISSION_ID = 100L;

    @BeforeEach
    void setUp() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        Mockito.lenient().when(securityHelper.getCurrentUserId()).thenReturn(99L);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(ADMISSION_ID);
        admission.setHospitalId(HOSPITAL_ID);
        admission.setPatientId(PATIENT_ID);
        Mockito.lenient().when(ipdAdmissionRepo.findById(ADMISSION_ID)).thenReturn(Optional.of(admission));
    }

    @Test
    void evaluatePrescription_allergyMatch_returnsAllergyAlert() {
        when(patientAllergyRepo.findAllergyNamesByPatientId(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(List.of("Penicillin"));
        when(prescriptionRepo.findByIpdAdmissionIdAndStatus(ADMISSION_ID, "ACTIVE"))
                .thenReturn(Collections.emptyList());
        when(drugInteractionRepo.findInteractionsInvolvingMedicine(eq(HOSPITAL_ID), anyString()))
                .thenReturn(Collections.emptyList());

        List<CdssAlertDTO> alerts = service.evaluatePrescription(
                PATIENT_ID, "Penicillin VK 500mg", ADMISSION_ID);

        assertEquals(1, alerts.size());
        assertEquals("ALLERGY", alerts.get(0).getType());
        assertEquals("HIGH", alerts.get(0).getSeverity());
    }

    @Test
    void evaluatePrescription_noAllergy_returnsEmpty() {
        when(patientAllergyRepo.findAllergyNamesByPatientId(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(List.of("Penicillin"));
        when(prescriptionRepo.findByIpdAdmissionIdAndStatus(ADMISSION_ID, "ACTIVE"))
                .thenReturn(Collections.emptyList());
        when(drugInteractionRepo.findInteractionsInvolvingMedicine(eq(HOSPITAL_ID), anyString()))
                .thenReturn(Collections.emptyList());

        List<CdssAlertDTO> alerts = service.evaluatePrescription(
                PATIENT_ID, "Paracetamol 500mg", ADMISSION_ID);

        assertTrue(alerts.stream().noneMatch(a -> "ALLERGY".equals(a.getType())));
    }

    @Test
    void evaluatePrescription_duplicateMedicine_returnsDuplicateAlert() {
        Prescription existing = new Prescription();
        existing.setMedicineName("Metformin 500mg");
        existing.setStatus("ACTIVE");

        when(patientAllergyRepo.findAllergyNamesByPatientId(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(Collections.emptyList());
        when(prescriptionRepo.findByIpdAdmissionIdAndStatus(ADMISSION_ID, "ACTIVE"))
                .thenReturn(List.of(existing));
        when(drugInteractionRepo.findInteractionsInvolvingMedicine(eq(HOSPITAL_ID), anyString()))
                .thenReturn(Collections.emptyList());

        List<CdssAlertDTO> alerts = service.evaluatePrescription(
                PATIENT_ID, "Metformin 500mg", ADMISSION_ID);

        assertTrue(alerts.stream().anyMatch(a -> "DUPLICATE_MEDICINE".equals(a.getType())));
    }

    @Test
    void evaluatePrescription_drugInteraction_returnsInteractionAlert() {
        Prescription existing = new Prescription();
        existing.setMedicineName("Warfarin 5mg");
        existing.setStatus("ACTIVE");

        DrugInteractionMaster dim = new DrugInteractionMaster();
        dim.setDrugAName("Warfarin");
        dim.setDrugBName("Aspirin");
        dim.setSeverity("HIGH");
        dim.setInteractionDescription("Bleeding risk increased.");

        when(patientAllergyRepo.findAllergyNamesByPatientId(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(Collections.emptyList());
        when(prescriptionRepo.findByIpdAdmissionIdAndStatus(ADMISSION_ID, "ACTIVE"))
                .thenReturn(List.of(existing));
        when(drugInteractionRepo.findInteractionsInvolvingMedicine(HOSPITAL_ID, "Aspirin 100mg"))
                .thenReturn(List.of(dim));

        List<CdssAlertDTO> alerts = service.evaluatePrescription(
                PATIENT_ID, "Aspirin 100mg", ADMISSION_ID);

        assertTrue(alerts.stream().anyMatch(a -> "DRUG_INTERACTION".equals(a.getType())));
    }

    @Test
    void calculateEws_normalVitals_returnsNormalSeverity() {
        VitalSigns v = new VitalSigns();
        v.setBloodPressure("120/80");
        v.setPulse(75);
        v.setTemperature(new BigDecimal("36.8"));
        v.setSpo2(98);
        v.setRespiratoryRate(16);

        when(vitalSignsRepo.findByIpdAdmissionIdOrderByRecordedAtDesc(ADMISSION_ID))
                .thenReturn(List.of(v));

        EwsResultDTO result = service.calculateEws(ADMISSION_ID);

        assertEquals("NORMAL", result.getSeverity());
        assertEquals(0, result.getTotalScore());
    }

    @Test
    void calculateEws_abnormalVitals_returnsHighSeverity() {
        VitalSigns v = new VitalSigns();
        v.setBloodPressure("85/50");
        v.setPulse(135);
        v.setTemperature(new BigDecimal("39.5"));
        v.setSpo2(91);
        v.setRespiratoryRate(26);

        when(vitalSignsRepo.findByIpdAdmissionIdOrderByRecordedAtDesc(ADMISSION_ID))
                .thenReturn(List.of(v));

        EwsResultDTO result = service.calculateEws(ADMISSION_ID);

        assertEquals("HIGH", result.getSeverity());
        assertTrue(result.getTotalScore() >= 5);
    }

    @Test
    void calculateEws_noVitals_returnsUnknown() {
        when(vitalSignsRepo.findByIpdAdmissionIdOrderByRecordedAtDesc(ADMISSION_ID))
                .thenReturn(Collections.emptyList());

        EwsResultDTO result = service.calculateEws(ADMISSION_ID);

        assertEquals("UNKNOWN", result.getSeverity());
    }

    @Test
    void evaluateLabResult_abnormal_returnsCriticalAlert() {
        when(alertLogRepo.save(any())).thenReturn(new CdssAlertLog());

        List<CdssAlertDTO> alerts = service.evaluateLabResult(
                PATIENT_ID, ADMISSION_ID, "CBC", true,
                "[{\"name\":\"WBC\",\"value\":\"16000\",\"unit\":\"/µL\",\"referenceRange\":\"4000-11000\",\"flag\":\"High\"}]");

        assertEquals(1, alerts.size());
        assertEquals("CRITICAL_LAB", alerts.get(0).getType());
        verify(alertLogRepo).save(any(CdssAlertLog.class));
    }

    @Test
    void evaluateLabResult_normal_returnsEmpty() {
        List<CdssAlertDTO> alerts = service.evaluateLabResult(
                PATIENT_ID, ADMISSION_ID, "CBC", false, "[]");

        assertTrue(alerts.isEmpty());
        verify(alertLogRepo, never()).save(any());
    }
}
