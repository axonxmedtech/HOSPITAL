package com.hms.service.hospital;

import com.hms.exception.ResourceNotFoundException;

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

    @Autowired
    private PresetOwnershipSupport ownership;

    @Autowired
    private com.hms.repository.ConsultationStatementRepository statementRepository;

    public List<ConsultationNotePreset> listPresets(String fieldType) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (ownership.isAdmin()) {
            return presetRepository.findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId, fieldType);
        }
        return presetRepository.findVisibleToDoctor(hospitalId, fieldType, ownership.currentDoctorIdOrNull());
    }

    public ConsultationNotePreset createPreset(String fieldType, String text, Long requestedDoctorId) {
        return createPreset(fieldType, text, requestedDoctorId, null, null);
    }

    public ConsultationNotePreset createPreset(String fieldType, String text, Long requestedDoctorId, String marathiText, String hindiText) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Preset text is required");
        }

        Long ownerDoctorId = ownership.resolveOwnerDoctorId(requestedDoctorId, hospitalId);

        int nextOrder = presetRepository
                .findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId, fieldType)
                .size();

        String trimmedText = text.trim();
        String resolvedMarathi = (marathiText != null && !marathiText.trim().isEmpty()) ? marathiText.trim() : null;
        String resolvedHindi = (hindiText != null && !hindiText.trim().isEmpty()) ? hindiText.trim() : null;

        // Auto-match translations from master platform statements if omitted
        if ((resolvedMarathi == null || resolvedHindi == null) && statementRepository != null) {
            try {
                var stmtOpt = statementRepository.findMatchingActiveStatements(trimmedText).stream().findFirst();
                if (stmtOpt.isPresent()) {
                    var stmt = stmtOpt.get();
                    if (resolvedMarathi == null && stmt.getMarathiText() != null && !stmt.getMarathiText().isBlank()) {
                        resolvedMarathi = stmt.getMarathiText().trim();
                    }
                    if (resolvedHindi == null && stmt.getHindiText() != null && !stmt.getHindiText().isBlank()) {
                        resolvedHindi = stmt.getHindiText().trim();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        ConsultationNotePreset preset = new ConsultationNotePreset();
        preset.setHospitalId(hospitalId);
        preset.setDoctorId(ownerDoctorId);
        preset.setFieldType(fieldType);
        preset.setText(trimmedText);
        preset.setMarathiText(resolvedMarathi);
        preset.setHindiText(resolvedHindi);
        preset.setDisplayOrder(nextOrder);
        preset.setIsActive(true);
        ConsultationNotePreset saved = presetRepository.save(preset);
        ownership.notifyPresetsChanged(hospitalId);
        return saved;
    }

    /**
     * Doctor assignment is only changed by admins, and only on a real edit (text
     * present) — reorder calls send displayOrder only and must not disturb ownership.
     */
    public ConsultationNotePreset updatePreset(Long id, String text, Integer displayOrder, Long requestedDoctorId) {
        return updatePreset(id, text, displayOrder, requestedDoctorId, null, null);
    }

    public ConsultationNotePreset updatePreset(Long id, String text, Integer displayOrder, Long requestedDoctorId, String marathiText, String hindiText) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ConsultationNotePreset preset = findEditablePreset(id, hospitalId);

        if (text != null && !text.trim().isEmpty()) {
            preset.setText(text.trim());
            preset.setDoctorId(ownership.resolveOwnerDoctorId(requestedDoctorId, hospitalId));
        }
        if (displayOrder != null) {
            preset.setDisplayOrder(displayOrder);
        }
        if (marathiText != null) {
            preset.setMarathiText(marathiText.trim().isEmpty() ? null : marathiText.trim());
        }
        if (hindiText != null) {
            preset.setHindiText(hindiText.trim().isEmpty() ? null : hindiText.trim());
        }
        ConsultationNotePreset saved = presetRepository.save(preset);
        ownership.notifyPresetsChanged(hospitalId);
        return saved;
    }

    public void deletePreset(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ConsultationNotePreset preset = findEditablePreset(id, hospitalId);
        preset.setIsActive(false);
        presetRepository.save(preset);
        ownership.notifyPresetsChanged(hospitalId);
    }

    /**
     * Admins can edit any preset in the hospital; doctors only their own private
     * ones (shared presets are admin-managed and not editable by a doctor).
     */
    private ConsultationNotePreset findEditablePreset(Long id, Long hospitalId) {
        if (ownership.isAdmin()) {
            return presetRepository.findByIdAndHospitalId(id, hospitalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Preset not found"));
        }
        return presetRepository.findByIdAndHospitalIdAndDoctorId(id, hospitalId, ownership.currentDoctorIdOrNull())
                .orElseThrow(() -> new ResourceNotFoundException("Preset not found"));
    }
}
