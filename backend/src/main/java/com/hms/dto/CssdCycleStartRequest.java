package com.hms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CssdCycleStartRequest {
    private String machineId;
    private String method; // STEAM / ETO / PLASMA / DRY_HEAT
    private BigDecimal temperature;
    private BigDecimal pressure;
    private Integer duration;
    private List<Long> trayIds;
}
