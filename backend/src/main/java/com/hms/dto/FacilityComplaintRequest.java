package com.hms.dto;

import lombok.Data;

@Data
public class FacilityComplaintRequest {
    private String location;
    private String complaintType; // LEAKAGE / LIGHTING / ELECTRICAL / AC / PLUMBING
}
