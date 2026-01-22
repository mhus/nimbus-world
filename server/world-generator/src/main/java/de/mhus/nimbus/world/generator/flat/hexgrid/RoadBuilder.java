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
 * RoadBuilder manipulator builder.
 * Creates roads through hex grids from one side to another.
 * Roads can be streets or tracks with transitions to grass.
 * <p>
 * Parameter format in HexGrid:
 * road={
 *   from: [{
 *     side: "NE",
 *     width: 3,
 *     level: 50,
 *     type: "street"
 *   }],
 *   to: [{
 *     side: "SW",
 *     width: 5,
 *     level: 55,
 *     type: "street"
 *   }],
 *   groupId: "road-1234"
 * }
 */
@Slf4j
public class RoadBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
            // Parse road definition
            RoadDefinition roadDef = parseRoadDefinition(roadParam);
            log.debug("Parsed road definition: from={}, to={}, groupId={}",
                    roadDef.getFrom(), roadDef.getTo(), roadDef.getGroupId());

            // Build road for each from-to pair
            for (RoadEndpoint fromEndpoint : roadDef.getFrom()) {
                for (RoadEndpoint toEndpoint : roadDef.getTo()) {
                    buildRoad(flat, fromEndpoint, toEndpoint, roadDef.getGroupId());
                }
            }

            log.info("Roads completed for flat: {}", flat.getFlatId());
        } catch (Exception e) {
            log.error("Failed to build roads for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Parse road definition from JSON string.
     */
    private RoadDefinition parseRoadDefinition(String roadParam) throws Exception {
        JsonNode root = objectMapper.readTree(roadParam);

        RoadDefinition roadDef = new RoadDefinition();
        roadDef.setGroupId(root.has("groupId") ? root.get("groupId").asText() : null);

        // Parse from endpoints
        List<RoadEndpoint> fromList = new ArrayList<>();
        if (root.has("from") && root.get("from").isArray()) {
            for (JsonNode fromNode : root.get("from")) {
                fromList.add(parseEndpoint(fromNode));
            }
        }
        roadDef.setFrom(fromList);

        // Parse to endpoints
        List<RoadEndpoint> toList = new ArrayList<>();
        if (root.has("to") && root.get("to").isArray()) {
            for (JsonNode toNode : root.get("to")) {
                toList.add(parseEndpoint(toNode));
            }
        }
        roadDef.setTo(toList);

        return roadDef;
    }

    /**
     * Parse a single endpoint from JSON node.
     */
    private RoadEndpoint parseEndpoint(JsonNode node) {
        RoadEndpoint endpoint = new RoadEndpoint();
        endpoint.setSide(parseSide(node.get("side").asText()));
        endpoint.setWidth(node.get("width").asInt());
        endpoint.setLevel(node.get("level").asInt());
        endpoint.setType(node.has("type") ? node.get("type").asText() : "street");
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
     * Build a road from one endpoint to another.
     */
    private void buildRoad(WFlat flat, RoadEndpoint from, RoadEndpoint to, String groupId) {
        log.debug("Building road from {} to {}", from.getSide(), to.getSide());

        // Get center coordinates of the flat
        int centerX = flat.getSizeX() / 2;
        int centerZ = flat.getSizeZ() / 2;

        // Get start and end coordinates on the edges
        int[] startCoords = getSideCoordinate(from.getSide(), flat.getSizeX(), flat.getSizeZ());
        int[] endCoords = getSideCoordinate(to.getSide(), flat.getSizeX(), flat.getSizeZ());

        // Calculate road path length
        int dx = endCoords[0] - startCoords[0];
        int dz = endCoords[1] - startCoords[1];
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        // Draw road along the path
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Interpolate position
            int x = (int) (startCoords[0] + t * dx);
            int z = (int) (startCoords[1] + t * dz);

            // Interpolate width and level
            int width = (int) (from.getWidth() + t * (to.getWidth() - from.getWidth()));
            int level = (int) (from.getLevel() + t * (to.getLevel() - from.getLevel()));

            // Draw road segment with width
            drawRoadSegment(flat, x, z, width, level, from.getType(), groupId);
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
                                  String type, String groupId) {
        // Determine material based on type
        int centerMaterial = type.equalsIgnoreCase("track") ? FlatMaterialService.TRACK : FlatMaterialService.STREET;
        int transitionNorth = type.equalsIgnoreCase("track") ? FlatMaterialService.TRACK2GRASS_NORTH : FlatMaterialService.STREET2GRASS_NORTH;
        int transitionEast = type.equalsIgnoreCase("track") ? FlatMaterialService.TRACK2GRASS_EAST : FlatMaterialService.STREET2GRASS_EAST;
        int transitionSouth = type.equalsIgnoreCase("track") ? FlatMaterialService.TRACK2GRASS_SOUTH : FlatMaterialService.STREET2GRASS_SOUTH;
        int transitionWest = type.equalsIgnoreCase("track") ? FlatMaterialService.TRACK2GRASS_WEST : FlatMaterialService.STREET2GRASS_WEST;

        // Draw center and edges with transitions
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
                } else if (Math.abs(dx) > Math.abs(dz)) {
                    // Transition east/west
                    material = dx > 0 ? transitionEast : transitionWest;
                } else {
                    // Transition north/south
                    material = dz > 0 ? transitionSouth : transitionNorth;
                }

                // Set level and material
                flat.setLevel(x, z, level);
                flat.setColumn(x, z, material);

                // Store metadata for road groupId
                if (groupId != null) {
                    // TODO: Store groupId in metadata when available
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
     * Road definition parsed from parameters.
     */
    @Data
    private static class RoadDefinition {
        private List<RoadEndpoint> from;
        private List<RoadEndpoint> to;
        private String groupId;
    }

    /**
     * Road endpoint definition.
     */
    @Data
    private static class RoadEndpoint {
        private WHexGrid.SIDE side;
        private int width;
        private int level;
        private String type;
    }
}
