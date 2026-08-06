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

    // Synonyms are stored already normalised (lowercase, punctuation replaced by a single space,
    // whitespace collapsed) because ColumnMapper.normalise() applies the same transform to headers
    // before comparing — e.g. "e-mail" is written "e mail" so it still matches "E-Mail" once the
    // header's hyphen has become a space, and "d.o.b" is written "d o b" for the same reason.
    private static final List<ImportFieldDef> PATIENT_FIELDS = List.of(
            new ImportFieldDef("name", "Full name", true,
                    List.of("name", "patient name", "full name", "patientname", "patient")),
            new ImportFieldDef("legacyId", "Old patient ID / MRN", false,
                    List.of("mrn", "patient id", "old id", "card no", "old card no", "registration no", "reg no",
                            "uhid", "file no", "opd no", "ipd no", "patient no", "old patient id")),
            new ImportFieldDef("gender", "Gender", false,
                    List.of("gender", "sex")),
            new ImportFieldDef("phone", "Phone", false,
                    List.of("phone", "mobile", "mob no", "mobile no", "contact", "contact number",
                            "phone no", "ph no", "mob", "cell", "cell no", "contact no", "mobile number")),
            new ImportFieldDef("email", "Email", false,
                    List.of("email", "email id", "e mail", "mail", "mail id")),
            new ImportFieldDef("dateOfBirth", "Date of birth", false,
                    List.of("dob", "date of birth", "birth date", "birthdate", "d o b", "dob date")),
            new ImportFieldDef("address", "Address", false,
                    List.of("address", "residence", "addr", "add", "city")),
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
