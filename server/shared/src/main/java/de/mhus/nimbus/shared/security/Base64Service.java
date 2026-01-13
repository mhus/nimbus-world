package de.mhus.nimbus.shared.security;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service for encoding and decoding strings to/from Base64.
 *
 * <p>Provides simple utility methods to convert strings to Base64 representation
 * and decode Base64 strings back to their original form.
 */
@Service
@Slf4j
public class Base64Service {

    /**
     * Encodes the given text to Base64.
     *
     * @param text the text to encode, never null
     * @return the Base64-encoded string
     * @throws Base64Exception if encoding fails
     */
    public String encode(@NonNull String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("Failed to encode text to Base64: {}", e.getMessage(), e);
            throw new Base64Exception("Failed to encode text to Base64: " + e.getMessage(), e);
        }
    }

    /**
     * Decodes the given Base64 string back to its original text.
     *
     * @param base64Text the Base64-encoded string, never null
     * @return the decoded original text
     * @throws Base64Exception if decoding fails or the input is not valid Base64
     */
    public String decode(@NonNull String base64Text) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Text);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode Base64 text (invalid format): {}", e.getMessage(), e);
            throw new Base64Exception("Failed to decode Base64 text: invalid format", e);
        } catch (Exception e) {
            log.error("Failed to decode Base64 text: {}", e.getMessage(), e);
            throw new Base64Exception("Failed to decode Base64 text: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Custom exception

    public static class Base64Exception extends RuntimeException {
        public Base64Exception(String message) {
            super(message);
        }

        public Base64Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
