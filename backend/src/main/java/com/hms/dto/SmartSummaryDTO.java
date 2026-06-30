package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmartSummaryDTO {
    private List<String> allergies;
    private List<String> activeMedicines;
    private List<String> pendingLabTests;
    private List<String> pendingRadiology;
    private EwsResultDTO ews;
    private List<CdssAlertDTO> unacknowledgedAlerts;
}
