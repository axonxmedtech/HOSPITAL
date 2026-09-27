package com.hms.security;

import com.hms.controller.hospital.AppointmentController;
import com.hms.controller.hospital.PatientController;
import com.hms.entity.Appointment;
import com.hms.entity.Patient;
import com.hms.service.hospital.AppointmentService;
import com.hms.service.hospital.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The front-desk mutations consult {@link FrontDeskAccessGuard} <b>before</b> they touch a service.
 *
 * <p>Written because this is precisely what a bad merge silently deletes: both the role list on
 * {@code @PreAuthorize} and the guard call sit in regions that conflict whenever two branches edit
 * these controllers. An end-to-end 403 test proves the rule for one hospital; this proves the call
 * is wired at all, and that a refusal stops the write rather than merely reporting it afterwards.
 */
class FrontDeskGuardWiringTest {

    private final FrontDeskAccessGuard guard = mock(FrontDeskAccessGuard.class);

    private PatientController patientController(PatientService service) {
        PatientController c = new PatientController();
        ReflectionTestUtils.setField(c, "patientService", service);
        ReflectionTestUtils.setField(c, "frontDeskAccessGuard", guard);
        return c;
    }

    private AppointmentController appointmentController(AppointmentService service) {
        AppointmentController c = new AppointmentController();
        ReflectionTestUtils.setField(c, "appointmentService", service);
        ReflectionTestUtils.setField(c, "frontDeskAccessGuard", guard);
        return c;
    }

    @Test
    void registeringAPatientIsRefusedBeforeTheServiceIsReached() {
        PatientService service = mock(PatientService.class);
        doThrow(new AccessDeniedException("denied")).when(guard).require();

        assertThatThrownBy(() -> patientController(service).addPatient(new Patient(), false))
                .isInstanceOf(AccessDeniedException.class);
        verify(service, never()).addPatient(any(), anyBoolean());
    }

    @Test
    void editingAPatientIsRefusedBeforeTheServiceIsReached() {
        PatientService service = mock(PatientService.class);
        doThrow(new AccessDeniedException("denied")).when(guard).require();

        assertThatThrownBy(() -> patientController(service).updatePatient(1L, new Patient(), false))
                .isInstanceOf(AccessDeniedException.class);
        verify(service, never()).updatePatient(anyLong(), any(), anyBoolean());
    }

    @Test
    void bookingAnAppointmentIsRefusedBeforeTheServiceIsReached() {
        AppointmentService service = mock(AppointmentService.class);
        doThrow(new AccessDeniedException("denied")).when(guard).require();

        assertThatThrownBy(() -> appointmentController(service).createAppointment(new Appointment(), false))
                .isInstanceOf(AccessDeniedException.class);
        verify(service, never()).createAppointment(any(), anyBoolean());
    }

    @Test
    void aPermittedCallerStillReachesTheService() {
        PatientService patients = mock(PatientService.class);
        AppointmentService appointments = mock(AppointmentService.class);

        assertThatCode(() -> patientController(patients).addPatient(new Patient(), false))
                .doesNotThrowAnyException();
        assertThatCode(() -> appointmentController(appointments).createAppointment(new Appointment(), false))
                .doesNotThrowAnyException();

        verify(guard, times(2)).require();
        verify(patients, times(1)).addPatient(any(), anyBoolean());
        verify(appointments, times(1)).createAppointment(any(), anyBoolean());
    }
}
