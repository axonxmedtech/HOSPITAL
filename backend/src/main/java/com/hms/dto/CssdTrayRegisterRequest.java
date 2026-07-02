package com.hms.dto;

import lombok.Data;

@Data
public class CssdTrayRegisterRequest {
    private String trayName;
    private String specialty;
    private String barcode;
}
