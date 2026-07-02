package com.hms.dto;

import lombok.Data;

@Data
public class TransfusionCompletionRequest {
    private String reaction; // NONE / FEBRILE / ALLERGIC / HEMOLYTIC
    private String reactionNotes;
}
