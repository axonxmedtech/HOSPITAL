package com.hms.service.portal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * MSG91 OTP delivery. If {@code msg91.auth.key} is unconfigured (blank), falls back to
 * logging the OTP to the server console instead of calling the external API — this only
 * activates when the credential is absent, so production behavior once configured is a
 * real SMS send.
 */
@Component
public class Msg91SmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsGateway.class);

    @Value("${msg91.auth.key}")
    private String authKey;

    @Value("${msg91.sender.id}")
    private String senderId;

    @Value("${msg91.otp.template.id}")
    private String templateId;

    @Value("${msg91.api.url}")
    private String apiUrl;

    @Value("${msg91.dev.fallback.enabled}")
    private boolean devFallbackEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void send(String mobile, String otp) {
        if (authKey == null || authKey.isBlank()) {
            if (!devFallbackEnabled) {
                throw new RuntimeException("MSG91 is not configured and dev fallback is disabled. Set MSG91_AUTH_KEY.");
            }
            log.warn("[DEV-OTP] MSG91_AUTH_KEY not configured — OTP for {} is: {}", mobile, otp);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authkey", authKey);

            Map<String, Object> body = new HashMap<>();
            body.put("template_id", templateId);
            body.put("mobile", mobile);
            body.put("otp", otp);
            body.put("sender", senderId);

            restTemplate.exchange(apiUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.error("MSG91 OTP dispatch failed for {}: {}", mobile, e.getMessage());
            throw new RuntimeException("Failed to send OTP. Please try again.", e);
        }
    }
}
