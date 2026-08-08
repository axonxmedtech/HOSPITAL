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

        if (patient == null) {
            patient = new Patient();
        }

        patient.setHospitalId(hospitalId);
        patient.setName(name);
        patient.setLegacyId(legacyId);
        patient.setSource(ImportSource.IMPORTED);
        patient.setGender(blankToNull(byField.get("gender")));
        patient.setPhone(blankToNull(byField.get("phone")));
        patient.setEmail(blankToNull(byField.get("email")));
        patient.setAddress(blankToNull(byField.get("address")));
        patient.setMedicalHistory(blankToNull(byField.get("medicalHistory")));

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
            patient.setCustomFields(buildCustomFields(row, unmappedHeaders));
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
     * Unmapped columns are kept verbatim, blanks included, so nothing from the file is lost.
     * Serialised with Jackson rather than hand-rolled: escaping by iterating {@code char} walks
     * UTF-16 code units, so an unpaired surrogate from a mis-encoded export would emit invalid
     * JSON. A LinkedHashMap keeps the column order stable and the output diffable.
     */
    private String buildCustomFields(Map<String, String> row, List<String> unmappedHeaders)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        if (unmappedHeaders == null || unmappedHeaders.isEmpty()) return null;
        Map<String, String> preserved = new LinkedHashMap<>();
        for (String header : unmappedHeaders) {
            preserved.put(header, row.getOrDefault(header, ""));
        }
        return JSON.writeValueAsString(preserved);
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
