package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveDashboardResponse {
    private String timeframe;
    private long totalBeds;
    private long occupiedBeds;
    private BigDecimal bedOccupancyRate;
    private BigDecimal totalRevenue;
    private BigDecimal outstandingAr;
    private long stockExpiryAlerts;
    private long activeAlerts;
    private long criticalAlerts;
}
