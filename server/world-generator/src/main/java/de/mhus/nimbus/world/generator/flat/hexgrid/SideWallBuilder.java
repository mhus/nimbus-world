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
 * SideWallBuilder manipulator builder.
 * Creates walls along specified sides of hex grids.
 * Walls are built at a specified distance from the edge with configurable height and width.
 * <p>
 * Parameter format in HexGrid:
 * sidewall={
 *   sides: ["NE","E", "SE"],
 *   height: 5,
 *   level: 50,
 *   width: 3,
 *   distance: 5,
 *   minimum: 3,
 *   type: 3,
 *   material: 3,
 *   respectRoad: false,
 *   respectRiver: false
 * }
 */
@Slf4j
public class SideWallBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_WIDTH = 3;
    private static final int DEFAULT_DISTANCE = 5;
    private static final int DEFAULT_TYPE = FlatMaterialService.STONE;

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.info("Building side walls for flat: {}", flat.getFlatId());

        // Get sidewall parameter from hex grid
        String wallParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("g_sidewall") : null;
        if (wallParam == null || wallParam.isBlank()) {
            log.debug("No sidewall parameter found, skipping");
            return;
        }

        try {
            // Parse wall definition
            WallDefinition wallDef = parseWallDefinition(wallParam);
            log.debug("Parsed sidewall definition: sides={}, height={}, level={}, width={}, distance={}, minimum={}, type={}, material={}, respectRoad={}, respectRiver={}",
                    wallDef.getSides(), wallDef.getHeight(), wallDef.getLevel(), wallDef.getWidth(),
                    wallDef.getDistance(), wallDef.getMinimum(), wallDef.getType(), wallDef.getMaterial(),
                    wallDef.isRespectRoad(), wallDef.isRespectRiver());

            // Build wall for each specified side
            for (WHexGrid.SIDE side : wallDef.getSides()) {
                buildWall(flat, side, wallDef);
            }

            log.info("Side walls completed for flat: {}", flat.getFlatId());
        } catch (Exception e) {
            log.error("Failed to build side walls for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Parse wall definition from JSON string.
     */
    private WallDefinition parseWallDefinition(String wallParam) throws Exception {
        JsonNode root = objectMapper.readTree(wallParam);

        WallDefinition wallDef = new WallDefinition();

        // Parse sides array
        List<WHexGrid.SIDE> sides = new ArrayList<>();
        if (root.has("sides") && root.get("sides").isArray()) {
            for (JsonNode sideNode : root.get("sides")) {
                sides.add(parseSide(sideNode.asText()));
            }
        }
        wallDef.setSides(sides);

        // Parse other parameters
        wallDef.setHeight(root.get("height").asInt());
        wallDef.setLevel(root.get("level").asInt());
        wallDef.setWidth(root.has("width") ? root.get("width").asInt() : DEFAULT_WIDTH);
        wallDef.setDistance(root.has("distance") ? root.get("distance").asInt() : DEFAULT_DISTANCE);
        wallDef.setMinimum(root.has("minimum") ? root.get("minimum").asInt() : 0);
        wallDef.setType(root.has("type") ? root.get("type").asInt() : DEFAULT_TYPE);
        wallDef.setMaterial(root.has("material") ? root.get("material").asInt() : DEFAULT_TYPE);
        wallDef.setRespectRoad(root.has("respectRoad") && root.get("respectRoad").asBoolean());
        wallDef.setRespectRiver(root.has("respectRiver") && root.get("respectRiver").asBoolean());

        return wallDef;
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
     * Build a wall along a specified side.
     */
    private void buildWall(WFlat flat, WHexGrid.SIDE side, WallDefinition wallDef) {
        log.debug("Building wall on side: {}", side);

        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();
        int distance = wallDef.getDistance();
        int width = wallDef.getWidth();

        // Get corner coordinates for this side
        int[][] sideCoords = getSideCorners(side, sizeX, sizeZ);
        int[] startCorner = sideCoords[0];
        int[] endCorner = sideCoords[1];

        // Calculate direction vector along the side
        int dx = endCorner[0] - startCorner[0];
        int dz = endCorner[1] - startCorner[1];
        double sideLength = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(sideLength);

        // Calculate perpendicular direction (inward from edge)
        int[] inwardDir = getInwardDirection(side);

        // Build wall along the side at specified distance from edge
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Position along the edge
            int edgeX = (int) (startCorner[0] + t * dx);
            int edgeZ = (int) (startCorner[1] + t * dz);

            // Position at wall distance from edge
            int wallX = edgeX + inwardDir[0] * distance;
            int wallZ = edgeZ + inwardDir[1] * distance;

            // Build wall segment with width
            buildWallSegment(flat, wallX, wallZ, width, wallDef, inwardDir);
        }
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
     * Get the inward direction (perpendicular to side, pointing into the hex).
     */
    private int[] getInwardDirection(WHexGrid.SIDE side) {
        switch (side) {
            case NORTH_WEST:
            case NORTH_EAST:
                return new int[]{0, 1};  // Inward is south
            case SOUTH_EAST:
            case SOUTH_WEST:
                return new int[]{0, -1}; // Inward is north
            case EAST:
                return new int[]{-1, 0}; // Inward is west
            case WEST:
                return new int[]{1, 0};  // Inward is east
            default:
                return new int[]{0, 0};
        }
    }

    /**
     * Build a wall segment at the given position with the given width.
     * Builds perpendicular to the wall direction.
     * Wall is interrupted when it hits a street/trail or river (if respectRoad/respectRiver is true).
     */
    private void buildWallSegment(WFlat flat, int centerX, int centerZ, int width,
                                   WallDefinition wallDef, int[] inwardDir) {
        int halfWidth = width / 2;

        // Get water block definition if needed
        String waterBlockDef = wallDef.isRespectRiver() ? getWaterBlockDef(flat) : null;

        // Calculate perpendicular direction for width
        // If wall goes north-south, width is east-west, and vice versa
        int widthDx = Math.abs(inwardDir[1]); // If inward is Z, width is X
        int widthDz = Math.abs(inwardDir[0]); // If inward is X, width is Z

        for (int offset = -halfWidth; offset <= halfWidth; offset++) {
            int x = centerX + offset * widthDx;
            int z = centerZ + offset * widthDz;

            // Check bounds
            if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                continue;
            }

            // Check if wall should respect roads and hits a street or trail
            if (wallDef.isRespectRoad()) {
                int currentMaterial = flat.getColumn(x, z);
                if (isStreetOrTrailMaterial(currentMaterial)) {
                    log.debug("Wall interrupted at ({}, {}) - street/trail present", x, z);
                    continue;
                }
            }

            // Check if wall should respect rivers and hits a river
            if (wallDef.isRespectRiver()) {
                if (hasWaterAtPosition(flat, x, z, waterBlockDef)) {
                    log.debug("Wall interrupted at ({}, {}) - river present", x, z);
                    continue;
                }
            }

            // Get current terrain level
            int currentLevel = flat.getLevel(x, z);

            // Calculate wall base level
            int wallBaseLevel = wallDef.getLevel();

            // Apply minimum height constraint
            // Wall should be at least 'minimum' blocks above current terrain
            if (wallDef.getMinimum() > 0) {
                int minimumWallBase = currentLevel + wallDef.getMinimum();
                wallBaseLevel = Math.max(wallBaseLevel, minimumWallBase);
            }

            // Build wall from base to height
            // Set the level to wall top
            int wallTopLevel = wallBaseLevel + wallDef.getHeight();
            flat.setLevel(x, z, wallTopLevel);

            // Set wall material (use material if set, otherwise type)
            int material = wallDef.getMaterial() > 0 ? wallDef.getMaterial() : wallDef.getType();
            flat.setColumn(x, z, material);
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
     * Wall definition parsed from parameters.
     */
    @Data
    private static class WallDefinition {
        private List<WHexGrid.SIDE> sides;
        private int height;
        private int level;
        private int width;
        private int distance;
        private int minimum;
        private int type;
        private int material;
        private boolean respectRoad;
        private boolean respectRiver;
    }
}
