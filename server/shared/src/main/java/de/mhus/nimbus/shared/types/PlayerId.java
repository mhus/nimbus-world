package de.mhus.nimbus.shared.types;

import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

import java.util.Optional;

/**
 * PlayerId represents a unique identifier for a player in the format "@userId:charachterId".
 * Each part is a string 'a-zA-Z0-9_-' from 3 to 64 characters.
 */
public class PlayerId {
    @Getter
    private String id;
    private String userId;
    private String characterId;

    private PlayerId(String id) {
        this.id = id;
    }

    public String getUserId() {
        if (userId == null) {
            parseId();
        }
        return userId;
    }

    public UserId getUserIdAsUserId() {
        return UserId.of(getUserId()).orElseThrow(() -> new IllegalStateException("Invalid userId in playerId: " + id));
    }

    public String getCharacterId() {
        if (userId == null) {
            parseId();
        }
        return characterId;
    }

    /**
     * Returns the raw id without the leading "@".
     */
    public String getRawId() {
        return id.substring(1);
    }

    private void parseId() {
        var parts = id.substring(1).split(":", 3); // remove @ and one more for garbage
        userId = parts[0];
        characterId = parts[1];
    }

    public static Optional<PlayerId> of(String userId, String characterId) {
        if (userId == null || characterId == null) return Optional.empty();
        var id = "@" + userId + ":" + characterId;
        if (!validate(id)) return Optional.empty();
        return Optional.of(new PlayerId(id));
    }

    public static Optional<PlayerId> of(String id) {
        if (id == null) return Optional.empty();
        if (!id.startsWith("@")) id = "@" + id;
        if (!validate(id)) return Optional.empty();
        return Optional.of(new PlayerId(id));
    }

    public static boolean validate(String id) {
        if (Strings.isBlank(id)) return false;
        if (id.length() < 3) return false;
        return id.matches("@[a-zA-Z0-9_\\-]{2,64}:[a-zA-Z0-9_\\-]{2,64}");
    }

    public String toString() {
        return id;
    }

}
