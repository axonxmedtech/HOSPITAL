package com.hms.service.import_;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Patient;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The seven demographic fields an import may write, as plain values. Used three ways: the values
 * a row proposes, the values a patient currently holds, and the snapshot of what the importer
 * wrote last time ({@code patient_import_links.last_imported_values_json}). Comparing the last two
 * is the whole edit-protection mechanism, so the representation is deliberately dumb and
 * deterministic: strings and an ISO date, null for absent, and a JSON form with fixed keys.
 *
 * <p>Detached from JPA on purpose — {@link #of(Patient)} copies out of the entity and nothing
 * here can write back into it.
 */
public record PatientFieldValues(
        String name,
        String phone,
        String gender,
        LocalDate dateOfBirth,
        String email,
        String address,
        String medicalHistory) {

    public static final List<String> FIELDS =
            List.of("name", "phone", "gender", "dateOfBirth", "email", "address", "medicalHistory");

    private static final ObjectMapper JSON = new ObjectMapper();

    public static PatientFieldValues of(Patient p) {
        return new PatientFieldValues(
                p.getName(), p.getPhone(), p.getGender(), p.getDateOfBirth(), p.getEmail(), p.getAddress(), p.getMedicalHistory());
    }

    /** Value by field key; a date as ISO text; null and blank both come back as null. */
    public String get(String field) {
        String v = switch (field) {
            case "name" -> name;
            case "phone" -> phone;
            case "gender" -> gender;
            case "dateOfBirth" -> dateOfBirth == null ? null : dateOfBirth.toString();
            case "email" -> email;
            case "address" -> address;
            case "medicalHistory" -> medicalHistory;
            default -> throw new IllegalArgumentException("Not an import field: " + field);
        };
        return v == null || v.isBlank() ? null : v;
    }

    public static boolean same(String a, String b) {
        return Objects.equals(blankToNull(a), blankToNull(b));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String f : FIELDS) m.put(f, get(f));
        return m;
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(toMap());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Field values are plain strings; this cannot fail", e);
        }
    }

    /** Null for a null/blank/unreadable snapshot: an unreadable snapshot is treated as "no snapshot", never as "all owned". */
    public static PatientFieldValues fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, String> m = JSON.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
            String dob = m.get("dateOfBirth");
            return new PatientFieldValues(
                    m.get("name"),
                    m.get("phone"),
                    m.get("gender"),
                    dob == null || dob.isBlank() ? null : LocalDate.parse(dob),
                    m.get("email"),
                    m.get("address"),
                    m.get("medicalHistory"));
        } catch (Exception e) {
            return null;
        }
    }
}
