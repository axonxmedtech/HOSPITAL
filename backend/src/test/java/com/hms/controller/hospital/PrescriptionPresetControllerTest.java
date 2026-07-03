package com.hms.controller.hospital;

import com.hms.dto.PrescriptionPresetItemDTO;
import com.hms.entity.PrescriptionPreset;
import com.hms.security.JwtUtil;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.PrescriptionPresetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(PrescriptionPresetController.class)
@Import(PrescriptionPresetControllerTest.MethodSecurityTestConfig.class)
class PrescriptionPresetControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean private PrescriptionPresetService presetService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;

    @Test
    @WithMockUser(roles = "HOSPITAL_ADMIN")
    void listPresets_returnsOkForHospitalAdmin() throws Exception {
        when(presetService.listPresets()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/hospital/prescription-presets").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void listPresets_returnsOkForDoctor() throws Exception {
        when(presetService.listPresets()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/hospital/prescription-presets").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void listPresets_returnsForbiddenForReceptionist() throws Exception {
        mockMvc.perform(get("/hospital/prescription-presets").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void createPreset_returnsOkWhenServiceSucceeds() throws Exception {
        PrescriptionPreset saved = new PrescriptionPreset();
        saved.setId(1L);
        saved.setName("Fever Protocol");
        saved.setDisplayOrder(0);
        when(presetService.createPreset(eq("Fever Protocol"), anyList())).thenReturn(saved);
        when(presetService.getItems(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/hospital/prescription-presets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fever Protocol\",\"items\":[{\"medicineName\":\"Paracetamol\",\"dosage\":\"500mg\"}]}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fever Protocol"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void createPreset_returnsBadRequestWhenServiceThrows() throws Exception {
        when(presetService.createPreset(anyString(), anyList()))
                .thenThrow(new IllegalArgumentException("Preset name is required"));

        mockMvc.perform(post("/hospital/prescription-presets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"items\":[]}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void createPreset_returnsForbiddenForReceptionist() throws Exception {
        mockMvc.perform(post("/hospital/prescription-presets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fever Protocol\",\"items\":[]}")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HOSPITAL_ADMIN")
    void updatePreset_returnsOkWhenFound() throws Exception {
        PrescriptionPreset updated = new PrescriptionPreset();
        updated.setId(5L);
        updated.setName("Updated Name");
        when(presetService.updatePreset(eq(5L), eq("Updated Name"), any(), any())).thenReturn(updated);
        when(presetService.getItems(5L)).thenReturn(Collections.emptyList());

        mockMvc.perform(put("/hospital/prescription-presets/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @WithMockUser(roles = "HOSPITAL_ADMIN")
    void updatePreset_returnsBadRequestWhenNotFound() throws Exception {
        when(presetService.updatePreset(eq(999L), any(), any(), any()))
                .thenThrow(new RuntimeException("Preset not found"));

        mockMvc.perform(put("/hospital/prescription-presets/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void deletePreset_returnsOkWhenSuccessful() throws Exception {
        mockMvc.perform(delete("/hospital/prescription-presets/7").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void deletePreset_returnsForbiddenForReceptionist() throws Exception {
        mockMvc.perform(delete("/hospital/prescription-presets/7").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
