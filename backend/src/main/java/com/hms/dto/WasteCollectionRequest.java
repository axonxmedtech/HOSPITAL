package com.hms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WasteCollectionRequest {
    private String wasteType; // YELLOW / RED / BLUE / WHITE / GENERAL
    private BigDecimal quantity;
    private String barcodeTag;
    private String vendor;
    private String manifestNumber;
}
