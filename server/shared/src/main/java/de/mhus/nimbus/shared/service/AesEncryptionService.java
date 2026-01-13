package de.mhus.nimbus.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for AES-256-GCM encryption and decryption.
 * Uses a password from application.yaml which is hashed with SHA-256 to derive the encryption key.
 */
@Service
@Slf4j
public class AesEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public AesEncryptionService(@Value("${nimbus.encryption.password}") String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Encryption password must be configured in application.yaml");
        }
        this.secretKey = deriveKeyFromPassword(password);
        this.secureRandom = new SecureRandom();
    }

    /**
     * Derives a 256-bit AES key from the password using SHA-256 hashing
     */
    private SecretKey deriveKeyFromPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive encryption key from password", e);
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Returns Base64 encoded string containing IV + encrypted data + auth tag
     *
     * @param plaintext The text to encrypt
     * @return Base64 encoded encrypted data with IV
     * @throws RuntimeException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedData.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedData);

            // Encode to Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts Base64 encoded ciphertext using AES-256-GCM.
     * Expects format: IV + encrypted data + auth tag
     *
     * @param ciphertext Base64 encoded encrypted data with IV
     * @return Decrypted plaintext
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }

        try {
            // Decode from Base64
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            // Extract IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedData = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedData);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] decryptedData = cipher.doFinal(encryptedData);

            return new String(decryptedData, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
