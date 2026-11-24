package com.hms.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupMetadataDto {
    private String fileName;
    private LocalDateTime createdAt;
    private long sizeBytes;
    private String sha256;
    private boolean verified;
    private LocalDateTime lastDownloadedAt;
    private String lastDownloadedBy;
}
