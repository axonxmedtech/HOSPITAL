package com.hms.service.hospital;

import com.hms.entity.Appointment;
import com.hms.entity.Doctor;
import com.hms.entity.Patient;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceNewPatientTest {

    @Mock PatientRepository patientRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock SecurityContextHelper securityHelper;
    // The insert itself belongs to PatientRegistrar, which is where the registration number is
    // assigned. These two tests are about the date of birth this service puts on the entity
    // before handing it over, so the insert is stubbed to hand the same instance back.
    @Mock PatientRegistrar patientRegistrar;
    @Mock com.hms.service.hospital.PatientDuplicateFinder patientDuplicateFinder;

    @InjectMocks AppointmentService service;

    @Captor ArgumentCaptor<Patient> patientCaptor;

    private Appointment newWalkInAppointment(String phone, LocalDate patientDob) {
        Appointment appointment = new Appointment();
        appointment.setPatientName("Walk-in Patient");
        appointment.setPatientPhone(phone);
        appointment.setPatientDateOfBirth(patientDob);
        appointment.setPatientGender("MALE");
        appointment.setDoctorId(1L);
        appointment.setAppointmentDate(LocalDate.now().plusDays(1));
        appointment.setAppointmentTime(LocalTime.of(10, 0));
        return appointment;
    }

    private void mockCommonCollaborators(String phone) {
        // Module validity is normalized at the plan boundary; this service only enforces tenant
        // ownership for the patient and doctor referenced by the booking.
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        // Nobody at this hospital is on this number, so the booking creates a patient rather
        // than stopping to ask which of several people sharing it is being booked.
        when(patientDuplicateFinder.findActiveByPhone(1L, phone, null))
                .thenReturn(Collections.emptyList());
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setName("Dr. Test");
        when(doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(doctor));
        // Lenient: a refused booking never reaches save, and a test may replace the day's schedule.
        org.mockito.Mockito.lenient().when(appointmentRepository.lockActiveForDoctorOnDate(any(), any()))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.lenient().when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAppointment_newPatientWithDob_setsDateOfBirthOnCreatedPatient() {
        mockCommonCollaborators("9876543210");
        when(patientRegistrar.persistNewPatient(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(9L);
            return p;
        });

        Appointment appointment = newWalkInAppointment("9876543210", LocalDate.now().minusYears(25));

        service.createAppointment(appointment);

        verify(patientRegistrar).persistNewPatient(patientCaptor.capture());
        assertThat(patientCaptor.getValue().getDateOfBirth()).isEqualTo(LocalDate.now().minusYears(25));
    }

    @Test
    void createAppointment_newPatientNoDob_defaultsToToday() {
        mockCommonCollaborators("9876543211");
        when(patientRegistrar.persistNewPatient(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        Appointment appointment = newWalkInAppointment("9876543211", null);

        service.createAppointment(appointment);

        verify(patientRegistrar).persistNewPatient(patientCaptor.capture());
        assertThat(patientCaptor.getValue().getDateOfBirth()).isEqualTo(LocalDate.now());
    }

    /**
     * Two desks booking the same slot at the same moment: the doctor's row is locked before the
     * day is read, and the day is read with a locking read, so the second booking waits for the
     * first and then sees it. The order matters -- reading first would let both pass.
     */
    @Test
    void createAppointment_locksTheDoctorBeforeReadingTheDay() {
        mockCommonCollaborators("9876543212");
        when(patientRegistrar.persistNewPatient(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        Appointment appointment = newWalkInAppointment("9876543212", null);

        service.createAppointment(appointment);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(doctorRepository, appointmentRepository);
        order.verify(doctorRepository).lockById(1L);
        order.verify(appointmentRepository).lockActiveForDoctorOnDate(1L, appointment.getAppointmentDate());
        order.verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void createAppointment_refusesASlotAnotherDeskAlreadyTook() {
        mockCommonCollaborators("9876543213");
        lenientStubPatient();
        Appointment taken = new Appointment();
        taken.setId(77L);
        taken.setStatus("SCHEDULED");
        taken.setAppointmentTime(LocalTime.of(10, 0));
        when(appointmentRepository.lockActiveForDoctorOnDate(any(), any())).thenReturn(java.util.List.of(taken));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.createAppointment(newWalkInAppointment("9876543213", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already booked");
        verify(appointmentRepository, org.mockito.Mockito.never()).save(any(Appointment.class));
    }

    /** The booking form asks for one day; the doctor's whole history must not be loaded. */
    @Test
    void doctorDayView_readsOnlyThatDay() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        when(doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(doctor));
        LocalDate day = LocalDate.now().plusDays(2);
        when(appointmentRepository.findByDoctorIdAndAppointmentDateAndIsActiveTrue(1L, day))
                .thenReturn(Collections.emptyList());

        assertThat(service.getAppointmentsByDoctor(1L, "date", day)).isEmpty();

        verify(appointmentRepository).findByDoctorIdAndAppointmentDateAndIsActiveTrue(1L, day);
        verify(appointmentRepository, org.mockito.Mockito.never())
                .findByDoctorIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(any(), any());
    }

    private void lenientStubPatient() {
        org.mockito.Mockito.lenient().when(patientRegistrar.persistNewPatient(any(Patient.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }
}
