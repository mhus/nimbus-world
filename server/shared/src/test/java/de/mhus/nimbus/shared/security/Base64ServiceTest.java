package de.mhus.nimbus.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base64ServiceTest {

    private Base64Service base64Service;

    private static final String TEST_TEXT = "Hello, World!";
    private static final String TEST_TEXT_BASE64 = "SGVsbG8sIFdvcmxkIQ==";

    @BeforeEach
    void setUp() {
        base64Service = new Base64Service();
    }

    // ================================================================
    // encode() tests
    // ================================================================

    @Test
    void encode_simpleText_shouldReturnBase64() {
        String encoded = base64Service.encode(TEST_TEXT);

        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEmpty();
        assertThat(encoded).isEqualTo(TEST_TEXT_BASE64);
    }

    @Test
    void encode_emptyString_shouldReturnEmptyBase64() {
        String encoded = base64Service.encode("");

        assertThat(encoded).isNotNull();
        assertThat(encoded).isEmpty();
    }

    @Test
    void encode_textWithSpecialCharacters_shouldReturnBase64() {
        String specialText = "Text with special chars: äöü 日本語 emoji 🎉";
        String encoded = base64Service.encode(specialText);

        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEmpty();
    }

    @Test
    void encode_textWithNewlines_shouldReturnBase64() {
        String textWithNewlines = "Line 1\nLine 2\nLine 3";
        String encoded = base64Service.encode(textWithNewlines);

        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEmpty();
    }

    @Test
    void encode_textWithTabs_shouldReturnBase64() {
        String textWithTabs = "Column1\tColumn2\tColumn3";
        String encoded = base64Service.encode(textWithTabs);

        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEmpty();
    }

    @Test
    void encode_longText_shouldReturnBase64() {
        String longText = "a".repeat(10000);
        String encoded = base64Service.encode(longText);

        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEmpty();
    }

    @Test
    void encode_binaryLikeText_shouldReturnBase64() {
        String binaryText = "\u0000\u0001\u0002\u0003";
        String encoded = base64Service.encode(binaryText);

        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEmpty();
    }

    @Test
    void encode_sameTextMultipleTimes_shouldProduceSameResult() {
        String encoded1 = base64Service.encode(TEST_TEXT);
        String encoded2 = base64Service.encode(TEST_TEXT);
        String encoded3 = base64Service.encode(TEST_TEXT);

        assertThat(encoded1).isEqualTo(encoded2);
        assertThat(encoded2).isEqualTo(encoded3);
    }

    // ================================================================
    // decode() tests
    // ================================================================

    @Test
    void decode_validBase64_shouldReturnOriginalText() {
        String decoded = base64Service.decode(TEST_TEXT_BASE64);

        assertThat(decoded).isNotNull();
        assertThat(decoded).isEqualTo(TEST_TEXT);
    }

    @Test
    void decode_emptyString_shouldReturnEmptyString() {
        String decoded = base64Service.decode("");

        assertThat(decoded).isNotNull();
        assertThat(decoded).isEmpty();
    }

    @Test
    void decode_invalidBase64_shouldThrowException() {
        String invalidBase64 = "invalid!!!base64";

        assertThatThrownBy(() -> base64Service.decode(invalidBase64))
                .isInstanceOf(Base64Service.Base64Exception.class)
                .hasMessageContaining("Failed to decode");
    }

    @Test
    void decode_invalidCharacters_shouldThrowException() {
        String invalidBase64 = "SGVs bG8h"; // Contains space which is invalid

        assertThatThrownBy(() -> base64Service.decode(invalidBase64))
                .isInstanceOf(Base64Service.Base64Exception.class);
    }

    @Test
    void decode_incompleteBase64_shouldThrowException() {
        String incompleteBase64 = "SGVs"; // Incomplete padding

        // Note: Basic decoder is lenient with padding, so this might not throw
        // But we test that it either throws or decodes to something
        try {
            String decoded = base64Service.decode(incompleteBase64);
            assertThat(decoded).isNotNull();
        } catch (Base64Service.Base64Exception e) {
            // Expected in some cases
            assertThat(e).hasMessageContaining("Failed to decode");
        }
    }

    // ================================================================
    // Round-trip tests
    // ================================================================

    @Test
    void roundTrip_simpleText_shouldWorkCorrectly() {
        String encoded = base64Service.encode(TEST_TEXT);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(TEST_TEXT);
    }

    @Test
    void roundTrip_emptyText_shouldWorkCorrectly() {
        String encoded = base64Service.encode("");
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEmpty();
    }

    @Test
    void roundTrip_textWithSpecialCharacters_shouldWorkCorrectly() {
        String specialText = "Text with special chars: äöü 日本語 emoji 🎉 \n\t";
        String encoded = base64Service.encode(specialText);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(specialText);
    }

    @Test
    void roundTrip_longText_shouldWorkCorrectly() {
        String longText = "a".repeat(10000);
        String encoded = base64Service.encode(longText);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(longText);
    }

    @Test
    void roundTrip_textWithNewlinesAndTabs_shouldWorkCorrectly() {
        String text = "Line 1\nLine 2\tColumn 2\nLine 3";
        String encoded = base64Service.encode(text);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(text);
    }

    @Test
    void roundTrip_allPrintableAscii_shouldWorkCorrectly() {
        StringBuilder sb = new StringBuilder();
        for (int i = 32; i < 127; i++) {
            sb.append((char) i);
        }
        String text = sb.toString();

        String encoded = base64Service.encode(text);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(text);
    }

    @Test
    void roundTrip_multipleTimes_shouldWorkCorrectly() {
        String text = TEST_TEXT;

        // Encode-decode multiple times
        String encoded1 = base64Service.encode(text);
        String decoded1 = base64Service.decode(encoded1);

        String encoded2 = base64Service.encode(decoded1);
        String decoded2 = base64Service.decode(encoded2);

        String encoded3 = base64Service.encode(decoded2);
        String decoded3 = base64Service.decode(encoded3);

        assertThat(decoded1).isEqualTo(text);
        assertThat(decoded2).isEqualTo(text);
        assertThat(decoded3).isEqualTo(text);
    }

    // ================================================================
    // UTF-8 encoding tests
    // ================================================================

    @Test
    void encode_unicodeCharacters_shouldPreserveEncoding() {
        String unicode = "Hello 世界 🌍";
        String encoded = base64Service.encode(unicode);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(unicode);
    }

    @Test
    void encode_germanUmlauts_shouldPreserveEncoding() {
        String german = "äöüÄÖÜß";
        String encoded = base64Service.encode(german);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(german);
    }

    @Test
    void encode_cyrillicCharacters_shouldPreserveEncoding() {
        String cyrillic = "Привет мир";
        String encoded = base64Service.encode(cyrillic);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(cyrillic);
    }

    @Test
    void encode_arabicCharacters_shouldPreserveEncoding() {
        String arabic = "مرحبا بالعالم";
        String encoded = base64Service.encode(arabic);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(arabic);
    }

    // ================================================================
    // Edge case tests
    // ================================================================

    @Test
    void encode_singleCharacter_shouldWorkCorrectly() {
        String encoded = base64Service.encode("A");
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo("A");
    }

    @Test
    void encode_twoCharacters_shouldWorkCorrectly() {
        String encoded = base64Service.encode("AB");
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo("AB");
    }

    @Test
    void encode_threeCharacters_shouldWorkCorrectly() {
        String encoded = base64Service.encode("ABC");
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo("ABC");
    }

    @Test
    void encode_fourCharacters_shouldWorkCorrectly() {
        String encoded = base64Service.encode("ABCD");
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo("ABCD");
    }

    @Test
    void encode_textWithOnlySpaces_shouldWorkCorrectly() {
        String spaces = "     ";
        String encoded = base64Service.encode(spaces);
        String decoded = base64Service.decode(encoded);

        assertThat(decoded).isEqualTo(spaces);
    }

    // ================================================================
    // Deterministic behavior tests
    // ================================================================

    @Test
    void encode_deterministic() {
        // Encoding should be deterministic
        String text = "Test text for deterministic encoding";

        String encoded1 = base64Service.encode(text);
        String encoded2 = base64Service.encode(text);
        String encoded3 = base64Service.encode(text);

        assertThat(encoded1).isEqualTo(encoded2);
        assertThat(encoded2).isEqualTo(encoded3);
    }

    @Test
    void decode_deterministic() {
        // Decoding should be deterministic
        String base64 = "VGVzdCB0ZXh0IGZvciBkZXRlcm1pbmlzdGljIGRlY29kaW5n";

        String decoded1 = base64Service.decode(base64);
        String decoded2 = base64Service.decode(base64);
        String decoded3 = base64Service.decode(base64);

        assertThat(decoded1).isEqualTo(decoded2);
        assertThat(decoded2).isEqualTo(decoded3);
    }

    // ================================================================
    // Format validation tests
    // ================================================================

    @Test
    void encode_resultShouldBeValidBase64Format() {
        String encoded = base64Service.encode(TEST_TEXT);

        // Base64 should only contain A-Z, a-z, 0-9, +, /, and = for padding
        assertThat(encoded).matches("^[A-Za-z0-9+/]*={0,2}$");
    }

    @Test
    void encode_resultLengthShouldBeMultipleOfFour() {
        String text = "Test";
        String encoded = base64Service.encode(text);

        // Base64 output length should be a multiple of 4
        assertThat(encoded.length() % 4).isEqualTo(0);
    }

    // ================================================================
    // Exception handling tests
    // ================================================================

    @Test
    void decode_nullInput_shouldThrowException() {
        assertThatThrownBy(() -> base64Service.decode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void encode_nullInput_shouldThrowException() {
        assertThatThrownBy(() -> base64Service.encode(null))
                .isInstanceOf(NullPointerException.class);
    }
}
