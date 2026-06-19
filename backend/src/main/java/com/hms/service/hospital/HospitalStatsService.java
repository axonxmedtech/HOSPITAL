package com.hms.service.hospital;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.hms.entity.Appointment;
import com.hms.entity.IpdAdmission;
import com.hms.entity.Opd;
import com.hms.entity.Patient;
import com.hms.entity.Doctor;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.OpdRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.DoctorRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

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

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private OpdRepository opdRepository;

    @Autowired
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Cacheable(value = "hospitalStats", key = "#hospitalId")
    public Map<String, Long> getStats(Long hospitalId) {
        long totalPatients = patientService.getPatientCount();
        long totalDoctors = doctorService.getAllDoctors(PageRequest.of(0, 1))
                .getTotalElements();
        long todaysAppointments = appointmentService.getTodaysAppointmentsCount();

        // Patient-focused stats for Overview tab
        java.time.LocalDateTime localStartOfToday = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime localEndOfToday = java.time.LocalDate.now().atTime(java.time.LocalTime.MAX);
        java.time.LocalDateTime localStartOfMonth = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();

        java.time.ZoneId sysZone = java.time.ZoneId.systemDefault();
        java.time.ZoneOffset utcOffset = java.time.ZoneOffset.UTC;

        LocalDateTime startOfToday = localStartOfToday.atZone(sysZone).withZoneSameInstant(utcOffset).toLocalDateTime();
        LocalDateTime endOfToday = localEndOfToday.atZone(sysZone).withZoneSameInstant(utcOffset).toLocalDateTime();
        LocalDateTime startOfMonth = localStartOfMonth.atZone(sysZone).withZoneSameInstant(utcOffset).toLocalDateTime();

        long patientsToday = patientRepository.countByHospitalIdAndIsActiveTrueAndCreatedAtBetween(
                hospitalId, startOfToday, endOfToday);
        long patientsThisMonth = patientRepository.countByHospitalIdAndIsActiveTrueAndCreatedAtBetween(
                hospitalId, startOfMonth, endOfToday);

        Map<String, Long> stats = new HashMap<>();
        // Existing keys (kept for backward compatibility)
        stats.put("totalPatients", totalPatients);
        stats.put("totalDoctors", totalDoctors);
        stats.put("todaysAppointments", todaysAppointments);
        // New patient-focused keys for Overview stat cards
        stats.put("totalRegisteredPatients", totalPatients);
        stats.put("patientsThisMonth", patientsThisMonth);
        stats.put("patientsToday", patientsToday);
        return stats;
    }

    /**
     * Get patient activity for a specific date.
     * Aggregates OPD visits, Appointments, and IPD admissions.
     * Returns list sorted by activity time descending (latest first).
     */
    public List<Map<String, Object>> getPatientActivityByDate(Long hospitalId, LocalDate date) {
        java.time.LocalDateTime localStartOfDay = date.atStartOfDay();
        java.time.LocalDateTime localEndOfOfDay = date.atTime(LocalTime.MAX);

        java.time.ZoneId sysZone = java.time.ZoneId.systemDefault();
        java.time.ZoneOffset utcOffset = java.time.ZoneOffset.UTC;

        LocalDateTime startOfDay = localStartOfDay.atZone(sysZone).withZoneSameInstant(utcOffset).toLocalDateTime();
        LocalDateTime endOfDay = localEndOfOfDay.atZone(sysZone).withZoneSameInstant(utcOffset).toLocalDateTime();

        List<Map<String, Object>> activities = new ArrayList<>();

        // Collect all doctor IDs and patient IDs for batch lookup
        Set<Long> doctorIds = new HashSet<>();
        Set<Long> patientIds = new HashSet<>();

        // 1. OPD records for this date
        List<Opd> opds = opdRepository.searchByHospitalAndDateRange(
                hospitalId, null, startOfDay, endOfDay, null, PageRequest.of(0, 1000)).getContent();
        for (Opd opd : opds) {
            if (opd.getDoctor() != null) doctorIds.add(opd.getDoctor().getId());
            if (opd.getPatient() != null) patientIds.add(opd.getPatient().getId());
        }

        // 2. Appointments for this date
        List<Appointment> appointments = appointmentRepository
                .findByHospitalIdAndAppointmentDateAndIsActiveTrue(hospitalId, date);
        for (Appointment appt : appointments) {
            if (appt.getDoctorId() != null) doctorIds.add(appt.getDoctorId());
            if (appt.getPatientId() != null) patientIds.add(appt.getPatientId());
        }

        // 3. IPD admissions for this date
        List<IpdAdmission> ipdAdmissions = ipdAdmissionRepository
                .findByHospitalIdAndAdmissionDatetimeBetween(hospitalId, startOfDay, endOfDay);
        for (IpdAdmission ipd : ipdAdmissions) {
            if (ipd.getDoctorId() != null) doctorIds.add(ipd.getDoctorId());
            if (ipd.getPatientId() != null) patientIds.add(ipd.getPatientId());
        }

        // Batch fetch doctors and patients for name resolution
        Map<Long, String> doctorNameMap = new HashMap<>();
        if (!doctorIds.isEmpty()) {
            List<Doctor> doctors = doctorRepository.findAllById(doctorIds);
            for (Doctor doc : doctors) {
                doctorNameMap.put(doc.getId(), doc.getName());
            }
        }

        Map<Long, Patient> patientMap = new HashMap<>();
        if (!patientIds.isEmpty()) {
            List<Patient> patients = patientRepository.findAllById(patientIds);
            for (Patient pat : patients) {
                patientMap.put(pat.getId(), pat);
            }
        }

        // Build OPD activity entries
        for (Opd opd : opds) {
            Map<String, Object> entry = new HashMap<>();
            Patient pat = opd.getPatient() != null ? patientMap.get(opd.getPatient().getId()) : null;
            entry.put("patientId", pat != null ? (pat.getCustomId() != null ? pat.getCustomId() : pat.getPublicId()) : null);
            entry.put("patientPublicId", pat != null ? pat.getPublicId() : null);
            entry.put("patientName", pat != null ? pat.getName() : "Unknown");
            entry.put("phone", pat != null ? pat.getPhone() : null);
            entry.put("activityType", "OPD");
            entry.put("activityTime", opd.getCreatedAt());
            entry.put("doctorName", opd.getDoctor() != null ? doctorNameMap.getOrDefault(opd.getDoctor().getId(), "Unknown") : "N/A");
            entry.put("details", opd.getCaseId());
            entry.put("status", opd.getStatus() != null ? opd.getStatus().toString() : null);
            activities.add(entry);
        }

        // Build Appointment activity entries
        for (Appointment appt : appointments) {
            Map<String, Object> entry = new HashMap<>();
            Patient pat = appt.getPatientId() != null ? patientMap.get(appt.getPatientId()) : null;
            entry.put("patientId", pat != null ? (pat.getCustomId() != null ? pat.getCustomId() : pat.getPublicId()) : null);
            entry.put("patientPublicId", pat != null ? pat.getPublicId() : null);
            entry.put("patientName", appt.getPatientName() != null ? appt.getPatientName() : (pat != null ? pat.getName() : "Unknown"));
            entry.put("phone", pat != null ? pat.getPhone() : appt.getPatientPhone());
            entry.put("activityType", "APPOINTMENT");
            // Combine date + time for sorting
            LocalDateTime activityTime = appt.getAppointmentTime() != null
                    ? appt.getAppointmentDate().atTime(appt.getAppointmentTime())
                    : appt.getAppointmentDate().atStartOfDay();
            entry.put("activityTime", activityTime);
            entry.put("doctorName", appt.getDoctorName() != null ? appt.getDoctorName() : doctorNameMap.getOrDefault(appt.getDoctorId(), "Unknown"));
            entry.put("details", appt.getCustomId() != null ? appt.getCustomId() : appt.getPublicId());
            entry.put("status", appt.getStatus());
            activities.add(entry);
        }

        // Build IPD activity entries
        for (IpdAdmission ipd : ipdAdmissions) {
            Map<String, Object> entry = new HashMap<>();
            Patient pat = patientMap.get(ipd.getPatientId());
            entry.put("patientId", pat != null ? (pat.getCustomId() != null ? pat.getCustomId() : pat.getPublicId()) : null);
            entry.put("patientPublicId", pat != null ? pat.getPublicId() : null);
            entry.put("patientName", pat != null ? pat.getName() : "Unknown");
            entry.put("phone", pat != null ? pat.getPhone() : null);
            entry.put("activityType", "IPD");
            entry.put("activityTime", ipd.getAdmissionDatetime());
            entry.put("doctorName", doctorNameMap.getOrDefault(ipd.getDoctorId(), "Unknown"));
            entry.put("details", ipd.getIpdNumber());
            entry.put("status", ipd.getStatus());
            activities.add(entry);
        }

        // Sort by activityTime descending (latest first)
        activities.sort((a, b) -> {
            Object timeA = a.get("activityTime");
            Object timeB = b.get("activityTime");
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            LocalDateTime dtA = (LocalDateTime) timeA;
            LocalDateTime dtB = (LocalDateTime) timeB;
            return dtB.compareTo(dtA);
        });

        return activities;
    }

    @CacheEvict(value = "hospitalStats", key = "#hospitalId")
    public void evictStats(Long hospitalId) {
        // Handled automatically by Spring Cache eviction mechanism
    }
}
