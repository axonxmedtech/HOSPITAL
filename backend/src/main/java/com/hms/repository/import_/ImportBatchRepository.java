package com.hms.repository.import_;

import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every application-facing lookup is hospital-scoped. A batch is addressed by its public id
 * together with the caller's hospital, so another tenant's id resolves to "not found" rather than
 * to someone else's import history.
 */
@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    Optional<ImportBatch> findByPublicIdAndHospitalId(String publicId, Long hospitalId);

    List<ImportBatch> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);

    boolean existsByHospitalIdAndStatus(Long hospitalId, ImportStatus status);

    /** Exact-file retry guard: the newest batch for these bytes in one of the "already imported" statuses. */
    Optional<ImportBatch> findFirstByHospitalIdAndFileSha256AndStatusInOrderByCreatedAtDesc(
            Long hospitalId, String fileSha256, Collection<ImportStatus> statuses);

    /** Abandoned-run detector: RUNNING batches whose heartbeat stopped before {@code cutoff}. */
    List<ImportBatch> findByHospitalIdAndStatusAndHeartbeatAtBefore(
            Long hospitalId, ImportStatus status, LocalDateTime cutoff);
}
