package com.hms.controller.hospital;

import com.hms.entity.Hospital;
import com.hms.entity.SupportTicket;
import com.hms.entity.User;
import com.hms.repository.HospitalRepository;
import com.hms.repository.SupportTicketRepository;
import com.hms.repository.UserRepository;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital/tickets")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class HospitalTicketController {

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityContextHelper securityHelper;

    @GetMapping
    public ResponseEntity<?> getMyTickets() {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            return ResponseEntity.ok(supportTicketRepository.findByHospitalId(hospitalId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createTicket(@RequestBody SupportTicket ticket) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            Long userId = securityHelper.getCurrentUserId();

            Hospital hospital = hospitalRepository.findById(hospitalId)
                    .orElseThrow(() -> new RuntimeException("Hospital not found"));
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (ticket.getSubject() == null || ticket.getSubject().trim().isEmpty() ||
                ticket.getMessage() == null || ticket.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Subject and message are required.");
            }

            ticket.setHospitalId(hospitalId);
            ticket.setHospitalName(hospital.getName());
            ticket.setAdminName(user.getName());
            ticket.setStatus("OPEN");
            
            SupportTicket saved = supportTicketRepository.save(ticket);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
