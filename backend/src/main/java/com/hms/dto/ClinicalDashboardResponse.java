package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalDashboardResponse {
    private long totalBeds;
    private long occupiedBeds;
    private BigDecimal bedOccupancyRate;
    private long otBookingsScheduled;
    private long otBookingsCompleted;
    private long otBookingsCancelled;
    private long activeClinicalAlerts;
}
