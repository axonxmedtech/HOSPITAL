package com.hms.service;

import com.hms.dto.OtBookingRequest;
import com.hms.dto.OtChecklistRequest;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.service.hospital.OtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtServiceTest {

    @Mock
    private OtBookingRepository bookingRepository;

    @Mock
    private OtChecklistRepository checklistRepository;

    @Mock
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Mock
    private SecurityContextHelper securityHelper;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private HospitalWebSocketHandler webSocketHandler;

    @InjectMocks
    private OtService otService;

    @Test
    void scheduleBooking_savesBookingAndChecklist() {
        Long hospitalId = 1L;
        Long admissionId = 10L;

        // Mock security auditing context
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("doctor@hospital.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        // Mock room list to avoid room conflicts
        when(bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId))
                .thenReturn(new ArrayList<>());

        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(invocation -> {
            OtBooking booking = invocation.getArgument(0);
            booking.setId(100L); // set mock ID
            return booking;
        });

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", LocalDateTime.now().plusDays(2), 5L, "Dr. Ana", "OT 1", "Scheduled surgery"
        );

        OtBooking booking = otService.scheduleBooking(admissionId, req);

        assertThat(booking.getProcedureName()).isEqualTo("Cholecystectomy");
        assertThat(booking.getOtRoomNumber()).isEqualTo("OT 1");
        assertThat(booking.getStatus()).isEqualTo("SCHEDULED");

        verify(bookingRepository, times(1)).save(any(OtBooking.class));
        verify(checklistRepository, times(1)).save(any(OtChecklist.class));
    }

    @Test
    void scheduleBooking_throwsOnRoomConflict() {
        Long hospitalId = 1L;
        Long admissionId = 10L;
        LocalDateTime testTime = LocalDateTime.now().plusDays(2);

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        // Mock an existing conflicting booking scheduled at same time in room 'OT 1'
        OtBooking conflictBooking = new OtBooking();
        conflictBooking.setOtRoomNumber("OT 1");
        conflictBooking.setScheduledDateTime(testTime);
        conflictBooking.setStatus("SCHEDULED");
        when(bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId))
                .thenReturn(java.util.List.of(conflictBooking));

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", testTime, 5L, "Dr. Ana", "OT 1", "Conflicting surgery"
        );

        assertThatThrownBy(() -> otService.scheduleBooking(admissionId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is already booked within 1 hour of this time");
    }

    @Test
    void signChecklist_advancesStatusesCorrectly() {
        Long hospitalId = 1L;
        Long bookingId = 100L;

        // Mock security auditing context
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("nurse@hospital.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");

        OtBooking booking = new OtBooking();
        booking.setId(bookingId);
        booking.setHospitalId(hospitalId);
        booking.setStatus("SCHEDULED");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        OtChecklist checklist = new OtChecklist();
        checklist.setOtBookingId(bookingId);
        checklist.setHospitalId(hospitalId);
        checklist.setSignInCompleted(true); // pre-requisite for timeout
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId))
                .thenReturn(Optional.of(checklist));

        when(checklistRepository.save(any(OtChecklist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Sign Time Out
        OtChecklistRequest req = new OtChecklistRequest("TIME_OUT", "All prep check done");
        OtChecklist updated = otService.signChecklist(bookingId, req);

        assertThat(updated.isTimeOutCompleted()).isTrue();
        assertThat(updated.getTimeOutBy()).isEqualTo("nurse@hospital.com");
        assertThat(booking.getStatus()).isEqualTo("IN_PROGRESS");
    }
}
