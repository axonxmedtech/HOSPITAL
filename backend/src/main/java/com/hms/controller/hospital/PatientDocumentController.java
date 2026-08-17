package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.PatientDocumentResponse;
import com.hms.service.documents.PatientDocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Records attached to a patient from outside — lab reports, scans, prescriptions.
 *
 * <p>Aliased to clinic as well: a clinic receives outside lab reports exactly as a hospital does.
 * Not module-gated, matching Files &amp; Access, because this is core to a patient record rather
 * than a plan feature.
 */
@RestController
@RequestMapping({"/hospital/patients/{patientPublicId}/documents",
                 "/clinic/patients/{patientPublicId}/documents"})
public class PatientDocumentController {

    private final PatientDocumentService service;

    public PatientDocumentController(PatientDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<ApiResponse<List<PatientDocumentResponse>>> list(
            @PathVariable String patientPublicId) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(patientPublicId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<ApiResponse<PatientDocumentResponse>> upload(
            @PathVariable String patientPublicId,
            @RequestParam("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate) {

        return ResponseEntity.ok(ApiResponse.ok("Record attached",
                service.upload(patientPublicId, file, title, documentType, documentDate)));
    }

    @GetMapping("/{documentPublicId}/file")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE')")
    public ResponseEntity<InputStreamResource> download(@PathVariable String documentPublicId) {
        PatientDocumentService.DownloadHandle handle = service.download(documentPublicId);

        return ResponseEntity.ok()
                // Always an attachment. Rendering a PDF or SVG inline would run it in this
                // application's origin, alongside the session.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFilename(handle.filename()) + "\"")
                .contentType(handle.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(handle.contentType()))
                .body(new InputStreamResource(handle.content()));
    }

    @DeleteMapping("/{documentPublicId}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<String>> remove(@PathVariable String documentPublicId) {
        service.softDelete(documentPublicId);
        return ResponseEntity.ok(ApiResponse.ok("Record removed", documentPublicId));
    }

    /**
     * Strips anything that could forge a header or escape the quoted value.
     *
     * <p>The name came from an uploader's machine, so a CR, LF or double quote in it would let
     * them append headers of their own to this response.
     */
    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "document";
        return filename.replaceAll("[\\r\\n\"\\\\]", "_");
    }
}
