package com.hms.service.hospital;

import com.hms.entity.ConsultationNotePreset;
import com.hms.repository.ConsultationNotePresetRepository;
import com.hms.repository.DoctorRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsultationNotePresetService {

    @Autowired
    private ConsultationNotePresetRepository presetRepository;

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
     * Decide the owning doctor for a note being created/edited. An admin
     * explicitly assigning via the dashboard wins; otherwise the caller who acts
     * as a doctor (a real DOCTOR, or a single-doctor-clinic admin consulting as
     * the sole doctor, resolved by email) owns it; a pure admin falls back to the
     * dashboard choice (null = shared).
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

    public List<ConsultationNotePreset> listPresets(String fieldType) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (isAdmin()) {
            return presetRepository.findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId, fieldType);
        }
        return presetRepository.findVisibleToDoctor(hospitalId, fieldType, currentDoctorIdOrNull());
    }

    public ConsultationNotePreset createPreset(String fieldType, String text, Long requestedDoctorId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Preset text is required");
        }

        Long ownerDoctorId = resolveOwnerDoctorId(requestedDoctorId, hospitalId);

        int nextOrder = presetRepository
                .findByHospitalIdAndFieldTypeAndIsActiveTrueOrderByDisplayOrderAsc(hospitalId, fieldType)
                .size();

        ConsultationNotePreset preset = new ConsultationNotePreset();
        preset.setHospitalId(hospitalId);
        preset.setDoctorId(ownerDoctorId);
        preset.setFieldType(fieldType);
        preset.setText(text.trim());
        preset.setDisplayOrder(nextOrder);
        preset.setIsActive(true);
        ConsultationNotePreset saved = presetRepository.save(preset);
        notifyPresetsChanged(hospitalId);
        return saved;
    }

    /**
     * Doctor assignment is only changed by admins, and only on a real edit (text
     * present) — reorder calls send displayOrder only and must not disturb ownership.
     */
    public ConsultationNotePreset updatePreset(Long id, String text, Integer displayOrder, Long requestedDoctorId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ConsultationNotePreset preset = findEditablePreset(id, hospitalId);

        if (text != null && !text.trim().isEmpty()) {
            preset.setText(text.trim());
            preset.setDoctorId(resolveOwnerDoctorId(requestedDoctorId, hospitalId));
        }
        if (displayOrder != null) {
            preset.setDisplayOrder(displayOrder);
        }
        ConsultationNotePreset saved = presetRepository.save(preset);
        notifyPresetsChanged(hospitalId);
        return saved;
    }

    public void deletePreset(Long id) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ConsultationNotePreset preset = findEditablePreset(id, hospitalId);
        preset.setIsActive(false);
        presetRepository.save(preset);
        notifyPresetsChanged(hospitalId);
    }

    /**
     * Admins can edit any preset in the hospital; doctors only their own private
     * ones (shared presets are admin-managed and not editable by a doctor).
     */
    private ConsultationNotePreset findEditablePreset(Long id, Long hospitalId) {
        if (isAdmin()) {
            return presetRepository.findByIdAndHospitalId(id, hospitalId)
                    .orElseThrow(() -> new RuntimeException("Preset not found"));
        }
        return presetRepository.findByIdAndHospitalIdAndDoctorId(id, hospitalId, currentDoctorIdOrNull())
                .orElseThrow(() -> new RuntimeException("Preset not found"));
    }
}
