package com.hms.security;

import com.hms.repository.PatientNurseAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * NurseAccessGuard - central check that the current nurse is actively assigned
 * to a given IPD admission (Phase 1 Nurse module). Reused by the workspace,
 * vitals, notes, and medication-administration services so the "only your
 * assigned patients" rule lives in one place.
 */
@Component
public class NurseAccessGuard {

    @Autowired
    private PatientNurseAssignmentRepository assignmentRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    /** users.id of the current nurse. */
    public Long currentNurseId() {
        return securityHelper.getCurrentUserId();
    }

    /**
     * Throws AccessDeniedException (403) unless the current nurse has an active
     * assignment to the admission.
     */
    public void assertAssigned(Long ipdAdmissionId) {
        Long nurseId = securityHelper.getCurrentUserId();
        boolean assigned = assignmentRepository
                .existsByIpdAdmissionIdAndNurseUserIdAndIsActiveTrue(ipdAdmissionId, nurseId);
        if (!assigned) {
            throw new AccessDeniedException("You are not assigned to this patient");
        }
    }
}
