package de.mhus.nimbus.shared.security;

import java.util.Objects;

/**
 * Identifier for a cryptographic key.
 * <p>
 * A key id is composed of an owner (e.g. tenant, system, application) and a UUID
 * to uniquely identify a particular key version. The concrete format of both
 * fields is up to the implementation, but both must be non-null and non-blank.
 */
public record KeyOwner(String owner) {

    public KeyOwner {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be null or blank");
        }
    }

    /**
     * Creates a KeyId ensuring both components are trimmed.
     */
    public static KeyOwner of(String owner) {
        Objects.requireNonNull(owner, "owner");
        return new KeyOwner(owner.trim());
    }
}
