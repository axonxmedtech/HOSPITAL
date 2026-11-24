package com.hms.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupValidateResponse {
    private boolean valid;
    private int tableCount;
    private boolean coreTablesPresent;
    private Long patientCount;
    private String message;
}
