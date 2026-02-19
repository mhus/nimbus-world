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
 * Creates rivers through hex grids from one position to another.
 * Rivers carve through the terrain with configurable width and depth.
 * Uses FastNoiseLite to create natural, curved river paths.
 * <p>
 * Parameter format in HexGrid:
 * river={
 *   from: [{
 *     position: "<NE2/4>",
 *     width: 3,
 *     depth: 2,
 *     level: 40
 *   }],
 *   to: [{
 *     position: "<SW2/4>",
 *     width: 5,
 *     depth: 2,
 *     level: 42
 *   }],
 *   groupId: "river-1234"
 * }
 * <p>
 * Position format (HexLocal):
 * - Edge positions: "<NE2/4>", "<SW1/3>" - position on hex edge (North to South)
 * - Inner positions: "<0;0>", "<1;-1>" - position within hex grid
 * <p>
 * Optional parameters:
 * - riverCurvature: Maximum lateral offset for river curves in pixels (default: 30)
 * - riverSeed: Seed for river curve generation (default: based on groupId hash)
 */
@Slf4j
public class RiverBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_CURVATURE = 30;  // Default maximum lateral offset for curves
    /**
     * Extra pixels added to hexGridSize when computing edge endpoint coordinates.
     * Pushes endpoints slightly beyond the grid boundary so adjacent river segments
     * overlap in the blending buffer zone and connect seamlessly.
     */
    private static final int GRID_EDGE_OVERLAP = 28;
    /**
     * Minimum drawing radius for river circles, ensuring thin rivers
     * produce continuous coverage without pixel gaps on diagonal paths.
     */
    private static final double MIN_DRAW_RADIUS = 1.5;

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.debug("Building rivers for flat: {}", flat.getFlatId());

        // Clear all existing WATER extra blocks before building rivers
        clearWaterExtraBlocks(flat);

        // Get river parameter from hex grid
        String riverParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("g_river") : null;
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
     * Position must be in HexLocal format: "<NE2/4>" for edge or "<0;0>" for position.
     */
    private RiverEndpoint parseEndpoint(JsonNode node) {
        RiverEndpoint endpoint = new RiverEndpoint();

        // Parse position (required)
        if (!node.has("position")) {
            throw new IllegalArgumentException("River endpoint must have 'position' field in HexLocal format (e.g., '<NE2/4>' or '<0;0>')");
        }
        endpoint.setPosition(node.get("position").asText());

        // Parse common fields
        endpoint.setWidth(node.get("width").asInt());
        endpoint.setDepth(node.get("depth").asInt());
        endpoint.setLevel(node.get("level").asInt());

        return endpoint;
    }

    /**
     * Build a river from one endpoint to another with natural curves.
     */
    private void buildRiver(WFlat flat, RiverEndpoint from, RiverEndpoint to, String groupId) {
        // Log endpoint info
        log.debug("Building river from {} to {}", from.getPosition(), to.getPosition());

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

        int lastX = -1, lastZ = -1; // Track last drawn position for logging

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

            if (lastX != -1 && lastZ != -1) {
                drawRiverLine(flat, lastX, lastZ, x, z, depth, level, groupId); // Log line drawing for debugging
            }
            // Draw river segment with width and depth
            drawRiverSegment(flat, x, z, width, depth, level, groupId);

            lastX = x;
            lastZ = z;
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
    /**
     * Get absolute WFlat coordinates from HexLocal position string.
     * Uses HexLocalUtil to parse the position and convert to absolute coordinates.
     *
     * @param endpoint the river endpoint with position in HexLocal format
     * @param sizeX WFlat width
     * @param sizeZ WFlat height
     * @return absolute coordinates [lx, lz] in WFlat coordinate system
     */
    private int[] getEndpointCoordinate(RiverEndpoint endpoint, int sizeX, int sizeZ) {
        // Use inflated hexGridSize so edge positions extend slightly beyond the
        // actual grid boundary into the blending buffer zone. This ensures adjacent
        // grids' river segments overlap and connect without gaps.
        int hexGridSize = sizeX + GRID_EDGE_OVERLAP;

        // Parse position string and get relative coordinates
        de.mhus.nimbus.generated.types.Vector2Int relativePos =
            de.mhus.nimbus.world.shared.util.HexLocalUtil.toHexgridLocalCenter(
                endpoint.getPosition(), hexGridSize);

        // Convert to absolute WFlat coordinates (center offset stays at actual sizeX/2)
        int lx = sizeX / 2 + relativePos.getX();
        int lz = sizeZ / 2 + relativePos.getZ();

        return new int[]{lx, lz};
    }

    /**
     * Draw a line between two points. Draw it like no empty edges are allowed, this means
     * the next puxel position is on one of the neighboring sides never diagonal.
     * This is important to avoid gaps in the river when drawing thin rivers on diagonal paths.
     *
     * @param flat The WFlat to modify
     * @param x1 Point 1
     * @param z1 Point 1
     * @param x2 Point 2
     * @param z2 Point 2
     * @param depth Depth for this segment
     * @param level Water level for this segment
     * @param groupId Group ID for logging and debugging
     */
    private void drawRiverLine(WFlat flat, int x1, int z1, int x2, int z2, int depth, int level, String groupId) {
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int sx = Integer.compare(x2, x1);
        int sz = Integer.compare(z2, z1);

        String waterBlockDef = getWaterBlockDef(flat);
        int bedLevel = level - depth;

        int x = x1;
        int z = z1;

        // 4-connected Bresenham: total steps = |dx| + |dz|, one axis per step
        int steps = dx + dz;
        int err = dx - dz;

        for (int i = 0; i <= steps; i++) {
            // Set water and lower terrain at current position (bounds check)
            if (x >= 0 && x < flat.getSizeX() && z >= 0 && z < flat.getSizeZ()) {
                // Lower terrain if river bed is below current terrain, like drawRiverSegment
                int currentLevel = flat.getLevel(x, z);
                if (bedLevel < currentLevel) {
                    flat.setLevel(x, z, bedLevel);
                    flat.setColumn(x, z, FlatMaterialService.SAND);
                    if (groupId != null) {
                        flat.setGroup(x, z, groupId);
                    }
                }
                // Set water surface extra block at water level
                flat.setExtraBlock(x, level, z, waterBlockDef);
                if (groupId != null) {
                    flat.setGroup(x, level, z, groupId);
                }
            }

            if (i == steps) break;

            // Step in exactly one direction: positive err favors x, negative favors z
            if (err > 0 || (err == 0 && dx >= dz)) {
                x += sx;
                err -= 2 * dz;
            } else {
                z += sz;
                err += 2 * dx;
            }
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

        // Effective drawing radius: at least MIN_DRAW_RADIUS so thin rivers
        // produce overlapping circles without gaps on diagonal paths
        double effectiveRadius = Math.max(MIN_DRAW_RADIUS, halfWidth);
        int drawRadius = (int) Math.ceil(effectiveRadius);

        // Get water block definition from material palette
        String waterBlockDef = getWaterBlockDef(flat);

        // Draw river bed and water surface
        for (int dx = -drawRadius; dx <= drawRadius; dx++) {
            for (int dz = -drawRadius; dz <= drawRadius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Calculate distance from center for depth variation
                double distanceFromCenter = Math.sqrt(dx * dx + dz * dz);

                // River bed level - deeper in the center, shallower at edges
                int bedLevel;
                if (distanceFromCenter <= effectiveRadius) {
                    // Smooth depth gradient based on configured width (not effective radius)
                    double depthFactor = halfWidth > 0
                        ? Math.max(0.0, 1.0 - (distanceFromCenter / halfWidth))
                        : 1.0;
                    bedLevel = level - (int) (depth * depthFactor);
                } else {
                    // Outside drawing radius
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
        private String position;  // HexLocal format: "<NE2/4>" for edge or "<0;0>" for position
        private int width;
        private int depth;
        private int level;
    }
}
