package de.mhus.nimbus.world.generator.flat.hexgrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * WallBuilder manipulator builder.
 * Creates straight walls from a center point to various destinations (sides or positions).
 * Unlike RoadBuilder, walls are straight lines without curves.
 * <p>
 * Parameter format in HexGrid:
 * wall={
 *   lx: 130,
 *   lz: 130,
 *   route: [
 *     {
 *       side: "NE",
 *       height: 5,
 *       level: 50,
 *       type: 3,
 *       width: 3,
 *       minimum: 3,
 *       respectRoad: false,
 *       respectRiver: false
 *     },
 *     {
 *       lx: 80,
 *       lz: 40,
 *       height: 4,
 *       level: 55,
 *       type: 3
 *     }
 *   ]
 * }
 */
@Slf4j
public class WallBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_WIDTH = 3;
    private static final int DEFAULT_HEIGHT = 5;
    private static final int DEFAULT_TYPE = FlatMaterialService.STONE;

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.info("Building walls for flat: {}", flat.getFlatId());

        // Get wall parameter from hex grid
        String wallParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("g_wall") : null;
        if (wallParam == null || wallParam.isBlank()) {
            log.debug("No wall parameter found, skipping");
            return;
        }

        try {
            // Parse wall configuration
            WallConfig config = parseWallConfig(wallParam);
            log.debug("Parsed wall config: center=({}, {}), routes={}",
                    config.getCenter().getLx(), config.getCenter().getLz(), config.getRoute().size());

            // Determine center position (use specified or default to flat center)
            int centerX = config.getCenter().getLx() >= 0 ? config.getCenter().getLx() : flat.getSizeX() / 2;
            int centerZ = config.getCenter().getLz() >= 0 ? config.getCenter().getLz() : flat.getSizeZ() / 2;

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

        // Parse center position (optional, defaults to -1 which means flat center)
        CenterDefinition center = new CenterDefinition();
        center.setLx(root.has("lx") ? root.get("lx").asInt() : -1);
        center.setLz(root.has("lz") ? root.get("lz").asInt() : -1);
        config.setCenter(center);

        // Parse route array
        List<WallRoute> routes = new ArrayList<>();
        if (root.has("route") && root.get("route").isArray()) {
            for (JsonNode routeNode : root.get("route")) {
                WallRoute route = new WallRoute();

                // Parse destination (either side or lx/lz position)
                if (routeNode.has("side")) {
                    route.setSide(parseSide(routeNode.get("side").asText()));
                } else if (routeNode.has("lx") && routeNode.has("lz")) {
                    route.setPositionX(routeNode.get("lx").asInt());
                    route.setPositionZ(routeNode.get("lz").asInt());
                }

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
        // Determine destination coordinates
        int[] destCoords;
        if (route.getSide() != null) {
            // Destination is a hex grid side
            destCoords = getSideCoordinate(route.getSide(), flat.getSizeX(), flat.getSizeZ());
        } else if (route.getPositionX() != null && route.getPositionZ() != null) {
            // Destination is an absolute position
            destCoords = new int[]{route.getPositionX(), route.getPositionZ()};
        } else {
            log.warn("Wall route has neither side nor position, skipping");
            return;
        }

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
    private int[] getSideCoordinate(WHexGrid.SIDE side, int sizeX, int sizeZ) {
        switch (side) {
            case NORTH_WEST:
                return new int[]{sizeX / 4, 0};
            case NORTH_EAST:
                return new int[]{3 * sizeX / 4, 0};
            case EAST:
                return new int[]{sizeX - 1, sizeZ / 2};
            case SOUTH_EAST:
                return new int[]{3 * sizeX / 4, sizeZ - 1};
            case SOUTH_WEST:
                return new int[]{sizeX / 4, sizeZ - 1};
            case WEST:
                return new int[]{0, sizeZ / 2};
            default:
                return new int[]{sizeX / 2, sizeZ / 2};
        }
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
                || material == FlatMaterialService.TRACK
                || material == FlatMaterialService.TRACK_BORDER
                || material == FlatMaterialService.TRACK_BRIDGE;
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
    protected int getDefaultLandOffset() {
        return 0;
    }

    @Override
    protected int getDefaultLandLevel() {
        return 0;
    }

    @Override
    public int getLandSideLevel(WHexGrid.SIDE side) {
        return getLandCenterLevel();
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
        private int lx;  // Absolute local X position (-1 = use flat center)
        private int lz;  // Absolute local Z position (-1 = use flat center)
    }

    /**
     * Wall route definition.
     */
    @Data
    private static class WallRoute {
        private WHexGrid.SIDE side;      // Optional: destination side
        private Integer positionX;       // Optional: destination X position
        private Integer positionZ;       // Optional: destination Z position
        private int height;
        private int level;
        private int width;
        private int minimum;
        private int type;
        private boolean respectRoad;
        private boolean respectRiver;
    }
}
