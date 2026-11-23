package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.entity.LabOrder;
import com.hms.entity.MedicalRecord;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.LabOrderRepository;
import com.hms.repository.MedicalRecordRepository;
import com.hms.repository.PatientNurseAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Investigations for inpatients: the doctor orders tests, the ward collects the sample, and the
 * result is recorded when the report arrives (which may be after the patient has gone home).
 *
 * <p>ORDERED -> SAMPLE_COLLECTED -> COMPLETED, or CANCELLED by the doctor before it completes.
 */
@Service
public class IpdInvestigationService {

    static final String ORDERED = "ORDERED";
    static final String SAMPLE_COLLECTED = "SAMPLE_COLLECTED";
    static final String COMPLETED = "COMPLETED";
    static final String CANCELLED = "CANCELLED";
    private static final int MAX_TESTS = 20;
    private static final String ROUTINE = "ROUTINE";
    private static final String URGENT = "URGENT";
    private static final Logger logger = LoggerFactory.getLogger(IpdInvestigationService.class);

    private final LabOrderRepository labOrders;
    private final MedicalRecordRepository medicalRecords;
    private final PatientNurseAssignmentRepository nurseAssignments;
    private final NotificationService notificationService;
    private final IpdCareAccess access;
    private final com.hms.service.AuditLogService auditLogService;

    public IpdInvestigationService(LabOrderRepository labOrders, MedicalRecordRepository medicalRecords,
                                   PatientNurseAssignmentRepository nurseAssignments, NotificationService notificationService,
                                   IpdCareAccess access, com.hms.service.AuditLogService auditLogService) {
        this.labOrders = labOrders;
        this.medicalRecords = medicalRecords;
        this.nurseAssignments = nurseAssignments;
        this.notificationService = notificationService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long ipdAdmissionId) {
        IpdAdmission a = access.requireReadable(ipdAdmissionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LabOrder o : labOrders.findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDescIdDesc(a.getHospitalId(), a.getId())) {
            out.add(view(o));
        }
        return out;
    }

    /** Orders one or more tests (one row each, so each has its own sample and result). */
    @Transactional
    public List<Map<String, Object>> order(Long ipdAdmissionId, List<String> tests, String priority, String notes) {
        IpdAdmission a = access.requireOrderable(ipdAdmissionId);
        List<String> names = testNames(tests);
        String p = priority(priority);
        String note = notes == null || notes.isBlank() ? null : notes.trim();
        if (note != null && note.length() > 500) throw new IllegalArgumentException("The note is too long");

        Long recordId = ipdRecordId(a);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String n : names) {
            LabOrder o = new LabOrder();
            o.setHospitalId(a.getHospitalId());
            o.setPatientId(a.getPatientId());
            o.setIpdAdmissionId(a.getId());
            o.setMedicalRecordId(recordId);
            o.setTestName(n);
            o.setPriority(p);
            o.setStatus(ORDERED);
            o.setNotes(note);
            o.setOrderedBy(access.actor());
            out.add(view(labOrders.save(o)));
        }
        boolean urgent = URGENT.equals(p);
        audit("IPD_INVESTIGATION_ORDERED", a, String.join(", ", names) + (urgent ? " (urgent)" : ""));
        nurseAssignments.findByIpdAdmissionIdAndIsActiveTrue(a.getId()).ifPresent(asg ->
                notificationService.create(asg.getNurseUserId(), a.getHospitalId(), "INVESTIGATION_ORDERED",
                        (urgent ? "Urgent: " : "") + "Investigation ordered",
                        String.join(", ", names) + " ordered for " + a.getIpdNumber() + ". Collect the sample.",
                        "IPD", a.getId()));
        return out;
    }

    /** The test names given, trimmed, blanks dropped; 1 to MAX_TESTS of them. */
    private static List<String> testNames(List<String> tests) {
        List<String> names = new ArrayList<>();
        if (tests != null) {
            tests.stream().filter(t -> t != null && !t.isBlank()).map(String::trim).forEach(names::add);
        }
        if (names.isEmpty()) throw new IllegalArgumentException("Enter at least one test");
        if (names.size() > MAX_TESTS) throw new IllegalArgumentException("Order at most " + MAX_TESTS + " tests at a time");
        names.stream().filter(n -> n.length() > 200).findFirst().ifPresent(n -> {
            throw new IllegalArgumentException("Test name is too long: " + n.substring(0, 40) + "…");
        });
        return names;
    }

    private static String priority(String priority) {
        String p = priority == null || priority.isBlank() ? ROUTINE : priority.trim().toUpperCase(Locale.ROOT);
        if (!ROUTINE.equals(p) && !URGENT.equals(p)) throw new IllegalArgumentException("Priority must be Routine or Urgent");
        return p;
    }

    @Transactional
    public Map<String, Object> collectSample(String publicId) {
        LabOrder o = find(publicId);
        IpdAdmission a = access.admission(o.getIpdAdmissionId());
        access.assertWardWork(a);
        if (!ORDERED.equals(o.getStatus())) {
            throw new IllegalArgumentException("Only an ordered test can have its sample collected");
        }
        o.setStatus(SAMPLE_COLLECTED);
        o.setSampleCollectedAt(LocalDateTime.now(ZoneId.systemDefault()));
        o.setSampleCollectedBy(access.actor());
        audit("IPD_INVESTIGATION_SAMPLE", a, o.getTestName());
        return view(labOrders.save(o));
    }

    /** Records the result. A completed result may be corrected; the audit keeps the old text. */
    @Transactional
    public Map<String, Object> recordResult(String publicId, String result) {
        LabOrder o = find(publicId);
        IpdAdmission a = access.admission(o.getIpdAdmissionId());
        access.assertWardWork(a);
        if (CANCELLED.equals(o.getStatus())) throw new IllegalArgumentException("This test was cancelled");
        String text = result == null ? "" : result.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Enter the result");
        if (text.length() > 4000) throw new IllegalArgumentException("The result is too long (4000 characters at most)");
        String previous = o.getResultText();
        o.setResultText(text);
        o.setResultAt(LocalDateTime.now(ZoneId.systemDefault()));
        o.setResultBy(access.actor());
        o.setStatus(COMPLETED);
        audit(previous == null ? "IPD_INVESTIGATION_RESULT" : "IPD_INVESTIGATION_RESULT_CORRECTED", a,
                o.getTestName() + (previous == null ? "" : " (was: " + abbreviate(previous) + ")"));
        return view(labOrders.save(o));
    }

    @Transactional
    public Map<String, Object> cancel(String publicId, String reason) {
        LabOrder o = find(publicId);
        String role = access.role();
        if (!"DOCTOR".equals(role) && !"HOSPITAL_ADMIN".equals(role)) {
            throw new AccessDeniedException("Only the doctor can cancel a test");
        }
        IpdAdmission a = access.admission(o.getIpdAdmissionId());
        if (COMPLETED.equals(o.getStatus()) || CANCELLED.equals(o.getStatus())) {
            throw new IllegalArgumentException("This test is already " + o.getStatus().toLowerCase(Locale.ROOT));
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Enter why the test is cancelled");
        o.setStatus(CANCELLED);
        o.setCancelReason(reason.trim().length() > 255 ? reason.trim().substring(0, 255) : reason.trim());
        o.setCancelledAt(LocalDateTime.now(ZoneId.systemDefault()));
        audit("IPD_INVESTIGATION_CANCELLED", a, o.getTestName() + ": " + o.getCancelReason());
        return view(labOrders.save(o));
    }

    private LabOrder find(String publicId) {
        LabOrder o = labOrders.findByPublicIdAndHospitalId(publicId, access.hospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("Investigation not found"));
        if (o.getIpdAdmissionId() == null) throw new ResourceNotFoundException("Investigation not found");
        return o;
    }

    /** The admission's latest medical record (lab_orders requires one in older schemas); created if none. */
    private Long ipdRecordId(IpdAdmission a) {
        List<MedicalRecord> records = medicalRecords.findByIpdAdmissionIdOrderByCreatedAtDesc(a.getId());
        if (records != null && !records.isEmpty()) return records.get(0).getId();
        MedicalRecord mr = new MedicalRecord();
        mr.setHospitalId(a.getHospitalId());
        mr.setPatientId(a.getPatientId());
        mr.setDoctorId(a.getDoctorId());
        mr.setIpdAdmissionId(a.getId());
        mr.setVisitType("IPD");
        mr.setDiagnosis(a.getPrimaryDiagnosis() == null || a.getPrimaryDiagnosis().isBlank() ? "IPD Admission" : a.getPrimaryDiagnosis());
        mr.setTreatmentNotes("Initial IPD Admission Medical Record");
        return medicalRecords.save(mr).getId();
    }

    private static Map<String, Object> view(LabOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publicId", o.getPublicId());
        m.put("testName", o.getTestName());
        m.put("priority", o.getPriority());
        m.put("status", o.getStatus());
        m.put("notes", o.getNotes());
        m.put("orderedBy", o.getOrderedBy());
        m.put("orderedAt", o.getCreatedAt());
        m.put("sampleCollectedAt", o.getSampleCollectedAt());
        m.put("sampleCollectedBy", o.getSampleCollectedBy());
        m.put("resultText", o.getResultText());
        m.put("resultAt", o.getResultAt());
        m.put("resultBy", o.getResultBy());
        m.put("cancelReason", o.getCancelReason());
        m.put("cancelledAt", o.getCancelledAt());
        return m;
    }

    private static String abbreviate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private void audit(String action, IpdAdmission a, String details) {
        try {
            auditLogService.logAction(action, details + " — " + a.getIpdNumber(), access.email(), a.getHospitalId(),
                    "IPD", a.getId().toString(), null);
        } catch (Exception e) {
            logger.warn("Failed to audit {}", action, e);
        }
    }
}
