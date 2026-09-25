package com.hms.service.import_;

import com.hms.dto.DuplicatePatientMatch;
import com.hms.entity.Patient;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportStatus;
import com.hms.entity.import_.PatientImportLink;
import com.hms.repository.PatientRepository;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.repository.import_.PatientImportLinkRepository;
import com.hms.service.hospital.PatientDuplicateFinder;
import com.hms.validation.NoEmojiValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates one parsed row into a {@link RowEvaluation}: what the import WOULD do, and why not
 * when it would not. Reads patients and import links (always through the caller's hospital),
 * writes nothing, and never mutates a managed entity — a preview and a commit run the same
 * evaluation, and the commit's writer applies the returned candidate afterwards.
 *
 * <p>Order of decisions, fixed:
 * <ol>
 *   <li>parser problems (a dropped over-long cell, a stray value) → FAILED, no candidate</li>
 *   <li>name: required, entity rules (≤100, no emoji) → FAILED</li>
 *   <li>phone / gender / date-of-birth normalisation → NEEDS_REVIEW or FAILED (the strict rules
 *       of the current {@code Patient} are not weakened: no blank phone, no placeholder, no
 *       guessed gender, no clamped date)</li>
 *   <li>in-file duplicate MRN → SKIPPED; in-file duplicate phone → NEEDS_REVIEW</li>
 *   <li>matching (MRN link, else exact name + canonical phone) → create / update target, or
 *       NEEDS_REVIEW where the match is not safe</li>
 *   <li>database duplicate phone via {@code PatientDuplicateFinder}, excluding the matched patient
 *       → NEEDS_REVIEW; the importer never acknowledges anything</li>
 *   <li>for an update: field ownership against the last-imported snapshot → NEEDS_REVIEW if any
 *       human-changed field would change, else only the proposed changes</li>
 * </ol>
 *
 * <p>The mapping is display header → field key, already confirmed by the administrator and
 * already restricted by {@link ImportFieldRegistry#isWritable}. A {@code hospital_id} column in
 * the file is just another unmapped column; tenancy is the {@link ImportEvaluationContext}'s.
 */
@Component
public class PatientImporter {

    // Business limits are the entity's own (Patient.java); repeated here only to decide the
    // preservation strategy BEFORE the entity would reject the value.
    static final int NAME_MAX = 100;
    static final int EMAIL_MAX = 100;
    static final int ADDRESS_MAX = 255;
    static final int MEDICAL_HISTORY_MAX = 1000;
    static final int LEGACY_ID_MAX = 100;

    static final String PHONE_AS_IMPORTED = "Phone (as imported)";
    static final String EMAIL_NOT_VALID = "Email (not a valid address)";
    static final String ADDRESS_FULL = "Address (full value)";
    static final String MEDICAL_HISTORY_FULL = "Medical history (full value)";

    /** The email rule the entity applies (@Email is permissive; this is at least as strict, never stricter than useful). */
    private static final java.util.regex.Pattern PLAUSIBLE_EMAIL =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final PatientImportLinkRepository links;
    private final PatientRepository patients;
    private final ImportBatchRepository batches;
    private final PatientDuplicateFinder duplicateFinder;
    private final NoEmojiValidator noEmoji = new NoEmojiValidator();

    public PatientImporter(
            PatientImportLinkRepository links,
            PatientRepository patients,
            ImportBatchRepository batches,
            PatientDuplicateFinder duplicateFinder) {
        this.links = links;
        this.patients = patients;
        this.batches = batches;
        this.duplicateFinder = duplicateFinder;
    }

    /**
     * @param row     the parsed row
     * @param header  its header
     * @param mapping display header → field key, confirmed by the administrator
     * @param ctx     the run's context; carries the authenticated hospital
     */
    @Transactional(readOnly = true)
    public RowEvaluation evaluate(ParsedRow row, SheetHeader header, Map<String, String> mapping, ImportEvaluationContext ctx) {
        int rowNum = row.rowNum();
        Long hospitalId = ctx.hospitalId();

        // 1. Parser problems come first. A CELL_TOO_LONG value was replaced by "" — that is not a
        //    blank the hospital chose, and it must never be written as one.
        if (row.hasProblems()) {
            RowProblem p = row.problems().get(0);
            return switch (p.code()) {
                case CELL_TOO_LONG -> RowEvaluation.failed(
                        rowNum,
                        ImportReasonCode.VALUE_TOO_LONG,
                        p.column(),
                        "The value in \"" + p.column() + "\" is longer than " + ParserLimits.MAX_CELL_CHARS
                                + " characters and was not read. Shorten it and re-import this row.");
                case UNEXPECTED_CELL -> RowEvaluation.failed(
                        rowNum,
                        ImportReasonCode.UNEXPECTED_COLUMN,
                        p.column(),
                        "The row has a value in column " + p.column() + ", beyond the header row. "
                                + "Give that column a header or remove the value.");
            };
        }

        Map<String, String> byField = new LinkedHashMap<>();
        Map<String, String> headerOf = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            byField.put(e.getValue(), row.get(header, e.getKey()));
            headerOf.put(e.getValue(), e.getKey());
        }
        Map<String, String> customFields = unmappedColumns(row, header, mapping);

        // 2. Name.
        String name = blankToNull(byField.get("name"));
        if (name == null) {
            return RowEvaluation.failed(rowNum, ImportReasonCode.NAME_MISSING, headerOf.get("name"), "Full name is required and was blank.");
        }
        if (name.length() > NAME_MAX) {
            return RowEvaluation.failed(
                    rowNum, ImportReasonCode.VALIDATION_FAILED, headerOf.get("name"), "Full name is longer than " + NAME_MAX + " characters.");
        }
        if (!noEmoji.isValid(name, null)) {
            return RowEvaluation.failed(
                    rowNum, ImportReasonCode.VALIDATION_FAILED, headerOf.get("name"), "Full name contains characters that are not allowed.");
        }

        // 3. Normalisation.
        String legacyId = blankToNull(byField.get("legacyId"));
        if (legacyId != null && legacyId.length() > LEGACY_ID_MAX) {
            return RowEvaluation.failed(
                    rowNum, ImportReasonCode.VALIDATION_FAILED, headerOf.get("legacyId"), "Old patient ID is longer than " + LEGACY_ID_MAX + " characters.");
        }
        LegacyPhoneNormalizer.Result phone = LegacyPhoneNormalizer.normalize(byField.get("phone"));
        if (phone.asImportedIfDifferent() != null) customFields.put(PHONE_AS_IMPORTED, phone.asImportedIfDifferent());

        String genderRaw = blankToNull(byField.get("gender"));
        Optional<String> gender = GenderNormalizer.normalize(genderRaw);

        LegacyDateParser.Result dob = LegacyDateParser.parse(byField.get("dateOfBirth"));
        if (dob.kind() == LegacyDateParser.Kind.INVALID) {
            return RowEvaluation.failed(
                    rowNum,
                    ImportReasonCode.INVALID_DOB,
                    headerOf.get("dateOfBirth"),
                    "The date of birth could not be read as a real date (dates are read day-first, e.g. 03/04/1977 = 3 April 1977).");
        }

        String email = blankToNull(byField.get("email"));
        if (email != null && (email.length() > EMAIL_MAX || !PLAUSIBLE_EMAIL.matcher(email).matches())) {
            // LEGACY_COMPATIBILITY: "n/a", "-", "none" are everywhere in legacy exports. The value
            // is kept where it cannot be mistaken for an address; Patient.email stays empty.
            customFields.put(EMAIL_NOT_VALID, email);
            email = null;
        }
        String address = blankToNull(byField.get("address"));
        if (address != null && address.length() > ADDRESS_MAX) {
            customFields.put(ADDRESS_FULL, address);
            address = address.substring(0, ADDRESS_MAX);
        }
        if (address != null && !noEmoji.isValid(address, null)) {
            return RowEvaluation.failed(
                    rowNum, ImportReasonCode.VALIDATION_FAILED, headerOf.get("address"), "Address contains characters that are not allowed.");
        }
        String history = blankToNull(byField.get("medicalHistory"));
        if (history != null && history.length() > MEDICAL_HISTORY_MAX) {
            customFields.put(MEDICAL_HISTORY_FULL, history);
            history = history.substring(0, MEDICAL_HISTORY_MAX);
        }
        if (history != null && !noEmoji.isValid(history, null)) {
            return RowEvaluation.failed(
                    rowNum,
                    ImportReasonCode.VALIDATION_FAILED,
                    headerOf.get("medicalHistory"),
                    "Medical history contains characters that are not allowed.");
        }

        // 4. In-file duplicates.
        if (legacyId != null) {
            Optional<Integer> first = ctx.claimLegacyId(legacyId, rowNum);
            if (first.isPresent()) {
                return RowEvaluation.skipped(
                        rowNum,
                        ImportReasonCode.DUPLICATE_MRN_IN_FILE,
                        "The old patient ID on this row already appears on row " + first.get()
                                + ". Only the first row was used; resolve the duplicate and re-import this one.",
                        null);
            }
        }
        if (phone.isCanonical()) {
            Optional<Integer> first = ctx.claimPhone(phone.canonical(), legacyId, rowNum);
            if (first.isPresent()) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.DUPLICATE_PHONE_IN_FILE,
                        headerOf.get("phone"),
                        "This phone number also appears on row " + first.get() + " for a different patient. "
                                + "Confirm whether these are two people sharing a number, then register the second one by hand.",
                        PatientDuplicateFinder.maskPhone(phone.canonical()),
                        null,
                        List.of());
            }
        }

        // 5. Matching.
        Patient matched = null;
        boolean reactivate = false;
        PatientImportLink link = null;

        if (legacyId != null) {
            Optional<PatientImportLink> byMrn = links.findByHospitalIdAndLegacyId(hospitalId, legacyId);
            if (byMrn.isPresent()) {
                link = byMrn.get();
                Optional<Patient> p = patients.findByIdAndHospitalId(link.getPatientId(), hospitalId);
                if (p.isEmpty()) {
                    return RowEvaluation.review(
                            rowNum,
                            ImportReasonCode.INACTIVE_MATCH,
                            headerOf.get("legacyId"),
                            "The old patient ID matches an import record whose patient no longer exists.",
                            null,
                            link.getPatientId(),
                            List.of());
                }
                matched = p.get();
                if (!Boolean.TRUE.equals(matched.getIsActive())) {
                    if (deactivatedByUndoneImport(link, hospitalId)) {
                        reactivate = true;
                    } else {
                        return RowEvaluation.review(
                                rowNum,
                                ImportReasonCode.INACTIVE_MATCH,
                                headerOf.get("legacyId"),
                                "The old patient ID matches a patient that staff deactivated"
                                        + customIdSuffix(matched) + ". The row was not applied.",
                                null,
                                matched.getId(),
                                List.of());
                    }
                }
                // Identity sanity check: MRN says same person; name AND date of birth both saying otherwise wins.
                PatientFieldValues snapshotForIdentity = PatientFieldValues.fromJson(link.getLastImportedValuesJson());
                PatientFieldValues reference = snapshotForIdentity != null ? snapshotForIdentity : PatientFieldValues.of(matched);
                boolean nameDiffers = !NameMatching.sameName(name, reference.name());
                boolean dobDiffers = dob.isValid() && reference.dateOfBirth() != null && !dob.date().equals(reference.dateOfBirth());
                if (nameDiffers && dobDiffers) {
                    return RowEvaluation.review(
                            rowNum,
                            ImportReasonCode.MRN_IDENTITY_MISMATCH,
                            headerOf.get("legacyId"),
                            "The old patient ID matches an existing patient" + customIdSuffix(matched)
                                    + " but both the name and the date of birth differ. The row was not applied.",
                            null,
                            matched.getId(),
                            List.of());
                }
            }
            // Unknown MRN: a new patient. Deliberately no name+phone fallback — the file told us who this is.
        } else {
            // No MRN: only an exact active name + canonical phone match is safe, and a phone is needed to try.
            if (phone.kind() == LegacyPhoneNormalizer.Kind.BLANK) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.PHONE_MISSING,
                        headerOf.get("phone"),
                        "Phone number is blank. A phone number is required to register a patient; add one and re-import this row.",
                        null,
                        null,
                        List.of());
            }
            if (!phone.isCanonical()) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.PHONE_UNRECOVERABLE,
                        headerOf.get("phone"),
                        "Phone number could not be read as a 10-digit number. Correct it and re-import this row.",
                        null,
                        null,
                        List.of());
            }
            List<Patient> candidates = patients.findActiveByPhoneOrdered(phone.canonical(), hospitalId).stream()
                    .filter(p -> NameMatching.sameName(name, p.getName()))
                    .toList();
            if (candidates.size() > 1) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.AMBIGUOUS_PATIENT_MATCH,
                        headerOf.get("name"),
                        "More than one existing patient has this name and phone number; the row was not applied. Resolve it by hand.",
                        PatientDuplicateFinder.maskPhone(phone.canonical()),
                        null,
                        candidates.stream().map(Patient::getId).toList());
            }
            if (candidates.size() == 1) {
                Patient c = candidates.get(0);
                if (dob.isValid() && c.getDateOfBirth() != null && !dob.date().equals(c.getDateOfBirth())) {
                    return RowEvaluation.review(
                            rowNum,
                            ImportReasonCode.AMBIGUOUS_PATIENT_MATCH,
                            headerOf.get("dateOfBirth"),
                            "An existing patient" + customIdSuffix(c)
                                    + " has this name and phone number but a different date of birth; the row was not applied.",
                            PatientDuplicateFinder.maskPhone(phone.canonical()),
                            c.getId(),
                            List.of());
                }
                matched = c;
                link = links.findByHospitalIdAndPatientId(hospitalId, c.getId()).orElse(null);
            }
        }

        // 6. Requirements for a NEW patient, then the database duplicate-phone check.
        if (matched == null) {
            if (phone.kind() == LegacyPhoneNormalizer.Kind.BLANK) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.PHONE_MISSING,
                        headerOf.get("phone"),
                        "Phone number is blank. A phone number is required to register a patient; add one and re-import this row.",
                        null,
                        null,
                        List.of());
            }
            if (!phone.isCanonical()) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.PHONE_UNRECOVERABLE,
                        headerOf.get("phone"),
                        "Phone number could not be read as a 10-digit number. Correct it and re-import this row.",
                        null,
                        null,
                        List.of());
            }
            if (gender.isEmpty()) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.INVALID_GENDER,
                        headerOf.get("gender"),
                        genderRaw == null
                                ? "Gender is blank. It is required to register a patient; add M, F or O and re-import this row."
                                : "Gender was not recognised. Use M, F or O (or Male, Female, Other) and re-import this row.",
                        null,
                        null,
                        List.of());
            }
            if (!dob.isValid()) {
                // A new patient needs a date of birth, as on the manual path. An omitted or blank
                // cell is a question for a human, not a null on the record.
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.DOB_MISSING,
                        headerOf.get("dateOfBirth"),
                        headerOf.containsKey("dateOfBirth")
                                ? "Date of birth is blank. It is required to register a patient; add it and re-import this row."
                                : "No date of birth column is mapped. It is required to register a patient; map one and re-import this row.",
                        null,
                        null,
                        List.of());
            }
        } else {
            // An existing patient: an omitted or blank phone/DOB leaves the record as it is (the
            // file never erases), but a phone that was SUPPLIED and cannot be read is not ignored —
            // the whole row waits for a human rather than applying its other fields around it.
            if (phone.kind() == LegacyPhoneNormalizer.Kind.UNRECOVERABLE) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.PHONE_UNRECOVERABLE,
                        headerOf.get("phone"),
                        "Phone number could not be read as a 10-digit number. Correct it and re-import this row; nothing on it was applied.",
                        null,
                        matched.getId(),
                        List.of());
            }
        }
        if (matched != null && genderRaw != null && gender.isEmpty()) {
            return RowEvaluation.review(
                    rowNum,
                    ImportReasonCode.INVALID_GENDER,
                    headerOf.get("gender"),
                    "Gender was not recognised. Use M, F or O (or Male, Female, Other) and re-import this row.",
                    null,
                    matched.getId(),
                    List.of());
        }

        if (phone.isCanonical()) {
            Long exclude = matched == null ? null : matched.getId();
            List<DuplicatePatientMatch> holders = duplicateFinder.findActiveByPhone(hospitalId, phone.canonical(), exclude);
            if (!holders.isEmpty()) {
                return RowEvaluation.review(
                        rowNum,
                        ImportReasonCode.DUPLICATE_PHONE_REQUIRES_REVIEW,
                        headerOf.get("phone"),
                        "This phone number already belongs to an active patient"
                                + (holders.size() == 1 ? customIdSuffix(holders.get(0).customId()) : "s")
                                + ". If this is a different person sharing the number, register them by hand and confirm the shared number there.",
                        PatientDuplicateFinder.maskPhone(phone.canonical()),
                        exclude,
                        holders.stream().map(DuplicatePatientMatch::id).toList());
            }
        }

        PatientFieldValues proposed = new PatientFieldValues(
                name,
                phone.isCanonical() ? phone.canonical() : null,
                gender.orElse(null),
                dob.isValid() ? dob.date() : null,
                email,
                address,
                history);

        if (matched == null) {
            return RowEvaluation.created(rowNum, new CreateCandidate(hospitalId, proposed, legacyId, customFields));
        }

        // 7. Update: ownership against the last-imported snapshot, then only the changes.
        PatientFieldValues current = PatientFieldValues.of(matched);
        PatientFieldValues snapshot = link == null ? null : PatientFieldValues.fromJson(link.getLastImportedValuesJson());
        Map<String, String> changes = new LinkedHashMap<>();
        List<String> humanOwnedConflicts = new ArrayList<>();
        for (String f : PatientFieldValues.FIELDS) {
            String fileValue = proposed.get(f);
            if (fileValue == null) continue; // the file never blanks a field
            String currentValue = current.get(f);
            if (PatientFieldValues.same(fileValue, currentValue)) continue;
            // A name that differs only in case, spacing or simple punctuation is the same name,
            // not a correction; the stored spelling is kept.
            if ("name".equals(f) && NameMatching.sameName(fileValue, currentValue)) continue;
            if (currentValue != null) {
                boolean importOwned = snapshot != null && PatientFieldValues.same(currentValue, snapshot.get(f));
                if (!importOwned) {
                    humanOwnedConflicts.add(headerOf.getOrDefault(f, f));
                    continue;
                }
            }
            changes.put(f, fileValue);
        }
        if (!humanOwnedConflicts.isEmpty()) {
            return RowEvaluation.review(
                    rowNum,
                    ImportReasonCode.EDITED_SINCE_IMPORT,
                    String.join(", ", humanOwnedConflicts),
                    "This patient" + customIdSuffix(matched) + " was changed in the system after the last import ("
                            + String.join(", ", humanOwnedConflicts)
                            + "), and the file wants to change the same field(s). Staff changes take precedence; nothing was applied.",
                    null,
                    matched.getId(),
                    List.of());
        }

        boolean clearStaleAck = changes.containsKey("phone")
                && matched.getDuplicatePhoneAckFor() != null
                && !matched.getDuplicatePhoneAckFor().equals(changes.get("phone"));

        UpdateCandidate update = new UpdateCandidate(
                hospitalId,
                matched.getId(),
                current,
                Boolean.TRUE.equals(matched.getIsActive()),
                matched.getDuplicatePhoneAckFor(),
                changes,
                reactivate,
                clearStaleAck,
                legacyId,
                customFields,
                snapshot != null);
        if (update.isNoOp()) {
            return RowEvaluation.skipped(
                    rowNum, ImportReasonCode.NO_CHANGE, "This row matches an existing patient" + customIdSuffix(matched) + " and changes nothing.", matched.getId());
        }
        return RowEvaluation.updated(rowNum, update);
    }

    /** Only a patient an UNDONE import deactivated may be brought back by a re-import; a staff deactivation is left alone. */
    private boolean deactivatedByUndoneImport(PatientImportLink link, Long hospitalId) {
        if (link.getCreatedByBatchId() == null) return false;
        return batches.findByIdAndHospitalId(link.getCreatedByBatchId(), hospitalId)
                .map(ImportBatch::getStatus)
                .filter(s -> s == ImportStatus.UNDONE)
                .isPresent();
    }

    /** Unmapped, non-blank columns, verbatim under their display header. */
    private static Map<String, String> unmappedColumns(ParsedRow row, SheetHeader header, Map<String, String> mapping) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> display = header.display();
        for (int i = 0; i < display.size(); i++) {
            String h = display.get(i);
            if (mapping.containsKey(h)) continue;
            String v = i < row.values().size() ? row.values().get(i) : "";
            if (!v.isBlank()) out.put(h, v);
        }
        return out;
    }

    private static String customIdSuffix(Patient p) {
        return customIdSuffix(p.getCustomId());
    }

    private static String customIdSuffix(String customId) {
        return customId == null || customId.isBlank() ? "" : " (" + customId + ")";
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

}
