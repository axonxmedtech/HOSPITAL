package com.hms.controller.platform;

import com.hms.entity.MedicineList;
import com.hms.repository.MedicineListRepository;
import com.hms.security.JwtUtil;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.MedicineService;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvcTest for PlatformMedicineController.
 * BUG-026: adds controller-layer coverage for the /platform/medicines endpoints.
 *
 * Verifies:
 *  - SUPER_ADMIN can list, create, update and delete medicines.
 *  - Non-SUPER_ADMIN roles receive 403 Forbidden.
 *  - Service errors map to 400 Bad Request responses.
 */
@WebMvcTest(PlatformMedicineController.class)
class PlatformMedicineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private MedicineService medicineService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;
    @MockBean private SecurityContextHelper securityHelper;
    @MockBean private HospitalWebSocketHandler webSocketHandler;
    @MockBean private MedicineListRepository medicineListRepository;

    // ─── GET /platform/medicines ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void getMedicines_returnsOkForSuperAdmin() throws Exception {
        var page = new PageImpl<MedicineList>(Collections.emptyList());
        when(medicineService.getPlatformMedicines(
                isNull(),
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 10)))
                .thenReturn(page);

        mockMvc.perform(get("/platform/medicines")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void getMedicines_returnsForbiddenForNonSuperAdmin() throws Exception {
        mockMvc.perform(get("/platform/medicines")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ─── POST /platform/medicines ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createMedicine_returnsOkWhenServiceSucceeds() throws Exception {
        MedicineList saved = new MedicineList();
        saved.setId(1L);
        saved.setName("Ibuprofen 400mg");
        saved.setType("Tablet");

        when(medicineService.addCatalogMedicine(any(MedicineList.class))).thenReturn(saved);

        mockMvc.perform(post("/platform/medicines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ibuprofen 400mg\",\"type\":\"Tablet\"}")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ibuprofen 400mg"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createMedicine_returnsBadRequestWhenServiceThrows() throws Exception {
        when(medicineService.addCatalogMedicine(any(MedicineList.class)))
                .thenThrow(new IllegalArgumentException("Medicine already exists in catalog"));

        mockMvc.perform(post("/platform/medicines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Paracetamol\"}")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
    }

    // ─── PUT /platform/medicines/{id} ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateMedicine_returnsOkWhenFound() throws Exception {
        MedicineList updated = new MedicineList();
        updated.setId(5L);
        updated.setName("Cetirizine 10mg");
        updated.setType("Tablet");

        when(medicineService.updateCatalogMedicine(eq(5L), any(MedicineList.class))).thenReturn(updated);

        mockMvc.perform(put("/platform/medicines/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cetirizine 10mg\",\"type\":\"Tablet\"}")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cetirizine 10mg"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateMedicine_returnsBadRequestWhenNotFound() throws Exception {
        when(medicineService.updateCatalogMedicine(eq(999L), any(MedicineList.class)))
                .thenThrow(new RuntimeException("Catalog medicine not found"));

        mockMvc.perform(put("/platform/medicines/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /platform/medicines/{id} ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deleteMedicine_returnsOkWhenSuccessful() throws Exception {
        // medicineService.deleteCatalogMedicine is void – no stub needed for happy path

        mockMvc.perform(delete("/platform/medicines/7")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deleteMedicine_returnsBadRequestWhenNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("Catalog medicine not found"))
                .when(medicineService).deleteCatalogMedicine(999L);

        mockMvc.perform(delete("/platform/medicines/999")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
    }
}
