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
 * RiverBuilder manipulator builder.
 * Creates rivers through hex grids from one side to another.
 * Rivers carve through the terrain with configurable width and depth.
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
 */
@Slf4j
public class RiverBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.info("Building rivers for flat: {}", flat.getFlatId());

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
     */
    private RiverEndpoint parseEndpoint(JsonNode node) {
        RiverEndpoint endpoint = new RiverEndpoint();
        endpoint.setSide(parseSide(node.get("side").asText()));
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
     * Build a river from one endpoint to another.
     */
    private void buildRiver(WFlat flat, RiverEndpoint from, RiverEndpoint to, String groupId) {
        log.debug("Building river from {} to {}", from.getSide(), to.getSide());

        // Get start and end coordinates on the edges
        int[] startCoords = getSideCoordinate(from.getSide(), flat.getSizeX(), flat.getSizeZ());
        int[] endCoords = getSideCoordinate(to.getSide(), flat.getSizeX(), flat.getSizeZ());

        // Calculate river path length
        int dx = endCoords[0] - startCoords[0];
        int dz = endCoords[1] - startCoords[1];
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        // Draw river along the path
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Interpolate position
            int x = (int) (startCoords[0] + t * dx);
            int z = (int) (startCoords[1] + t * dz);

            // Interpolate width, depth and level
            int width = (int) (from.getWidth() + t * (to.getWidth() - from.getWidth()));
            int depth = (int) (from.getDepth() + t * (to.getDepth() - from.getDepth()));
            int level = (int) (from.getLevel() + t * (to.getLevel() - from.getLevel()));

            // Draw river segment with width and depth
            drawRiverSegment(flat, x, z, width, depth, level, groupId);
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
     * Creates a river bed by lowering the terrain and filling with water.
     */
    private void drawRiverSegment(WFlat flat, int centerX, int centerZ, int width, int depth,
                                   int level, String groupId) {
        int halfWidth = width / 2;

        // Draw river bed
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
                }

                // Set water material
                flat.setColumn(x, z, FlatMaterialService.WATER);

                // Store metadata for river groupId
                if (groupId != null) {
                    // TODO: Store groupId in metadata when available
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
     */
    @Data
    private static class RiverEndpoint {
        private WHexGrid.SIDE side;
        private int width;
        private int depth;
        private int level;
    }
}
