package com.hms.controller.hospital;

import com.hms.dto.ApiResponse;
import com.hms.dto.import_.ImportFieldDef;
import com.hms.dto.import_.ImportPreview;
import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.ImportBatch;
import com.hms.entity.ImportEntityType;
import com.hms.entity.ImportRowError;
import com.hms.repository.ImportBatchRepository;
import com.hms.repository.ImportRowErrorRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.import_.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Import API. hospital_id always comes from the JWT — a hospital_id column present in an uploaded
 * file is treated as ordinary data and never as tenancy, or a crafted spreadsheet would become a
 * cross-tenant write.
 */
@RestController
@RequestMapping({"/hospital/imports", "/clinic/imports"})
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class ImportController {

    /**
     * Request parameters that are never spreadsheet headers. Anything else in the parameter map is
     * treated as a header→field mapping entry.
     */
    private static final java.util.Set<String> RESERVED_PARAMS =
            java.util.Set.of("file", "entityType", "sheetName", "headers");

    /**
     * Defaulted (not injected) so the constructor signature matches exactly across production
     * wiring and the plain {@code new ImportController(...)} calls in tests. Spring overwrites it
     * via field injection from {@code hms.import.max-file-size} after construction; tests that
     * build the controller directly keep this literal default.
     */
    @Value("${hms.import.max-file-size:50MB}")
    private String maxFileSize = "50MB";

    private final ImportEngine engine;
    private final WorkbookParser parser;
    private final ColumnMapper columnMapper;
    private final ImportFieldRegistry fieldRegistry;
    private final ImportBatchService batchService;
    private final ImportBatchRepository batchRepository;
    private final ImportRowErrorRepository rowErrorRepository;
    private final SecurityContextHelper securityHelper;

    public ImportController(ImportEngine engine, WorkbookParser parser, ColumnMapper columnMapper,
                            ImportFieldRegistry fieldRegistry, ImportBatchService batchService,
                            ImportBatchRepository batchRepository,
                            ImportRowErrorRepository rowErrorRepository,
                            SecurityContextHelper securityHelper) {
        this.engine = engine;
        this.parser = parser;
        this.columnMapper = columnMapper;
        this.fieldRegistry = fieldRegistry;
        this.batchService = batchService;
        this.batchRepository = batchRepository;
        this.rowErrorRepository = rowErrorRepository;
        this.securityHelper = securityHelper;
    }

    @GetMapping("/fields")
    public ResponseEntity<ApiResponse<List<ImportFieldDef>>> fields(
            @RequestParam(defaultValue = "PATIENT") String entityType) {
        return ResponseEntity.ok(ApiResponse.ok(
                fieldRegistry.fieldsFor(ImportEntityType.valueOf(entityType))));
    }

    /** Parses the upload and returns headers, suggested mapping and sample rows. Writes nothing. */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "PATIENT") String entityType) throws IOException {

        validateUpload(file);
        ImportEntityType type = ImportEntityType.valueOf(entityType);
        ParsedSheet sheet = readSheet(file, null);
        Map<String, String> suggested = columnMapper.suggest(sheet.headers(), type);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "sheetName", sheet.sheetName(),
                "headers", sheet.headers(),
                "suggestedMapping", suggested,
                "unmapped", columnMapper.unmapped(sheet.headers(), suggested),
                "totalRows", sheet.rows().size(),
                "sampleRows", sheet.rows().stream().limit(10).toList())));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<ImportPreview>> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> mapping) throws IOException {

        validateUpload(file);
        ParsedSheet sheet = readSheet(file, null);
        return ResponseEntity.ok(ApiResponse.ok(previewSheet(sheet, toFieldMapping(mapping))));
    }

    /** Extracted so the tenant-isolation test can drive it without building a multipart request. */
    ImportPreview previewSheet(ParsedSheet sheet, Map<String, String> mapping) {
        return engine.dryRun(sheet, mapping, securityHelper.getCurrentHospitalId());
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<ImportBatch>> commit(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> mapping) throws IOException {

        validateUpload(file);
        ParsedSheet sheet = readSheet(file, null);
        ImportBatch batch = batchService.commit(sheet, toFieldMapping(mapping),
                file.getOriginalFilename());
        return ResponseEntity.ok(ApiResponse.ok("Import complete", batch));
    }

    /**
     * Strips the request's own parameters out of the header→field mapping.
     *
     * <p>{@code @RequestParam Map<String,String>} collects every parameter on the request, so
     * {@code file}, {@code entityType} and {@code sheetName} would otherwise be read as spreadsheet
     * headers and mapped to nonsense fields.
     *
     * <p>Shared by preview and commit deliberately. They diverged before this existed — commit
     * filtered and preview did not — which is the worst possible bug in this feature: the preview
     * would have shown an outcome the commit did not reproduce, and the admin's whole reason for
     * trusting the dry-run is that it predicts the commit exactly.
     */
    private Map<String, String> toFieldMapping(Map<String, String> requestParams) {
        Map<String, String> fieldMapping = new java.util.LinkedHashMap<>(requestParams);
        fieldMapping.keySet().removeAll(RESERVED_PARAMS);
        return fieldMapping;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ImportBatch>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(batchService.listBatches()));
    }

    @PostMapping("/{publicId}/undo")
    public ResponseEntity<ApiResponse<ImportBatch>> undo(@PathVariable String publicId) {
        return ResponseEntity.ok(ApiResponse.ok("Import reversed", batchService.undo(publicId)));
    }

    @GetMapping("/{publicId}/errors.csv")
    public ResponseEntity<byte[]> errorsCsv(@PathVariable String publicId,
                                            @RequestParam List<String> headers) {
        ImportBatch batch = batchService.requireOwnBatch(publicId);
        List<ImportRowError> errors = rowErrorRepository.findByBatchIdOrderByRowNumberAsc(batch.getId());
        byte[] body = ImportCsvWriter.write(errors, headers).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import-errors.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    private ParsedSheet readSheet(MultipartFile file, String sheetName) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".csv")) {
            return parser.parseCsv(file.getInputStream());
        }
        return parser.parseXlsx(file.getInputStream(), sheetName);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > DataSize.parse(maxFileSize).toBytes()) {
            throw new IllegalArgumentException("File is too large. Maximum size is 50 MB.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".csv")) {
            throw new IllegalArgumentException("Only .xlsx and .csv files are allowed.");
        }
    }
}
