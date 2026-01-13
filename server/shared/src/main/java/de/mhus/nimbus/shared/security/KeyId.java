package de.mhus.nimbus.shared.security;

import java.util.Objects;

/**
 * Identifier for a cryptographic key.
 * <p>
 * A key id is composed of an owner (e.g. tenant, system, application) and a UUID
 * to uniquely identify a particular key version. The concrete format of both
 * fields is up to the implementation, but both must be non-null and non-blank.
 */
public record KeyId(String owner, String intent, String id) {

    public KeyId(KeyOwner owner, String intent, String id) {
        this(owner.owner(), intent, id);
    }

    public KeyId(KeyIntent intent, String id) {
        this(intent.owner(), intent.intent(), id);
    }

    public KeyId {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be null or blank");
        }
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("intent must not be null or blank");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
    }

    public KeyIntent toIntent() {
        return new KeyIntent(owner, intent);
    }

    public KeyOwner toOwner() {
        return new KeyOwner(owner);
    }

    /**
     * Creates a KeyId ensuring both components are trimmed.
     */
    public static KeyId of(String owner, String intent, String id) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(intent, "intent");
        return new KeyId(owner.trim(), intent.trim() ,id.trim());
    }

    public String toString() {
        return owner + ";" + intent + ";" + id;
    }

    /**
     * Creates a KeyId ensuring both components are trimmed.
     */
    public static KeyId of(KeyIntent intent, String id) {
        return of(intent.owner(), intent.intent(), id);
    }

    public static KeyId newOf(KeyIntent intent) {
        return of(intent, java.util.UUID.randomUUID().toString());
    }

}
