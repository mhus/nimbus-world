package de.mhus.nimbus.shared.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesEncryptionServiceTest {

    private AesEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new AesEncryptionService("testPasswordForEncryption123");
    }

    @Test
    void shouldEncryptAndDecryptSuccessfully() {
        // Given
        String plaintext = "mySecretPassword123!";

        // When
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldProduceDifferentCiphertextsForSamePlaintext() {
        // Given
        String plaintext = "mySecretPassword123!";

        // When
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        // Then - Different IVs should produce different ciphertexts
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(plaintext);
    }

    @Test
    void shouldHandleNullPlaintext() {
        // When
        String encrypted = encryptionService.encrypt(null);

        // Then
        assertThat(encrypted).isNull();
    }

    @Test
    void shouldHandleNullCiphertext() {
        // When
        String decrypted = encryptionService.decrypt(null);

        // Then
        assertThat(decrypted).isNull();
    }

    @Test
    void shouldHandleBlankCiphertext() {
        // When
        String decrypted = encryptionService.decrypt("");

        // Then
        assertThat(decrypted).isNull();
    }

    @Test
    void shouldHandleEmptyString() {
        // Given
        String plaintext = "";

        // When
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldHandleSpecialCharacters() {
        // Given
        String plaintext = "!@#$%^&*()_+-=[]{}|;':\",./<>?äöüÄÖÜß€";

        // When
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldHandleUnicodeCharacters() {
        // Given
        String plaintext = "Hello 世界 🌍 مرحبا";

        // When
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldHandleLongText() {
        // Given
        String plaintext = "a".repeat(10000);

        // When
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldThrowExceptionWhenDecryptingInvalidCiphertext() {
        // Given
        String invalidCiphertext = "this-is-not-a-valid-encrypted-string";

        // When/Then
        assertThatThrownBy(() -> encryptionService.decrypt(invalidCiphertext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void shouldThrowExceptionWhenDecryptingTamperedCiphertext() {
        // Given
        String plaintext = "mySecretPassword123!";
        String encrypted = encryptionService.encrypt(plaintext);
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "XXXX";

        // When/Then
        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsNull() {
        // When/Then
        assertThatThrownBy(() -> new AesEncryptionService(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encryption password must be configured");
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsBlank() {
        // When/Then
        assertThatThrownBy(() -> new AesEncryptionService(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encryption password must be configured");
    }

    @Test
    void shouldProduceDifferentKeysForDifferentPasswords() {
        // Given
        String plaintext = "mySecretPassword123!";
        AesEncryptionService service1 = new AesEncryptionService("password1");
        AesEncryptionService service2 = new AesEncryptionService("password2");

        // When
        String encrypted1 = service1.encrypt(plaintext);

        // Then - service2 should not be able to decrypt service1's ciphertext
        assertThatThrownBy(() -> service2.decrypt(encrypted1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }
}
