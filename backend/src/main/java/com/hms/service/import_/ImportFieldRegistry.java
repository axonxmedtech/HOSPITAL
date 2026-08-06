package com.hms.service.import_;

import com.hms.dto.import_.ImportFieldDef;
import com.hms.entity.ImportEntityType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Canonical list of importable fields per entity, in the same spirit as FormRegistry and
 * VitalRegistry. Only PATIENT is populated in Phase 1A; later phases add their own lists.
 */
@Component
public class ImportFieldRegistry {

    private static final List<ImportFieldDef> PATIENT_FIELDS = List.of(
            new ImportFieldDef("name", "Full name", true,
                    List.of("name", "patient name", "full name", "patientname")),
            new ImportFieldDef("legacyId", "Old patient ID / MRN", false,
                    List.of("mrn", "patient id", "old id", "card no", "old card no", "registration no", "reg no")),
            new ImportFieldDef("gender", "Gender", false,
                    List.of("gender", "sex")),
            new ImportFieldDef("phone", "Phone", false,
                    List.of("phone", "mobile", "mob no", "mobile no", "contact", "contact number")),
            new ImportFieldDef("email", "Email", false,
                    List.of("email", "email id", "e-mail")),
            new ImportFieldDef("dateOfBirth", "Date of birth", false,
                    List.of("dob", "date of birth", "birth date", "birthdate")),
            new ImportFieldDef("address", "Address", false,
                    List.of("address", "residence", "addr")),
            new ImportFieldDef("medicalHistory", "Medical history", false,
                    List.of("medical history", "history", "notes", "remarks"))
    );

    public List<ImportFieldDef> fieldsFor(ImportEntityType type) {
        if (type == ImportEntityType.PATIENT) {
            return PATIENT_FIELDS;
        }
        return List.of();
    }
}
