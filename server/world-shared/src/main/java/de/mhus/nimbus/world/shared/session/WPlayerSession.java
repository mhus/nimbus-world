package de.mhus.nimbus.world.shared.session;

import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB Entity for Player Session State.
 * Persists player position and rotation for each world/player combination.
 * This is independent of WSession (Redis) and used for long-term persistence.
 */
@Document(collection = "w_player_sessions")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_player_idx", def = "{ 'worldId': 1, 'playerId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WPlayerSession {

    @Id
    private String id;  // MongoDB internal ID

    /**
     * Full worldId with instance (e.g., "main:terra!abc123").
     * One save per player per world (including instance).
     */
    @Indexed
    private String worldId;

    /**
     * Player ID in format: @userId:characterId
     */
    @Indexed
    private String playerId;

    /**
     * Last known position.
     */
    private Vector3 position;

    /**
     * Last known rotation.
     */
    private Rotation rotation;

    /**
     * Previous worldId before teleportation.
     * Stored when player teleports to allow tracking/return functionality.
     */
    private String previousWorldId;

    /**
     * Previous position before teleportation.
     * Stored when player teleports to allow tracking/return functionality.
     */
    private Vector3 previousPosition;

    /**
     * Previous rotation before teleportation.
     * Stored when player teleports to allow tracking/return functionality.
     */
    private Rotation previousRotation;

    /**
     * Session ID reference for administrative tracking.
     */
    private String sessionId;

    /**
     * Actor type (PLAYER, EDITOR, SUPPORT).
     */
    private String actor;

    @Indexed
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Initialize timestamps for new entity.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        updatedAt = Instant.now();
    }
}
