package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationStatementDTO {
    private Long id;

    private String hospitalType;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "English text is required")
    private String englishText;

    @NotBlank(message = "Marathi text is required")
    private String marathiText;

    @NotBlank(message = "Hindi text is required")
    private String hindiText;

    private Integer displayOrder = 0;
    private Boolean isActive = true;
}
