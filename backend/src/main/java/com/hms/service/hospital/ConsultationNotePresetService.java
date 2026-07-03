package com.hms.service.hospital;

import com.hms.entity.ConsultationNotePreset;
import com.hms.repository.ConsultationNotePresetRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsultationNotePresetService {

    @Autowired
    private ConsultationNotePresetRepository presetRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    public List<ConsultationNotePreset> listPresets(String fieldType) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return presetRepository.findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId, fieldType);
    }

    public ConsultationNotePreset createPreset(String fieldType, String text) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Preset text is required");
        }
        int nextOrder = presetRepository
                .findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId, fieldType)
                .size();

        ConsultationNotePreset preset = new ConsultationNotePreset();
        preset.setHospitalId(hospitalId);
        preset.setFieldType(fieldType);
        preset.setText(text.trim());
        preset.setDisplayOrder(nextOrder);
        preset.setIsActive(true);
        return presetRepository.save(preset);
    }

    public ConsultationNotePreset updatePreset(Long id, String text, Integer displayOrder) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ConsultationNotePreset preset = presetRepository.findByIdAndHospitalId(id, hospitalId)
                .orElseThrow(() -> new RuntimeException("Preset not found"));

        if (text != null && !text.trim().isEmpty()) {
            preset.setText(text.trim());
        }
        if (displayOrder != null) {
            preset.setDisplayOrder(displayOrder);
        }
        return presetRepository.save(preset);
    }

    public void deletePreset(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ConsultationNotePreset preset = presetRepository.findByIdAndHospitalId(id, hospitalId)
                .orElseThrow(() -> new RuntimeException("Preset not found"));
        preset.setIsActive(false);
        presetRepository.save(preset);
    }
}
