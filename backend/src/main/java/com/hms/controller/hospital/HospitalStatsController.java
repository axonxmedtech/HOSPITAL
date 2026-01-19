package com.hms.controller.hospital;

import com.hms.service.hospital.AppointmentService;
import com.hms.service.hospital.DoctorService;
import com.hms.service.hospital.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * HospitalStatsController - Provides dashboard statistics for Hospital Admin
 * 
 * This controller provides aggregate stats for the Overview dashboard:
 * - Total Patients
 * - Total Doctors
 * - Today's Appointments count
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/stats")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR')")
public class HospitalStatsController {

    @Autowired
    private PatientService patientService;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private AppointmentService appointmentService;

    /**
     * Get dashboard statistics for Hospital Admin Overview
     * Returns total patients, total doctors, and today's appointments count
     */
    @GetMapping
    public ResponseEntity<Map<String, Long>> getDashboardStats() {
        long totalPatients = patientService.getAllPatients().size();
        long totalDoctors = doctorService.getAllDoctors(PageRequest.of(0, 1))
                .getTotalElements();
        long todaysAppointments = appointmentService.getTodaysAppointments().size();

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalPatients", totalPatients);
        stats.put("totalDoctors", totalDoctors);
        stats.put("todaysAppointments", todaysAppointments);

        return ResponseEntity.ok(stats);
    }
}
