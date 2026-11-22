package com.hms.service.hospital;

import com.hms.entity.IcuInfusion;
import com.hms.entity.IcuVentilatorSetting;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Surgery;
import com.hms.entity.SurgeryStatus;
import com.hms.repository.IcuInfusionRepository;
import com.hms.repository.IcuVentilatorSettingRepository;
import com.hms.repository.SurgeryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What is still going on for an inpatient — open surgeries, ventilation, running infusions — and
 * the rules that use it: who may be discharged and how, and when an admission still takes entries.
 *
 * <p>Reads go straight to the repositories rather than through the ICU/OT services, because those
 * services authorise by the caller's role and form access; a receptionist confirming a discharge
 * must still be told the patient is on a ventilator.
 */
@Service
public class IpdClinicalState {

    public static final String NORMAL = "NORMAL";
    public static final String LAMA = "LAMA";
    public static final String REFERRED = "REFERRED";
    public static final String EXPIRED = "EXPIRED";
    public static final String ABSCONDED = "ABSCONDED";
    public static final List<String> DISCHARGE_TYPES = List.of(NORMAL, LAMA, REFERRED, EXPIRED, ABSCONDED);

    @Autowired private SurgeryRepository surgeryRepository;
    @Autowired private IcuVentilatorSettingRepository ventilatorRepository;
    @Autowired private IcuInfusionRepository infusionRepository;
    @Autowired @Lazy private SurgeryService surgeryService;

    /** An admission still takes clinical entries only while the patient is in the hospital. */
    public static boolean isActive(IpdAdmission admission) {
        String s = admission == null ? null : admission.getStatus();
        return "ADMITTED".equalsIgnoreCase(s) || "DISCHARGE_PLANNED".equalsIgnoreCase(s);
    }

    /** Refuses a new clinical entry for a patient who has already left. */
    public static void requireActive(IpdAdmission admission) {
        if (!isActive(admission)) {
            throw new IllegalArgumentException(
                    "This patient has already been discharged. Nothing more can be recorded on this admission.");
        }
    }

    /** Validates and normalises a discharge type; blank means a normal discharge. */
    public static String normaliseDischargeType(String type) {
        if (type == null || type.isBlank()) return NORMAL;
        String t = type.trim().toUpperCase(Locale.ROOT);
        if ("HOME".equals(t) || "RECOVERED".equals(t)) return NORMAL;
        if ("DAMA".equals(t)) return LAMA;
        if ("DEATH".equals(t) || "DECEASED".equals(t)) return EXPIRED;
        if (!DISCHARGE_TYPES.contains(t)) {
            throw new IllegalArgumentException("Unknown discharge type: " + type
                    + ". Use Normal, LAMA, Referred, Expired or Absconded.");
        }
        return t;
    }

    /** A patient who died or ran away leaves whether or not the bill is paid; the bill stays open. */
    public static boolean dischargeAllowedWithDues(String dischargeType) {
        return EXPIRED.equals(dischargeType) || ABSCONDED.equals(dischargeType);
    }

    /** The ICU stay disposition matching an IPD discharge type. */
    public static String icuDisposition(String dischargeType) {
        return switch (dischargeType == null ? NORMAL : dischargeType) {
            case LAMA -> com.hms.entity.IcuStay.DISP_LAMA;
            case REFERRED -> com.hms.entity.IcuStay.DISP_REFERRED_OUT;
            case EXPIRED -> com.hms.entity.IcuStay.DISP_EXPIRED;
            default -> com.hms.entity.IcuStay.DISP_HOME;
        };
    }

    /** Surgeries for this admission that are neither finished nor called off. */
    public List<Surgery> openSurgeries(Long ipdAdmissionId) {
        Set<String> open = new HashSet<>();
        for (SurgeryStatus st : SurgeryStatus.values()) {
            if (!st.isTerminal() && st != SurgeryStatus.COMPLETED) open.add(st.name());
        }
        return surgeryRepository.findByIpdAdmissionIdAndStatusIn(ipdAdmissionId, open);
    }

    /** Surgeries done during this admission, for the discharge summary. */
    public List<Surgery> completedSurgeries(Long ipdAdmissionId) {
        return surgeryRepository.findByIpdAdmissionIdAndStatusIn(ipdAdmissionId, Set.of(SurgeryStatus.COMPLETED.name(), SurgeryStatus.CLOSED.name()));
    }

    /** The ventilation status in force now (INVASIVE / NIV / OFF), or null if never recorded. */
    public String ventilationStatus(Long ipdAdmissionId, Long hospitalId) {
        Set<Long> superseded = new HashSet<>(ventilatorRepository.findSupersededIds(ipdAdmissionId, hospitalId));
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.systemDefault());
        for (IcuVentilatorSetting s : ventilatorRepository
                .findByIpdAdmissionIdAndHospitalIdAndIsActiveTrueOrderByObservedAtDescIdDesc(ipdAdmissionId, hospitalId)) {
            if (superseded.contains(s.getId())) continue;
            if (!s.getObservedAt().isAfter(now)) return s.getVentilationStatus();
        }
        return null;
    }

    /** Name of the procedure under way for this admission, or null when none is in progress. */
    public String surgeryInProgress(Long ipdAdmissionId) {
        for (Surgery s : openSurgeries(ipdAdmissionId)) {
            if (SurgeryStatus.IN_PROGRESS.name().equals(s.getStatus())) return s.getProcedureName();
        }
        return null;
    }

    public boolean isVentilated(Long ipdAdmissionId, Long hospitalId) {
        String v = ventilationStatus(ipdAdmissionId, hospitalId);
        return v != null && !IcuVentilatorSetting.OFF.equals(v);
    }

    /**
     * Checks that a patient can be discharged this way, before anything is written.
     *
     * <ul>
     *   <li>A surgery in progress always blocks; one not yet started blocks unless the patient
     *       died or absconded, in which case it is cancelled by {@link #closeOutForDischarge}.</li>
     *   <li>A normal discharge home is refused while the patient is on a ventilator.</li>
     * </ul>
     */
    public void assertDischargeAllowed(IpdAdmission ipd, String dischargeType) {
        for (Surgery s : openSurgeries(ipd.getId())) {
            boolean started = SurgeryStatus.IN_PROGRESS.name().equals(s.getStatus());
            if (started || !dischargeAllowedWithDues(dischargeType)) {
                throw new IllegalArgumentException("Surgery \"" + s.getProcedureName() + "\" is "
                        + s.getStatus().replace('_', ' ').toLowerCase(Locale.ROOT)
                        + ". Complete, postpone or cancel it in OT before discharging the patient.");
            }
        }
        if (NORMAL.equals(dischargeType) && isVentilated(ipd.getId(), ipd.getHospitalId())) {
            throw new IllegalArgumentException("The patient is on a ventilator. Record ventilation as OFF "
                    + "before a normal discharge, or choose LAMA / Referred / Expired.");
        }
    }

    /** Stops every infusion still running on this admission, with the reason given. */
    public void stopRunningInfusions(IpdAdmission ipd, String reason) {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.systemDefault());
        for (IcuInfusion inf : infusionRepository
                .findByIpdAdmissionIdAndHospitalIdAndIsActiveTrueAndStoppedAtIsNullOrderByStartedAtDesc(
                        ipd.getId(), ipd.getHospitalId())) {
            inf.setStoppedAt(now.isBefore(inf.getStartedAt()) ? inf.getStartedAt() : now);
            inf.setStopReason(reason);
            infusionRepository.save(inf);
        }
    }

    /** Ends what cannot outlive the admission: running infusions, and not-yet-started surgeries. */
    public void closeOutForDischarge(IpdAdmission ipd, String dischargeType) {
        stopRunningInfusions(ipd, "Patient discharged (" + dischargeType + ")");
        if (dischargeAllowedWithDues(dischargeType)) {
            for (Surgery s : openSurgeries(ipd.getId())) {
                surgeryService.cancel(s.getPublicId(), "OTHER",
                        EXPIRED.equals(dischargeType) ? "Patient expired" : "Patient absconded");
            }
        }
    }
}
