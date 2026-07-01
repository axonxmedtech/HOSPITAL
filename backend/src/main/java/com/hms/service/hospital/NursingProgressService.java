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
import java.util.List;
import java.util.Optional;

/**
 * NursingProgressService - Handles shift progress notes, non-medication procedure logs,
 * shift handovers, and role-based validation rules.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class NursingProgressService {

    private static final Logger log = LoggerFactory.getLogger(NursingProgressService.class);

    @Autowired
    private NursingProgressNoteRepository progressNoteRepository;

    @Autowired
    private NursingProcedureRepository procedureRepository;

    @Autowired
    private ShiftHandoverRepository handoverRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    /**
     * Creates a new shift progress note in DRAFT status. MORNING/EVENING/NIGHT.
     */
    @Transactional
    public NursingProgressNote createProgressNote(Long admissionId, String shift, String generalCondition,
                                                Integer painScore, String remarks, Boolean doctorNotified,
                                                String doctorName, String doctorAdvice, String patientResponse) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        // Validate shift enum value
        if (shift == null || (!"MORNING".equalsIgnoreCase(shift) && !"EVENING".equalsIgnoreCase(shift) && !"NIGHT".equalsIgnoreCase(shift))) {
            throw new IllegalArgumentException("Shift must be MORNING, EVENING, or NIGHT");
        }

        // Validate pain score limits (0-10)
        if (painScore != null && (painScore < 0 || painScore > 10)) {
            throw new IllegalArgumentException("Pain score must be between 0 and 10");
        }

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found"));

        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        // BR-1: Check if note already exists for this admission and shift to avoid duplication
        Optional<NursingProgressNote> existing = progressNoteRepository.findByHospitalIdAndAdmissionIdAndShift(hospitalId, admissionId, shift);
        if (existing.isPresent()) {
            return existing.get();
        }

        NursingProgressNote note = new NursingProgressNote();
        note.setHospitalId(hospitalId);
        note.setPatientId(admission.getPatientId());
        note.setAdmissionId(admissionId);
        note.setShift(shift.toUpperCase());
        note.setNurseId(securityHelper.getCurrentUserId());
        note.setGeneralCondition(generalCondition);
        note.setPainScore(painScore);
        note.setRemarks(remarks);
        note.setDoctorNotified(doctorNotified != null && doctorNotified);
        note.setDoctorName(doctorName);
        note.setDoctorAdvice(doctorAdvice);
        note.setPatientResponse(patientResponse);
        note.setStatus("DRAFT");

        NursingProgressNote saved = progressNoteRepository.save(note);
        log.info("Created nursing progress note ID: {} for shift: {}", saved.getId(), shift);
        return saved;
    }

    /**
     * Updates an existing shift progress note.
     */
    @Transactional
    public NursingProgressNote updateProgressNote(Long id, String generalCondition, Integer painScore,
                                                String remarks, Boolean doctorNotified, String doctorName,
                                                String doctorAdvice, String patientResponse, String status) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        NursingProgressNote note = progressNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Progress note not found"));

        if (!note.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        // BR-7: Edit only the DRAFT note
        if (!"DRAFT".equals(note.getStatus())) {
            throw new IllegalArgumentException("Only draft notes can be updated");
        }

        if (painScore != null && (painScore < 0 || painScore > 10)) {
            throw new IllegalArgumentException("Pain score must be between 0 and 10");
        }

        note.setGeneralCondition(generalCondition);
        note.setPainScore(painScore);
        note.setRemarks(remarks);
        note.setDoctorNotified(doctorNotified != null && doctorNotified);
        note.setDoctorName(doctorName);
        note.setDoctorAdvice(doctorAdvice);
        note.setPatientResponse(patientResponse);

        if ("SUBMITTED".equalsIgnoreCase(status)) {
            note.setStatus("SUBMITTED");
        }

        return progressNoteRepository.save(note);
    }

    /**
     * Records a non-medication nursing procedure during a shift.
     */
    @Transactional
    public NursingProcedure recordProcedure(Long progressNoteId, String procedureName, String remarks) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        NursingProgressNote note = progressNoteRepository.findById(progressNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Progress note not found"));

        if (!note.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        NursingProcedure proc = new NursingProcedure();
        proc.setProgressNoteId(progressNoteId);
        proc.setHospitalId(hospitalId);
        proc.setProcedureName(procedureName);
        proc.setPerformedBy(securityHelper.getCurrentUserId());
        proc.setPerformedTime(LocalDateTime.now());
        proc.setRemarks(remarks);

        return procedureRepository.save(proc);
    }

    /**
     * Executes the handover of shift observations to the next shift nurse.
     */
    @Transactional
    public ShiftHandover recordHandover(Long admissionId, String shift, Long incomingNurseId,
                                       String pendingTasks, String criticalAlerts, String medsDue,
                                       String investigationsPending, Boolean doctorReviewPending) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found"));

        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        ShiftHandover handover = new ShiftHandover();
        handover.setHospitalId(hospitalId);
        handover.setAdmissionId(admissionId);
        handover.setShift(shift);
        handover.setOutgoingNurseId(securityHelper.getCurrentUserId());
        handover.setIncomingNurseId(incomingNurseId);
        handover.setPendingTasks(pendingTasks);
        handover.setCriticalAlerts(criticalAlerts);
        handover.setMedsDue(medsDue);
        handover.setInvestigationsPending(investigationsPending);
        handover.setDoctorReviewPending(doctorReviewPending != null && doctorReviewPending);

        return handoverRepository.save(handover);
    }

    @Transactional(readOnly = true)
    public List<NursingProgressNote> getNotesForPatient(Long patientId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return progressNoteRepository.findByHospitalIdAndPatientId(hospitalId, patientId);
    }

    @Transactional(readOnly = true)
    public List<NursingProcedure> getProceduresForNote(Long progressNoteId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return procedureRepository.findByHospitalIdAndProgressNoteId(hospitalId, progressNoteId);
    }
}
