package com.hms.service.whatsapp;

import com.hms.config.WhatsAppTemplateConstants;
import com.hms.entity.WhatsAppConfig;
import com.hms.entity.WhatsAppMessageLog;
import com.hms.repository.WhatsAppConfigRepository;
import com.hms.repository.WhatsAppMessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final String BASE_URL = "https://graph.facebook.com/%s/%s/messages";
    private static final int MAX_RETRIES = 2;

    @Value("${whatsapp.access-token:}")
    private String platformAccessToken;

    @Value("${whatsapp.phone-number-id:}")
    private String platformPhoneNumberId;

    @Value("${whatsapp.api-version:v19.0}")
    private String apiVersion;

    /**
     * AES encryption key for storing access tokens.
     * Uses Arrays.copyOf(..., 16) to produce a 16-byte (128-bit) key regardless of env var length.
     * Keys longer than 16 chars are truncated to 16 bytes; shorter keys are zero-padded.
     */
    @Value("${whatsapp.encryption-key:}")
    private String encryptionKey;

    private final WhatsAppConfigRepository configRepository;
    private final WhatsAppMessageLogRepository logRepository;
    private final RestTemplate restTemplate;

    public WhatsAppService(WhatsAppConfigRepository configRepository,
                           WhatsAppMessageLogRepository logRepository) {
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Returns true if the hospital has WhatsApp enabled for the given sub-module.
     * WHATSAPP_CUSTOM always returns true (hospital controls per-type via whatsapp_config toggles).
     * WHATSAPP_PLATFORM returns true only if the hospital also has the specific sub-module string.
     */
    public boolean isEnabled(Long hospitalId, List<String> hospitalModules, String subModule) {
        if (hospitalModules == null) return false;
        if (hospitalModules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_CUSTOM)) return true;
        return hospitalModules.contains(WhatsAppTemplateConstants.MODULE_WHATSAPP_PLATFORM)
                && hospitalModules.contains(subModule);
    }

    /**
     * Send appointment confirmation template.
     * {{1}} patientName · {{2}} hospitalName · {{3}} date · {{4}} time
     */
    public void sendAppointmentConfirmation(Long hospitalId, Long patientId,
                                            String phone, String patientName,
                                            String hospitalName, String date, String time) {
        List<String> params = List.of(patientName, hospitalName, date, time);
        doSend(hospitalId, patientId, phone,
                WhatsAppTemplateConstants.APPOINTMENT_CONFIRMATION,
                WhatsAppTemplateConstants.MSG_TYPE_APPOINTMENT_CONFIRMATION,
                params, null);
    }

    /**
     * Send appointment reminder template.
     * {{1}} patientName · {{2}} hospitalName · {{3}} date · {{4}} time
     */
    public void sendAppointmentReminder(Long hospitalId, Long patientId,
                                        String phone, String patientName,
                                        String hospitalName, String date, String time) {
        List<String> params = List.of(patientName, hospitalName, date, time);
        doSend(hospitalId, patientId, phone,
                WhatsAppTemplateConstants.APPOINTMENT_REMINDER,
                WhatsAppTemplateConstants.MSG_TYPE_APPOINTMENT_REMINDER,
                params, null);
    }

    /**
     * Send document-ready template with an optional Cloudinary public URL.
     * {{1}} patientName · {{2}} docType · {{3}} hospitalName
     */
    public void sendDocument(Long hospitalId, Long patientId,
                             String phone, String patientName,
                             String hospitalName, String docType,
                             String documentUrl, String msgType) {
        List<String> params = List.of(patientName, docType, hospitalName);
        doSend(hospitalId, patientId, phone,
                WhatsAppTemplateConstants.DOCUMENT_READY, msgType, params, documentUrl);
    }

    /**
     * Send broadcast template. {{1}} is the full message body.
     */
    public void sendBroadcast(Long hospitalId, Long patientId,
                              String phone, String messageText, String imageUrl) {
        List<String> params = List.of(messageText);
        doSend(hospitalId, patientId, phone,
                WhatsAppTemplateConstants.BROADCAST,
                WhatsAppTemplateConstants.MSG_TYPE_BROADCAST, params, imageUrl);
    }

    /**
     * Test WhatsApp credentials by calling the Meta Graph API for the given phoneNumberId.
     * Reuses the shared RestTemplate rather than creating a new one per request.
     */
    public Map<String, Object> testCredentials(String phoneNumberId, String plainAccessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(plainAccessToken);
            ResponseEntity<String> resp = restTemplate.exchange(
                    "https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return Map.of("success", true, "message", "Credentials valid");
            }
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
        return Map.of("success", false, "message", "Unexpected response");
    }

    /**
     * Retry a previously failed log entry. Called by WhatsAppRetryScheduler.
     */
    public void retry(WhatsAppMessageLog entry) {
        String[] creds = resolveCredentials(entry.getHospitalId());
        if (creds == null) {
            entry.setStatus(WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED);
            entry.setErrorMessage("No WhatsApp credentials configured");
            logRepository.save(entry);
            return;
        }
        List<String> params = null;
        if (entry.getTemplateParamsJson() != null && !entry.getTemplateParamsJson().isBlank()) {
            params = Arrays.asList(entry.getTemplateParamsJson().split("\\|\\|", -1));
        }
        boolean success = callMetaApi(creds[0], creds[1], entry.getPatientPhone(),
                entry.getTemplateName(), params, entry.getMediaUrl());
        if (success) {
            entry.setStatus(WhatsAppMessageLog.STATUS_SENT);
            entry.setSentAt(LocalDateTime.now());
            entry.setErrorMessage(null);
        } else {
            entry.setRetryCount(entry.getRetryCount() + 1);
            if (entry.getRetryCount() >= MAX_RETRIES) {
                entry.setStatus(WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED);
                entry.setErrorMessage("Max retries exceeded");
            } else {
                entry.setStatus(WhatsAppMessageLog.STATUS_RETRYING);
                entry.setNextRetryAt(LocalDateTime.now().plusMinutes(15));
            }
        }
        logRepository.save(entry);
    }

    // ---- Internal ----

    private void doSend(Long hospitalId, Long patientId, String rawPhone,
                        String templateName, String msgType,
                        List<String> templateParams, String mediaUrl) {
        String phone = normalizePhone(rawPhone);
        String[] creds = resolveCredentials(hospitalId);

        WhatsAppMessageLog entry = new WhatsAppMessageLog();
        entry.setHospitalId(hospitalId);
        entry.setPatientId(patientId);
        entry.setPatientPhone(phone);
        entry.setMessageType(msgType);
        entry.setTemplateName(templateName);
        if (templateParams != null && !templateParams.isEmpty()) {
            entry.setTemplateParamsJson(String.join("||", templateParams));
        }
        entry.setMediaUrl(mediaUrl);

        if (creds == null) {
            entry.setStatus(WhatsAppMessageLog.STATUS_FAILED);
            entry.setErrorMessage("No WhatsApp credentials configured for hospital " + hospitalId);
            entry.setNextRetryAt(LocalDateTime.now().plusMinutes(15));
            logRepository.save(entry);
            return;
        }

        boolean ok = callMetaApi(creds[0], creds[1], phone, templateName, templateParams, mediaUrl);
        if (ok) {
            entry.setStatus(WhatsAppMessageLog.STATUS_SENT);
            entry.setSentAt(LocalDateTime.now());
        } else {
            entry.setStatus(WhatsAppMessageLog.STATUS_FAILED);
            entry.setNextRetryAt(LocalDateTime.now().plusMinutes(15));
        }
        logRepository.save(entry);
    }

    private boolean callMetaApi(String accessToken, String phoneNumberId,
                                String toPhone, String templateName,
                                List<String> params, String mediaUrl) {
        try {
            String url = String.format(BASE_URL, apiVersion, phoneNumberId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", toPhone);
            body.put("type", "template");

            if (templateName != null) {
                Map<String, Object> template = new LinkedHashMap<>();
                template.put("name", templateName);
                Map<String, Object> language = new LinkedHashMap<>();
                language.put("code", "en_US");
                template.put("language", language);

                List<Map<String, Object>> components = new ArrayList<>();

                if (params != null && !params.isEmpty()) {
                    Map<String, Object> bodyComp = new LinkedHashMap<>();
                    bodyComp.put("type", "body");
                    List<Map<String, Object>> bodyParams = new ArrayList<>();
                    for (String p : params) {
                        bodyParams.add(Map.of("type", "text", "text", p));
                    }
                    bodyComp.put("parameters", bodyParams);
                    components.add(bodyComp);
                }

                if (mediaUrl != null && !mediaUrl.isBlank()) {
                    Map<String, Object> headerComp = new LinkedHashMap<>();
                    headerComp.put("type", "header");
                    List<Map<String, Object>> headerParams = new ArrayList<>();
                    if (WhatsAppTemplateConstants.DOCUMENT_READY.equals(templateName)) {
                        headerParams.add(Map.of("type", "document",
                                "document", Map.of("link", mediaUrl)));
                    } else {
                        headerParams.add(Map.of("type", "image",
                                "image", Map.of("link", mediaUrl)));
                    }
                    headerComp.put("parameters", headerParams);
                    components.add(headerComp);
                }

                if (!components.isEmpty()) {
                    template.put("components", components);
                }
                body.put("template", template);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("WhatsApp API call failed for phone {}: {}", toPhone, e.getMessage());
            return false;
        }
    }

    /**
     * Returns [accessToken, phoneNumberId] for the hospital, or null if not configured.
     * Prefers hospital-specific custom config over platform env vars.
     */
    public String[] resolveCredentials(Long hospitalId) {
        Optional<WhatsAppConfig> custom = configRepository.findByHospitalId(hospitalId);
        if (custom.isPresent() && Boolean.TRUE.equals(custom.get().getIsActive())) {
            WhatsAppConfig cfg = custom.get();
            return new String[]{decrypt(cfg.getAccessToken()), cfg.getPhoneNumberId()};
        }
        if (!platformAccessToken.isBlank() && !platformPhoneNumberId.isBlank()) {
            return new String[]{platformAccessToken, platformPhoneNumberId};
        }
        return null;
    }

    /** Prepends "91" to a 10-digit Indian phone number. No-ops if already in E.164. */
    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) return "91" + digits;
        if (digits.startsWith("91") && digits.length() == 12) return digits;
        return digits;
    }

    /**
     * Encrypts plaintext using AES/ECB/PKCS5Padding with a 16-byte key derived from
     * encryptionKey via Arrays.copyOf (truncates or zero-pads to exactly 16 bytes).
     */
    public String encrypt(String plaintext) {
        if (encryptionKey == null || encryptionKey.isBlank()) return plaintext;
        try {
            byte[] keyBytes = Arrays.copyOf(
                    encryptionKey.getBytes(StandardCharsets.UTF_8), 16);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("WhatsApp token encryption failed, storing plain: {}", e.getMessage());
            return plaintext;
        }
    }

    /**
     * Decrypts ciphertext using AES/ECB/PKCS5Padding with a 16-byte key derived from
     * encryptionKey via Arrays.copyOf (truncates or zero-pads to exactly 16 bytes).
     */
    public String decrypt(String ciphertext) {
        if (encryptionKey == null || encryptionKey.isBlank()) return ciphertext;
        try {
            byte[] keyBytes = Arrays.copyOf(
                    encryptionKey.getBytes(StandardCharsets.UTF_8), 16);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
            return new String(cipher.doFinal(
                    Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("WhatsApp token decryption failed, returning as-is: {}", e.getMessage());
            return ciphertext;
        }
    }
}
