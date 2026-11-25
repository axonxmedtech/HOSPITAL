package com.hms.service.hospital;

import com.hms.dto.MedicationAdminRequest;
import com.hms.entity.IpdAdmission;
import com.hms.entity.MedicationAdministration;
import com.hms.entity.Prescription;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.MedicationAdministrationRepository;
import com.hms.repository.PrescriptionRepository;
import com.hms.security.NurseAccessGuard;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * MedicationAdministrationService - records nurse administration against the
 * doctor's ACTIVE prescriptions (Phase 1 Nurse module). The prescription is the
 * single source of truth for what to give; this only records what happened.
 * Corrections are appended as new records (no destructive edit).
 */
@Service
public class MedicationAdministrationService {

    private static final String ROLE_NURSE = "NURSE";
    private static final String UNLINKED = "UNLINKED";

    private static final Logger logger = LoggerFactory.getLogger(MedicationAdministrationService.class);
    private static final Set<String> STATUSES = Set.of("GIVEN", "SKIPPED", "DELAYED", "REFUSED", "NOT_AVAILABLE");
    private static final Set<String> TIME_REQUIRED = Set.of("GIVEN", "DELAYED");

    /**
     * How close together two identical entries have to be to count as one double-submitted act
     * rather than two real ones. Wide enough to absorb a double click and a client retry, far
     * short of any real dosing interval.
     */
    private static final int DUPLICATE_WINDOW_SECONDS = 60;

    @Autowired private MedicationAdministrationRepository marRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private NurseAccessGuard nurseAccessGuard;
    @Autowired private com.hms.security.NurseWriteAccess nurseWriteAccess;
    @Autowired private FormAccessService formAccessService;
    @Autowired private com.hms.security.PerformingNurseResolver performingNurseResolver;
    @Autowired private AuditLogService auditLogService;
    @Autowired private com.hms.service.RealtimeNotifier notifier;
    @Autowired private MedicineStockService medicineStockService;
    @Autowired private com.hms.repository.StockMovementRepository stockMovementRepository;
    @Autowired private MedicationStockService medicationStock;
    @Autowired private com.hms.repository.MedicalRecordRepository medicalRecordRepository;
    @Autowired private com.hms.repository.HospitalSettingRepository hospitalSettingRepository;

    /** A dose given from what the pharmacy already dispensed to the ward for this order. */
    public static final String SOURCE_DISPENSED = "DISPENSED";

    /** Everyone who may be configured to record doses; the default when a hospital chose nothing. */
    public static final List<String> RECORDING_ROLES =
            List.of(ROLE_NURSE, "NURSE_INCHARGE", "DOCTOR", "HOSPITAL_ADMIN", "RECEPTIONIST");

    /**
     * ACTIVE prescriptions for an admission — the medicine list the nurse
     * records against. Nurses must be assigned; doctors/admins may read.
     */
    public List<Prescription> getActivePrescriptions(Long ipdAdmissionId) {
        Long hospitalId = requireHospitalId();
        IpdAdmission admission = requireAdmission(ipdAdmissionId, hospitalId);
        if (ROLE_NURSE.equals(securityHelper.getCurrentUserRole())) {
            nurseAccessGuard.assertAssigned(admission.getId());
        }
        return prescriptionRepository.findByIpdAdmissionIdAndStatus(ipdAdmissionId, "ACTIVE");
    }

    /**
     * The full medication chart for an admission: every prescription (ACTIVE,
     * STOPPED, COMPLETED — never deleted) enriched with course progress and
     * today's administration state, so the nurse sees history and pending doses.
     */
    public java.util.List<com.hms.dto.MedicationChartItemDTO> getMedicationChart(Long ipdAdmissionId) {
        Long hospitalId = requireHospitalId();
        IpdAdmission admission = requireAdmission(ipdAdmissionId, hospitalId);
        if (ROLE_NURSE.equals(securityHelper.getCurrentUserRole())) {
            nurseAccessGuard.assertAssigned(admission.getId());
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.List<Prescription> all = prescriptionRepository.findByIpdAdmissionIdOrderByStartDate(ipdAdmissionId);
        java.util.List<com.hms.dto.MedicationChartItemDTO> result = new java.util.ArrayList<>();

        for (Prescription p : all) {
            com.hms.dto.MedicationChartItemDTO d = new com.hms.dto.MedicationChartItemDTO();
            d.setPrescriptionId(p.getId());
            d.setMedicineName(p.getMedicineName());
            d.setDosage(p.getDosage());
            d.setFrequency(p.getFrequency());
            d.setDuration(p.getDuration());
            d.setRoute(p.getRoute());
            d.setType(p.getType());
            d.setInstructions(p.getInstructions());
            d.setFoodTiming(p.getFoodTiming());
            d.setStatus(p.getStatus());
            d.setStartDate(p.getStartDate());
            d.setDurationDays(p.getDurationDays());

            boolean active = "ACTIVE".equalsIgnoreCase(p.getStatus());
            boolean withinWindow = true;
            if (p.getStartDate() != null) {
                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(p.getStartDate(), today); // 0 on start day
                d.setDayOfCourse((int) elapsed + 1);
                if (p.getDurationDays() != null && p.getDurationDays() > 0) {
                    withinWindow = (elapsed + 1) <= p.getDurationDays();
                }
            }
            boolean courseActive = active && withinWindow;
            d.setCourseActive(courseActive);

            // Today's administration state (any GIVEN record dated today).
            java.util.List<MedicationAdministration> mars =
                    marRepository.findByPrescriptionIdAndIsActiveTrueOrderByCreatedAtDesc(p.getId());
            boolean administeredToday = mars.stream().anyMatch(m ->
                    "GIVEN".equalsIgnoreCase(m.getStatus())
                    && m.getAdministeredTime() != null
                    && m.getAdministeredTime().toLocalDate().equals(today));
            d.setAdministeredToday(administeredToday);
            d.setPending(courseActive && !administeredToday);
            if (!mars.isEmpty()) {
                MedicationAdministration last = mars.get(0);
                d.setLastAdministeredAt(last.getAdministeredTime());
                d.setLastAdministeredStatus(last.getStatus());
            }
            annotateInventory(d, p, hospitalId);
            result.add(d);
        }
        return result;
    }

    /**
     * Say what is known about this order's stock, without ever hiding the order.
     *
     * <p>The bug this replaces: an order the inventory could not account for was shown as though
     * the facility held none of it. A nurse reading "0 in stock" against a live prescription
     * concludes the drug is unavailable, when in truth nobody had linked the order to a stock row
     * -- a data-entry gap reported as a clinical one. Absent and empty are different answers and
     * are now given different names.
     *
     * <p>Failures here are swallowed on purpose. This is advisory decoration on a clinical
     * record; an inventory problem must not be able to blank the medication chart.
     */
    private void annotateInventory(com.hms.dto.MedicationChartItemDTO d, Prescription p, Long hospitalId) {
        d.setMedicineId(p.getMedicineId());
        d.setStockSource(p.getStockSource());
        d.setHospitalItemName(p.getHospitalItemName());
        d.setQuantityPerDose(p.getQuantityPerDose() == null ? 1 : p.getQuantityPerDose());
        if (MedicationStockService.SOURCE_HOSPITAL_ITEM.equals(p.getStockSource())) {
            try {
                java.util.Map<String, Object> stock = medicationStock.stockFor(p.getStockSource(), null, p.getHospitalItemName());
                if (stock == null) {
                    d.setInventoryStatus(UNLINKED);
                } else {
                    int available = ((Number) stock.get("available")).intValue();
                    d.setAvailableQuantity(available);
                    d.setEarliestExpiry((java.time.LocalDate) stock.get("earliestExpiry"));
                    d.setMinLevel(((Number) stock.get("minLevel")).intValue());
                    d.setLowStock(Boolean.TRUE.equals(stock.get("low")));
                    d.setUnitPrice((java.math.BigDecimal) stock.get("unitPrice"));
                    d.setInventoryStatus(available <= 0 ? "LINKED_NO_STOCK" : "LINKED_AVAILABLE");
                }
            } catch (Exception e) {
                logger.warn("Could not read hospital stock for prescription {}: {}", p.getId(), e.getMessage());
                d.setInventoryStatus(UNLINKED);
            }
            return;
        }
        try {
            d.setQuantityDispensed(stockMovementRepository.dispensedForPrescription(hospitalId, p.getId()));
        } catch (Exception e) {
            logger.warn("Could not read dispensing history for prescription {}: {}", p.getId(), e.getMessage());
        }
        if (p.getMedicineId() == null) {
            d.setInventoryStatus(UNLINKED);
            return;
        }
        try {
            int available = medicineStockService.availableQuantityFor(p.getMedicineId(), hospitalId);
            d.setAvailableQuantity(available);
            d.setEarliestExpiry(medicineStockService.earliestUsableExpiry(p.getMedicineId(), hospitalId));
            d.setInventoryStatus(available <= 0 ? "LINKED_NO_STOCK" : "LINKED_AVAILABLE");
            d.setStockSource(MedicationStockService.SOURCE_MEDICINE);
            java.util.Map<String, Object> stock = medicationStock.stockFor(MedicationStockService.SOURCE_MEDICINE, p.getMedicineId(), null);
            if (stock != null) {
                d.setMinLevel(((Number) stock.get("minLevel")).intValue());
                d.setLowStock(Boolean.TRUE.equals(stock.get("low")));
                d.setUnitPrice((java.math.BigDecimal) stock.get("unitPrice"));
            }
        } catch (Exception e) {
            // Including the case where the linked medicine has since been removed from inventory.
            logger.warn("Could not read stock for prescription {}: {}", p.getId(), e.getMessage());
            d.setInventoryStatus(UNLINKED);
        }
    }

    @Transactional
    public MedicationAdministration record(MedicationAdminRequest req) {
        Long hospitalId = requireHospitalId();
        if (req.getIpdAdmissionId() == null || req.getPrescriptionId() == null) {
            throw new IllegalArgumentException("ipdAdmissionId and prescriptionId are required");
        }
        IpdAdmission admission = requireAdmission(req.getIpdAdmissionId(), hospitalId);
        com.hms.service.hospital.IpdClinicalState.requireActive(admission);
        assertMayRecord(admission, hospitalId);

        String status = req.getStatus() == null ? null : req.getStatus().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid administration status");
        }

        // The prescription must be one of the admission's current ACTIVE orders.
        List<Prescription> active = prescriptionRepository.findByIpdAdmissionIdAndStatus(req.getIpdAdmissionId(), "ACTIVE");
        Prescription prescription = active.stream()
                .filter(p -> p.getId().equals(req.getPrescriptionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prescription is not an active order for this admission"));
        if (!hospitalId.equals(prescription.getHospitalId())) {
            throw new UnauthorizedException("Access denied: prescription belongs to another hospital");
        }

        LocalDateTime administeredTime = req.getAdministeredTime();
        if (TIME_REQUIRED.contains(status)) {
            if (administeredTime == null) administeredTime = LocalDateTime.now();
        }
        if (administeredTime != null && administeredTime.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalArgumentException("Administered time cannot be in the future");
        }

        // A double-clicked "Given" is not two doses.
        //
        // Nothing stopped the same administration being written twice: the second click created a
        // second row, and the chart then showed a patient having received two doses when they had
        // received one. It is not fixed with a unique key -- giving the same drug again later in
        // the day is normal and must stay recordable -- so what is refused is specifically a
        // repeat of the same order, with the same outcome, moments after the first.
        LocalDateTime repeatCutoff = LocalDateTime.now().minusSeconds(DUPLICATE_WINDOW_SECONDS);
        List<MedicationAdministration> recent = marRepository
                .findByPrescriptionIdAndStatusAndIsActiveTrueAndCreatedAtAfter(
                        prescription.getId(), status, repeatCutoff);
        if (!recent.isEmpty()) {
            throw new com.hms.exception.ConflictException(
                    prescription.getMedicineName() + " was already recorded as " + status
                    + " a moment ago. Refresh the chart before recording it again.");
        }

        MedicationAdministration mar = new MedicationAdministration();
        mar.setHospitalId(hospitalId);
        mar.setIpdAdmissionId(admission.getId());
        mar.setPrescriptionId(prescription.getId());
        mar.setPatientId(admission.getPatientId());
        mar.setNurseUserId(securityHelper.getCurrentUserId());
        mar.setScheduledTime(req.getScheduledTime());
        mar.setAdministeredTime(administeredTime);
        mar.setStatus(status);
        mar.setRemarks(req.getRemarks());
        mar.setPerformedByNurseId(performingNurseResolver.resolve(req.getPerformedByNurseId()));
        mar.setIsActive(true);
        MedicationAdministration saved = marRepository.save(mar);
        if ("GIVEN".equals(status)) {
            saved = takeFromStock(saved, prescription, admission, req.getQuantity(), Boolean.TRUE.equals(req.getOwnSupply()));
        }

        auditAndNotify("MEDICATION_ADMINISTERED",
                status + " - " + prescription.getMedicineName() + " (IPD " + admission.getIpdNumber() + ")",
                hospitalId, admission.getId());
        return saved;
    }

    public List<MedicationAdministration> getByAdmission(Long ipdAdmissionId) {
        Long hospitalId = requireHospitalId();
        IpdAdmission admission = requireAdmission(ipdAdmissionId, hospitalId);
        if (ROLE_NURSE.equals(securityHelper.getCurrentUserRole())) {
            nurseAccessGuard.assertAssigned(admission.getId());
        }
        return marRepository.findByIpdAdmissionIdAndIsActiveTrueOrderByCreatedAtDesc(ipdAdmissionId);
    }

    /**
     * A GIVEN dose comes out of stock and onto the bill — unless the patient brought it, or the
     * order is not linked to any stock (the doctor asked the patient to buy it outside).
     */
    private MedicationAdministration takeFromStock(MedicationAdministration mar, Prescription p, IpdAdmission admission,
                                                   Integer requestedQty, boolean ownSupply) {
        int qty = doseQuantity(requestedQty, p);
        mar.setQuantity(qty);
        String source = stockSourceOf(p);
        boolean stocked = source != null;
        if (ownSupply || !stocked) {
            mar.setOwnSupply(true);
            return marRepository.save(mar);
        }
        // Already handed to the ward by the pharmacy (dispensed against this order): that supply
        // was taken from stock and billed at dispensing, so the dose draws on it and nothing more.
        if (MedicationStockService.SOURCE_MEDICINE.equals(source)) {
            int dispensed = stockMovementRepository.dispensedForPrescription(p.getHospitalId(), p.getId());
            if (dispensed > 0 && dispensed - marRepository.givenFromDispensed(p.getId()) >= qty) {
                mar.setOwnSupply(false);
                mar.setStockSource(SOURCE_DISPENSED);
                return marRepository.save(mar);
            }
        }
        MedicationStockService.Taken taken = medicationStock.take(admission, source, p.getMedicineId(),
                p.getHospitalItemName(), qty, mar.getId());
        mar.setOwnSupply(false);
        mar.setStockSource(source);
        mar.setChargeAmount(taken.charge());
        mar.setBillingItemId(taken.billingItemId());
        mar.setBillingMedicineId(taken.billingMedicineId());
        mar.setStockDetail(taken.detail());
        return marRepository.save(mar);
    }

    /** Units for a dose: what was entered, else the order's quantity per dose, else 1. */
    private static int doseQuantity(Integer requested, Prescription p) {
        if (requested != null) return requested;
        return p.getQuantityPerDose() != null ? p.getQuantityPerDose() : 1;
    }

    /** Orders made before stock sources existed carry only a medicine id: that is medicine stock. */
    private static String stockSourceOf(Prescription p) {
        if (p.getStockSource() != null) return p.getStockSource();
        return p.getMedicineId() != null ? MedicationStockService.SOURCE_MEDICINE : null;
    }

    /**
     * Undoes a dose recorded by mistake: puts its stock back, takes its charge off the bill, and
     * marks the record undone (kept, with who and why, for the audit trail).
     */
    @Transactional
    public MedicationAdministration undo(String publicId, String reason) {
        Long hospitalId = requireHospitalId();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for undoing this entry");
        MedicationAdministration mar = marRepository.findByPublicId(publicId)
                .filter(m -> hospitalId.equals(m.getHospitalId()))
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Medication entry not found"));
        if (!Boolean.TRUE.equals(mar.getIsActive()) || mar.getUndoneAt() != null) {
            throw new IllegalArgumentException("This entry has already been undone");
        }
        IpdAdmission admission = requireAdmission(mar.getIpdAdmissionId(), hospitalId);
        com.hms.service.hospital.IpdClinicalState.requireActive(admission);
        assertMayRecord(admission, hospitalId);
        medicationStock.assertChargeRemovable(mar);
        medicationStock.putBack(mar);
        mar.setIsActive(false);
        mar.setUndoneAt(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        mar.setUndoneBy(securityHelper.getCurrentUserEmail());
        mar.setUndoReason(reason.trim().length() > 255 ? reason.trim().substring(0, 255) : reason.trim());
        MedicationAdministration saved = marRepository.save(mar);
        auditAndNotify("MEDICATION_UNDONE", "Undid " + mar.getStatus() + " (" + reason.trim() + ") on IPD "
                + admission.getIpdNumber(), hospitalId, admission.getId());
        return saved;
    }

    /**
     * Gives something that was not ordered — an extra saline bottle, a dressing — from stock or the
     * patient's own supply. Recorded as a one-off order so it appears on the chart and the record.
     */
    @Transactional
    public MedicationAdministration giveExtra(com.hms.dto.GiveExtraItemRequest req) {
        Long hospitalId = requireHospitalId();
        IpdAdmission admission = requireAdmission(req.getIpdAdmissionId(), hospitalId);
        com.hms.service.hospital.IpdClinicalState.requireActive(admission);
        assertMayRecord(admission, hospitalId);
        String source = req.getSource() == null ? null : req.getSource().trim().toUpperCase(java.util.Locale.ROOT);
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Choose or type the item given");

        Prescription p = new Prescription();
        p.setHospitalId(hospitalId);
        p.setMedicalRecordId(ipdMedicalRecordId(admission, hospitalId));
        p.setMedicineName(name);
        p.setDosage(req.getQuantity() + " given once");
        p.setFrequency("Once");
        p.setDuration("Once");
        p.setDurationDays(1);
        p.setStartDate(java.time.LocalDate.now(java.time.ZoneId.systemDefault()));
        p.setType(MedicationStockService.SOURCE_HOSPITAL_ITEM.equals(source) ? "OTHER" : "TABLET");
        p.setRoute("OTHER");
        p.setInstructions(req.getRemarks());
        p.setStatus("COMPLETED");
        if (!Boolean.TRUE.equals(req.getOwnSupply())
                && (MedicationStockService.SOURCE_MEDICINE.equals(source) || MedicationStockService.SOURCE_HOSPITAL_ITEM.equals(source))) {
            p.setStockSource(source);
            p.setMedicineId(MedicationStockService.SOURCE_MEDICINE.equals(source) ? req.getMedicineId() : null);
            p.setHospitalItemName(MedicationStockService.SOURCE_HOSPITAL_ITEM.equals(source) ? name : null);
        }
        p.setQuantityPerDose(req.getQuantity());
        p = prescriptionRepository.save(p);

        MedicationAdministration mar = new MedicationAdministration();
        mar.setHospitalId(hospitalId);
        mar.setIpdAdmissionId(admission.getId());
        mar.setPrescriptionId(p.getId());
        mar.setPatientId(admission.getPatientId());
        mar.setNurseUserId(securityHelper.getCurrentUserId());
        mar.setAdministeredTime(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        mar.setStatus("GIVEN");
        mar.setRemarks(req.getRemarks());
        mar.setPerformedByNurseId(performingNurseResolver.resolve(req.getPerformedByNurseId()));
        mar.setIsActive(true);
        MedicationAdministration saved = takeFromStock(marRepository.save(mar), p, admission, req.getQuantity(),
                Boolean.TRUE.equals(req.getOwnSupply()));
        auditAndNotify("MEDICATION_ADMINISTERED", "GIVEN (extra) - " + name + " x" + req.getQuantity()
                + " (IPD " + admission.getIpdNumber() + ")", hospitalId, admission.getId());
        return saved;
    }

    /** The admission's latest medical record, which orders hang off; created when there is none. */
    private Long ipdMedicalRecordId(IpdAdmission admission, Long hospitalId) {
        List<com.hms.entity.MedicalRecord> records =
                medicalRecordRepository.findByIpdAdmissionIdOrderByCreatedAtDesc(admission.getId());
        if (records != null && !records.isEmpty()) return records.get(0).getId();
        com.hms.entity.MedicalRecord mr = new com.hms.entity.MedicalRecord();
        mr.setHospitalId(hospitalId);
        mr.setPatientId(admission.getPatientId());
        mr.setDoctorId(admission.getDoctorId());
        mr.setIpdAdmissionId(admission.getId());
        mr.setVisitType("IPD");
        mr.setDiagnosis(admission.getPrimaryDiagnosis() != null && !admission.getPrimaryDiagnosis().isBlank()
                ? admission.getPrimaryDiagnosis() : "IPD Admission");
        mr.setTreatmentNotes("Initial IPD Admission Medical Record");
        return medicalRecordRepository.save(mr).getId();
    }

    /** Roles this hospital lets record doses (all of them unless the admin narrowed it). */
    public List<String> recordingRoles(Long hospitalId) {
        String configured = hospitalSettingRepository.findByHospital_Id(hospitalId)
                .map(com.hms.entity.HospitalSetting::getMedRecordRoles).orElse(null);
        if (configured == null || configured.isBlank()) return RECORDING_ROLES;
        return java.util.Arrays.stream(configured.split(",")).map(String::trim)
                .filter(RECORDING_ROLES::contains).toList();
    }

    /** Admin: which roles may record doses. At least one role must stay allowed. */
    @Transactional
    public void setRecordingRoles(Long hospitalId, List<String> roles) {
        List<String> clean = roles == null ? List.of() : roles.stream()
                .filter(java.util.Objects::nonNull)
                .map(r -> r.trim().toUpperCase(java.util.Locale.ROOT))
                .filter(RECORDING_ROLES::contains).distinct().toList();
        if (clean.isEmpty()) throw new IllegalArgumentException("Allow at least one role to record medicines");
        com.hms.entity.HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital settings not found"));
        settings.setMedRecordRoles(String.join(",", clean));
        hospitalSettingRepository.save(settings);
        auditAndNotify("MEDICATION_ACCESS_CHANGED", "Medicines can be recorded by: " + String.join(", ", clean),
                hospitalId, null);
    }

    /** Whether the signed-in user may record doses here. */
    public boolean mayRecord(Long hospitalId) {
        String role = securityHelper.getCurrentUserRole();
        return role != null && recordingRoles(hospitalId).contains(role);
    }

    private void assertMayRecord(IpdAdmission admission, Long hospitalId) {
        String role = securityHelper.getCurrentUserRole();
        if (role == null || !recordingRoles(hospitalId).contains(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Your role can't record medicines in this hospital. The admin can allow it in Settings.");
        }
        // Files & Access: the Medication tab is read-only (or off) for this role, so the server
        // refuses the write too — the same check the ICU infusion chart already applies.
        formAccessService.assertCanEdit("MEDICATION");
        // Nurses keep their ward / assignment rules; other roles are scoped to the hospital above.
        if (ROLE_NURSE.equals(role) || "NURSE_INCHARGE".equals(role)) {
            nurseWriteAccess.assertCanWriteFor(admission.getId());
        }
    }

    // --- helpers ---

    private IpdAdmission requireAdmission(Long ipdAdmissionId, Long hospitalId) {
        IpdAdmission admission = ipdAdmissionRepository.findById(ipdAdmissionId)
                .orElseThrow(() -> new IllegalArgumentException("IPD admission not found"));
        if (!hospitalId.equals(admission.getHospitalId())) {
            throw new UnauthorizedException("Access denied: admission belongs to another hospital");
        }
        return admission;
    }

    private Long requireHospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new UnauthorizedException("Hospital ID not found in context");
        }
        return hospitalId;
    }

    /**
     * Records the write in the audit trail AND pushes it to the tenant's open tabs.
     *
     * These two always go together: every write path here ends by calling this, and each one is a
     * change a doctor may be staring at right now on the same IPD case. Keeping the broadcast here
     * rather than at each call site means a future write path cannot silently ship without one --
     * which is exactly how these records ended up non-realtime in the first place.
     */
    private void auditAndNotify(String action, String details, Long hospitalId, Long admissionId) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(), hospitalId,
                    "IPD", admissionId != null ? admissionId.toString() : null, null);
        } catch (Exception e) {
            logger.warn("Failed to write audit log for {}", action, e);
        }
        notifier.refresh(hospitalId);
    }
}
