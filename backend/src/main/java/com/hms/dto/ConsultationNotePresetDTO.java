package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationNotePresetDTO {
    private Long id;
    private String fieldType;
    private String text;
    private Integer displayOrder;
    // doctorId: NULL = shared (all doctors); set = private to that doctor.
    // doctorName is populated on read for the admin view; null when shared.
    private Long doctorId;
    private String doctorName;
}
