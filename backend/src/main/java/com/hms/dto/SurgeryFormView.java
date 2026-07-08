package com.hms.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** A saved surgery form returned to the frontend (data parsed back to a map). */
@Data
public class SurgeryFormView {
    private String formType;
    private Map<String, Object> data;
    private LocalDateTime savedAt;
}
