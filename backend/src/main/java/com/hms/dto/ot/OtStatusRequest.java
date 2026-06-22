package com.hms.dto.ot;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtStatusRequest {
    @NotBlank
    private String status;
    private String notes;
}
