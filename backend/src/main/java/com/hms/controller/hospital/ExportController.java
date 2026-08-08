package com.hms.controller.hospital;

import com.hms.service.import_.HospitalExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads the calling hospital's data as an Excel workbook.
 *
 * <p>Admin-only, and scoped entirely by the JWT — there is no parameter selecting a hospital,
 * because an endpoint that returns every patient record a tenant holds should not have one.
 */
@RestController
@RequestMapping({"/hospital/exports", "/clinic/exports"})
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class ExportController {

    private final HospitalExportService exportService;

    public ExportController(HospitalExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/patients.xlsx")
    public ResponseEntity<byte[]> exportPatients() {
        byte[] workbook = exportService.exportAll();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + exportService.suggestedFilename())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }
}
