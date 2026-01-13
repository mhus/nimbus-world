package de.mhus.nimbus.shared.security;

import lombok.Getter;

import java.util.Optional;

@Getter
public class FormattedKey {
    private KeyId keyId;
    private String key;

    public FormattedKey(KeyId keyId, String key) {
        this.keyId = keyId;
        this.key = key;
    }

    public static Optional<FormattedKey> of(KeyId keyId, String key) {
        if (keyId == null || key == null) return Optional.empty();
        return Optional.of(new FormattedKey(keyId, key));
    }

    public String toString() {
        return keyId.toString() + ";" + key;
    }

    public static Optional<FormattedKey> of(String formattedKey) {
        if (formattedKey == null) return Optional.empty();
        var parts = formattedKey.split(";", 4);
        if (parts.length != 4) return Optional.empty();
        var keyId = KeyId.of(parts[0], parts[1], parts[2]);
        return of(keyId, parts[3]);
    }

}
