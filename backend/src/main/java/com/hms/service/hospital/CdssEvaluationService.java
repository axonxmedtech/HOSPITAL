package com.hms.service.hospital;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.CdssAlertDTO;
import com.hms.dto.EwsResultDTO;
import com.hms.dto.SmartSummaryDTO;
import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CdssEvaluationService {

    @Autowired private PatientAllergyRepository patientAllergyRepo;
    @Autowired private PrescriptionRepository prescriptionRepo;
    @Autowired private DrugInteractionMasterRepository drugInteractionRepo;
    @Autowired private CdssAlertLogRepository alertLogRepo;
    @Autowired private VitalSignsRepository vitalSignsRepo;
    @Autowired private LabOrderRepository labOrderRepo;
    @Autowired private RadiologyOrderRepository radiologyOrderRepo;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepo;
    @Autowired private SecurityContextHelper securityHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 1. Prescription check ─────────────────────────────────────────────────

    public List<CdssAlertDTO> evaluatePrescription(Long patientId, String medicineName,
                                                    Long ipdAdmissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        List<CdssAlertDTO> alerts = new ArrayList<>();

        if (medicineName == null || medicineName.isBlank()) return alerts;
        String medLower = medicineName.trim().toLowerCase();

        // Allergy check
        List<String> allergyNames = patientAllergyRepo.findAllergyNamesByPatientId(patientId, hospitalId);
        for (String allergyName : allergyNames) {
            if (medLower.contains(allergyName.trim().toLowerCase())
                    || allergyName.trim().toLowerCase().contains(medLower)) {
                alerts.add(new CdssAlertDTO(
                        "ALLERGY", "HIGH",
                        "Allergy Alert",
                        "This patient has a documented allergy to " + allergyName +
                        ". The prescribed medicine '" + medicineName + "' may conflict.",
                        "Review the allergy record and consider an alternative medicine."
                ));
            }
        }

        // Duplicate + interaction checks (IPD only)
        if (ipdAdmissionId != null) {
            validateAdmissionOwnership(ipdAdmissionId, hospitalId);
            List<Prescription> active = prescriptionRepo.findByIpdAdmissionIdAndStatus(ipdAdmissionId, "ACTIVE");

            // Duplicate medicine
            for (Prescription p : active) {
                if (p.getMedicineName() != null &&
                        p.getMedicineName().trim().equalsIgnoreCase(medicineName.trim())) {
                    alerts.add(new CdssAlertDTO(
                            "DUPLICATE_MEDICINE", "MEDIUM",
                            "Duplicate Medicine",
                            "'" + medicineName + "' is already prescribed and active for this admission.",
                            "Review the existing prescription before adding another dose."
                    ));
                    break;
                }
            }

            // Drug interaction
            List<DrugInteractionMaster> interactions =
                    drugInteractionRepo.findInteractionsInvolvingMedicine(hospitalId, medicineName);
            if (!interactions.isEmpty()) {
                Set<String> activeMedNamesLower = active.stream()
                        .map(p -> p.getMedicineName() == null ? "" : p.getMedicineName().trim().toLowerCase())
                        .collect(Collectors.toSet());

                for (DrugInteractionMaster dim : interactions) {
                    String otherDrug = medLower.contains(dim.getDrugAName().trim().toLowerCase())
                            ? dim.getDrugBName() : dim.getDrugAName();
                    boolean otherPresent = activeMedNamesLower.stream()
                            .anyMatch(name -> name.contains(otherDrug.trim().toLowerCase())
                                    || otherDrug.trim().toLowerCase().contains(name));
                    if (otherPresent) {
                        alerts.add(new CdssAlertDTO(
                                "DRUG_INTERACTION", dim.getSeverity(),
                                "Drug Interaction",
                                dim.getInteractionDescription() +
                                " [" + dim.getDrugAName() + " + " + dim.getDrugBName() + "]",
                                "Review both medicines with the treating physician before prescribing."
                        ));
                    }
                }
            }
        }

        return alerts;
    }

    // ── 2. Critical lab alert ─────────────────────────────────────────────────

    @Transactional
    public List<CdssAlertDTO> evaluateLabResult(Long patientId, Long ipdAdmissionId,
                                                 String testName, boolean isAbnormal,
                                                 String parametersJson) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        List<CdssAlertDTO> alerts = new ArrayList<>();
        if (!isAbnormal) return alerts;

        List<String> flaggedParams = parseFlaggedParameters(parametersJson);
        String paramSummary = flaggedParams.isEmpty()
                ? "One or more values are outside normal range."
                : "Abnormal: " + String.join(", ", flaggedParams);

        String message = "Lab test '" + testName + "' has abnormal results. " + paramSummary;
        alerts.add(new CdssAlertDTO("CRITICAL_LAB", "HIGH", "Critical Lab Result", message,
                "Immediate doctor review required."));

        CdssAlertLog log = new CdssAlertLog();
        log.setHospitalId(hospitalId);
        log.setAlertType("CRITICAL_LAB");
        log.setPatientId(patientId);
        log.setIpdAdmissionId(ipdAdmissionId);
        log.setAlertMessage(message);
        log.setSeverity("HIGH");
        alertLogRepo.save(log);

        return alerts;
    }

    // ── 3. EWS calculation (NEWS2 5-parameter subset) ─────────────────────────

    public EwsResultDTO calculateEws(Long ipdAdmissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        validateAdmissionOwnership(ipdAdmissionId, hospitalId);

        Optional<VitalSigns> opt = vitalSignsRepo
                .findByIpdAdmissionIdOrderByRecordedAtDesc(ipdAdmissionId)
                .stream().findFirst();

        EwsResultDTO result = new EwsResultDTO();
        if (opt.isEmpty()) {
            result.setSeverity("UNKNOWN");
            result.setMessage("No vitals recorded yet.");
            return result;
        }

        VitalSigns v = opt.get();
        result.setBloodPressure(v.getBloodPressure());
        result.setPulse(v.getPulse());
        result.setTemperature(v.getTemperature() == null ? null : v.getTemperature().doubleValue());
        result.setSpo2(v.getSpo2());
        result.setRespiratoryRate(v.getRespiratoryRate());

        int sbpScore   = scoreSbp(parseSystolic(v.getBloodPressure()));
        int pulseScore = scorePulse(v.getPulse());
        int tempScore  = scoreTemp(v.getTemperature());
        int spo2Score  = scoreSpo2(v.getSpo2());
        int respScore  = scoreRespRate(v.getRespiratoryRate());
        int total = sbpScore + pulseScore + tempScore + spo2Score + respScore;

        result.setSbpScore(sbpScore);
        result.setPulseScore(pulseScore);
        result.setTempScore(tempScore);
        result.setSpo2Score(spo2Score);
        result.setRespRateScore(respScore);
        result.setTotalScore(total);

        if (total >= 5) {
            result.setSeverity("HIGH");
            result.setMessage("NEWS2 score " + total + ": Urgent doctor review recommended.");
        } else if (total >= 3) {
            result.setSeverity("MEDIUM");
            result.setMessage("NEWS2 score " + total + ": Increased monitoring advised.");
        } else {
            result.setSeverity("NORMAL");
            result.setMessage("NEWS2 score " + total + ": Vitals within acceptable range.");
        }
        return result;
    }

    // ── 4. Smart Summary ─────────────────────────────────────────────────────

    public SmartSummaryDTO getSmartSummary(Long ipdAdmissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        validateAdmissionOwnership(ipdAdmissionId, hospitalId);

        IpdAdmission admission = ipdAdmissionRepo.findById(ipdAdmissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + ipdAdmissionId));
        Long patientId = admission.getPatientId();

        List<String> allergyNames = patientAllergyRepo.findAllergyNamesByPatientId(patientId, hospitalId);

        List<String> activeMeds = prescriptionRepo
                .findByIpdAdmissionIdAndStatus(ipdAdmissionId, "ACTIVE")
                .stream()
                .map(p -> p.getMedicineName() + (p.getDosage() != null ? " " + p.getDosage() : ""))
                .collect(Collectors.toList());

        List<String> pendingLabs = labOrderRepo
                .findByIpdAdmissionIdAndStatusNot(ipdAdmissionId, "CANCELLED")
                .stream()
                .filter(o -> !"COMPLETED".equals(o.getStatus()))
                .map(LabOrder::getTestName)
                .collect(Collectors.toList());

        List<String> pendingRad = radiologyOrderRepo
                .findByIpdAdmissionIdAndStatusNot(ipdAdmissionId, "CANCELLED")
                .stream()
                .filter(o -> !"COMPLETED".equals(o.getStatus()))
                .map(RadiologyOrder::getTestName)
                .collect(Collectors.toList());

        EwsResultDTO ews = calculateEws(ipdAdmissionId);

        List<CdssAlertLog> logs = alertLogRepo
                .findByIpdAdmissionIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(ipdAdmissionId);
        List<CdssAlertDTO> unacked = logs.stream()
                .map(l -> new CdssAlertDTO(l.getAlertType(), l.getSeverity(),
                        l.getAlertType().replace("_", " "), l.getAlertMessage(), "Review required."))
                .collect(Collectors.toList());

        return new SmartSummaryDTO(allergyNames, activeMeds, pendingLabs, pendingRad, ews, unacked);
    }

    // ── 5. Log acknowledgement ────────────────────────────────────────────────

    @Transactional
    public void logAcknowledgement(Long patientId, Long ipdAdmissionId,
                                    List<CdssAlertDTO> alerts, String overrideReason) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Long userId = securityHelper.getCurrentUserId();
        for (CdssAlertDTO alert : alerts) {
            CdssAlertLog log = new CdssAlertLog();
            log.setHospitalId(hospitalId);
            log.setAlertType(alert.getType());
            log.setPatientId(patientId);
            log.setIpdAdmissionId(ipdAdmissionId);
            log.setAlertMessage(alert.getMessage());
            log.setSeverity(alert.getSeverity());
            log.setAcknowledgedByUserId(userId);
            log.setAcknowledgedAt(LocalDateTime.now());
            log.setOverrideReason(overrideReason);
            alertLogRepo.save(log);
        }
    }

    // ── 6. Seed drug interactions ─────────────────────────────────────────────

    @Transactional
    public void seedDrugInteractions(Long hospitalId) {
        if (drugInteractionRepo.existsByHospitalId(hospitalId)) return;
        drugInteractionRepo.saveAll(buildSeedInteractions(hospitalId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateAdmissionOwnership(Long ipdAdmissionId, Long hospitalId) {
        IpdAdmission admission = ipdAdmissionRepo.findById(ipdAdmissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found: " + ipdAdmissionId));
        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new RuntimeException("Access denied to admission: " + ipdAdmissionId);
        }
    }

    private Integer parseSystolic(String bp) {
        if (bp == null || bp.isBlank()) return null;
        try { return Integer.parseInt(bp.trim().split("[/\\s]")[0]); }
        catch (Exception e) { return null; }
    }

    private List<String> parseFlaggedParameters(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            List<Map<String, String>> params = objectMapper.readValue(json, new TypeReference<>() {});
            return params.stream()
                    .filter(p -> { String f = p.getOrDefault("flag", ""); return f != null && !f.isBlank() && !"Normal".equalsIgnoreCase(f); })
                    .map(p -> p.getOrDefault("name", "?") + " (" + p.getOrDefault("value", "?") +
                              " " + p.getOrDefault("unit", "") + ") - " + p.getOrDefault("flag", ""))
                    .collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private int scoreSbp(Integer sbp) {
        if (sbp == null) return 0;
        if (sbp <= 90) return 3; if (sbp <= 100) return 2;
        if (sbp <= 110) return 1; if (sbp <= 219) return 0; return 3;
    }
    private int scorePulse(Integer p) {
        if (p == null) return 0;
        if (p <= 40) return 3; if (p <= 50) return 1;
        if (p <= 90) return 0; if (p <= 110) return 1;
        if (p <= 130) return 2; return 3;
    }
    private int scoreTemp(BigDecimal t) {
        if (t == null) return 0;
        double td = t.doubleValue();
        if (td <= 35.0) return 3; if (td <= 36.0) return 1;
        if (td <= 38.0) return 0; if (td <= 39.0) return 1; return 2;
    }
    private int scoreSpo2(Integer s) {
        if (s == null) return 0;
        if (s <= 91) return 3; if (s <= 93) return 2;
        if (s <= 95) return 1; return 0;
    }
    private int scoreRespRate(Integer r) {
        if (r == null) return 0;
        if (r <= 8) return 3; if (r <= 11) return 1;
        if (r <= 20) return 0; if (r <= 24) return 2; return 3;
    }

    private List<DrugInteractionMaster> buildSeedInteractions(Long hospitalId) {
        List<Object[]> raw = Arrays.asList(
            new Object[]{"Warfarin", "Aspirin", "HIGH", "Combined use significantly increases risk of bleeding."},
            new Object[]{"Warfarin", "Ibuprofen", "HIGH", "NSAIDs increase anticoagulant effect of Warfarin; bleeding risk elevated."},
            new Object[]{"Warfarin", "Naproxen", "HIGH", "NSAIDs increase anticoagulant effect of Warfarin; bleeding risk elevated."},
            new Object[]{"Warfarin", "Fluconazole", "HIGH", "Fluconazole inhibits Warfarin metabolism; INR may rise sharply."},
            new Object[]{"Warfarin", "Rifampicin", "HIGH", "Rifampicin induces Warfarin metabolism; anticoagulation may be lost."},
            new Object[]{"Methotrexate", "Ibuprofen", "HIGH", "NSAIDs reduce Methotrexate excretion; risk of Methotrexate toxicity."},
            new Object[]{"Methotrexate", "Aspirin", "HIGH", "Aspirin displaces Methotrexate from plasma proteins; toxicity risk."},
            new Object[]{"Lithium", "Ibuprofen", "HIGH", "NSAIDs reduce renal Lithium clearance; Lithium toxicity risk."},
            new Object[]{"Lithium", "Naproxen", "HIGH", "NSAIDs reduce renal Lithium clearance; Lithium toxicity risk."},
            new Object[]{"Digoxin", "Amiodarone", "HIGH", "Amiodarone inhibits P-glycoprotein; Digoxin levels may double."},
            new Object[]{"Amiodarone", "Simvastatin", "HIGH", "Amiodarone inhibits Simvastatin metabolism; myopathy and rhabdomyolysis risk."},
            new Object[]{"Clarithromycin", "Simvastatin", "HIGH", "Clarithromycin inhibits CYP3A4; Simvastatin levels elevated; myopathy risk."},
            new Object[]{"Erythromycin", "Simvastatin", "HIGH", "Erythromycin inhibits Simvastatin metabolism; myopathy risk."},
            new Object[]{"Sildenafil", "Nitrate", "HIGH", "Combined use causes severe hypotension; potentially fatal."},
            new Object[]{"Sildenafil", "Nitroglycerin", "HIGH", "Combined use causes severe hypotension; potentially fatal."},
            new Object[]{"Tramadol", "SSRI", "HIGH", "Combined serotonergic effect; serotonin syndrome risk."},
            new Object[]{"Tramadol", "Sertraline", "HIGH", "Combined serotonergic effect; serotonin syndrome risk."},
            new Object[]{"Tramadol", "Fluoxetine", "HIGH", "Combined serotonergic effect; serotonin syndrome risk."},
            new Object[]{"MAOIs", "Tramadol", "HIGH", "Potentially fatal serotonin syndrome."},
            new Object[]{"Gentamicin", "Furosemide", "HIGH", "Combined ototoxicity risk; hearing loss may be irreversible."},
            new Object[]{"Metformin", "Contrast", "HIGH", "IV contrast dye combined with Metformin increases lactic acidosis risk; hold Metformin 48h before."},
            new Object[]{"Isoniazid", "Phenytoin", "MEDIUM", "Isoniazid inhibits Phenytoin metabolism; toxicity risk."},
            new Object[]{"Rifampicin", "Oral Contraceptive", "HIGH", "Rifampicin induces contraceptive metabolism; contraceptive failure possible."},
            new Object[]{"Clopidogrel", "Omeprazole", "MEDIUM", "Omeprazole reduces Clopidogrel activation via CYP2C19."},
            new Object[]{"Ciprofloxacin", "Theophylline", "HIGH", "Ciprofloxacin inhibits Theophylline metabolism; seizure risk."},
            new Object[]{"Spironolactone", "ACE Inhibitor", "MEDIUM", "Combined use increases risk of hyperkalemia."},
            new Object[]{"Potassium", "ACE Inhibitor", "MEDIUM", "ACE Inhibitors reduce renal potassium excretion; hyperkalemia risk."},
            new Object[]{"Haloperidol", "Metoclopramide", "MEDIUM", "Combined dopamine antagonism increases extrapyramidal side effects."},
            new Object[]{"Insulin", "Beta Blocker", "MEDIUM", "Beta Blockers mask hypoglycaemia symptoms; use with caution in diabetics."},
            new Object[]{"Metronidazole", "Alcohol", "HIGH", "Disulfiram-like reaction: flushing, vomiting, rapid heartbeat."}
        );
        List<DrugInteractionMaster> result = new ArrayList<>();
        for (Object[] r : raw) {
            DrugInteractionMaster dim = new DrugInteractionMaster();
            dim.setHospitalId(hospitalId);
            dim.setDrugAName((String) r[0]);
            dim.setDrugBName((String) r[1]);
            dim.setSeverity((String) r[2]);
            dim.setInteractionDescription((String) r[3]);
            dim.setIsActive(true);
            result.add(dim);
        }
        return result;
    }
}
