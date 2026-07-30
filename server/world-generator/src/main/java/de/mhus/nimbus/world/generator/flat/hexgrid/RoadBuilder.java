package de.mhus.nimbus.world.generator.flat.hexgrid;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

/**
 * RoadBuilder manipulator builder.
 * Creates roads from positions to the center where they all meet.
 * Roads can be streets or trails with transitions to grass.
 * Uses sinusoidal curves for natural, slightly curved roads.
 * Builds bridges when crossing rivers.
 * <p>
 * Parameter format in HexGrid:
 * road={
 *   position: "<0;0>",
 *   level: 95,
 *   plazaSize: 30,
 *   plazaMaterial: "street",
 *   route: [
 *     {
 *       position: "<NE2/4>",
 *       width: 3,
 *       level: 50,
 *       type: "street"
 *     },
 *     {
 *       position: "<1;-1>",
 *       width: 4,
 *       level: 55,
 *       type: "street"
 *     }
 *   ]
 * }
 * <p>
 * Position format (HexLocal):
 * - Edge positions: "<NE2/4>", "<SW1/3>" - position on hex edge (North to South)
 * - Inner positions: "<0;0>", "<1;-1>" - position within hex grid
 * <p>
 * Optional parameters:
 * - position: Center position (default: flat center)
 * - level: Center level (default: calculated from average toLevel of all roads)
 * - roadCurvature: Maximum lateral offset for road curves in pixels (default: 10)
 * - roadWaves: Number of sine wave cycles along the road (default: 1.5)
 * - plazaSize: Size of plaza at center (default: 0 = no plaza)
 * - plazaMaterial: Material for plaza (default: best material from routes, street > trail)
 * <p>
 * Note: The center level is automatically calculated as the average toLevel of all roads
 * arriving at the center. If specified in the configuration, it's used as a fallback.
 */
@Slf4j
public class RoadBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    /**
     * Extra pixels added to hexGridSize when computing edge endpoint coordinates.
     * Pushes endpoints slightly beyond the grid boundary so adjacent road segments
     * overlap in the blending buffer zone and connect seamlessly.
     */
    private static final int GRID_EDGE_OVERLAP = 24;

    // Legacy parameters (kept for backward compatibility)
    private static final int DEFAULT_CURVATURE = 10;  // Default maximum lateral offset for curves
    private static final double DEFAULT_WAVES = 1.5;  // Default number of sine wave cycles

    // Maximum extra blocks to extend road width when cutting through higher terrain
    private static final int MAX_TERRAIN_EXTENSION = 4;

    // New pathfinding parameters
    private static final int DEFAULT_MAX_SLOPE = 1;  // Maximum elevation change per block
    private static final int DEFAULT_TERRAIN_THRESHOLD = 5;  // Distance from terrain to trigger TerrainPathFinder
    private static final double DEFAULT_MAX_DRIFT_RATIO = 1.5;  // Max path length ratio (1.5 = 50% longer)
    private static final double DEFAULT_STRAIGHTNESS = 0.7;  // 0.0 = very curvy, 1.0 = straight

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.debug("Building roads for flat: {}", flat.getFlatId());

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

            // Calculate center level from average toLevel of all roads
            int centerLevel = calculateCenterLevel(config);

            log.debug("Parsed {} roads with center at ({}, {}) and level {}",
                    config.getRoute().size(), centerX, centerZ, centerLevel);

            // Build each road from its side to the center
            for (Road road : config.getRoute()) {
                buildRoadToCenter(flat, road, centerX, centerZ, centerLevel);
            }

            // Fill center point to ensure all roads meet (prevent gaps)
            if (!config.getRoute().isEmpty()) {
                fillCenterPoint(flat, centerX, centerZ, centerLevel, config);
            }

            // Build plaza at center if configured
            if (config.getCenter().getPlazaSize() > 0) {
                String plazaMaterial = determinePlazaMaterial(config);
                buildPlaza(flat, centerX, centerZ, centerLevel, config.getCenter().getPlazaSize(), plazaMaterial);
            }

            log.debug("Roads completed for flat: {} roads built", config.getRoute().size());
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
        center.setPosition(root.has("position") ? root.get("position").asText() : null);  // null means use flat center
        center.setLevel(root.has("level") ? root.get("level").asInt() : 0);
        center.setPlazaSize(root.has("plazaSize") ? root.get("plazaSize").asInt() : 0);
        center.setPlazaMaterial(root.has("plazaMaterial") ? root.get("plazaMaterial").asText() : null);
        config.setCenter(center);

        // Parse route array
        List<Road> roads = new ArrayList<>();
        if (root.has("route") && root.get("route").isArray()) {
            for (JsonNode roadNode : root.get("route")) {
                Road road = new Road();

                // Support multiple position formats:
                // 1. "position": "<NE 2>" (HexLocal format - preferred)
                // 2. "lx": 256, "lz": 256 (absolute coordinates)
                // 3. "side": "NORTH_EAST" (EDGE enum - legacy)

                if (roadNode.has("position")) {
                    // HexLocal format (preferred)
                    road.setPosition(roadNode.get("position").asText());
                } else if (roadNode.has("lx") && roadNode.has("lz")) {
                    // Absolute coordinates - convert to position string format
                    int lx = roadNode.get("lx").asInt();
                    int lz = roadNode.get("lz").asInt();
                    road.setPosition(String.format("<%d;%d>", lx, lz));
                } else if (roadNode.has("side")) {
                    // EDGE enum (legacy) - convert to HexLocal edge format
                    String sideStr = roadNode.get("side").asText();
                    // Convert "NORTH_EAST" to "NE", etc.
                    String edgeShort = convertEdgeToShortForm(sideStr);
                    road.setPosition(String.format("<%s 2>", edgeShort));  // Default to middle of edge (2/4)
                } else {
                    throw new IllegalArgumentException("Road route must have 'position' (HexLocal), 'lx'/'lz' (coordinates), or 'side' (EDGE) field");
                }

                road.setWidth(roadNode.get("width").asInt());

                // Read fromLevel/toLevel if available, otherwise fall back to level
                if (roadNode.has("fromLevel") && roadNode.has("toLevel")) {
                    road.setFromLevel(roadNode.get("fromLevel").asInt());
                    road.setToLevel(roadNode.get("toLevel").asInt());
                    road.setLevel(road.getFromLevel()); // Backward compatibility
                } else if (roadNode.has("level")) {
                    int level = roadNode.get("level").asInt();
                    road.setLevel(level);
                    road.setFromLevel(level);  // Use same level for both
                    road.setToLevel(level);
                } else {
                    throw new IllegalArgumentException("Road route must have 'level' or 'fromLevel'/'toLevel' fields");
                }

                road.setType(roadNode.has("type") ? roadNode.get("type").asText() : "street");
                roads.add(road);
            }
        }
        config.setRoute(roads);

        return config;
    }

    /**
     * Build a road from a position to the center using intelligent pathfinding.
     * Chooses between TerrainPathFinder (terrain-adaptive) and StraightPathFinder (elevated/deep).
     */
    private void buildRoadToCenter(WFlat flat, Road road, int centerX, int centerZ, int centerLevel) {
        // Get start coordinates from position string (with edge overlap for grid transitions)
        int[] startCoords = getAbsoluteCoordinates(road.getPosition(), flat.getSizeX(), flat.getSizeZ(), GRID_EDGE_OVERLAP);
        int startX = startCoords[0];
        int startZ = startCoords[1];

        // Use fromLevel if available, otherwise fall back to level
        int startLevel = road.getFromLevel() != null ? road.getFromLevel() : road.getLevel();
        // Ensure startLevel is at least 1 (roads must be above sea level)
        startLevel = Math.max(1, startLevel);

        // Use toLevel if available, otherwise use centerLevel or startLevel as fallback
        int endLevel;
        if (road.getToLevel() != null) {
            endLevel = road.getToLevel();
        } else if (centerLevel > 0) {
            endLevel = centerLevel;
        } else {
            endLevel = startLevel;
        }
        // Ensure endLevel is at least 1 (roads must be above sea level)
        endLevel = Math.max(1, endLevel);

        log.debug("Building road from {} (fromLevel={}, toLevel={}) to center ({},{}) (centerLevel={})",
            road.getPosition(), startLevel, endLevel, centerX, centerZ, centerLevel);

        // Read pathfinding parameters
        int maxSlopePerBlock = parseIntParameter(parameters, "maxSlopePerBlock", DEFAULT_MAX_SLOPE);
        int terrainThreshold = parseIntParameter(parameters, "terrainThreshold", DEFAULT_TERRAIN_THRESHOLD);
        double maxDriftRatio = parseDoubleParameter(parameters, "maxDriftRatio", DEFAULT_MAX_DRIFT_RATIO);
        double straightness = parseDoubleParameter(parameters, "straightness", DEFAULT_STRAIGHTNESS);
        int maxLateralOffset = parseIntParameter(parameters, "roadCurvature", DEFAULT_CURVATURE);

        // Determine which pathfinder to use
        List<TerrainPathFinder.PathPoint> path = null;
        boolean useTerrainPathfinder = shouldUseTerrainPathfinder(
            flat, startX, startZ, startLevel, centerX, centerZ, endLevel, terrainThreshold);

        if (useTerrainPathfinder) {
            // Try terrain-adaptive pathfinding
            log.debug("Using TerrainPathFinder (road near terrain)");
            TerrainPathFinder terrainFinder = new TerrainPathFinder(flat, maxSlopePerBlock, maxDriftRatio);
            path = terrainFinder.findPath(startX, startZ, startLevel, centerX, centerZ, endLevel);

            if (path == null) {
                log.debug("TerrainPathFinder failed, falling back to StraightPathFinder");
            }
        }

        // Fall back to straight pathfinding if terrain pathfinder not used or failed
        if (path == null) {
            log.debug("Using StraightPathFinder (road elevated/deep or terrain path blocked)");
            long seed = flat.getFlatId().hashCode();  // Deterministic seed
            StraightPathFinder straightFinder = new StraightPathFinder(
                maxSlopePerBlock, straightness, maxLateralOffset, seed);
            path = straightFinder.findPath(startX, startZ, startLevel, centerX, centerZ, endLevel);
        }

        // Draw the road along the path
        if (path != null && !path.isEmpty()) {
            // Draw segments between consecutive path points
            for (int i = 0; i < path.size() - 1; i++) {
                TerrainPathFinder.PathPoint p1 = path.get(i);
                TerrainPathFinder.PathPoint p2 = path.get(i + 1);

                // Calculate direction
                double dirX = p2.x - p1.x;
                double dirZ = p2.z - p1.z;
                double dirLength = Math.sqrt(dirX * dirX + dirZ * dirZ);

                if (dirLength > 0.001) {
                    dirX /= dirLength;
                    dirZ /= dirLength;

                    // Interpolate points between p1 and p2
                    int steps = (int) Math.ceil(dirLength);
                    for (int step = 0; step <= steps; step++) {
                        double t = steps > 0 ? (double) step / steps : 0;
                        int x = (int) Math.round(p1.x + t * (p2.x - p1.x));
                        int z = (int) Math.round(p1.z + t * (p2.z - p1.z));
                        int level = (int) Math.round(p1.level + t * (p2.level - p1.level));

                        drawRoadSegment(flat, x, z, road.getWidth(), level, road.getType(), dirX, dirZ);
                    }
                }
            }

            // Draw last point
            if (!path.isEmpty()) {
                TerrainPathFinder.PathPoint lastPoint = path.get(path.size() - 1);
                double dirX = 0, dirZ = 1;
                if (path.size() > 1) {
                    TerrainPathFinder.PathPoint prevPoint = path.get(path.size() - 2);
                    dirX = lastPoint.x - prevPoint.x;
                    dirZ = lastPoint.z - prevPoint.z;
                    double dirLength = Math.sqrt(dirX * dirX + dirZ * dirZ);
                    if (dirLength > 0.001) {
                        dirX /= dirLength;
                        dirZ /= dirLength;
                    }
                }
                drawRoadSegment(flat, lastPoint.x, lastPoint.z, road.getWidth(), lastPoint.level, road.getType(), dirX, dirZ);
            }

            log.debug("Road built with {} segments", path.size());
        } else {
            log.warn("Failed to generate path for road from {} to center", road.getPosition());
        }
    }

    /**
     * Determines whether to use TerrainPathFinder based on road's distance from terrain.
     *
     * @param terrainThreshold If road is within this distance from terrain, use TerrainPathFinder
     * @return true if TerrainPathFinder should be used
     */
    private boolean shouldUseTerrainPathfinder(WFlat flat, int startX, int startZ, int startLevel,
                                                 int endX, int endZ, int endLevel, int terrainThreshold) {
        // Sample a few points along the direct path to check terrain distance
        int samples = 5;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            int x = (int) (startX + t * (endX - startX));
            int z = (int) (startZ + t * (endZ - startZ));
            int level = (int) (startLevel + t * (endLevel - startLevel));

            // Check bounds
            if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                continue;
            }

            int terrainLevel = flat.getLevel(x, z);
            int distanceFromTerrain = Math.abs(level - terrainLevel);

            if (distanceFromTerrain <= terrainThreshold) {
                // Road is close to terrain - use terrain pathfinder
                return true;
            }
        }

        // Road is elevated or deep - use straight pathfinder
        return false;
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
     * Get absolute WFlat coordinates from HexLocal position string.
     * Uses HexLocalUtil to parse the position and convert to absolute coordinates.
     *
     * @param position the position in HexLocal format (e.g., "<NE2/4>" or "<0;0>")
     * @param sizeX WFlat width
     * @param sizeZ WFlat height
     * @return absolute coordinates [lx, lz] in WFlat coordinate system
     */
    private int[] getAbsoluteCoordinates(String position, int sizeX, int sizeZ) {
        return getAbsoluteCoordinates(position, sizeX, sizeZ, 0);
    }

    /**
     * Get absolute WFlat coordinates from HexLocal position string with optional edge overlap.
     * The edgeOverlap inflates the hexGridSize so edge positions extend beyond the grid boundary
     * into the blending buffer zone, ensuring adjacent grids' road segments connect.
     *
     * @param position the position in HexLocal format (e.g., "<NE2/4>" or "<0;0>")
     * @param sizeX WFlat width
     * @param sizeZ WFlat height
     * @param edgeOverlap extra pixels to add to hexGridSize for edge overlap
     * @return absolute coordinates [lx, lz] in WFlat coordinate system
     */
    private int[] getAbsoluteCoordinates(String position, int sizeX, int sizeZ, int edgeOverlap) {
        // Inflate hexGridSize so edge positions extend into the blending buffer zone
        int hexGridSize = sizeX + edgeOverlap;

        // Parse position string and get relative coordinates
        de.mhus.nimbus.generated.types.Vector2Int relativePos =
            de.mhus.nimbus.world.shared.util.HexLocalUtil.toHexgridLocalCenter(position, hexGridSize);

        // Convert to absolute WFlat coordinates (center offset stays at actual sizeX/2)
        int lx = sizeX / 2 + relativePos.getX();
        int lz = sizeZ / 2 + relativePos.getZ();

        return new int[]{lx, lz};
    }

    /**
     * Draw a road segment at the given position with the given width.
     *
     * @param dirX Direction X component (normalized)
     * @param dirZ Direction Z component (normalized)
     */
    private void drawRoadSegment(WFlat flat, int centerX, int centerZ, int width, int level,
                                  String type, double dirX, double dirZ) {
        // Determine material based on type
        boolean isTrail = type.equalsIgnoreCase("trail") || type.equalsIgnoreCase("path");
        int centerMaterial = type.equalsIgnoreCase("trail") ? FlatMaterialService.TRAIL : FlatMaterialService.STREET;
        int borderMaterial = type.equalsIgnoreCase("trail") ? FlatMaterialService.TRAIL_BORDER : FlatMaterialService.STREET_BORDER;
        int bridgeMaterial = type.equalsIgnoreCase("trail") ? FlatMaterialService.TRAIL_BRIDGE : FlatMaterialService.STREET_BRIDGE;

        // Get water block definition
        String waterBlockDef = getWaterBlockDef(flat);

        // Calculate perpendicular direction (rotate 90 degrees)
        // If direction is (dx, dz), perpendicular is (-dz, dx)
        double perpX = -dirZ;
        double perpZ = dirX;

        // Use a set to trail unique positions (avoid duplicates)
        Set<String> drawnPositions = new HashSet<>();

        int effectiveHalfWidth;
        if (isTrail) {
            // TRAIL: Bounding-box scan with perpendicular distance check
            // Guarantees gap-free pixel coverage at any angle
            double halfWidth = width / 2.0;
            effectiveHalfWidth = (int) Math.ceil(halfWidth);
            double halfWidthThreshold = halfWidth + 0.5;
            for (int dx = -effectiveHalfWidth - 1; dx <= effectiveHalfWidth + 1; dx++) {
                for (int dz = -effectiveHalfWidth - 1; dz <= effectiveHalfWidth + 1; dz++) {
                    double perpDist = dx * perpX + dz * perpZ;
                    if (Math.abs(perpDist) > halfWidthThreshold) continue;

                    int x = centerX + dx;
                    int z = centerZ + dz;

                    // Create position key for duplicate checking
                    String posKey = x + "," + z;
                    if (drawnPositions.contains(posKey)) {
                        continue;  // Skip if already drawn
                    }
                    drawnPositions.add(posKey);

                    drawRoadBlock(flat, x, z, level, perpDist, centerMaterial, borderMaterial, bridgeMaterial, waterBlockDef);
                }
            }
        } else {
            // STREET/ROAD: Bounding-box scan with perpendicular distance check
            // Guarantees gap-free pixel coverage at any angle
            effectiveHalfWidth = width / 2;
            double halfWidthThreshold = effectiveHalfWidth + 0.5;
            for (int dx = -effectiveHalfWidth - 1; dx <= effectiveHalfWidth + 1; dx++) {
                for (int dz = -effectiveHalfWidth - 1; dz <= effectiveHalfWidth + 1; dz++) {
                    double perpDist = dx * perpX + dz * perpZ;
                    if (Math.abs(perpDist) > halfWidthThreshold) continue;

                    int x = centerX + dx;
                    int z = centerZ + dz;

                    // Create position key for duplicate checking
                    String posKey = x + "," + z;
                    if (drawnPositions.contains(posKey)) {
                        continue;  // Skip if already drawn
                    }
                    drawnPositions.add(posKey);

                    drawRoadBlock(flat, x, z, level, perpDist, centerMaterial, borderMaterial, bridgeMaterial, waterBlockDef);
                }
            }
        }

        // Extend road width where adjacent terrain is higher than road level.
        // This ensures players can walk on roads that cut through elevated terrain.
        extendRoadForHigherTerrain(flat, centerX, centerZ, level, effectiveHalfWidth,
            perpX, perpZ, centerMaterial, borderMaterial, bridgeMaterial, waterBlockDef, drawnPositions);
    }

    /**
     * Extend road width on both sides where adjacent terrain is higher than road level.
     * When a road cuts through elevated terrain, the road needs to be wider so players
     * can walk on it without being blocked by the terrain wall at the road edge.
     */
    private void extendRoadForHigherTerrain(WFlat flat, int centerX, int centerZ, int level,
                                             int effectiveHalfWidth, double perpX, double perpZ,
                                             int centerMaterial, int borderMaterial, int bridgeMaterial,
                                             String waterBlockDef, Set<String> drawnPositions) {
        // Extend on negative perpendicular side
        for (int ext = effectiveHalfWidth + 1; ext <= effectiveHalfWidth + MAX_TERRAIN_EXTENSION; ext++) {
            int x = (int) Math.round(centerX + (-ext) * perpX);
            int z = (int) Math.round(centerZ + (-ext) * perpZ);
            if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) break;
            if (flat.getLevel(x, z) <= level) break;
            String posKey = x + "," + z;
            if (!drawnPositions.contains(posKey)) {
                drawnPositions.add(posKey);
                drawRoadBlock(flat, x, z, level, -ext, centerMaterial, borderMaterial, bridgeMaterial, waterBlockDef);
            }
        }

        // Extend on positive perpendicular side
        for (int ext = effectiveHalfWidth + 1; ext <= effectiveHalfWidth + MAX_TERRAIN_EXTENSION; ext++) {
            int x = (int) Math.round(centerX + ext * perpX);
            int z = (int) Math.round(centerZ + ext * perpZ);
            if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) break;
            if (flat.getLevel(x, z) <= level) break;
            String posKey = x + "," + z;
            if (!drawnPositions.contains(posKey)) {
                drawnPositions.add(posKey);
                drawRoadBlock(flat, x, z, level, ext, centerMaterial, borderMaterial, bridgeMaterial, waterBlockDef);
            }
        }
    }

    /**
     * Draw a single road block at the given position.
     *
     * @param offset Distance from road center (for material determination)
     */
    private void drawRoadBlock(WFlat flat, int x, int z, int level, double offset,
                                int centerMaterial, int borderMaterial, int bridgeMaterial,
                                String waterBlockDef) {
        // Check bounds
        if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
            return;
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
            // Determine material based on distance from center
            int material;
            if (Math.abs(offset) <= 1.0) {
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
     * street is better than trail.
     */
    private String determinePlazaMaterial(RoadConfiguration config) {
        // If explicitly specified, use that
        if (config.getCenter().getPlazaMaterial() != null && !config.getCenter().getPlazaMaterial().isBlank()) {
            return config.getCenter().getPlazaMaterial();
        }

        // Otherwise, find the best material from routes
        // street is better than trail
        boolean hasStreet = false;
        for (Road road : config.getRoute()) {
            if ("street".equalsIgnoreCase(road.getType())) {
                hasStreet = true;
                break;
            }
        }

        return hasStreet ? "street" : "trail";
    }

    /**
     * Calculate center level from average toLevel of all roads arriving at the center.
     * Falls back to configured center level if available, otherwise uses road levels.
     */
    private int calculateCenterLevel(RoadConfiguration config) {
        int totalLevel = 0;
        int roadCount = 0;
        int configuredCenterLevel = config.getCenter().getLevel();

        for (Road road : config.getRoute()) {
            // Get the level this road arrives at (toLevel or fallback to centerLevel)
            int arrivalLevel;
            if (road.getToLevel() != null) {
                arrivalLevel = road.getToLevel();
            } else if (configuredCenterLevel > 0) {
                arrivalLevel = configuredCenterLevel;
            } else {
                // Fallback: use fromLevel or level
                arrivalLevel = road.getFromLevel() != null ? road.getFromLevel() : road.getLevel();
            }

            totalLevel += arrivalLevel;
            roadCount++;
        }

        // Use average level of arriving roads, or configured center level as fallback
        int centerLevel = roadCount > 0 ? totalLevel / roadCount : configuredCenterLevel;
        // Ensure minimum level 1 (above sea)
        return Math.max(1, centerLevel);
    }

    /**
     * Fill the center point to ensure all roads meet without gaps.
     * Draws a small circle around the center to connect all incoming roads.
     */
    private void fillCenterPoint(WFlat flat, int centerX, int centerZ, int centerLevel, RoadConfiguration config) {
        // Determine material and maximum width from roads
        String plazaMaterial = determinePlazaMaterial(config);
        int material = plazaMaterial.equalsIgnoreCase("trail") ? FlatMaterialService.TRAIL : FlatMaterialService.STREET;

        // Get water block definition
        String waterBlockDef = getWaterBlockDef(flat);

        // Find maximum road width to determine fill radius
        int maxWidth = 0;
        for (Road road : config.getRoute()) {
            if (road.getWidth() > maxWidth) {
                maxWidth = road.getWidth();
            }
        }

        // Fill radius should be at least half the maximum width, minimum 2
        int fillRadius = Math.max(2, maxWidth / 2 + 1);

        // Draw small circle at center to connect all roads
        for (int dx = -fillRadius; dx <= fillRadius; dx++) {
            for (int dz = -fillRadius; dz <= fillRadius; dz++) {
                // Check if point is within circle
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > fillRadius * fillRadius) {
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

                // Don't draw where water is present
                if (hasWater) {
                    continue;
                }

                // Set level and material
                flat.setLevel(x, z, centerLevel);
                flat.setColumn(x, z, material);
            }
        }

        log.debug("Filled center point at ({}, {}) with radius {} and level {}",
            centerX, centerZ, fillRadius, centerLevel);
    }

    /**
     * Build a plaza at the center point.
     * Plaza is a circular area with the specified material.
     * If water is present, the plaza is not drawn at that position.
     */
    private void buildPlaza(WFlat flat, int centerX, int centerZ, int level, int plazaSize, String plazaMaterial) {
        log.debug("Building plaza at ({}, {}) with size {} and material {}", centerX, centerZ, plazaSize, plazaMaterial);

        // Determine material based on type
        int material = plazaMaterial.equalsIgnoreCase("trail") ? FlatMaterialService.TRAIL : FlatMaterialService.STREET;

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
     * Clear all STREET_BRIDGE and TRAIL_BRIDGE extra blocks from the flat.
     * This removes previous bridges before building new roads.
     */
    private void clearBridgeExtraBlocks(WFlat flat) {
        // Get bridge block definitions from material palette
        String streetBridgeDef = getBridgeBlockDef(flat, FlatMaterialService.STREET_BRIDGE);
        String trailBridgeDef = getBridgeBlockDef(flat, FlatMaterialService.TRAIL_BRIDGE);

        if (streetBridgeDef == null && trailBridgeDef == null) {
            log.warn("No bridge block definitions found in material palette, skipping clear");
            return;
        }

        // Iterate through all extra blocks and remove bridge blocks
        flat.getExtraBlocks().entrySet().removeIf(entry -> {
            String blockDef = entry.getValue();
            return streetBridgeDef.equals(blockDef) || trailBridgeDef.equals(blockDef);
        });

        log.debug("Cleared all bridge extra blocks");
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
     * Road definition from a side or position to the center.
     * Either side OR position (lx, lz) must be set.
     */
    @Data
    private static class Road {
        private String position;  // HexLocal format: "<NE2/4>" for edge or "<0;0>" for position
        private int width;
        private int level;        // Deprecated: use fromLevel/toLevel
        private Integer fromLevel; // Level at entry point
        private Integer toLevel;   // Level at exit point
        private String type;
    }

    /**
     * Converts EDGE enum name to short form for HexLocal format.
     * NORTH_EAST -> NE, SOUTH_WEST -> SW, EAST -> E, etc.
     */
    private String convertEdgeToShortForm(String edgeName) {
        return switch (edgeName) {
            case "NORTH_EAST" -> "NE";
            case "EAST" -> "E";
            case "SOUTH_EAST" -> "SE";
            case "SOUTH_WEST" -> "SW";
            case "WEST" -> "W";
            case "NORTH_WEST" -> "NW";
            default -> edgeName; // Fallback to original
        };
    }

    /**
     * Center definition with position, level and optional plaza.
     */
    @Data
    private static class CenterDefinition {
        private String position;  // HexLocal format: "<NE2/4>" for edge or "<0;0>" for position
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
