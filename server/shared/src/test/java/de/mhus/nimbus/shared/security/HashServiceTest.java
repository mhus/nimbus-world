package de.mhus.nimbus.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashServiceTest {

    private HashService hashService;

    private static final String TEST_TEXT = "myPassword123";
    private static final String TEST_SALT = "randomSalt123";

    @BeforeEach
    void setUp() {
        hashService = new HashService();
    }

    // ================================================================
    // hash(text) tests - without salt
    // ================================================================

    @Test
    void hash_withoutSalt_shouldReturnHash() {
        String hash = hashService.hash(TEST_TEXT);

        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        // Hash format: algorithm:hashBase64
        assertThat(hash.split(";")).hasSize(2);
        assertThat(hash).startsWith("SHA-256;");
    }

    @Test
    void hash_sameTextMultipleTimes_shouldProduceSameHash() {
        String hash1 = hashService.hash(TEST_TEXT);
        String hash2 = hashService.hash(TEST_TEXT);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hash_differentText_shouldProduceDifferentHashes() {
        String hash1 = hashService.hash("Text 1");
        String hash2 = hashService.hash("Text 2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_emptyText_shouldReturnHash() {
        String hash = hashService.hash("");

        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        assertThat(hash).startsWith("SHA-256;");
    }

    @Test
    void hash_textWithSpecialCharacters_shouldReturnHash() {
        String specialText = "Text with special chars: äöü 日本語 emoji 🎉 \n\t";
        String hash = hashService.hash(specialText);

        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        assertThat(hash).startsWith("SHA-256;");
    }

    // ================================================================
    // hash(text, salt) tests - with salt
    // ================================================================

    @Test
    void hash_withSalt_shouldReturnHash() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);

        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        // Hash format: algorithm:saltBase64:hashBase64
        assertThat(hash.split(";")).hasSize(3);
        String expectedSaltBase64 = java.util.Base64.getEncoder().encodeToString(TEST_SALT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(hash).startsWith("SHA-256;" + expectedSaltBase64 + ";");
    }

    @Test
    void hash_sameTextAndSalt_shouldProduceSameHash() {
        String hash1 = hashService.hash(TEST_TEXT, TEST_SALT);
        String hash2 = hashService.hash(TEST_TEXT, TEST_SALT);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hash_sameTextDifferentSalt_shouldProduceDifferentHashes() {
        String hash1 = hashService.hash(TEST_TEXT, "salt1");
        String hash2 = hashService.hash(TEST_TEXT, "salt2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_differentTextSameSalt_shouldProduceDifferentHashes() {
        String hash1 = hashService.hash("Text 1", TEST_SALT);
        String hash2 = hashService.hash("Text 2", TEST_SALT);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_withSalt_shouldDifferFromHashWithoutSalt() {
        String hashWithoutSalt = hashService.hash(TEST_TEXT);
        String hashWithSalt = hashService.hash(TEST_TEXT, TEST_SALT);

        assertThat(hashWithoutSalt).isNotEqualTo(hashWithSalt);
    }

    @Test
    void hash_emptySalt_shouldReturnHash() {
        String hash = hashService.hash(TEST_TEXT, "");

        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
    }

    // ================================================================
    // validate(text, hash) tests - without salt
    // ================================================================

    @Test
    void validate_withoutSalt_validHash_shouldReturnTrue() {
        String hash = hashService.hash(TEST_TEXT);
        boolean isValid = hashService.validate(TEST_TEXT, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void validate_withoutSalt_wrongText_shouldReturnFalse() {
        String hash = hashService.hash(TEST_TEXT);
        boolean isValid = hashService.validate("wrongPassword", hash);

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_withoutSalt_modifiedHash_shouldReturnFalse() {
        String hash = hashService.hash(TEST_TEXT);
        String modifiedHash = hash + "extra";

        boolean isValid = hashService.validate(TEST_TEXT, modifiedHash);

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_withoutSalt_invalidHashFormat_shouldReturnFalse() {
        boolean isValid = hashService.validate(TEST_TEXT, "invalid:format:toomany:parts");

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_withoutSalt_emptyHash_shouldReturnFalse() {
        boolean isValid = hashService.validate(TEST_TEXT, "");

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_withoutSalt_invalidBase64_shouldReturnFalse() {
        String invalidHash = "SHA-256:invalid!!!base64";
        boolean isValid = hashService.validate(TEST_TEXT, invalidHash);

        assertThat(isValid).isFalse();
    }

    // ================================================================
    // validate(text, salt, hash) tests - with salt
    // ================================================================

    @Test
    void validate_withSalt_validHash_shouldReturnTrue() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);
        boolean isValid = hashService.validate(TEST_TEXT, TEST_SALT, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void validate_withSalt_wrongText_shouldReturnFalse() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);
        boolean isValid = hashService.validate("wrongPassword", TEST_SALT, hash);

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_withSalt_wrongSalt_shouldReturnFalse() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);
        boolean isValid = hashService.validate(TEST_TEXT, "wrongSalt", hash);

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_withSalt_modifiedHash_shouldReturnFalse() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);
        String modifiedHash = hash + "extra";

        boolean isValid = hashService.validate(TEST_TEXT, TEST_SALT, modifiedHash);

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_withSalt_invalidHashFormat_shouldReturnFalse() {
        boolean isValid = hashService.validate(TEST_TEXT, TEST_SALT, "invalid");

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_withSalt_invalidBase64_shouldReturnFalse() {
        String invalidHash = "SHA-256;" + TEST_SALT + ";invalid!!!base64";
        boolean isValid = hashService.validate(TEST_TEXT, TEST_SALT, invalidHash);

        assertThat(isValid).isFalse();
    }

    // ================================================================
    // Round-trip tests
    // ================================================================

    @Test
    void roundTrip_withoutSalt_shouldWorkCorrectly() {
        String hash = hashService.hash(TEST_TEXT);
        boolean isValid = hashService.validate(TEST_TEXT, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void roundTrip_withSalt_shouldWorkCorrectly() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);
        boolean isValid = hashService.validate(TEST_TEXT, TEST_SALT, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void roundTrip_emptyText_shouldWorkCorrectly() {
        String hash = hashService.hash("");
        boolean isValid = hashService.validate("", hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void roundTrip_emptyTextWithSalt_shouldWorkCorrectly() {
        String hash = hashService.hash("", TEST_SALT);
        boolean isValid = hashService.validate("", TEST_SALT, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void roundTrip_textWithSpecialCharacters_shouldWorkCorrectly() {
        String specialText = "Text with special chars: äöü 日本語 emoji 🎉 \n\t";
        String hash = hashService.hash(specialText);
        boolean isValid = hashService.validate(specialText, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void roundTrip_longText_shouldWorkCorrectly() {
        String longText = "a".repeat(10000);
        String hash = hashService.hash(longText);
        boolean isValid = hashService.validate(longText, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void roundTrip_longTextWithSalt_shouldWorkCorrectly() {
        String longText = "a".repeat(10000);
        String hash = hashService.hash(longText, TEST_SALT);
        boolean isValid = hashService.validate(longText, TEST_SALT, hash);

        assertThat(isValid).isTrue();
    }

    // ================================================================
    // Hash format tests
    // ================================================================

    @Test
    void hashFormat_withoutSalt_shouldContainAlgorithm() {
        String hash = hashService.hash(TEST_TEXT);
        String[] parts = hash.split(";");

        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isEqualTo("SHA-256");
    }

    @Test
    void hashFormat_withoutSalt_shouldContainBase64Hash() {
        String hash = hashService.hash(TEST_TEXT);
        String[] parts = hash.split(";");

        assertThat(parts).hasSize(2);
        assertThat(parts[1]).isNotEmpty();
        // Verify it's valid base64 by trying to decode
        assertThat(java.util.Base64.getDecoder().decode(parts[1])).isNotEmpty();
    }

    @Test
    void hashFormat_withSalt_shouldContainAlgorithm() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);
        String[] parts = hash.split(";");

        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo("SHA-256");
    }

    @Test
    void hashFormat_withSalt_shouldContainSalt() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);
        String[] parts = hash.split(";");

        assertThat(parts).hasSize(3);
        // Decode the Base64-encoded salt and compare
        String decodedSalt = new String(java.util.Base64.getDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(decodedSalt).isEqualTo(TEST_SALT);
    }

    @Test
    void hashFormat_withSalt_shouldContainBase64Hash() {
        String hash = hashService.hash(TEST_TEXT, TEST_SALT);
        String[] parts = hash.split(";");

        assertThat(parts).hasSize(3);
        assertThat(parts[2]).isNotEmpty();
        // Verify it's valid base64 by trying to decode
        assertThat(java.util.Base64.getDecoder().decode(parts[2])).isNotEmpty();
    }

    // ================================================================
    // Edge case tests
    // ================================================================

    @Test
    void hash_textWithColons_shouldWorkCorrectly() {
        String textWithColons = "text:with:colons";
        String hash = hashService.hash(textWithColons);
        boolean isValid = hashService.validate(textWithColons, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void hash_saltWithColons_shouldWorkCorrectly() {
        String saltWithColons = "salt:with:colons";
        String hash = hashService.hash(TEST_TEXT, saltWithColons);
        boolean isValid = hashService.validate(TEST_TEXT, saltWithColons, hash);

        assertThat(isValid).isTrue();
    }

    @Test
    void validate_wrongAlgorithmInHash_shouldReturnFalse() {
        // Create a hash with correct format but wrong algorithm
        String fakeHash = "MD5:dGVzdA==";
        boolean isValid = hashService.validate(TEST_TEXT, fakeHash);

        assertThat(isValid).isFalse();
    }

    @Test
    void validate_caseSensitivity_shouldBeRespected() {
        String hash = hashService.hash("password");
        boolean isValidLower = hashService.validate("password", hash);
        boolean isValidUpper = hashService.validate("PASSWORD", hash);

        assertThat(isValidLower).isTrue();
        assertThat(isValidUpper).isFalse();
    }

    // ================================================================
    // Deterministic behavior tests
    // ================================================================

    @Test
    void hash_deterministicWithoutSalt() {
        // Multiple calls with same input should produce identical results
        String hash1 = hashService.hash(TEST_TEXT);
        String hash2 = hashService.hash(TEST_TEXT);
        String hash3 = hashService.hash(TEST_TEXT);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash2).isEqualTo(hash3);
    }

    @Test
    void hash_deterministicWithSalt() {
        // Multiple calls with same input and salt should produce identical results
        String hash1 = hashService.hash(TEST_TEXT, TEST_SALT);
        String hash2 = hashService.hash(TEST_TEXT, TEST_SALT);
        String hash3 = hashService.hash(TEST_TEXT, TEST_SALT);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash2).isEqualTo(hash3);
    }
}
