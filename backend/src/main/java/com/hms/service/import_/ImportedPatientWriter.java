package com.hms.service.import_;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Patient;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportStatus;
import com.hms.entity.import_.PatientImportLink;
import com.hms.repository.PatientRepository;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.repository.import_.PatientImportLinkRepository;
import com.hms.service.hospital.DuplicatePhoneAcknowledgement;
import com.hms.service.hospital.PatientRegistrar;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists ONE evaluated row — a {@link CreateCandidate} or an {@link UpdateCandidate} — in its
 * own transaction. Patient and import lineage are written together or not at all.
 *
 * <p><b>Transaction boundary.</b> Each public method is {@code @Transactional} (REQUIRED) and is
 * reached cross-bean, so the annotation is real. {@link PatientRegistrar#persistNewPatient}
 * joins the create transaction; the link row is saved inside the same one; a failure of either
 * rolls back both. A {@code DataIntegrityViolationException} — the V21 index refusing a phone
 * that another registration took between evaluation and now — surfaces at the flush, the
 * transaction rolls back on the way out, and the exception reaches the caller OUTSIDE it. That
 * is exactly the shape {@code PatientService} relies on, and it is why classification lives in
 * {@link ImportRowPersister}, not here: nothing may catch that exception and keep using the
 * transaction that just died. The future engine calls the persister once per row and holds no
 * transaction of its own, so one row's race can never poison another's.
 *
 * <p><b>Candidates are proposals, not state.</b> For an update the patient and its link are
 * reloaded here, by id AND hospital, and every value the proposal was computed from is compared
 * against what the database holds now. Any difference — a human edited the record after the
 * preview — refuses the write with {@link PatientChangedSinceEvaluationException}. The writer
 * never recomputes the proposal; the row goes back to a human.
 *
 * <p><b>What may be written.</b> Only the fields in {@link UpdateCandidate#changes()}, through an
 * explicit allowlist ({@link #WRITABLE}); never id, publicId, customId, hospitalId, status, and
 * is_active only for an approved reactivation; the acknowledgement columns only through
 * {@link DuplicatePhoneAcknowledgement#clear} when the proposal says the phone is changing.
 * No entity is ever copied wholesale.
 *
 * <p>Nothing from the row — names, phones, custom fields — is logged.
 */
@Component
public class ImportedPatientWriter {

    /** The demographic fields an import may set on a patient, and nothing else. */
    static final Set<String> WRITABLE = Set.of("name", "phone", "gender", "dateOfBirth", "email", "address", "medicalHistory");

    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() {};

    private final PatientRepository patients;
    private final PatientImportLinkRepository links;
    private final ImportBatchRepository batches;
    private final PatientRegistrar registrar;
    private final ObjectMapper json;

    public ImportedPatientWriter(
            PatientRepository patients,
            PatientImportLinkRepository links,
            ImportBatchRepository batches,
            PatientRegistrar registrar,
            ObjectMapper json) {
        this.patients = patients;
        this.links = links;
        this.batches = batches;
        this.registrar = registrar;
        this.json = json;
    }

    // ── create ───────────────────────────────────────────────────────────────

    /**
     * Registers a new patient through {@link PatientRegistrar} (registration number, public id
     * and entity validation exactly as manual registration) and records its lineage, atomically.
     *
     * @return the new patient's id
     * @throws org.springframework.dao.DataIntegrityViolationException from the flush, after rollback — the
     *         caller classifies it (a V21 refusal is a lost phone race)
     */
    @Transactional
    public Long create(CreateCandidate candidate, ImportWriteContext ctx) {
        requireSameTenant(candidate.hospitalId(), ctx);
        PatientFieldValues v = candidate.values();

        Patient p = new Patient();
        p.setHospitalId(ctx.hospitalId());
        p.setName(v.name());
        p.setPhone(v.phone());
        p.setGender(v.gender());
        p.setDateOfBirth(v.dateOfBirth());
        p.setEmail(v.email());
        p.setAddress(v.address());
        p.setMedicalHistory(v.medicalHistory());
        p.setIsActive(true);
        // No id, publicId (@PrePersist), customId (registrar), status (entity default) or
        // duplicate_phone_ack_* (never from an import) are set here.

        Patient saved = registrar.persistNewPatient(p);

        PatientImportLink link = new PatientImportLink();
        link.setPatientId(saved.getId());
        link.setHospitalId(ctx.hospitalId());
        link.setLegacyId(candidate.legacyId());
        link.setCreatedByBatchId(ctx.batchId());
        link.setLastBatchId(ctx.batchId());
        link.setLastImportedAt(ctx.now());
        link.setLastImportedValuesJson(toJson(PatientFieldValues.of(saved).toMap()));
        link.setCustomFieldsJson(candidate.customFields().isEmpty() ? null : toJson(candidate.customFields()));
        links.saveAndFlush(link); // flush now: a link constraint failure must surface inside this transaction

        return saved.getId();
    }

    // ── update ───────────────────────────────────────────────────────────────

    /**
     * Applies exactly the proposed changes to a reloaded patient and advances its lineage,
     * atomically — or refuses, if the patient is not what the proposal was computed from.
     *
     * @return the patient's id
     * @throws PatientChangedSinceEvaluationException the reloaded state differs from {@code expected}
     * @throws org.springframework.dao.DataIntegrityViolationException from the flush, after rollback
     */
    @Transactional
    public Long update(UpdateCandidate candidate, ImportWriteContext ctx) {
        requireSameTenant(candidate.hospitalId(), ctx);
        Long id = candidate.patientId();

        Patient p = patients.findByIdAndHospitalId(id, ctx.hospitalId())
                .orElseThrow(() -> new PatientChangedSinceEvaluationException(id, "missing"));
        requireExpectedState(p, candidate);

        PatientImportLink link = links.findByHospitalIdAndPatientId(ctx.hospitalId(), id).orElse(null);
        PatientFieldValues oldSnapshot = link == null ? null : PatientFieldValues.fromJson(link.getLastImportedValuesJson());

        if (candidate.reactivate()) {
            // Re-verify, not trust: only a patient an UNDONE import deactivated comes back.
            boolean eligible = link != null
                    && link.getCreatedByBatchId() != null
                    && batches.findByIdAndHospitalId(link.getCreatedByBatchId(), ctx.hospitalId())
                            .map(ImportBatch::getStatus)
                            .filter(s -> s == ImportStatus.UNDONE)
                            .isPresent();
            if (!eligible) throw new PatientChangedSinceEvaluationException(id, "reactivation");
            p.setIsActive(true);
        }

        for (Map.Entry<String, String> change : candidate.changes().entrySet()) {
            apply(p, change.getKey(), change.getValue());
        }
        if (candidate.clearStalePhoneAcknowledgement()) {
            DuplicatePhoneAcknowledgement.clear(p);
        }
        patients.saveAndFlush(p); // the V21 refusal, if any, surfaces here and rolls everything back

        if (link == null) {
            link = new PatientImportLink();
            link.setPatientId(id);
            link.setHospitalId(ctx.hospitalId());
            link.setLegacyId(candidate.legacyId());
            // createdByBatchId stays null: this batch did not create the patient, so undo must not touch it.
        } else if (link.getLegacyId() == null && candidate.legacyId() != null) {
            link.setLegacyId(candidate.legacyId());
        } else if (candidate.legacyId() != null && !candidate.legacyId().equals(link.getLegacyId())) {
            // The MRN is the identity key of the link; rewriting it is not something a routine
            // re-import may do. Matching by MRN can never produce this, so reaching it means the
            // proposal is inconsistent with the lineage — refuse rather than guess.
            throw new PatientChangedSinceEvaluationException(id, "legacyId");
        }
        link.setLastBatchId(ctx.batchId());
        link.setLastImportedAt(ctx.now());
        link.setLastImportedValuesJson(toJson(nextSnapshot(p, oldSnapshot, candidate.changes())));
        if (!candidate.customFields().isEmpty()) {
            link.setCustomFieldsJson(toJson(mergeCustomFields(link.getCustomFieldsJson(), candidate.customFields())));
        }
        links.saveAndFlush(link);

        return id;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static void requireSameTenant(Long candidateHospital, ImportWriteContext ctx) {
        if (!Objects.equals(candidateHospital, ctx.hospitalId())) {
            throw new ImportTenantMismatchException(candidateHospital, ctx.hospitalId());
        }
    }

    /** Every value the proposal was computed from must still hold; otherwise a human decides. */
    private static void requireExpectedState(Patient p, UpdateCandidate c) {
        PatientFieldValues now = PatientFieldValues.of(p);
        for (String f : PatientFieldValues.FIELDS) {
            if (!PatientFieldValues.same(now.get(f), c.expected().get(f))) {
                throw new PatientChangedSinceEvaluationException(p.getId(), f);
            }
        }
        if (Boolean.TRUE.equals(p.getIsActive()) != c.expectedActive()) {
            throw new PatientChangedSinceEvaluationException(p.getId(), "active");
        }
        if (!Objects.equals(p.getDuplicatePhoneAckFor(), c.expectedAckFor())) {
            throw new PatientChangedSinceEvaluationException(p.getId(), "acknowledgement");
        }
    }

    /** Explicit, allowlisted assignment. Anything not listed is not a field an import may touch. */
    private static void apply(Patient p, String field, String value) {
        if (!WRITABLE.contains(field)) {
            throw new IllegalArgumentException("Not an import-writable patient field: " + field);
        }
        switch (field) {
            case "name" -> p.setName(value);
            case "phone" -> p.setPhone(value);
            case "gender" -> p.setGender(value);
            case "dateOfBirth" -> p.setDateOfBirth(LocalDate.parse(value));
            case "email" -> p.setEmail(value);
            case "address" -> p.setAddress(value);
            case "medicalHistory" -> p.setMedicalHistory(value);
            default -> throw new IllegalArgumentException("Not an import-writable patient field: " + field);
        }
    }

    /**
     * The snapshot after an update records, per field, what the importer now owns: the value it
     * just wrote, or the earlier snapshot value where the field is still exactly that. A field a
     * human changed keeps NO entry — recording its current value would hand it back to the
     * importer and let the next file overwrite the edit.
     */
    static Map<String, String> nextSnapshot(Patient p, PatientFieldValues oldSnapshot, Map<String, String> changes) {
        PatientFieldValues now = PatientFieldValues.of(p);
        Map<String, String> next = new LinkedHashMap<>();
        for (String f : PatientFieldValues.FIELDS) {
            if (changes.containsKey(f)) {
                next.put(f, now.get(f));
            } else if (oldSnapshot != null && oldSnapshot.get(f) != null && PatientFieldValues.same(now.get(f), oldSnapshot.get(f))) {
                next.put(f, oldSnapshot.get(f));
            } else {
                next.put(f, null);
            }
        }
        return next;
    }

    private Map<String, String> mergeCustomFields(String existingJson, Map<String, String> incoming) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                merged.putAll(json.readValue(existingJson, STRING_MAP));
            } catch (JsonProcessingException e) {
                // An unreadable earlier blob is not a reason to refuse the row; the new values still land.
            }
        }
        merged.putAll(incoming);
        return merged;
    }

    private String toJson(Map<String, String> map) {
        try {
            return json.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // Maps of strings do not fail to serialise; if the mapper is misconfigured that is
            // infrastructure, and the message must not carry the map's contents.
            throw new IllegalStateException("Could not serialise import lineage", e);
        }
    }
}
