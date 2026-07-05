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
    // doctorId: NULL = shared (all doctors); set = private to that doctor.
    // doctorName is populated on read for the admin view; null when shared.
    private Long doctorId;
    private String doctorName;
}
