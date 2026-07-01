package com.hms.controller.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.ApiResponse;
import com.hms.dto.ConsentCreateRequest;
import com.hms.dto.ConsentSignRequest;
import com.hms.entity.PatientConsent;
import com.hms.repository.*;
import com.hms.security.JwtUtil;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.PdfService;
import com.hms.service.hospital.ConsentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsentController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ConsentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private ConsentService consentService;
    @MockBean private SecurityContextHelper securityHelper;
    @MockBean private HospitalRepository hospitalRepository;
    @MockBean private PatientRepository patientRepository;
    @MockBean private IpdAdmissionRepository ipdAdmissionRepository;
    @MockBean private DoctorRepository doctorRepository;
    @MockBean private PdfService pdfService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;
    @MockBean private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    private PatientConsent mockConsent;

    @BeforeEach
    void setUp() {
        mockConsent = new PatientConsent();
        mockConsent.setId(10L);
        mockConsent.setHospitalId(1L);
        mockConsent.setPatientId(100L);
        mockConsent.setAdmissionId(200L);
        mockConsent.setConsentType("GENERAL");
        mockConsent.setStatus("DRAFT");
    }

    @Test
    public void createConsent_withValidRequest_returnsDraft() throws Exception {
        ConsentCreateRequest request = new ConsentCreateRequest();
        request.setPatientId(100L);
        request.setAdmissionId(200L);
        request.setEncounterType("IPD");
        request.setConsentType("GENERAL");

        when(consentService.createConsentDraft(any(ConsentCreateRequest.class)))
                .thenReturn(mockConsent);

        mockMvc.perform(post("/hospital/consents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    public void createConsent_withMissingFields_returns400() throws Exception {
        ConsentCreateRequest request = new ConsentCreateRequest();
        // missing patientId, encounterType, consentType

        mockMvc.perform(post("/hospital/consents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getConsent_returnsDetails() throws Exception {
        when(consentService.getConsent(10L)).thenReturn(mockConsent);

        mockMvc.perform(get("/hospital/consents/10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10L));
    }

    @Test
    public void signConsent_validRequest_updatesConsent() throws Exception {
        ConsentSignRequest request = new ConsentSignRequest();
        request.setPatientSigned(true);
        request.setSignatureType("STYLUS");

        PatientConsent signedConsent = new PatientConsent();
        signedConsent.setId(10L);
        signedConsent.setStatus("SIGNED");
        signedConsent.setPatientSigned(true);

        when(consentService.signConsent(eq(10L), any(ConsentSignRequest.class)))
                .thenReturn(signedConsent);

        mockMvc.perform(post("/hospital/consents/10/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNED"))
                .andExpect(jsonPath("$.data.patientSigned").value(true));
    }
}
