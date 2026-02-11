package com.hms.controller.hospital;

import com.hms.dto.CreateOpdRequest;
import com.hms.entity.Opd;
import com.hms.service.hospital.OpdService;
import com.hms.security.SecurityContextHelper;
import com.hms.repository.DoctorRepository;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital/opd")
public class OpdController {

    private static final Logger logger = LoggerFactory.getLogger(OpdController.class);

    private final OpdService opdService;
    private final SpringTemplateEngine templateEngine;
    private final SecurityContextHelper securityHelper;
    private final DoctorRepository doctorRepository;

    public OpdController(OpdService opdService, SpringTemplateEngine templateEngine,
                         SecurityContextHelper securityHelper, DoctorRepository doctorRepository) {
        this.opdService = opdService;
        this.templateEngine = templateEngine;
        this.securityHelper = securityHelper;
        this.doctorRepository = doctorRepository;
    }

    @PostMapping
    public ResponseEntity<Opd> createOpd(@RequestBody CreateOpdRequest req) {
        Opd opd = opdService.createOpd(req);
        return ResponseEntity.ok(opd);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getOpdPdf(@PathVariable Long id) {
        Opd opd = opdService.getOpdById(id);
        if (opd == null) return ResponseEntity.notFound().build();

        Context ctx = new Context();
        ctx.setVariable("opd", opd);
        ctx.setVariable("patient", opd.getPatient());
        ctx.setVariable("doctor", opd.getDoctor());
        ctx.setVariable("receptionist", opd.getReceptionist());

        String html = templateEngine.process("case-paper", ctx);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, "");
            builder.toStream(baos);
            builder.run();
            byte[] pdf = baos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "case_" + opd.getCaseId() + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping
    public ResponseEntity<?> listOpds(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        var result = opdService.getOpds(search, pageable);
        return ResponseEntity.ok(result);
    }

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

    @GetMapping("/{id}")
    public ResponseEntity<Opd> getOpd(@PathVariable Long id) {
        Opd opd = opdService.getOpdById(id);
        if (opd == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opd);
    }

    @GetMapping("/queue")
    public ResponseEntity<java.util.List<?>> getHospitalQueue() {
        java.util.List<?> queue = opdService.getHospitalQueue();
        return ResponseEntity.ok(queue);
    }
}
