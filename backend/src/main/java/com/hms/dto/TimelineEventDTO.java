package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Form 31 — one event on the longitudinal patient EMR timeline. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventDTO {
    private String type;        // ADMISSION, DISCHARGE, EMERGENCY, ASSESSMENT, SURGERY
    private LocalDateTime eventTime;
    private String title;
    private String detail;
    private Long refId;
}
