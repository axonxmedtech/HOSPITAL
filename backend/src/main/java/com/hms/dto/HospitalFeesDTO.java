package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalFeesDTO {
    private BigDecimal consultationFee;
    private BigDecimal casePaperFee;
}
