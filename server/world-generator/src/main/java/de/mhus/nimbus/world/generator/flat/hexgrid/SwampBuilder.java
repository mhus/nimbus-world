package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.manipulator.HillyTerrainManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Swamp scenario builder.
 * Creates swampy terrain with water-filled valleys.
 * Uses HillyTerrainManipulator for base terrain and fills enclosed valleys with water.
 * <p>
 * Optional parameters:
 * - swampDepth: Depth of water in valleys (default: 3)
 * - stoneOffset: Height offset from ocean level where stone starts (default: 20)
 * - snowOffset: Height offset from ocean level where snow starts (default: 50)
 * - sandMaterial: Material for areas at/below ocean level (default: SAND or 4)
 * - grassMaterial: Material for low elevations (default: GRASS or 1)
 * - stoneMaterial: Material for medium elevations (default: STONE or 3)
 * - snowMaterial: Material for high elevations (default: SNOW or 7)
 */
@Slf4j
public class SwampBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.debug("Building swamp scenario for flat: {}", flat.getFlatId());

        int seaLevel = flat.getSeaLevel();

        // Use getHexGridLevel() as baseHeight and getLandOffset() as hillHeight
        int hillHeight = getOffset();
        int baseHeight = getHexGridAsl();

        long seed = context.getWorld().getNoiseSeed();
        double frequency = CastUtil.todouble(parameters.getOrDefault(HillyTerrainManipulator.PARAM_FREQUENCY, "1.0"), 1d);

        log.debug("Swamp terrain generation: baseHeight={}, hillHeight={}, seaLevel={}, seed={}, frequency={}",
                baseHeight, hillHeight, seaLevel, seed, frequency);

        // Build parameters for HillyTerrainManipulator
        Map<String, String> hillyParams = new HashMap<>();
        hillyParams.put(HillyTerrainManipulator.PARAM_BASE_HEIGHT, String.valueOf(baseHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_HILL_HEIGHT, String.valueOf(hillHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_SEED, String.valueOf(seed));
        hillyParams.put(HillyTerrainManipulator.PARAM_FREQUENCY, String.valueOf(frequency));

        // Use HillyTerrainManipulator to generate base swamp terrain
        context.getManipulatorService().executeManipulator(
                HillyTerrainManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                hillyParams
        );

        // Set materials based on height
        setSwampMaterials(flat, seaLevel);

        // Find and fill enclosed valleys with water
        int swampDepth = parseIntParameter(parameters, "swampDepth", 3);
        fillEnclosedValleys(flat, seaLevel, swampDepth);

        log.debug("Swamp scenario completed: baseHeight={}, hillHeight={}, oceanLevel={}, swampDepth={}",
                baseHeight, hillHeight, seaLevel, swampDepth);
    }

    /**
     * Set materials based on height.
     * Similar to MountainBuilder but optimized for swamp terrain.
     * Optional groundType parameter can override material settings.
     */
    private void setSwampMaterials(WFlat flat, int oceanLevel) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Apply ground type if specified (overrides individual material settings)
        applyGroundTypeIfPresent();

        // Get material thresholds from parameters (with defaults)
        int stoneOffset = parseIntParameter(parameters, "stoneOffset", 20);
        int snowOffset = parseIntParameter(parameters, "snowOffset", 50);

        // Get materials from parameters (with defaults)
        int sandMaterial = parseMaterialParameter(parameters, "sandMaterial", FlatMaterialService.SAND);
        int grassMaterial = parseMaterialParameter(parameters, "grassMaterial", FlatMaterialService.GRASS);
        int stoneMaterial = parseMaterialParameter(parameters, "stoneMaterial", FlatMaterialService.STONE);
        int snowMaterial = parseMaterialParameter(parameters, "snowMaterial", FlatMaterialService.SNOW);

        int grassToStoneThreshold = oceanLevel + stoneOffset;
        int snowThreshold = oceanLevel + snowOffset;

        log.debug("Material thresholds: stone={}, snow={} (oceanLevel={})",
                grassToStoneThreshold, snowThreshold, oceanLevel);
        log.debug("Materials: sand={}, grass={}, stone={}, snow={}",
                sandMaterial, grassMaterial, stoneMaterial, snowMaterial);

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int level = flat.getLevel(x, z);

                int material;
                if (level >= snowThreshold) {
                    material = snowMaterial;
                } else if (level >= grassToStoneThreshold) {
                    material = stoneMaterial;
                } else if (level <= oceanLevel) {
                    material = sandMaterial;
                } else {
                    material = grassMaterial;
                }

                flat.setColumn(x, z, material);
            }
        }
    }

    /**
     * Find enclosed valleys and fill them with water.
     * A valley is enclosed if it's surrounded by higher terrain and doesn't reach the edge.
     */
    private void fillEnclosedValleys(WFlat flat, int seaLevel, int swampDepth) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        log.debug("Searching for enclosed valleys to fill with water");

        // Track which positions have been processed
        boolean[][] processed = new boolean[sizeX][sizeZ];

        int valleysFound = 0;
        int valleysFilled = 0;

        // Scan terrain for potential valley starting points
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                if (processed[x][z]) continue;

                int level = flat.getLevel(x, z);

                // Skip if at or below sea level (will be flooded anyway)
                if (level <= seaLevel) continue;

                // Skip if material is UNKNOWN
                int material = flat.getColumn(x, z);
                if (material == FlatMaterialService.UNKNOWN_PROTECTED ||
                    material == FlatMaterialService.UNKNOWN_NOT_PROTECTED) {
                    continue;
                }

                // Try to find an enclosed valley starting from this point
                ValleyInfo valley = findEnclosedValley(flat, x, z, processed, seaLevel);

                if (valley != null && valley.isEnclosed) {
                    valleysFound++;
                    log.debug("Found enclosed valley at ({}, {}): minLevel={}, maxLevel={}, size={}",
                            x, z, valley.minLevel, valley.maxLevel, valley.positions.size());

                    // Fill valley with water if conditions are met
                    if (valley.minLevel > seaLevel) {
                        fillValleyWithWater(flat, valley, swampDepth, seaLevel);
                        valleysFilled++;
                    } else {
                        log.debug("Valley at ({}, {}) is below sea level, skipping", x, z);
                    }
                }
            }
        }

        log.debug("Valley search complete: found={}, filled={}", valleysFound, valleysFilled);
    }

    /**
     * Find an enclosed valley starting from a given position using flood-fill.
     */
    private ValleyInfo findEnclosedValley(WFlat flat, int startX, int startZ, boolean[][] processed, int seaLevel) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        ValleyInfo valley = new ValleyInfo();
        valley.positions = new ArrayList<>();
        valley.isEnclosed = true;

        Queue<int[]> queue = new LinkedList<>();
        boolean[][] visited = new boolean[sizeX][sizeZ];

        int startLevel = flat.getLevel(startX, startZ);
        valley.minLevel = startLevel;
        valley.maxLevel = startLevel;

        queue.add(new int[]{startX, startZ});
        visited[startX][startZ] = true;

        // Flood-fill to find all connected positions at similar height
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int x = pos[0];
            int z = pos[1];

            int level = flat.getLevel(x, z);
            valley.positions.add(new int[]{x, z, level});
            processed[x][z] = true;

            valley.minLevel = Math.min(valley.minLevel, level);
            valley.maxLevel = Math.max(valley.maxLevel, level);

            // Check all 4 neighbors
            int[][] neighbors = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};

            for (int[] neighbor : neighbors) {
                int nx = neighbor[0];
                int nz = neighbor[1];

                // If we reach the edge, valley is not enclosed
                if (nx < 0 || nx >= sizeX || nz < 0 || nz >= sizeZ) {
                    valley.isEnclosed = false;
                    continue;
                }

                if (visited[nx][nz]) continue;

                int neighborLevel = flat.getLevel(nx, nz);
                int neighborMaterial = flat.getColumn(nx, nz);

                // If neighbor is UNKNOWN material, valley is not properly enclosed
                if (neighborMaterial == FlatMaterialService.UNKNOWN_PROTECTED ||
                    neighborMaterial == FlatMaterialService.UNKNOWN_NOT_PROTECTED) {
                    valley.isEnclosed = false;
                    continue;
                }

                // If neighbor is at similar or lower height, include it in the valley
                if (neighborLevel <= startLevel + 2) {
                    visited[nx][nz] = true;
                    queue.add(new int[]{nx, nz});
                }
            }
        }

        // Only return valley if it's enclosed and has a reasonable size
        if (valley.isEnclosed && valley.positions.size() >= 5) {
            return valley;
        }

        return null;
    }

    /**
     * Fill a valley with water using ExtraBlocks.
     * Only the top layer (minLevel to minLevel + swampDepth) is filled.
     */
    private void fillValleyWithWater(WFlat flat, ValleyInfo valley, int swampDepth, int seaLevel) {
        log.debug("Filling valley with water: minLevel={}, maxLevel={}, depth={}, positions={}",
                valley.minLevel, valley.maxLevel, swampDepth, valley.positions.size());

        // Determine water level (top of the swamp)
        int waterLevel = Math.min(valley.minLevel + swampDepth, valley.maxLevel);

        // Make sure we don't go below sea level
        if (waterLevel <= seaLevel) {
            log.debug("Water level would be at/below sea level, skipping");
            return;
        }

        // Get water block definition
        String waterBlockDef = getWaterBlockDef(flat);
        if (waterBlockDef == null) {
            log.warn("No water block definition found, skipping valley fill");
            return;
        }

        // Fill only the top layer with water
        for (int[] pos : valley.positions) {
            int x = pos[0];
            int z = pos[1];
            int groundLevel = pos[2];

            // Only fill if ground is below water level
            if (groundLevel < waterLevel) {
                // Set water at the water level
                flat.setExtraBlock(x, z, waterLevel, waterBlockDef);
            }
        }

        log.debug("Valley filled with water at level {}", waterLevel);
    }

    /**
     * Get water block definition from flat material service.
     */
    private String getWaterBlockDef(WFlat flat) {
        WFlat.MaterialDefinition waterMaterial = flat.getMaterial((byte) FlatMaterialService.WATER);
        if (waterMaterial != null) {
            return waterMaterial.getBlockDef();
        }
        // Fallback to nimbus default if no material definition found
        return "n:w";
    }

    @Override
    protected int getDefaultOffset() {
        return 10;  // SWAMP: moderate variation for rolling hills and valleys
    }

    @Override
    protected int getDefaultAsl() {
        return 5;  // SWAMP: low elevation, slightly above sea level
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getCenterAsl();
    }

    private int parseIntParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid int parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Parse material parameter. Accepts either material name (e.g. "SNOW", "GRASS") or material ID (e.g. "7", "1").
     */
    private int parseMaterialParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }

        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        // Try to parse as integer first
        try {
            int materialId = Integer.parseInt(value);
            if (materialId >= 0 && materialId <= 255) {
                return materialId;
            }
            log.warn("Material ID out of range (0-255) for '{}': {}, using default: {}", name, value, defaultValue);
            return defaultValue;
        } catch (NumberFormatException e) {
            // Not a number, try to parse as material name
            return parseMaterialName(value, name, defaultValue);
        }
    }

    /**
     * Parse material name to material ID.
     */
    private int parseMaterialName(String name, String paramName, int defaultValue) {
        switch (name.toUpperCase().trim()) {
            case "GRASS":
                return FlatMaterialService.GRASS;
            case "DIRT":
                return FlatMaterialService.DIRT;
            case "STONE":
                return FlatMaterialService.STONE;
            case "SAND":
                return FlatMaterialService.SAND;
            case "WATER":
                return FlatMaterialService.WATER;
            case "BEDROCK":
                return FlatMaterialService.BEDROCK;
            case "SNOW":
                return FlatMaterialService.SNOW;
            case "INVISIBLE":
                return FlatMaterialService.INVISIBLE;
            case "INVISIBLE_SOLID":
                return FlatMaterialService.INVISIBLE_SOLID;
            case "DESERT_SAND":
                return FlatMaterialService.DESERT_SAND;
            case "SWAMP":
                return FlatMaterialService.SWAMP;
            case "ICE":
                return FlatMaterialService.ICE;
            default:
                log.warn("Unknown material name for '{}': {}, using default: {}", paramName, name, defaultValue);
                return defaultValue;
        }
    }

    /**
     * Information about a valley.
     */
    private static class ValleyInfo {
        List<int[]> positions;  // [x, z, level]
        int minLevel;
        int maxLevel;
        boolean isEnclosed;
    }

    /**
     * Apply ground type materials if groundType parameter is present.
     * This allows direct specification of ground type in builder parameters.
     */
    private void applyGroundTypeIfPresent() {
        if (parameters == null || !parameters.containsKey("groundType")) {
            return;
        }

        String groundTypeStr = parameters.get("groundType");
        if (groundTypeStr == null || groundTypeStr.isBlank()) {
            return;
        }

        try {
            de.mhus.nimbus.world.generator.composer.biome.GroundType groundType =
                de.mhus.nimbus.world.generator.composer.biome.GroundType.valueOf(groundTypeStr.toUpperCase());
            groundType.applyToParameters(parameters);
            log.debug("Applied ground type: {}", groundType);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ground type '{}', using defaults", groundTypeStr);
        }
    }
}
