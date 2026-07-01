package com.hms.controller.hospital;

import com.hms.dto.CreateOpdRequest;
import com.hms.entity.Opd;
import com.hms.entity.Hospital;
import com.hms.entity.MedicalRecord;
import com.hms.service.hospital.OpdService;
import com.hms.security.SecurityContextHelper;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.MedicalRecordRepository;
import com.hms.entity.Doctor;
import java.util.Collections;
import java.util.Optional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.ByteArrayOutputStream;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/hospital/opd")
public class OpdController {

    @Autowired
    private com.hms.service.PdfService pdfService;


    private static final Logger logger = LoggerFactory.getLogger(OpdController.class);

    private final OpdService opdService;
    private final SpringTemplateEngine templateEngine;
    private final SecurityContextHelper securityHelper;
    private final DoctorRepository doctorRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final HospitalRepository hospitalRepository;

    public OpdController(OpdService opdService, SpringTemplateEngine templateEngine,
                         SecurityContextHelper securityHelper, DoctorRepository doctorRepository,
                         MedicalRecordRepository medicalRecordRepository, HospitalRepository hospitalRepository) {
        this.opdService = opdService;
        this.templateEngine = templateEngine;
        this.securityHelper = securityHelper;
        this.doctorRepository = doctorRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.hospitalRepository = hospitalRepository;
    }

    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @PostMapping
    public ResponseEntity<Opd> createOpd(@jakarta.validation.Valid @RequestBody CreateOpdRequest req) {
        Opd opd = opdService.createOpd(req);
        return ResponseEntity.ok(opd);
    }

    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getOpdPdf(@PathVariable String id) {
        Long opdId;
        try {
            if (id.startsWith("OPD-")) {
                opdId = Long.parseLong(id.substring(4));
            } else {
                opdId = Long.parseLong(id);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        Opd opd = opdService.getOpdById(opdId);
        if (opd == null) return ResponseEntity.notFound().build();

        com.hms.entity.Patient patient = opd.getPatient();
        Doctor doctor = opd.getDoctor();
        Hospital hospital = null;
        if (patient != null && patient.getHospitalId() != null) {
            hospital = hospitalRepository.findById(patient.getHospitalId()).orElse(null);
        }
        MedicalRecord record = medicalRecordRepository.findByOpdId(opdId).orElse(null);

        try (java.io.ByteArrayInputStream pdfStream = pdfService.generateCasePaperPdf(hospital, doctor, patient, opd, record)) {
            byte[] pdfBytes = pdfStream.readAllBytes();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add("Content-Disposition", "inline; filename=case_" + opd.getCaseId() + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }


    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping
    public ResponseEntity<?> listOpds(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) com.hms.entity.Opd.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        var result = opdService.getOpds(search, date, status, pageable);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/queue/doctor/{doctorId}")
    public ResponseEntity<java.util.List<?>> getDoctorQueue(@PathVariable Long doctorId) {
        java.util.List<?> queue = opdService.getQueueForDoctor(doctorId);
        return ResponseEntity.ok(queue);
    }

    /**
     * Get queue for the currently authenticated doctor.
     * This maps the authenticated user (by email + hospital) to a Doctor record
     * and returns that doctor's queue. Useful for doctor clients that only have
     * the user authentication context.
     */
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    @GetMapping("/queue/my")
    public ResponseEntity<java.util.List<?>> getMyQueue() {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            String email = securityHelper.getCurrentUserEmail();
            logger.debug("/hospital/opd/queue/my called - hospitalId={}, email={}", hospitalId, email);
            Optional<Doctor> d = doctorRepository.findByEmailAndHospitalId(email, hospitalId);
            if (d.isPresent()) {
                Long docId = d.get().getId();
                java.util.List<?> queue = opdService.getQueueForDoctor(docId);
                logger.debug("Doctor id={} -> queue size={}", docId, queue == null ? 0 : queue.size());
                return ResponseEntity.ok(queue == null ? java.util.List.of() : queue);
            }
            logger.debug("No doctor record found for email={} hospitalId={}", email, hospitalId);
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            logger.error("Failed to fetch my queue", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/{id}")
    public ResponseEntity<Opd> getOpd(@PathVariable String id) {
        Long opdId;
        try {
            if (id.startsWith("OPD-")) {
                opdId = Long.parseLong(id.substring(4));
            } else {
                opdId = Long.parseLong(id);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        Opd opd = opdService.getOpdById(opdId);
        if (opd == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opd);
    }

    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/queue")
    public ResponseEntity<java.util.List<?>> getHospitalQueue() {
        java.util.List<?> queue = opdService.getHospitalQueue();
        return ResponseEntity.ok(queue);
    }

    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @GetMapping("/today-followups")
    public ResponseEntity<java.util.List<?>> getTodayFollowUps() {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            if (hospitalId == null) {
                return ResponseEntity.ok(java.util.List.of());
            }

            // Trigger auto-queueing first so today's followups are created and queued
            opdService.autoQueueTodaysFollowupsForHospital(hospitalId);

            String email = securityHelper.getCurrentUserEmail();
            java.util.Optional<com.hms.entity.Doctor> d = doctorRepository.findByEmailAndHospitalId(email, hospitalId);

            java.util.List<com.hms.entity.MedicalRecord> followUps;
            java.time.LocalDate today = java.time.LocalDate.now();
            if (d.isPresent() && "DOCTOR".equals(securityHelper.getCurrentUserRole())) {
                followUps = opdService.getFollowUpsForDoctorToday(hospitalId, d.get().getId(), today);
            } else {
                followUps = opdService.getFollowUpsForHospitalToday(hospitalId, today);
            }

            java.util.List<java.util.Map<String, Object>> response = new java.util.ArrayList<>();
            for (com.hms.entity.MedicalRecord mr : followUps) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", mr.getId());
                map.put("followUpDate", mr.getFollowUpDate());
                map.put("diagnosis", mr.getDiagnosis());

                opdService.getPatientNameAndCustomIdAndPublicId(mr.getPatientId()).ifPresent(p -> {
                    map.put("patientName", p.get("name"));
                    map.put("patientCustomId", p.get("customId"));
                    map.put("patientPublicId", p.get("publicId"));
                });

                opdService.getDoctorName(mr.getDoctorId()).ifPresent(name -> {
                    map.put("doctorName", name);
                });

                response.add(map);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to fetch today's followups", e);
            return ResponseEntity.ok(java.util.List.of());
        }
    }

    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @GetMapping("/report/pdf")
    public ResponseEntity<?> downloadOpdReportPdf(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(required = false) com.hms.entity.Opd.Status status,
            @RequestParam(required = false) String reportType) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) {
            throw new com.hms.exception.UnauthorizedException("Hospital context not found");
        }

        String dateStr = (date != null) ? date.toString() : null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 1000);
        java.util.List<Opd> opds = opdService.getOpds(null, dateStr, status, pageable).getContent();

        com.hms.entity.Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        java.io.ByteArrayInputStream pdfStream = pdfService.generateOpdReportPdf(hospital, date, opds, reportType);
        org.springframework.core.io.InputStreamResource resource = new org.springframework.core.io.InputStreamResource(pdfStream);

        String filename = "OPD_Report_" + (date != null ? date.toString() : "AllTime") + ".pdf";
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(resource);
    }
}

