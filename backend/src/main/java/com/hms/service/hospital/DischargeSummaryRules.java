package com.hms.service.hospital;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.PlanDischargeRequest;
import com.hms.dto.PlanDischargeRequest.DischargeMedication;
import com.hms.entity.DischargeSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a discharge summary must contain, and how its parts are stored.
 *
 * <p>The summary is handed to the patient and read by the next doctor, so the parts that make it
 * useful are required: the final diagnosis, the course in hospital, and — for a patient who
 * leaves alive and in our care's hands — the condition at discharge. Everything else is optional.
 */
final class DischargeSummaryRules {

    static final Set<String> CONDITIONS = Set.of("IMPROVED", "STABLE", "UNCHANGED", "DETERIORATED");
    private static final int MAX_MEDICINES = 40;
    private static final ObjectMapper JSON = new ObjectMapper();

    private DischargeSummaryRules() {}

    static void validate(PlanDischargeRequest req, String dischargeType) {
        if (blank(req.getFinalDiagnosis())) {
            throw new IllegalArgumentException("Enter the final diagnosis");
        }
        if (blank(req.getTreatmentGiven())) {
            throw new IllegalArgumentException("Enter the course in hospital (treatment given)");
        }
        boolean leavesAlive = !IpdClinicalState.EXPIRED.equals(dischargeType)
                && !IpdClinicalState.ABSCONDED.equals(dischargeType);
        String condition = normaliseCondition(req.getConditionAtDischarge());
        if (leavesAlive && condition == null) {
            throw new IllegalArgumentException("Select the patient's condition at discharge");
        }
        List<DischargeMedication> meds = req.getDischargeMedications();
        if (meds != null) {
            if (meds.size() > MAX_MEDICINES) {
                throw new IllegalArgumentException("A discharge summary can list at most " + MAX_MEDICINES + " medicines");
            }
            for (DischargeMedication m : meds) {
                if (m == null || blank(m.getName())) {
                    throw new IllegalArgumentException("Every medicine to take home needs a name");
                }
                if (m.getDurationDays() != null && (m.getDurationDays() < 1 || m.getDurationDays() > 365)) {
                    throw new IllegalArgumentException("Duration for " + m.getName().trim() + " must be 1 to 365 days");
                }
            }
        }
        if (req.getFollowUpDate() != null
                && req.getFollowUpDate().isBefore(java.time.LocalDate.now(java.time.ZoneId.systemDefault()))) {
            throw new IllegalArgumentException("The follow-up date cannot be in the past");
        }
    }

    static void apply(PlanDischargeRequest req, DischargeSummary ds) {
        ds.setFinalDiagnosis(req.getFinalDiagnosis().trim());
        ds.setTreatmentGiven(req.getTreatmentGiven().trim());
        ds.setDischargeNotes(trimToNull(req.getDischargeNotes()));
        ds.setConditionAtDischarge(normaliseCondition(req.getConditionAtDischarge()));
        ds.setProceduresDone(trimToNull(req.getProceduresDone()));
        ds.setInvestigations(trimToNull(req.getInvestigations()));
        ds.setAdvice(trimToNull(req.getAdvice()));
        ds.setDischargeMedications(writeMedicines(req.getDischargeMedications()));
        // A changed follow-up date or time is a new request; the booking is made at discharge.
        ds.setFollowUpDate(req.getFollowUpDate());
        ds.setFollowUpTime(req.getFollowUpDate() == null ? null : req.getFollowUpTime());
    }

    /** The medicines to take home, read back from the stored JSON; empty when none or unreadable. */
    static List<DischargeMedication> medicines(DischargeSummary ds) {
        String raw = ds == null ? null : ds.getDischargeMedications();
        if (blank(raw)) return new ArrayList<>();
        try {
            return JSON.readValue(raw, new TypeReference<List<DischargeMedication>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static String writeMedicines(List<DischargeMedication> meds) {
        if (meds == null || meds.isEmpty()) return null;
        List<DischargeMedication> clean = new ArrayList<>();
        for (DischargeMedication m : meds) {
            DischargeMedication c = new DischargeMedication();
            c.setName(m.getName().trim());
            c.setDose(trimToNull(m.getDose()));
            c.setFrequency(trimToNull(m.getFrequency()));
            c.setDurationDays(m.getDurationDays());
            c.setInstructions(trimToNull(m.getInstructions()));
            clean.add(c);
        }
        try {
            return JSON.writeValueAsString(clean);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store the discharge medicines", e);
        }
    }

    private static String normaliseCondition(String condition) {
        if (blank(condition)) return null;
        String c = condition.trim().toUpperCase(Locale.ROOT);
        if (!CONDITIONS.contains(c)) {
            throw new IllegalArgumentException("Condition at discharge must be Improved, Stable, Unchanged or Deteriorated");
        }
        return c;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return blank(s) ? null : s.trim();
    }
}
