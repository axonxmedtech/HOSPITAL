package com.hms.dto.backup;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackupRestoreRequest {
    @NotBlank(message = "Backup filename is required")
    private String fileName;

    @NotBlank(message = "Backup secret key is required")
    private String backupKey;

    @NotBlank(message = "Confirmation is required")
    private String confirmation;
}
