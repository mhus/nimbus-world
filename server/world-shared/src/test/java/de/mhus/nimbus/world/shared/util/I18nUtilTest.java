package de.mhus.nimbus.world.shared.util;

import org.junit.jupiter.api.Test;

import static de.mhus.nimbus.world.shared.util.I18nUtil.*;
import static org.assertj.core.api.Assertions.*;

class I18nUtilTest {

    // --- encode ---

    @Test
    void encodeSimple() {
        assertThat(encode(EN, "Hello", DE, "Hallo"))
                .isEqualTo("¶en=Hello&de=Hallo");
    }

    @Test
    void encodeSingleLanguage() {
        assertThat(encode(EN, "Hello"))
                .isEqualTo("¶en=Hello");
    }

    @Test
    void encodeFourLanguages() {
        assertThat(encode(EN, "Hello", DE, "Hallo", FR, "Bonjour", ES, "Hola"))
                .isEqualTo("¶en=Hello&de=Hallo&fr=Bonjour&es=Hola");
    }

    @Test
    void encodeUrlEncodesSpecialChars() {
        String result = encode(EN, "Buy & Sell", DE, "Kaufen & Verkaufen");
        assertThat(result).startsWith("¶");
        assertThat(decode(EN, result)).isEqualTo("Buy & Sell");
        assertThat(decode(DE, result)).isEqualTo("Kaufen & Verkaufen");
    }

    @Test
    void encodeEmpty() {
        assertThat(encode()).isEmpty();
    }

    @Test
    void encodeOddPairsThrows() {
        assertThatThrownBy(() -> encode("en", "Hello", "de"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- builder ---

    @Test
    void builderSimple() {
        assertThat(builder().en("Hello").de("Hallo").build())
                .isEqualTo("¶en=Hello&de=Hallo");
    }

    @Test
    void builderWithSpecialChars() {
        String result = builder().en("A=B").de("X&Y").build();
        assertThat(decode(EN, result)).isEqualTo("A=B");
        assertThat(decode(DE, result)).isEqualTo("X&Y");
    }

    @Test
    void builderEmptyReturnsEmpty() {
        assertThat(builder().build()).isEmpty();
    }

    @Test
    void builderAllLanguages() {
        String result = builder()
                .en("en").de("de").fr("fr").es("es")
                .it("it").pt("pt").ja("ja").zh("zh").ko("ko").ru("ru")
                .build();
        assertThat(decode("ko", result)).isEqualTo("ko");
        assertThat(decode("ru", result)).isEqualTo("ru");
    }

    // --- decode ---

    @Test
    void decodeExactMatch() {
        assertThat(decode(DE, "¶en=Hello&de=Hallo")).isEqualTo("Hallo");
    }

    @Test
    void decodeFallbackToEnglish() {
        assertThat(decode(FR, "¶en=Hello&de=Hallo")).isEqualTo("Hello");
    }

    @Test
    void decodeFallbackToFirst() {
        assertThat(decode(FR, "¶de=Hallo&es=Hola")).isEqualTo("Hallo");
    }

    @Test
    void decodePlainText() {
        assertThat(decode(DE, "plain text")).isEqualTo("plain text");
    }

    @Test
    void decodeNull() {
        assertThat(decode(DE, null)).isEmpty();
    }

    @Test
    void decodeEmptyEncoded() {
        assertThat(decode(DE, "¶")).isEmpty();
    }

    @Test
    void decodeUrlEncodedValues() {
        assertThat(decode(EN, "¶en=Buy%20%26%20Sell")).isEqualTo("Buy & Sell");
    }

    @Test
    void decodeNullLangDefaultsToEnglish() {
        assertThat(decode(null, "¶en=Hello&de=Hallo")).isEqualTo("Hello");
    }

    @Test
    void decodeLongLangCodeTruncated() {
        assertThat(decode("de-DE", "¶en=Hello&de=Hallo")).isEqualTo("Hallo");
    }

    @Test
    void decodeCaseInsensitive() {
        assertThat(decode("DE", "¶en=Hello&de=Hallo")).isEqualTo("Hallo");
    }

    // --- isEncoded ---

    @Test
    void isEncodedTrue() {
        assertThat(isEncoded("¶en=Hello")).isTrue();
    }

    @Test
    void isEncodedFalseForPlain() {
        assertThat(isEncoded("Hello")).isFalse();
    }

    @Test
    void isEncodedFalseForParagraphSign() {
        assertThat(isEncoded("§1 BGB")).isFalse();
    }

    @Test
    void isEncodedFalseForNull() {
        assertThat(isEncoded(null)).isFalse();
    }

    @Test
    void isEncodedFalseForEmpty() {
        assertThat(isEncoded("")).isFalse();
    }

    // --- roundtrip ---

    @Test
    void roundtripEncodeDecodeAllLanguages() {
        String encoded = encode(EN, "Hello", DE, "Hallo", FR, "Bonjour", ES, "Hola");
        assertThat(decode(EN, encoded)).isEqualTo("Hello");
        assertThat(decode(DE, encoded)).isEqualTo("Hallo");
        assertThat(decode(FR, encoded)).isEqualTo("Bonjour");
        assertThat(decode(ES, encoded)).isEqualTo("Hola");
    }

    @Test
    void roundtripWithUnicode() {
        String encoded = builder().en("Hello").de("Hallo").ja("こんにちは").build();
        assertThat(decode("ja", encoded)).isEqualTo("こんにちは");
        assertThat(decode(EN, encoded)).isEqualTo("Hello");
    }
}
