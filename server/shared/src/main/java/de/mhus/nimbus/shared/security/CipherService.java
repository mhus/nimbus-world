package de.mhus.nimbus.shared.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for encrypting and decrypting text using cryptographic keys from KeyService.
 *
 * <p>The cipher format is: "keyIdBase64:algorithm:encryptedDataBase64:ivBase64"
 * where keyIdBase64 is the Base64-encoded keyId (to avoid delimiter conflicts with "owner:id" format),
 * algorithm is the cryptographic algorithm used, encryptedDataBase64 is the Base64-encoded encrypted data,
 * and ivBase64 is the Base64-encoded initialization vector (IV).
 *
 * <p>Encryption uses a constant default algorithm (AES/GCM/NoPadding for symmetric keys).
 * Decryption extracts the keyId, algorithm, and IV from the cipher string.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CipherService {

    private final KeyService keyService;

    /**
     * Default algorithm used for encryption with symmetric keys.
     */
    private static final String DEFAULT_CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    
    /**
     * GCM tag length in bits (128 bits = 16 bytes is standard).
     */
    private static final int GCM_TAG_LENGTH = 128;
    
    /**
     * IV length in bytes (12 bytes is recommended for GCM).
     */
    private static final int IV_LENGTH = 12;

    /**
     * Encrypts the given text using a symmetric key identified by keyId.
     * The cipher format is "keyIdBase64:algorithm:encryptedDataBase64:ivBase64".
     *
     * @param text the text to encrypt, never null
     * @param keyId the key identifier in format "owner:id", never null
     * @return the complete cipher string containing Base64-encoded keyId, algorithm, encrypted data, and IV
     * @throws CipherException if the key cannot be found or encryption fails
     */
    public String encryptAes(@NonNull String text, KeyType keyType, @NonNull String keyId) {
        Optional<SecretKey> syncKey = keyService.getSecretKey(keyType, keyId);
        if (syncKey.isPresent()) {
            return encryptWithAesGcm(text, keyId, syncKey.get(), DEFAULT_CIPHER_ALGORITHM);
        }
        throw new CipherException("No symmetric key found for keyId: " + keyId);
    }

    /**
     * Decrypts the given cipher string.
     * The cipher string must be in the format "keyIdBase64:algorithm:encryptedDataBase64:ivBase64".
     *
     * @param cipherString the complete cipher string, never null
     * @return the decrypted text
     * @throws CipherException if decryption fails or the cipher format is invalid
     */
    public String decryptAes(@NonNull String cipherString, KeyType keyType) {
        try {
            CipherParts parts = parseCipher(cipherString);
            Optional<SecretKey> syncKey = keyService.getSecretKey(keyType, parts.keyId());
            if (syncKey.isPresent()) {
                return decryptWithAesGcm(parts, syncKey.get());
            }
            throw new CipherException("No symmetric key found for keyId: " + parts.keyId());
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage(), e);
            throw new CipherException("Decryption failed: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Private helper methods

    private String encryptWithAesGcm(String text, String keyId, SecretKey key, String algorithm) {
        try {
            // Generate a random IV
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            // Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(algorithm);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            // Encrypt the text
            byte[] encryptedBytes = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

            // Encode to Base64
            String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String keyIdBase64 = Base64.getEncoder().encodeToString(keyId.getBytes(StandardCharsets.UTF_8));

            // Return format: keyIdBase64:algorithm:encryptedDataBase64:ivBase64
            return keyIdBase64 + ";" + algorithm + ";" + encryptedBase64 + ";" + ivBase64;

        } catch (Exception e) {
            throw new CipherException("Failed to encrypt text: " + e.getMessage(), e);
        }
    }

    private String decryptWithAesGcm(CipherParts parts, SecretKey key) {
        try {
            // Decode from Base64
            byte[] encryptedBytes = Base64.getDecoder().decode(parts.encryptedDataBase64());
            byte[] iv = Base64.getDecoder().decode(parts.ivBase64());

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(parts.algorithm());
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            // Decrypt the data
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("AES-GCM decryption error: {}", e.getMessage(), e);
            throw new CipherException("Decryption failed: " + e.getMessage(), e);
        }
    }

    private CipherParts parseCipher(String cipherString) {
        String[] parts = cipherString.split(";", 4);
        if (parts.length != 4) {
            throw new CipherException("Invalid cipher format. Expected 'keyIdBase64:algorithm:encryptedDataBase64:ivBase64'");
        }
        // Decode the Base64-encoded keyId
        String keyIdBase64 = parts[0];
        String keyId = new String(Base64.getDecoder().decode(keyIdBase64), StandardCharsets.UTF_8);
        return new CipherParts(keyId, parts[1], parts[2], parts[3]);
    }

    // ----------------------------------------------------------------
    // Internal record to hold parsed cipher components

    private record CipherParts(String keyId, String algorithm, String encryptedDataBase64, String ivBase64) {}

    // ----------------------------------------------------------------
    // Custom exception

    public static class CipherException extends RuntimeException {
        public CipherException(String message) {
            super(message);
        }

        public CipherException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
