package de.mhus.nimbus.world.life.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity ownership metadata for multi-pod coordination.
 * Tracks which pod owns simulation of a specific entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityOwnership {

    /**
     * Entity identifier.
     */
    private String entityId;

    /**
     * World ID where entity exists.
     */
    private String worldId;

    /**
     * Pod ID that owns this entity.
     * Typically the Kubernetes hostname or configured pod identifier.
     */
    private String podId;

    /**
     * Timestamp when ownership was initially claimed (milliseconds).
     */
    private long claimTimestamp;

    /**
     * Timestamp of last ownership heartbeat (milliseconds).
     * Updated every 5 seconds while pod owns the entity.
     */
    private long lastHeartbeat;

    /**
     * Current chunk where entity is located.
     * Format: "cx:cz" (e.g., "6:-13")
     */
    private String currentChunk;

    /**
     * Check if ownership is stale (no heartbeat for threshold duration).
     * Stale ownership indicates the owning pod may have crashed or disconnected.
     *
     * @param currentTime Current timestamp (milliseconds)
     * @param staleThresholdMs Stale threshold in milliseconds
     * @return True if ownership is stale
     */
    public boolean isStale(long currentTime, long staleThresholdMs) {
        return (currentTime - lastHeartbeat) > staleThresholdMs;
    }

    /**
     * Refresh heartbeat timestamp to current time.
     */
    public void touch() {
        this.lastHeartbeat = System.currentTimeMillis();
    }
}
