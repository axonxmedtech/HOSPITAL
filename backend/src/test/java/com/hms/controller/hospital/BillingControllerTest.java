package com.hms.controller.hospital;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalSetting;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.security.JwtUtil;
import com.hms.service.PdfService;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.BillingService;
import com.hms.service.hospital.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(BillingController.class)
public class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private BillingService billingService;

    @MockBean
    private BillingPaymentRepository billingPaymentRepository;

    @MockBean
    private HospitalSettingRepository hospitalSettingRepository;

    @MockBean
    private PdfService pdfService;

    @MockBean
    private HospitalRepository hospitalRepository;

    @MockBean
    private PatientService patientService;

    @MockBean
    private BillingRepository billingRepository;

    @MockBean
    private BillingItemRepository billingItemRepository;

    @MockBean
    private InsuranceClaimRepository insuranceClaimRepository;

    @MockBean
    private IpdAdmissionRepository ipdAdmissionRepository;

    @MockBean
    private WardRepository wardRepository;

    @MockBean
    private SecurityContextHelper securityHelper;

    @MockBean
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @MockBean
    private com.hms.repository.BillingMedicineRepository billingMedicineRepository;

    private HospitalSetting settings;

    @BeforeEach
    void setUp() {
        settings = new HospitalSetting();
        Hospital hospital = new Hospital();
        hospital.setId(1L);
        settings.setHospital(hospital);
        
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(hospitalSettingRepository.findByHospital_Id(1L)).thenReturn(Optional.of(settings));

        org.springframework.data.domain.Page<com.hms.entity.Billing> emptyPage = new org.springframework.data.domain.PageImpl<>(Collections.emptyList());
        when(billingService.getAllBills(null, null, org.springframework.data.domain.PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())))
                .thenReturn(emptyPage);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    public void testDoctorAccess_WhenBillingHandlerIsDoctor_Allowed() throws Exception {
        settings.setBillingHandler("DOCTOR");
        when(securityHelper.getCurrentUserRole()).thenReturn("ROLE_DOCTOR");

        mockMvc.perform(get("/hospital/billing")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    public void testDoctorAccess_WhenBillingHandlerIsReceptionist_Forbidden() throws Exception {
        settings.setBillingHandler("RECEPTIONIST");
        settings.setReceptionMode("HAS_RECEPTIONIST");
        when(securityHelper.getCurrentUserRole()).thenReturn("ROLE_DOCTOR");

        mockMvc.perform(get("/hospital/billing")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    public void testDoctorAccess_WhenBillingHandlerIsReceptionistButReceptionModeIsSolo_Allowed() throws Exception {
        settings.setBillingHandler("RECEPTIONIST");
        settings.setReceptionMode("SOLO");
        when(securityHelper.getCurrentUserRole()).thenReturn("ROLE_DOCTOR");

        mockMvc.perform(get("/hospital/billing")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    public void testReceptionistAccess_WhenBillingHandlerIsReceptionist_Allowed() throws Exception {
        settings.setBillingHandler("RECEPTIONIST");
        settings.setReceptionMode("HAS_RECEPTIONIST");
        when(securityHelper.getCurrentUserRole()).thenReturn("ROLE_RECEPTIONIST");

        mockMvc.perform(get("/hospital/billing")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    public void testReceptionistAccess_WhenBillingHandlerIsDoctor_Forbidden() throws Exception {
        settings.setBillingHandler("DOCTOR");
        settings.setReceptionMode("HAS_RECEPTIONIST");
        when(securityHelper.getCurrentUserRole()).thenReturn("ROLE_RECEPTIONIST");

        mockMvc.perform(get("/hospital/billing")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    public void testReceptionistAccess_WhenReceptionModeIsSolo_Forbidden() throws Exception {
        settings.setBillingHandler("RECEPTIONIST");
        settings.setReceptionMode("SOLO");
        when(securityHelper.getCurrentUserRole()).thenReturn("ROLE_RECEPTIONIST");

        mockMvc.perform(get("/hospital/billing")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HOSPITAL_ADMIN")
    public void testAdminAccess_AlwaysAllowed() throws Exception {
        settings.setBillingHandler("DOCTOR");
        when(securityHelper.getCurrentUserRole()).thenReturn("ROLE_HOSPITAL_ADMIN");

        mockMvc.perform(get("/hospital/billing")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    public void testPayBilling_CrossTenantBill_ReturnsNotFound() throws Exception {
        settings.setBillingHandler("RECEPTIONIST");
        settings.setReceptionMode("HAS_RECEPTIONIST");
        when(securityHelper.getCurrentUserRole()).thenReturn("RECEPTIONIST");

        com.hms.entity.Billing otherHospitalBill = new com.hms.entity.Billing();
        otherHospitalBill.setId(99L);
        otherHospitalBill.setHospitalId(2L);
        when(billingRepository.findById(99L)).thenReturn(Optional.of(otherHospitalBill));

        mockMvc.perform(post("/hospital/billing/99/pay")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100,\"mode\":\"CASH\",\"reference\":\"ref1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    public void testPayBilling_ActiveInsuranceClaim_Blocked() throws Exception {
        settings.setBillingHandler("RECEPTIONIST");
        settings.setReceptionMode("HAS_RECEPTIONIST");
        when(securityHelper.getCurrentUserRole()).thenReturn("RECEPTIONIST");

        com.hms.entity.Billing bill = new com.hms.entity.Billing();
        bill.setId(5L);
        bill.setHospitalId(1L);
        bill.setAmount(new java.math.BigDecimal("1000"));
        when(billingRepository.findById(5L)).thenReturn(Optional.of(bill));

        com.hms.entity.InsuranceClaim claim = new com.hms.entity.InsuranceClaim();
        claim.setStatus("APPROVED");
        claim.setPayer("Acme Insurance");
        when(insuranceClaimRepository.findByHospitalIdAndBillingId(1L, 5L)).thenReturn(java.util.List.of(claim));

        mockMvc.perform(post("/hospital/billing/5/pay")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":500,\"mode\":\"CASH\",\"reference\":\"ref2\"}"))
                .andExpect(status().isBadRequest());
    }
}
