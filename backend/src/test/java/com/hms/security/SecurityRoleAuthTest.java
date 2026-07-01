package com.hms.security;

import com.hms.config.SecurityConfig;
import com.hms.controller.platform.PlatformHospitalController;
import com.hms.controller.hospital.PatientController;
import com.hms.repository.HospitalRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.JwtUtil;
import com.hms.service.PdfService;
import com.hms.service.hospital.PatientService;
import com.hms.service.platform.PlatformHospitalService;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PlatformHospitalController.class, PatientController.class})
@Import({SecurityConfig.class, com.hms.filter.RateLimitFilter.class, JwtAuthenticationFilter.class})
class SecurityRoleAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;
    @MockBean private SecurityContextHelper securityHelper;
    @MockBean private PlatformHospitalService platformHospitalService;
    @MockBean private PatientService patientService;
    @MockBean private HospitalRepository hospitalRepository;
    @MockBean private PdfService pdfService;

    // --- /platform/** Authorization Tests ---

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void platformEndpoints_allowSuperAdmin() throws Exception {
        org.springframework.data.domain.Page<com.hms.entity.Hospital> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList());
        when(platformHospitalService.getAllHospitals(any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/platform/hospitals"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void platformEndpoints_denyDoctor() throws Exception {
        mockMvc.perform(get("/platform/hospitals"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MRD_OFFICER")
    void platformEndpoints_denyMrdOfficer() throws Exception {
        mockMvc.perform(get("/platform/hospitals"))
                .andExpect(status().isForbidden());
    }

    // --- /hospital/** Authorization Tests ---

    @Test
    @WithMockUser(roles = "DOCTOR")
    void hospitalEndpoints_allowDoctor() throws Exception {
        org.springframework.data.domain.Page<com.hms.entity.Patient> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList());
        when(patientService.getAllPatients(any(), any(), any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/hospital/patients"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MRD_OFFICER")
    void hospitalEndpoints_allowMrdOfficer() throws Exception {
        org.springframework.data.domain.Page<com.hms.entity.Patient> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList());
        when(patientService.getAllPatients(any(), any(), any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/hospital/patients"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "QUALITY_OFFICER")
    void hospitalEndpoints_allowQualityOfficer() throws Exception {
        org.springframework.data.domain.Page<com.hms.entity.Patient> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList());
        when(patientService.getAllPatients(any(), any(), any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/hospital/patients"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void hospitalEndpoints_denySuperAdmin() throws Exception {
        mockMvc.perform(get("/hospital/patients"))
                .andExpect(status().isForbidden());
    }
}
