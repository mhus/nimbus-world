package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
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
import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB Entity for hexagonal grid areas in the world.
 * Each hex grid represents a pentagonal/hexagonal area that divides the world into regions.
 * The grid uses axial coordinates (q, r) with pointy-top orientation.
 */
@Document(collection = "w_hexgrids")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_position_idx", def = "{ 'worldId': 1, 'position': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WHexGrid implements Identifiable {

    public enum NEIGHBOR {
        TOP_RIGHT,
        RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
        LEFT,
        TOP_LEFT
    }

    @Id
    private String id;

    /**
     * World identifier where this hex grid exists.
     */
    @Indexed
    private String worldId;

    /**
     * Position key in format "q:r" (e.g., "0:0", "-1:2").
     * Derived from publicData.position and indexed for efficient queries.
     */
    @Indexed
    private String position;

    /**
     * Public data containing the HexGrid DTO with position, name, description, icon, and entryPoint.
     * This is what gets serialized and sent to clients.
     */
    private HexGrid publicData;

    /**
     * Generator configuration parameters for this hex grid area.
     * Used by world generators to configure terrain/biome generation.
     */
    @Builder.Default
    private Map<String, String> generatorParameters = new HashMap<>();

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Soft delete flag.
     */
    @Indexed
    @Builder.Default
    private boolean enabled = true;

    /**
     * Initialize timestamps for new hex grid.
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
     * Synchronizes the position field from publicData.position.
     * This should be called before saving to ensure consistency.
     *
     * @throws IllegalStateException if publicData or publicData.position is null
     */
    public void syncPositionKey() {
        if (publicData == null || publicData.getPosition() == null) {
            throw new IllegalStateException("Cannot sync position key: publicData or position is null");
        }
        this.position = HexMathUtil.positionKey(publicData.getPosition());
    }

    /**
     * Calculates all flat positions (blocks) within this hexagonal grid area.
     * Returns an iterator that lazily generates positions on-demand for memory efficiency.
     * The number of blocks depends on the gridSize (diameter) configured in the world.
     *
     * @param worldEntity The world entity containing the hexGridSize configuration
     * @return Iterable over FlatPosition objects within this hex grid
     * @throws IllegalStateException if publicData or position is null
     * @throws IllegalArgumentException if worldEntity has no publicData or hexGridSize
     */
    public Iterable<FlatPosition> getFlatPositionSet(WWorld worldEntity) {
        if (publicData == null || publicData.getPosition() == null) {
            throw new IllegalStateException("Cannot calculate positions: publicData or position is null");
        }
        if (worldEntity == null || worldEntity.getPublicData() == null) {
            throw new IllegalArgumentException("World entity or publicData cannot be null");
        }

        int gridSize = worldEntity.getPublicData().getHexGridSize();
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Invalid hexGridSize: " + gridSize + " (must be positive)");
        }

        return () -> HexMathUtil.createFlatPositionIterator(publicData.getPosition(), gridSize);
    }

    public HexVector2 getNeighborPosition(NEIGHBOR nabor) {
        if (publicData == null || publicData.getPosition() == null) {
            throw new IllegalStateException("Cannot calculate neighbor position: publicData or position is null");
        }
        return HexMathUtil.getNeighborPosition(publicData.getPosition(), nabor);
    }

    public Map<NEIGHBOR, HexVector2> getAllNeighborPositions() {
        if (publicData == null || publicData.getPosition() == null) {
            throw new IllegalStateException("Cannot calculate neighbor positions: publicData or position is null");
        }
        Map<NEIGHBOR, HexVector2> naborMap = new HashMap<>();
        for (NEIGHBOR neighbor : NEIGHBOR.values()) {
            naborMap.put(neighbor, HexMathUtil.getNeighborPosition(publicData.getPosition(), neighbor));
        }
        return naborMap;
    }

    /**
     * Get all affected chunk keys for this hex grid.
     * Calculates all flat positions within the hex and converts them to chunk keys.
     *
     * @param worldEntity The world entity containing the hexGridSize and chunkSize configuration
     * @return Set of affected chunk keys (format: "cx:cz")
     */
    public java.util.Set<String> getAffectedChunkKeys(WWorld worldEntity) {
        if (worldEntity == null || worldEntity.getPublicData() == null) {
            throw new IllegalArgumentException("World entity or publicData cannot be null");
        }

        int chunkSize = worldEntity.getPublicData().getChunkSize();
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Invalid chunkSize: " + chunkSize + " (must be positive)");
        }

        java.util.Set<String> chunkKeys = new java.util.HashSet<>();

        // Iterate over all flat positions in this hex grid
        for (FlatPosition pos : getFlatPositionSet(worldEntity)) {
            // Calculate chunk coordinates
            int cx = Math.floorDiv(pos.x(), chunkSize);
            int cz = Math.floorDiv(pos.z(), chunkSize);
            String chunkKey = cx + ":" + cz;
            chunkKeys.add(chunkKey);
        }

        return chunkKeys;
    }
}
