package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * NursingProcedureRequest - DTO payload to log non-medication nursing procedures.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class NursingProcedureRequest {

    @NotNull(message = "Progress note ID is mandatory")
    private Long progressNoteId;

    @NotBlank(message = "Procedure name is mandatory")
    private String procedureName;

    private String remarks;
}
