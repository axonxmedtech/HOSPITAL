package com.hms.controller.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.FluidIntakeRequest;
import com.hms.dto.FluidOutputRequest;
import com.hms.entity.DailyFluidBalance;
import com.hms.entity.FluidIntake;
import com.hms.entity.FluidOutput;
import com.hms.repository.*;
import com.hms.security.JwtUtil;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.PdfService;
import com.hms.service.hospital.FluidService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FluidController.class)
@AutoConfigureMockMvc(addFilters = false)
public class FluidControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private FluidService fluidService;
    @MockBean private SecurityContextHelper securityHelper;
    @MockBean private HospitalRepository hospitalRepository;
    @MockBean private PatientRepository patientRepository;
    @MockBean private IpdAdmissionRepository ipdAdmissionRepository;
    @MockBean private DoctorRepository doctorRepository;
    @MockBean private PdfService pdfService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;
    @MockBean private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    private FluidIntake mockIntake;
    private FluidOutput mockOutput;

    @BeforeEach
    void setUp() {
        mockIntake = new FluidIntake();
        mockIntake.setId(10L);
        mockIntake.setAdmissionId(200L);
        mockIntake.setType("ORAL");
        mockIntake.setVolumeMl(300);

        mockOutput = new FluidOutput();
        mockOutput.setId(15L);
        mockOutput.setAdmissionId(200L);
        mockOutput.setType("URINE");
        mockOutput.setVolumeMl(400);
    }

    @Test
    public void recordIntake_validPayload_returnsIntake() throws Exception {
        FluidIntakeRequest request = new FluidIntakeRequest();
        request.setAdmissionId(200L);
        request.setType("ORAL");
        request.setVolumeMl(300);
        request.setDescription("Water");

        when(fluidService.recordIntake(200L, "ORAL", 300, "Water")).thenReturn(mockIntake);

        mockMvc.perform(post("/hospital/fluid/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L));
    }

    @Test
    public void recordOutput_validPayload_returnsOutput() throws Exception {
        FluidOutputRequest request = new FluidOutputRequest();
        request.setAdmissionId(200L);
        request.setType("URINE");
        request.setVolumeMl(400);
        request.setColor("Yellow");
        request.setDescription("Natural");

        when(fluidService.recordOutput(200L, "URINE", 400, "Yellow", "Natural")).thenReturn(mockOutput);

        mockMvc.perform(post("/hospital/fluid/output")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(15L));
    }

    @Test
    public void getFluidBalance_returnsBalance() throws Exception {
        DailyFluidBalance balance = new DailyFluidBalance();
        balance.setAdmissionId(200L);
        balance.setTotalIntake(800);
        balance.setTotalOutput(500);
        balance.setNetBalance(300);

        when(fluidService.getFluidBalance(200L)).thenReturn(balance);

        mockMvc.perform(get("/hospital/fluid/balance/200")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netBalance").value(300));
    }
}
