package de.mhus.nimbus.shared.types;

import lombok.Getter;

import java.util.Optional;

/**
 * UserId represents a unique identifier for a user in the format "userId".
 * UserId is a string of 'a-zA-Z0-9_-' from 2 to 64 characters.
 */
public class UserId implements Comparable<UserId> {

    @Getter
    private String id;

    private UserId(String id) {
        this.id = id;
    }

    public static Optional<UserId> of(String id) {
        if (id == null) return Optional.empty();
        if (!validate(id)) return Optional.empty();
        return Optional.of(new UserId(id));
    }

    private static boolean validate(String id) {
        if (id == null || id.length() < 2) return false;
        return id.matches("[a-zA-Z0-9_\\-]{2,64}");
    }

    public String toString() {
        return id;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UserId other = (UserId) obj;
        return id.equals(other.id);
    }

    @Override
    public int compareTo(UserId o) {
        return this.id.compareTo(o.id);
    }
}
