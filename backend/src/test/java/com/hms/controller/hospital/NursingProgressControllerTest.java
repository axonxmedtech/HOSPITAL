package com.hms.controller.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.*;
import com.hms.entity.NursingProgressNote;
import com.hms.entity.NursingProcedure;
import com.hms.entity.ShiftHandover;
import com.hms.repository.*;
import com.hms.security.JwtUtil;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.PdfService;
import com.hms.service.hospital.NursingProgressService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NursingProgressController.class)
@AutoConfigureMockMvc(addFilters = false)
public class NursingProgressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private NursingProgressService progressService;
    @MockBean private SecurityContextHelper securityHelper;
    @MockBean private HospitalRepository hospitalRepository;
    @MockBean private PatientRepository patientRepository;
    @MockBean private IpdAdmissionRepository ipdAdmissionRepository;
    @MockBean private DoctorRepository doctorRepository;
    @MockBean private PdfService pdfService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;
    @MockBean private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    private NursingProgressNote mockNote;
    private NursingProcedure mockProcedure;
    private ShiftHandover mockHandover;

    @BeforeEach
    void setUp() {
        mockNote = new NursingProgressNote();
        mockNote.setId(10L);
        mockNote.setAdmissionId(200L);
        mockNote.setShift("MORNING");
        mockNote.setPainScore(3);
        mockNote.setStatus("DRAFT");

        mockProcedure = new NursingProcedure();
        mockProcedure.setId(20L);
        mockProcedure.setProcedureName("Nebulization");

        mockHandover = new ShiftHandover();
        mockHandover.setId(30L);
        mockHandover.setShift("EVENING");
    }

    @Test
    public void createNote_validPayload_returnsNote() throws Exception {
        NursingProgressNoteCreateRequest request = new NursingProgressNoteCreateRequest();
        request.setAdmissionId(200L);
        request.setShift("MORNING");
        request.setGeneralCondition("Stable");
        request.setPainScore(3);
        request.setPatientResponse("STABLE");

        when(progressService.createProgressNote(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockNote);

        mockMvc.perform(post("/hospital/nursing/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L));
    }

    @Test
    public void updateNote_validPayload_updatesSuccessfully() throws Exception {
        NursingProgressNoteUpdateRequest request = new NursingProgressNoteUpdateRequest();
        request.setRemarks("Doing better");

        when(progressService.updateProgressNote(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockNote);

        mockMvc.perform(put("/hospital/nursing/progress/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    public void recordProcedure_validPayload_returnsProcedure() throws Exception {
        NursingProcedureRequest request = new NursingProcedureRequest();
        request.setProgressNoteId(10L);
        request.setProcedureName("Nebulization");

        when(progressService.recordProcedure(10L, "Nebulization", null))
                .thenReturn(mockProcedure);

        mockMvc.perform(post("/hospital/nursing/procedure")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(20L));
    }

    @Test
    public void recordHandover_validPayload_returnsHandover() throws Exception {
        ShiftHandoverRequest request = new ShiftHandoverRequest();
        request.setAdmissionId(200L);
        request.setShift("EVENING");
        request.setIncomingNurseId(22L);

        when(progressService.recordHandover(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockHandover);

        mockMvc.perform(post("/hospital/nursing/handover")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(30L));
    }
}
