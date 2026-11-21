package com.hms.service.hospital;

import com.hms.entity.Billing;
import com.hms.entity.IcuStay;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Opd;
import com.hms.entity.Patient;
import com.hms.entity.PatientDocument;
import com.hms.entity.Surgery;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.BedRepository;
import com.hms.repository.BillingRepository;
import com.hms.repository.DischargeSummaryRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.IcuStayRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.OpdRepository;
import com.hms.repository.PatientDocumentRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.SurgeryRepository;
import com.hms.repository.WardRepository;
import com.hms.security.BillingAccessPolicy;
import com.hms.security.NurseAccessGuard;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The Patient File: everything that ever happened to one patient at this hospital, in one read —
 * every OPD visit, IPD stay, ICU stay, surgery, bill and document, current or finished.
 *
 * <p>Read-only. It reads the authoritative records and never writes, copies or deletes anything.
 *
 * <p>Who sees what:
 * <ul>
 *   <li>Admin and doctor — the whole file, clinical detail included.</li>
 *   <li>Receptionist — the visits, stays and bills, but no clinical detail (complaint, diagnosis,
 *       documents, the clinical timeline).</li>
 *   <li>Staff nurse / nurse in-charge — the clinical file, but only for a patient currently
 *       admitted in their care (their ward, their assignment, or a cover they hold).</li>
 *   <li>Bills follow the hospital's Billing Handler setting ({@link BillingAccessPolicy}).</li>
 * </ul>
 * Everything is scoped to the caller's hospital.
 */
@Service
public class PatientFileService {

    private static final List<String> IN_HOSPITAL = List.of("ADMITTED", "DISCHARGE_PLANNED");

    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private PatientRepository patientRepository;
    @Autowired private OpdRepository opdRepository;
    @Autowired private IpdAdmissionRepository ipdAdmissionRepository;
    @Autowired private IcuStayRepository icuStayRepository;
    @Autowired private SurgeryRepository surgeryRepository;
    @Autowired private BillingRepository billingRepository;
    @Autowired private BillingService billingService;
    @Autowired private PatientDocumentRepository patientDocumentRepository;
    @Autowired private DischargeSummaryRepository dischargeSummaryRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private WardRepository wardRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private NurseAccessGuard nurseAccessGuard;
    @Autowired private BillingAccessPolicy billingAccessPolicy;

    /** What the caller may see in a file. */
    public record Access(boolean clinical, boolean billing) {}

    private static String role(SecurityContextHelper h) {
        String r = h.getCurrentUserRole();
        return r == null ? "" : r.toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", "");
    }

    private Long hospitalId() {
        Long id = securityHelper.getCurrentHospitalId();
        if (id == null) throw new UnauthorizedException("Hospital ID not found in context");
        return id;
    }

    /**
     * Loads the patient and proves the caller may open their file. An admin may open the file of
     * a deleted patient (their records are kept); everyone else sees active patients only.
     */
    public Patient requireViewable(String publicId) {
        Long hospitalId = hospitalId();
        String role = role(securityHelper);
        Patient patient = ("HOSPITAL_ADMIN".equals(role)
                ? patientRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                : patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue(publicId, hospitalId))
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        switch (role) {
            case "HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST" -> { return patient; }
            case "NURSE", "NURSE_INCHARGE" -> {
                IpdAdmission current = ipdAdmissionRepository
                        .findFirstByHospitalIdAndPatientIdAndStatusInOrderByIdDesc(hospitalId, patient.getId(), IN_HOSPITAL)
                        .orElseThrow(() -> new AccessDeniedException(
                                "You can open the file of patients currently in your care only"));
                try {
                    nurseAccessGuard.assertAssigned(current.getId());
                } catch (AccessDeniedException e) {
                    throw new AccessDeniedException("You can open the file of patients currently in your care only");
                }
                return patient;
            }
            default -> throw new AccessDeniedException("The patient file is not available for your role");
        }
    }

    public Access accessFor() {
        String role = role(securityHelper);
        boolean clinical = !"RECEPTIONIST".equals(role);
        boolean billing = hasModule("BILLING") && billingAccessPolicy.allowed();
        return new Access(clinical, billing);
    }

    private boolean hasModule(String module) {
        try {
            List<String> modules = securityHelper.getCurrentUserDetails().getModules();
            return modules == null || modules.contains(module);
        } catch (Exception e) {
            return true; // module gating is enforced elsewhere; never hide records on a lookup failure
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fileFor(String publicId) {
        Patient patient = requireViewable(publicId);
        Long hospitalId = hospitalId();
        Long patientId = patient.getId();
        Access access = accessFor();

        Map<Long, String> doctorNames = new HashMap<>();
        Function<Long, String> doctorName = id -> id == null ? null
                : doctorNames.computeIfAbsent(id, k -> doctorRepository.findByIdAndHospitalId(k, hospitalId)
                        .map(d -> d.getName()).orElse(null));
        Map<Long, String> wardNames = new HashMap<>();
        Function<Long, String> wardName = id -> id == null ? null
                : wardNames.computeIfAbsent(id, k -> wardRepository.findByWardIdAndHospitalId(k, hospitalId)
                        .map(w -> w.getWardName()).orElse(null));

        // ---- OPD visits ----
        List<Map<String, Object>> opdVisits = new ArrayList<>();
        List<Opd> opds = new ArrayList<>(opdRepository.findByPatientAndHospitalIdOrderByCreatedAtAsc(patientId, hospitalId));
        opds.sort(Comparator.comparing(Opd::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        for (Opd o : opds) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("caseId", o.getCaseId());
            m.put("date", o.getCreatedAt());
            m.put("status", o.getStatus() != null ? o.getStatus().name() : null);
            m.put("visitType", o.getVisitType() != null ? o.getVisitType().name() : null);
            m.put("doctorName", o.getDoctor() != null ? o.getDoctor().getName() : null);
            if (access.clinical()) m.put("problem", o.getProblem());
            opdVisits.add(m);
        }

        // ---- IPD stays ----
        List<Map<String, Object>> ipdStays = new ArrayList<>();
        List<IpdAdmission> admissions = new ArrayList<>(ipdAdmissionRepository.findByHospitalIdAndPatientId(hospitalId, patientId));
        admissions.sort(Comparator.comparing(IpdAdmission::getAdmissionDatetime, Comparator.nullsLast(Comparator.reverseOrder())));
        for (IpdAdmission a : admissions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("ipdNumber", a.getIpdNumber());
            m.put("status", a.getStatus());
            m.put("admissionType", a.getAdmissionType());
            m.put("admittedAt", a.getAdmissionDatetime());
            m.put("dischargedAt", a.getDischargeDatetime());
            m.put("days", days(a.getAdmissionDatetime(), a.getDischargeDatetime()));
            m.put("doctorName", doctorName.apply(a.getDoctorId()));
            m.put("wardName", wardName.apply(a.getWardId()));
            m.put("bedCode", a.getBedId() == null ? null : bedRepository.findByBedIdAndHospitalId(a.getBedId(), hospitalId)
                    .map(b -> b.getBedCode()).orElse(null));
            if (access.clinical()) {
                m.put("hasDischargeSummary", dischargeSummaryRepository.findByIpdAdmissionId(a.getId()).isPresent());
            }
            ipdStays.add(m);
        }

        // ---- ICU stays ----
        List<Map<String, Object>> icuStays = new ArrayList<>();
        for (IcuStay s : icuStayRepository.findByPatientIdAndHospitalIdOrderByAdmittedAtDesc(patientId, hospitalId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("publicId", s.getPublicId());
            m.put("ipdAdmissionId", s.getIpdAdmissionId());
            m.put("status", s.getStatus());
            m.put("unitName", wardName.apply(s.getWardId()));
            m.put("admittedAt", s.getAdmittedAt());
            m.put("dischargedAt", s.getDischargedAt());
            m.put("days", days(s.getAdmittedAt(), s.getDischargedAt()));
            m.put("disposition", s.getDisposition());
            m.put("intensivistName", doctorName.apply(s.getIntensivistDoctorId()));
            if (access.clinical()) m.put("admissionReason", s.getAdmissionReason());
            icuStays.add(m);
        }

        // ---- Surgeries ----
        List<Map<String, Object>> surgeries = new ArrayList<>();
        List<Surgery> ops = new ArrayList<>(surgeryRepository.findByPatientIdAndHospitalId(patientId, hospitalId));
        ops.sort(Comparator.comparing((Surgery s) -> s.getScheduledAt() != null ? s.getScheduledAt() : s.getRequestedAt(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        for (Surgery s : ops) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("publicId", s.getPublicId());
            m.put("ipdAdmissionId", s.getIpdAdmissionId());
            m.put("encounterType", s.getEncounterType());
            m.put("procedureName", s.getProcedureName());
            m.put("status", s.getStatus());
            m.put("priority", s.getPriority());
            m.put("requestedAt", s.getRequestedAt());
            m.put("scheduledAt", s.getScheduledAt());
            m.put("completedAt", s.getCompletedAt());
            m.put("surgeonName", s.getSurgeonName() != null ? s.getSurgeonName() : doctorName.apply(s.getSurgeonDoctorId()));
            m.put("anaesthetistName", s.getAnaesthetistName());
            surgeries.add(m);
        }

        // ---- Bills ----
        List<Map<String, Object>> bills = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, paid = BigDecimal.ZERO, balance = BigDecimal.ZERO;
        if (access.billing()) {
            for (Billing b : billingRepository.findByPatientIdAndHospitalIdOrderByCreatedAtDesc(patientId, hospitalId)) {
                BillMoney money = billingService.moneyFor(b);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", b.getId());
                m.put("customId", b.getCustomId());
                m.put("category", b.getBillingType());
                m.put("opdId", b.getOpdId());
                m.put("ipdAdmissionId", b.getIpdAdmissionId());
                m.put("date", b.getCreatedAt());
                m.put("status", b.getPaymentStatus());
                m.put("description", b.getDescription());
                m.put("totalAmount", money.net());
                m.put("paidAmount", money.paid());
                m.put("balance", money.balance());
                bills.add(m);
                if (!"CANCELLED".equalsIgnoreCase(b.getPaymentStatus())) {
                    total = total.add(nz(money.net()));
                    paid = paid.add(nz(money.paid()));
                    balance = balance.add(nz(money.balance()));
                }
            }
        }

        // ---- Documents (clinical) ----
        List<Map<String, Object>> documents = new ArrayList<>();
        if (access.clinical()) {
            for (PatientDocument d : patientDocumentRepository
                    .findByHospitalIdAndPatientIdAndIsActiveTrueOrderByReportDateDescIdDesc(hospitalId, patientId)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("publicId", d.getPublicId());
                m.put("title", d.getTitle());
                m.put("documentType", d.getDocumentType());
                m.put("reportDate", d.getReportDate());
                m.put("opdId", d.getOpdId());
                m.put("ipdAdmissionId", d.getIpdAdmissionId());
                documents.add(m);
            }
        }

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", patient.getId());
        p.put("publicId", patient.getPublicId());
        p.put("customId", patient.getCustomId());
        p.put("name", patient.getName());
        p.put("phone", patient.getPhone());
        p.put("gender", patient.getGender());
        p.put("dateOfBirth", patient.getDateOfBirth());
        p.put("address", patient.getAddress());
        p.put("registeredAt", patient.getCreatedAt());
        p.put("isActive", !Boolean.FALSE.equals(patient.getIsActive()));
        if (access.clinical()) p.put("medicalHistory", patient.getMedicalHistory());

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalAmount", total);
        totals.put("paidAmount", paid);
        totals.put("balance", balance);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("opdVisits", opdVisits.size());
        counts.put("ipdStays", ipdStays.size());
        counts.put("icuStays", icuStays.size());
        counts.put("surgeries", surgeries.size());
        counts.put("bills", bills.size());
        counts.put("documents", documents.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("patient", p);
        out.put("access", Map.of("clinical", access.clinical(), "billing", access.billing()));
        out.put("counts", counts);
        out.put("opdVisits", opdVisits);
        out.put("ipdStays", ipdStays);
        out.put("icuStays", icuStays);
        out.put("surgeries", surgeries);
        out.put("bills", bills);
        out.put("billTotals", totals);
        out.put("documents", documents);
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Whole days stayed (at least 1); a stay still open counts up to now. */
    private static Long days(LocalDateTime from, LocalDateTime to) {
        if (from == null) return null;
        long d = ChronoUnit.DAYS.between(from.toLocalDate(), (to != null ? to : LocalDateTime.now()).toLocalDate());
        return Math.max(1, d);
    }
}
