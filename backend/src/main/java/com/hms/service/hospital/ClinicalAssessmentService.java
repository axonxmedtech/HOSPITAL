package com.hms.service.hospital;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * ClinicalAssessmentService - Handles EMR initial assessments, amendment history chains,
 * vitals imports, downstream doctor order spawning, and longitudinal history.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class ClinicalAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalAssessmentService.class);

    @Autowired
    private ClinicalAssessmentRepository clinicalAssessmentRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private VitalSignsRepository vitalSignsRepository;

    @Autowired
    private DoctorOrderRepository doctorOrderRepository;

    @Autowired
    private PatientDiagnosisRepository patientDiagnosisRepository;

    @Autowired
    private PatientMedicalHistoryRepository patientMedicalHistoryRepository;

    @Autowired
    private PatientSurgicalHistoryRepository patientSurgicalHistoryRepository;

    @Autowired
    private PatientMedicationHistoryRepository patientMedicationHistoryRepository;

    @Autowired
    private PatientFamilyHistoryRepository patientFamilyHistoryRepository;

    @Autowired
    private PatientSocialHistoryRepository patientSocialHistoryRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    /**
     * Creates a new ClinicalAssessment draft for an admission.
     * Pre-populates the assessment with vitals (BR-8) and longitudinal details.
     */
    @Transactional
    public ClinicalAssessment createDraft(Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission record not found"));

        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        // BR-1: Assert only one active/draft assessment per admission
        Optional<ClinicalAssessment> existing = clinicalAssessmentRepository
                .findFirstByHospitalIdAndAdmissionIdAndStatusNotInOrderByVersionDesc(
                        hospitalId, admissionId, Arrays.asList("AMENDED", "ARCHIVED")
                );
        if (existing.isPresent()) {
            return existing.get(); // Idempotent draft check
        }

        // Fetch current doctor (from logged-in user context)
        Doctor doctor = doctorRepository.findByHospitalIdAndUserId(hospitalId, securityHelper.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user context"));

        // Initialize new assessment draft
        ClinicalAssessment assessment = new ClinicalAssessment();
        assessment.setHospitalId(hospitalId);
        assessment.setPatientId(admission.getPatientId());
        assessment.setAdmissionId(admissionId);
        assessment.setDoctorId(doctor.getId());
        assessment.setStatus("DRAFT");
        assessment.setVersion(1);

        // Pre-populate latest vitals as starter notes in chief complaint or illness narrative
        List<VitalSigns> vitalsList = vitalSignsRepository.findByIpdAdmissionIdOrderByRecordedAtDesc(admissionId);
        StringBuilder textPrep = new StringBuilder();
        if (!vitalsList.isEmpty()) {
            VitalSigns v = vitalsList.get(0);
            textPrep.append("Latest Vitals: Temp: ").append(v.getTemperature())
                    .append(" C, Pulse: ").append(v.getPulse())
                    .append(" bpm, BP: ").append(v.getBloodPressure())
                    .append(", SpO2: ").append(v.getSpo2()).append("%");
        }
        assessment.setChiefComplaint(textPrep.toString());
        assessment.setHistoryPresentIllness("");
        assessment.setProvisionalDiagnosis("");
        assessment.setTreatmentPlan("");

        ClinicalAssessment saved = clinicalAssessmentRepository.save(assessment);
        log.info("Initialized clinical assessment draft ID: {} for admission ID: {}", saved.getId(), admissionId);
        return saved;
    }

    /**
     * Updates details of a draft assessment.
     */
    @Transactional
    public ClinicalAssessment updateDraft(Long id, String chiefComplaint, String historyPresentIllness,
                                          String provisionalDiagnosis, String treatmentPlan) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ClinicalAssessment assessment = clinicalAssessmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical assessment not found"));

        if (!assessment.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        if (!"DRAFT".equals(assessment.getStatus())) {
            throw new IllegalArgumentException("Cannot update a finalized or amended assessment");
        }

        assessment.setChiefComplaint(chiefComplaint);
        assessment.setHistoryPresentIllness(historyPresentIllness);
        assessment.setProvisionalDiagnosis(provisionalDiagnosis);
        assessment.setTreatmentPlan(treatmentPlan);

        return clinicalAssessmentRepository.save(assessment);
    }

    /**
     * Finalizes the clinical assessment and saves structured longitudinal items and doctor orders.
     */
    @Transactional
    public ClinicalAssessment finalizeAssessment(Long id, List<PatientMedicalHistory> medHistory,
                                                  List<PatientSurgicalHistory> surgHistory,
                                                  List<PatientMedicationHistory> medInstructions,
                                                  List<PatientFamilyHistory> famHistory,
                                                  PatientSocialHistory socHistory,
                                                  List<DoctorOrder> spawnedOrders) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ClinicalAssessment assessment = clinicalAssessmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical assessment not found"));

        if (!assessment.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        if (!"DRAFT".equals(assessment.getStatus())) {
            throw new IllegalArgumentException("Assessment is already finalized");
        }

        // BR-2: Only assigned doctor/consultant can finalize
        IpdAdmission admission = ipdAdmissionRepository.findById(assessment.getAdmissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found"));
        Doctor doctor = doctorRepository.findByHospitalIdAndUserId(hospitalId, securityHelper.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));

        if (!admission.getDoctorId().equals(doctor.getId())) {
            throw new UnauthorizedException("Only the admitting doctor/consultant is authorized to finalize this assessment");
        }

        // Save longitudinal EMR items (BR-7)
        if (medHistory != null) {
            for (PatientMedicalHistory item : medHistory) {
                item.setHospitalId(hospitalId);
                item.setPatientId(assessment.getPatientId());
                patientMedicalHistoryRepository.save(item);
            }
        }
        if (surgHistory != null) {
            for (PatientSurgicalHistory item : surgHistory) {
                item.setHospitalId(hospitalId);
                item.setPatientId(assessment.getPatientId());
                patientSurgicalHistoryRepository.save(item);
            }
        }
        if (medInstructions != null) {
            for (PatientMedicationHistory item : medInstructions) {
                item.setHospitalId(hospitalId);
                item.setPatientId(assessment.getPatientId());
                patientMedicationHistoryRepository.save(item);
            }
        }
        if (famHistory != null) {
            for (PatientFamilyHistory item : famHistory) {
                item.setHospitalId(hospitalId);
                item.setPatientId(assessment.getPatientId());
                patientFamilyHistoryRepository.save(item);
            }
        }
        if (socHistory != null) {
            socHistory.setHospitalId(hospitalId);
            socHistory.setPatientId(assessment.getPatientId());
            patientSocialHistoryRepository.save(socHistory);
        }

        // Legacy Patient.medical_history backfill reconciliation
        Patient patient = patientRepository.findByIdAndHospitalIdAndIsActiveTrue(assessment.getPatientId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        if (patient.getMedicalHistory() != null && !patient.getMedicalHistory().isBlank()) {
            String legacy = patient.getMedicalHistory();
            String[] items = legacy.split("[,;\\n]");
            for (String splitItem : items) {
                String clean = splitItem.trim();
                if (!clean.isEmpty()) {
                    PatientMedicalHistory item = new PatientMedicalHistory();
                    item.setHospitalId(hospitalId);
                    item.setPatientId(patient.getId());
                    item.setConditionName(clean);
                    item.setIsActive(true);
                    patientMedicalHistoryRepository.save(item);
                }
            }
            // Clear legacy string to signify successful reconciliation/migration
            patient.setMedicalHistory(null);
            patientRepository.save(patient);
        }

        // Write Provisional Diagnosis to timeline history (BR-6)
        if (assessment.getProvisionalDiagnosis() != null && !assessment.getProvisionalDiagnosis().isBlank()) {
            PatientDiagnosis diag = new PatientDiagnosis();
            diag.setHospitalId(hospitalId);
            diag.setPatientId(assessment.getPatientId());
            diag.setAdmissionId(assessment.getAdmissionId());
            diag.setDiagnosisDescription(assessment.getProvisionalDiagnosis());
            diag.setDiagnosisType("PROVISIONAL");
            diag.setRecordedBy(doctor.getId());
            patientDiagnosisRepository.save(diag);
        }

        // Spawn downstream doctor orders (BR-5)
        if (spawnedOrders != null) {
            for (DoctorOrder order : spawnedOrders) {
                order.setHospitalId(hospitalId);
                order.setIpdAdmissionId(assessment.getAdmissionId());
                order.setStatus("ACTIVE");
                order.setCreatedBy(securityHelper.getCurrentUserId());
                order.setCreatedByName(doctor.getName());
                doctorOrderRepository.save(order);
            }
        }

        assessment.setStatus("FINALIZED");
        assessment.setFinalizedBy(doctor.getId());
        assessment.setFinalizedAt(LocalDateTime.now());

        return clinicalAssessmentRepository.save(assessment);
    }

    /**
     * Creates an amendment for a finalized assessment (BR-3 version incrementing).
     */
    @Transactional
    public ClinicalAssessment amendAssessment(Long id, String chiefComplaint, String HPI,
                                              String provisionalDiagnosis, String treatmentPlan) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        ClinicalAssessment parent = clinicalAssessmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical assessment not found"));

        if (!parent.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        if (!"FINALIZED".equals(parent.getStatus())) {
            throw new IllegalArgumentException("Only finalized assessments can be amended");
        }

        // Lock parent status as AMENDED
        parent.setStatus("AMENDED");
        clinicalAssessmentRepository.save(parent);

        // Fetch current doctor context
        Doctor doctor = doctorRepository.findByHospitalIdAndUserId(hospitalId, securityHelper.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found"));

        // Create versioned amendment assessment
        ClinicalAssessment amendment = new ClinicalAssessment();
        amendment.setHospitalId(hospitalId);
        amendment.setPatientId(parent.getPatientId());
        amendment.setAdmissionId(parent.getAdmissionId());
        amendment.setDoctorId(doctor.getId());
        amendment.setChiefComplaint(chiefComplaint);
        amendment.setHistoryPresentIllness(HPI);
        amendment.setProvisionalDiagnosis(provisionalDiagnosis);
        amendment.setTreatmentPlan(treatmentPlan);
        amendment.setStatus("DRAFT");
        amendment.setVersion(parent.getVersion() + 1);
        amendment.setParentId(parent.getId());

        ClinicalAssessment saved = clinicalAssessmentRepository.save(amendment);
        log.info("Created clinical assessment amendment version: {} for parent ID: {}", saved.getVersion(), parent.getId());
        return saved;
    }

    /**
     * Fetches the complete longitudinal history (D-I) timeline for a patient.
     */
    @Transactional(readOnly = true)
    public List<PatientDiagnosis> getPatientDiagnoses(Long patientId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return patientDiagnosisRepository.findByHospitalIdAndPatientIdOrderByRecordedAtDesc(hospitalId, patientId);
    }
}
