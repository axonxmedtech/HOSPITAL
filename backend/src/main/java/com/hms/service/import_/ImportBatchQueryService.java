package com.hms.service.import_;

import com.hms.dto.import_.ImportBatchStatusResponse;
import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportStatus;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.import_.ImportBatchRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * Read side of a batch for the API. Every lookup is hospital + public id; a batch of another
 * tenant is simply not found. A RUNNING batch is passed through Phase 5's stale-run recovery
 * first, so a caller polling an abandoned import sees FAILED rather than RUNNING forever — the
 * same rule a new commit applies, not a second implementation of it.
 */
@Service
public class ImportBatchQueryService {

    private final ImportBatchRepository batches;
    private final ImportBatchStore batchStore;
    private final Clock clock;

    public ImportBatchQueryService(ImportBatchRepository batches, ImportBatchStore batchStore, Clock clock) {
        this.batches = batches;
        this.batchStore = batchStore;
        this.clock = clock;
    }

    public ImportBatchStatusResponse status(Long hospitalId, String publicId) {
        ImportBatch b = find(hospitalId, publicId);
        if (b.getStatus() == ImportStatus.RUNNING) {
            batchStore.resolveStale(hospitalId, LocalDateTime.now(clock));
            b = find(hospitalId, publicId);
        }
        return ImportBatchStatusResponse.from(b);
    }

    private ImportBatch find(Long hospitalId, String publicId) {
        return batches.findByPublicIdAndHospitalId(publicId, hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Import not found"));
    }
}
