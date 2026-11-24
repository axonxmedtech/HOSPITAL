package com.hms.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupListResponse {
    private List<BackupMetadataDto> backups;
    private Long daysSinceLastDownload;
    private boolean hasOverdueDownload;
}
