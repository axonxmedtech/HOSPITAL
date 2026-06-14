package com.hms.controller.hospital;

import com.hms.dto.AddDoctorRequest;
import com.hms.entity.Doctor;
import com.hms.repository.OpdRepository;
import com.hms.service.hospital.DoctorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * DoctorController - REST controller for doctor management
 * 
 * This controller provides endpoints for:
 * - Adding new doctors (Hospital Admin only)
 * - Listing doctors (Hospital Admin and Doctor)
 * - Getting doctor details (Hospital Admin and Doctor)
 * 
 * All operations are automatically filtered by hospital_id.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/hospital/doctors")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class DoctorController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DoctorController.class);

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private OpdRepository opdRepository;

    @Autowired
    private com.hms.security.SecurityContextHelper securityHelper;

    /**
     * Add a new doctor
     * Only Hospital Admin can add doctors
     * Creates both Doctor record and User account
     * 
     * @param request AddDoctorRequest containing doctor details and password
     * @return Created Doctor entity
     */
    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> addDoctor(@Valid @RequestBody AddDoctorRequest request) {
        // Create Doctor entity from request
        Doctor doctor = new Doctor();
        doctor.setName(request.getName());
        doctor.setSpecialization(request.getSpecialization());
        doctor.setPhone(request.getPhone());
        doctor.setEmail(request.getEmail());

        Doctor createdDoctor = doctorService.addDoctor(doctor, request.getPassword());
        return ResponseEntity.ok(createdDoctor);
    }

    /**
     * Update an existing doctor
     * Only Hospital Admin can update doctors (Name, Spec, Phone)
     * 
     * @param id     Doctor ID
     * @param doctor Updated doctor details
     * @return Updated Doctor entity
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> updateDoctor(@PathVariable String id, @RequestBody Doctor doctor) {
        try {
            // Note: Not validating full request as password/email might not be sent
            Doctor updatedDoctor = doctorService.updateDoctor(id, doctor);
            return ResponseEntity.ok(updatedDoctor);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get all doctors for the current hospital
     * Accessible by Hospital Admin, Doctor, and Receptionist
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getAllDoctors(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(doctorService.searchDoctors(search));
        }
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(doctorService.getAllDoctors(pageable));
    }

    /**
     * Get doctor by ID
     * Accessible by Hospital Admin, Doctor, and Receptionist
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getDoctorById(@PathVariable String id) {
        try {
            Doctor doctor = doctorService.getDoctorByPublicId(id);
            return ResponseEntity.ok(doctor);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Delete (Soft Delete) a doctor
     * Only Hospital Admin can delete doctors
     * 
     * @param id Doctor ID
     * @return Success message
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> deleteDoctor(@PathVariable String id, @RequestParam(required = false) String reason) {
        try {
            doctorService.deleteDoctor(id, reason);
            return ResponseEntity.ok("Doctor deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Submit a consultation (Diagnosis + Prescription)
     * Only Doctors can submit consultations
     */
    @PostMapping("/consultation")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HOSPITAL_ADMIN')")
    public ResponseEntity<?> submitConsultation(@RequestBody com.hms.dto.ConsultationRequest request) {
        try {
            com.hms.entity.Opd opd = doctorService.submitConsultation(request);
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "Consultation submitted successfully");
            if (opd != null) {
                response.put("opdId", opd.getId());
                response.put("opd", opd);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/consultation/{appointmentId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    public ResponseEntity<?> getConsultationDetails(@PathVariable String appointmentId) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            // Resolve Appointment ID
            java.util.Optional<com.hms.entity.Appointment> apptOpt = appointmentRepository
                    .findByPublicIdAndHospitalIdAndIsActiveTrue(appointmentId, hospitalId);
            if (apptOpt.isEmpty()) {
                try {
                    Long id = Long.parseLong(appointmentId);
                    apptOpt = appointmentRepository.findByIdAndHospitalIdAndIsActiveTrue(id, hospitalId);
                } catch (NumberFormatException e) {
                }
            }
            com.hms.entity.Appointment appointment = apptOpt
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            com.hms.entity.MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointment.getId())
                    .orElseThrow(() -> new RuntimeException("Consultation record not found"));

            java.util.List<com.hms.entity.Prescription> prescriptions = prescriptionRepository
                    .findByMedicalRecordId(record.getId());

            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("medicalRecord", record);
            response.put("prescriptions", prescriptions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Autowired
    private com.hms.service.PdfService pdfService;
    @Autowired
    private com.hms.repository.MedicalRecordRepository medicalRecordRepository;
    @Autowired
    private com.hms.repository.PrescriptionRepository prescriptionRepository;
    @Autowired
    private com.hms.service.hospital.PatientService patientService;
    @Autowired
    private com.hms.repository.HospitalRepository hospitalRepository;
    @Autowired
    private com.hms.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.hms.repository.AppointmentRepository appointmentRepository;

    @GetMapping("/prescription/{appointmentId}/pdf")
    public ResponseEntity<?> downloadPrescription(@PathVariable String appointmentId) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            logger.info("Downloading prescription for appointment: {} in hospital: {}", appointmentId, hospitalId);

            // Resolve Appointment ID
            java.util.Optional<com.hms.entity.Appointment> apptOpt = appointmentRepository
                    .findByPublicIdAndHospitalIdAndIsActiveTrue(appointmentId, hospitalId);
            if (apptOpt.isEmpty()) {
                try {
                    Long id = Long.parseLong(appointmentId);
                    apptOpt = appointmentRepository.findByIdAndHospitalIdAndIsActiveTrue(id, hospitalId);
                } catch (NumberFormatException e) {
                }
            }
            com.hms.entity.Appointment appointment = apptOpt
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            com.hms.entity.MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointment.getId())
                    .orElseThrow(() -> new RuntimeException("Consultation not found for this appointment"));
            java.util.List<com.hms.entity.Prescription> prescriptions = prescriptionRepository
                    .findByMedicalRecordId(record.getId());

            Doctor doctor = doctorRepository.findById(record.getDoctorId())
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));

            com.hms.entity.Patient patient = patientService.getPatientById(record.getPatientId());
            com.hms.entity.Hospital hospital = hospitalRepository.findById(record.getHospitalId())
                    .orElseThrow(() -> new RuntimeException("Hospital not found"));

            java.io.ByteArrayInputStream pdf = pdfService.generatePrescriptionPdf(hospital, doctor, patient, record,
                    prescriptions);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "inline; filename=prescription_" + appointmentId + ".pdf");

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new InputStreamResource(pdf));
        } catch (Exception e) {
            logger.error("Error downloading prescription for appointment {}: {}", appointmentId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

        @GetMapping("/prescription/opd/{opdId}/pdf")
        public ResponseEntity<?> downloadPrescriptionByOpd(@PathVariable Long opdId) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();

            com.hms.entity.Opd opd = opdRepository.findById(opdId)
                .orElseThrow(() -> new RuntimeException("OPD not found"));

            com.hms.entity.MedicalRecord record = medicalRecordRepository.findByOpdId(opd.getId())
                .orElseThrow(() -> new RuntimeException("Consultation not found for this OPD"));

            java.util.List<com.hms.entity.Prescription> prescriptions = prescriptionRepository
                .findByMedicalRecordId(record.getId());

            Doctor doctor = doctorRepository.findById(record.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

            com.hms.entity.Patient patient = patientService.getPatientById(record.getPatientId());
            com.hms.entity.Hospital hospital = hospitalRepository.findById(record.getHospitalId())
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

            java.io.ByteArrayInputStream pdf = pdfService.generatePrescriptionPdf(hospital, doctor, patient, record,
                prescriptions);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "inline; filename=prescription_opd_" + opdId + ".pdf");

            return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(pdf));
        } catch (Exception e) {
            logger.error("Error downloading prescription for opd {}: {}", opdId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        }
}
