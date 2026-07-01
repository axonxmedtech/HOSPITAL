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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FluidService - Manages manual/derived fluid intake and output records,
 * computes fluid balance, auto-imports IV administrations, and screens for AKI alerts.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class FluidService {

    private static final Logger log = LoggerFactory.getLogger(FluidService.class);

    @Autowired
    private FluidIntakeRepository intakeRepository;

    @Autowired
    private FluidOutputRepository outputRepository;

    @Autowired
    private DailyFluidBalanceRepository balanceRepository;

    @Autowired
    private FluidMasterRepository masterRepository;

    @Autowired
    private NurseTaskRepository nurseTaskRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private CdssEvaluationService cdssService;

    /**
     * Records a manual fluid intake entry (e.g. Oral or NG Tube).
     */
    @Transactional
    public FluidIntake recordIntake(Long admissionId, String type, Integer volumeMl, String description) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        // BR-1: Validate volume greater than 0
        if (volumeMl == null || volumeMl <= 0) {
            throw new IllegalArgumentException("Fluid volume must be positive");
        }

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found"));

        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        // BR-2: Validate fluid type against master list or standard set
        validateFluidType(hospitalId, type, "INTAKE");

        FluidIntake intake = new FluidIntake();
        intake.setHospitalId(hospitalId);
        intake.setPatientId(admission.getPatientId());
        intake.setAdmissionId(admissionId);
        intake.setType(type);
        intake.setVolumeMl(volumeMl);
        intake.setDescription(description);
        intake.setRecordedTime(LocalDateTime.now());
        intake.setRecordedBy(securityHelper.getCurrentUserId());

        FluidIntake saved = intakeRepository.save(intake);

        // Update daily balance cache
        updateDailyBalance(hospitalId, admission.getPatientId(), admissionId, LocalDate.now());

        return saved;
    }

    /**
     * Records a manual fluid output entry (e.g. Urine, Drain, Vomit).
     */
    @Transactional
    public FluidOutput recordOutput(Long admissionId, String type, Integer volumeMl, String color, String description) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in security context");
        }

        // BR-1: Validate volume greater than 0
        if (volumeMl == null || volumeMl <= 0) {
            throw new IllegalArgumentException("Fluid volume must be positive");
        }

        // BR-3: Output guardrails check (e.g., urine single output cannot exceed 3000ml in one log)
        if (volumeMl > 3000) {
            throw new IllegalArgumentException("Output volume exceeds plausible safety limits. Please confirm entry.");
        }

        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found"));

        if (!admission.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Access Denied: Tenant mismatch");
        }

        // BR-2: Validate fluid type against master list or standard set
        validateFluidType(hospitalId, type, "OUTPUT");

        FluidOutput output = new FluidOutput();
        output.setHospitalId(hospitalId);
        output.setPatientId(admission.getPatientId());
        output.setAdmissionId(admissionId);
        output.setType(type);
        output.setVolumeMl(volumeMl);
        output.setColor(color);
        output.setDescription(description);
        output.setRecordedTime(LocalDateTime.now());
        output.setRecordedBy(securityHelper.getCurrentUserId());

        FluidOutput saved = outputRepository.save(output);

        // Update daily balance cache
        updateDailyBalance(hospitalId, admission.getPatientId(), admissionId, LocalDate.now());

        // Evaluate AKI/overload alerts under CDSS
        cdssService.evaluateFluidBalance(admissionId);

        return saved;
    }

    /**
     * Auto-imports fluid administrations from completed IV NurseTasks (BR-7).
     */
    @Transactional
    public void deriveIvIntakes(Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        IpdAdmission admission = ipdAdmissionRepository.findById(admissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Admission not found"));

        // Fetch completed IV administration tasks
        List<NurseTask> tasks = nurseTaskRepository.findByIpdAdmissionIdAndStatus(admissionId, "DONE");
        for (NurseTask task : tasks) {
            if ("DOCTOR_ORDER".equals(task.getSource()) && task.getAdministeredQuantity() != null && task.getAdministeredQuantity() > 0) {
                // Check if already derived
                boolean alreadyImported = intakeRepository.existsByHospitalIdAndSourceRef(hospitalId, task.getId());
                if (!alreadyImported) {
                    FluidIntake derived = new FluidIntake();
                    derived.setHospitalId(hospitalId);
                    derived.setPatientId(admission.getPatientId());
                    derived.setAdmissionId(admissionId);
                    derived.setType("IV");
                    derived.setSourceRef(task.getId());
                    derived.setVolumeMl(task.getAdministeredQuantity().intValue());
                    derived.setDescription("Derived from MAR task: " + task.getTaskType());
                    derived.setRecordedTime(task.getExecutedAt() != null ? task.getExecutedAt() : LocalDateTime.now());
                    derived.setRecordedBy(task.getExecutedBy() != null ? task.getExecutedBy() : 1L);
                    intakeRepository.save(derived);

                    updateDailyBalance(hospitalId, admission.getPatientId(), admissionId, derived.getRecordedTime().toLocalDate());
                }
            }
        }
    }

    /**
     * Gets the daily balance cache for a given admission.
     */
    @Transactional
    public DailyFluidBalance getFluidBalance(Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        deriveIvIntakes(admissionId);

        LocalDate today = LocalDate.now();
        return balanceRepository.findByAdmissionIdAndBalanceDate(admissionId, today)
                .orElseGet(() -> {
                    IpdAdmission admission = ipdAdmissionRepository.findById(admissionId).orElse(null);
                    if (admission == null) return new DailyFluidBalance();
                    return updateDailyBalance(hospitalId, admission.getPatientId(), admissionId, today);
                });
    }

    /**
     * Returns intake/output trends over the last 7 days.
     */
    @Transactional(readOnly = true)
    public List<DailyFluidBalance> getFluidTrends(Long admissionId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return balanceRepository.findByHospitalIdAndAdmissionIdOrderByBalanceDateAsc(hospitalId, admissionId);
    }

    private DailyFluidBalance updateDailyBalance(Long hospitalId, Long patientId, Long admissionId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<FluidIntake> intakes = intakeRepository.findByAdmissionIdAndRecordedTimeBetween(admissionId, startOfDay, endOfDay);
        List<FluidOutput> outputs = outputRepository.findByAdmissionIdAndRecordedTimeBetween(admissionId, startOfDay, endOfDay);

        int totalIntake = intakes.stream().mapToInt(FluidIntake::getVolumeMl).sum();
        int totalOutput = outputs.stream().mapToInt(FluidOutput::getVolumeMl).sum();

        DailyFluidBalance balance = balanceRepository.findByAdmissionIdAndBalanceDate(admissionId, date)
                .orElseGet(() -> {
                    DailyFluidBalance dfb = new DailyFluidBalance();
                    dfb.setHospitalId(hospitalId);
                    dfb.setPatientId(patientId);
                    dfb.setAdmissionId(admissionId);
                    dfb.setBalanceDate(date);
                    return dfb;
                });

        balance.setTotalIntake(totalIntake);
        balance.setTotalOutput(totalOutput);
        return balanceRepository.save(balance);
    }

    private void validateFluidType(Long hospitalId, String type, String direction) {
        // Enforce types from fluid_master or defaults if master is empty
        long count = masterRepository.countByHospitalId(hospitalId);
        if (count == 0) {
            // Allow standard types by default
            if ("INTAKE".equals(direction)) {
                if (List.of("ORAL", "IV", "TUBE", "BLOOD").contains(type.toUpperCase())) return;
            } else {
                if (List.of("URINE", "STOOL", "VOMIT", "DRAIN", "DIALYSIS").contains(type.toUpperCase())) return;
            }
            throw new IllegalArgumentException("Invalid fluid type: " + type);
        }

        boolean exists = masterRepository.existsByHospitalIdAndCategoryAndName(
                hospitalId, direction, type
        );
        if (!exists) {
            throw new IllegalArgumentException("Fluid type " + type + " must be pre-configured in fluid_master");
        }
    }
}
