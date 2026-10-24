package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hms.entity.import_.ImportBatch;
import com.hms.entity.import_.ImportStatus;
import com.hms.repository.import_.ImportBatchRepository;
import com.hms.repository.import_.ImportRowResultRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The store's answer after V24 refused a start: the real winner when visible, an honest "in progress" when not. */
class ImportBatchStoreTest {

    private final ImportBatchRepository batches = mock(ImportBatchRepository.class);
    private final ImportBatchStore store = new ImportBatchStore(batches, mock(ImportRowResultRepository.class));

    @Test
    void aVisibleWinnerIsNamedWithItsRealPublicIdStatusAndCommitTime() {
        ImportBatch winner = new ImportBatch();
        winner.setId(99L);
        winner.setPublicId("real-public-id");
        winner.setStatus(ImportStatus.COMPLETED);
        winner.setCommittedAt(LocalDateTime.of(2026, 9, 18, 9, 0));
        when(batches.findFirstByHospitalIdAndFileSha256AndStatusInOrderByCreatedAtDesc(anyLong(), anyString(), any())).thenReturn(Optional.of(winner));

        AlreadyImportedException e = store.alreadyImported(7L, "sha");

        assertThat(e.getCondition()).isEqualTo(AlreadyImportedException.Condition.ALREADY_IMPORTED);
        assertThat(e.getBatchPublicId()).contains("real-public-id");
        assertThat(e.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(e.getCommittedAt()).contains(LocalDateTime.of(2026, 9, 18, 9, 0));
        assertThat(e.getMessage()).doesNotContain("99"); // never the database id
    }

    @Test
    void anInvisibleWinnerIsInProgressWithNoIdAtAll() {
        when(batches.findFirstByHospitalIdAndFileSha256AndStatusInOrderByCreatedAtDesc(anyLong(), anyString(), any())).thenReturn(Optional.empty());

        AlreadyImportedException e = store.alreadyImported(7L, "sha");

        assertThat(e.getCondition()).isEqualTo(AlreadyImportedException.Condition.IMPORT_ALREADY_IN_PROGRESS);
        assertThat(e.isDetailsAvailable()).isFalse();
        assertThat(e.getBatchPublicId()).isEmpty();
        assertThat(e.getCommittedAt()).isEmpty();
        assertThat(e.getStatus()).isEqualTo(ImportStatus.RUNNING);
        assertThat(e.getMessage()).doesNotContain("unknown").contains("Retry shortly");
    }
}
