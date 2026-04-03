package de.mhus.nimbus.world.shared.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility for encoding inline multilingual texts using the ¶-prefix format.
 *
 * Format: ¶en=Hello&de=Hallo&fr=Bonjour
 *
 * Values containing &, = or non-ASCII characters are URL-encoded.
 *
 * Usage:
 *   I18nUtil.encode("en", "Hello", "de", "Hallo")
 *   // → "¶en=Hello&de=Hallo"
 *
 *   I18nUtil.builder().en("Hello").de("Hallo").fr("Bonjour").build()
 *   // → "¶en=Hello&de=Hallo&fr=Bonjour"
 */
public final class I18nUtil {

    public static final char PREFIX = '¶';

    public static final String EN = "en";
    public static final String DE = "de";
    public static final String FR = "fr";
    public static final String ES = "es";

    private I18nUtil() {}

    /**
     * Encode key-value pairs as i18n text. Pairs are passed alternating: lang, value, lang, value, ...
     *
     * @param pairs alternating language code and text (e.g. "en", "Hello", "de", "Hallo")
     * @return encoded i18n string starting with ¶
     * @throws IllegalArgumentException if odd number of arguments
     */
    public static String encode(String... pairs) {
        if (pairs.length == 0) return "";
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Pairs must be even: lang, value, lang, value, ...");

        var sb = new StringBuilder(pairs.length * 16);
        sb.append(PREFIX);
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) sb.append('&');
            sb.append(pairs[i]);
            sb.append('=');
            appendEncoded(sb, pairs[i + 1]);
        }
        return sb.toString();
    }

    /**
     * Create a builder for fluent i18n text construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Decode an i18n text to the best matching language.
     * Non-encoded texts are returned as-is.
     *
     * Resolution order: exact match → "en" fallback → first available → ""
     *
     * @param lang target language code (e.g. "de")
     * @param text the text to decode (may or may not start with ¶)
     * @return the resolved text
     */
    public static String decode(String lang, String text) {
        if (text == null) return "";
        if (!isEncoded(text)) return text;

        String encoded = text.substring(1); // skip ¶
        if (encoded.isEmpty()) return "";

        String targetLang = lang != null ? lang.toLowerCase() : "en";
        if (targetLang.length() > 2) targetLang = targetLang.substring(0, 2);

        String exact = null;
        String english = null;
        String first = null;

        int pos = 0;
        while (pos < encoded.length()) {
            // Find '='
            int eqIdx = encoded.indexOf('=', pos);
            if (eqIdx < 0) break;
            String key = encoded.substring(pos, eqIdx).trim().toLowerCase();

            // Find '&' or end
            int ampIdx = encoded.indexOf('&', eqIdx + 1);
            String rawValue;
            if (ampIdx < 0) {
                rawValue = encoded.substring(eqIdx + 1);
                pos = encoded.length();
            } else {
                rawValue = encoded.substring(eqIdx + 1, ampIdx);
                pos = ampIdx + 1;
            }

            if (key.isEmpty()) continue;

            String value = decodeValue(rawValue);
            if (first == null) first = value;

            if (key.equals(targetLang)) {
                exact = value;
                break; // best match found, no need to continue
            }
            if ("en".equals(key)) {
                english = value;
            }
        }

        if (exact != null) return exact;
        if (english != null) return english;
        if (first != null) return first;
        return "";
    }

    /**
     * Check if a text is an i18n encoded string.
     */
    public static boolean isEncoded(String text) {
        return text != null && !text.isEmpty() && text.charAt(0) == PREFIX;
    }

    private static String decodeValue(String raw) {
        // Fast path: no percent-encoding present
        if (raw.indexOf('%') < 0) return raw;
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private static void appendEncoded(StringBuilder sb, String value) {
        if (value == null) return;
        // Fast path: if value contains no special chars, append directly
        boolean needsEncoding = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&' || c == '=' || c == '%' || c > 127) {
                needsEncoding = true;
                break;
            }
        }
        if (!needsEncoding) {
            sb.append(value);
        } else {
            sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    /**
     * Fluent builder for i18n texts. Reuses a single StringBuilder.
     */
    public static final class Builder {
        private final StringBuilder sb = new StringBuilder(64);
        private boolean hasEntry = false;

        private Builder() {
            sb.append(PREFIX);
        }

        public Builder put(String lang, String value) {
            if (hasEntry) sb.append('&');
            sb.append(lang);
            sb.append('=');
            appendEncoded(sb, value);
            hasEntry = true;
            return this;
        }

        public Builder en(String value) { return put("en", value); }
        public Builder de(String value) { return put("de", value); }
        public Builder fr(String value) { return put("fr", value); }
        public Builder es(String value) { return put("es", value); }
        public Builder it(String value) { return put("it", value); }
        public Builder pt(String value) { return put("pt", value); }
        public Builder ja(String value) { return put("ja", value); }
        public Builder zh(String value) { return put("zh", value); }
        public Builder ko(String value) { return put("ko", value); }
        public Builder ru(String value) { return put("ru", value); }

        public String build() {
            return hasEntry ? sb.toString() : "";
        }
    }
}
