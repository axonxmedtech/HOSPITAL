package com.hms.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank(message = "Category name is required")
    private String categoryName;
    private String description;
    private Boolean isActive;
}
