package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for adding / updating an implant record (Form 24).
 * All business fields are editable while status = DRAFT.
 */
@Data
public class ImplantRecordRequest {

    private Long inventoryItemId;
    private String implantName;
    private String manufacturer;
    private String modelNumber;
    private String catalogNumber;
    private String batchNumber;
    private String lotNumber;
    private String serialNumber;
    private String udi;
    private LocalDate expiryDate;
    private Integer quantityOpened;
    private Integer quantityImplanted;
    private Integer quantityReturned;
    private Integer quantityWasted;
    private String implantLocation;
    private String warrantyCardNumber;
    private Boolean patientCardIssued;
    private String nurseSig;

    /** Optional: surgeon sign-off payload (used on sign endpoint). */
    private String surgeonSig;
}
