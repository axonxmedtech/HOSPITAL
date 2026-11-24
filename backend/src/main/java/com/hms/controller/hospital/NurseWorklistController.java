package com.hms.controller.hospital;

import com.hms.dto.NurseWorklistItemDTO;
import com.hms.security.RequireModule;
import com.hms.service.hospital.NurseWorklistService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The nurse's "due now" list (doses, vitals, samples) across the patients they can see. */
@RestController
@RequestMapping("/hospital/nurse/worklist")
@RequireModule("NURSING")
@PreAuthorize("hasAnyRole('NURSE','NURSE_INCHARGE')")
public class NurseWorklistController {

    @Autowired
    private NurseWorklistService worklistService;

    @GetMapping
    public ResponseEntity<List<NurseWorklistItemDTO>> worklist() {
        return ResponseEntity.ok(worklistService.worklist());
    }
}
