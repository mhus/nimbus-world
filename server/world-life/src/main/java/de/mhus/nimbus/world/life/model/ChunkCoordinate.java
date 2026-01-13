package de.mhus.nimbus.world.life.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Chunk coordinate wrapper.
 * Represents a chunk's position in the world (cx, cz).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkCoordinate {

    private int cx;
    private int cz;

    /**
     * Parse chunk coordinate from string format "cx:cz".
     *
     * @param chunkKey Chunk key in format "cx:cz" (e.g., "6:-13")
     * @return ChunkCoordinate
     * @throws IllegalArgumentException if format is invalid
     */
    public static ChunkCoordinate fromString(String chunkKey) {
        if (chunkKey == null || chunkKey.isBlank()) {
            throw new IllegalArgumentException("Chunk key cannot be null or empty");
        }

        String[] parts = chunkKey.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid chunk key format: " + chunkKey + " (expected 'cx:cz')");
        }

        try {
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            return new ChunkCoordinate(cx, cz);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid chunk coordinates in key: " + chunkKey, e);
        }
    }

    /**
     * Convert to string format "cx:cz".
     *
     * @return Chunk key string
     */
    public String toKey() {
        return cx + ":" + cz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkCoordinate that = (ChunkCoordinate) o;
        return cx == that.cx && cz == that.cz;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cx, cz);
    }

    @Override
    public String toString() {
        return toKey();
    }
}
