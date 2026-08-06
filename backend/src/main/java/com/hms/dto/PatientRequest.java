package com.hms.dto;

import com.hms.validation.NoEmoji;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Create/update payload for manually entered patients. The strict field rules live here rather than
 * on the Patient entity so that the importer can persist legacy records with blank or oddly
 * formatted values without Hibernate rejecting them (see the legacy import design, section 4.1).
 * Field names match the previous entity binding exactly, so the frontend contract is unchanged.
 *
 * Every rule below — including the @NoEmoji text guards — is a verbatim copy of what the entity
 * enforced when the controller bound Patient directly. Manual registration must stay exactly as
 * strict as it was; only the importer, which never passes through this DTO, gets the freedom.
 */
@Data
public class PatientRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name is too long")
    @NoEmoji
    private String name;

    @NotBlank(message = "Gender is required")
    @Pattern(regexp = "^[A-Za-z \\-]{1,10}$", message = "Invalid gender")
    private String gender;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email is too long")
    private String email;

    @Size(max = 255, message = "Address is too long")
    @NoEmoji
    private String address;

    @Size(max = 1000, message = "Medical history is too long")
    @NoEmoji
    private String medicalHistory;

    private LocalDate dateOfBirth;
}
