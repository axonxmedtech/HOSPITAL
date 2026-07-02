package com.hms.controller.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.ClinicalAssessmentFinalizeRequest;
import com.hms.dto.ClinicalAssessmentUpdateRequest;
import com.hms.entity.ClinicalAssessment;
import com.hms.repository.*;
import com.hms.security.JwtUtil;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.PdfService;
import com.hms.service.hospital.ClinicalAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClinicalAssessmentController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ClinicalAssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private ClinicalAssessmentService clinicalAssessmentService;
    @MockBean private SecurityContextHelper securityHelper;
    @MockBean private HospitalRepository hospitalRepository;
    @MockBean private PatientRepository patientRepository;
    @MockBean private IpdAdmissionRepository ipdAdmissionRepository;
    @MockBean private DoctorRepository doctorRepository;
    @MockBean private PdfService pdfService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;
    @MockBean private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    private ClinicalAssessment mockAssessment;

    @BeforeEach
    void setUp() {
        mockAssessment = new ClinicalAssessment();
        mockAssessment.setId(90L);
        mockAssessment.setHospitalId(1L);
        mockAssessment.setPatientId(100L);
        mockAssessment.setAdmissionId(200L);
        mockAssessment.setDoctorId(5L);
        mockAssessment.setStatus("DRAFT");
        mockAssessment.setVersion(1);
    }

    @Test
    public void createDraft_returnsSuccessfully() throws Exception {
        when(clinicalAssessmentService.createDraft(200L)).thenReturn(mockAssessment);

        mockMvc.perform(post("/hospital/clinical-assessments")
                .param("admissionId", "200")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(90L));
    }

    @Test
    public void updateDraft_validPayload_updatesSuccessfully() throws Exception {
        ClinicalAssessmentUpdateRequest request = new ClinicalAssessmentUpdateRequest();
        request.setChiefComplaint("Chest pain");
        request.setHistoryPresentIllness("Started 2 hours ago");

        ClinicalAssessment updated = new ClinicalAssessment();
        updated.setId(90L);
        updated.setStatus("DRAFT");
        updated.setChiefComplaint("Chest pain");

        when(clinicalAssessmentService.updateDraft(eq(90L), any(ClinicalAssessmentUpdateRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/hospital/clinical-assessments/90")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chiefComplaint").value("Chest pain"));
    }

    @Test
    public void finalizeAssessment_validRequest_locksRecord() throws Exception {
        ClinicalAssessmentFinalizeRequest request = new ClinicalAssessmentFinalizeRequest();
        request.setMedicalHistory(new ArrayList<>());
        request.setDoctorOrders(new ArrayList<>());

        ClinicalAssessment finalized = new ClinicalAssessment();
        finalized.setId(90L);
        finalized.setStatus("FINALIZED");

        when(clinicalAssessmentService.finalizeAssessment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(finalized);

        mockMvc.perform(post("/hospital/clinical-assessments/90/finalize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINALIZED"));
    }

    @Test
    public void amendAssessment_createsVersion2() throws Exception {
        ClinicalAssessmentUpdateRequest request = new ClinicalAssessmentUpdateRequest();
        request.setChiefComplaint("Amended chest pain");

        ClinicalAssessment amendment = new ClinicalAssessment();
        amendment.setId(91L);
        amendment.setStatus("DRAFT");
        amendment.setVersion(2);

        when(clinicalAssessmentService.amendAssessment(90L, "Amended chest pain", null, null, null))
                .thenReturn(amendment);

        mockMvc.perform(post("/hospital/clinical-assessments/90/amend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));
    }
}
