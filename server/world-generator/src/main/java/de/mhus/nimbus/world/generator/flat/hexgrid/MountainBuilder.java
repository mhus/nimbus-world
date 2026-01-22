package de.mhus.nimbus.world.generator.flat.hexgrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.HillyTerrainManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mountain scenario builder.
 * Creates mountainous terrain with optional ridges along specified sides.
 * Uses HillyTerrainManipulator for base terrain and applies height transformations
 * to create mountain ridges at specified edges.
 * <p>
 * Ridge parameter format in HexGrid:
 * ridge=[
 *   {
 *     side: "NE",
 *     level: 100
 *   },
 *   {
 *     side: "E",
 *     level: 90
 *   }
 * ]
 * <p>
 * Optional parameters:
 * - ridgeWidth: Width of ridge effect in pixels (default: 200)
 */
@Slf4j
public class MountainBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_RIDGE_WIDTH = 200;  // Default width of ridge effect in pixels

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Building mountain scenario for flat: {}", flat.getFlatId());

        int oceanLevel = flat.getOceanLevel();

        // Use getHexGridLevel() as baseHeight and getLandOffset() as hillHeight
        int hillHeight = getLandOffset();
        int baseHeight = getHexGridLevel();

        long seed = parseLongParameter(parameters, "seed", System.currentTimeMillis());

        log.debug("Mountain terrain generation: baseHeight={}, hillHeight={}, oceanLevel={}, seed={}",
                baseHeight, hillHeight, oceanLevel, seed);

        // Build parameters for HillyTerrainManipulator
        Map<String, String> hillyParams = new HashMap<>();
        hillyParams.put(HillyTerrainManipulator.PARAM_BASE_HEIGHT, String.valueOf(baseHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_HILL_HEIGHT, String.valueOf(hillHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_SEED, String.valueOf(seed));

        // Use HillyTerrainManipulator to generate base mountain terrain
        context.getManipulatorService().executeManipulator(
                HillyTerrainManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                hillyParams
        );

        // Get ridge width from parameters (default: 200)
        int ridgeWidth = parseIntParameter(parameters, "ridgeWidth", DEFAULT_RIDGE_WIDTH);
        log.debug("Using ridge width: {}", ridgeWidth);

        // Parse and apply ridge transformations if defined
        applyRidgeTransformations(flat, baseHeight, hillHeight, ridgeWidth);

        // Set materials based on height
        setMountainMaterials(flat, oceanLevel);

        log.info("Mountain scenario completed: baseHeight={}, hillHeight={}, oceanLevel={}",
                baseHeight, hillHeight, oceanLevel);
    }

    /**
     * Parse ridge definitions and apply height transformations.
     */
    private void applyRidgeTransformations(WFlat flat, int baseHeight, int hillHeight, int ridgeWidth) {
        WHexGrid hexGrid = context.getHexGrid();
        String ridgeParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("ridge") : null;

        if (ridgeParam == null || ridgeParam.isBlank()) {
            log.debug("No ridge parameter found, skipping ridge transformations");
            return;
        }

        try {
            List<RidgeDefinition> ridges = parseRidgeDefinitions(ridgeParam);
            log.debug("Parsed {} ridge definitions", ridges.size());

            // Apply ridge transformations to height map
            for (RidgeDefinition ridge : ridges) {
                applyRidgeTransformation(flat, ridge, baseHeight, hillHeight, ridgeWidth);
            }

        } catch (Exception e) {
            log.error("Failed to parse or apply ridge definitions", e);
        }
    }

    /**
     * Parse ridge definitions from JSON array.
     */
    private List<RidgeDefinition> parseRidgeDefinitions(String ridgeParam) throws Exception {
        JsonNode root = objectMapper.readTree(ridgeParam);
        List<RidgeDefinition> ridges = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode ridgeNode : root) {
                RidgeDefinition ridge = new RidgeDefinition();
                ridge.setSide(parseSide(ridgeNode.get("side").asText()));
                ridge.setLevel(ridgeNode.get("level").asInt());
                ridges.add(ridge);
            }
        }

        return ridges;
    }

    /**
     * Parse side string to SIDE enum.
     */
    private WHexGrid.SIDE parseSide(String sideStr) {
        switch (sideStr.toUpperCase()) {
            case "NW":
            case "NORTH_WEST":
                return WHexGrid.SIDE.NORTH_WEST;
            case "NE":
            case "NORTH_EAST":
                return WHexGrid.SIDE.NORTH_EAST;
            case "E":
            case "EAST":
                return WHexGrid.SIDE.EAST;
            case "SE":
            case "SOUTH_EAST":
                return WHexGrid.SIDE.SOUTH_EAST;
            case "SW":
            case "SOUTH_WEST":
                return WHexGrid.SIDE.SOUTH_WEST;
            case "W":
            case "WEST":
                return WHexGrid.SIDE.WEST;
            default:
                throw new IllegalArgumentException("Unknown side: " + sideStr);
        }
    }

    /**
     * Apply height transformation for a single ridge.
     * Increases height near the specified side to create a mountain ridge.
     */
    private void applyRidgeTransformation(WFlat flat, RidgeDefinition ridge, int baseHeight, int hillHeight, int ridgeWidth) {
        log.debug("Applying ridge transformation: side={}, level={}, ridgeWidth={}", ridge.getSide(), ridge.getLevel(), ridgeWidth);

        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Get side corners
        int[][] sideCorners = getSideCorners(ridge.getSide(), sizeX, sizeZ);
        int[] cornerA = sideCorners[0];
        int[] cornerB = sideCorners[1];

        // Transform height map to create ridge
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                // Calculate distance from this point to the ridge line
                double distanceToRidge = calculateDistanceToLine(x, z, cornerA[0], cornerA[1], cornerB[0], cornerB[1]);

                // Apply ridge effect if within range
                if (distanceToRidge < ridgeWidth) {
                    int currentLevel = flat.getLevel(x, z);

                    // Calculate ridge factor (1.0 at ridge line, 0.0 at ridgeWidth distance)
                    double ridgeFactor = 1.0 - (distanceToRidge / ridgeWidth);
                    ridgeFactor = Math.pow(ridgeFactor, 2.0); // Square for smoother falloff

                    // Calculate target height at ridge
                    int ridgeHeight = ridge.getLevel();

                    // Interpolate between current height and ridge height
                    int newLevel = 0;
                    if (currentLevel == 0) {
                        newLevel = (int) (ridgeFactor * ridgeHeight);
                    } else {
                        newLevel = (int) (currentLevel + ridgeFactor * (ridgeHeight - currentLevel));
                    }
                    // Ensure we don't lower the terrain, only raise it
                    newLevel = Math.max(currentLevel, newLevel);

                    // Clamp to valid range
                    newLevel = Math.max(0, Math.min(255, newLevel));

                    flat.setLevel(x, z, newLevel);
                }
            }
        }

        log.debug("Ridge transformation applied for side: {}", ridge.getSide());
    }

    /**
     * Calculate distance from a point to a line segment.
     */
    private double calculateDistanceToLine(int px, int pz, int x1, int z1, int x2, int z2) {
        // Vector from point 1 to point 2
        double lineX = x2 - x1;
        double lineZ = z2 - z1;

        // Length squared of the line
        double lineLengthSquared = lineX * lineX + lineZ * lineZ;

        if (lineLengthSquared == 0) {
            // Line is a point
            double dx = px - x1;
            double dz = pz - z1;
            return Math.sqrt(dx * dx + dz * dz);
        }

        // Calculate projection parameter t
        double t = ((px - x1) * lineX + (pz - z1) * lineZ) / lineLengthSquared;

        // Clamp t to [0, 1] to stay on line segment
        t = Math.max(0, Math.min(1, t));

        // Calculate closest point on line segment
        double closestX = x1 + t * lineX;
        double closestZ = z1 + t * lineZ;

        // Calculate distance
        double dx = px - closestX;
        double dz = pz - closestZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Get the two corners that define a side.
     */
    private int[][] getSideCorners(WHexGrid.SIDE side, int sizeX, int sizeZ) {
        switch (side) {
            case NORTH_WEST:
                return new int[][]{{0, 0}, {sizeX / 2, 0}};
            case NORTH_EAST:
                return new int[][]{{sizeX / 2, 0}, {sizeX - 1, 0}};
            case EAST:
                return new int[][]{{sizeX - 1, 0}, {sizeX - 1, sizeZ - 1}};
            case SOUTH_EAST:
                return new int[][]{{sizeX - 1, sizeZ - 1}, {sizeX / 2, sizeZ - 1}};
            case SOUTH_WEST:
                return new int[][]{{sizeX / 2, sizeZ - 1}, {0, sizeZ - 1}};
            case WEST:
                return new int[][]{{0, sizeZ - 1}, {0, 0}};
            default:
                return new int[][]{{0, 0}, {sizeX - 1, sizeZ - 1}};
        }
    }

    /**
     * Set materials based on height.
     * Higher elevations get stone, lower elevations get grass.
     */
    private void setMountainMaterials(WFlat flat, int oceanLevel) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Calculate material thresholds
        int grassToStoneThreshold = oceanLevel + 20;  // Stone starts 20 above ocean level
        int snowThreshold = oceanLevel + 50;          // Snow starts 50 above ocean level

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int level = flat.getLevel(x, z);

                int material;
                if (level >= snowThreshold) {
                    material = FlatMaterialService.SNOW;
                } else if (level >= grassToStoneThreshold) {
                    material = FlatMaterialService.STONE;
                } else if (level <= oceanLevel) {
                    material = FlatMaterialService.SAND;
                } else {
                    material = FlatMaterialService.GRASS;
                }

                flat.setColumn(x, z, material);
            }
        }
    }

    @Override
    protected int getDefaultLandOffset() {
        return 20;  // MOUNTAIN: large variation for dramatic peaks
    }

    @Override
    protected int getDefaultLandLevel() {
        return 50;  // MOUNTAIN: well above ocean level
    }

    @Override
    public int getLandSideLevel(WHexGrid.SIDE side) {
        // Check if this side has a ridge defined
        WHexGrid hexGrid = context.getHexGrid();
        String ridgeParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("ridge") : null;

        if (ridgeParam != null && !ridgeParam.isBlank()) {
            try {
                List<RidgeDefinition> ridges = parseRidgeDefinitions(ridgeParam);
                for (RidgeDefinition ridge : ridges) {
                    if (ridge.getSide() == side) {
                        // This side has a ridge, return the ridge level
                        return ridge.getLevel() - context.getWorld().getOceanLevel();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse ridge definitions for side level", e);
            }
        }

        // No ridge on this side, return center level
        return getLandCenterLevel();
    }

    private long parseLongParameter(Map<String, String> parameters, String name, long defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid long parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
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
     * Ridge definition for a mountain side.
     */
    @Data
    private static class RidgeDefinition {
        private WHexGrid.SIDE side;
        private int level;
    }
}
