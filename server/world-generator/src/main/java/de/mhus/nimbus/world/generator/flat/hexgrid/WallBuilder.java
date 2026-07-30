package de.mhus.nimbus.world.generator.flat.hexgrid;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

/**
 * WallBuilder manipulator builder.
 * Creates straight walls from a center point to various destinations.
 * Unlike RoadBuilder, walls are straight lines without curves.
 * <p>
 * Parameter format in HexGrid:
 * wall={
 *   position: "<0;0>",
 *   route: [
 *     {
 *       position: "<NE2/4>",
 *       height: 5,
 *       level: 50,
 *       type: 3,
 *       width: 3,
 *       minimum: 3,
 *       respectRoad: false,
 *       respectRiver: false
 *     },
 *     {
 *       position: "<1;-1>",
 *       height: 4,
 *       level: 55,
 *       type: 3
 *     }
 *   ]
 * }
 * <p>
 * Position format (HexLocal):
 * - Edge positions: "<NE2/4>", "<SW1/3>" - position on hex edge (North to South)
 * - Inner positions: "<0;0>", "<1;-1>" - position within hex grid
 */
@Slf4j
public class WallBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private static final int DEFAULT_WIDTH = 3;
    private static final int DEFAULT_HEIGHT = 5;
    private static final int DEFAULT_TYPE = FlatMaterialService.STONE;

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.debug("Building walls for flat: {}", flat.getFlatId());

        // Get wall parameter from hex grid
        String wallParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("g_wall") : null;
        if (wallParam == null || wallParam.isBlank()) {
            log.debug("No wall parameter found, skipping");
            return;
        }

        try {
            // Parse wall configuration
            WallConfig config = parseWallConfig(wallParam);

            // Determine center position (use position string or default to flat center)
            int centerX, centerZ;
            if (config.getCenter().getPosition() != null) {
                int[] centerCoords = getAbsoluteCoordinates(config.getCenter().getPosition(),
                    flat.getSizeX(), flat.getSizeZ());
                centerX = centerCoords[0];
                centerZ = centerCoords[1];
            } else {
                // Default to flat center
                centerX = flat.getSizeX() / 2;
                centerZ = flat.getSizeZ() / 2;
            }

            log.debug("Parsed wall config: center=({}, {}), routes={}", centerX, centerZ, config.getRoute().size());

            log.debug("Center position: ({}, {})", centerX, centerZ);

            // Build walls from center to each destination
            for (WallRoute route : config.getRoute()) {
                buildWallToDestination(flat, centerX, centerZ, route);
            }

            log.info("Walls completed for flat: {} routes built", config.getRoute().size());
        } catch (Exception e) {
            log.error("Failed to build walls for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Parse wall configuration from JSON string.
     */
    private WallConfig parseWallConfig(String wallParam) throws Exception {
        JsonNode root = objectMapper.readTree(wallParam);

        WallConfig config = new WallConfig();

        // Parse center position (optional, null means flat center)
        CenterDefinition center = new CenterDefinition();
        center.setPosition(root.has("position") ? root.get("position").asText() : null);
        config.setCenter(center);

        // Parse route array
        List<WallRoute> routes = new ArrayList<>();
        if (root.has("route") && root.get("route").isArray()) {
            for (JsonNode routeNode : root.get("route")) {
                WallRoute route = new WallRoute();

                // Parse position (required)
                if (!routeNode.has("position")) {
                    throw new IllegalArgumentException("Wall route must have 'position' field in HexLocal format (e.g., '<NE2/4>' or '<0;0>')");
                }
                route.setPosition(routeNode.get("position").asText());

                // Parse wall properties
                route.setHeight(routeNode.has("height") ? routeNode.get("height").asInt() : DEFAULT_HEIGHT);
                route.setLevel(routeNode.get("level").asInt());
                route.setWidth(routeNode.has("width") ? routeNode.get("width").asInt() : DEFAULT_WIDTH);
                route.setMinimum(routeNode.has("minimum") ? routeNode.get("minimum").asInt() : 0);
                route.setType(routeNode.has("type") ? routeNode.get("type").asInt() : DEFAULT_TYPE);
                route.setRespectRoad(routeNode.has("respectRoad") && routeNode.get("respectRoad").asBoolean());
                route.setRespectRiver(routeNode.has("respectRiver") && routeNode.get("respectRiver").asBoolean());

                routes.add(route);
            }
        }
        config.setRoute(routes);

        return config;
    }

    /**
     * Build a straight wall from center to destination.
     */
    private void buildWallToDestination(WFlat flat, int centerX, int centerZ, WallRoute route) {
        // Get destination coordinates from position string
        int[] destCoords = getAbsoluteCoordinates(route.getPosition(), flat.getSizeX(), flat.getSizeZ());
        int destX = destCoords[0];
        int destZ = destCoords[1];

        log.debug("Building wall from center ({}, {}) to destination ({}, {})",
                centerX, centerZ, destX, destZ);

        // Get water block definition if needed
        String waterBlockDef = route.isRespectRiver() ? getWaterBlockDef(flat) : null;

        // Calculate total distance
        int dx = destX - centerX;
        int dz = destZ - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        // Build straight line from center to destination
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Linear interpolation
            int x = (int) Math.round(centerX + t * dx);
            int z = (int) Math.round(centerZ + t * dz);

            // Check bounds
            if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                continue;
            }

            // Build wall segment with width
            buildWallSegment(flat, x, z, route, waterBlockDef);
        }
    }

    /**
     * Get coordinate on a specific side of the hex grid.
     * Returns the midpoint of that side.
     */
    /**
     * Get absolute WFlat coordinates from HexLocal position string.
     * Uses HexLocalUtil to parse the position and convert to absolute coordinates.
     *
     * @param position the position in HexLocal format (e.g., "<NE2/4>" or "<0;0>")
     * @param sizeX WFlat width
     * @param sizeZ WFlat height
     * @return absolute coordinates [lx, lz] in WFlat coordinate system
     */
    private int[] getAbsoluteCoordinates(String position, int sizeX, int sizeZ) {
        // Assume hexGridSize equals WFlat size (standard case)
        int hexGridSize = sizeX;  // or could use Math.max(sizeX, sizeZ)

        // Parse position string and get relative coordinates
        de.mhus.nimbus.generated.types.Vector2Int relativePos =
            de.mhus.nimbus.world.shared.util.HexLocalUtil.toHexgridLocalCenter(position, hexGridSize);

        // Convert to absolute WFlat coordinates
        int lx = sizeX / 2 + relativePos.getX();
        int lz = sizeZ / 2 + relativePos.getZ();

        return new int[]{lx, lz};
    }

    /**
     * Build a wall segment at the given position with width.
     * Width is distributed in all directions around the center point.
     */
    private void buildWallSegment(WFlat flat, int centerX, int centerZ, WallRoute route, String waterBlockDef) {
        int halfWidth = route.getWidth() / 2;

        // Build wall in square pattern around center point
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Check if wall should respect roads and hits a street or trail
                if (route.isRespectRoad()) {
                    int currentMaterial = flat.getColumn(x, z);
                    if (isStreetOrTrailMaterial(currentMaterial)) {
                        log.debug("Wall interrupted at ({}, {}) - street/trail present", x, z);
                        continue;
                    }
                }

                // Check if wall should respect rivers and hits a river
                if (route.isRespectRiver()) {
                    if (hasWaterAtPosition(flat, x, z, waterBlockDef)) {
                        log.debug("Wall interrupted at ({}, {}) - river present", x, z);
                        continue;
                    }
                }

                // Get current terrain level
                int currentLevel = flat.getLevel(x, z);

                // Calculate wall base level
                int wallBaseLevel = route.getLevel();

                // Apply minimum height constraint
                // Wall should be at least 'minimum' blocks above current terrain
                if (route.getMinimum() > 0) {
                    int minimumWallBase = currentLevel + route.getMinimum();
                    wallBaseLevel = Math.max(wallBaseLevel, minimumWallBase);
                }

                // Build wall from base to height
                // Set the level to wall top
                int wallTopLevel = wallBaseLevel + route.getHeight();
                flat.setLevel(x, z, wallTopLevel);

                // Set wall material
                flat.setColumn(x, z, route.getType());
            }
        }
    }

    /**
     * Check if material is a street or trail material.
     * Checks for STREET, STREET_BORDER, STREET_BRIDGE, TRACK, TRACK_BORDER, TRACK_BRIDGE.
     */
    private boolean isStreetOrTrailMaterial(int material) {
        return material == FlatMaterialService.STREET
                || material == FlatMaterialService.STREET_BORDER
                || material == FlatMaterialService.STREET_BRIDGE
                || material == FlatMaterialService.TRAIL
                || material == FlatMaterialService.TRAIL_BORDER
                || material == FlatMaterialService.TRAIL_BRIDGE;
    }

    /**
     * Check if there's water at the given position.
     */
    private boolean hasWaterAtPosition(WFlat flat, int x, int z, String waterBlockDef) {
        if (waterBlockDef == null) {
            return false;
        }

        // Get all extra blocks for this column
        String[] extraBlocks = flat.getExtraBlocksForColumn(x, z);
        if (extraBlocks == null || extraBlocks.length == 0) {
            return false;
        }

        // Check if any extra block is water
        for (String blockDef : extraBlocks) {
            if (waterBlockDef.equals(blockDef)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get water block definition from material palette.
     * Returns the blockDef for WATER material (5).
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
        return 0;
    }

    @Override
    protected int getDefaultAsl() {
        return 0;
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getCenterAsl();
    }

    /**
     * Wall configuration parsed from parameters.
     */
    @Data
    private static class WallConfig {
        private CenterDefinition center;
        private List<WallRoute> route;
    }

    /**
     * Center point definition.
     */
    @Data
    private static class CenterDefinition {
        private String position;  // HexLocal format: "<NE2/4>" or "<0;0>" (null = use flat center)
    }

    /**
     * Wall route definition.
     */
    @Data
    private static class WallRoute {
        private String position;  // HexLocal format: "<NE2/4>" or "<0;0>"
        private int height;
        private int level;
        private int width;
        private int minimum;
        private int type;
        private boolean respectRoad;
        private boolean respectRiver;
    }
}
