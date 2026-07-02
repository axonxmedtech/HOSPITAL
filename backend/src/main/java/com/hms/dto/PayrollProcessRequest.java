package com.hms.dto;

import lombok.Data;

import java.util.List;

@Data
public class PayrollProcessRequest {
    private String salaryMonth; // e.g. 2026-06
    private List<PayrollLineItem> entries;

    @Data
    public static class PayrollLineItem {
        private Long employeeId;
        private java.math.BigDecimal grossSalary;
        private java.math.BigDecimal deductions;
    }
}
