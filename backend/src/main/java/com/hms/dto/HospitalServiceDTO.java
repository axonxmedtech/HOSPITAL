package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalServiceDTO {
    private Long id;
    private String name;
    private BigDecimal charge;
    // Relevant items expressed as master item ids (for save) and, on read,
    // enriched names are provided via itemNames.
    private List<Long> masterItemIds;
    private List<String> itemNames;
}
