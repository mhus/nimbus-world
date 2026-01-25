package de.mhus.nimbus.world.generator.flat.hexgrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.utils.FastNoiseLite;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RiverBuilder manipulator builder.
 * Creates rivers through hex grids from one side to another.
 * Rivers carve through the terrain with configurable width and depth.
 * Uses FastNoiseLite to create natural, curved river paths.
 * <p>
 * Parameter format in HexGrid:
 * river={
 *   from: [{
 *     side: "NE",
 *     width: 3,
 *     depth: 2,
 *     level: 40
 *   }],
 *   to: [{
 *     side: "SW",
 *     width: 5,
 *     depth: 2,
 *     level: 42
 *   }],
 *   groupId: "river-1234"
 * }
 * <p>
 * Optional parameters:
 * - riverCurvature: Maximum lateral offset for river curves in pixels (default: 30)
 * - riverSeed: Seed for river curve generation (default: based on groupId hash)
 */
@Slf4j
public class RiverBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_CURVATURE = 30;  // Default maximum lateral offset for curves

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.info("Building rivers for flat: {}", flat.getFlatId());

        // Clear all existing WATER extra blocks before building rivers
        clearWaterExtraBlocks(flat);

        // Get river parameter from hex grid
        String riverParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("river") : null;
        if (riverParam == null || riverParam.isBlank()) {
            log.debug("No river parameter found, skipping");
            return;
        }

        try {
            // Parse river definition
            RiverDefinition riverDef = parseRiverDefinition(riverParam);
            log.debug("Parsed river definition: from={}, to={}, groupId={}",
                    riverDef.getFrom(), riverDef.getTo(), riverDef.getGroupId());

            // Build river for each from-to pair
            for (RiverEndpoint fromEndpoint : riverDef.getFrom()) {
                for (RiverEndpoint toEndpoint : riverDef.getTo()) {
                    buildRiver(flat, fromEndpoint, toEndpoint, riverDef.getGroupId());
                }
            }

            log.info("Rivers completed for flat: {}", flat.getFlatId());
        } catch (Exception e) {
            log.error("Failed to build rivers for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Parse river definition from JSON string.
     */
    private RiverDefinition parseRiverDefinition(String riverParam) throws Exception {
        JsonNode root = objectMapper.readTree(riverParam);

        RiverDefinition riverDef = new RiverDefinition();
        riverDef.setGroupId(root.has("groupId") ? root.get("groupId").asText() : null);

        // Parse from endpoints
        List<RiverEndpoint> fromList = new ArrayList<>();
        if (root.has("from") && root.get("from").isArray()) {
            for (JsonNode fromNode : root.get("from")) {
                fromList.add(parseEndpoint(fromNode));
            }
        }
        riverDef.setFrom(fromList);

        // Parse to endpoints
        List<RiverEndpoint> toList = new ArrayList<>();
        if (root.has("to") && root.get("to").isArray()) {
            for (JsonNode toNode : root.get("to")) {
                toList.add(parseEndpoint(toNode));
            }
        }
        riverDef.setTo(toList);

        return riverDef;
    }

    /**
     * Parse a single endpoint from JSON node.
     * Supports either side-based (side) or position-based (lx/lz) endpoints.
     */
    private RiverEndpoint parseEndpoint(JsonNode node) {
        RiverEndpoint endpoint = new RiverEndpoint();

        // Parse side-based endpoint
        if (node.has("side")) {
            endpoint.setSide(parseSide(node.get("side").asText()));
        }

        // Parse position-based endpoint
        if (node.has("lx")) {
            endpoint.setLx(node.get("lx").asInt());
        }
        if (node.has("lz")) {
            endpoint.setLz(node.get("lz").asInt());
        }

        // Parse common fields
        endpoint.setWidth(node.get("width").asInt());
        endpoint.setDepth(node.get("depth").asInt());
        endpoint.setLevel(node.get("level").asInt());

        return endpoint;
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
     * Build a river from one endpoint to another with natural curves.
     */
    private void buildRiver(WFlat flat, RiverEndpoint from, RiverEndpoint to, String groupId) {
        // Log endpoint info
        String fromDesc = from.hasCoordinates() ?
            String.format("lx=%d,lz=%d", from.getLx(), from.getLz()) :
            String.format("side=%s", from.getSide());
        String toDesc = to.hasCoordinates() ?
            String.format("lx=%d,lz=%d", to.getLx(), to.getLz()) :
            String.format("side=%s", to.getSide());
        log.debug("Building river from {} to {}", fromDesc, toDesc);

        // Get curvature parameter
        int curvature = parseIntParameter(parameters, "riverCurvature", DEFAULT_CURVATURE);

        // Get or generate seed for noise
        long seed;
        if (parameters != null && parameters.containsKey("riverSeed")) {
            seed = Long.parseLong(parameters.get("riverSeed"));
        } else if (groupId != null) {
            seed = groupId.hashCode();
        } else {
            seed = System.currentTimeMillis();
        }

        // Get start and end coordinates (from side or from lx/lz)
        int[] startCoords = getEndpointCoordinate(from, flat.getSizeX(), flat.getSizeZ());
        int[] endCoords = getEndpointCoordinate(to, flat.getSizeX(), flat.getSizeZ());

        // Calculate river path length
        int dx = endCoords[0] - startCoords[0];
        int dz = endCoords[1] - startCoords[1];
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        // Initialize noise generator for river curves
        FastNoiseLite noise = new FastNoiseLite((int) seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.05f);  // Lower frequency = smoother, wider curves

        // Calculate perpendicular direction for lateral offset
        double[] perpDir = calculatePerpendicularDirection(dx, dz);

        // Draw river along the curved path
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Base position (straight line)
            double baseX = startCoords[0] + t * dx;
            double baseZ = startCoords[1] + t * dz;

            // Calculate lateral offset using noise
            // Offset is 0 at start and end, maximum in the middle
            double curveWeight = Math.sin(t * Math.PI); // 0 at ends, 1 in middle
            float noiseValue = noise.GetNoise((float) baseX, (float) baseZ);
            double lateralOffset = noiseValue * curvature * curveWeight;

            // Apply lateral offset perpendicular to river direction
            int x = (int) (baseX + lateralOffset * perpDir[0]);
            int z = (int) (baseZ + lateralOffset * perpDir[1]);

            // Interpolate width, depth and level
            int width = (int) (from.getWidth() + t * (to.getWidth() - from.getWidth()));
            int depth = (int) (from.getDepth() + t * (to.getDepth() - from.getDepth()));
            int level = (int) (from.getLevel() + t * (to.getLevel() - from.getLevel()));

            // Draw river segment with width and depth
            drawRiverSegment(flat, x, z, width, depth, level, groupId);
        }
    }

    /**
     * Calculate perpendicular direction vector (normalized).
     * Returns a unit vector perpendicular to the direction (dx, dz).
     */
    private double[] calculatePerpendicularDirection(int dx, int dz) {
        // Perpendicular vector is (-dz, dx) or (dz, -dx)
        // We choose (-dz, dx) for consistent direction
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
     * Get endpoint coordinate - either from side or from lx/lz.
     */
    private int[] getEndpointCoordinate(RiverEndpoint endpoint, int sizeX, int sizeZ) {
        if (endpoint.hasCoordinates()) {
            // Use exact lx/lz coordinates
            return new int[]{endpoint.getLx(), endpoint.getLz()};
        } else {
            // Use side-based coordinate
            return getSideCoordinate(endpoint.getSide(), sizeX, sizeZ);
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
     * Draw a river segment at the given position with the given width and depth.
     * Creates a river bed by lowering the terrain and sets water surface as extra blocks.
     * - River bed: lowered terrain with SAND material
     * - Water surface: extra blocks at water level with WATER material
     */
    private void drawRiverSegment(WFlat flat, int centerX, int centerZ, int width, int depth,
                                   int level, String groupId) {
        int halfWidth = width / 2;

        // Get water block definition from material palette
        String waterBlockDef = getWaterBlockDef(flat);

        // Draw river bed and water surface
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Calculate distance from center for depth variation
                double distanceFromCenter = Math.sqrt(dx * dx + dz * dz);
                double maxDistance = halfWidth;

                // River bed level - deeper in the center, shallower at edges
                int bedLevel;
                if (distanceFromCenter <= maxDistance) {
                    // Smooth depth gradient from center to edge
                    double depthFactor = 1.0 - (distanceFromCenter / maxDistance);
                    bedLevel = level - (int) (depth * depthFactor);
                } else {
                    // Outside river width
                    continue;
                }

                // Get current level
                int currentLevel = flat.getLevel(x, z);

                // Only lower terrain if river bed is lower than current terrain
                // This creates the river bed
                if (bedLevel < currentLevel) {
                    flat.setLevel(x, z, bedLevel);
                    // Set river bed material to SAND
                    flat.setColumn(x, z, FlatMaterialService.SAND);

                    // Store groupId for level
                    if (groupId != null) {
                        flat.setGroup(x, z, groupId);
                    }
                }

                // Set water surface as extra block at water level
                // Water level is the original level parameter (not the lowered bed level)
                flat.setExtraBlock(x, level, z, waterBlockDef);

                // Store groupId for extra block (water surface)
                if (groupId != null) {
                    flat.setGroup(x, level, z, groupId);
                }
            }
        }

        // Add river banks (slight elevation change at edges)
        drawRiverBanks(flat, centerX, centerZ, halfWidth);
    }

    /**
     * Draw river banks - slight terrain modification at river edges.
     */
    private void drawRiverBanks(WFlat flat, int centerX, int centerZ, int halfWidth) {
        int bankWidth = 2;  // Width of bank area

        for (int dx = -(halfWidth + bankWidth); dx <= (halfWidth + bankWidth); dx++) {
            for (int dz = -(halfWidth + bankWidth); dz <= (halfWidth + bankWidth); dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Calculate distance from center
                double distanceFromCenter = Math.sqrt(dx * dx + dz * dz);

                // Only modify terrain in bank area (between river edge and bank edge)
                if (distanceFromCenter > halfWidth && distanceFromCenter <= halfWidth + bankWidth) {
                    // Get current level
                    int currentLevel = flat.getLevel(x, z);

                    // Slightly lower the bank (1-2 blocks)
                    double bankFactor = (distanceFromCenter - halfWidth) / bankWidth;
                    int bankLowering = (int) (2 * (1.0 - bankFactor));

                    int newLevel = Math.max(0, currentLevel - bankLowering);
                    flat.setLevel(x, z, newLevel);

                    // Keep existing material (don't change to water)
                }
            }
        }
    }

    /**
     * Clear all WATER extra blocks from the flat.
     * This removes previous water surfaces before building new rivers.
     */
    private void clearWaterExtraBlocks(WFlat flat) {
        // Get water block definition from material palette
        String waterBlockDef = getWaterBlockDef(flat);
        if (waterBlockDef == null) {
            log.warn("No water block definition found in material palette, skipping clear");
            return;
        }

        // Iterate through all extra blocks and remove WATER blocks
        flat.getExtraBlocks().entrySet().removeIf(entry -> waterBlockDef.equals(entry.getValue()));
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
     * River definition parsed from parameters.
     */
    @Data
    private static class RiverDefinition {
        private List<RiverEndpoint> from;
        private List<RiverEndpoint> to;
        private String groupId;
    }

    /**
     * River endpoint definition.
     * Can use either SIDE (edge of hex) or lx/lz (exact position).
     */
    @Data
    private static class RiverEndpoint {
        private WHexGrid.SIDE side;  // Side-based endpoint (NE, NW, etc.)
        private Integer lx;           // Position-based endpoint x (alternative to side)
        private Integer lz;           // Position-based endpoint z (alternative to side)
        private int width;
        private int depth;
        private int level;

        /**
         * Returns true if this endpoint uses position coordinates instead of side
         */
        public boolean hasCoordinates() {
            return lx != null && lz != null;
        }
    }
}
