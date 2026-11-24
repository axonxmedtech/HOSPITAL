package com.hms.util;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Utility for streaming AES-256-CBC encryption and decryption, fully compatible with:
 * openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt
 */
public final class BackupCryptoUtil {

    private static final byte[] SALTED_MAGIC = "Salted__".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_LEN = 8;
    private static final int ITERATION_COUNT = 100_000;
    private static final int KEY_LEN_BYTES = 32; // 256 bits
    private static final int IV_LEN_BYTES = 16;  // 128 bits
    private static final int TOTAL_DERIVED_BYTES = KEY_LEN_BYTES + IV_LEN_BYTES;
    private static final int BUFFER_SIZE = 8192;

    private BackupCryptoUtil() {}

    /**
     * Encrypts input stream into output stream in OpenSSL-compatible format.
     */
    public static void encrypt(InputStream in, OutputStream out, String password)
            throws IOException, GeneralSecurityException {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);

        // Write OpenSSL magic "Salted__" followed by 8 bytes salt
        out.write(SALTED_MAGIC);
        out.write(salt);

        byte[] derived = deriveKeyAndIv(password.toCharArray(), salt);
        byte[] keyBytes = Arrays.copyOfRange(derived, 0, KEY_LEN_BYTES);
        byte[] ivBytes = Arrays.copyOfRange(derived, KEY_LEN_BYTES, TOTAL_DERIVED_BYTES);

        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

        try (CipherOutputStream cos = new CipherOutputStream(out, cipher)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) != -1) {
                cos.write(buf, 0, read);
            }
            cos.flush();
        }
    }

    /**
     * Creates an InputStream that decrypts on-the-fly, reading OpenSSL Salted__ header and salt.
     */
    public static InputStream createDecryptionStream(InputStream in, String password)
            throws IOException, GeneralSecurityException {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        byte[] magic = new byte[SALTED_MAGIC.length];
        int readMagic = in.readNBytes(magic, 0, magic.length);
        if (readMagic != magic.length || !Arrays.equals(magic, SALTED_MAGIC)) {
            throw new IllegalArgumentException("Invalid backup file: missing OpenSSL Salted__ header");
        }

        byte[] salt = new byte[SALT_LEN];
        int readSalt = in.readNBytes(salt, 0, salt.length);
        if (readSalt != salt.length) {
            throw new IllegalArgumentException("Invalid backup file: truncated salt header");
        }

        byte[] derived = deriveKeyAndIv(password.toCharArray(), salt);
        byte[] keyBytes = Arrays.copyOfRange(derived, 0, KEY_LEN_BYTES);
        byte[] ivBytes = Arrays.copyOfRange(derived, KEY_LEN_BYTES, TOTAL_DERIVED_BYTES);

        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

        return new CipherInputStream(in, cipher);
    }

    /**
     * Decrypts input stream into output stream, expecting OpenSSL-compatible format.
     */
    public static void decrypt(InputStream in, OutputStream out, String password)
            throws IOException, GeneralSecurityException {
        try (InputStream cis = createDecryptionStream(in, password)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = cis.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        }
    }

    private static byte[] deriveKeyAndIv(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATION_COUNT, TOTAL_DERIVED_BYTES * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }
}
