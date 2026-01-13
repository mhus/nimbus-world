package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import de.mhus.nimbus.shared.types.WorldId;
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
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Entity for World Instances.
 * World Instances are temporary or persistent copies of worlds that can be played independently.
 * Each instance is based on a world and can have its own set of players.
 */
@Document(collection = "w_world_instances")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_instance_idx", def = "{ 'worldId': 1, 'instanceId': 1 }"),
        @CompoundIndex(name = "creator_idx", def = "{ 'creator': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("entities")
public class WWorldInstance implements Identifiable {

    @Id
    @TypeScript(ignore = true)
    private String id;

    /**
     * Unique identifier for this instance.
     * This is the technical instance ID used to reference this specific instance.
     */
    @Indexed(unique = true)
    private String instanceId;

    /**
     * The worldId this instance is based on.
     * References the WWorld entity that serves as the template for this instance.
     */
    @Indexed
    private String worldId;

    /**
     * Display title for the instance.
     */
    private String title;

    /**
     * Description of the instance.
     */
    private String description;

    /**
     * PlayerId of the creator of this instance.
     * The creator has special permissions and can manage the instance.
     */
    @Indexed
    private String creator;

    /**
     * List of PlayerIds that are allowed to play in this instance.
     * Empty list means no restrictions (public instance).
     */
    @Builder.Default
    private List<String> players = new ArrayList<>();

    /**
     * List of PlayerIds currently active in this instance.
     * Used to track active sessions and cleanup when empty.
     */
    @Builder.Default
    private List<String> activePlayers = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Last time a player accessed this instance.
     * Updated when players join or leave.
     */
    private Instant lastAccessTime;

    /**
     * Soft delete flag.
     */
    @Indexed
    @Builder.Default
    private boolean enabled = true;

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

    /**
     * Check if a player is allowed to access this instance.
     * Creator always has access.
     * If players list is empty, instance is public and everyone has access.
     *
     * @param playerId The playerId to check
     * @return true if player has access, false otherwise
     */
    public boolean isPlayerAllowed(String playerId) {
        if (playerId == null) {
            return false;
        }

        // Creator always has access
        if (playerId.equals(creator)) {
            return true;
        }

        // Empty list means public instance
        if (players == null || players.isEmpty()) {
            return true;
        }

        // Check if player is in the list
        return players.contains(playerId);
    }

    /**
     * Add a player to the instance.
     *
     * @param playerId The playerId to add
     * @return true if player was added, false if already present
     */
    public boolean addPlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }

        if (players == null) {
            players = new ArrayList<>();
        }

        if (players.contains(playerId)) {
            return false;
        }

        players.add(playerId);
        return true;
    }

    /**
     * Remove a player from the instance.
     *
     * @param playerId The playerId to remove
     * @return true if player was removed, false if not present
     */
    public boolean removePlayer(String playerId) {
        if (playerId == null || players == null) {
            return false;
        }

        return players.remove(playerId);
    }

    /**
     * Add a player to the active players list.
     *
     * @param playerId The playerId to add
     * @return true if player was added, false if already present
     */
    public boolean addActivePlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }

        if (activePlayers == null) {
            activePlayers = new ArrayList<>();
        }

        if (activePlayers.contains(playerId)) {
            return false;
        }

        activePlayers.add(playerId);
        lastAccessTime = Instant.now();
        return true;
    }

    /**
     * Remove a player from the active players list.
     *
     * @param playerId The playerId to remove
     * @return true if player was removed, false if not present
     */
    public boolean removeActivePlayer(String playerId) {
        if (playerId == null || activePlayers == null) {
            return false;
        }

        boolean removed = activePlayers.remove(playerId);
        if (removed) {
            lastAccessTime = Instant.now();
        }
        return removed;
    }

    /**
     * Check if there are any active players in this instance.
     *
     * @return true if no active players, false otherwise
     */
    public boolean hasNoActivePlayers() {
        return activePlayers == null || activePlayers.isEmpty();
    }

    /**
     * Get the count of active players.
     *
     * @return Number of active players
     */
    public int getActivePlayerCount() {
        return activePlayers == null ? 0 : activePlayers.size();
    }

    /**
     * Get the full worldId including instance part.
     * Combines worldId and instanceId into format: worldId!instance
     *
     * @return Full worldId with instance (e.g., "main:terra!abc123")
     */
    public String getWorldWithInstanceId() {
        if (instanceId == null || instanceId.isBlank()) {
            return worldId;
        }
        // Parse instanceId to extract instance part only (format: worldId!instance)
        WorldId parsed = WorldId.unchecked(instanceId);
        if (parsed.isInstance()) {
            // instanceId already contains full format
            return instanceId;
        }
        // If instanceId doesn't contain '!', construct it
        return WorldId.unchecked(worldId).withInstance(instanceId).getId();
    }
}
