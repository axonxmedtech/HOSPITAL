package com.hms.dto;

import lombok.Data;

@Data
public class CssdReturnRequest {
    private String trayBarcode;
    private String fromDepartment;
    private String condition; // DIRTY / DAMAGED / MISSING
}
