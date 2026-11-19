package com.hms.service.hospital;

import com.hms.entity.IcuInfusion;
import com.hms.entity.IcuVentilatorSetting;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Surgery;
import com.hms.repository.IcuInfusionRepository;
import com.hms.repository.IcuVentilatorSettingRepository;
import com.hms.repository.SurgeryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpdClinicalStateTest {

    @Mock SurgeryRepository surgeryRepository;
    @Mock IcuVentilatorSettingRepository ventilatorRepository;
    @Mock IcuInfusionRepository infusionRepository;
    @Mock SurgeryService surgeryService;
    @InjectMocks IpdClinicalState state;

    private IpdAdmission admission() {
        IpdAdmission a = new IpdAdmission();
        a.setId(9L);
        a.setHospitalId(1L);
        a.setStatus("ADMITTED");
        return a;
    }

    private Surgery surgery(String status) {
        Surgery s = new Surgery();
        s.setPublicId("s-1");
        s.setProcedureName("Hernia repair");
        s.setStatus(status);
        return s;
    }

    private void ventilation(String status) {
        IcuVentilatorSetting v = new IcuVentilatorSetting();
        v.setId(1L);
        v.setVentilationStatus(status);
        v.setObservedAt(LocalDateTime.now().minusMinutes(5));
        lenient().when(ventilatorRepository.findSupersededIds(9L, 1L)).thenReturn(List.of());
        lenient().when(ventilatorRepository.findByIpdAdmissionIdAndHospitalIdAndIsActiveTrueOrderByObservedAtDescIdDesc(9L, 1L))
                .thenReturn(List.of(v));
    }

    @Test
    void dischargeTypesNormaliseCommonSpellings() {
        assertThat(IpdClinicalState.normaliseDischargeType(null)).isEqualTo("NORMAL");
        assertThat(IpdClinicalState.normaliseDischargeType("dama")).isEqualTo("LAMA");
        assertThat(IpdClinicalState.normaliseDischargeType("Death")).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> IpdClinicalState.normaliseDischargeType("HOLIDAY"))
                .hasMessageContaining("Unknown discharge type");
    }

    @Test
    void onlyDeathOrAbscondingLeavesWithDues() {
        assertThat(IpdClinicalState.dischargeAllowedWithDues("EXPIRED")).isTrue();
        assertThat(IpdClinicalState.dischargeAllowedWithDues("ABSCONDED")).isTrue();
        assertThat(IpdClinicalState.dischargeAllowedWithDues("LAMA")).isFalse();
        assertThat(IpdClinicalState.dischargeAllowedWithDues("NORMAL")).isFalse();
        assertThat(IpdClinicalState.icuDisposition("REFERRED")).isEqualTo("REFERRED_OUT");
    }

    @Test
    void aDischargedAdmissionTakesNoNewEntries() {
        IpdAdmission a = admission();
        a.setStatus("DISCHARGED");
        assertThatThrownBy(() -> IpdClinicalState.requireActive(a)).hasMessageContaining("already been discharged");
        a.setStatus("DISCHARGE_PLANNED");
        assertThatCode(() -> IpdClinicalState.requireActive(a)).doesNotThrowAnyException();
    }

    @Test
    void aScheduledSurgeryBlocksANormalDischarge() {
        when(surgeryRepository.findByIpdAdmissionIdAndStatusIn(eq(9L), any())).thenReturn(List.of(surgery("SCHEDULED")));
        assertThatThrownBy(() -> state.assertDischargeAllowed(admission(), "NORMAL"))
                .hasMessageContaining("Hernia repair").hasMessageContaining("scheduled");
    }

    @Test
    void aSurgeryInProgressBlocksEvenADeathDischarge() {
        when(surgeryRepository.findByIpdAdmissionIdAndStatusIn(eq(9L), any())).thenReturn(List.of(surgery("IN_PROGRESS")));
        assertThatThrownBy(() -> state.assertDischargeAllowed(admission(), "EXPIRED"))
                .hasMessageContaining("in progress");
    }

    @Test
    void aNotStartedSurgeryIsCancelledWhenThePatientDies() {
        when(surgeryRepository.findByIpdAdmissionIdAndStatusIn(eq(9L), any())).thenReturn(List.of(surgery("SCHEDULED")));
        when(infusionRepository.findByIpdAdmissionIdAndHospitalIdAndIsActiveTrueAndStoppedAtIsNullOrderByStartedAtDesc(9L, 1L))
                .thenReturn(List.of());
        assertThatCode(() -> state.assertDischargeAllowed(admission(), "EXPIRED")).doesNotThrowAnyException();

        state.closeOutForDischarge(admission(), "EXPIRED");
        verify(surgeryService).cancel("s-1", "OTHER", "Patient expired");
    }

    @Test
    void aVentilatedPatientCannotGoHomeButCanBeReferred() {
        when(surgeryRepository.findByIpdAdmissionIdAndStatusIn(anyLong(), any())).thenReturn(List.of());
        ventilation("INVASIVE");
        assertThatThrownBy(() -> state.assertDischargeAllowed(admission(), "NORMAL"))
                .hasMessageContaining("ventilator");
        assertThatCode(() -> state.assertDischargeAllowed(admission(), "REFERRED")).doesNotThrowAnyException();
    }

    @Test
    void ventilationOffAllowsANormalDischarge() {
        when(surgeryRepository.findByIpdAdmissionIdAndStatusIn(anyLong(), any())).thenReturn(List.of());
        ventilation("OFF");
        assertThatCode(() -> state.assertDischargeAllowed(admission(), "NORMAL")).doesNotThrowAnyException();
    }

    @Test
    void runningInfusionsAreStoppedAtDischarge() {
        IcuInfusion inf = new IcuInfusion();
        inf.setStartedAt(LocalDateTime.now().minusHours(2));
        when(infusionRepository.findByIpdAdmissionIdAndHospitalIdAndIsActiveTrueAndStoppedAtIsNullOrderByStartedAtDesc(9L, 1L))
                .thenReturn(List.of(inf));

        state.closeOutForDischarge(admission(), "NORMAL");

        assertThat(inf.getStoppedAt()).isNotNull();
        assertThat(inf.getStopReason()).contains("discharged");
        verify(infusionRepository).save(inf);
        verify(surgeryService, never()).cancel(any(), any(), any());
    }
}
