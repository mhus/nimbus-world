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
 * Creates roads from hex grid sides to the center where they all meet.
 * Roads can be streets or trails with transitions to grass.
 * Uses sinusoidal curves for natural, slightly curved roads.
 * <p>
 * Parameter format in HexGrid:
 * road={
 *   center: {
 *     "dx": -40,
 *     "dz": -40,
 *     "level": 95
 *   },
 *   route: [
 *     {
 *       side: "NE",
 *       width: 3,
 *       level: 50,
 *       type: "street"
 *     },
 *     {
 *       side: "SW",
 *       width: 5,
 *       level: 55,
 *       type: "trail"
 *     }
 *   ]
 * }
 * <p>
 * Optional parameters:
 * - roadCurvature: Maximum lateral offset for road curves in pixels (default: 10)
 * - roadWaves: Number of sine wave cycles along the road (default: 1.5)
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

        // Get road parameter from hex grid
        String roadParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("road") : null;
        if (roadParam == null || roadParam.isBlank()) {
            log.debug("No road parameter found, skipping");
            return;
        }

        try {
            // Parse road configuration
            RoadConfiguration config = parseRoadConfiguration(roadParam);
            log.debug("Parsed {} roads with center offset ({}, {})",
                    config.getRoute().size(), config.getCenter().getDx(), config.getCenter().getDz());

            // Calculate center of the flat with offset
            int centerX = flat.getSizeX() / 2 + config.getCenter().getDx();
            int centerZ = flat.getSizeZ() / 2 + config.getCenter().getDz();
            int centerLevel = config.getCenter().getLevel();

            // Build each road from its side to the center
            for (Road road : config.getRoute()) {
                buildRoadToCenter(flat, road, centerX, centerZ, centerLevel);
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

        // Parse center (optional, defaults to 0, 0, 0)
        CenterDefinition center = new CenterDefinition();
        if (root.has("center") && root.get("center").isObject()) {
            JsonNode centerNode = root.get("center");
            center.setDx(centerNode.has("dx") ? centerNode.get("dx").asInt() : 0);
            center.setDz(centerNode.has("dz") ? centerNode.get("dz").asInt() : 0);
            center.setLevel(centerNode.has("level") ? centerNode.get("level").asInt() : 0);
        } else {
            center.setDx(0);
            center.setDz(0);
            center.setLevel(0);
        }
        config.setCenter(center);

        // Parse route array
        List<Road> roads = new ArrayList<>();
        if (root.has("route") && root.get("route").isArray()) {
            for (JsonNode roadNode : root.get("route")) {
                Road road = new Road();
                road.setSide(parseSide(roadNode.get("side").asText()));
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
     * Build a road from a side to the center of the hex grid with slight curves.
     */
    private void buildRoadToCenter(WFlat flat, Road road, int centerX, int centerZ, int centerLevel) {
        log.debug("Building road from {} to center", road.getSide());

        // Get curvature parameters
        int curvature = parseIntParameter(parameters, "roadCurvature", DEFAULT_CURVATURE);
        double waves = parseDoubleParameter(parameters, "roadWaves", DEFAULT_WAVES);

        // Get start coordinates at the edge
        int[] startCoords = getSideCoordinate(road.getSide(), flat.getSizeX(), flat.getSizeZ());

        // Calculate road path from edge to center
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
     * Road definition from a side to the center.
     */
    @Data
    private static class Road {
        private WHexGrid.SIDE side;
        private int width;
        private int level;
        private String type;
    }

    /**
     * Center definition with offset and level.
     */
    @Data
    private static class CenterDefinition {
        private int dx;
        private int dz;
        private int level;
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
