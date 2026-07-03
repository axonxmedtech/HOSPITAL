package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionPresetDTO {
    private Long id;
    private String name;
    private List<PrescriptionPresetItemDTO> items;
    private Integer displayOrder;
}
