package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.FollowUpDTO;
import com.hms.repository.DoctorRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.FollowUpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The outstanding-follow-up list.
 *
 * <p>Read-only by design, and that is the whole point of this checkpoint: the previous way to see
 * due follow-ups was to open the OPD queue, which created the encounters as a side effect. A GET
 * here creates nothing. Turning a due follow-up into an actual visit is a separate, explicit
 * action taken when the patient is standing at the desk.
 *
 * <p>No pharmacy alias: a pharmacy has no consultations to follow up.
 */
@RestController
@RequestMapping({"/hospital/follow-ups", "/clinic/follow-ups"})
public class FollowUpController {

    @Autowired private FollowUpService followUpService;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private DoctorRepository doctorRepository;

    /**
     * Outstanding follow-ups for this facility.
     *
     * @param timing      optional bucket: OVERDUE, DUE_TODAY or UPCOMING. All three when omitted.
     * @param mine        a doctor asking only for their own patients
     * @param overdueDays how far back to look; the service's default window when omitted
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST')")
    public ResponseEntity<?> outstanding(
            @RequestParam(required = false) String timing,
            @RequestParam(required = false, defaultValue = "false") boolean mine,
            @RequestParam(required = false) Integer overdueDays) {

        Long doctorId = null;
        if (mine) {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            // Resolved from the caller's own identity, never from a request parameter: a doctor
            // id in the query string would let anyone read any doctor's list.
            doctorId = doctorRepository
                    .findByEmailAndHospitalId(securityHelper.getCurrentUserEmail(), hospitalId)
                    .map(com.hms.entity.Doctor::getId)
                    .orElse(null);
            if (doctorId == null) {
                return ResponseEntity.ok(List.of());
            }
        }

        try {
            List<FollowUpDTO> rows = followUpService.outstanding(timing, doctorId, overdueDays);
            return ResponseEntity.ok(rows);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
