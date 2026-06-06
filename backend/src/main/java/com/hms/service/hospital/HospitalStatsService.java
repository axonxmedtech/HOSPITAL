package com.hms.service.hospital;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * HospitalStatsService - Cached service to fetch and evict tenant-specific hospital stats.
 */
@Service
public class HospitalStatsService {

    @Autowired
    private PatientService patientService;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private AppointmentService appointmentService;

    @Cacheable(value = "hospitalStats", key = "#hospitalId")
    public Map<String, Long> getStats(Long hospitalId) {
        long totalPatients = patientService.getPatientCount();
        long totalDoctors = doctorService.getAllDoctors(PageRequest.of(0, 1))
                .getTotalElements();
        long todaysAppointments = appointmentService.getTodaysAppointmentsCount();

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalPatients", totalPatients);
        stats.put("totalDoctors", totalDoctors);
        stats.put("todaysAppointments", todaysAppointments);
        return stats;
    }

    @CacheEvict(value = "hospitalStats", key = "#hospitalId")
    public void evictStats(Long hospitalId) {
        // Handled automatically by Spring Cache eviction mechanism
    }
}
