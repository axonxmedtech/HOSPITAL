package com.hms.service.import_;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-run state for in-file duplicate detection, owned by whoever drives a preview or a commit
 * and handed to {@link PatientImporter} row by row. One context per file per run; never static,
 * never shared between hospitals — the hospital it was built for is the only tenant any lookup
 * made through it may see.
 *
 * <p>Two things are remembered: MRNs already claimed by an earlier row (a repeat is a
 * {@code DUPLICATE_MRN_IN_FILE} skip) and canonical phones already claimed by an earlier row
 * together with that row's MRN (a repeat from a DIFFERENT MRN, or from any no-MRN row, is a
 * {@code DUPLICATE_PHONE_IN_FILE} review; the same MRN re-listing its own phone is not a conflict).
 */
public final class ImportEvaluationContext {

    private final Long hospitalId;
    private final Map<String, Integer> seenLegacyIds = new HashMap<>();
    private final Map<String, PhoneClaim> seenPhones = new HashMap<>();

    private record PhoneClaim(int rowNum, String legacyId) {}

    public ImportEvaluationContext(Long hospitalId) {
        this.hospitalId = Objects.requireNonNull(hospitalId, "hospitalId comes from the authenticated context and is required");
    }

    public Long hospitalId() {
        return hospitalId;
    }

    /** @return the row that first used this MRN, if any; otherwise claims it for {@code rowNum}. */
    Optional<Integer> claimLegacyId(String legacyId, int rowNum) {
        Integer first = seenLegacyIds.putIfAbsent(legacyId, rowNum);
        return Optional.ofNullable(first);
    }

    /**
     * @return the row that first claimed this canonical phone under a different identity, if any;
     *         otherwise records the claim. A row with the same MRN as the first claimant is the
     *         same person and not a conflict.
     */
    Optional<Integer> claimPhone(String canonicalPhone, String legacyId, int rowNum) {
        PhoneClaim first = seenPhones.get(canonicalPhone);
        if (first == null) {
            seenPhones.put(canonicalPhone, new PhoneClaim(rowNum, legacyId));
            return Optional.empty();
        }
        if (legacyId != null && legacyId.equals(first.legacyId())) {
            return Optional.empty();
        }
        return Optional.of(first.rowNum());
    }

    public int distinctLegacyIds() {
        return seenLegacyIds.size();
    }

    public int distinctPhones() {
        return seenPhones.size();
    }
}
