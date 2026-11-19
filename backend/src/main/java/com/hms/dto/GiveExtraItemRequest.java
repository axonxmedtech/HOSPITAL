package com.hms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Something given that was not ordered: an extra saline bottle, a dressing, a stat tablet. */
@Data
public class GiveExtraItemRequest {
    @NotNull
    @Positive
    private Long ipdAdmissionId;

    /** MEDICINE, HOSPITAL_ITEM, or blank when the patient brought it. */
    private String source;

    /** For MEDICINE. */
    @Positive
    private Long medicineId;

    /** Item or medicine name (the hospital-inventory item name for HOSPITAL_ITEM). */
    @NotBlank
    @Size(max = 150)
    private String name;

    @NotNull
    @Positive
    @Max(1000)
    private Integer quantity;

    private Boolean ownSupply;

    @Size(max = 500)
    private String remarks;

    @Positive
    private Long performedByNurseId;
}
