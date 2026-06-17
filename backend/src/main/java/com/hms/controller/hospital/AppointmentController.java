package com.hms.controller.hospital;

import com.hms.entity.Appointment;
import com.hms.service.hospital.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * AppointmentController - REST controller for appointment management
 * 
 * This controller provides endpoints for:
 * - Creating appointments (Hospital Admin only)
 * - Listing all appointments (Hospital Admin)
 * - Listing doctor's own appointments (Doctor)
 * - Getting appointment details (Hospital Admin and Doctor)
 * 
 * All operations are automatically filtered by hospital_id.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/appointments")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    /**
     * Create a new appointment
     * Accessible by Hospital Admin and Receptionist
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> createAppointment(@Valid @RequestBody Appointment appointment) {
        Appointment createdAppointment = appointmentService.createAppointment(appointment);
        return ResponseEntity.ok(createdAppointment);
    }

    /**
     * Get all appointments for the current hospital
     * Accessible by Hospital Admin and Receptionist
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> getAllAppointments(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String view) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(appointmentService.getAllAppointments(search, pageable, view));
    }

    /**
     * Get today's appointments for Overview dashboard
     * Accessible by Hospital Admin, Receptionist, and Doctor
     */
    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public ResponseEntity<List<Appointment>> getTodaysAppointments() {
        List<Appointment> appointments = appointmentService.getTodaysAppointments();
        return ResponseEntity.ok(appointments);
    }

    /**
     * Get appointments for the current logged-in doctor with pagination, search,
     * and view filter
     */
    @GetMapping("/my-appointments")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> getMyAppointments(@RequestParam(required = false) String view,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        org.springframework.data.domain.Page<Appointment> appointments = appointmentService.getMyAppointments(view,
                search, pageable);
        return ResponseEntity.ok(appointments);
    }

    /**
     * Get appointments for a specific doctor
     * Accessible by Admin, Doctor, and Receptionist
     */
    /**
     * Get appointments for a specific doctor
     * Accessible by Admin, Doctor, and Receptionist
     */
    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getAppointmentsByDoctor(@PathVariable Long doctorId,
            @RequestParam(required = false) String view) {
        List<Appointment> appointments = appointmentService.getAppointmentsByDoctor(doctorId, view);
        return ResponseEntity.ok(appointments);
    }

    /**
     * Get appointments for a specific patient (History)
     * Accessible by Admin, Doctor, and Receptionist
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getAppointmentsByPatient(@PathVariable String patientId) {
        List<Appointment> appointments = appointmentService.getAppointmentsByPatient(patientId);
        return ResponseEntity.ok(appointments);
    }

    /**
     * Get appointment by ID
     * Accessible by all roles
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getAppointmentById(@PathVariable String id) {
        Appointment appointment = appointmentService.getAppointmentByPublicId(id);
        return ResponseEntity.ok(appointment);
    }

    /**
     * Delete (Soft Delete) an appointment
     * Only Hospital Admin can delete appointments
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> deleteAppointment(@PathVariable String id, @RequestParam(required = false) String reason) {
        appointmentService.deleteAppointment(id, reason);
        return ResponseEntity.ok("Appointment deleted successfully");
    }

    /**
     * Get dashboard statistics (Today's, Pending, Total)
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getDashboardStats() {
        java.util.Map<String, Long> stats = appointmentService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Update appointment status
     */
    /**
     * Update appointment details (Status & Notes)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updateAppointment(@PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        String status = payload.get("status");
        String notes = payload.get("notes");

        Appointment updatedAppointment = appointmentService.updateDetails(id, status, notes);
        return ResponseEntity.ok(updatedAppointment);
    }

    /**
     * Update appointment status (Legacy/Specific)
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> updateAppointmentStatus(@PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        String status = payload.get("status");
        String reason = payload.get("reason");
        if (status == null) {
            throw new RuntimeException("Status is required");
        }
        Appointment updatedAppointment = appointmentService.updateStatus(id, status, reason);
        return ResponseEntity.ok(updatedAppointment);
    }
}
