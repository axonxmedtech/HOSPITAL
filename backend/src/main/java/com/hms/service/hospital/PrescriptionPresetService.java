package com.hms.service.hospital;

import com.hms.entity.PrescriptionPreset;
import com.hms.entity.PrescriptionPresetItem;
import com.hms.repository.PrescriptionPresetItemRepository;
import com.hms.repository.PrescriptionPresetRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PrescriptionPresetService {

    @Autowired
    private PrescriptionPresetRepository presetRepository;

    @Autowired
    private PrescriptionPresetItemRepository itemRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    public List<PrescriptionPreset> listPresets() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return presetRepository.findByHospitalIdAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId);
    }

    public List<PrescriptionPresetItem> getItems(Long presetId) {
        return itemRepository.findByPresetIdOrderBySortOrderAsc(presetId);
    }

    public PrescriptionPreset createPreset(String name, List<PrescriptionPresetItem> items) {
        // hospitalId is fetched first so every call path touches securityHelper
        // consistently, matching the pattern established in ConsultationNotePresetService.
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Preset name is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Preset must contain at least one medicine");
        }
        int nextOrder = presetRepository.findByHospitalIdAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId).size();

        PrescriptionPreset preset = new PrescriptionPreset();
        preset.setHospitalId(hospitalId);
        preset.setName(name.trim());
        preset.setDisplayOrder(nextOrder);
        preset.setIsActive(true);
        PrescriptionPreset saved = presetRepository.save(preset);

        saveItems(saved.getId(), items);
        return saved;
    }

    /**
     * items == null means "leave the existing item list untouched" (e.g. a
     * name-only rename); items == [] is rejected — a preset always needs at
     * least one medicine, so an empty list is never a valid full replacement.
     */
    public PrescriptionPreset updatePreset(Long id, String name, List<PrescriptionPresetItem> items, Integer displayOrder) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PrescriptionPreset preset = presetRepository.findByIdAndHospitalId(id, hospitalId)
                .orElseThrow(() -> new RuntimeException("Preset not found"));

        if (name != null && !name.trim().isEmpty()) {
            preset.setName(name.trim());
        }
        if (displayOrder != null) {
            preset.setDisplayOrder(displayOrder);
        }
        presetRepository.save(preset);

        if (items != null) {
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Preset must contain at least one medicine");
            }
            itemRepository.deleteByPresetId(id);
            saveItems(id, items);
        }
        return preset;
    }

    public void deletePreset(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PrescriptionPreset preset = presetRepository.findByIdAndHospitalId(id, hospitalId)
                .orElseThrow(() -> new RuntimeException("Preset not found"));
        preset.setIsActive(false);
        presetRepository.save(preset);
    }

    private void saveItems(Long presetId, List<PrescriptionPresetItem> items) {
        int order = 0;
        for (PrescriptionPresetItem item : items) {
            PrescriptionPresetItem toSave = new PrescriptionPresetItem();
            toSave.setPresetId(presetId);
            toSave.setMedicineName(item.getMedicineName());
            toSave.setDosage(item.getDosage());
            toSave.setFrequency(item.getFrequency());
            toSave.setDuration(item.getDuration());
            toSave.setInstructions(item.getInstructions());
            toSave.setSortOrder(order++);
            itemRepository.save(toSave);
        }
    }
}
