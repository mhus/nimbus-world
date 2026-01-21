package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Area;
import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector2Int;
import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import de.mhus.nimbus.shared.utils.TypeUtil;
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

    public enum SIDE {
        NORTH_EAST, // TOP_RIGHT
        EAST, // RIGHT
        SOUTH_EAST, // BOTTOM_RIGHT
        SOUTH_WEST, // BOTTOM_LEFT
        WEST, // LEFT
        NORTH_WEST // TOP_LEFT
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
    private Map<String, String> parameters = new HashMap<>();

    /**
     * Area-specific data maps.
     * Each key is an area, mapping to another map of parameters for this area.
     * Used for defining areas within the grid.
     * Key is an Area of Flat World Coordinates as specified in COORDINATES.md
     *
     * Key format: x,z+sizeX'x'sizeZ (e.g., "0,0+16x16" for a 16x16 area at origin).
     *
     * areas can and should overlap. It will return the smallest matching area found.
     *
     */
    @Builder.Default
    private Map<String, Map<String, String>> areas = new HashMap<>();

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
        this.position = TypeUtil.toStringHexCoord(publicData.getPosition());
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
    public Iterable<Vector2Int> getFlatPositionSet(WWorld worldEntity) {
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

    public HexVector2 getNeighborPosition(SIDE nabor) {
        if (publicData == null || publicData.getPosition() == null) {
            throw new IllegalStateException("Cannot calculate neighbor position: publicData or position is null");
        }
        return HexMathUtil.getNeighborPosition(publicData.getPosition(), nabor);
    }

    public Map<SIDE, HexVector2> getAllNeighborPositions() {
        if (publicData == null || publicData.getPosition() == null) {
            throw new IllegalStateException("Cannot calculate neighbor positions: publicData or position is null");
        }
        Map<SIDE, HexVector2> naborMap = new HashMap<>();
        for (SIDE neighbor : SIDE.values()) {
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
        for (Vector2Int pos : getFlatPositionSet(worldEntity)) {
            // Calculate chunk coordinates
            int cx = Math.floorDiv(pos.getX(), chunkSize);
            int cz = Math.floorDiv(pos.getZ(), chunkSize);
            String chunkKey = cx + ":" + cz;
            chunkKeys.add(chunkKey);
        }

        return chunkKeys;
    }

    /**
     * Get all chunk keys where this hex grid is the dominant hex (has largest overlap).
     * This is an efficient implementation that calculates dominant chunks directly from the hex coordinate.
     *
     * @param worldEntity The world entity containing the hexGridSize and chunkSize configuration
     * @param hexCoord The hex coordinate (typically from publicData.position)
     * @return Set of chunk keys (format: "cx:cz") where this hex is dominant
     */
    public java.util.Set<String> getDominantAffectedChunkKeys(WWorld worldEntity, HexVector2 hexCoord) {
        if (worldEntity == null || worldEntity.getPublicData() == null) {
            throw new IllegalArgumentException("World entity or publicData cannot be null");
        }
        if (hexCoord == null) {
            throw new IllegalArgumentException("hexCoord cannot be null");
        }

        int chunkSize = worldEntity.getPublicData().getChunkSize();
        int gridSize = worldEntity.getPublicData().getHexGridSize();

        return HexMathUtil.getDominantChunkKeysForHex(hexCoord, chunkSize, gridSize);
    }


        // --- Area methods using TypeUtil for key parsing/formatting ---

    /**
     * Returns the parameter map for the given area, or null if not present.
     */
    public Map<String, String> getAreaData(Area area) {
        if (area == null) return null;
        String key = TypeUtil.toStringArea(area);
        return areas.get(key);
    }

    /**
     * Sets the parameter map for the given area (overwrites if already present).
     */
    public void setAreaData(Area area, Map<String, String> data) {
        if (area == null) throw new IllegalArgumentException("Area is null");
        String key = TypeUtil.toStringArea(area);
        areas.put(key, data);
    }

    public void setAreaData(Area area, String key, String value) {
        if (area == null) throw new IllegalArgumentException("Area is null");
        String areaKey = TypeUtil.toStringArea(area);
        Map<String, String> data = areas.get(areaKey);
        if (data == null) {
            data = new HashMap<>();
            areas.put(areaKey, data);
        }
        data.put(key, value);
    }

    public void removeAreaData(Area area, String key) {
        if (area == null) return;
        String areaKey = TypeUtil.toStringArea(area);
        Map<String, String> data = areas.get(areaKey);
        if (data != null) {
            data.remove(key);
        }
    }

    public String getAreaDataValue(Area area, String key) {
        if (area == null) return null;
        String areaKey = TypeUtil.toStringArea(area);
        Map<String, String> data = areas.get(areaKey);
        if (data != null) {
            return data.get(key);
        }
        return null;
    }

    /**
     * Removes the area entry for the given area.
     */
    public void removeArea(Area area) {
        if (area == null) return;
        String key = TypeUtil.toStringArea(area);
        areas.remove(key);
    }

    /**
     * Returns all areas as a Set of Area objects.
     */
    public java.util.Set<Area> getAllAreas() {
        java.util.Set<Area> result = new java.util.HashSet<>();
        for (String key : areas.keySet()) {
            try {
                result.add(TypeUtil.parseArea(key));
            } catch (Exception ignore) {}
        }
        return result;
    }

    /**
     * Returns the smallest matching area (by area size) that contains the given area.
     * Returns null if no area contains the given area.
     */
    public Area getSmallestMatchingArea(Area area) {
        if (area == null) return null;
        Area best = null;
        int bestSize = Integer.MAX_VALUE;
        for (String key : areas.keySet()) {
            try {
                Area candidate = TypeUtil.parseArea(key);
                if (containsArea(candidate, area)) {
                    int size = candidate.getSize().getX() * candidate.getSize().getZ();
                    if (size < bestSize) {
                        best = candidate;
                        bestSize = size;
                    }
                }
            } catch (Exception ignore) {}
        }
        return best;
    }

    /**
     * Returns true if outer contains inner (flat world coordinates, inclusive).
     */
    private static boolean containsArea(Area outer, Area inner) {
        int ox = outer.getPosition().getX();
        int oz = outer.getPosition().getZ();
        int osx = outer.getSize().getX();
        int osz = outer.getSize().getZ();
        int ix = inner.getPosition().getX();
        int iz = inner.getPosition().getZ();
        int isx = inner.getSize().getX();
        int isz = inner.getSize().getZ();
        return ix >= ox && iz >= oz && (ix + isx) <= (ox + osx) && (iz + isz) <= (oz + osz);
    }

    /**
     * Returns the parameter map for the smallest matching area containing the given x,z point (flat world coordinates).
     * Returns null if no area contains the point.
     */
    public Map<String, String> getAreaData(int x, int z) {
        Area pointArea = Area.builder()
            .position(de.mhus.nimbus.generated.types.Vector3Int.builder().x(x).y(0).z(z).build())
            .size(de.mhus.nimbus.generated.types.Vector3Int.builder().x(1).y(1).z(1).build())
            .build();
        Area match = getSmallestMatchingArea(pointArea);
        if (match == null) return null;
        return getAreaData(match);
    }

    /**
     * Returns a map of all areas (as Area objects) that overlap with the given chunk (cx,cz).
     * The returned map is a subset of the 'areas' map: key is the Area object, value is the parameter map.
     */
    public java.util.Map<Area, Map<String, String>> findAreasForChunk(WWorld world, int cx, int cz) {
        java.util.Map<Area, Map<String, String>> result = new java.util.HashMap<>();
        int chunkSize = world.getPublicData().getChunkSize(); // Default, ideally from world config
        // Try to get chunk size from parameters if available
        try {
            if (parameters != null && parameters.containsKey("chunkSize")) {
                chunkSize = Integer.parseInt(parameters.get("chunkSize"));
            }
        } catch (Exception ignore) {}
        int minX = cx * chunkSize;
        int minZ = cz * chunkSize;
        int maxX = (cx + 1) * chunkSize - 1;
        int maxZ = (cz + 1) * chunkSize - 1;
        for (Map.Entry<String, Map<String, String>> entry : areas.entrySet()) {
            try {
                Area area = TypeUtil.parseArea(entry.getKey());
                int ax = area.getPosition().getX();
                int az = area.getPosition().getZ();
                int asx = area.getSize().getX();
                int asz = area.getSize().getZ();
                int amaxX = ax + asx - 1;
                int amaxZ = az + asz - 1;
                boolean overlap = (minX <= amaxX && maxX >= ax && minZ <= amaxZ && maxZ >= az);
                if (overlap) result.put(area, entry.getValue());
            } catch (Exception ignore) {}
        }
        return result;
    }

    /**
     * Returns a map of all areas (as Area objects) whose parameter map contains the given key-value pair.
     * The returned map is a subset of the 'areas' map: key is the Area object, value is the parameter map.
     */
    public java.util.Map<Area, Map<String, String>> findAreasForParameter(String key, String value) {
        java.util.Map<Area, Map<String, String>> result = new java.util.HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : areas.entrySet()) {
            Map<String, String> data = entry.getValue();
            if (data != null && value != null && value.equals(data.get(key))) {
                try {
                    Area area = TypeUtil.parseArea(entry.getKey());
                    result.put(area, data);
                } catch (Exception ignore) {}
            }
        }
        return result;
    }

    /**
     * Returns a map of all areas (as Area objects) whose area contains the given world coordinate (x,z).
     * The returned map is a subset of the 'areas' map: key is the Area object, value is the parameter map.
     */
    public java.util.Map<Area, Map<String, String>> findAreasForWorldCoord(int x, int z) {
        java.util.Map<Area, Map<String, String>> result = new java.util.HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : areas.entrySet()) {
            try {
                Area area = TypeUtil.parseArea(entry.getKey());
                int ax = area.getPosition().getX();
                int az = area.getPosition().getZ();
                int asx = area.getSize().getX();
                int asz = area.getSize().getZ();
                int amaxX = ax + asx - 1;
                int amaxZ = az + asz - 1;
                boolean contains = (x >= ax && x <= amaxX && z >= az && z <= amaxZ);
                if (contains) result.put(area, entry.getValue());
            } catch (Exception ignore) {}
        }
        return result;
    }
}
