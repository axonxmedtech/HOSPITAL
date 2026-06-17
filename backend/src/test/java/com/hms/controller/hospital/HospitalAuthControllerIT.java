package com.hms.controller.hospital;

import com.hms.security.HospitalWebSocketHandler;
import com.hms.service.hospital.HospitalAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.hms.repository.UserRepository;
import com.hms.security.JwtUtil;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// addFilters=false disables the security filter chain so we test controller and validation logic only
@WebMvcTest(HospitalAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HospitalAuthControllerIT {

    @Autowired
    MockMvc mvc;

    @MockBean
    HospitalAuthService authService;

    @MockBean
    HospitalWebSocketHandler webSocketHandler;

    @MockBean
    UserRepository userRepository;

    @MockBean
    JwtUtil jwtUtil;

    @Test
    void updateOperationsSettings_withNoPrincipal_returns401() throws Exception {
        // Controller guards against null principal (no active session) before calling service
        mvc.perform(put("/hospital/settings/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"receptionMode":"SOLO","billingHandler":"DOCTOR","inClinic":true}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withEmptyBody_returns400() throws Exception {
        // @Valid on LoginRequest rejects missing email/password before service is called
        mvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
