package com.hms.service;

import com.hms.entity.VitalSigns;
import com.hms.repository.NurseAssessmentRepository;
import com.hms.repository.VitalSignsRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.MrdService;
import com.hms.service.hospital.NurseAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NurseAssessmentServiceTest {

    @Mock private NurseAssessmentRepository assessmentRepository;
    @Mock private VitalSignsRepository vitalsRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private MrdService mrdService;

    @InjectMocks
    private NurseAssessmentService service;

    private void stubCommon() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        lenient().when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(vitalsRepository.save(any(VitalSigns.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordVitals_parsesLegacyBloodPressureStringIntoStructuredColumns() {
        stubCommon();
        Map<String, Object> data = new HashMap<>();
        data.put("bloodPressure", "110/70");
        data.put("pulse", 80);
        data.put("respiratoryRate", 18);

        VitalSigns saved = service.recordVitals(10L, data);

        assertThat(saved.getBpSystolic()).isEqualTo(110);
        assertThat(saved.getBpDiastolic()).isEqualTo(70);
        assertThat(saved.getBloodPressure()).isEqualTo("110/70");
        assertThat(saved.getRespiratoryRate()).isEqualTo(18);
    }

    @Test
    void recordVitals_acceptsStructuredBpAndBackfillsLegacyString() {
        stubCommon();
        Map<String, Object> data = new HashMap<>();
        data.put("bpSystolic", 120);
        data.put("bpDiastolic", 80);
        data.put("painScore", 3);
        data.put("weight", "72.5");
        data.put("oxygenSupport", "ROOM_AIR");
        data.put("remarks", "stable");

        VitalSigns saved = service.recordVitals(10L, data);

        assertThat(saved.getBpSystolic()).isEqualTo(120);
        assertThat(saved.getBpDiastolic()).isEqualTo(80);
        assertThat(saved.getBloodPressure()).isEqualTo("120/80");
        assertThat(saved.getPainScore()).isEqualTo(3);
        assertThat(saved.getWeight()).isEqualByComparingTo(new BigDecimal("72.5"));
        assertThat(saved.getOxygenSupport()).isEqualTo("ROOM_AIR");
        assertThat(saved.getRemarks()).isEqualTo("stable");
    }

    @Test
    void recordVitals_treatsBlankNumericFieldsAsNullWithoutThrowing() {
        // The frontend posts every field, sending "" for the ones a nurse leaves blank.
        // Blank numeric strings must coerce to null, not throw NumberFormatException (HTTP 500).
        stubCommon();
        Map<String, Object> data = new HashMap<>();
        data.put("bloodPressure", "118/76");
        data.put("pulse", "");
        data.put("temperature", "");
        data.put("spo2", "");
        data.put("respiratoryRate", "  ");
        data.put("painScore", "");
        data.put("weight", "");

        VitalSigns saved = service.recordVitals(10L, data);

        assertThat(saved.getBpSystolic()).isEqualTo(118);
        assertThat(saved.getBpDiastolic()).isEqualTo(76);
        assertThat(saved.getPulse()).isNull();
        assertThat(saved.getTemperature()).isNull();
        assertThat(saved.getSpo2()).isNull();
        assertThat(saved.getRespiratoryRate()).isNull();
        assertThat(saved.getPainScore()).isNull();
        assertThat(saved.getWeight()).isNull();
    }
}
