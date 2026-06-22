package com.hms.scheduler;

import com.hms.config.WhatsAppTemplateConstants;
import com.hms.entity.Appointment;
import com.hms.entity.Hospital;
import com.hms.entity.Patient;
import com.hms.entity.WhatsAppConfig;
import com.hms.repository.AppointmentRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.WhatsAppConfigRepository;
import com.hms.service.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class AppointmentReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderScheduler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final HospitalRepository hospitalRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final WhatsAppConfigRepository configRepository;
    private final WhatsAppService whatsAppService;

    public AppointmentReminderScheduler(HospitalRepository hospitalRepository,
                                        AppointmentRepository appointmentRepository,
                                        PatientRepository patientRepository,
                                        WhatsAppConfigRepository configRepository,
                                        WhatsAppService whatsAppService) {
        this.hospitalRepository = hospitalRepository;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.configRepository = configRepository;
        this.whatsAppService = whatsAppService;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void sendDayBeforeReminders() {
        log.info("AppointmentReminderScheduler: running day-before reminder job");
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<Hospital> hospitals = hospitalRepository.findByAnyModule(
                List.of(WhatsAppTemplateConstants.MODULE_WA_APPOINTMENTS,
                        WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM));

        for (Hospital hospital : hospitals) {
            if (hospital.getModules().contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) {
                Optional<WhatsAppConfig> cfg = configRepository.findByHospitalId(hospital.getId());
                if (cfg.isEmpty() || !cfg.get().isSendAppointments()) continue;
            }

            List<Appointment> appointments = appointmentRepository
                    .findByHospitalIdAndAppointmentDateAndStatusAndIsActiveTrue(
                            hospital.getId(), tomorrow, "SCHEDULED");

            for (Appointment appt : appointments) {
                try {
                    Patient patient = patientRepository.findById(appt.getPatientId()).orElse(null);
                    if (patient == null || patient.getPhone() == null || patient.getPhone().isBlank()) continue;

                    String date = appt.getAppointmentDate().format(DATE_FMT);
                    String time = appt.getAppointmentTime() != null
                            ? appt.getAppointmentTime().format(TIME_FMT) : "—";

                    whatsAppService.sendAppointmentReminder(
                            hospital.getId(), patient.getId(),
                            patient.getPhone(), patient.getName(),
                            hospital.getName(), date, time);
                } catch (Exception e) {
                    log.warn("Reminder failed for appointment {}: {}", appt.getId(), e.getMessage());
                }
            }
        }
    }
}
