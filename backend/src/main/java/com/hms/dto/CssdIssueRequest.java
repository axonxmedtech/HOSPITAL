package com.hms.dto;

import lombok.Data;

@Data
public class CssdIssueRequest {
    private String trayBarcode;
    private String issuedToDepartment;
    private Long receivedBy;
}
