package com.hms.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** Form 02 — computed IPD-file completeness checklist (each item PASS or FAIL). */
@Data
public class MrdCompletenessDTO {
    private Long ipdAdmissionId;
    private Map<String, String> items = new LinkedHashMap<>();
    private boolean complete;
}
