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
}
