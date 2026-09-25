package com.hms.controller.hospital;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.ApiResponse;
import com.hms.dto.import_.ImportBatchStatusResponse;
import com.hms.dto.import_.ImportCommitResponse;
import com.hms.dto.import_.ImportPreviewResponse;
import com.hms.security.SecurityContextHelper;
import com.hms.service.import_.ImportBatchQueryService;
import com.hms.service.import_.ImportCommitRequest;
import com.hms.service.import_.ImportEngine;
import com.hms.service.import_.ImportFilenames;
import com.hms.service.import_.ImportFormat;
import com.hms.service.import_.ImportMappingValidator;
import com.hms.service.import_.InvalidImportMappingException;
import com.hms.service.import_.SpooledUpload;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.hms.filter.UploadLimits;
import com.hms.exception.UploadTooLargeException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * Legacy patient import over HTTP — synchronous in this phase: a commit responds only after
 * {@link ImportEngine#commit} has finished, with the batch's final state.
 *
 * <p>Tenancy and actor come from the authenticated principal, nothing else: no request part,
 * parameter, mapping key, filename or spreadsheet column can name a hospital or a user. Every
 * status lookup is hospital + public id. HOSPITAL_ADMIN only, enforced here; patients are a CORE
 * capability of every tenant (see {@code ControllerModules}), exactly like {@code PatientController}.
 * Reachable from /hospital and /clinic, never /pharmacy: like patient documents, a pharmacy holds
 * no patient register to import into (see ClinicPharmacyIsolationTest, the review gate for aliases).
 *
 * <p>The upload is spooled once ({@link SpooledUpload}, try-with-resources) so the temp file is
 * gone on every path out of a request — success, refused mapping, parse failure, engine failure,
 * already-imported. The upload boundary is {@link UploadLimits}: 50 MiB for the file, enforced
 * before parsing by {@code UploadSizeGuardFilter}, and re-checked here.
 */
@RestController
@RequestMapping({"/hospital/patients/import", "/clinic/patients/import"})
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class PatientImportController {

    private static final TypeReference<Map<String, String>> FLAT_MAPPING = new TypeReference<>() {};

    private final ImportEngine engine;
    private final ImportMappingValidator mappingValidator;
    private final ImportBatchQueryService batchQuery;
    private final SecurityContextHelper securityHelper;
    private final ObjectMapper json;
    private final Path spoolDir;

    public PatientImportController(
            ImportEngine engine,
            ImportMappingValidator mappingValidator,
            ImportBatchQueryService batchQuery,
            SecurityContextHelper securityHelper,
            ObjectMapper json,
            @Value("${hms.import.spool-dir:#{null}}") String spoolDir) {
        this.engine = engine;
        this.mappingValidator = mappingValidator;
        this.batchQuery = batchQuery;
        this.securityHelper = securityHelper;
        this.json = json;
        this.spoolDir = spoolDir == null || spoolDir.isBlank() ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(spoolDir);
    }

    /** Dry run. Writes nothing. */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportPreviewResponse>> preview(
            MultipartHttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam("mapping") String mappingJson,
            @RequestParam(value = "sheetName", required = false) String sheetName) throws IOException {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        requireShape(request, file);
        Map<String, String> mapping = mappingValidator.validate(parseMapping(mappingJson));
        String name = ImportFilenames.sanitize(file.getOriginalFilename());
        ImportFormat format = requireFormat(name, file);

        try (InputStream in = file.getInputStream(); SpooledUpload upload = SpooledUpload.spool(in, spoolDir)) {
            List<String> sheets = format == ImportFormat.XLSX ? engine.sheetNames(upload) : List.of();
            return ResponseEntity.ok(ApiResponse.ok(
                    ImportPreviewResponse.from(engine.preview(upload, format, blankToNull(sheetName), mapping, hospitalId), sheets)));
        }
    }

    /** Imports the file. Synchronous: 200 with the finished batch, or a 409/400/5xx from the advice. */
    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportCommitResponse>> commit(
            MultipartHttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam("mapping") String mappingJson,
            @RequestParam(value = "sheetName", required = false) String sheetName) throws IOException {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        String actor = securityHelper.getCurrentUserEmail();
        requireShape(request, file);
        Map<String, String> mapping = mappingValidator.validate(parseMapping(mappingJson));
        String name = ImportFilenames.sanitize(file.getOriginalFilename());
        ImportFormat format = requireFormat(name, file);
        ImportCommitRequest commitRequest = new ImportCommitRequest(hospitalId, actor, name, blankToNull(sheetName), mapping);

        try (InputStream in = file.getInputStream(); SpooledUpload upload = SpooledUpload.spool(in, spoolDir)) {
            return ResponseEntity.ok(ApiResponse.ok("Import complete", ImportCommitResponse.from(engine.commit(upload, format, commitRequest))));
        }
    }

    @GetMapping("/{batchPublicId}")
    public ResponseEntity<ApiResponse<ImportBatchStatusResponse>> status(@PathVariable String batchPublicId) {
        return ResponseEntity.ok(ApiResponse.ok(batchQuery.status(securityHelper.getCurrentHospitalId(), batchPublicId)));
    }

    // ── request shaping ──────────────────────────────────────────────────────

    /** A flat JSON object of header → field key; anything nested or non-string is refused by the type itself. */
    private Map<String, String> parseMapping(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            throw new InvalidImportMappingException("The mapping is required.");
        }
        if (mappingJson.length() > 64 * 1024) {
            throw new InvalidImportMappingException("The mapping is too large.");
        }
        try {
            return json.readValue(mappingJson, FLAT_MAPPING);
        } catch (IOException e) {
            throw new InvalidImportMappingException("The mapping must be a JSON object of column header to patient field.");
        }
    }

    /**
     * Defence in depth behind {@link com.hms.filter.UploadSizeGuardFilter}, which is the resource
     * boundary: the file itself may not exceed 50 MiB, and the request may carry exactly one file
     * part named {@code file} plus the {@code mapping} and optional {@code sheetName} parameters —
     * no second file, no arbitrary extra parts.
     */
    private static void requireShape(MultipartHttpServletRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > UploadLimits.IMPORT_FILE_BYTES) {
            throw new UploadTooLargeException("The import file is too large. The maximum is 50 MB.");
        }
        int fileParts = request.getMultiFileMap().values().stream().mapToInt(List::size).sum();
        if (fileParts != 1 || !request.getMultiFileMap().containsKey("file")) {
            throw new IllegalArgumentException("Upload exactly one file, in the \"file\" part.");
        }
        // Only the multipart PARTS are shape-checked; a query-string parameter is not an upload
        // and is ignored exactly as before (the tenant never comes from it either way).
        String query = request.getQueryString();
        for (String name : request.getParameterMap().keySet()) {
            if (ALLOWED_PARAMS.contains(name)) continue;
            boolean fromQuery = query != null && (query.startsWith(name + "=") || query.contains("&" + name + "="));
            if (!fromQuery) {
                throw new IllegalArgumentException("Unexpected request part \"" + name + "\".");
            }
        }
    }

    private static final java.util.Set<String> ALLOWED_PARAMS = java.util.Set.of("mapping", "sheetName");

    private static ImportFormat requireFormat(String sanitizedName, MultipartFile file) {
        ImportFormat format = ImportFilenames.formatOf(sanitizedName);
        if (format == null) {
            throw new IllegalArgumentException("Only .xlsx and .csv files are supported.");
        }
        return format;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
