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
import java.util.Map;

/**
 * RoadBuilder manipulator builder.
 * Creates roads from hex grid sides or positions to the center where they all meet.
 * Roads can be streets or trails with transitions to grass.
 * Uses sinusoidal curves for natural, slightly curved roads.
 * Builds bridges when crossing rivers.
 * <p>
 * Parameter format in HexGrid:
 * road={
 *   lx: 130,
 *   lz: 130,
 *   level: 95,
 *   plazaSize: 30,
 *   plazaMaterial: "street",
 *   route: [
 *     {
 *       side: "NE",
 *       width: 3,
 *       level: 50,
 *       type: "street"
 *     },
 *     {
 *       lx: 80,
 *       lz: 40,
 *       width: 4,
 *       level: 55,
 *       type: "street"
 *     }
 *   ]
 * }
 * <p>
 * Optional parameters:
 * - lx, lz: Center position (default: flat center)
 * - level: Center level (default: 0)
 * - roadCurvature: Maximum lateral offset for road curves in pixels (default: 10)
 * - roadWaves: Number of sine wave cycles along the road (default: 1.5)
 * - plazaSize: Size of plaza at center (default: 0 = no plaza)
 * - plazaMaterial: Material for plaza (default: best material from routes, street > trail)
 */
@Slf4j
public class RoadBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_CURVATURE = 10;  // Default maximum lateral offset for curves
    private static final double DEFAULT_WAVES = 1.5;  // Default number of sine wave cycles

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.info("Building roads for flat: {}", flat.getFlatId());

        // Clear all existing bridge extra blocks before building roads
        clearBridgeExtraBlocks(flat);

        // Get road parameter from hex grid
        String roadParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("g_road") : null;
        if (roadParam == null || roadParam.isBlank()) {
            log.debug("No road parameter found, skipping");
            return;
        }

        try {
            // Parse road configuration
            RoadConfiguration config = parseRoadConfiguration(roadParam);

            // Determine center position (use absolute position or default to flat center)
            int centerX = config.getCenter().getLx() >= 0 ? config.getCenter().getLx() : flat.getSizeX() / 2;
            int centerZ = config.getCenter().getLz() >= 0 ? config.getCenter().getLz() : flat.getSizeZ() / 2;
            int centerLevel = config.getCenter().getLevel();

            log.debug("Parsed {} roads with center at ({}, {})",
                    config.getRoute().size(), centerX, centerZ);

            // Build each road from its side to the center
            for (Road road : config.getRoute()) {
                buildRoadToCenter(flat, road, centerX, centerZ, centerLevel);
            }

            // Build plaza at center if configured
            if (config.getCenter().getPlazaSize() > 0) {
                String plazaMaterial = determinePlazaMaterial(config);
                buildPlaza(flat, centerX, centerZ, centerLevel, config.getCenter().getPlazaSize(), plazaMaterial);
            }

            log.info("Roads completed for flat: {} roads built", config.getRoute().size());
        } catch (Exception e) {
            log.error("Failed to build roads for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Parse road configuration from JSON string.
     */
    private RoadConfiguration parseRoadConfiguration(String roadParam) throws Exception {
        JsonNode root = objectMapper.readTree(roadParam);

        RoadConfiguration config = new RoadConfiguration();

        // Parse center properties (optional, defaults to flat center)
        CenterDefinition center = new CenterDefinition();
        center.setLx(root.has("lx") ? root.get("lx").asInt() : -1);  // -1 means use flat center
        center.setLz(root.has("lz") ? root.get("lz").asInt() : -1);
        center.setLevel(root.has("level") ? root.get("level").asInt() : 0);
        center.setPlazaSize(root.has("plazaSize") ? root.get("plazaSize").asInt() : 0);
        center.setPlazaMaterial(root.has("plazaMaterial") ? root.get("plazaMaterial").asText() : null);
        config.setCenter(center);

        // Parse route array
        List<Road> roads = new ArrayList<>();
        if (root.has("route") && root.get("route").isArray()) {
            for (JsonNode roadNode : root.get("route")) {
                Road road = new Road();

                // Check if road has 'side' or 'lx'/'lz'
                if (roadNode.has("side")) {
                    // Start from hex grid side
                    road.setSide(parseSide(roadNode.get("side").asText()));
                } else if (roadNode.has("lx") && roadNode.has("lz")) {
                    // Start from absolute position
                    road.setPositionX(roadNode.get("lx").asInt());
                    road.setPositionZ(roadNode.get("lz").asInt());
                }

                road.setWidth(roadNode.get("width").asInt());
                road.setLevel(roadNode.get("level").asInt());
                road.setType(roadNode.has("type") ? roadNode.get("type").asText() : "street");
                roads.add(road);
            }
        }
        config.setRoute(roads);

        return config;
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
     * Build a road from a side or position to the center of the hex grid with slight curves.
     */
    private void buildRoadToCenter(WFlat flat, Road road, int centerX, int centerZ, int centerLevel) {
        // Get curvature parameters
        int curvature = parseIntParameter(parameters, "roadCurvature", DEFAULT_CURVATURE);
        double waves = parseDoubleParameter(parameters, "roadWaves", DEFAULT_WAVES);

        // Get start coordinates (either from side or position)
        int[] startCoords;
        if (road.getSide() != null) {
            // Start from hex grid side
            startCoords = getSideCoordinate(road.getSide(), flat.getSizeX(), flat.getSizeZ());
            log.debug("Building road from side {} to center", road.getSide());
        } else if (road.getPositionX() != null && road.getPositionZ() != null) {
            // Start from absolute position
            startCoords = new int[]{road.getPositionX(), road.getPositionZ()};
            log.debug("Building road from position ({}, {}) to center", road.getPositionX(), road.getPositionZ());
        } else {
            log.warn("Road has neither side nor position defined, skipping");
            return;
        }

        // Calculate road path from start to center
        int dx = centerX - startCoords[0];
        int dz = centerZ - startCoords[1];
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        // Calculate perpendicular direction for lateral offset
        double[] perpDir = calculatePerpendicularDirection(dx, dz);

        // Determine start and end levels
        int startLevel = road.getLevel();
        int endLevel = centerLevel > 0 ? centerLevel : startLevel;

        // Draw road along the curved path from edge to center
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Base position (straight line)
            double baseX = startCoords[0] + t * dx;
            double baseZ = startCoords[1] + t * dz;

            // Calculate lateral offset using sine wave
            // Sine creates smooth, natural curves
            double sineValue = Math.sin(t * Math.PI * 2.0 * waves);
            double lateralOffset = sineValue * curvature;

            // Apply lateral offset perpendicular to road direction
            int x = (int) (baseX + lateralOffset * perpDir[0]);
            int z = (int) (baseZ + lateralOffset * perpDir[1]);

            // Interpolate width and level from start to center
            int width = road.getWidth();
            int level = (int) (startLevel + t * (endLevel - startLevel));

            // Draw road segment
            drawRoadSegment(flat, x, z, width, level, road.getType());
        }
    }

    /**
     * Calculate perpendicular direction vector (normalized).
     * Returns a unit vector perpendicular to the direction (dx, dz).
     */
    private double[] calculatePerpendicularDirection(int dx, int dz) {
        // Perpendicular vector is (-dz, dx)
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length == 0) {
            return new double[]{0, 0};
        }
        return new double[]{-dz / length, dx / length};
    }

    /**
     * Parse integer parameter with default value.
     */
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
     * Parse double parameter with default value.
     */
    private double parseDoubleParameter(Map<String, String> parameters, String name, double defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid double parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get the center coordinate of a side.
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
     * Draw a road segment at the given position with the given width.
     */
    private void drawRoadSegment(WFlat flat, int centerX, int centerZ, int width, int level,
                                  String type) {
        // Determine material based on type
        int centerMaterial = type.equalsIgnoreCase("track") ? FlatMaterialService.TRACK : FlatMaterialService.STREET;
        int borderMaterial = type.equalsIgnoreCase("track") ? FlatMaterialService.TRACK_BORDER : FlatMaterialService.STREET_BORDER;
        int bridgeMaterial = type.equalsIgnoreCase("track") ? FlatMaterialService.TRACK_BRIDGE : FlatMaterialService.STREET_BRIDGE;

        // Get water block definition
        String waterBlockDef = getWaterBlockDef(flat);

        // Draw center and edges
        int halfWidth = width / 2;
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Check if there's water at this position
                boolean hasWater = hasWaterAtPosition(flat, x, z, waterBlockDef);

                if (hasWater) {
                    // Build bridge: extraBlock at least 3 blocks above water level
                    int waterLevel = getWaterLevel(flat, x, z);
                    int bridgeLevel = Math.max(level, waterLevel + 3);

                    // Set bridge as extra block
                    String bridgeBlockDef = getBridgeBlockDef(flat, bridgeMaterial);
                    flat.setExtraBlock(x, bridgeLevel, z, bridgeBlockDef);
                } else {
                    // Normal road: set level and material
                    // Determine material based on position relative to center
                    int material;
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        // Center of road
                        material = centerMaterial;
                    } else {
                        // Border/edge of road
                        material = borderMaterial;
                    }

                    // Set level and material
                    flat.setLevel(x, z, level);
                    flat.setColumn(x, z, material);
                }
            }
        }
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
     * Get the water level at the given position.
     * Returns the highest Y coordinate where water exists.
     */
    private int getWaterLevel(WFlat flat, int x, int z) {
        String waterBlockDef = getWaterBlockDef(flat);
        if (waterBlockDef == null) {
            return flat.getLevel(x, z);
        }

        // Search for water blocks from top to bottom
        for (int y = 255; y >= 0; y--) {
            String blockDef = flat.getExtraBlock(x, y, z);
            if (waterBlockDef.equals(blockDef)) {
                return y;
            }
        }

        // No water found, return terrain level
        return flat.getLevel(x, z);
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

    /**
     * Get bridge block definition from material palette.
     * Returns the blockDef for the specified bridge material.
     */
    private String getBridgeBlockDef(WFlat flat, int bridgeMaterial) {
        WFlat.MaterialDefinition material = flat.getMaterial((byte) bridgeMaterial);
        if (material != null) {
            return material.getBlockDef();
        }
        // Fallback to stone if no material definition found
        return "n:s";
    }

    /**
     * Determine the material for the plaza.
     * If plazaMaterial is specified, use it. Otherwise, use the best material from routes.
     * street is better than trail/track.
     */
    private String determinePlazaMaterial(RoadConfiguration config) {
        // If explicitly specified, use that
        if (config.getCenter().getPlazaMaterial() != null && !config.getCenter().getPlazaMaterial().isBlank()) {
            return config.getCenter().getPlazaMaterial();
        }

        // Otherwise, find the best material from routes
        // street is better than trail/track
        boolean hasStreet = false;
        for (Road road : config.getRoute()) {
            if ("street".equalsIgnoreCase(road.getType())) {
                hasStreet = true;
                break;
            }
        }

        return hasStreet ? "street" : "track";
    }

    /**
     * Build a plaza at the center point.
     * Plaza is a circular area with the specified material.
     * If water is present, the plaza is not drawn at that position.
     */
    private void buildPlaza(WFlat flat, int centerX, int centerZ, int level, int plazaSize, String plazaMaterial) {
        log.debug("Building plaza at ({}, {}) with size {} and material {}", centerX, centerZ, plazaSize, plazaMaterial);

        // Determine material based on type
        int material = plazaMaterial.equalsIgnoreCase("track") ? FlatMaterialService.TRACK : FlatMaterialService.STREET;

        // Get water block definition
        String waterBlockDef = getWaterBlockDef(flat);

        // Draw plaza as circle centered at centerX, centerZ
        int radius = plazaSize / 2;
        double radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Check if point is within circle
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > radiusSquared) {
                    continue;
                }

                int x = centerX + dx;
                int z = centerZ + dz;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Check if there's water at this position
                boolean hasWater = hasWaterAtPosition(flat, x, z, waterBlockDef);

                // Don't draw plaza where water is present
                if (hasWater) {
                    continue;
                }

                // Set level and material
                flat.setLevel(x, z, level);
                flat.setColumn(x, z, material);
            }
        }

        log.debug("Plaza completed at ({}, {})", centerX, centerZ);
    }

    /**
     * Clear all STREET_BRIDGE and TRACK_BRIDGE extra blocks from the flat.
     * This removes previous bridges before building new roads.
     */
    private void clearBridgeExtraBlocks(WFlat flat) {
        // Get bridge block definitions from material palette
        String streetBridgeDef = getBridgeBlockDef(flat, FlatMaterialService.STREET_BRIDGE);
        String trackBridgeDef = getBridgeBlockDef(flat, FlatMaterialService.TRACK_BRIDGE);

        if (streetBridgeDef == null && trackBridgeDef == null) {
            log.warn("No bridge block definitions found in material palette, skipping clear");
            return;
        }

        // Iterate through all extra blocks and remove bridge blocks
        flat.getExtraBlocks().entrySet().removeIf(entry -> {
            String blockDef = entry.getValue();
            return streetBridgeDef.equals(blockDef) || trackBridgeDef.equals(blockDef);
        });

        log.debug("Cleared all bridge extra blocks");
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
     * Road definition from a side or position to the center.
     * Either side OR position (lx, lz) must be set.
     */
    @Data
    private static class Road {
        private WHexGrid.SIDE side;  // Optional: start from hex grid side
        private Integer positionX;   // Optional: start from absolute position
        private Integer positionZ;   // Optional: start from absolute position
        private int width;
        private int level;
        private String type;
    }

    /**
     * Center definition with absolute local position, level and optional plaza.
     */
    @Data
    private static class CenterDefinition {
        private int lx;
        private int lz;
        private int level;
        private int plazaSize;
        private String plazaMaterial;
    }

    /**
     * Road configuration with center and routes.
     */
    @Data
    private static class RoadConfiguration {
        private CenterDefinition center;
        private List<Road> route;
    }
}
