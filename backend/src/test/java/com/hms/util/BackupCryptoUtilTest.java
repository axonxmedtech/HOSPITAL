package com.hms.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupCryptoUtilTest {

    @Test
    void encryptAndDecrypt_successRoundTrip() throws Exception {
        String originalContent = "CREATE TABLE patients (id INT PRIMARY KEY, name VARCHAR(100));\nINSERT INTO patients VALUES (1, 'Ravi');";
        String testPassword = "test-key-" + UUID.randomUUID();

        ByteArrayOutputStream encOut = new ByteArrayOutputStream();
        try (ByteArrayInputStream in = new ByteArrayInputStream(originalContent.getBytes(StandardCharsets.UTF_8))) {
            BackupCryptoUtil.encrypt(in, encOut, testPassword);
        }

        byte[] encryptedBytes = encOut.toByteArray();
        assertThat(encryptedBytes).isNotEmpty();
        // Verify OpenSSL magic prefix
        assertThat(new String(encryptedBytes, 0, 8, StandardCharsets.US_ASCII)).isEqualTo("Salted__");

        ByteArrayOutputStream decOut = new ByteArrayOutputStream();
        try (ByteArrayInputStream in = new ByteArrayInputStream(encryptedBytes)) {
            BackupCryptoUtil.decrypt(in, decOut, testPassword);
        }

        String decryptedContent = decOut.toString(StandardCharsets.UTF_8);
        assertThat(decryptedContent).isEqualTo(originalContent);
    }

    @Test
    void decrypt_withWrongPassword_fails() throws Exception {
        String originalContent = "Sensitive hospital records";
        String correctPassword = "test-correct-" + UUID.randomUUID();
        String wrongPassword = "test-wrong-" + UUID.randomUUID();

        ByteArrayOutputStream encOut = new ByteArrayOutputStream();
        try (ByteArrayInputStream in = new ByteArrayInputStream(originalContent.getBytes(StandardCharsets.UTF_8))) {
            BackupCryptoUtil.encrypt(in, encOut, correctPassword);
        }

        ByteArrayOutputStream decOut = new ByteArrayOutputStream();
        try (ByteArrayInputStream in = new ByteArrayInputStream(encOut.toByteArray())) {
            assertThatThrownBy(() -> BackupCryptoUtil.decrypt(in, decOut, wrongPassword))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void decrypt_missingMagicHeader_fails() throws Exception {
        byte[] invalidData = "NotAnEncryptedBackupFileAtAll".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream decOut = new ByteArrayOutputStream();
        try (ByteArrayInputStream in = new ByteArrayInputStream(invalidData)) {
            assertThatThrownBy(() -> BackupCryptoUtil.decrypt(in, decOut, "dummyKey"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing OpenSSL Salted__ header");
        }
    }

    @Test
    void createDecryptionStream_streamsDecryptedDataDirectly() throws Exception {
        String originalContent = "Streamed patient data";
        String testPassword = "test-streaming-" + UUID.randomUUID();

        ByteArrayOutputStream encOut = new ByteArrayOutputStream();
        try (ByteArrayInputStream in = new ByteArrayInputStream(originalContent.getBytes(StandardCharsets.UTF_8))) {
            BackupCryptoUtil.encrypt(in, encOut, testPassword);
        }

        try (ByteArrayInputStream in = new ByteArrayInputStream(encOut.toByteArray());
                java.io.InputStream decStream = BackupCryptoUtil.createDecryptionStream(in, testPassword)) {
            byte[] decrypted = decStream.readAllBytes();
            assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(originalContent);
        }
    }
}
