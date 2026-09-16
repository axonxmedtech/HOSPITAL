package com.hms.repository.import_;

import com.hms.entity.import_.ImportRowResult;
import com.hms.entity.import_.ImportRowState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Results are addressed by batch id. Tenancy is enforced one level up: callers must obtain the
 * batch through {@link ImportBatchRepository#findByPublicIdAndHospitalId} before asking for its
 * rows, so a batch id is never accepted from a request directly.
 */
@Repository
public interface ImportRowResultRepository extends JpaRepository<ImportRowResult, Long> {

    List<ImportRowResult> findByBatchIdOrderByRowNumAsc(Long batchId);

    Page<ImportRowResult> findByBatchIdAndStateInOrderByRowNumAsc(
            Long batchId, Collection<ImportRowState> states, Pageable pageable);

    long countByBatchIdAndState(Long batchId, ImportRowState state);
}
