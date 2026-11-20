package com.hms.controller.hospital;

import com.hms.security.JwtUtil;
import com.hms.service.hospital.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The notification bell sits on both nurse dashboards. NURSE_INCHARGE was missing from every
 * endpoint, so an incharge's bell returned 403 on each page load.
 */
@WebMvcTest(NotificationController.class)
@Import(NotificationControllerRoleTest.MethodSecurityTestConfig.class)
class NotificationControllerRoleTest {

    @MockBean private com.hms.repository.UserRepository r1bUserRepository;
    @MockBean private com.hms.repository.HospitalRepository r1bHospitalRepository;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mockMvc;

    @MockBean private NotificationService notificationService;
    @MockBean private JwtUtil jwtUtil;

    @Test
    @WithMockUser(roles = "NURSE_INCHARGE")
    void nurseInchargeCanReadTheirNotifications() throws Exception {
        when(notificationService.getMyNotifications()).thenReturn(List.of());
        when(notificationService.getUnreadCount()).thenReturn(0L);
        mockMvc.perform(get("/hospital/notifications")).andExpect(status().isOk());
        mockMvc.perform(get("/hospital/notifications/unread-count")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void staffNurseStillCanReadTheirNotifications() throws Exception {
        when(notificationService.getUnreadCount()).thenReturn(0L);
        mockMvc.perform(get("/hospital/notifications/unread-count")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void otherRolesAreStillRefused() throws Exception {
        mockMvc.perform(get("/hospital/notifications")).andExpect(status().isForbidden());
        verify(notificationService, never()).getMyNotifications();
    }
}
