package com.hms.service.hospital;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RiskAssessmentService - Manages vulnerability risk assessments, scoring, EMR updates,
 * safety tasks scheduling, and auto-referrals.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class RiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentService.class);

    @Autowired
    private PatientRiskAssessmentRepository riskRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private NurseTaskRepository nurseTaskRepository;

    @Autowired
    private DoctorOrderRepository doctorOrderRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private NurseAssessmentRepository nurseAssessmentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates and computes a new patient risk assessment.
     */
    @Transactional
    public PatientRiskAssessment evaluateAndSaveRisk(Long patientId, Long admissionId, String inputsJson, String remarks) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        // Verify patient and admission tenant scope
        Patient patient = patientRepository.findByIdAndHospitalIdAndIsActiveTrue(patientId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission record not found"));

        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        // Compute version
        List<PatientRiskAssessment> existing = riskRepository
                .findByHospitalIdAndAdmissionIdOrderByCreatedAtDesc(hospitalId, admissionId);
        int version = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;
        Long parentId = existing.isEmpty() ? null : existing.get(0).getId();

        // Perform server-side rule calculation
        ComputedRisk computed = computeVulnerabilityRisk(inputsJson);

        PatientRiskAssessment risk = new PatientRiskAssessment();
        risk.setHospitalId(hospitalId);
        risk.setPatientId(patientId);
        risk.setAdmissionId(admissionId);
        risk.setScaleType("VULNERABILITY");
        risk.setFallRisk(computed.fallRisk);
        risk.setPressureUlcerRisk(computed.pressureRisk);
        risk.setNutritionRisk(computed.nutritionRisk);
        risk.setMentalStatus(computed.mentalStatus);
        risk.setMobilityStatus(computed.mobilityStatus);
        risk.setInfectionRisk(computed.infectionRisk);
        risk.setAllergyRisk(computed.allergyRisk);
        risk.setIsolationRequired(computed.isolationRequired);
        risk.setOverallRisk(computed.overallRisk);
        risk.setInputsJson(inputsJson);
        risk.setStatus("COMPLETED");
        risk.setVersion(version);
        risk.setParentId(parentId);
        risk.setAssessedBy(securityHelper.getCurrentUserId());
        risk.setCreatedAt(LocalDateTime.now());

        PatientRiskAssessment saved = riskRepository.save(risk);

        // Sync to NurseAssessment cached mirror if it exists
        Optional<NurseAssessment> nurseAssessOpt = nurseAssessmentRepository.findByIpdAdmissionId(admissionId);
        if (nurseAssessOpt.isPresent()) {
            NurseAssessment na = nurseAssessOpt.get();
            na.setFallRisk(saved.getFallRisk());
            nurseAssessmentRepository.save(na);
        }

        // Schedule safety tasks based on risk findings (BR-2, BR-3)
        scheduleSafetyTasks(saved);

        // Generate auto-referrals (Dietician/Physiotherapist) (BR-4, BR-5)
        generateAutoReferrals(saved, admission);

        // Broadcast alert status across Doctor & Nurse dashboards
        notificationService.sendWebSocketRefresh(hospitalId, "PATIENT_RISK_COMPLETED", saved.getId());

        log.info("Saved risk assessment ID: {} (Overall: {}) for admission ID: {}", saved.getId(), saved.getOverallRisk(), admissionId);
        return saved;
    }

    /**
     * Signs off review comments by a doctor for high risk findings.
     */
    @Transactional
    public PatientRiskAssessment reviewRiskAssessment(Long id, String reviewRemarks) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PatientRiskAssessment risk = riskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk assessment record not found"));

        if (!risk.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        // Verify context user has DOCTOR role
        Doctor doctor = doctorRepository.findByHospitalIdAndUserId(hospitalId, securityHelper.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Only authorized doctors can sign off risk reviews"));

        risk.setReviewedBy(doctor.getId());
        risk.setStatus("REVIEWED");

        log.info("Doctor ID: {} reviewed risk assessment ID: {}", doctor.getId(), id);
        return riskRepository.save(risk);
    }

    @Transactional(readOnly = true)
    public List<PatientRiskAssessment> getAssessmentsForPatient(Long patientId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return riskRepository.findByHospitalIdAndPatientId(hospitalId, patientId);
    }

    @Transactional(readOnly = true)
    public List<PatientRiskAssessment> getAssessmentsForAdmission(Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return riskRepository.findByHospitalIdAndAdmissionIdOrderByCreatedAtDesc(hospitalId, admissionId);
    }

    private void scheduleSafetyTasks(PatientRiskAssessment risk) {
        // High Fall Risk Protocols
        if ("HIGH".equals(risk.getFallRisk())) {
            createNurseTask(risk, "Apply Fall Band", "Patient is at high fall risk. Apply colorful fall warning band.");
            createNurseTask(risk, "Ensure bed rails are up", "Verify all safety side bed rails are secured.");
            createNurseTask(risk, "Hourly rounding (Fall Prevention)", "Execute hourly check-ins on patient.");
        }

        // High Pressure Ulcer turning reminder
        if ("HIGH".equals(risk.getPressureUlcerRisk())) {
            createNurseTask(risk, "2-hourly turning and skin integrity check", "Turn patient and log skin checks.");
            createNurseTask(risk, "Apply skin barrier cream", "Apply barrier cream to high pressure zones.");
        }
    }

    private void createNurseTask(PatientRiskAssessment risk, String taskType, String notes) {
        NurseTask task = new NurseTask();
        task.setHospitalId(risk.getHospitalId());
        task.setIpdAdmissionId(risk.getAdmissionId());
        task.setSource("RISK_PROTOCOL");
        task.setTaskType(taskType);
        task.setNotes(notes);
        task.setScheduledAt(LocalDateTime.now());
        task.setStatus("PENDING");
        nurseTaskRepository.save(task);
    }

    private void generateAutoReferrals(PatientRiskAssessment risk, IpdAdmission admission) {
        // High nutrition risk -> Dietician referral
        if ("HIGH".equals(risk.getNutritionRisk())) {
            createReferralOrder(risk.getHospitalId(), admission.getId(), "Dietician Referral", "High nutritional alert triggered.");
        }

        // Bedridden or High Pressure Ulcer -> Physiotherapist referral
        if ("BEDRIDDEN".equalsIgnoreCase(risk.getMobilityStatus()) || "HIGH".equals(risk.getPressureUlcerRisk())) {
            createReferralOrder(risk.getHospitalId(), admission.getId(), "Physiotherapy Referral", "High immobility referral alert.");
        }
    }

    private void createReferralOrder(Long hospitalId, Long admissionId, String description, String notes) {
        DoctorOrder order = new DoctorOrder();
        order.setHospitalId(hospitalId);
        order.setIpdAdmissionId(admissionId);
        order.setOrderType("REFERRAL");
        order.setDescription(description);
        order.setNotes(notes);
        order.setStatus("ACTIVE");
        doctorOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getRiskDashboard() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        List<IpdAdmission> activeAdmissions = ipdAdmissionRepository.findByHospitalIdAndStatus(hospitalId, "ADMITTED");

        long highFallRisk = 0;
        long highPressureUlcerRisk = 0;
        long highNutritionRisk = 0;
        long isolationPatients = 0;
        long awaitingAssessment = 0;
        long assessmentOverdue = 0;

        LocalDateTime now = LocalDateTime.now();

        for (IpdAdmission adm : activeAdmissions) {
            List<PatientRiskAssessment> risks = riskRepository
                    .findByHospitalIdAndAdmissionIdOrderByCreatedAtDesc(hospitalId, adm.getId());

            if (risks.isEmpty()) {
                if (adm.getAdmissionDatetime().plusHours(24).isBefore(now)) {
                    assessmentOverdue++;
                } else {
                    awaitingAssessment++;
                }
            } else {
                PatientRiskAssessment latest = risks.get(0);
                if ("HIGH".equals(latest.getFallRisk())) {
                    highFallRisk++;
                }
                if ("HIGH".equals(latest.getPressureUlcerRisk())) {
                    highPressureUlcerRisk++;
                }
                if ("HIGH".equals(latest.getNutritionRisk())) {
                    highNutritionRisk++;
                }
                if (latest.getIsolationRequired() != null && latest.getIsolationRequired()) {
                    isolationPatients++;
                }
            }
        }

        java.util.Map<String, Object> dashboard = new java.util.HashMap<>();
        dashboard.put("highFallRisk", highFallRisk);
        dashboard.put("highPressureUlcerRisk", highPressureUlcerRisk);
        dashboard.put("highNutritionRisk", highNutritionRisk);
        dashboard.put("isolationPatients", isolationPatients);
        dashboard.put("awaitingAssessment", awaitingAssessment);
        dashboard.put("assessmentOverdue", assessmentOverdue);
        return dashboard;
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getHighRiskPatients() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        List<IpdAdmission> activeAdmissions = ipdAdmissionRepository.findByHospitalIdAndStatus(hospitalId, "ADMITTED");
        List<java.util.Map<String, Object>> highRiskList = new java.util.ArrayList<>();

        for (IpdAdmission adm : activeAdmissions) {
            List<PatientRiskAssessment> risks = riskRepository
                    .findByHospitalIdAndAdmissionIdOrderByCreatedAtDesc(hospitalId, adm.getId());
            if (!risks.isEmpty()) {
                PatientRiskAssessment latest = risks.get(0);
                if ("HIGH".equals(latest.getOverallRisk()) || "MED".equals(latest.getOverallRisk())) {
                    Optional<Patient> pOpt = patientRepository.findByIdAndHospitalIdAndIsActiveTrue(adm.getPatientId(), hospitalId);
                    String patientName = pOpt.isPresent() ? pOpt.get().getName() : "Unknown";

                    String wardName = "General Ward";
                    String bedName = "Bed " + adm.getBedId();
                    Optional<Ward> wOpt = wardRepository.findById(adm.getWardId());
                    if (wOpt.isPresent()) {
                        wardName = wOpt.get().getName();
                    }
                    
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", latest.getId());
                    map.put("admissionId", adm.getId());
                    map.put("patientId", adm.getPatientId());
                    map.put("patientName", patientName);
                    map.put("ipdNumber", adm.getIpdNumber());
                    map.put("wardName", wardName);
                    map.put("bedName", bedName);
                    map.put("overallRisk", latest.getOverallRisk());
                    map.put("fallRisk", latest.getFallRisk());
                    map.put("pressureUlcerRisk", latest.getPressureUlcerRisk());
                    map.put("nutritionRisk", latest.getNutritionRisk());
                    map.put("isolationRequired", latest.getIsolationRequired());
                    map.put("assessedAt", latest.getCreatedAt());
                    highRiskList.add(map);
                }
            }
        }
        return highRiskList;
    }

    private ComputedRisk computeVulnerabilityRisk(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            // 1. Fall Risk
            int age = node.has("age") ? node.get("age").asInt() : 0;
            boolean prevFall = node.has("previous_fall") && node.get("previous_fall").asBoolean();
            boolean walkingAid = node.has("walking_aid") && node.get("walking_aid").asBoolean();
            boolean sedation = node.has("sedation") && node.get("sedation").asBoolean();
            boolean dizziness = node.has("dizziness") && node.get("dizziness").asBoolean();
            boolean weakness = node.has("weakness") && node.get("weakness").asBoolean();

            String fallRisk = "LOW";
            if (age > 75 && (prevFall || walkingAid || sedation || dizziness || weakness)) {
                fallRisk = "HIGH";
            } else if (prevFall || walkingAid || sedation || dizziness || weakness || age > 65) {
                fallRisk = "MED";
            }

            // 2. Pressure Ulcer Risk
            boolean bedridden = node.has("bedridden") && node.get("bedridden").asBoolean();
            boolean limitedMobility = node.has("limited_mobility") && node.get("limited_mobility").asBoolean();
            boolean incontinence = node.has("incontinence") && node.get("incontinence").asBoolean();
            boolean poorNutrition = node.has("poor_nutrition") && node.get("poor_nutrition").asBoolean();
            boolean existingDamage = node.has("existing_skin_damage") && node.get("existing_skin_damage").asBoolean();

            String pressureRisk = "LOW";
            if (bedridden || (limitedMobility && incontinence)) {
                pressureRisk = "HIGH";
            } else if (limitedMobility || incontinence || poorNutrition || existingDamage) {
                pressureRisk = "MED";
            }

            // 3. Nutrition Risk
            boolean appetiteLoss = node.has("appetite_loss") && node.get("appetite_loss").asBoolean();
            boolean weightLoss = node.has("weight_loss") && node.get("weight_loss").asBoolean();
            boolean swallowingDiff = node.has("swallowing_difficulty") && node.get("swallowing_difficulty").asBoolean();
            boolean tubeFeeding = node.has("tube_feeding") && node.get("tube_feeding").asBoolean();

            String nutritionRisk = "LOW";
            if (tubeFeeding || swallowingDiff || weightLoss) {
                nutritionRisk = "HIGH";
            } else if (appetiteLoss) {
                nutritionRisk = "MED";
            }

            // Enums
            String mentalStatus = node.has("mental_status") ? node.get("mental_status").asText() : "CONSCIOUS";
            String mobilityStatus = node.has("mobility_status") ? node.get("mobility_status").asText() : "INDEPENDENT";

            // Special precautions flags
            boolean infection = node.has("infection_risk") && node.get("infection_risk").asBoolean();
            boolean allergy = node.has("allergy_risk") && node.get("allergy_risk").asBoolean();
            boolean isolation = node.has("isolation_required") && node.get("isolation_required").asBoolean();

            // Overall score calculation
            String overall = "LOW";
            if ("HIGH".equals(fallRisk) || "HIGH".equals(pressureRisk) || "HIGH".equals(nutritionRisk)) {
                overall = "HIGH";
            } else if ("MED".equals(fallRisk) || "MED".equals(pressureRisk) || "MED".equals(nutritionRisk)) {
                overall = "MED";
            }

            return new ComputedRisk(fallRisk, pressureRisk, nutritionRisk, mentalStatus, mobilityStatus, infection, allergy, isolation, overall);
        } catch (Exception e) {
            log.error("Failed to parse risk inputs JSON", e);
            throw new IllegalArgumentException("Invalid risk inputs JSON format");
        }
    }

    private static class ComputedRisk {
        String fallRisk;
        String pressureRisk;
        String nutritionRisk;
        String mentalStatus;
        String mobilityStatus;
        boolean infectionRisk;
        boolean allergyRisk;
        boolean isolationRequired;
        String overallRisk;

        ComputedRisk(String fallRisk, String pressureRisk, String nutritionRisk, String mentalStatus, String mobilityStatus,
                     boolean infectionRisk, boolean allergyRisk, boolean isolationRequired, String overallRisk) {
            this.fallRisk = fallRisk;
            this.pressureRisk = pressureRisk;
            this.nutritionRisk = nutritionRisk;
            this.mentalStatus = mentalStatus;
            this.mobilityStatus = mobilityStatus;
            this.infectionRisk = infectionRisk;
            this.allergyRisk = allergyRisk;
            this.isolationRequired = isolationRequired;
            this.overallRisk = overallRisk;
        }
    }
}
