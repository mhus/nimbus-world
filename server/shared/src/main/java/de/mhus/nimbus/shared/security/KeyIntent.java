package de.mhus.nimbus.shared.security;

import java.util.Objects;

/**
 * Identifier for a cryptographic key.
 * <p>
 * A key id is composed of an owner (e.g. tenant, system, application) and a UUID
 * to uniquely identify a particular key version. The concrete format of both
 * fields is up to the implementation, but both must be non-null and non-blank.
 */
public record KeyIntent(String owner, String intent) {

    public static final String MAIN_JWT_TOKEN = "main-jwt-token";
    public static final String REGION_SERVER_JWT_TOKEN = "region-server-jwt-token";
    public static final String REGION_JWT_TOKEN = "region-jwt-token";

    public KeyIntent(KeyOwner owner, String intent) {
        this(owner.owner(), intent);
    }

    public KeyIntent {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be null or blank");
        }
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("intent must not be null or blank");
        }
    }

    /**
     * Creates a KeyId ensuring both components are trimmed.
     */
    public static KeyIntent of(String owner, String intent) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(intent, "intent");
        return new KeyIntent(owner.trim(), intent.trim());
    }
}
