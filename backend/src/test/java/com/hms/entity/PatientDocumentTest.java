package com.hms.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class PatientDocumentTest {

    /**
     * Soft delete by default. A lab report that has been removed is still a clinical record that
     * existed, and hard-deleting one loses both the file and any way to explain the gap.
     */
    @Test
    void aNewDocumentIsActive() {
        PatientDocument doc = new PatientDocument();
        assertThat(doc.getIsActive()).isTrue();
    }

    @Test
    void theTypesCoverWhatArrivesFromOutside() {
        assertThat(DocumentType.values()).containsExactly(
                DocumentType.LAB_REPORT, DocumentType.XRAY, DocumentType.SCAN,
                DocumentType.PRESCRIPTION, DocumentType.DISCHARGE_SUMMARY, DocumentType.OTHER);
    }

    /**
     * {@code storedFilename} is "the only thing DocumentStorage ever opens" (see its Javadoc), so
     * patient isolation between documents rests entirely on this value never colliding. A unit
     * test cannot exercise the database constraint itself, but it can pin the JPA annotation that
     * generates it — so anyone changing the filename scheme later (Task 3 generates a UUID) is
     * forced to notice they are touching a uniqueness guarantee, not just a column definition.
     */
    @Test
    void storedFilenameIsDeclaredUniqueBecauseItIsTheOnlyPatientIsolationGuarantee() throws Exception {
        Field field = PatientDocument.class.getDeclaredField("storedFilename");
        Column column = field.getAnnotation(Column.class);

        assertThat(column.unique())
                .as("stored_filename must stay UNIQUE — a collision would let one patient's "
                        + "document be served back in place of another's")
                .isTrue();
    }
}
