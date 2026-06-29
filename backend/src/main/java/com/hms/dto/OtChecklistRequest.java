package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtChecklistRequest {
    private String phase; // SIGN_IN, TIME_OUT, SIGN_OUT
    private String notes;
}
