package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalDashboardResponse {
    private long upcomingAppointments;
    private long releasedReports;
    private long activePrescriptions;
    private BigDecimal outstandingBalance;
}
