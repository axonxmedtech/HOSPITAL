package com.hms.service.import_;

import com.hms.entity.import_.ImportRowResult;
import com.hms.repository.import_.ImportRowResultRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists row results one chunk per transaction (REQUIRES_NEW, so a chunk never joins a
 * patient-row transaction), then flushes and clears the persistence context so a 100,000-row
 * import never holds more than one chunk of result entities in memory.
 */
@Component
public class ImportResultStore {

    /** Rows per result transaction. */
    public static final int CHUNK_SIZE = 500;

    private final ImportRowResultRepository results;
    private final EntityManager em;

    public ImportResultStore(ImportRowResultRepository results, EntityManager em) {
        this.results = results;
        this.em = em;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveChunk(List<ImportRowResult> chunk) {
        results.saveAll(chunk);
        em.flush();
        em.clear();
    }
}
