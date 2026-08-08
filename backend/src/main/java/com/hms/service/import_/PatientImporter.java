package com.hms.service.import_;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.import_.RowOutcome;
import com.hms.entity.ImportEntityType;
import com.hms.entity.ImportSource;
import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

@Component
public class PatientImporter implements EntityImporter {

    /**
     * Formats seen in real legacy exports, tried in order.
     *
     * <p><b>Day-first is deliberate.</b> "03/04/1977" is read as 3 April, not 4 March, because
     * day-first is the convention in this market. The importer cannot know what the source system
     * meant, so this is a documented assumption rather than a resolved ambiguity; the dry-run
     * surfaces it as a warning before anything is committed.
     *
     * <p><b>STRICT, and {@code uuuu} not {@code yyyy}, both matter.</b> The default SMART resolver
     * silently clamps an out-of-range day to the end of the month — "31-02-2020" becomes
     * 2020-02-29 with no exception — which would write a wrong date of birth into a patient record
     * with nothing to notice. STRICT rejects it instead. STRICT also cannot resolve {@code yyyy},
     * which is year-of-era and would demand an era field the input never carries, so the patterns
     * use the proleptic-year symbol {@code uuuu}; with {@code yyyy} every date would fail to parse.
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)
    );

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PatientRepository patientRepository;

    public PatientImporter(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.PATIENT;
    }

    @Override
    public RowOutcome evaluate(Map<String, String> row, Map<String, String> mapping,
                               List<String> unmappedHeaders, Long hospitalId, int rowNumber) {

        Map<String, String> byField = new HashMap<>();
        String nameHeader = null;
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            String value = row.getOrDefault(e.getKey(), "");
            byField.put(e.getValue(), value);
            if ("name".equals(e.getValue())) nameHeader = e.getKey();
        }

        String name = blankToNull(byField.get("name"));
        if (name == null) {
            return RowOutcome.error(nameHeader, "Full name is required and was blank");
        }

        String legacyId = blankToNull(byField.get("legacyId"));

        Patient patient = null;
        boolean isUpdate = false;

        if (legacyId != null) {
            // Deliberately not filtered on isActive. Undo soft-deletes rows but leaves legacy_id in
            // place, so they still occupy the unique (hospital_id, legacy_id) index. Matching them
            // lets a corrected re-import reactivate the row instead of failing on the constraint —
            // and "undo, fix the file, re-import" is the main recovery path.
            Optional<Patient> existing = patientRepository.findByHospitalIdAndLegacyId(hospitalId, legacyId);
            if (existing.isPresent()) {
                patient = existing.get();
                isUpdate = true;
            }
        } else {
            // No MRN column in the file. name+phone is the only fallback available, and name alone
            // is not an identity: matching on it would merge unrelated people who happen to share a
            // name, which is far worse than leaving a duplicate behind.
            String phoneValue = blankToNull(byField.get("phone"));
            if (phoneValue != null) {
                List<Patient> candidates =
                        patientRepository.findByHospitalIdAndNameAndPhone(hospitalId, name, phoneValue);
                if (candidates.size() > 1) {
                    return RowOutcome.skip("Matches more than one existing patient with the same name "
                            + "and phone; skipped rather than merged. Resolve this one manually.");
                }
                if (candidates.size() == 1) {
                    patient = candidates.get(0);
                    isUpdate = true;
                }
            }
        }

        // A record a human has corrected outranks the legacy file. Re-running an import to pick up
        // failed rows used to silently revert every fix reception had made since, with nothing to
        // show it had happened. Skipped and reported rather than quietly ignored, so the admin can
        // see which rows the file could not apply and reconcile them deliberately.
        if (isUpdate && patient != null && Boolean.TRUE.equals(patient.getManuallyEdited())) {
            return RowOutcome.skip("This patient has been edited in the system since the last import"
                    + (patient.getCustomId() != null ? " (" + patient.getCustomId() + ")" : "")
                    + ", so the file was not applied to them. Staff changes take precedence — update "
                    + "the record directly if the file is more accurate.");
        }

        if (patient == null) {
            patient = new Patient();
        }

        // Values that will not fit the column, or that the column cannot represent, are coerced
        // here and the untouched original is kept in customFields. Letting them through instead
        // meant a Bean Validation or data-truncation error thrown from inside saveAll - outside
        // the per-row handler - which aborts the whole import over one bad cell.
        Map<String, String> coerced = new LinkedHashMap<>();

        patient.setHospitalId(hospitalId);
        patient.setName(clamp(name, 100, "Name", coerced));
        patient.setSource(ImportSource.IMPORTED);
        patient.setGender(clamp(blankToNull(byField.get("gender")), 10, "Gender", coerced));
        patient.setPhone(clamp(blankToNull(byField.get("phone")), 15, "Phone", coerced));
        patient.setAddress(clamp(blankToNull(byField.get("address")), 255, "Address", coerced));
        patient.setMedicalHistory(
                clamp(blankToNull(byField.get("medicalHistory")), 1000, "Medical history", coerced));

        // legacyId is the dedupe and join key. Only assign it when this file actually supplied one:
        // on the name+phone fallback branch it is always null, and writing that would erase the
        // legacy_id an earlier MRN import established, destroying the key future runs match on.
        if (legacyId != null) {
            patient.setLegacyId(clamp(legacyId, 100, "Old patient ID", coerced));
        }

        String email = blankToNull(byField.get("email"));
        if (email != null && !PLAUSIBLE_EMAIL.matcher(email).matches()) {
            // "n/a", "-", "none" are everywhere in legacy exports. Keep the value, just not in a
            // column that is meant to hold a reachable address.
            coerced.put("Email (not a valid address)", email);
            email = null;
        }
        patient.setEmail(clamp(email, 100, "Email", coerced));

        String dob = blankToNull(byField.get("dateOfBirth"));
        if (dob != null) {
            LocalDate parsed = parseDate(dob);
            if (parsed == null) {
                String header = headerFor(mapping, "dateOfBirth");
                return RowOutcome.error(header, "Could not read \"" + dob + "\" as a date");
            }
            patient.setDateOfBirth(parsed);
        }

        try {
            String merged = mergeCustomFields(patient.getCustomFields(), row, unmappedHeaders, coerced);
            // Only write when this file actually carried unmapped columns. Assigning unconditionally
            // meant re-importing a file without the extra columns - the corrected errors.csv being
            // the obvious case - set customFields to null and erased what an earlier import had
            // preserved. Silent data loss in the one place the feature promises none.
            if (merged != null) {
                patient.setCustomFields(merged);
            }
        } catch (Exception e) {
            return RowOutcome.error(null,
                    "Could not preserve the unmapped columns for this row: " + e.getMessage());
        }

        return isUpdate ? RowOutcome.update(patient) : RowOutcome.create(patient);
    }

    private String headerFor(Map<String, String> mapping, String fieldKey) {
        return mapping.entrySet().stream()
                .filter(e -> e.getValue().equals(fieldKey))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, f);
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return null;
    }


    /**
     * Merges this file's unmapped columns over whatever an earlier import preserved, rather than
     * replacing it. A second import that happens not to carry a column must not delete the values
     * a first one captured — successive partial files should accumulate, not overwrite.
     *
     * @return null when this file has no unmapped columns, meaning the caller should leave the
     *         existing value untouched.
     */
    private String mergeCustomFields(String existingJson, Map<String, String> row,
                                     List<String> unmappedHeaders, Map<String, String> coerced)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        boolean nothingToAdd = (unmappedHeaders == null || unmappedHeaders.isEmpty())
                && (coerced == null || coerced.isEmpty());
        if (nothingToAdd) return null;

        Map<String, String> merged = new LinkedHashMap<>();
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                merged.putAll(JSON.readValue(existingJson,
                        new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, String>>() { }));
            } catch (Exception ignored) {
                // Unreadable prior value: keep going rather than fail the row. Losing an
                // unparseable blob is better than refusing to import the patient at all.
            }
        }
        if (unmappedHeaders != null) {
            for (String header : unmappedHeaders) {
                merged.put(header, row.getOrDefault(header, ""));
            }
        }
        if (coerced != null) {
            merged.putAll(coerced);
        }
        return JSON.writeValueAsString(merged);
    }

    /**
     * Deliberately permissive — this only decides whether a value belongs in the email column, not
     * whether it is deliverable. Anything rejected is preserved verbatim in customFields.
     */
    private static final java.util.regex.Pattern PLAUSIBLE_EMAIL =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * Truncates a value to what its column can hold, recording the untouched original under
     * {@code label} so the full value survives in customFields.
     *
     * <p>Truncating quietly would be data loss; refusing the row would abort an import over a long
     * address. Keeping both the usable value and the original is the only option that loses nothing.
     */
    private String clamp(String value, int max, String label, Map<String, String> coerced) {
        if (value == null) return null;
        if (value.length() <= max) return value;
        coerced.put(label + " (full value)", value);
        return value.substring(0, max);
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
