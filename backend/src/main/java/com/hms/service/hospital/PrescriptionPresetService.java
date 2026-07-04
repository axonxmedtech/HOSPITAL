package com.hms.service.hospital;

import com.hms.entity.PrescriptionPreset;
import com.hms.entity.PrescriptionPresetItem;
import com.hms.repository.DoctorRepository;
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
    private DoctorRepository doctorRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    /** Tell every connected client for this hospital to reload its preset lists. */
    private void notifyPresetsChanged(Long hospitalId) {
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"PRESETS_UPDATED\"}");
        } catch (Exception ignored) {
            // best-effort real-time sync; a failed broadcast must not fail the write
        }
    }

    private boolean isAdmin() {
        return "HOSPITAL_ADMIN".equals(securityHelper.getCurrentUserRole());
    }

    /**
     * The id of the doctor making the request, or null if the caller is not a
     * doctor (e.g. an admin) or has no matching doctor profile.
     */
    private Long currentDoctorIdOrNull() {
        if (!"DOCTOR".equals(securityHelper.getCurrentUserRole())) {
            return null;
        }
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId)
                .map(com.hms.entity.Doctor::getId)
                .orElse(null);
    }

    /**
     * Decide the owning doctor for a preset being created/edited.
     * - An admin explicitly assigning to a doctor via the dashboard wins.
     * - Otherwise, if the caller is (or acts as) a doctor — a real DOCTOR, or a
     *   single-doctor-clinic admin who consults as the sole doctor — the preset
     *   is owned by that doctor. This resolves the caller by email, so it works
     *   even when a single-doctor clinic has no separate DOCTOR login.
     * - A pure admin with no doctor profile falls back to the dashboard choice
     *   (null = shared).
     */
    private Long resolveOwnerDoctorId(Long requestedDoctorId, Long hospitalId) {
        if (isAdmin() && requestedDoctorId != null) {
            return sanitizeAssignedDoctorId(requestedDoctorId, hospitalId);
        }
        Long selfDoctorId = doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId)
                .map(com.hms.entity.Doctor::getId)
                .orElse(null);
        if (selfDoctorId != null) {
            return selfDoctorId;
        }
        return sanitizeAssignedDoctorId(requestedDoctorId, hospitalId);
    }

    /**
     * Validate that an admin-supplied doctorId (may be null = shared) belongs to
     * this hospital; returns the id unchanged, or null when shared/invalid.
     */
    private Long sanitizeAssignedDoctorId(Long doctorId, Long hospitalId) {
        if (doctorId == null) {
            return null; // shared
        }
        boolean belongs = doctorRepository.findById(doctorId)
                .map(d -> hospitalId.equals(d.getHospitalId()))
                .orElse(false);
        if (!belongs) {
            throw new IllegalArgumentException("Assigned doctor does not belong to this hospital");
        }
        return doctorId;
    }

    public List<PrescriptionPreset> listPresets() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (isAdmin()) {
            return presetRepository.findByHospitalIdAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId);
        }
        return presetRepository.findVisibleToDoctor(hospitalId, currentDoctorIdOrNull());
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

        Long ownerDoctorId = resolveOwnerDoctorId(requestedDoctorId, hospitalId);

        int nextOrder = presetRepository.findByHospitalIdAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId).size();

        PrescriptionPreset preset = new PrescriptionPreset();
        preset.setHospitalId(hospitalId);
        preset.setDoctorId(ownerDoctorId);
        preset.setName(name.trim());
        preset.setDisplayOrder(nextOrder);
        preset.setIsActive(true);
        PrescriptionPreset saved = presetRepository.save(preset);

        saveItems(saved.getId(), items);
        notifyPresetsChanged(hospitalId);
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
            preset.setDoctorId(resolveOwnerDoctorId(requestedDoctorId, hospitalId));
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
        notifyPresetsChanged(hospitalId);
        return preset;
    }

    @Transactional
    public void deletePreset(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PrescriptionPreset preset = findEditablePreset(id, hospitalId);
        preset.setIsActive(false);
        presetRepository.save(preset);
        notifyPresetsChanged(hospitalId);
    }

    /**
     * Admins can edit any preset in the hospital; doctors only their own private
     * ones (shared presets are admin-managed and not editable by a doctor).
     */
    private PrescriptionPreset findEditablePreset(Long id, Long hospitalId) {
        if (isAdmin()) {
            return presetRepository.findByIdAndHospitalId(id, hospitalId)
                    .orElseThrow(() -> new RuntimeException("Preset not found"));
        }
        return presetRepository.findByIdAndHospitalIdAndDoctorId(id, hospitalId, currentDoctorIdOrNull())
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
