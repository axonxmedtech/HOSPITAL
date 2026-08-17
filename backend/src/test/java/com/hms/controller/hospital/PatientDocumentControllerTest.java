package com.hms.controller.hospital;

import com.hms.dto.PatientDocumentResponse;
import com.hms.entity.DocumentType;
import com.hms.service.documents.PatientDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientDocumentControllerTest {

    @Mock PatientDocumentService service;
    @InjectMocks PatientDocumentController controller;

    private PatientDocumentResponse doc() {
        return new PatientDocumentResponse("doc-1", "Blood test", DocumentType.LAB_REPORT,
                LocalDate.of(2026, 8, 12), "report.pdf", "application/pdf", 1234L,
                "reena@h.com", LocalDateTime.now());
    }

    @Test
    void listsAPatientsDocuments() {
        when(service.list("pat-1")).thenReturn(List.of(doc()));

        var body = controller.list("pat-1").getBody();

        assertThat(body).isNotNull();
        assertThat(body.data()).hasSize(1);
        assertThat(body.data().get(0).title()).isEqualTo("Blood test");
    }

    /**
     * A download must arrive as an attachment. Serving a PDF or an SVG inline lets a crafted file
     * execute in the application's own origin, which would put it next to the session token.
     */
    @Test
    void downloadIsSentAsAnAttachmentNeverInline() {
        when(service.download("doc-1")).thenReturn(new PatientDocumentService.DownloadHandle(
                "report.pdf", "application/pdf", new ByteArrayInputStream("x".getBytes())));

        var response = controller.download("doc-1");

        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .startsWith("attachment;");
    }

    /** A filename containing quotes or newlines must not be able to forge response headers. */
    @Test
    void aHostileFilenameCannotBreakOutOfTheHeader() {
        when(service.download("doc-1")).thenReturn(new PatientDocumentService.DownloadHandle(
                "evil\"\r\nX-Injected: yes.pdf", "application/pdf",
                new ByteArrayInputStream("x".getBytes())));

        var response = controller.download("doc-1");
        String disposition = response.getHeaders().getFirst("Content-Disposition");

        assertThat(disposition).doesNotContain("\r").doesNotContain("\n");
        assertThat(response.getHeaders().getFirst("X-Injected")).isNull();
    }
}
