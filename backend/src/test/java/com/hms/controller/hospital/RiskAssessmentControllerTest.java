package com.hms.controller.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.RiskAssessmentCreateRequest;
import com.hms.dto.RiskAssessmentReviewRequest;
import com.hms.entity.PatientRiskAssessment;
import com.hms.repository.*;
import com.hms.security.JwtUtil;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.PdfService;
import com.hms.service.hospital.RiskAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskAssessmentController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RiskAssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private RiskAssessmentService riskService;
    @MockBean private SecurityContextHelper securityHelper;
    @MockBean private HospitalRepository hospitalRepository;
    @MockBean private PatientRepository patientRepository;
    @MockBean private IpdAdmissionRepository ipdAdmissionRepository;
    @MockBean private DoctorRepository doctorRepository;
    @MockBean private PdfService pdfService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;
    @MockBean private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    private PatientRiskAssessment mockRisk;

    @BeforeEach
    void setUp() {
        mockRisk = new PatientRiskAssessment();
        mockRisk.setId(50L);
        mockRisk.setHospitalId(1L);
        mockRisk.setPatientId(100L);
        mockRisk.setAdmissionId(200L);
        mockRisk.setOverallRisk("HIGH");
        mockRisk.setStatus("COMPLETED");
    }

    @Test
    public void evaluateRisk_validPayload_returnsScores() throws Exception {
        RiskAssessmentCreateRequest request = new RiskAssessmentCreateRequest();
        request.setPatientId(100L);
        request.setAdmissionId(200L);
        request.setInputsJson("{\"age\":78}");

        when(riskService.evaluateAndSaveRisk(100L, 200L, "{\"age\":78}", null))
                .thenReturn(mockRisk);

        mockMvc.perform(post("/hospital/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(50L))
                .andExpect(jsonPath("$.data.overallRisk").value("HIGH"));
    }

    @Test
    public void evaluateRisk_missingFields_returns400() throws Exception {
        RiskAssessmentCreateRequest request = new RiskAssessmentCreateRequest();
        // missing fields

        mockMvc.perform(post("/hospital/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void reviewRisk_validRemarks_signsOff() throws Exception {
        RiskAssessmentReviewRequest request = new RiskAssessmentReviewRequest();
        request.setReviewRemarks("Doctor signs off.");

        PatientRiskAssessment reviewedRisk = new PatientRiskAssessment();
        reviewedRisk.setId(50L);
        reviewedRisk.setStatus("REVIEWED");

        when(riskService.reviewRiskAssessment(50L, "Doctor signs off."))
                .thenReturn(reviewedRisk);

        mockMvc.perform(post("/hospital/risk-assessments/50/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWED"));
    }
}
