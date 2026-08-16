package com.hms.entity;

import org.junit.jupiter.api.Test;

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
}
