package de.mhus.nimbus.shared.security;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Service for hashing and validating strings with optional salt support.
 *
 * <p>The hash format is: "algorithm:hashBase64" for simple hashing
 * or "algorithm:saltBase64:hashBase64" when using salt (salt is Base64-encoded to avoid delimiter conflicts).
 *
 * <p>Uses SHA-256 as the default secure hash algorithm.
 */
@Service
@Slf4j
public class HashService {

    /**
     * Default algorithm used for hashing.
     */
    private static final String DEFAULT_HASH_ALGORITHM = "SHA-256";

    /**
     * Hashes the given text using the default algorithm (SHA-256).
     * The hash format is "algorithm:hashBase64".
     *
     * @param text the text to hash, never null
     * @return the complete hash string containing algorithm and hash
     * @throws HashException if hashing fails
     */
    public String hash(@NonNull String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DEFAULT_HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            String hashBase64 = Base64.getEncoder().encodeToString(hashBytes);
            return DEFAULT_HASH_ALGORITHM + ";" + hashBase64;
        } catch (NoSuchAlgorithmException e) {
            throw new HashException("Failed to hash text: " + e.getMessage(), e);
        }
    }

    /**
     * Hashes the given text with a salt using the default algorithm (SHA-256).
     * The hash format is "algorithm:saltBase64:hashBase64".
     *
     * @param text the text to hash, never null
     * @param salt the salt to use, never null
     * @return the complete hash string containing algorithm, salt (Base64 encoded), and hash
     * @throws HashException if hashing fails
     */
    public String hash(@NonNull String text, @NonNull String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DEFAULT_HASH_ALGORITHM);
            // First hash the salt, then the text
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            String hashBase64 = Base64.getEncoder().encodeToString(hashBytes);
            String saltBase64 = Base64.getEncoder().encodeToString(salt.getBytes(StandardCharsets.UTF_8));
            return DEFAULT_HASH_ALGORITHM + ";" + saltBase64 + ";" + hashBase64;
        } catch (NoSuchAlgorithmException e) {
            throw new HashException("Failed to hash text with salt: " + e.getMessage(), e);
        }
    }

    /**
     * Validates the given text against a hash string (without salt).
     * The hash string must be in the format "algorithm:hashBase64".
     *
     * @param text the original text to validate, never null
     * @param hashString the complete hash string, never null
     * @return true if the hash is valid, false otherwise
     */
    public boolean validate(@NonNull String text, @NonNull String hashString) {
        try {
            // Parse the hash string
            HashParts parts = parseHash(hashString);
            if (parts.salt() != null) {
                log.warn("Hash string contains salt but validate was called without salt parameter");
                return false;
            }

            // Compute hash of the text
            MessageDigest digest = MessageDigest.getInstance(parts.algorithm());
            byte[] computedHashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            byte[] providedHashBytes = Base64.getDecoder().decode(parts.hashBase64());

            return MessageDigest.isEqual(computedHashBytes, providedHashBytes);

        } catch (Exception e) {
            log.error("Validation failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validates the given text against a hash string with salt.
     * The hash string must be in the format "algorithm:saltBase64:hashBase64".
     *
     * @param text the original text to validate, never null
     * @param salt the salt used during hashing, never null
     * @param hashString the complete hash string, never null
     * @return true if the hash is valid, false otherwise
     */
    public boolean validate(@NonNull String text, @NonNull String salt, @NonNull String hashString) {
        try {
            // Parse the hash string
            HashParts parts = parseHash(hashString);
            if (parts.salt() == null) {
                log.warn("Hash string does not contain salt but validate was called with salt parameter");
                return false;
            }

            // Verify salt matches
            if (!salt.equals(parts.salt())) {
                log.warn("Provided salt does not match salt in hash string");
                return false;
            }

            // Compute hash of the text with salt
            MessageDigest digest = MessageDigest.getInstance(parts.algorithm());
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] computedHashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            byte[] providedHashBytes = Base64.getDecoder().decode(parts.hashBase64());

            return MessageDigest.isEqual(computedHashBytes, providedHashBytes);

        } catch (Exception e) {
            log.error("Validation with salt failed: {}", e.getMessage(), e);
            return false;
        }
    }

    // ----------------------------------------------------------------
    // Private helper methods

    private HashParts parseHash(String hashString) {
        String[] parts = hashString.split(";", 3);
        if (parts.length == 2) {
            // Format: algorithm:hashBase64 (no salt)
            return new HashParts(parts[0], null, parts[1]);
        } else if (parts.length == 3) {
            // Format: algorithm:saltBase64:hashBase64
            // Decode the Base64-encoded salt
            String saltBase64 = parts[1];
            String salt = new String(Base64.getDecoder().decode(saltBase64), StandardCharsets.UTF_8);
            return new HashParts(parts[0], salt, parts[2]);
        } else {
            throw new HashException("Invalid hash format. Expected 'algorithm;hashBase64' or 'algorithm:saltBase64;hashBase64'");
        }
    }

    // ----------------------------------------------------------------
    // Internal record to hold parsed hash components

    private record HashParts(String algorithm, String salt, String hashBase64) {}

    // ----------------------------------------------------------------
    // Custom exception

    public static class HashException extends RuntimeException {
        public HashException(String message) {
            super(message);
        }

        public HashException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
