package com.hms.service.whatsapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the WhatsApp token-at-rest encryption: new values use authenticated AES/GCM, and
 * tokens saved by the previous AES/ECB implementation still decrypt (backward compatibility).
 */
class WhatsAppServiceEncryptionTest {

    private static final String KEY = "test-encryption-key-123";
    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        // encrypt/decrypt only use the encryptionKey field, so the repositories can be null here.
        service = new WhatsAppService(null, null);
        ReflectionTestUtils.setField(service, "encryptionKey", KEY);
    }

    @Test
    void encryptThenDecrypt_roundTrips() {
        String plain = "EAAG_super_secret_whatsapp_token_9f8a";
        String enc = service.encrypt(plain);
        assertThat(enc).startsWith("g1:").isNotEqualTo(plain);
        assertThat(service.decrypt(enc)).isEqualTo(plain);
    }

    @Test
    void encrypt_usesFreshIv_soCiphertextDiffersEachTime() {
        String plain = "same-token";
        assertThat(service.encrypt(plain)).isNotEqualTo(service.encrypt(plain));
    }

    @Test
    void decrypt_stillReadsLegacyEcbTokens() throws Exception {
        // Simulate a token stored by the previous AES/ECB implementation.
        byte[] keyBytes = Arrays.copyOf(KEY.getBytes(StandardCharsets.UTF_8), 16);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
        String plain = "legacy-token-value";
        String legacy = Base64.getEncoder().encodeToString(
                cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));

        assertThat(service.decrypt(legacy)).isEqualTo(plain);
    }

    @Test
    void blankKey_returnsInputUnchanged() {
        ReflectionTestUtils.setField(service, "encryptionKey", "");
        assertThat(service.encrypt("x")).isEqualTo("x");
        assertThat(service.decrypt("x")).isEqualTo("x");
    }
}
