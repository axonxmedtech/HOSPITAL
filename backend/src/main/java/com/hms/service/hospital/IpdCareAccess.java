package com.hms.service.hospital;

import com.hms.entity.IpdAdmission;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.security.NurseAccessGuard;
import com.hms.security.NurseWriteAccess;
import com.hms.security.SecurityContextHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Who may see and change an inpatient's care orders (rounds, investigations, diet).
 *
 * <ul>
 *   <li>Read: doctors, the admin and reception see any admission of their hospital; a staff
 *       nurse only a patient assigned to them; an incharge any (as for nursing notes).</li>
 *   <li>Orders (diet, ordering or cancelling a test): the doctor or the admin.</li>
 *   <li>Ward work (sample collected, result recorded): also reception, and nurses for the
 *       patients they may record for (assigned nurse / ward incharge).</li>
 * </ul>
 */
@Component
public class IpdCareAccess {

    private static final Set<String> DESK = Set.of("DOCTOR", "HOSPITAL_ADMIN", "RECEPTIONIST");
    private static final Set<String> NURSES = Set.of("NURSE", "NURSE_INCHARGE");

    private final IpdAdmissionRepository admissions;
    private final SecurityContextHelper securityHelper;
    private final NurseAccessGuard nurseAccessGuard;
    private final NurseWriteAccess nurseWriteAccess;

    public IpdCareAccess(IpdAdmissionRepository admissions, SecurityContextHelper securityHelper,
                         NurseAccessGuard nurseAccessGuard, NurseWriteAccess nurseWriteAccess) {
        this.admissions = admissions;
        this.securityHelper = securityHelper;
        this.nurseAccessGuard = nurseAccessGuard;
        this.nurseWriteAccess = nurseWriteAccess;
    }

    public Long hospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new ResourceNotFoundException("IPD admission not found");
        return hospitalId;
    }

    public String role() {
        return securityHelper.getCurrentUserRole();
    }

    public String email() {
        return securityHelper.getCurrentUserEmail();
    }

    /** "ROLE (email)" for the audit columns. */
    public String actor() {
        return role() + " (" + securityHelper.getCurrentUserEmail() + ")";
    }

    /** The caller's admission (cross-tenant ids read as not found). */
    public IpdAdmission admission(Long ipdAdmissionId) {
        return admissions.findByIdAndHospitalId(ipdAdmissionId, hospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("IPD admission not found"));
    }

    public IpdAdmission requireReadable(Long ipdAdmissionId) {
        IpdAdmission a = admission(ipdAdmissionId);
        String role = role();
        if ("NURSE".equals(role)) {
            nurseAccessGuard.assertAssigned(a.getId());
        } else if (!DESK.contains(role) && !NURSES.contains(role)) {
            throw new AccessDeniedException("Not allowed");
        }
        return a;
    }

    /** The doctor's (or admin's) orders, on a patient still in the hospital. */
    public IpdAdmission requireOrderable(Long ipdAdmissionId) {
        String role = role();
        if (!"DOCTOR".equals(role) && !"HOSPITAL_ADMIN".equals(role)) {
            throw new AccessDeniedException("Only the doctor can change this order");
        }
        IpdAdmission a = admission(ipdAdmissionId);
        IpdClinicalState.requireActive(a);
        return a;
    }

    /**
     * As {@link #requireOrderable}, with the admission's row locked for the rest of the
     * transaction: two copies of one order (a double-click, a resend) run one after the other, so
     * the idempotency check sees the first one's record.
     */
    public IpdAdmission requireOrderableForUpdate(Long ipdAdmissionId) {
        IpdAdmission a = requireOrderable(ipdAdmissionId);
        return admissions.findByIdAndHospitalIdForUpdate(a.getId(), hospitalId())
                .orElseThrow(() -> new ResourceNotFoundException("IPD admission not found"));
    }

    /** Ward work on an admission (may be after discharge: reports arrive late). */
    public void assertWardWork(IpdAdmission a) {
        String role = role();
        if (DESK.contains(role)) return;
        if (NURSES.contains(role)) {
            nurseWriteAccess.assertCanWriteFor(a.getId());
            return;
        }
        throw new AccessDeniedException("Not allowed");
    }
}
