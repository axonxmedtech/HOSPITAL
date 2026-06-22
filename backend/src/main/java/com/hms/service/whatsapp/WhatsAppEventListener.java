package com.hms.service.whatsapp;

import com.hms.config.WhatsAppTemplateConstants;
import com.hms.entity.*;
import com.hms.event.*;
import com.hms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class WhatsAppEventListener {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppEventListener.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final WhatsAppService whatsAppService;
    private final HospitalRepository hospitalRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final WhatsAppConfigRepository configRepository;

    public WhatsAppEventListener(WhatsAppService whatsAppService,
                                 HospitalRepository hospitalRepository,
                                 PatientRepository patientRepository,
                                 AppointmentRepository appointmentRepository,
                                 WhatsAppConfigRepository configRepository) {
        this.whatsAppService = whatsAppService;
        this.hospitalRepository = hospitalRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.configRepository = configRepository;
    }

    @Async("whatsAppTaskExecutor")
    @EventListener
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        try {
            Hospital hospital = hospitalRepository.findById(event.getHospitalId()).orElse(null);
            if (hospital == null) return;
            List<String> modules = hospital.getModules();
            if (!whatsAppService.isEnabled(event.getHospitalId(), modules,
                    WhatsAppTemplateConstants.MODULE_WA_APPOINTMENTS)) return;

            if (modules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) {
                Optional<WhatsAppConfig> cfg = configRepository.findByHospitalId(event.getHospitalId());
                if (cfg.isPresent() && !cfg.get().isSendAppointments()) return;
            }

            Patient patient = patientRepository.findById(event.getPatientId()).orElse(null);
            if (patient == null || patient.getPhone() == null || patient.getPhone().isBlank()) return;

            Appointment appt = appointmentRepository.findById(event.getAppointmentId()).orElse(null);
            if (appt == null) return;

            String date = appt.getAppointmentDate() != null
                    ? appt.getAppointmentDate().format(DATE_FMT) : "—";
            String time = appt.getAppointmentTime() != null
                    ? appt.getAppointmentTime().format(TIME_FMT) : "—";

            whatsAppService.sendAppointmentConfirmation(
                    event.getHospitalId(), patient.getId(),
                    patient.getPhone(), patient.getName(),
                    hospital.getName(), date, time);
        } catch (Exception e) {
            log.warn("WhatsApp appointment confirmation failed for hospitalId={}, appointmentId={}",
                    event.getHospitalId(), event.getAppointmentId(), e);
        }
    }

    @Async("whatsAppTaskExecutor")
    @EventListener
    public void onConsultationCompleted(ConsultationCompletedEvent event) {
        try {
            Hospital hospital = hospitalRepository.findById(event.getHospitalId()).orElse(null);
            if (hospital == null) return;
            List<String> modules = hospital.getModules();

            Patient patient = patientRepository.findById(event.getPatientId()).orElse(null);
            if (patient == null || patient.getPhone() == null || patient.getPhone().isBlank()) return;

            if (whatsAppService.isEnabled(event.getHospitalId(), modules,
                    WhatsAppTemplateConstants.MODULE_WA_BILLING)
                    && isCustomSendEnabled(event.getHospitalId(), modules, "billing")) {
                whatsAppService.sendDocument(event.getHospitalId(), patient.getId(),
                        patient.getPhone(), patient.getName(),
                        hospital.getName(), "Billing Receipt", null,
                        WhatsAppTemplateConstants.MSG_TYPE_BILLING);
            }
        } catch (Exception e) {
            log.warn("WhatsApp consultation-completed handler failed for hospitalId={}, appointmentId={}",
                    event.getHospitalId(), event.getAppointmentId(), e);
        }
    }

    @Async("whatsAppTaskExecutor")
    @EventListener
    public void onMedicineDispensed(MedicineDispensedEvent event) {
        try {
            if (event.getPatientId() == null) return;
            Hospital hospital = hospitalRepository.findById(event.getHospitalId()).orElse(null);
            if (hospital == null) return;
            List<String> modules = hospital.getModules();
            if (!whatsAppService.isEnabled(event.getHospitalId(), modules,
                    WhatsAppTemplateConstants.MODULE_WA_MEDICINE_LIST)) return;
            if (modules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) {
                Optional<WhatsAppConfig> cfg = configRepository.findByHospitalId(event.getHospitalId());
                if (cfg.isPresent() && !cfg.get().isSendMedicineList()) return;
            }
            Patient patient = patientRepository.findById(event.getPatientId()).orElse(null);
            if (patient == null || patient.getPhone() == null || patient.getPhone().isBlank()) return;

            whatsAppService.sendDocument(event.getHospitalId(), patient.getId(),
                    patient.getPhone(), patient.getName(),
                    hospital.getName(), "Medicine List", null,
                    WhatsAppTemplateConstants.MSG_TYPE_MEDICINE_LIST);
        } catch (Exception e) {
            log.warn("WhatsApp medicine-dispensed handler failed for hospitalId={}, purchaseId={}",
                    event.getHospitalId(), event.getPurchaseId(), e);
        }
    }

    private boolean isCustomSendEnabled(Long hospitalId, List<String> modules, String type) {
        if (!modules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) return true;
        Optional<WhatsAppConfig> cfg = configRepository.findByHospitalId(hospitalId);
        if (cfg.isEmpty()) return true;
        return switch (type) {
            case "billing"      -> cfg.get().isSendBilling();
            case "casePaper"    -> cfg.get().isSendCasePapers();
            case "prescription" -> cfg.get().isSendPrescription();
            case "medicineList" -> cfg.get().isSendMedicineList();
            default             -> true;
        };
    }
}
