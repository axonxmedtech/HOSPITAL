package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Biomedical waste collection log (Form 37 core, BR-3). */
@Entity
@Table(name = "waste_collection")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WasteCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    // YELLOW / RED / BLUE / WHITE / GENERAL
    @Column(name = "waste_type", nullable = false, length = 20)
    private String wasteType;

    @Column(name = "quantity", nullable = false, precision = 5, scale = 2)
    private BigDecimal quantity;

    @Column(name = "barcode_tag", nullable = false, length = 50)
    private String barcodeTag;

    @Column(name = "collector_name", nullable = false, length = 100)
    private String collectorName;

    @Column(name = "vendor", nullable = false, length = 100)
    private String vendor;

    @Column(name = "manifest_number", length = 50)
    private String manifestNumber;

    @Column(name = "collection_time", nullable = false)
    private LocalDateTime collectionTime;
}
