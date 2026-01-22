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
 * Creates roads from hex grid sides to the center where they all meet.
 * Roads can be streets or trails with transitions to grass.
 * <p>
 * Parameter format in HexGrid:
 * road=[
 *   {
 *     side: "NE",
 *     width: 3,
 *     level: 50,
 *     type: "street"
 *   },
 *   {
 *     side: "SW",
 *     width: 5,
 *     level: 55,
 *     type: "trail"
 *   }
 * ]
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
            // Parse road definitions
            List<Road> roads = parseRoads(roadParam);
            log.debug("Parsed {} roads", roads.size());

            // Calculate center of the flat
            int centerX = flat.getSizeX() / 2;
            int centerZ = flat.getSizeZ() / 2;

            // Build each road from its side to the center
            for (Road road : roads) {
                buildRoadToCenter(flat, road, centerX, centerZ);
            }

            log.info("Roads completed for flat: {} roads built", roads.size());
        } catch (Exception e) {
            log.error("Failed to build roads for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Parse road definitions from JSON string.
     */
    private List<Road> parseRoads(String roadParam) throws Exception {
        JsonNode root = objectMapper.readTree(roadParam);
        List<Road> roads = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode roadNode : root) {
                Road road = new Road();
                road.setSide(parseSide(roadNode.get("side").asText()));
                road.setWidth(roadNode.get("width").asInt());
                road.setLevel(roadNode.get("level").asInt());
                road.setType(roadNode.has("type") ? roadNode.get("type").asText() : "street");
                roads.add(road);
            }
        }

        return roads;
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
     * Build a road from a side to the center of the hex grid.
     */
    private void buildRoadToCenter(WFlat flat, Road road, int centerX, int centerZ) {
        log.debug("Building road from {} to center", road.getSide());

        // Get start coordinates at the edge
        int[] startCoords = getSideCoordinate(road.getSide(), flat.getSizeX(), flat.getSizeZ());

        // Calculate road path from edge to center
        int dx = centerX - startCoords[0];
        int dz = centerZ - startCoords[1];
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        // Draw road along the path from edge to center
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Interpolate position
            int x = (int) (startCoords[0] + t * dx);
            int z = (int) (startCoords[1] + t * dz);

            // Width stays constant (no interpolation needed)
            int width = road.getWidth();
            int level = road.getLevel();

            // Draw road segment
            drawRoadSegment(flat, x, z, width, level, road.getType());
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
}
