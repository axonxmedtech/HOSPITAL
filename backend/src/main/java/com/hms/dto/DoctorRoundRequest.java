package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorRoundRequest {
    private String subjective;
    private String objective;
    private String assessment;
    private String plan;
    private LocalDateTime nextRoundAt;
}
