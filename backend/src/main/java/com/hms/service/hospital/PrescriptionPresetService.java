package com.hms.service.hospital;

import com.hms.entity.PrescriptionPreset;
import com.hms.entity.PrescriptionPresetItem;
import com.hms.repository.PrescriptionPresetItemRepository;
import com.hms.repository.PrescriptionPresetRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrescriptionPresetService {

    @Autowired
    private PrescriptionPresetRepository presetRepository;

    @Autowired
    private PrescriptionPresetItemRepository itemRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private PresetOwnershipSupport ownership;

    public List<PrescriptionPreset> listPresets() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (ownership.isAdmin()) {
            return presetRepository.findByHospitalIdAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId);
        }
        return presetRepository.findVisibleToDoctor(hospitalId, ownership.currentDoctorIdOrNull());
    }

    public List<PrescriptionPresetItem> getItems(Long presetId) {
        return itemRepository.findByPresetIdOrderBySortOrderAsc(presetId);
    }

    @Transactional
    public PrescriptionPreset createPreset(String name, List<PrescriptionPresetItem> items, Long requestedDoctorId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Preset name is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Preset must contain at least one medicine");
        }

        Long ownerDoctorId = ownership.resolveOwnerDoctorId(requestedDoctorId, hospitalId);

        int nextOrder = presetRepository.findByHospitalIdAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId).size();

        PrescriptionPreset preset = new PrescriptionPreset();
        preset.setHospitalId(hospitalId);
        preset.setDoctorId(ownerDoctorId);
        preset.setName(name.trim());
        preset.setDisplayOrder(nextOrder);
        preset.setIsActive(true);
        PrescriptionPreset saved = presetRepository.save(preset);

        saveItems(saved.getId(), items);
        ownership.notifyPresetsChanged(hospitalId);
        return saved;
    }

    /**
     * items == null means "leave the existing item list untouched" (e.g. a
     * name-only rename or a reorder); items == [] is rejected. Doctor assignment
     * is only changed by admins, and only on a real edit (name present) — reorder
     * calls send displayOrder only and must not disturb ownership.
     */
    @Transactional
    public PrescriptionPreset updatePreset(Long id, String name, List<PrescriptionPresetItem> items,
                                           Integer displayOrder, Long requestedDoctorId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PrescriptionPreset preset = findEditablePreset(id, hospitalId);

        if (name != null && !name.trim().isEmpty()) {
            preset.setName(name.trim());
            preset.setDoctorId(ownership.resolveOwnerDoctorId(requestedDoctorId, hospitalId));
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
        ownership.notifyPresetsChanged(hospitalId);
        return preset;
    }

    @Transactional
    public void deletePreset(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PrescriptionPreset preset = findEditablePreset(id, hospitalId);
        preset.setIsActive(false);
        presetRepository.save(preset);
        ownership.notifyPresetsChanged(hospitalId);
    }

    /**
     * Admins can edit any preset in the hospital; doctors only their own private
     * ones (shared presets are admin-managed and not editable by a doctor).
     */
    private PrescriptionPreset findEditablePreset(Long id, Long hospitalId) {
        if (ownership.isAdmin()) {
            return presetRepository.findByIdAndHospitalId(id, hospitalId)
                    .orElseThrow(() -> new RuntimeException("Preset not found"));
        }
        return presetRepository.findByIdAndHospitalIdAndDoctorId(id, hospitalId, ownership.currentDoctorIdOrNull())
                .orElseThrow(() -> new RuntimeException("Preset not found"));
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
