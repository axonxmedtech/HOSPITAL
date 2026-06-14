package com.hms.controller.platform;

import com.hms.entity.SupportTicket;
import com.hms.repository.SupportTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/platform/tickets")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformTicketController {

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @GetMapping
    public ResponseEntity<?> getAllTickets() {
        try {
            return ResponseEntity.ok(supportTicketRepository.findAll());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateTicketStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            SupportTicket ticket = supportTicketRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ticket not found"));
            
            String newStatus = body.get("status");
            if (newStatus == null || newStatus.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Status is required");
            }
            
            ticket.setStatus(newStatus.toUpperCase());
            if ("RESOLVED".equalsIgnoreCase(newStatus)) {
                ticket.setResolvedAt(LocalDateTime.now());
            }
            
            SupportTicket saved = supportTicketRepository.save(ticket);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
