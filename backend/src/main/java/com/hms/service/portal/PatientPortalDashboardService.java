package com.hms.service.portal;

import com.hms.dto.PortalDashboardResponse;
import com.hms.dto.PortalReportResponse;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Patient portal read-only dashboard (Form 40 phase 1). Every method resolves the caller's
 * own {@code patientId} from their {@code patient_portal_user} row — never a client-supplied
 * ID — and the reports query enforces BR-2 (RELEASED only) server-side. BR-4: every read
 * writes an {@link AuditLog} entry (patient portal actions are audited per-access, unlike
 * internal staff endpoints).
 */
@Service
public class PatientPortalDashboardService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PatientPortalDashboardService.class);

    @Autowired private PatientPortalUserRepository portalUserRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private LabOrderRepository labOrderRepository;
    @Autowired private LabResultRepository labResultRepository;
    @Autowired private RadiologyOrderRepository radiologyOrderRepository;
    @Autowired private RadiologyResultRepository radiologyResultRepository;
    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private BillingRepository billingRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private DoctorRepository doctorRepository;

    public PortalDashboardResponse getDashboard(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_DASHBOARD_ACCESSED", "Dashboard viewed");

        long upcoming = appointmentRepository
                .findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(patientId, hospitalId)
                .stream()
                .filter(a -> !a.getAppointmentDate().isBefore(LocalDate.now()))
                .count();

        // Order-status-based count (RELEASED orders), intentionally not requiring a joined
        // result row like getReports() does — keeps the dashboard tile in sync with the
        // report list count without paying for the LabResult/RadiologyResult lookups here.
        long releasedLabReports = labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId)
                .stream().filter(o -> "RELEASED".equals(o.getStatus())).count();
        long releasedRadiologyReports = radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId)
                .stream().filter(o -> "RELEASED".equals(o.getStatus())).count();
        long releasedReports = releasedLabReports + releasedRadiologyReports;

        List<MedicalRecord> records = medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        List<Long> recordIds = records.stream().map(MedicalRecord::getId).collect(Collectors.toList());
        long prescriptions = prescriptionRepository.findByMedicalRecordIdIn(recordIds).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .count();

        BigDecimal outstanding = billingRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .filter(b -> "PENDING".equalsIgnoreCase(b.getPaymentStatus()))
                .map(Billing::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortalDashboardResponse(upcoming, releasedReports, prescriptions, outstanding);
    }

    public List<Appointment> getAppointments(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_APPOINTMENTS_ACCESSED", "Appointments viewed");
        List<Appointment> appointments = appointmentRepository
                .findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(patientId, hospitalId);
        for (Appointment appointment : appointments) {
            doctorRepository.findById(appointment.getDoctorId()).ifPresent(d -> appointment.setDoctorName(d.getName()));
        }
        return appointments;
    }

    /** BR-2: only RELEASED lab/radiology orders are ever visible to the patient. */
    public List<PortalReportResponse> getReports(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_REPORTS_ACCESSED", "Released reports list viewed");
        List<PortalReportResponse> reports = new ArrayList<>();

        for (LabOrder order : labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId)) {
            if (!"RELEASED".equals(order.getStatus())) continue;
            labResultRepository.findByLabOrderId(order.getId()).ifPresent(result ->
                    reports.add(new PortalReportResponse(order.getId(), "LAB", order.getTestName(),
                            result.getReleasedAt(), result.getResultSummary(), result.getParameters(),
                            result.getIsAbnormal(), result.getResultFileUrl())));
        }

        for (RadiologyOrder order : radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(hospitalId, patientId)) {
            if (!"RELEASED".equals(order.getStatus())) continue;
            radiologyResultRepository.findByRadiologyOrderId(order.getId()).ifPresent(result ->
                    reports.add(new PortalReportResponse(order.getId(), "RADIOLOGY", order.getTestName(),
                            result.getReleasedAt(), result.getImpression(), result.getFindings(),
                            result.getIsAbnormal(), result.getResultFileUrl())));
        }

        return reports;
    }

    public List<Prescription> getPrescriptions(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_PRESCRIPTIONS_ACCESSED", "Prescriptions viewed");
        List<MedicalRecord> records = medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        List<Long> recordIds = records.stream().map(MedicalRecord::getId).collect(Collectors.toList());
        return prescriptionRepository.findByMedicalRecordIdIn(recordIds);
    }

    public List<Billing> getBilling(Long hospitalId, Long portalUserId) {
        Long patientId = resolvePatientId(hospitalId, portalUserId);
        audit(hospitalId, patientId, "PATIENT_PORTAL_BILLING_ACCESSED", "Billing viewed");
        return billingRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    private Long resolvePatientId(Long hospitalId, Long portalUserId) {
        PatientPortalUser portalUser = portalUserRepository.findById(portalUserId)
                .orElseThrow(() -> new UnauthorizedException("Portal account not found"));
        if (!portalUser.getHospitalId().equals(hospitalId)) {
            throw new UnauthorizedException("Portal account not found");
        }
        if (!"ACTIVE".equals(portalUser.getStatus())) {
            throw new UnauthorizedException("This portal account is not active.");
        }
        return portalUser.getPatientId();
    }

    /** BR-4: writes one AuditLog entry per portal record access, tagged with the patient ID. */
    private void audit(Long hospitalId, Long patientId, String action, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setDetails(details + " (patientId=" + patientId + ")");
            entry.setPerformedBy("patient-portal");
            entry.setHospitalId(hospitalId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }
}
