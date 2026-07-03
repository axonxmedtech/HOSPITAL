package com.hms.controller.hospital;

import com.hms.dto.ConsultationNotePresetDTO;
import com.hms.entity.ConsultationNotePreset;
import com.hms.service.hospital.ConsultationNotePresetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/hospital/consultation-note-presets")
public class ConsultationNotePresetController {

    @Autowired
    private ConsultationNotePresetService presetService;

    private ConsultationNotePresetDTO toDto(ConsultationNotePreset p) {
        return new ConsultationNotePresetDTO(p.getId(), p.getFieldType(), p.getText(), p.getDisplayOrder());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> listPresets(@RequestParam String fieldType) {
        List<ConsultationNotePresetDTO> dtos = presetService.listPresets(fieldType).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> createPreset(@RequestBody ConsultationNotePresetDTO dto) {
        try {
            ConsultationNotePreset saved = presetService.createPreset(dto.getFieldType(), dto.getText());
            return ResponseEntity.ok(toDto(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> updatePreset(@PathVariable Long id, @RequestBody ConsultationNotePresetDTO dto) {
        try {
            ConsultationNotePreset saved = presetService.updatePreset(id, dto.getText(), dto.getDisplayOrder());
            return ResponseEntity.ok(toDto(saved));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<?> deletePreset(@PathVariable Long id) {
        try {
            presetService.deletePreset(id);
            return ResponseEntity.ok("Preset deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
