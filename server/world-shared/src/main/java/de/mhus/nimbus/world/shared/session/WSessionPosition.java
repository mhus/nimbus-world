package de.mhus.nimbus.world.shared.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Player position and rotation data stored separately from WSession.
 * Updated frequently as player moves, stored in Redis with short TTL.
 *
 * This is kept separate from WSession to avoid overhead when updating position
 * frequently (multiple times per second).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WSessionPosition {

    /**
     * Session ID this position belongs to
     */
    private String sessionId;

    /**
     * World coordinates - position
     */
    private Double x;
    private Double y;
    private Double z;

    /**
     * Chunk coordinates (calculated from world position)
     */
    private Integer chunkX;
    private Integer chunkZ;

    /**
     * Rotation - yaw and pitch (in degrees)
     */
    private Double yaw;   // y rotation (horizontal, 0-360)
    private Double pitch; // p rotation (vertical, -90 to 90)

    /**
     * Last update timestamp
     */
    private Instant updatedAt;

    /**
     * Update the timestamp to now
     */
    public void touchUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Check if position data is present
     */
    public boolean hasPosition() {
        return x != null && y != null && z != null;
    }

    /**
     * Check if rotation data is present
     */
    public boolean hasRotation() {
        return yaw != null || pitch != null;
    }

    /**
     * Check if chunk coordinates are present
     */
    public boolean hasChunkCoordinates() {
        return chunkX != null && chunkZ != null;
    }
}
