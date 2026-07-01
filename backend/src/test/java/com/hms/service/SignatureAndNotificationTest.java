package com.hms.service;

import com.hms.entity.DocumentVersion;
import com.hms.entity.SignatureSlot;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.DocumentVersionRepository;
import com.hms.repository.SignatureSlotRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.NotificationService;
import com.hms.service.hospital.SignatureAndDocumentService;
import com.hms.service.whatsapp.WhatsAppService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignatureAndNotificationTest {

    @Mock private SignatureSlotRepository signatureSlotRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private HospitalWebSocketHandler webSocketHandler;
    @Mock private WhatsAppService whatsAppService;

    @InjectMocks private SignatureAndDocumentService signatureService;
    @InjectMocks private NotificationService notificationService;

    // --- SignatureSlot Tests ---

    @Test
    void saveSignatureSlot_setsHospitalIdAndSaves() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(4L);

        SignatureSlot inputSlot = new SignatureSlot();
        inputSlot.setSignerRole("PATIENT");
        inputSlot.setSignerName("John Doe");
        inputSlot.setDocumentType("CONSENT");
        inputSlot.setDocumentId("DOC101");

        SignatureSlot savedSlot = new SignatureSlot();
        savedSlot.setId(12L);
        savedSlot.setHospitalId(4L);
        savedSlot.setSignerRole("PATIENT");
        savedSlot.setSignerName("John Doe");
        savedSlot.setDocumentType("CONSENT");
        savedSlot.setDocumentId("DOC101");

        when(signatureSlotRepository.save(any(SignatureSlot.class))).thenReturn(savedSlot);

        SignatureSlot result = signatureService.saveSignatureSlot(inputSlot);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(12L);
        assertThat(result.getHospitalId()).isEqualTo(4L);
        verify(signatureSlotRepository).save(inputSlot);
    }

    @Test
    void getSignaturesForDocument_filtersByTenant() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(4L);

        SignatureSlot slot = new SignatureSlot();
        slot.setHospitalId(4L);
        slot.setDocumentType("CONSENT");
        slot.setDocumentId("DOC101");

        when(signatureSlotRepository.findByHospitalIdAndDocumentTypeAndDocumentIdOrderBySignedAtAsc(4L, "CONSENT", "DOC101"))
                .thenReturn(Collections.singletonList(slot));

        List<SignatureSlot> result = signatureService.getSignaturesForDocument("CONSENT", "DOC101");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHospitalId()).isEqualTo(4L);
    }

    // --- DocumentVersion Tests ---

    @Test
    void incrementDocumentVersion_withNewDocument_createsVersion1() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(4L);
        when(documentVersionRepository.findFirstByHospitalIdAndDocumentTypeAndDocumentIdOrderByVersionDesc(4L, "CONSENT", "DOC101"))
                .thenReturn(Optional.empty());

        DocumentVersion expected = new DocumentVersion();
        expected.setVersion(1);
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenReturn(expected);

        DocumentVersion result = signatureService.incrementDocumentVersion(4L, "CONSENT", "DOC101", "http://cloud.com/doc", 9L);

        assertThat(result.getVersion()).isEqualTo(1);
    }

    @Test
    void incrementDocumentVersion_withExistingDocument_incrementsVersion() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(4L);

        DocumentVersion last = new DocumentVersion();
        last.setVersion(3);
        when(documentVersionRepository.findFirstByHospitalIdAndDocumentTypeAndDocumentIdOrderByVersionDesc(4L, "CONSENT", "DOC101"))
                .thenReturn(Optional.of(last));

        DocumentVersion expected = new DocumentVersion();
        expected.setVersion(4);
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenReturn(expected);

        DocumentVersion result = signatureService.incrementDocumentVersion(4L, "CONSENT", "DOC101", "http://cloud.com/doc", 9L);

        assertThat(result.getVersion()).isEqualTo(4);
    }

    @Test
    void incrementDocumentVersion_rejectsCrossTenant() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(4L);

        assertThatThrownBy(() -> signatureService.incrementDocumentVersion(5L, "CONSENT", "DOC101", "http://cloud.com/doc", 9L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Tenant mismatch");
    }

    // --- NotificationService Facade Tests ---

    @Test
    void sendWebSocketRefresh_broadcastsPayload() {
        notificationService.sendWebSocketRefresh(4L, "PATIENT_CREATED", 101L);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(webSocketHandler).broadcast(eq(4L), payloadCaptor.capture());

        String json = payloadCaptor.getValue();
        assertThat(json).contains("\"type\":\"REFRESH_DATA\"");
        assertThat(json).contains("\"event\":\"PATIENT_CREATED\"");
        assertThat(json).contains("\"entityId\":101");
    }

    @Test
    void sendWhatsAppNotification_routesToWhatsAppService() {
        List<String> params = List.of("John", "General Hospital");
        notificationService.sendWhatsAppNotification(4L, 101L, "919999999999", "welcome_template", "MSG_WELCOME", params, "http://cloud.com/welcome.pdf");

        verify(whatsAppService).sendWhatsApp(4L, 101L, "919999999999", "welcome_template", "MSG_WELCOME", params, "http://cloud.com/welcome.pdf");
    }
}
