package com.hms.service.import_;

import java.util.Map;

/**
 * What a row would create. Plain values, no entity: the writer phase builds the {@code Patient}
 * from this, runs it through {@code PatientRegistrar}, and records the link. Nothing here has
 * touched the database.
 *
 * @param values       the demographic fields to write (phone canonical, gender normalised)
 * @param legacyId     the source system's MRN for the link, or null when the file had none
 * @param customFields unmapped columns and preserved originals ("Phone (as imported)",
 *                     "Email (not a valid address)", "Address (full value)"), verbatim
 */
public record CreateCandidate(PatientFieldValues values, String legacyId, Map<String, String> customFields) {
    public CreateCandidate {
        customFields = Map.copyOf(customFields);
    }
}
