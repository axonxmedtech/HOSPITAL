package com.hms.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ImportBatchTest {

    @Test
    void newBatchStartsAsDraftWithZeroCounts() {
        ImportBatch b = new ImportBatch();
        assertThat(b.getStatus()).isEqualTo(ImportStatus.DRAFT);
        assertThat(b.getCreatedCount()).isZero();
        assertThat(b.getFailedCount()).isZero();
    }

    @Test
    void undoIsOnlyAllowedForCompletedBatches() {
        ImportBatch b = new ImportBatch();
        b.setStatus(ImportStatus.DRAFT);
        assertThat(b.isUndoable()).isFalse();
        b.setStatus(ImportStatus.COMPLETED);
        assertThat(b.isUndoable()).isTrue();
        b.setStatus(ImportStatus.UNDONE);
        assertThat(b.isUndoable()).isFalse();
    }
}
