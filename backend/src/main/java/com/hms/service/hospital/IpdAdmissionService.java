package com.hms.service.hospital;

import com.hms.entity.Bed;
import com.hms.entity.Billing;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Opd;
import com.hms.repository.BedRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.OpdRepository;
import com.hms.security.SecurityContextHelper;

import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.exception.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class IpdAdmissionService {

    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String STATUS_ADMITTED = "ADMITTED";
    private static final String STATUS_DISCHARGE_PLANNED = "DISCHARGE_PLANNED";
    private static final String STATUS_DISCHARGED = "DISCHARGED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String ELECTIVE = "ELECTIVE";
    private static final String MODULE_BILLING = "BILLING";
    private static final String PATIENT_NOT_FOUND = "Patient not found";
    private static final String BALANCE = "balance";

    private static final Logger logger = LoggerFactory.getLogger(IpdAdmissionService.class);

    /** Admissions whose patient is still in a bed. */
    static final java.util.List<String> IN_HOSPITAL = java.util.List.of(STATUS_ADMITTED, STATUS_DISCHARGE_PLANNED);

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private com.hms.repository.PatientRepository patientRepository;

    // Nurse module (Phase 1): close active nurse assignments on discharge.
    @Autowired
    private com.hms.repository.PatientNurseAssignmentRepository patientNurseAssignmentRepository;

    // Nurse module: auto-assign the admitted patient to a ward nurse.
    @Autowired
    private NurseAssignmentService nurseAssignmentService;

    @Autowired
    private PatientAssignmentService patientAssignmentService;

    @Autowired
    private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.hms.repository.WardRepository wardRepository;

    @Autowired
    private OpdRepository opdRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private BillingRepository billingRepository;

    @Autowired
    private BillingService billingService;

    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private com.hms.repository.PrescriptionRepository prescriptionRepository;
    @Autowired
    private com.hms.repository.MedicineRepository medicineRepository;
    @Autowired
    private com.hms.repository.BillingItemRepository billingItemRepository;
    @Autowired
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;
    @Autowired
    private com.hms.repository.DischargeSummaryRepository dischargeSummaryRepository;
    @Autowired
    private com.hms.service.hospital.NotificationService notificationService;
    @Autowired
    private com.hms.repository.BillingPaymentRepository billingPaymentRepository;

    @Autowired
    private com.hms.repository.AppointmentRepository appointmentRepository;

    @Autowired
    private com.hms.repository.HospitalRepository hospitalRepository;

    @Autowired
    private com.hms.repository.IpdBedHistoryRepository ipdBedHistoryRepository;

    @Autowired
    private com.hms.repository.IcuWardRepository icuWardRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @Autowired
    private com.hms.repository.QueueEntryRepository queueEntryRepository;

    @Autowired
    private com.hms.repository.HospitalInventoryRepository hospitalInventoryRepository;

    @Autowired
    private HospitalInventoryService hospitalInventoryService;

    @Autowired
    private com.hms.repository.HospitalServiceRepository hospitalServiceRepository;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private com.hms.repository.IcuCleaningTaskRepository icuCleaningTaskRepository;

    // Nursing Mgmt Phase C2: all bed status writes go through the audited service.
    @Autowired
    private BedStatusService bedStatusService;

    /**
     * ICU Phase 3. MANDATORY propagation — it joins the transaction opened here and never starts
     * one, so an ICU stay can never commit apart from the bed movement that caused it.
     */
    @Autowired
    private com.hms.service.hospital.icu.IcuStayService icuStayService;

    @Autowired
    private IpdClinicalState ipdClinicalState;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private BillingSchedulerService billingSchedulerService;

    @Autowired
    private com.hms.repository.MedicationAdministrationRepository medicationAdministrationRepository;

    @Autowired
    private com.hms.service.pdf.ClinicalPdfService clinicalPdfService;

    @Autowired
    private IpdBillingSettingsService ipdBillingSettingsService;

    @Autowired
    private IpdPackageService ipdPackageService;

    @Autowired
    private IpdInsuranceService ipdInsuranceService;

    @Autowired
    private BedReservationService bedReservationService;

    @Autowired
    private com.hms.service.pdf.BillingPdfService billingPdfService;

    @Transactional
    public IpdAdmission admitFromOpd(Long opdId, Long wardId, Long bedId, String admissionType, String primaryDiagnosis) {
        return admitFromOpd(opdId, null, null, wardId, bedId, admissionType, primaryDiagnosis);
    }

    @Transactional
    public IpdAdmission admitFromOpd(Long opdId, Long directPatientId, Long directDoctorId, Long wardId, Long bedId, String admissionType, String primaryDiagnosis) {
        com.hms.dto.CreateIpdAdmissionRequest req = new com.hms.dto.CreateIpdAdmissionRequest();
        req.setOpdId(opdId);
        req.setPatientId(directPatientId);
        req.setDoctorId(directDoctorId);
        req.setWardId(wardId);
        req.setBedId(bedId);
        req.setAdmissionType(admissionType);
        req.setPrimaryDiagnosis(primaryDiagnosis);
        return admit(req);
    }

    /**
     * Admits a patient (from an OPD visit or directly), with an optional package and an
     * optional advance. The hospital's bed-charge rule is fixed on the admission here, so a later
     * change of the setting never re-prices a patient already in a bed.
     */
    @Transactional
    public IpdAdmission admit(com.hms.dto.CreateIpdAdmissionRequest req) {
        Long opdId = req.getOpdId();
        Long directPatientId = req.getPatientId();
        Long directDoctorId = req.getDoctorId();
        Long wardId = req.getWardId();
        Long bedId = req.getBedId();
        String admissionType = req.getAdmissionType() == null || req.getAdmissionType().isBlank() ? ELECTIVE : req.getAdmissionType();
        String primaryDiagnosis = req.getPrimaryDiagnosis();
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        validateAdmissionSelectionIds(opdId, directPatientId, directDoctorId);

        // IPD numbers run per hospital (IPD-1, IPD-2 ...). Locking this hospital's own row before
        // reading MAX+1 makes two admissions in the same hospital take turns; other hospitals
        // are not held up, because their numbers can no longer clash with this one.
        com.hms.entity.Hospital hospital = hospitalRepository.lockById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));
        boolean billing = hasBilling(hospital);
        com.hms.entity.IpdBillingSetting rules = ipdBillingSettingsService.forHospital(hospitalId);
        com.hms.entity.IpdPackage pkg = req.getPackageId() == null ? null : ipdPackageService.requireActive(req.getPackageId());
        assertAdmissionMoney(req, rules, billing, pkg, admissionType);

        Opd opd = resolveAdmissionOpd(opdId, hospitalId);
        com.hms.entity.Patient patient = resolveAdmissionPatient(opd, directPatientId, hospitalId);
        Long patientId = patient.getId();
        Long doctorId = resolveAdmissionDoctor(opd, directDoctorId, hospitalId);

        if (ipdAdmissionRepository.existsByHospitalIdAndPatientIdAndStatusIn(hospitalId, patient.getId(),
                IN_HOSPITAL)) {
            throw new ConflictException("Patient already has an active IPD admission");
        }

        // Lock the selected tenant-scoped bed before checking availability. The lock remains
        // held through the admission and status audit, so exactly one concurrent request wins.
        Bed bed = bedRepository.findByBedIdAndHospitalIdForUpdate(bedId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        if (!bed.getStatus().equalsIgnoreCase("available")) {
            throw new ConflictException("Bed is no longer available");
        }
        // A bed held for a planned admission takes only the patient it is held for.
        com.hms.entity.BedReservation hold = bedReservationService.claimFor(hospitalId, bed.getBedId(), patientId,
                req.getReservationId());

        // Resolve the selected ward tenant-scoped before creating an admission.
        com.hms.entity.Ward ward = wardRepository.findByWardIdAndHospitalId(wardId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Ward not found"));
        if (!wardId.equals(bed.getWardId())) {
            throw new IllegalArgumentException("Selected bed does not belong to the selected ward");
        }

        // Create IPD admission with sequential IPD-1, IPD-2, IPD-3...
        IpdAdmission ipd = new IpdAdmission();
        long maxIpd = java.util.Optional.ofNullable(ipdAdmissionRepository.findMaxIpdSequence(hospitalId)).orElse(0L);
        if (maxIpd == Long.MAX_VALUE) {
            throw new IllegalStateException("IPD number sequence is exhausted");
        }
        long nextIpd = maxIpd + 1;
        ipd.setIpdNumber("IPD-" + nextIpd);
        ipd.setPatientId(patientId);
        ipd.setDoctorId(doctorId);
        ipd.setHospitalId(hospitalId);
        ipd.setSourceOpdId(opd != null ? opd.getId() : null);
        ipd.setAdmissionType(admissionType);
        ipd.setStatus(STATUS_ADMITTED);
        ipd.setAdmissionDatetime(LocalDateTime.now());
        ipd.setWardId(wardId);
        ipd.setBedId(bedId);
        ipd.setPrimaryDiagnosis(primaryDiagnosis != null ? primaryDiagnosis : "");
        ipd.setAdmittedByUserId(securityHelper.getCurrentUserId());
        applyStayTerms(ipd, rules, pkg);

        IpdAdmission saved = ipdAdmissionRepository.save(ipd);
        bedReservationService.markUsed(hold, saved.getId());

        // Bed history is part of the admission record, not best-effort telemetry.
        com.hms.entity.IpdBedHistory initialHist = new com.hms.entity.IpdBedHistory();
        initialHist.setIpdAdmissionId(saved.getId());
        initialHist.setWardId(wardId);
        initialHist.setBedId(bedId);
        initialHist.setAssignedAt(LocalDateTime.now());
        ipdBedHistoryRepository.save(initialHist);

        // ICU Phase 3: a direct admission into a critical-care ward opens an ICU stay. Critical
        // state, deliberately NOT best-effort — a patient in an ICU bed with no stay record breaks
        // the board's central invariant silently and loses the admission time for good.
        // EMERGENCY vs OPD is derived from the admission type rather than asked again.
        icuStayService.onWardSettled(saved, null,
                "EMERGENCY".equalsIgnoreCase(saved.getAdmissionType())
                        ? com.hms.entity.IcuStay.SRC_EMERGENCY
                        : com.hms.entity.IcuStay.SRC_OPD,
                null, null);

        // Mark bed occupied (Nursing Mgmt Phase C2: audited bed status change)
        Bed occupiedBed = bedStatusService.changeLocked(bed, com.hms.entity.BedStatus.OCCUPIED, "IPD admission");
        occupiedBed.setCurrentIpdAdmissionId(saved.getId());
        bedRepository.save(occupiedBed);

        // Primary assignment is optional, but an attempted persisted assignment must not be
        // silently lost. With zero or multiple eligible nurses this is a no-op.
        patientAssignmentService.onAdmission(saved);

        closeOpdForAdmission(opd);
        if (billing) {
            openAdmissionBill(saved, opd, ward, pkg, req);
        }

        logger.info("Created IPD admission {} for OPD {}", saved.getIpdNumber(), opdId);

        // Audit log
        try {
            auditLogService.logAction(
                    "IPD_ADMISSION_CREATED",
                    "Patient was admitted to IPD (Case: " + saved.getIpdNumber() + ").",
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "IPD",
                    saved.getId().toString(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for IPD admission", e);
        }

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from admitFromOpd", e);
        }

        return saved;
    }

    private void validateAdmissionSelectionIds(Long opdId, Long patientId, Long doctorId) {
        // Enforce selection in the shared service so every admission entry point fails closed.
        if ((opdId != null && opdId <= 0) || (patientId != null && patientId <= 0)
                || (doctorId != null && doctorId <= 0)) {
            throw new IllegalArgumentException("Patient, OPD and doctor IDs must be positive");
        }
        if (opdId == null && patientId == null) {
            throw new IllegalArgumentException("Please select a patient or OPD to admit");
        }
    }

    private Opd resolveAdmissionOpd(Long opdId, Long hospitalId) {
        if (opdId == null) return null;
        Opd opd = opdRepository.findByIdAndHospitalIdWithPatientAndDoctorForUpdate(opdId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("OPD not found"));
        if (Opd.Status.IN_IPD.equals(opd.getStatus())) {
            throw new ConflictException("OPD is already admitted to IPD");
        }
        return opd;
    }

    private com.hms.entity.Patient resolveAdmissionPatient(Opd opd, Long patientId, Long hospitalId) {
        if (opd != null) {
            if (opd.getPatient() == null) {
                throw new IllegalArgumentException("Selected OPD has no patient");
            }
            Long opdPatientId = opd.getPatient().getId();
            if (patientId != null && !patientId.equals(opdPatientId)) {
                throw new IllegalArgumentException("Selected patient does not match the selected OPD");
            }
            patientId = opdPatientId;
        }
        com.hms.entity.Patient patient = patientRepository.findByIdAndHospitalIdForUpdate(patientId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException(PATIENT_NOT_FOUND));
        if (!Boolean.TRUE.equals(patient.getIsActive())) {
            throw new IllegalArgumentException("Selected patient is inactive");
        }
        return patient;
    }

    private Long resolveAdmissionDoctor(Opd opd, Long doctorId, Long hospitalId) {
        if (doctorId == null && opd != null && opd.getDoctor() != null) {
            doctorId = opd.getDoctor().getId();
        }
        if (doctorId == null) {
            java.util.List<com.hms.entity.Doctor> doctors = doctorRepository.findByHospitalIdAndIsActiveTrueOrderByCreatedAtDesc(hospitalId);
            if (doctors != null && !doctors.isEmpty()) {
                doctorId = doctors.get(0).getId();
            }
        }
        // Validate every source before persistence; foreign and missing doctors are indistinguishable.
        if (doctorId != null && doctorRepository
                .findByIdAndHospitalIdAndIsActiveTrue(doctorId, hospitalId).isEmpty()) {
            throw new ResourceNotFoundException("Doctor not found");
        }
        return doctorId;
    }

    private void closeOpdForAdmission(Opd opd) {
        // Mark OPD as completed/closed
        if (opd != null) {
            try {
                opd.setStatus(Opd.Status.IN_IPD);
            } catch (Exception ex) {
                // fallback if OPD uses enum type
                opd.setStatus(Opd.Status.COMPLETED);
            }
            opdRepository.save(opd);

            // Remove from doctor's active queue
            try {
                queueEntryRepository.deleteByOpdId(opd.getId());
            } catch (Exception e) {
                logger.warn("Failed to delete queue entry for OPD ID during IPD admission", e);
            }
        }
    }

    /**
     * The minimum advance (planned admissions only; an emergency is never held up at the desk),
     * and that money and packages need the billing module. Checked before anything is written.
     */
    private static void assertAdmissionMoney(com.hms.dto.CreateIpdAdmissionRequest req, com.hms.entity.IpdBillingSetting rules,
                                             boolean billing, com.hms.entity.IpdPackage pkg, String admissionType) {
        java.math.BigDecimal advance = req.getAdvanceAmount() == null ? java.math.BigDecimal.ZERO : req.getAdvanceAmount();
        if (!billing) {
            if (advance.signum() > 0 || pkg != null) {
                throw new IllegalArgumentException("Billing is not enabled for this hospital, so no advance or package can be recorded");
            }
            return;
        }
        java.math.BigDecimal min = rules.getMinAdvance();
        if (min != null && min.signum() > 0 && ELECTIVE.equalsIgnoreCase(admissionType) && advance.compareTo(min) < 0) {
            throw new IllegalArgumentException("A planned admission needs an advance of at least \u20B9"
                    + min.stripTrailingZeros().toPlainString() + ". Emergency admissions are not held up.");
        }
    }

    /** The bed-charge rule in force now, and the package, fixed on the admission. */
    private static void applyStayTerms(IpdAdmission ipd, com.hms.entity.IpdBillingSetting rules, com.hms.entity.IpdPackage pkg) {
        ipd.setBedChargeRule(rules.getBedChargeRule());
        ipd.setGraceHours(rules.getGraceHours());
        if (pkg != null) {
            ipd.setPackageId(pkg.getId());
            ipd.setPackageAppliedAt(ipd.getAdmissionDatetime());
        }
    }

    /** Opens the IPD bill and records the advance taken at the desk, if any. */
    private void openAdmissionBill(IpdAdmission saved, Opd opd, com.hms.entity.Ward ward, com.hms.entity.IpdPackage pkg,
                                   com.hms.dto.CreateIpdAdmissionRequest req) {
        Billing bill = createAdmissionBill(saved, opd, ward, saved.getHospitalId(), pkg);
        java.math.BigDecimal advance = req.getAdvanceAmount();
        if (advance != null && advance.signum() > 0) {
            billingService.recordAdvance(bill.getId(), advance, req.getAdvanceMode(), req.getAdvanceReference());
        }
    }

    private static boolean hasBilling(com.hms.entity.Hospital hospital) {
        return hospital != null && hospital.getModules() != null && hospital.getModules().contains(MODULE_BILLING);
    }

    /** Whether this hospital bills (the caller's own hospital; used by the round and bed paths). */
    private boolean hasBillingModule(Long hospitalId) {
        return hasBilling(hospitalRepository.findById(hospitalId).orElse(null));
    }

    /** Opens the admission's IPD bill: the package, or day 1's bed and nursing charges. */
    private Billing createAdmissionBill(IpdAdmission saved, Opd opd, com.hms.entity.Ward ward, Long hospitalId,
                                        com.hms.entity.IpdPackage pkg) {
        java.math.BigDecimal bedPrice = ward.getBedPrice() != null ? ward.getBedPrice() : java.math.BigDecimal.ZERO;

        Long appointmentId = null;
        try {
            java.util.List<com.hms.entity.Appointment> appointments = appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(saved.getPatientId(), hospitalId);
            if (appointments != null && !appointments.isEmpty()) {
                appointmentId = appointments.get(0).getId();
            }
        } catch (Exception e) {
            logger.debug("Could not resolve latest appointment for patient during IPD admission billing", e);
        }

        Billing bill = new Billing();
        bill.setHospitalId(hospitalId);
        bill.setPatientId(saved.getPatientId());
        bill.setDoctorId(saved.getDoctorId());
        bill.setIpdAdmissionId(saved.getId());
        bill.setOpdId(opd != null ? opd.getId() : null);
        bill.setAppointmentId(appointmentId);
        bill.setBillingType("IPD");
        bill.setAmount(pkg != null ? pkg.getPrice() : bedPrice);
        bill.setDescription(pkg != null ? packageLine(pkg) : IpdChargePeriods.BED_PRICE);
        bill.setPaymentStatus("PENDING");
        Billing savedBill = billingService.saveBillWithCollisionRetry(bill);
        if (savedBill == null) {
            savedBill = billingRepository.save(bill);
        }

        if (pkg != null) {
            addBillLine(savedBill, packageLine(pkg), pkg.getPrice());
        } else {
            addBillLine(savedBill, IpdChargePeriods.BED_PRICE, bedPrice);
            java.math.BigDecimal nursing = ward.getNursingCharge();
            if (nursing != null && nursing.signum() > 0) {
                IpdChargePeriods.Period day1 = IpdChargePeriods.started(saved, saved.getAdmissionDatetime()).get(0);
                addBillLine(savedBill, IpdChargePeriods.nursingLine(saved, day1), nursing);
            }
        }
        billingService.recalculateTotal(savedBill.getId());
        return savedBill;
    }

    private static String packageLine(com.hms.entity.IpdPackage pkg) {
        return "Package: " + pkg.getName() + " (" + pkg.getIncludedDays() + " day" + (pkg.getIncludedDays() == 1 ? "" : "s") + ")";
    }

    private void addBillLine(Billing bill, String description, java.math.BigDecimal amount) {
        com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
        item.setBillingId(bill.getId());
        item.setHospitalId(bill.getHospitalId());
        item.setDescription(description);
        item.setAmount(amount);
        billingItemRepository.save(item);
    }

    /** Days the admission's package covers; 0 without one. */
    private int packageDays(IpdAdmission ipd) {
        return ipdPackageService.find(ipd.getPackageId(), ipd.getHospitalId())
                .map(com.hms.entity.IpdPackage::getIncludedDays).orElse(0);
    }

    /** The admission's live IPD bill, or null (none, cancelled or closed). */
    private Billing liveIpdBill(IpdAdmission ipd) {
        return billingRepository.findByIpdAdmissionId(ipd.getId()).stream()
                .filter(b -> java.util.Objects.equals(b.getHospitalId(), ipd.getHospitalId()))
                .filter(b -> !STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus()) && !"CLOSED".equalsIgnoreCase(b.getPaymentStatus()))
                .findFirst().orElse(null);
    }

    /** IPD list views: who is in now, who has left, cancelled admissions, or everything. */
    public static final java.util.Map<String, java.util.List<String>> IPD_VIEWS = java.util.Map.of(
            "CURRENT", IN_HOSPITAL,
            STATUS_DISCHARGED, java.util.List.of(STATUS_DISCHARGED),
            STATUS_CANCELLED, java.util.List.of(STATUS_CANCELLED),
            "ALL", java.util.List.of(STATUS_ADMITTED, STATUS_DISCHARGE_PLANNED, STATUS_DISCHARGED, STATUS_CANCELLED));

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<java.util.Map<String, Object>> listIpdAdmissions(int page, int size, String search) {
        return listIpdAdmissions(page, size, search, "ALL", null, null);
    }

    /**
     * The IPD list with its history. {@code view} is CURRENT / DISCHARGED / CANCELLED / ALL; the
     * date window (inclusive dates) applies to the discharge date in the Discharged view and to
     * the admission date otherwise. A doctor sees the admissions they are the doctor for; admin
     * and reception see the whole hospital. Read-only.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<java.util.Map<String, Object>> listIpdAdmissions(
            int page, int size, String search, String view, java.time.LocalDate from, java.time.LocalDate to) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        String v = view == null || view.isBlank() ? "ALL" : view.trim().toUpperCase(java.util.Locale.ROOT);
        java.util.List<String> statuses = IPD_VIEWS.get(v);
        if (statuses == null) throw new IllegalArgumentException("Unknown view: " + view + " (use CURRENT, DISCHARGED, CANCELLED or ALL)");
        if (from != null && to != null && to.isBefore(from)) throw new IllegalArgumentException("The 'to' date is before the 'from' date");

        boolean byDischarge = STATUS_DISCHARGED.equals(v);
        String q = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), Math.max(1, Math.min(size, 100)),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        byDischarge ? "dischargeDatetime" : "admissionDatetime", "id"));
        org.springframework.data.domain.Page<IpdAdmission> p = ipdAdmissionRepository.searchHistory(hospitalId, statuses,
                currentDoctorScope(hospitalId), byDischarge,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                q, pageable);
        return new org.springframework.data.domain.PageImpl<>(withNames(hospitalId, p.getContent()), pageable, p.getTotalElements());
    }

    /** A doctor sees the admissions they are the doctor for; everyone else the whole hospital (null). */
    private Long currentDoctorScope(Long hospitalId) {
        if (!ROLE_DOCTOR.equals(securityHelper.getCurrentUserRole())) return null;
        return doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId)
                .map(com.hms.entity.Doctor::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));
    }

    /** Each admission with its patient, doctor, ward and bed, loaded in batches and tenant-scoped. */
    private java.util.List<java.util.Map<String, Object>> withNames(Long hospitalId, java.util.List<IpdAdmission> list) {
        java.util.Map<Long, com.hms.entity.Patient> patients = new java.util.HashMap<>();
        patientRepository.findByHospitalIdAndIdIn(hospitalId, idsOf(list, IpdAdmission::getPatientId))
                .forEach(x -> patients.put(x.getId(), x));
        java.util.Map<Long, com.hms.entity.Doctor> doctors = new java.util.HashMap<>();
        doctorRepository.findByHospitalIdAndIdIn(hospitalId, idsOf(list, IpdAdmission::getDoctorId))
                .forEach(x -> doctors.put(x.getId(), x));
        java.util.Set<Long> wardIds = idsOf(list, IpdAdmission::getWardId);
        java.util.Map<Long, com.hms.entity.Ward> wards = new java.util.HashMap<>();
        java.util.Map<Long, com.hms.entity.Bed> beds = new java.util.HashMap<>();
        if (!wardIds.isEmpty()) {
            wardRepository.findByHospitalId(hospitalId).stream().filter(w -> wardIds.contains(w.getWardId()))
                    .forEach(w -> wards.put(w.getWardId(), w));
            bedRepository.findByHospitalIdAndWardIdIn(hospitalId, wardIds).forEach(b -> beds.put(b.getBedId(), b));
        }
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (IpdAdmission ipd : list) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("ipd", ipd);
            putIfPresent(m, "patient", patients.get(ipd.getPatientId()));
            putIfPresent(m, "doctor", doctors.get(ipd.getDoctorId()));
            putIfPresent(m, "ward", wards.get(ipd.getWardId()));
            putIfPresent(m, "bed", beds.get(ipd.getBedId()));
            rows.add(m);
        }
        return rows;
    }

    private static java.util.Set<Long> idsOf(java.util.List<IpdAdmission> list, java.util.function.Function<IpdAdmission, Long> id) {
        return list.stream().map(id).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
    }

    private static void putIfPresent(java.util.Map<String, Object> m, String key, Object value) {
        if (value != null) m.put(key, value);
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> listMyIpdAdmissionsForDoctor() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        // Resolve current doctor's entity using authenticated user's email
        String email = securityHelper.getCurrentUserEmail();
        com.hms.entity.Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
            .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));
        Long doctorId = doctor.getId();

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 100, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "admissionDatetime"));
        org.springframework.data.domain.Page<IpdAdmission> p = ipdAdmissionRepository.findByHospitalIdAndDoctorIdAndStatusIn(hospitalId, doctorId, IN_HOSPITAL, pageable);

        java.util.List<java.util.Map<String,Object>> rows = new java.util.ArrayList<>();
        for (IpdAdmission ipd : p.getContent()) {
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("ipd", ipd);
            patientRepository.findById(ipd.getPatientId()).ifPresent(patient -> m.put("patient", patient));
            doctorRepository.findById(ipd.getDoctorId()).ifPresent(doc -> m.put("doctor", doc));
            wardRepository.findById(ipd.getWardId()).ifPresent(ward -> m.put("ward", ward));
            bedRepository.findById(ipd.getBedId()).ifPresent(bed -> m.put("bed", bed));
            rows.add(m);
        }

        return rows;
    }

    /**
     * Role-aware fetch of currently ADMITTED IPD admissions.
     * Returns only ADMITTED patients. Receptionist sees all hospital admissions,
     * Doctor sees only their assigned admissions.
     */
    @Transactional(readOnly = true)
    public java.util.List<com.hms.dto.IpdAdmissionSummaryDTO> getAdmittedIpdSummariesForCurrentUser() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        String role = securityHelper.getCurrentUserRole();
        java.util.List<IpdAdmission> admissions;

        if (ROLE_DOCTOR.equalsIgnoreCase(role)) {
            String email = securityHelper.getCurrentUserEmail();
            com.hms.entity.Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));
            Long doctorId = doctor.getId();
            // A planned discharge is still the doctor's patient, in the bed until reception confirms.
            admissions = ipdAdmissionRepository.findByHospitalIdAndDoctorIdAndStatusIn(hospitalId, doctorId, IN_HOSPITAL);
        } else if ("RECEPTIONIST".equalsIgnoreCase(role) || "HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            admissions = ipdAdmissionRepository.findByHospitalIdAndStatusIn(hospitalId, IN_HOSPITAL);
        } else {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed");
        }

        java.util.Set<Long> icuWardIds = icuWardRepository.findByHospitalId(hospitalId).stream()
                .map(com.hms.entity.IcuWard::getWardId)
                .collect(java.util.stream.Collectors.toSet());

        java.util.List<com.hms.dto.IpdAdmissionSummaryDTO> result = new java.util.ArrayList<>();
        for (IpdAdmission ipd : admissions) {
            if (ipd.getWardId() != null && icuWardIds.contains(ipd.getWardId())) {
                continue;
            }
            com.hms.dto.IpdAdmissionSummaryDTO dto = new com.hms.dto.IpdAdmissionSummaryDTO();
            dto.setIpdId(ipd.getId());
            dto.setIpdNumber(ipd.getIpdNumber());
            // patient
            patientRepository.findById(ipd.getPatientId()).ifPresent(p -> {
                dto.setPatientName(p.getName());
                try { dto.setAge(p.getAge()); } catch (Exception e) { logger.debug("Could not map patient age to DTO", e); }
                dto.setGender(p.getGender());
            });
            // ward/bed
            wardRepository.findById(ipd.getWardId()).ifPresent(w -> dto.setWardName(w.getWardName()));
            bedRepository.findById(ipd.getBedId()).ifPresent(b -> dto.setBedNumber(b.getBedCode()));
            // doctor
            doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> dto.setDoctorName(d.getName()));
            dto.setAdmissionConfirmed(Boolean.TRUE.equals(ipd.getAdmissionConfirmed()));
            dto.setAdmissionDateTime(ipd.getAdmissionDatetime());
            dto.setStatus(ipd.getStatus());
            result.add(dto);
        }

        // sort by admissionDatetime desc
        result.sort((a,b) -> b.getAdmissionDateTime().compareTo(a.getAdmissionDateTime()));
        return result;
    }

    @Transactional(readOnly = true)
    /**
     * Load an admission by id and prove it belongs to the caller's hospital. Every
     * user-facing method here takes a raw numeric ipdId from the URL; using this instead
     * of a bare findById is what stops one hospital reading or mutating another's admission
     * (and, in the discharge/bed paths, silently adopting the victim's tenant context via
     * hospitalId = ipd.getHospitalId()). A cross-tenant id is reported as not-found.
     */
    private IpdAdmission requireOwnedAdmission(Long ipdId) {
        Long callerHospitalId = securityHelper.getCurrentHospitalId();
        if (callerHospitalId == null) throw new ResourceNotFoundException("IPD admission not found");
        return ipdAdmissionRepository.findByIdAndHospitalId(ipdId, callerHospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("IPD admission not found"));
    }

    /** Re-reads the admission under lock before a lifecycle or bed mutation. */
    private IpdAdmission requireOwnedAdmissionForUpdate(Long ipdId) {
        Long callerHospitalId = securityHelper.getCurrentHospitalId();
        if (callerHospitalId == null) throw new ResourceNotFoundException("IPD admission not found");
        return ipdAdmissionRepository.findByIdAndHospitalIdForUpdate(ipdId, callerHospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("IPD admission not found"));
    }

    public com.hms.dto.IpdAdmissionDetailsDTO getIpdAdmissionDetails(Long ipdId) {
        IpdAdmission ipd = requireOwnedAdmission(ipdId);

        com.hms.dto.IpdAdmissionDetailsDTO dto = new com.hms.dto.IpdAdmissionDetailsDTO();
        dto.setIpdNumber(ipd.getIpdNumber());
        dto.setStatus(ipd.getStatus());
        dto.setDischargeType(ipd.getDischargeType());
        dto.setDischargeNote(ipd.getDischargeNote());
        dto.setDischargeDateTime(ipd.getDischargeDatetime());
        dto.setCancelReason(ipd.getCancelReason());
        dto.setCancelledAt(ipd.getCancelledAt());
        dto.setCancelledBy(ipd.getCancelledBy());

        // patient
        com.hms.entity.Patient patient = patientRepository.findById(ipd.getPatientId()).orElse(null);
        if (patient != null) {
            com.hms.dto.IpdAdmissionDetailsDTO.PatientDTO p = new com.hms.dto.IpdAdmissionDetailsDTO.PatientDTO();
            p.id = patient.getId();
            p.name = patient.getName();
            try { p.age = patient.getAge(); } catch (Exception e) { logger.debug("Could not map patient age to details DTO", e); }
            p.gender = patient.getGender();
            dto.setPatient(p);
        }

        // admission info
        com.hms.dto.IpdAdmissionDetailsDTO.AdmissionDTO adm = new com.hms.dto.IpdAdmissionDetailsDTO.AdmissionDTO();
        adm.admissionDateTime = ipd.getAdmissionDatetime();
        adm.admissionType = ipd.getAdmissionType();
        adm.primaryDiagnosis = ipd.getPrimaryDiagnosis();
        adm.admissionConfirmed = Boolean.TRUE.equals(ipd.getAdmissionConfirmed());
        wardRepository.findById(ipd.getWardId()).ifPresent(w -> adm.ward = w.getWardName());
        bedRepository.findById(ipd.getBedId()).ifPresent(b -> adm.bed = b.getBedCode());
        doctorRepository.findById(ipd.getDoctorId()).ifPresent(d -> adm.doctor = d.getName());
        adm.doctorId = ipd.getDoctorId();
        adm.bedChargeRule = ipd.getBedChargeRule() == null ? com.hms.entity.IpdBillingSetting.CALENDAR_DAY : ipd.getBedChargeRule();
        adm.graceHours = ipd.getGraceHours();
        ipdPackageService.find(ipd.getPackageId(), ipd.getHospitalId()).ifPresent(pk -> {
            adm.packageId = pk.getId();
            adm.packageName = pk.getName();
            adm.packageDays = pk.getIncludedDays();
        });
        dto.setAdmission(adm);

        // medical records for this IPD
        java.util.List<com.hms.entity.MedicalRecord> mrs = new java.util.ArrayList<>();
        try {
            mrs = medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(ipdId);
        } catch (Exception ex) {
            // fallback to empty
            mrs = java.util.Collections.emptyList();
        }
        java.util.List<com.hms.dto.IpdAdmissionDetailsDTO.MedicalRecordDTO> mrDtos = new java.util.ArrayList<>();
        for (com.hms.entity.MedicalRecord mr : mrs) {
            com.hms.dto.IpdAdmissionDetailsDTO.MedicalRecordDTO mrd = new com.hms.dto.IpdAdmissionDetailsDTO.MedicalRecordDTO();
            mrd.date = mr.getCreatedAt() != null ? mr.getCreatedAt().toLocalDate().toString() : null;
            doctorRepository.findById(mr.getDoctorId()).ifPresent(d -> mrd.doctor = d.getName());
            mrd.diagnosis = mr.getDiagnosis();
            mrd.notes = mr.getTreatmentNotes();
            mrDtos.add(mrd);
        }
        dto.setMedicalRecords(mrDtos);

        // active prescriptions -> fetch by IPD and status
        java.util.List<com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO> active = new java.util.ArrayList<>();
        try {
            java.util.List<com.hms.entity.Prescription> activeList = prescriptionRepository.findByIpdAdmissionIdAndStatus(ipdId, "ACTIVE");
            for (com.hms.entity.Prescription p : activeList) {
                com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO pd = new com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO();
                pd.id = p.getId();
                pd.name = p.getMedicineName();
                pd.type = p.getType();
                pd.route = p.getRoute();
                pd.frequency = p.getFrequency();
                pd.status = p.getStatus();
                pd.startDate = p.getStartDate() != null ? p.getStartDate().toString() : null;
                pd.dosage = p.getDosage();
                pd.durationDays = p.getDurationDays();
                active.add(pd);
            }
        } catch (Exception e) {
            logger.warn("Failed to load active prescriptions for IPD {}", ipdId, e);
        }
        dto.setActivePrescriptions(active);

        // all prescriptions history for this IPD (ordered)
        java.util.List<com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO> all = new java.util.ArrayList<>();
        try {
            java.util.List<com.hms.entity.Prescription> allList = prescriptionRepository.findByIpdAdmissionIdOrderByStartDate(ipdId);
            for (com.hms.entity.Prescription p : allList) {
                com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO pd = new com.hms.dto.IpdAdmissionDetailsDTO.PrescriptionDTO();
                pd.id = p.getId();
                pd.name = p.getMedicineName();
                pd.type = p.getType();
                pd.route = p.getRoute();
                pd.frequency = p.getFrequency();
                pd.status = p.getStatus();
                pd.startDate = p.getStartDate() != null ? p.getStartDate().toString() : null;
                pd.dosage = p.getDosage();
                pd.durationDays = p.getDurationDays();
                all.add(pd);
            }
        } catch (Exception e) {
            logger.warn("Failed to load all prescription history for IPD {}", ipdId, e);
        }
        dto.setAllPrescriptions(all);

        // billing summary (aggregate) — discount- and refund-aware, same figures as the bill screens
        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
        if (bills != null && !bills.isEmpty()) {
            java.math.BigDecimal net = java.math.BigDecimal.ZERO;
            java.math.BigDecimal paid = java.math.BigDecimal.ZERO;
            java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
            for (Billing b : bills) {
                if (STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus())) continue;
                BillMoney m = billingService.moneyFor(b);
                net = net.add(m.net());
                paid = paid.add(m.paid());
                discount = discount.add(m.discount());
            }
            com.hms.dto.IpdAdmissionDetailsDTO.BillingDTO bd = new com.hms.dto.IpdAdmissionDetailsDTO.BillingDTO();
            bd.totalAmount = net;
            bd.paidAmount = paid;
            bd.balance = net.subtract(paid);
            bd.discountAmount = discount;
            bd.advances = new java.util.ArrayList<>();
            bd.advancePaid = java.math.BigDecimal.ZERO;
            for (Billing b : bills) {
                if (STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus())) continue;
                for (com.hms.entity.BillingPayment adv : billingService.advancesOf(b.getId())) {
                    com.hms.dto.IpdAdmissionDetailsDTO.AdvanceDTO a = new com.hms.dto.IpdAdmissionDetailsDTO.AdvanceDTO();
                    a.id = adv.getId();
                    a.amount = adv.getAmount();
                    a.mode = adv.getMode();
                    a.reference = adv.getReference();
                    a.createdAt = adv.getCreatedAt();
                    bd.advances.add(a);
                    bd.advancePaid = bd.advancePaid.add(adv.getAmount());
                }
            }
            ipdPackageService.find(ipd.getPackageId(), ipd.getHospitalId()).ifPresent(pk -> bd.packagePrice = pk.getPrice());
            bd.insuranceExpected = ipdInsuranceService.expectedFromInsurer(ipd);
            try {
                bd.outstandingBills = billingService.outstandingBillsForPatient(ipd.getPatientId(), ipd.getHospitalId());
            } catch (Exception e) {
                logger.warn("Failed to list outstanding bills for IPD {}", ipdId, e);
            }
            dto.setBilling(bd);
        } else {
            dto.setBilling(null);
        }

        // administered stock items — sourced from BillingMedicine rows for this IPD
        java.util.List<com.hms.dto.IpdAdmissionDetailsDTO.AdministeredItemDTO> administeredDtos = new java.util.ArrayList<>();
        try {
            if (bills != null) {
                for (Billing b : bills) {
                    java.util.List<com.hms.entity.BillingMedicine> bMeds = billingMedicineRepository.findByBillingId(b.getId());
                    if (bMeds != null) {
                        for (com.hms.entity.BillingMedicine bm : bMeds) {
                            com.hms.dto.IpdAdmissionDetailsDTO.AdministeredItemDTO ad = new com.hms.dto.IpdAdmissionDetailsDTO.AdministeredItemDTO();
                            ad.name = bm.getMedicineName();
                            ad.quantity = bm.getQuantity();
                            ad.administeredAt = bm.getCreatedAt() != null ? bm.getCreatedAt().toLocalDate().toString() : null;
                            administeredDtos.add(ad);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load administered billing medicines for IPD {}", ipdId, e);
        }
        dto.setAdministeredItems(administeredDtos);
        dischargeSummaryRepository.findByIpdAdmissionId(ipdId)
                .ifPresent(ds -> dto.setDischargeSummary(summaryDto(ds, ipd.getHospitalId())));

        return dto;
    }

    @Transactional
    public com.hms.entity.MedicalRecord addIpdFollowup(Long ipdId, String diagnosis, String notes, java.util.List<com.hms.dto.ConsultationRequest.AdministeredItem> administeredItems) {
        String role = securityHelper.getCurrentUserRole();
        if (!ROLE_DOCTOR.equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can add follow-ups");
        }

        if (administeredItems != null && !administeredItems.isEmpty()) {
            // Medicines given on the ward are recorded on the medication chart, which takes them
            // out of stock batch by batch and can be undone. This old path took stock off the
            // medicine total without touching any batch, so the two no longer agreed.
            throw new IllegalArgumentException("Record medicines given on the Medication tab (medication chart), "
                    + "not on the follow-up.");
        }
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!IpdClinicalState.isActive(ipd)) {
            throw new IllegalArgumentException("Cannot add follow-up to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        String email = securityHelper.getCurrentUserEmail();
        com.hms.entity.Doctor doctor = doctorRepository.findByEmailAndHospitalId(email, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for current user"));

        com.hms.entity.MedicalRecord mr = new com.hms.entity.MedicalRecord();
        mr.setHospitalId(hospitalId);
        mr.setPatientId(ipd.getPatientId());
        mr.setDoctorId(doctor.getId());
        mr.setIpdAdmissionId(ipdId);
        mr.setVisitType("IPD");
        mr.setDiagnosis(diagnosis);
        mr.setTreatmentNotes(notes);

        com.hms.entity.MedicalRecord saved = medicalRecordRepository.save(mr);

        // A round is charged only when the hospital sets a doctor-visit fee (once per doctor per day).
        chargeDoctorVisit(ipd, doctor, hospitalId);

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from addIpdFollowup", e);
        }

        return saved;
    }

    @Transactional
    public void administerHospitalItems(Long ipdId, java.util.List<com.hms.dto.AdministerHospitalItemsRequest.HospitalItem> items) {
        String role = securityHelper.getCurrentUserRole();
        if (!ROLE_DOCTOR.equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can administer items");
        }

        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (ipd.getStatus() == null || (!ipd.getStatus().equalsIgnoreCase(STATUS_ADMITTED) && !ipd.getStatus().equalsIgnoreCase(STATUS_DISCHARGE_PLANNED))) {
            throw new IllegalArgumentException("Cannot administer items to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        boolean hasBillingModule = hasBilling(hospital);

        // Resolve billing record once (not per-item) to avoid N+1 queries
        Billing ipdBill = null;
        if (hasBillingModule) {
            java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
            ipdBill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;
        }

        if (items != null && !items.isEmpty()) {
            for (com.hms.dto.AdministerHospitalItemsRequest.HospitalItem item : items) {
                administerSingleHospitalItem(item, hospitalId, hasBillingModule, ipdBill);
            }
            if (hasBillingModule && ipdBill != null) {
                recalcBillTotalQuietly(ipdBill.getId());
            }
        }

        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from administerHospitalItems", e);
        }
    }

    /** Consumes one hospital service and, when billing is active, appends a billing line for it. */
    private void administerSingleHospitalItem(com.hms.dto.AdministerHospitalItemsRequest.HospitalItem item,
            Long hospitalId, boolean hasBillingModule, Billing ipdBill) {
        java.math.BigDecimal serviceCharge = hospitalInventoryService.consumeService(
                item.getServiceId(), item.getQuantity(), hospitalId);
        if (hasBillingModule && ipdBill != null) {
            com.hms.entity.HospitalServiceEntity svc = hospitalServiceRepository.findByIdAndHospitalId(item.getServiceId(), hospitalId).orElse(null);
            String svcName = svc != null ? svc.getName() : ("Service #" + item.getServiceId());
            com.hms.entity.BillingItem bi = new com.hms.entity.BillingItem();
            bi.setBillingId(ipdBill.getId());
            bi.setHospitalId(hospitalId);
            bi.setDescription(svcName + " (Qty: " + item.getQuantity() + ")");
            bi.setAmount(serviceCharge);
            billingItemRepository.save(bi);
        }
    }

    /** Recalculates a bill's total (consultation + medicines + bed fees + hospital items); best-effort. */
    private void recalcBillTotalQuietly(Long billingId) {
        try {
            billingService.recalculateTotal(billingId);
        } catch (Exception e) {
            logger.warn("Failed to recalculate billing total after hospital item administration", e);
        }
    }

    @Transactional
    public com.hms.entity.Prescription addIpdPrescription(Long ipdId, com.hms.dto.AddIpdPrescriptionRequest req) {
        String role = securityHelper.getCurrentUserRole();
        if (!ROLE_DOCTOR.equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can add prescriptions");
        }

        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!IpdClinicalState.isActive(ipd)) {
            throw new IllegalArgumentException("Cannot add prescription to non-admitted IPD");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");

        // Resolve latest medical record for this IPD
        java.util.List<com.hms.entity.MedicalRecord> mrs = medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(ipdId);
        com.hms.entity.MedicalRecord latest;
        if (mrs == null || mrs.isEmpty()) {
            latest = new com.hms.entity.MedicalRecord();
            latest.setHospitalId(hospitalId);
            latest.setPatientId(ipd.getPatientId());
            Long resolvedDocId = ipd.getDoctorId();
            if (resolvedDocId == null) {
                try {
                    java.util.Optional<com.hms.entity.Doctor> dopt = doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId);
                    if (dopt.isPresent()) {
                        resolvedDocId = dopt.get().getId();
                    }
                } catch (Exception e) {
                    logger.debug("Could not resolve doctor during IPD prescription creation", e);
                }
            }
            latest.setDoctorId(resolvedDocId);
            latest.setIpdAdmissionId(ipdId);
            latest.setVisitType("IPD");
            latest.setDiagnosis(ipd.getPrimaryDiagnosis() != null && !ipd.getPrimaryDiagnosis().trim().isEmpty() ? ipd.getPrimaryDiagnosis() : "IPD Admission");
            latest.setTreatmentNotes("Initial IPD Admission Medical Record");
            latest = medicalRecordRepository.save(latest);
        } else {
            latest = mrs.get(0);
        }

        String medicineName = req.getMedicineName() != null && !req.getMedicineName().trim().isEmpty()
                ? req.getMedicineName().trim()
                : null;
        if ((req.getMedicineName() == null || req.getMedicineName().trim().isEmpty()) && req.getMedicineId() == null
                && (req.getHospitalItemName() == null || req.getHospitalItemName().isBlank())) {
            throw new IllegalArgumentException("Either Medicine Name or Medicine ID is required");
        }
        if (req.getDose() == null || req.getDose().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription dose is required");
        }
        if (req.getFrequency() == null || req.getFrequency().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription frequency is required");
        }
        if (req.getType() == null || req.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription type is required");
        }
        if (req.getRoute() == null || req.getRoute().trim().isEmpty()) {
            throw new IllegalArgumentException("Prescription route is required");
        }
        if (req.getStartDate() == null || req.getStartDate().isBefore(java.time.LocalDate.now(java.time.ZoneId.systemDefault()))) {
            throw new IllegalArgumentException("Prescription start date cannot be in the past");
        }
        if (req.getDurationDays() == null || req.getDurationDays() <= 0) {
            throw new IllegalArgumentException("Prescription duration must be at least 1 day");
        }

        // Resolve the medicine, and with it the optional link to stock. After validation, so a
        // badly-formed order is reported as badly formed rather than as a lookup failure.
        //
        // A medicineId means the prescriber picked a row out of this facility's inventory, and
        // keeping that link is what later lets the nurse's chart show real stock instead of
        // guessing. It is scoped to the caller's facility because the id arrives in the request
        // body: unscoped, it read another hospital's medicine and copied its name onto this order.
        //
        // No medicineId means the doctor typed a name. That is a valid order and is stored as
        // typed; nothing here goes looking for an inventory row that might match the text.
        // Saline, IV fluids and consumables come from the hospital inventory, by item name.
        String hospitalItem = req.getHospitalItemName() == null || req.getHospitalItemName().isBlank()
                ? null : req.getHospitalItemName().trim();
        if (hospitalItem != null && hospitalInventoryRepository
                .findByNameAndHospitalIdAndIsActiveTrue(hospitalItem, hospitalId).isEmpty()) {
            throw new ResourceNotFoundException("Item not found in this hospital's inventory: " + hospitalItem);
        }
        com.hms.entity.Medicine linkedMedicine = null;
        if (hospitalItem == null && req.getMedicineId() != null) {
            linkedMedicine = medicineRepository.findByIdAndHospitalId(req.getMedicineId(), hospitalId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Medicine not found in this facility's inventory: ID " + req.getMedicineId()));
        }
        if (medicineName == null) {
            medicineName = hospitalItem != null ? hospitalItem : linkedMedicine.getName();
        }

        com.hms.entity.Prescription p = new com.hms.entity.Prescription();
        p.setHospitalId(hospitalId);
        p.setMedicalRecordId(latest.getId());
        p.setMedicineName(medicineName);
        p.setMedicineId(linkedMedicine == null ? null : linkedMedicine.getId());
        if (hospitalItem != null) {
            p.setStockSource(MedicationStockService.SOURCE_HOSPITAL_ITEM);
            p.setHospitalItemName(hospitalItem);
        } else if (linkedMedicine != null) {
            p.setStockSource(MedicationStockService.SOURCE_MEDICINE);
        }
        p.setQuantityPerDose(req.getQuantityPerDose() == null || req.getQuantityPerDose() < 1 ? 1 : req.getQuantityPerDose());
        p.setType(req.getType() != null ? req.getType() : "TABLET");
        p.setRoute(req.getRoute() != null ? req.getRoute() : "ORAL");
        p.setFoodTiming(com.hms.entity.FoodTiming.normalize(req.getFoodTiming()));
        p.setDosage(req.getDose());
        p.setFrequency(req.getFrequency());
        p.setDurationDays(req.getDurationDays());
        p.setStartDate(req.getStartDate());
        p.setStatus("ACTIVE");

        com.hms.entity.Prescription saved = prescriptionRepository.save(p);

        // Trigger notification to the assigned nurse, if any
        try {
            patientNurseAssignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(ipdId)
                .ifPresent(assignment -> {
                    notificationService.create(
                        assignment.getNurseUserId(),
                        hospitalId,
                        "PRESCRIPTION_ACTIVE",
                        "New Active Prescription",
                        "A new prescription for " + saved.getDosage() + " of " + saved.getMedicineName() + " has been added.",
                        "PRESCRIPTION",
                        saved.getId()
                    );
                });
        } catch (Exception e) {
            logger.error("Failed to trigger prescription add notification: {}", e.getMessage(), e);
        }

        // Standard prescriptions are now strictly informative (no auto-deduction/billing)
        return saved;
    }

    @Transactional
    public com.hms.entity.Prescription stopPrescription(Long prescriptionId) {
        String role = securityHelper.getCurrentUserRole();
        if (!ROLE_DOCTOR.equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can stop prescriptions");
        }

        Long hospitalId = securityHelper.getCurrentHospitalId();
        com.hms.entity.Prescription pres = prescriptionRepository.findByIdAndHospitalId(prescriptionId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));

        // Verify it belongs to an IPD by looking up medical record
        com.hms.entity.MedicalRecord mr = medicalRecordRepository.findByIdAndHospitalId(pres.getMedicalRecordId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Related medical record not found"));
        if (mr.getIpdAdmissionId() == null) throw new IllegalArgumentException("Prescription is not linked to an IPD admission");

        pres.setStatus("STOPPED");
        com.hms.entity.Prescription saved = prescriptionRepository.save(pres);

        // Trigger notification to the assigned nurse, if any
        try {
            patientNurseAssignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(mr.getIpdAdmissionId())
                .ifPresent(assignment -> {
                    notificationService.create(
                        assignment.getNurseUserId(),
                        saved.getHospitalId(),
                        "PRESCRIPTION_STOPPED",
                        "Prescription Stopped",
                        "The prescription for " + saved.getMedicineName() + " has been stopped.",
                        "PRESCRIPTION",
                        saved.getId()
                    );
                });
        } catch (Exception e) {
            logger.error("Failed to trigger stop prescription notification: {}", e.getMessage(), e);
        }

        return saved;
    }

    @Transactional
    public com.hms.entity.DischargeSummary planDischarge(Long ipdId, com.hms.dto.PlanDischargeRequest req) {
        String role = securityHelper.getCurrentUserRole();
        if (!ROLE_DOCTOR.equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can plan discharge");
        }

        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        // ADMITTED plans the discharge; DISCHARGE_PLANNED edits the summary before reception
        // confirms (a correction, a medicine added) without cancelling the plan first.
        if (!STATUS_ADMITTED.equalsIgnoreCase(ipd.getStatus()) && !STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException("Can only plan discharge for ADMITTED patients");
        }
        String dischargeType = IpdClinicalState.normaliseDischargeType(req.getDischargeType());
        DischargeSummaryRules.validate(req, dischargeType);

        // One summary per admission: planning again after a cancelled plan rewrites it.
        com.hms.entity.DischargeSummary ds = dischargeSummaryRepository.findByIpdAdmissionId(ipdId)
                .orElseGet(com.hms.entity.DischargeSummary::new);
        ds.setIpdAdmissionId(ipdId);
        DischargeSummaryRules.apply(req, ds);
        ds.setUpdatedAt(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), ipd.getHospitalId())
                .ifPresent(d -> ds.setPreparedByDoctorId(d.getId()));
        dischargeSummaryRepository.save(ds);

        // The doctor decides how the patient leaves; reception confirms it at discharge.
        ipd.setDischargeType(dischargeType);
        String planNote = req.getDischargeTypeNote();
        ipd.setDischargeNote(planNote == null || planNote.isBlank() ? null
                : planNote.trim().substring(0, Math.min(500, planNote.trim().length())));
        ipd.setStatus(STATUS_DISCHARGE_PLANNED);
        ipdAdmissionRepository.save(ipd);

        // Audit log
        try {
            auditLogService.logAction(
                    "IPD_DISCHARGE_PLANNED",
                    "Planned discharge for IPD Case: " + ipd.getIpdNumber() + ".",
                    securityHelper.getCurrentUserEmail(),
                    ipd.getHospitalId(),
                    "IPD",
                    ipd.getId().toString(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for IPD planned discharge", e);
        }

        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from planDischarge", e);
        }

        return ds;
    }

    /**
     * Takes back a planned discharge — the patient got worse, or the plan was a mistake. The
     * admission returns to ADMITTED; the discharge summary already written is kept and is
     * replaced when the discharge is planned again.
     */
    @Transactional
    public IpdAdmission cancelDischargePlan(Long ipdId, String reason) {
        String role = securityHelper.getCurrentUserRole();
        if (!ROLE_DOCTOR.equalsIgnoreCase(role) && !"HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only doctors can cancel a planned discharge");
        }
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException("This patient has no planned discharge to cancel");
        }
        revertDischargePlan(ipd, reason == null || reason.isBlank() ? "Discharge plan cancelled" : reason.trim());
        return ipd;
    }

    private void revertDischargePlan(IpdAdmission ipd, String why) {
        ipd.setStatus(STATUS_ADMITTED);
        ipd.setDischargeType(null);
        ipd.setDischargeNote(null);
        ipdAdmissionRepository.save(ipd);
        try {
            auditLogService.logAction("IPD_DISCHARGE_PLAN_CANCELLED",
                    why + " for IPD Case: " + ipd.getIpdNumber() + ".",
                    securityHelper.getCurrentUserEmail(), ipd.getHospitalId(), "IPD", ipd.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit discharge plan cancellation", e);
        }
        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast refresh after discharge plan cancellation", e);
        }
    }

    @Transactional
    public IpdAdmission confirmDischarge(Long ipdId) {
        return confirmDischarge(ipdId, null, null);
    }

    /**
     * Discharges the patient.
     *
     * @param dischargeType NORMAL (default), LAMA, REFERRED, EXPIRED or ABSCONDED. When null, the
     *                      type the doctor chose while planning the discharge is used.
     * @param dischargeNote referral hospital, cause / time of death, etc.
     */
    @Transactional
    public IpdAdmission confirmDischarge(Long ipdId, String dischargeType, String dischargeNote) {
        return confirmDischarge(ipdId, dischargeType, dischargeNote, null);
    }

    /**
     * @param refundMode CASH / CARD / UPI for returning an unused advance; required only when the
     *                   patient paid more than the final bill
     */
    @Transactional
    public IpdAdmission confirmDischarge(Long ipdId, String dischargeType, String dischargeNote, String refundMode) {
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        String type = IpdClinicalState.normaliseDischargeType(
                dischargeType != null && !dischargeType.isBlank() ? dischargeType : ipd.getDischargeType());
        String note = dischargeNoteFor(type, dischargeNote, ipd.getDischargeNote());
        Long hospitalId = ipd.getHospitalId();
        assertDischargeable(ipd, type);

        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        boolean hasBillingModule = hasBilling(hospital);
        // Bring bed-day charges up to date first, so the balance checked below is the final one.
        if (hasBillingModule) {
            billingSchedulerService.catchUpAdmission(ipd);
        }
        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);

        // A patient who died or absconded leaves regardless of dues; the bill stays open (not
        // CLOSED) so the balance is still visible and collectable from the family.
        boolean leavesWithDues = IpdClinicalState.dischargeAllowedWithDues(type);
        // An insurer's approved share is not the patient's to pay at the desk.
        java.math.BigDecimal insurerShare = hasBillingModule ? ipdInsuranceService.expectedFromInsurer(ipd) : java.math.BigDecimal.ZERO;
        if (hasBillingModule && !leavesWithDues) {
            assertNothingOwing(ipd, insurerShare);
        }

        if (hasBillingModule && bills != null) {
            returnUnusedAdvances(ipd, bills, insurerShare, refundMode);
        }
        completeActivePrescriptions(ipdId);
        ipdClinicalState.closeOutForDischarge(ipd, type);
        sendBedForCleaning(ipd, hospitalId);
        if (hasBillingModule && bills != null) {
            finaliseBills(bills, leavesWithDues || insurerShare.signum() > 0);
        }

        // ICU Phase 3: discharge ends any open ICU stay in the same transaction.
        icuStayService.onDischarged(ipd, IpdClinicalState.icuDisposition(type));

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.systemDefault());
        ipd.setStatus(STATUS_DISCHARGED);
        ipd.setDischargeDatetime(now);
        ipd.setDischargeType(type);
        ipd.setDischargeNote(note);
        ipdAdmissionRepository.save(ipd);

        if (IpdClinicalState.NORMAL.equals(type)) {
            bookFollowUp(ipd, hospital);
        }
        closeNurseAssignment(ipd, now);
        releaseBedHistory(ipd, now);
        auditDischarge(ipd, type);
        return ipd;
    }

    /** Who may discharge, and whether this patient can leave this way — checked before any write. */
    private void assertDischargeable(IpdAdmission ipd, String type) {
        if (!isFrontDesk(ipd.getHospitalId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only receptionists (or doctors under Solo / Both mode) can confirm discharge");
        }
        if (!IpdClinicalState.isActive(ipd)) {
            throw new IllegalArgumentException("Cannot discharge non-admitted patient");
        }
        // Open surgery / ventilator checks come before any write.
        ipdClinicalState.assertDischargeAllowed(ipd, type);
    }

    /** Returns any advance beyond the patient's share (the insurer's approved share excluded). */
    private void returnUnusedAdvances(IpdAdmission ipd, java.util.List<Billing> bills, java.math.BigDecimal insurerShare,
                                      String refundMode) {
        String reason = "Unused advance returned at discharge (" + ipd.getIpdNumber() + ")";
        for (Billing b : bills) {
            if (STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus()) || !java.util.Objects.equals(b.getHospitalId(), ipd.getHospitalId())) {
                continue;
            }
            billingService.refundUnusedAdvance(b, refundMode, reason,
                    "IPD".equalsIgnoreCase(b.getBillingType()) ? insurerShare : java.math.BigDecimal.ZERO);
        }
    }

    /** The discharge note: required for a death or a referral, kept from the plan when not given. */
    private static String dischargeNoteFor(String type, String given, String planned) {
        String note = given != null && !given.isBlank() ? given.trim() : planned;
        boolean required = IpdClinicalState.EXPIRED.equals(type) || IpdClinicalState.REFERRED.equals(type);
        if (required && (note == null || note.isBlank())) {
            throw new IllegalArgumentException(IpdClinicalState.EXPIRED.equals(type)
                    ? "Enter the time and cause of death"
                    : "Enter the hospital the patient is referred to");
        }
        return note != null && note.length() > 500 ? note.substring(0, 500) : note;
    }

    /** Reception, the admin, or a doctor where doctors run the desk (Solo / Both mode). */
    private boolean isFrontDesk(Long hospitalId) {
        String role = securityHelper.getCurrentUserRole();
        if ("RECEPTIONIST".equalsIgnoreCase(role) || "HOSPITAL_ADMIN".equalsIgnoreCase(role)) return true;
        if (!ROLE_DOCTOR.equalsIgnoreCase(role)) return false;
        com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId).orElse(null);
        return settings != null && ("SOLO".equalsIgnoreCase(settings.getReceptionMode())
                || "BOTH".equalsIgnoreCase(settings.getReceptionMode()));
    }

    /**
     * Every unpaid bill of this patient blocks discharge — the admission's IPD bill and also the
     * OPD bill from the consultation that led to it. Balances are discount- and refund-aware
     * (BillMoney), the same figures the billing screens show.
     */
    private void assertNothingOwing(IpdAdmission ipd, java.math.BigDecimal insurerShare) {
        java.util.Set<Long> thisStay = new java.util.HashSet<>();
        billingRepository.findByIpdAdmissionId(ipd.getId()).forEach(b -> thisStay.add(b.getId()));
        java.util.List<java.util.Map<String, Object>> owing = new java.util.ArrayList<>();
        java.math.BigDecimal share = insurerShare == null ? java.math.BigDecimal.ZERO : insurerShare;
        for (java.util.Map<String, Object> o : billingService.outstandingBillsForPatient(ipd.getPatientId(), ipd.getHospitalId())) {
            java.math.BigDecimal balance = (java.math.BigDecimal) o.get(BALANCE);
            if (thisStay.contains(o.get("billingId")) && share.signum() > 0) {
                java.math.BigDecimal covered = share.min(balance);
                share = share.subtract(covered);
                balance = balance.subtract(covered);
                if (balance.signum() <= 0) continue;
                o = new java.util.HashMap<>(o);
                o.put(BALANCE, balance);
            }
            owing.add(o);
        }
        if (owing.isEmpty()) return;
        String detail = owing.stream()
                .map(o -> o.get("customId") + " (" + o.get("billingType") + ") \u20B9"
                        + ((java.math.BigDecimal) o.get(BALANCE)).stripTrailingZeros().toPlainString())
                .collect(java.util.stream.Collectors.joining(", "));
        throw new IllegalArgumentException("Outstanding balance must be settled before discharge: " + detail);
    }

    private void completeActivePrescriptions(Long ipdId) {
        java.util.List<com.hms.entity.Prescription> active = prescriptionRepository.findByIpdAdmissionIdAndStatus(ipdId, "ACTIVE");
        if (active == null) return;
        for (com.hms.entity.Prescription pr : active) {
            pr.setStatus("COMPLETED");
            prescriptionRepository.save(pr);
        }
    }

    /** Vacated beds await cleaning before reuse; BedStatusService queues the cleaning task. */
    private void sendBedForCleaning(IpdAdmission ipd, Long hospitalId) {
        if (ipd.getBedId() == null) {
            throw new IllegalStateException("Active IPD admission has no bed to release");
        }
        Bed bed = bedRepository.findByBedIdAndHospitalIdForUpdate(ipd.getBedId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        if (!com.hms.entity.BedStatus.OCCUPIED.equalsIgnoreCase(bed.getStatus())
                || !ipd.getId().equals(bed.getCurrentIpdAdmissionId())) {
            throw new ConflictException("IPD bed is not occupied by this admission");
        }
        bedStatusService.changeLocked(bed, com.hms.entity.BedStatus.CLEANING, "IPD discharge");
    }

    /** Closes the admission's bills; one left with dues (death / absconded) stays open to collect. */
    private void finaliseBills(java.util.List<Billing> bills, boolean leavesWithDues) {
        for (Billing b : bills) {
            boolean keepOpen = STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus())
                    || (leavesWithDues && billingService.moneyFor(b).balance().signum() > 0);
            if (!keepOpen) {
                b.setPaymentStatus("CLOSED");
                billingRepository.save(b);
            }
        }
    }

    /** The patient drops off the nurse's "My Patients" list. */
    private void closeNurseAssignment(IpdAdmission ipd, LocalDateTime now) {
        patientNurseAssignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(ipd.getId())
                .ifPresent(assignment -> {
                    assignment.setIsActive(false);
                    assignment.setUnassignedAt(now);
                    patientNurseAssignmentRepository.save(assignment);
                });
    }

    private void releaseBedHistory(IpdAdmission ipd, LocalDateTime now) {
        com.hms.entity.IpdBedHistory activeHist = ipdBedHistoryRepository
                .findByIpdAdmissionIdAndReleasedAtIsNull(ipd.getId())
                .orElseThrow(() -> new IllegalStateException("IPD admission has no active bed history to release"));
        activeHist.setReleasedAt(now);
        ipdBedHistoryRepository.save(activeHist);
    }

    private void auditDischarge(IpdAdmission ipd, String type) {
        try {
            auditLogService.logAction("IPD_DISCHARGED",
                    "Confirmed discharge (" + type + ") for IPD Case: " + ipd.getIpdNumber() + ".",
                    securityHelper.getCurrentUserEmail(), ipd.getHospitalId(), "IPD", ipd.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for IPD discharge confirmation", e);
        }
        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from confirmDischarge", e);
        }
    }

    /**
     * Cancels an admission made by mistake — the wrong patient, a duplicate, or a patient who
     * left before being taken to the bed. Unlike a discharge, nothing about it is a real stay:
     * the bed is freed (sent for cleaning), the IPD bill is voided (refunding anything collected,
     * admin only), and the doctor's admission request goes back to the "Requested" list.
     *
     * <p>Records already written on the admission are kept and stay readable under the cancelled
     * admission. What cannot be undone here is refused instead: a dose given from stock (undo it
     * on the medication chart first) and a surgery that is booked or under way (cancel it in OT).
     *
     * @param refundMode CASH / CARD / UPI — required only when money was collected
     */
    @Transactional
    public IpdAdmission cancelAdmission(Long ipdId, String reason, String refundMode) {
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        Long hospitalId = ipd.getHospitalId();
        if (!isFrontDesk(hospitalId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only reception or the admin can cancel an admission");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Enter why the admission is being cancelled");
        }
        String why = reason.trim().length() > 200 ? reason.trim().substring(0, 200) : reason.trim();
        if (!STATUS_ADMITTED.equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException(STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(ipd.getStatus())
                    ? "A discharge is already planned for this patient, so the stay was real. Discharge them instead."
                    : "Only a current admission can be cancelled.");
        }
        assertCancellable(ipd);

        java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipdId);
        if (bills != null && !bills.isEmpty()) {
            billingService.voidForCancelledAdmission(bills, why, refundMode);
        }

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.systemDefault());
        for (com.hms.entity.Prescription pr : prescriptionRepository.findByIpdAdmissionIdAndStatus(ipdId, "ACTIVE")) {
            pr.setStatus("STOPPED");
            prescriptionRepository.save(pr);
        }
        ipdClinicalState.stopRunningInfusions(ipd, "Admission cancelled");
        sendBedForCleaning(ipd, hospitalId);
        icuStayService.onDischarged(ipd, com.hms.entity.IcuStay.DISP_CANCELLED);
        reopenAdmissionRequest(ipd, hospitalId);

        ipd.setStatus(STATUS_CANCELLED);
        ipd.setCancelReason(why);
        ipd.setCancelledAt(now);
        ipd.setCancelledBy(securityHelper.getCurrentUserRole() + " (" + securityHelper.getCurrentUserEmail() + ")");
        ipdAdmissionRepository.save(ipd);

        closeNurseAssignment(ipd, now);
        releaseBedHistory(ipd, now);
        try {
            auditLogService.logAction("IPD_ADMISSION_CANCELLED",
                    "Cancelled IPD admission " + ipd.getIpdNumber() + ": " + why,
                    securityHelper.getCurrentUserEmail(), hospitalId, "IPD", ipd.getId().toString(), why);
        } catch (Exception e) {
            logger.warn("Failed to audit IPD admission cancellation", e);
        }
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast refresh after IPD admission cancellation", e);
        }
        return ipd;
    }

    /** Refuses a cancellation that would hide real care: stock given, or surgery booked. */
    private void assertCancellable(IpdAdmission ipd) {
        boolean stockGiven = medicationAdministrationRepository
                .findByIpdAdmissionIdAndIsActiveTrueOrderByCreatedAtDesc(ipd.getId()).stream()
                .anyMatch(m -> "GIVEN".equalsIgnoreCase(m.getStatus()) && !Boolean.TRUE.equals(m.getOwnSupply()));
        if (stockGiven) {
            throw new IllegalArgumentException("Medicines from stock were given on this admission. Undo those doses on "
                    + "the medication chart first, or discharge the patient instead of cancelling.");
        }
        var open = ipdClinicalState.openSurgeries(ipd.getId());
        if (!open.isEmpty()) {
            var booked = open.get(0);
            throw new IllegalArgumentException("Surgery \"" + booked.getProcedureName() + "\" is "
                    + booked.getStatus().replace('_', ' ').toLowerCase(java.util.Locale.ROOT)
                    + ". Cancel it in OT before cancelling the admission.");
        }
        if (ipdClinicalState.isVentilated(ipd.getId(), ipd.getHospitalId())) {
            throw new IllegalArgumentException("The patient is on a ventilator. Record ventilation as OFF first.");
        }
    }

    /** The doctor's admission recommendation shows under "Requested" again, to be admitted correctly. */
    private void reopenAdmissionRequest(IpdAdmission ipd, Long hospitalId) {
        if (ipd.getSourceOpdId() == null) return;
        opdRepository.findByIdAndHospitalIdWithPatientAndDoctorForUpdate(ipd.getSourceOpdId(), hospitalId)
                .filter(opd -> Opd.Status.IN_IPD.equals(opd.getStatus()))
                .ifPresent(opd -> {
                    opd.setStatus(Opd.Status.COMPLETED);
                    opdRepository.save(opd);
                });
    }

    private com.hms.dto.IpdAdmissionDetailsDTO.DischargeSummaryDTO summaryDto(com.hms.entity.DischargeSummary ds, Long hospitalId) {
        com.hms.dto.IpdAdmissionDetailsDTO.DischargeSummaryDTO d = new com.hms.dto.IpdAdmissionDetailsDTO.DischargeSummaryDTO();
        d.finalDiagnosis = ds.getFinalDiagnosis();
        d.treatmentGiven = ds.getTreatmentGiven();
        d.dischargeNotes = ds.getDischargeNotes();
        d.conditionAtDischarge = ds.getConditionAtDischarge();
        d.proceduresDone = ds.getProceduresDone();
        d.investigations = ds.getInvestigations();
        d.advice = ds.getAdvice();
        d.dischargeMedications = DischargeSummaryRules.medicines(ds);
        d.followUpDate = ds.getFollowUpDate();
        d.followUpTime = ds.getFollowUpTime();
        d.updatedAt = ds.getUpdatedAt();
        if (ds.getFollowUpAppointmentId() != null) {
            appointmentRepository.findByIdAndHospitalIdAndIsActiveTrue(ds.getFollowUpAppointmentId(), hospitalId)
                    .ifPresent(a -> d.followUpAppointmentId = a.getCustomId());
        }
        return d;
    }

    /**
     * Books the follow-up visit the doctor asked for, with the attending doctor, when the patient
     * goes home. Best effort by design: a full diary must never stop a discharge, so a slot is
     * looked for in 15-minute steps from the requested time and, if none is free within two
     * hours, nothing is booked and reception books it by hand. Skipped when the hospital has no
     * appointments, or the date has already passed.
     */
    private void bookFollowUp(IpdAdmission ipd, com.hms.entity.Hospital hospital) {
        com.hms.entity.DischargeSummary ds = dischargeSummaryRepository.findByIpdAdmissionId(ipd.getId()).orElse(null);
        if (ds == null || ds.getFollowUpDate() == null || ds.getFollowUpAppointmentId() != null || ipd.getDoctorId() == null) return;
        if (hospital == null || !com.hms.entitlement.EntitlementRegistry.resolve(hospital.getModules())
                .contains(com.hms.entitlement.EntitlementRegistry.APPOINTMENTS)) return;
        java.time.LocalDate date = ds.getFollowUpDate();
        if (date.isBefore(java.time.LocalDate.now(java.time.ZoneId.systemDefault()))) return;

        java.util.Set<java.time.LocalTime> taken = new java.util.HashSet<>();
        for (com.hms.entity.Appointment a : appointmentRepository.findByDoctorIdAndAppointmentDateAndIsActiveTrue(ipd.getDoctorId(), date)) {
            if (!STATUS_CANCELLED.equalsIgnoreCase(a.getStatus()) && a.getAppointmentTime() != null) taken.add(a.getAppointmentTime());
        }
        java.time.LocalTime time = ds.getFollowUpTime() != null ? ds.getFollowUpTime() : java.time.LocalTime.of(10, 0);
        for (int step = 0; step < 8 && taken.contains(time); step++) {
            time = time.plusMinutes(15);
        }
        if (taken.contains(time)) {
            logger.info("No free follow-up slot for IPD {} on {}; reception books it by hand", ipd.getIpdNumber(), date);
            return;
        }
        com.hms.entity.Appointment appt = new com.hms.entity.Appointment();
        appt.setHospitalId(ipd.getHospitalId());
        appt.setPatientId(ipd.getPatientId());
        appt.setDoctorId(ipd.getDoctorId());
        appt.setAppointmentDate(date);
        appt.setAppointmentTime(time);
        appt.setStatus("SCHEDULED");
        appt.setNotes("Follow-up after discharge (" + ipd.getIpdNumber() + ")");
        appt.setIsActive(true);
        com.hms.entity.Appointment saved = appointmentRepository.save(appt);
        ds.setFollowUpAppointmentId(saved.getId());
        ds.setFollowUpTime(time);
        dischargeSummaryRepository.save(ds);
        try {
            auditLogService.logAction("APPOINTMENT_CREATED", "Follow-up after discharge (" + ipd.getIpdNumber() + ") booked for "
                    + date + " " + time, securityHelper.getCurrentUserEmail(), ipd.getHospitalId(), "APPOINTMENT", saved.getPublicId(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit the follow-up booking", e);
        }
    }

    /** The printable discharge summary; there is one once the doctor has planned the discharge. */
    @Transactional(readOnly = true)
    public java.io.ByteArrayInputStream dischargeSummaryPdf(Long ipdId) {
        IpdAdmission ipd = requireOwnedAdmission(ipdId);
        com.hms.entity.DischargeSummary ds = dischargeSummaryRepository.findByIpdAdmissionId(ipdId)
                .filter(x -> STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(ipd.getStatus()) || STATUS_DISCHARGED.equalsIgnoreCase(ipd.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("The doctor has not written the discharge summary yet. Plan the discharge first."));
        Long hospitalId = ipd.getHospitalId();
        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));
        com.hms.entity.Patient patient = patientRepository.findByIdAndHospitalId(ipd.getPatientId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException(PATIENT_NOT_FOUND));
        com.hms.service.pdf.DischargeSummaryPrint print = new com.hms.service.pdf.DischargeSummaryPrint();
        print.ipd = ipd;
        print.summary = ds;
        print.medicines = DischargeSummaryRules.medicines(ds);
        print.doctor = ipd.getDoctorId() == null ? null : doctorRepository.findByIdAndHospitalId(ipd.getDoctorId(), hospitalId).orElse(null);
        print.ward = wardRepository.findByWardIdAndHospitalId(ipd.getWardId(), hospitalId).map(com.hms.entity.Ward::getWardName).orElse(null);
        print.bed = bedRepository.findByBedIdAndHospitalId(ipd.getBedId(), hospitalId).map(Bed::getBedCode).orElse(null);
        print.surgeries = ipdClinicalState.completedSurgeries(ipd.getId());
        if (ds.getFollowUpAppointmentId() != null) {
            appointmentRepository.findByIdAndHospitalIdAndIsActiveTrue(ds.getFollowUpAppointmentId(), hospitalId)
                    .ifPresent(a -> print.followUpTime = a.getAppointmentTime());
        }
        try {
            auditLogService.logAction("DISCHARGE_SUMMARY_PRINTED", "Printed the discharge summary of " + ipd.getIpdNumber(),
                    securityHelper.getCurrentUserEmail(), hospitalId, "IPD", ipd.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit the discharge summary print", e);
        }
        return clinicalPdfService.generateDischargeSummaryPdf(hospital, patient, print);
    }

    /** Charges the doctor-visit fee for a round: once per doctor per day, never inside a package. */
    private void chargeDoctorVisit(IpdAdmission ipd, com.hms.entity.Doctor doctor, Long hospitalId) {
        if (!hasBillingModule(hospitalId)) return;
        java.math.BigDecimal fee = ipdBillingSettingsService.forHospital(hospitalId).getDoctorVisitFee();
        if (fee == null || fee.signum() <= 0) return;
        int day = IpdChargePeriods.periodAt(ipd, LocalDateTime.now(java.time.ZoneId.systemDefault()));
        if (day <= packageDays(ipd)) return;
        Billing bill = liveIpdBill(ipd);
        if (bill == null) return;
        String line = IpdChargePeriods.visitLine(day, com.hms.util.DoctorNames.withTitle(doctor.getName()));
        boolean already = billingItemRepository.findByBillingId(bill.getId()).stream()
                .anyMatch(it -> line.equals(it.getDescription()));
        if (already) return;
        addBillLine(bill, line, fee);
        billingService.recalculateTotal(bill.getId());
    }

    /** Takes an advance on the admission's IPD bill (reception, the admin, or a doctor at the desk). */
    @Transactional
    public com.hms.entity.BillingPayment takeAdvance(Long ipdId, java.math.BigDecimal amount, String mode, String reference) {
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!isFrontDesk(ipd.getHospitalId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only reception or the admin can take an advance");
        }
        IpdClinicalState.requireActive(ipd);
        Billing bill = liveIpdBill(ipd);
        if (bill == null) throw new IllegalArgumentException("This admission has no open IPD bill to take an advance on");
        com.hms.entity.BillingPayment p = billingService.recordAdvance(bill.getId(), amount, mode, reference);
        broadcast(ipd.getHospitalId());
        return p;
    }

    /** The printable receipt of one advance. */
    @Transactional(readOnly = true)
    public java.io.ByteArrayInputStream advanceReceiptPdf(Long paymentId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        com.hms.entity.BillingPayment p = billingPaymentRepository.findByIdAndHospitalId(paymentId, hospitalId)
                .filter(x -> BillingService.KIND_ADVANCE.equals(x.getKind()))
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));
        Billing bill = billingRepository.findByIdAndHospitalId(p.getBillingId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));
        IpdAdmission ipd = bill.getIpdAdmissionId() == null ? null
                : ipdAdmissionRepository.findByIdAndHospitalId(bill.getIpdAdmissionId(), hospitalId).orElse(null);
        com.hms.entity.Patient patient = patientRepository.findByIdAndHospitalId(bill.getPatientId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException(PATIENT_NOT_FOUND));
        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId).orElse(null);
        java.math.BigDecimal total = billingService.advancesOf(bill.getId()).stream()
                .filter(a -> a.getId() <= p.getId())
                .map(com.hms.entity.BillingPayment::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return billingPdfService.generateAdvanceReceiptPdf(hospital, patient, ipd, bill, p, total);
    }

    /**
     * Applies a package to a stay already under way. From now on its days take no bed, nursing
     * or visit charges; what was already charged for those days is credited back in one
     * "Package adjustment" line, so the bill shows exactly what the package replaced.
     */
    @Transactional
    public IpdAdmission applyPackage(Long ipdId, Long packageId) {
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        if (!isFrontDesk(ipd.getHospitalId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only reception or the admin can apply a package");
        }
        IpdClinicalState.requireActive(ipd);
        if (ipd.getPackageId() != null) {
            throw new IllegalArgumentException("This stay already has a package. Ask the admin to remove it first.");
        }
        if (packageId == null) throw new IllegalArgumentException("Select a package");
        com.hms.entity.IpdPackage pkg = ipdPackageService.requireActive(packageId);
        Billing bill = liveIpdBill(ipd);
        if (bill == null) throw new IllegalArgumentException("This admission has no open IPD bill");

        java.math.BigDecimal covered = java.math.BigDecimal.ZERO;
        for (com.hms.entity.BillingItem it : billingItemRepository.findByBillingId(bill.getId())) {
            Integer day = IpdChargePeriods.periodOfLine(ipd, it.getDescription());
            if (day != null && day <= pkg.getIncludedDays() && it.getAmount() != null) covered = covered.add(it.getAmount());
        }
        addBillLine(bill, packageLine(pkg), pkg.getPrice());
        if (covered.signum() > 0) {
            addBillLine(bill, PACKAGE_ADJUSTMENT + pkg.getName(), covered.negate());
        }
        billingService.recalculateTotal(bill.getId());
        ipd.setPackageId(pkg.getId());
        ipd.setPackageAppliedAt(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        ipdAdmissionRepository.save(ipd);
        auditIpd(ipd, "IPD_PACKAGE_APPLIED", "Package " + pkg.getName() + " (" + pkg.getPrice() + ") applied to "
                + ipd.getIpdNumber() + (covered.signum() > 0 ? "; " + covered + " of charges credited" : ""));
        return ipd;
    }

    private static final String PACKAGE_ADJUSTMENT = "Package adjustment - charges covered by ";

    /**
     * Takes a package off (admin only — it changes what the patient owes). The package price and
     * its adjustment are reversed by opposite lines (nothing is deleted from the bill); the bed
     * and nursing days it covered are charged again when the bill is next brought up to date.
     * Doctor visits it covered are not charged retroactively.
     */
    @Transactional
    public IpdAdmission removePackage(Long ipdId, String reason) {
        if (!"HOSPITAL_ADMIN".equalsIgnoreCase(securityHelper.getCurrentUserRole())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the hospital admin can remove a package");
        }
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        IpdClinicalState.requireActive(ipd);
        if (ipd.getPackageId() == null) throw new IllegalArgumentException("This stay has no package");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Enter why the package is removed");
        com.hms.entity.IpdPackage pkg = ipdPackageService.find(ipd.getPackageId(), ipd.getHospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
        Billing bill = liveIpdBill(ipd);
        if (bill != null) {
            reversePackageLines(ipd, bill, pkg);
        }
        ipd.setPackageId(null);
        ipd.setPackageAppliedAt(null);
        ipdAdmissionRepository.save(ipd);
        auditIpd(ipd, "IPD_PACKAGE_REMOVED", "Package " + pkg.getName() + " removed from " + ipd.getIpdNumber() + ": " + reason.trim());
        return ipd;
    }

    /** Opposite lines for the package price and its adjustment; day 1's bed charge back if missing. */
    private void reversePackageLines(IpdAdmission ipd, Billing bill, com.hms.entity.IpdPackage pkg) {
        java.math.BigDecimal price = java.math.BigDecimal.ZERO;
        java.math.BigDecimal adjustment = java.math.BigDecimal.ZERO;
        boolean hasBedPrice = false;
        for (com.hms.entity.BillingItem it : billingItemRepository.findByBillingId(bill.getId())) {
            String d = it.getDescription() == null ? "" : it.getDescription();
            java.math.BigDecimal amt = it.getAmount() == null ? java.math.BigDecimal.ZERO : it.getAmount();
            if (d.startsWith("Package: ") || d.startsWith(PACKAGE_REMOVED)) price = price.add(amt);
            if (d.startsWith(PACKAGE_ADJUSTMENT) || d.startsWith(PACKAGE_ADJUSTMENT_REVERSED)) adjustment = adjustment.add(amt);
            hasBedPrice |= IpdChargePeriods.BED_PRICE.equals(d);
        }
        if (price.signum() != 0) addBillLine(bill, PACKAGE_REMOVED + pkg.getName(), price.negate());
        if (adjustment.signum() != 0) addBillLine(bill, PACKAGE_ADJUSTMENT_REVERSED, adjustment.negate());
        if (!hasBedPrice) {
            Long wardId = ipdBedHistoryRepository.findByIpdAdmissionIdOrderByAssignedAtAsc(ipd.getId()).stream()
                    .findFirst().map(com.hms.entity.IpdBedHistory::getWardId).orElse(ipd.getWardId());
            wardRepository.findByWardIdAndHospitalId(wardId, ipd.getHospitalId())
                    .map(com.hms.entity.Ward::getBedPrice).filter(bp -> bp.signum() > 0)
                    .ifPresent(bp -> addBillLine(bill, IpdChargePeriods.BED_PRICE, bp));
        }
        billingService.recalculateTotal(bill.getId());
    }

    private static final String PACKAGE_REMOVED = "Package removed: ";
    private static final String PACKAGE_ADJUSTMENT_REVERSED = "Package adjustment reversed";

    private void auditIpd(IpdAdmission ipd, String action, String details) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(), ipd.getHospitalId(),
                    "IPD", ipd.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit {}", action, e);
        }
        broadcast(ipd.getHospitalId());
    }

    private void broadcast(Long hospitalId) {
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast refresh", e);
        }
    }

    private boolean isAttendingDoctor(IpdAdmission ipd) {
        if (!ROLE_DOCTOR.equalsIgnoreCase(securityHelper.getCurrentUserRole())) return false;
        return doctorRepository.findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), ipd.getHospitalId())
                .map(me -> me.getId().equals(ipd.getDoctorId())).orElse(false);
    }

    /**
     * Hands an inpatient over to another doctor: the attending doctor is on leave, has left, or
     * the patient was admitted under the wrong name. Without this the patient stayed on the old
     * doctor's list, and no other doctor could find them.
     *
     * <p>The front desk (reception, the admin, a doctor covering the desk) may move any patient; a
     * doctor may hand over their own.
     */
    @org.springframework.transaction.annotation.Transactional
    public IpdAdmission changeDoctor(Long ipdId, Long newDoctorId) {
        if (newDoctorId == null) throw new IllegalArgumentException("Select the doctor who takes over the patient.");
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);
        Long hospitalId = ipd.getHospitalId();
        if (!isFrontDesk(hospitalId) && !isAttendingDoctor(ipd)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the patient's own doctor, reception or the admin can hand this patient over.");
        }
        IpdClinicalState.requireActive(ipd);
        com.hms.entity.Doctor doctor = doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(newDoctorId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("That doctor is not available in this hospital. Select another doctor."));
        if (doctor.getId().equals(ipd.getDoctorId())) {
            throw new IllegalArgumentException(com.hms.util.DoctorNames.withTitle(doctor.getName()) + " is already this patient's doctor.");
        }
        String from = ipd.getDoctorId() == null ? "no doctor"
                : doctorRepository.findById(ipd.getDoctorId()).map(com.hms.entity.Doctor::getName).orElse("doctor #" + ipd.getDoctorId());
        ipd.setDoctorId(doctor.getId());
        IpdAdmission saved = ipdAdmissionRepository.save(ipd);
        // The IPD bill names the attending doctor on its print-out.
        billingRepository.findByIpdAdmissionId(ipd.getId()).stream()
                .filter(b -> java.util.Objects.equals(b.getHospitalId(), hospitalId))
                .filter(b -> !STATUS_CANCELLED.equalsIgnoreCase(b.getPaymentStatus()))
                .forEach(b -> {
                    b.setDoctorId(doctor.getId());
                    billingRepository.save(b);
                });
        try {
            auditLogService.logAction("IPD_DOCTOR_CHANGED", "IPD " + ipd.getIpdNumber() + " handed over from " + from
                    + " to " + doctor.getName(), securityHelper.getCurrentUserEmail(), hospitalId, "IPD", String.valueOf(ipd.getId()), null);
        } catch (Exception e) {
            logger.warn("Failed to audit IPD doctor change", e);
        }
        try {
            webSocketHandler.broadcast(hospitalId, "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast after IPD doctor change", e);
        }
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public IpdAdmission changeBed(Long ipdId, Long newBedId) {
        return changeBed(ipdId, newBedId, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public IpdAdmission changeBed(Long ipdId, Long newBedId, Long expectedWardId) {
        IpdAdmission ipd = requireOwnedAdmissionForUpdate(ipdId);

        String role = securityHelper.getCurrentUserRole();
        Long hospitalId = ipd.getHospitalId();
        com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId).orElse(null);
        boolean isSoloOrBoth = settings != null &&
            ("SOLO".equalsIgnoreCase(settings.getReceptionMode()) || "BOTH".equalsIgnoreCase(settings.getReceptionMode()));

        if (!"RECEPTIONIST".equalsIgnoreCase(role) && 
            !"HOSPITAL_ADMIN".equalsIgnoreCase(role) && 
            !(ROLE_DOCTOR.equalsIgnoreCase(role) && isSoloOrBoth)) {
            throw new org.springframework.security.access.AccessDeniedException("Only receptionists (or doctors under Solo / Both mode) can change beds");
        }

        if (!STATUS_ADMITTED.equalsIgnoreCase(ipd.getStatus()) && !STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(ipd.getStatus())) {
            throw new IllegalArgumentException("Bed change allowed only for active admissions");
        }

        Long oldBedId = ipd.getBedId();
        if (oldBedId == null) throw new IllegalStateException("Active IPD admission has no current bed");
        if (oldBedId.equals(newBedId)) throw new IllegalArgumentException("Patient is already assigned to the selected bed");

        // A stable order prevents two opposing transfers from deadlocking. The admission row
        // was locked first, followed by its bed resources.
        Long firstBedId = Math.min(oldBedId, newBedId);
        Long secondBedId = Math.max(oldBedId, newBedId);
        Bed first = bedRepository.findByBedIdAndHospitalIdForUpdate(firstBedId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        Bed second = bedRepository.findByBedIdAndHospitalIdForUpdate(secondBedId, hospitalId)
                // Same wording as the first lookup on purpose. The two beds are locked in id
                // order, so which of them is "the new one" depends on the ids involved; a
                // distinct message here would let a caller tell a foreign bed from a missing one
                // by watching which sentence comes back (C4).
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
        Bed oldBed = oldBedId.equals(firstBedId) ? first : second;
        Bed newBed = newBedId.equals(firstBedId) ? first : second;
        if (expectedWardId != null && !expectedWardId.equals(newBed.getWardId())) {
            throw new IllegalArgumentException("Selected bed does not belong to the destination ward");
        }
        if (!com.hms.entity.BedStatus.AVAILABLE.equalsIgnoreCase(newBed.getStatus())) {
            throw new ConflictException("Requested bed is no longer available");
        }
        com.hms.entity.BedReservation hold = bedReservationService.claimFor(hospitalId, newBed.getBedId(), ipd.getPatientId(), null);
        if (!com.hms.entity.BedStatus.OCCUPIED.equalsIgnoreCase(oldBed.getStatus())
                || !ipd.getId().equals(oldBed.getCurrentIpdAdmissionId())) {
            throw new ConflictException("Current IPD bed is not occupied by this admission");
        }

        boolean intoCriticalCare = icuStayService.isCriticalCare(newBed.getWardId(), hospitalId);
        if (!intoCriticalCare && ipdClinicalState.isVentilated(ipd.getId(), hospitalId)) {
            throw new IllegalArgumentException("The patient is on a ventilator and can only move to another "
                    + "ICU / HDU bed. Record ventilation as OFF before moving them to a ward.");
        }
        String inSurgery = ipdClinicalState.surgeryInProgress(ipd.getId());
        if (inSurgery != null) {
            throw new IllegalArgumentException("The patient is in surgery (" + inSurgery
                    + "). Change the bed after the surgery is completed.");
        }

        String oldBedCode = oldBed.getBedCode();
        com.hms.entity.Ward oldWard = wardRepository.findByWardIdAndHospitalId(oldBed.getWardId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Ward not found"));
        String oldWardName = oldWard.getWardName();
        bedStatusService.changeLocked(oldBed, com.hms.entity.BedStatus.CLEANING, "Bed transfer (vacated)");

        Bed occupiedNewBed = bedStatusService.changeLocked(newBed, com.hms.entity.BedStatus.OCCUPIED, "Bed transfer");
        occupiedNewBed.setCurrentIpdAdmissionId(ipd.getId());
        bedRepository.save(occupiedNewBed);

        String newBedCode = newBed.getBedCode();
        com.hms.entity.Ward newWardEntity = wardRepository.findByWardIdAndHospitalId(newBed.getWardId(), hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Ward not found"));
        String newWardName = newWardEntity.getWardName();

        // A chargeable upgrade must post together with the transfer; silently losing it is a
        // financial inconsistency, not an optional notification.
        {
            java.math.BigDecimal oldRate = oldWard.getBedPrice() != null ? oldWard.getBedPrice() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal newRate = newWardEntity.getBedPrice() != null ? newWardEntity.getBedPrice() : java.math.BigDecimal.ZERO;

            java.util.List<Billing> bills = billingRepository.findByIpdAdmissionId(ipd.getId());
            Billing bill = (bills != null && !bills.isEmpty()) ? bills.get(0) : null;

            // Charge only the part of the new rate not already billed for this admission. Using
            // newRate - oldRate charged a ward -> ICU -> ward -> ICU patient the full upgrade on
            // every return to ICU, because stepping down never credits it back. The admission
            // Bed Price plus earlier adjustments is what has been charged so far; the old ward's
            // rate is the floor, so a legacy bill without those lines keeps the old behaviour.
            java.math.BigDecimal alreadyCharged = oldRate;
            if (bill != null) {
                java.math.BigDecimal billed = java.math.BigDecimal.ZERO;
                for (com.hms.entity.BillingItem it : billingItemRepository.findByBillingId(bill.getId())) {
                    if (it.getAmount() != null && ("Bed Price".equals(it.getDescription())
                            || "Bed Upgrade Price Adjustment".equals(it.getDescription()))) {
                        billed = billed.add(it.getAmount());
                    }
                }
                alreadyCharged = alreadyCharged.max(billed);
            }

            java.math.BigDecimal diff = newRate.subtract(alreadyCharged);
            // A package covers the bed for its days, whatever the ward.
            boolean inPackage = IpdChargePeriods.periodAt(ipd, LocalDateTime.now(java.time.ZoneId.systemDefault())) <= packageDays(ipd);
            // If positive difference (upgrade), add to bill immediately
            if (diff.compareTo(java.math.BigDecimal.ZERO) > 0 && !inPackage) {
                if (bill != null) {
                    com.hms.entity.BillingItem item = new com.hms.entity.BillingItem();
                    item.setBillingId(bill.getId());
                    item.setHospitalId(bill.getHospitalId());
                    item.setDescription("Bed Upgrade Price Adjustment");
                    item.setAmount(diff);
                    billingItemRepository.save(item);

                    billingService.recalculateTotal(bill.getId());
                }
            }
        }

        ipd.setBedId(newBed.getBedId());
        ipd.setWardId(newBed.getWardId());
        bedReservationService.markUsed(hold, ipd.getId());
        
        IpdAdmission saved = ipdAdmissionRepository.save(ipd);

        // ICU Phase 3: one call covers every case, because they are one question — is the patient
        // in critical care now, and were they before? Ward -> ICU opens, ICU -> ward closes,
        // ICU -> a different ICU closes and reopens (readmission is real), and a bed change inside
        // the same unit does nothing, since the stay is bounded by the ward and ipd_bed_history
        // already records the bed. Same transaction as the transfer.
        icuStayService.onWardSettled(saved, oldWard.getWardId(), null, oldWard.getWardId(), null);
        // A patient moved into critical care is no longer going home: take back a planned discharge.
        if (intoCriticalCare && STATUS_DISCHARGE_PLANNED.equalsIgnoreCase(saved.getStatus())) {
            revertDischargePlan(saved, "Discharge plan cancelled: patient moved to critical care");
        }

        // A cross-ward transfer ends primary ownership; destination visibility is derived
        // from the admission's new ward and therefore needs no manufactured row.
        if (!java.util.Objects.equals(oldWard.getWardId(), newWardEntity.getWardId())) {
            patientNurseAssignmentRepository.findByIpdAdmissionIdAndIsActiveTrue(ipd.getId()).ifPresent(assignment -> {
                assignment.setIsActive(false);
                assignment.setUnassignedAt(LocalDateTime.now());
                patientNurseAssignmentRepository.save(assignment);
            });
        }

        // Transfer history is required for a defensible inpatient bed record.
        java.util.Optional<com.hms.entity.IpdBedHistory> activeHistOpt = ipdBedHistoryRepository
                .findByIpdAdmissionIdAndReleasedAtIsNull(ipd.getId());
        if (activeHistOpt.isEmpty()) {
            throw new IllegalStateException("IPD admission has no active bed history to transfer");
        }
        com.hms.entity.IpdBedHistory activeHist = activeHistOpt.get();
        activeHist.setReleasedAt(LocalDateTime.now());
        ipdBedHistoryRepository.save(activeHist);

        com.hms.entity.IpdBedHistory newHist = new com.hms.entity.IpdBedHistory();
        newHist.setIpdAdmissionId(ipd.getId());
        newHist.setWardId(newBed.getWardId());
        newHist.setBedId(newBed.getBedId());
        newHist.setAssignedAt(LocalDateTime.now());
        ipdBedHistoryRepository.save(newHist);

        try {
            String details = "Transferred from Bed " + oldBedCode + " (" + oldWardName + ") to Bed " + newBedCode + " (" + newWardName + ").";
            auditLogService.logAction(
                    "BED_CHANGED",
                    details,
                    securityHelper.getCurrentUserEmail(),
                    hospitalId,
                    "IPD",
                    ipd.getId().toString(),
                    null);
        } catch (Exception e) {
            logger.warn("Failed to create audit log for bed change", e);
        }

        try {
            webSocketHandler.broadcast(ipd.getHospitalId(), "{\"type\":\"REFRESH_DATA\"}");
        } catch (Exception e) {
            logger.warn("Failed to broadcast WebSocket refresh data from changeBed", e);
        }
        return saved;
    }
}
