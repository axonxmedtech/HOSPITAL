package com.hms.controller.hospital;

import com.hms.dto.WhatsAppBroadcastRequest;
import com.hms.dto.WhatsAppConfigDTO;
import com.hms.dto.WhatsAppLogDTO;
import com.hms.entity.WhatsAppConfig;
import com.hms.entity.WhatsAppMessageLog;
import com.hms.repository.WhatsAppConfigRepository;
import com.hms.repository.WhatsAppMessageLogRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.whatsapp.WhatsAppBroadcastService;
import com.hms.service.whatsapp.WhatsAppService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/hospital/whatsapp")
public class WhatsAppController {

    private final WhatsAppConfigRepository configRepository;
    private final WhatsAppMessageLogRepository logRepository;
    private final WhatsAppService whatsAppService;
    private final WhatsAppBroadcastService broadcastService;
    private final SecurityContextHelper securityHelper;

    public WhatsAppController(WhatsAppConfigRepository configRepository,
                              WhatsAppMessageLogRepository logRepository,
                              WhatsAppService whatsAppService,
                              WhatsAppBroadcastService broadcastService,
                              SecurityContextHelper securityHelper) {
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.whatsAppService = whatsAppService;
        this.broadcastService = broadcastService;
        this.securityHelper = securityHelper;
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody WhatsAppBroadcastRequest req) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        long patientCount = broadcastService.countActivePatients(hospitalId);
        broadcastService.broadcastToAllPatients(hospitalId, req.getMessageText(), req.getImageUrl());
        return ResponseEntity.ok(Map.of("message", "Broadcast queued", "patientCount", patientCount));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Page<WhatsAppLogDTO>> getLogs(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        PageRequest pr = PageRequest.of(page, size);
        Page<WhatsAppMessageLog> raw;
        if (type != null && status != null) {
            raw = logRepository.findByHospitalIdAndMessageTypeAndStatusOrderByCreatedAtDesc(
                    hospitalId, type, status, pr);
        } else if (type != null) {
            raw = logRepository.findByHospitalIdAndMessageTypeOrderByCreatedAtDesc(hospitalId, type, pr);
        } else if (status != null) {
            raw = logRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status, pr);
        } else {
            raw = logRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId, pr);
        }
        return ResponseEntity.ok(raw.map(this::toLogDTO));
    }

    @GetMapping("/logs/failed-count")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Long>> getFailedCount() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        long count = logRepository.countByHospitalIdAndStatus(
                hospitalId, WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/config")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<WhatsAppConfigDTO> getConfig() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        Optional<WhatsAppConfig> opt = configRepository.findByHospitalId(hospitalId);
        if (opt.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(toConfigDTO(opt.get(), true));
    }

    @PostMapping("/config")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<WhatsAppConfigDTO> saveConfig(@RequestBody WhatsAppConfigDTO dto) {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        WhatsAppConfig cfg = configRepository.findByHospitalId(hospitalId)
                .orElseGet(WhatsAppConfig::new);
        cfg.setHospitalId(hospitalId);
        cfg.setAccessToken(whatsAppService.encrypt(dto.getAccessToken()));
        cfg.setPhoneNumberId(dto.getPhoneNumberId());
        cfg.setWabaId(dto.getWabaId());
        cfg.setIsActive(dto.getActive() != null ? dto.getActive() : Boolean.TRUE);
        cfg.setSendAppointments(dto.getSendAppointments() != null ? dto.getSendAppointments() : true);
        cfg.setSendBilling(dto.getSendBilling() != null ? dto.getSendBilling() : true);
        cfg.setSendCasePapers(dto.getSendCasePapers() != null ? dto.getSendCasePapers() : true);
        cfg.setSendPrescription(dto.getSendPrescription() != null ? dto.getSendPrescription() : true);
        cfg.setSendMedicineList(dto.getSendMedicineList() != null ? dto.getSendMedicineList() : true);
        configRepository.save(cfg);
        return ResponseEntity.ok(toConfigDTO(cfg, true));
    }

    @DeleteMapping("/config")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Void> deleteConfig() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        configRepository.findByHospitalId(hospitalId).ifPresent(configRepository::delete);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/config/test")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Object>> testConfig(@RequestBody WhatsAppConfigDTO dto) {
        try {
            String token = whatsAppService.decrypt(whatsAppService.encrypt(dto.getAccessToken()));
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(token);
            org.springframework.http.ResponseEntity<String> resp = rt.exchange(
                    "https://graph.facebook.com/v19.0/" + dto.getPhoneNumberId(),
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Credentials valid"));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("success", false, "message", "Unexpected response"));
    }

    private WhatsAppLogDTO toLogDTO(WhatsAppMessageLog log) {
        WhatsAppLogDTO dto = new WhatsAppLogDTO();
        dto.setId(log.getId());
        dto.setPatientId(log.getPatientId());
        dto.setPatientPhone(log.getPatientPhone());
        dto.setMessageType(log.getMessageType());
        dto.setStatus(log.getStatus());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setRetryCount(log.getRetryCount());
        dto.setSentAt(log.getSentAt());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    private WhatsAppConfigDTO toConfigDTO(WhatsAppConfig cfg, boolean maskToken) {
        WhatsAppConfigDTO dto = new WhatsAppConfigDTO();
        String token = cfg.getAccessToken();
        if (maskToken && token != null && token.length() > 4) {
            dto.setAccessToken("••••••••" + token.substring(token.length() - 4));
        } else {
            dto.setAccessToken(token);
        }
        dto.setPhoneNumberId(cfg.getPhoneNumberId());
        dto.setWabaId(cfg.getWabaId());
        dto.setActive(cfg.getIsActive());
        dto.setSendAppointments(cfg.isSendAppointments());
        dto.setSendBilling(cfg.isSendBilling());
        dto.setSendCasePapers(cfg.isSendCasePapers());
        dto.setSendPrescription(cfg.isSendPrescription());
        dto.setSendMedicineList(cfg.isSendMedicineList());
        return dto;
    }
}
