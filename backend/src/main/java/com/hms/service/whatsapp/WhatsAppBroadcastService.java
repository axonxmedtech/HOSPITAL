package com.hms.service.whatsapp;

import com.hms.entity.Patient;
import com.hms.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WhatsAppBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBroadcastService.class);

    private final WhatsAppService whatsAppService;
    private final PatientRepository patientRepository;

    public WhatsAppBroadcastService(WhatsAppService whatsAppService,
                                    PatientRepository patientRepository) {
        this.whatsAppService = whatsAppService;
        this.patientRepository = patientRepository;
    }

    /**
     * Sends a broadcast to all active patients of the hospital.
     * Runs @Async — HTTP response returns immediately, progress tracked via message log.
     */
    @Async("whatsAppTaskExecutor")
    public void broadcastToAllPatients(Long hospitalId, String messageText, String imageUrl) {
        List<Patient> patients = patientRepository.findByHospitalIdAndIsActiveTrue(hospitalId);
        log.info("Broadcasting WhatsApp message to {} patients for hospital {}", patients.size(), hospitalId);
        for (Patient patient : patients) {
            if (patient.getPhone() == null || patient.getPhone().isBlank()) continue;
            try {
                whatsAppService.sendBroadcast(hospitalId, patient.getId(),
                        patient.getPhone(), messageText, imageUrl);
            } catch (Exception e) {
                log.warn("Broadcast failed for patient {}: {}", patient.getId(), e.getMessage());
            }
        }
    }

    /** Returns count of active patients — used to show "Will be sent to X patients" in UI. */
    public long countActivePatients(Long hospitalId) {
        return patientRepository.countByHospitalIdAndIsActiveTrue(hospitalId);
    }
}
