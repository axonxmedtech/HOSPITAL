package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HospitalSettingDTO - Data Transfer Object for hospital operational settings
 * 
 * Used to exchange settings (receptionMode, billingHandler) between backend and client.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalSettingDTO {
    private String receptionMode;
    private String billingHandler;
}
