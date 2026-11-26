package com.hms.service;

import com.hms.entity.AuditLog;
import com.hms.repository.AuditLogRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The admin audit trail records who (email + role) and from where (client IP) for each change. */
@ExtendWith(MockitoExtension.class)
class AuditLogClientIpTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock SecurityContextHelper securityHelper;
    @InjectMocks AuditLogService service;

    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

    private AuditLog logged() {
        ArgumentCaptor<AuditLog> row = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(row.capture());
        return row.getValue();
    }

    @Test
    void recordsTheRoleAndTheFirstForwardedHop_behindNginx() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(securityHelper.getCurrentUserRole()).thenReturn("RECEPTIONIST");

        service.logAction("BILL_DISCOUNTED", "Discount 50", "desk@h.test", 1L, "BILLING", "7", "Senior citizen");

        AuditLog row = logged();
        assertThat(row.getClientIp()).isEqualTo("203.0.113.9");
        assertThat(row.getPerformedByRole()).isEqualTo("RECEPTIONIST");
    }

    @Test
    void outsideARequest_aScheduledJob_recordsNoAddress() {
        service.logAction("IPD_DAY_CHARGE", "Day 2 bed", "system", 1L, "BILLING", "7", null);

        assertThat(logged().getClientIp()).isNull();
    }
}
