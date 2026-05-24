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
    public ResponseEntity<?> createTicket(@RequestBody com.hms.dto.CreateSupportTicketRequest req) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            Long userId = securityHelper.getCurrentUserId();

            Hospital hospital = hospitalRepository.findById(hospitalId)
                    .orElseThrow(() -> new RuntimeException("Hospital not found"));
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (req.getSubject() == null || req.getSubject().trim().isEmpty() ||
                req.getMessage() == null || req.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Subject and message are required.");
            }

            SupportTicket ticket = new SupportTicket();
            ticket.setSubject(req.getSubject().trim());
            ticket.setMessage(req.getMessage().trim());
            
            // Validate priority and default to MEDIUM if invalid
            String priority = "MEDIUM";
            if (req.getPriority() != null) {
                String reqPriority = req.getPriority().trim().toUpperCase();
                if ("LOW".equals(reqPriority) || "MEDIUM".equals(reqPriority) || "HIGH".equals(reqPriority)) {
                    priority = reqPriority;
                }
            }
            ticket.setPriority(priority);

            ticket.setHospitalId(hospitalId);
            ticket.setHospitalName(hospital.getName());
            ticket.setAdminName(user.getName());
            ticket.setStatus("OPEN");
            ticket.setCreatedAt(java.time.LocalDateTime.now());
            
            SupportTicket saved = supportTicketRepository.save(ticket);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
