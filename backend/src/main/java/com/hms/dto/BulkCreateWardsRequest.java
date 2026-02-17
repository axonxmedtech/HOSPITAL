package com.hms.dto;

import lombok.Data;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@Data
public class BulkCreateWardsRequest {
    @NotEmpty(message = "wards list cannot be empty")
    @Valid
    private List<CreateWardRequest> wards;
}
