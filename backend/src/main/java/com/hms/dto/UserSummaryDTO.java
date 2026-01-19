package com.hms.dto;

import com.hms.entity.User;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserSummaryDTO {
    private String id; // Public ID
    private String name;
    private String email;
    private String role;
    private String hospitalName;
    private Boolean isActive;
    // Format createdAt on frontend? or send string? Sending LocalDateTime is fine
    // for JSON.
    private LocalDateTime createdAt;

    public UserSummaryDTO(User user, String hospitalName) {
        this.id = user.getPublicId();
        // Fallback to Database ID if Public ID is missing
        if (this.id == null || this.id.trim().isEmpty() || "null".equals(this.id)) {
            this.id = String.valueOf(user.getId());
        }
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.hospitalName = hospitalName != null ? hospitalName : "Platform";
        this.isActive = user.getIsActive();
        this.createdAt = user.getCreatedAt();
    }
}
