package com.hms.service.import_;

import com.hms.dto.import_.ParsedSheet;
import com.hms.entity.ImportBatch;
import com.hms.entity.ImportEntityType;
import com.hms.entity.ImportStatus;
import com.hms.entity.Patient;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ImportBatchService {

    private final ImportBatchRepository batchRepository;
    private final ImportRowErrorRepository rowErrorRepository;
    private final PatientRepository patientRepository;
    private final OpdRepository opdRepository;
    private final BillingRepository billingRepository;
    private final SecurityContextHelper securityHelper;
    private final AuditLogService auditLogService;
    private final ImportEngine importEngine;

    public ImportBatchService(ImportBatchRepository batchRepository,
                              ImportRowErrorRepository rowErrorRepository,
                              PatientRepository patientRepository,
                              OpdRepository opdRepository,
                              BillingRepository billingRepository,
                              SecurityContextHelper securityHelper,
                              AuditLogService auditLogService,
                              ImportEngine importEngine) {
        this.batchRepository = batchRepository;
        this.rowErrorRepository = rowErrorRepository;
        this.patientRepository = patientRepository;
        this.opdRepository = opdRepository;
        this.billingRepository = billingRepository;
        this.securityHelper = securityHelper;
        this.auditLogService = auditLogService;
        this.importEngine = importEngine;
    }

    public ImportBatch requireOwnBatch(String publicId) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        return batchRepository.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Import not found"));
    }

    /**
     * Reverses a batch by soft-deleting the rows it created. Never a hard delete: the records may
     * be referenced elsewhere, and a soft delete keeps legacy_id in place so a corrected re-import
     * matches and reactivates them instead of colliding on the unique index.
     */
    @Transactional
    public ImportBatch undo(String publicId) {
        ImportBatch batch = requireOwnBatch(publicId);

        if (!batch.isUndoable()) {
            throw new IllegalArgumentException(
                    "This import cannot be undone (status: " + batch.getStatus() + ").");
        }

        List<Patient> imported = patientRepository.findByImportBatchId(batch.getId());
        if (!imported.isEmpty()) {
            List<Long> ids = imported.stream().map(Patient::getId).toList();
            long visits = opdRepository.countByPatientIdIn(ids);
            long bills = visits > 0 ? 0 : billingRepository.countByPatientIdIn(ids);
            if (visits > 0 || bills > 0) {
                throw new IllegalArgumentException(
                        "Cannot undo: " + (visits > 0 ? visits + " visit(s)" : bills + " bill(s)")
                        + " have been recorded against patients from this import. Undoing would "
                        + "delete live clinical data. Remove or reassign those records first.");
            }
            imported.forEach(p -> p.setIsActive(false));
            patientRepository.saveAll(imported);
        }

        batch.setStatus(ImportStatus.UNDONE);
        batch.setUndoneAt(LocalDateTime.now());
        batchRepository.save(batch);

        try {
            auditLogService.logAction("IMPORT_UNDO",
                    "Reversed import batch " + batch.getPublicId() + " (" + imported.size() + " patients)",
                    securityHelper.getCurrentUserEmail(), batch.getHospitalId(),
                    "ImportBatch", String.valueOf(batch.getId()), null);
        } catch (Exception ignored) {
            // audit logging is best-effort by convention in this codebase
        }

        return batch;
    }

    public List<ImportBatch> listBatches() {
        return batchRepository.findByHospitalIdOrderByCreatedAtDesc(securityHelper.getCurrentHospitalId());
    }

    /**
     * Creates a batch, applies the sheet, and records the outcome. The batch is saved before the
     * run so that every written row has a real import_batch_id to point at — that lineage is the
     * only thing undo has to work with.
     */
    @Transactional
    public ImportBatch commit(ParsedSheet sheet, Map<String, String> mapping, String sourceFilename) {

        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (batchRepository.existsByHospitalIdAndStatus(hospitalId, ImportStatus.RUNNING)) {
            throw new IllegalArgumentException(
                    "An import is already running for this hospital. Wait for it to finish before starting another.");
        }

        ImportBatch batch = new ImportBatch();
        batch.setHospitalId(hospitalId);
        batch.setEntityType(ImportEntityType.PATIENT);
        batch.setSourceFilename(sourceFilename);
        batch.setSheetName(sheet.sheetName());
        batch.setMappingJson(mappingToJson(mapping));
        batch.setCreatedBy(securityHelper.getCurrentUserEmail());
        batch.setStatus(ImportStatus.RUNNING);
        batch = batchRepository.save(batch);

        try {
            importEngine.commit(sheet, mapping, batch);
        } catch (RuntimeException e) {
            batch.setStatus(ImportStatus.FAILED);
            batchRepository.save(batch);
            throw e;
        }

        batchRepository.save(batch);

        try {
            auditLogService.logAction("IMPORT_COMMIT",
                    "Imported " + batch.getCreatedCount() + " new and " + batch.getUpdatedCount()
                            + " updated patient(s) from " + sourceFilename
                            + "; " + batch.getFailedCount() + " row(s) failed",
                    batch.getCreatedBy(), hospitalId, "ImportBatch", String.valueOf(batch.getId()), null);
        } catch (Exception ignored) {
            // audit logging is best-effort by convention in this codebase
        }

        return batch;
    }

    private String mappingToJson(Map<String, String> mapping) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey().replace("\"", "\\\"")).append("\":")
              .append("\"").append(e.getValue().replace("\"", "\\\"")).append("\"");
            first = false;
        }
        return sb.append("}").toString();
    }
}
