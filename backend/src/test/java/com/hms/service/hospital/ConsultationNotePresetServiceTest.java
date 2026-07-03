package com.hms.service.hospital;

import com.hms.entity.ConsultationNotePreset;
import com.hms.repository.ConsultationNotePresetRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsultationNotePresetServiceTest {

    @Mock ConsultationNotePresetRepository presetRepository;
    @Mock SecurityContextHelper securityHelper;

    @InjectMocks ConsultationNotePresetService service;

    @Test
    void listPresets_returnsHospitalScopedActivePresetsInOrder() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        ConsultationNotePreset p1 = new ConsultationNotePreset();
        p1.setText("Avoid oily food");
        when(presetRepository.findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(1L, "TREATMENT_NOTES"))
                .thenReturn(List.of(p1));

        List<ConsultationNotePreset> result = service.listPresets("TREATMENT_NOTES");

        assertThat(result).containsExactly(p1);
    }

    @Test
    void createPreset_blankText_throws() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        assertThatThrownBy(() -> service.createPreset("TREATMENT_NOTES", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void createPreset_validText_savesTrimmedWithHospitalIdAndNextDisplayOrder() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(presetRepository.findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(1L, "TREATMENT_NOTES"))
                .thenReturn(List.of(new ConsultationNotePreset(), new ConsultationNotePreset())); // 2 existing
        when(presetRepository.save(any(ConsultationNotePreset.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsultationNotePreset result = service.createPreset("TREATMENT_NOTES", "  Avoid oily food  ");

        assertThat(result.getText()).isEqualTo("Avoid oily food");
        assertThat(result.getHospitalId()).isEqualTo(1L);
        assertThat(result.getFieldType()).isEqualTo("TREATMENT_NOTES");
        assertThat(result.getDisplayOrder()).isEqualTo(2);
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void updatePreset_notFoundForHospital_throws() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(presetRepository.findByIdAndHospitalId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePreset(99L, "New text", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updatePreset_updatesTextAndDisplayOrder() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        ConsultationNotePreset existing = new ConsultationNotePreset();
        existing.setId(5L);
        existing.setHospitalId(1L);
        existing.setText("Old text");
        existing.setDisplayOrder(0);
        when(presetRepository.findByIdAndHospitalId(5L, 1L)).thenReturn(Optional.of(existing));
        when(presetRepository.save(any(ConsultationNotePreset.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsultationNotePreset result = service.updatePreset(5L, "New text", 3);

        assertThat(result.getText()).isEqualTo("New text");
        assertThat(result.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    void deletePreset_softDeletesWithinHospitalScope() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        ConsultationNotePreset existing = new ConsultationNotePreset();
        existing.setId(5L);
        existing.setHospitalId(1L);
        existing.setIsActive(true);
        when(presetRepository.findByIdAndHospitalId(5L, 1L)).thenReturn(Optional.of(existing));
        when(presetRepository.save(any(ConsultationNotePreset.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deletePreset(5L);

        assertThat(existing.getIsActive()).isFalse();
        verify(presetRepository).save(existing);
    }

    @Test
    void deletePreset_notFoundForHospital_throws() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(presetRepository.findByIdAndHospitalId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePreset(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }
}
