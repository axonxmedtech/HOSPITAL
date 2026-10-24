package com.hms.service.import_;

import com.hms.dto.import_.ImportFieldDef;
import com.hms.entity.import_.ImportEntityType;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The writable target fields of a patient import — the only keys a header may be mapped to. In
 * the spirit of FormRegistry and VitalRegistry: a closed list, with the reasoning next to it.
 *
 * <p>Deliberately absent, and refused by {@link #isWritable}: {@code hospital_id} (tenancy comes
 * from the authenticated context; a file's hospital_id column is ordinary data and is ignored),
 * {@code publicId}, {@code customId}, {@code id}, {@code isActive}, {@code status} and the
 * {@code duplicate_phone_ack_*} fields (server-controlled — see {@code Patient}). A mapping
 * naming any of them is rejected at the boundary rather than quietly dropped.
 *
 * <p>{@code legacyId} is the source system's own patient number (MRN); it is written to the
 * import link, never to the patient row, and is the deterministic re-import key.
 *
 * <p>Synonyms are stored normalised — lowercase, punctuation replaced by a space, whitespace
 * collapsed — because {@link ColumnMapper} normalises headers the same way before comparing, so
 * "Mob.No" and "mob no" and "MOB NO" all match. Only spellings that mean one thing are listed:
 * "contact" could be a phone or an email, "notes" could be anything, "city" is not an address,
 * and "OPD No" is a visit number, not a patient number — those stay unmapped for a human to place.
 */
@Component
public class ImportFieldRegistry {

    private static final List<ImportFieldDef> PATIENT_FIELDS = List.of(
            new ImportFieldDef(
                    "name",
                    "Full name",
                    true,
                    List.of("name", "patient name", "full name", "patientname", "name of patient")),
            new ImportFieldDef(
                    "legacyId",
                    "Old patient ID / MRN",
                    false,
                    List.of(
                            "mrn",
                            "patient id",
                            "patient no",
                            "patient number",
                            "old patient id",
                            "old id",
                            "uhid",
                            "registration no",
                            "reg no",
                            "registration number",
                            "card no",
                            "old card no")),
            new ImportFieldDef("gender", "Gender", false, List.of("gender", "sex")),
            new ImportFieldDef(
                    "phone",
                    "Phone",
                    false,
                    List.of(
                            "phone",
                            "phone no",
                            "phone number",
                            "ph no",
                            "mobile",
                            "mobile no",
                            "mobile number",
                            "mob",
                            "mob no",
                            "cell",
                            "cell no",
                            "contact no",
                            "contact number")),
            new ImportFieldDef("email", "Email", false, List.of("email", "email id", "e mail", "email address")),
            new ImportFieldDef(
                    "dateOfBirth",
                    "Date of birth",
                    false,
                    List.of("dob", "date of birth", "birth date", "birthdate", "d o b")),
            new ImportFieldDef("address", "Address", false, List.of("address", "addr", "residential address")),
            new ImportFieldDef(
                    "medicalHistory", "Medical history", false, List.of("medical history", "past medical history")));

    /** Field keys a mapping must never name. Compared case-insensitively against what a client sends. */
    private static final Set<String> FORBIDDEN = Set.of(
            "id",
            "hospitalid",
            "hospital_id",
            "publicid",
            "public_id",
            "customid",
            "custom_id",
            "isactive",
            "is_active",
            "status",
            "duplicatephoneackfor",
            "duplicate_phone_ack_for",
            "duplicatephoneackat",
            "duplicate_phone_ack_at",
            "duplicatephoneackby",
            "duplicate_phone_ack_by",
            "importbatchid",
            "import_batch_id");

    public List<ImportFieldDef> fieldsFor(ImportEntityType type) {
        return type == ImportEntityType.PATIENT ? PATIENT_FIELDS : List.of();
    }

    /** True only for a key in the registry for that entity. Anything else — including every {@link #FORBIDDEN} name — is not. */
    public boolean isWritable(ImportEntityType type, String fieldKey) {
        if (fieldKey == null) return false;
        if (FORBIDDEN.contains(fieldKey.toLowerCase(Locale.ROOT))) return false;
        return fieldsFor(type).stream().anyMatch(f -> f.key().equals(fieldKey));
    }
}
