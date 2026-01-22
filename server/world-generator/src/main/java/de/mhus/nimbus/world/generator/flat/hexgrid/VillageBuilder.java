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
 * VillageBuilder manipulator builder.
 * Creates villages or towns with plots (for houses) and connecting roads.
 * <p>
 * Parameter format in HexGrid:
 * village={
 *   level: 95,
 *   material: 1,
 *   plots: [
 *     {
 *       lx: 50,
 *       lz: 50,
 *       level: 96,
 *       sizeX: 10,
 *       sizeZ: 10,
 *       material: 1,
 *       groupId: "plot-1234",
 *       road: 0
 *     },
 *     {
 *       lx: 80,
 *       lz: 80,
 *       level: 97,
 *       size: 8,
 *       material: 2,
 *       groupId: "plot-5678",
 *       road: 0
 *     }
 *   ],
 *   roads: [
 *     {
 *       from: { lx: 50, lz: 50, level: 95 },
 *       to: { lx: 80, lz: 80, level: 95 },
 *       width: 3,
 *       type: "street"
 *     }
 *   ]
 * }
 */
@Slf4j
public class VillageBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_ROAD_WIDTH = 3;
    private static final String DEFAULT_ROAD_TYPE = "street";

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.info("Building village for flat: {}", flat.getFlatId());

        // Get village parameter from hex grid
        String villageParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("village") : null;
        if (villageParam == null || villageParam.isBlank()) {
            log.debug("No village parameter found, skipping");
            return;
        }

        try {
            // Parse village configuration
            VillageConfig config = parseVillageConfig(villageParam);
            log.debug("Parsed village config: plots={}, roads={}", config.getPlots().size(), config.getRoads().size());

            // Step 1: Build roads
            for (RoadDefinition road : config.getRoads()) {
                buildRoad(flat, road, config.getLevel());
            }

            // Step 2: Build plots
            for (PlotDefinition plot : config.getPlots()) {
                buildPlot(flat, plot, config);
            }

            // Step 3: Build connections from plots to roads
            for (PlotDefinition plot : config.getPlots()) {
                if (plot.getRoad() >= 0 && plot.getRoad() < config.getRoads().size()) {
                    RoadDefinition road = config.getRoads().get(plot.getRoad());
                    buildPlotToRoadConnection(flat, plot, road, config);
                }
            }

            log.info("Village completed: {} plots, {} roads", config.getPlots().size(), config.getRoads().size());
        } catch (Exception e) {
            log.error("Failed to build village for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Parse village configuration from JSON string.
     */
    private VillageConfig parseVillageConfig(String villageParam) throws Exception {
        JsonNode root = objectMapper.readTree(villageParam);

        VillageConfig config = new VillageConfig();

        // Parse root level (required)
        config.setLevel(root.get("level").asInt());

        // Parse root material (optional, defaults to GRASS)
        config.setMaterial(root.has("material") ? root.get("material").asInt() : FlatMaterialService.GRASS);

        // Parse plots array
        List<PlotDefinition> plots = new ArrayList<>();
        if (root.has("plots") && root.get("plots").isArray()) {
            for (JsonNode plotNode : root.get("plots")) {
                PlotDefinition plot = new PlotDefinition();

                // Position
                plot.setLx(plotNode.get("lx").asInt());
                plot.setLz(plotNode.get("lz").asInt());

                // Level (inherits from root if not specified)
                plot.setLevel(plotNode.has("level") ? plotNode.get("level").asInt() : config.getLevel());

                // Material (inherits from root if not specified)
                plot.setMaterial(plotNode.has("material") ? plotNode.get("material").asInt() : config.getMaterial());

                // Shape: rectangular or circular
                if (plotNode.has("sizeX") && plotNode.has("sizeZ")) {
                    // Rectangular plot
                    plot.setCircular(false);
                    plot.setSizeX(plotNode.get("sizeX").asInt());
                    plot.setSizeZ(plotNode.get("sizeZ").asInt());
                } else if (plotNode.has("size")) {
                    // Circular plot
                    plot.setCircular(true);
                    plot.setSize(plotNode.get("size").asInt());
                }

                // Optional groupId
                plot.setGroupId(plotNode.has("groupId") ? plotNode.get("groupId").asText() : null);

                // Optional road connection
                plot.setRoad(plotNode.has("road") ? plotNode.get("road").asInt() : -1);

                plots.add(plot);
            }
        }
        config.setPlots(plots);

        // Parse roads array
        List<RoadDefinition> roads = new ArrayList<>();
        if (root.has("roads") && root.get("roads").isArray()) {
            for (JsonNode roadNode : root.get("roads")) {
                RoadDefinition road = new RoadDefinition();

                // From position
                JsonNode fromNode = roadNode.get("from");
                road.setFromX(fromNode.get("lx").asInt());
                road.setFromZ(fromNode.get("lz").asInt());
                road.setFromLevel(fromNode.has("level") ? fromNode.get("level").asInt() : config.getLevel());

                // To position
                JsonNode toNode = roadNode.get("to");
                road.setToX(toNode.get("lx").asInt());
                road.setToZ(toNode.get("lz").asInt());
                road.setToLevel(toNode.has("level") ? toNode.get("level").asInt() : config.getLevel());

                // Width
                road.setWidth(roadNode.has("width") ? roadNode.get("width").asInt() : DEFAULT_ROAD_WIDTH);

                // Type
                String type = roadNode.has("type") ? roadNode.get("type").asText() : DEFAULT_ROAD_TYPE;
                road.setMaterial(getMaterialForType(type));

                roads.add(road);
            }
        }
        config.setRoads(roads);

        return config;
    }

    /**
     * Get material ID for road type.
     */
    private int getMaterialForType(String type) {
        switch (type.toLowerCase()) {
            case "street":
                return FlatMaterialService.STREET;
            case "track":
                return FlatMaterialService.TRACK;
            default:
                return FlatMaterialService.STREET;
        }
    }

    /**
     * Build a single plot (rectangular or circular).
     */
    private void buildPlot(WFlat flat, PlotDefinition plot, VillageConfig config) {
        if (plot.isCircular()) {
            buildCircularPlot(flat, plot);
        } else {
            buildRectangularPlot(flat, plot);
        }
    }

    /**
     * Build a rectangular plot.
     */
    private void buildRectangularPlot(WFlat flat, PlotDefinition plot) {
        log.debug("Building rectangular plot at ({}, {}) with size {}x{}",
                plot.getLx(), plot.getLz(), plot.getSizeX(), plot.getSizeZ());

        int startX = plot.getLx();
        int startZ = plot.getLz();
        int endX = startX + plot.getSizeX();
        int endZ = startZ + plot.getSizeZ();

        for (int x = startX; x < endX; x++) {
            for (int z = startZ; z < endZ; z++) {
                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Set level and material
                flat.setLevel(x, z, plot.getLevel());
                flat.setColumn(x, z, plot.getMaterial());
            }
        }

        log.debug("Rectangular plot completed at ({}, {})", plot.getLx(), plot.getLz());
    }

    /**
     * Build a circular plot.
     */
    private void buildCircularPlot(WFlat flat, PlotDefinition plot) {
        log.debug("Building circular plot at ({}, {}) with size {}",
                plot.getLx(), plot.getLz(), plot.getSize());

        int centerX = plot.getLx();
        int centerZ = plot.getLz();
        int radius = plot.getSize() / 2;
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

                // Set level and material
                flat.setLevel(x, z, plot.getLevel());
                flat.setColumn(x, z, plot.getMaterial());
            }
        }

        log.debug("Circular plot completed at ({}, {})", plot.getLx(), plot.getLz());
    }

    /**
     * Build a straight road from one point to another.
     */
    private void buildRoad(WFlat flat, RoadDefinition road, int defaultLevel) {
        log.debug("Building road from ({}, {}) to ({}, {})",
                road.getFromX(), road.getFromZ(), road.getToX(), road.getToZ());

        int dx = road.getToX() - road.getFromX();
        int dz = road.getToZ() - road.getFromZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        // Build straight line from start to end
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Linear interpolation
            int x = (int) Math.round(road.getFromX() + t * dx);
            int z = (int) Math.round(road.getFromZ() + t * dz);

            // Linear interpolation for level
            int level = (int) Math.round(road.getFromLevel() + t * (road.getToLevel() - road.getFromLevel()));

            // Build road segment with width
            buildRoadSegment(flat, x, z, level, road.getWidth(), road.getMaterial());
        }

        log.debug("Road completed");
    }

    /**
     * Build a road segment with width.
     */
    private void buildRoadSegment(WFlat flat, int centerX, int centerZ, int level, int width, int material) {
        int halfWidth = width / 2;

        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Set level and material
                flat.setLevel(x, z, level);
                flat.setColumn(x, z, material);
            }
        }
    }

    /**
     * Build connection from plot to road (shortest path).
     */
    private void buildPlotToRoadConnection(WFlat flat, PlotDefinition plot, RoadDefinition road, VillageConfig config) {
        // Get plot center
        int plotCenterX = plot.isCircular() ? plot.getLx() : plot.getLx() + plot.getSizeX() / 2;
        int plotCenterZ = plot.isCircular() ? plot.getLz() : plot.getLz() + plot.getSizeZ() / 2;

        // Find closest point on road
        int[] closestPoint = findClosestPointOnRoad(plotCenterX, plotCenterZ, road);
        int roadX = closestPoint[0];
        int roadZ = closestPoint[1];

        log.debug("Connecting plot at ({}, {}) to road at ({}, {})",
                plotCenterX, plotCenterZ, roadX, roadZ);

        // Build straight line from plot center to road
        int dx = roadX - plotCenterX;
        int dz = roadZ - plotCenterZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(distance);

        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            int x = (int) Math.round(plotCenterX + t * dx);
            int z = (int) Math.round(plotCenterZ + t * dz);

            // Use plot level for connection
            int level = plot.getLevel();

            // Build connection segment with road width
            buildRoadSegment(flat, x, z, level, road.getWidth(), road.getMaterial());
        }

        log.debug("Plot-to-road connection completed");
    }

    /**
     * Find the closest point on a road to a given position.
     */
    private int[] findClosestPointOnRoad(int x, int z, RoadDefinition road) {
        // Calculate direction vector of road
        int dx = road.getToX() - road.getFromX();
        int dz = road.getToZ() - road.getFromZ();

        // Calculate vector from road start to point
        int px = x - road.getFromX();
        int pz = z - road.getFromZ();

        // Calculate projection parameter t (clamped to [0, 1])
        double roadLengthSquared = dx * dx + dz * dz;
        double t = 0.0;
        if (roadLengthSquared > 0) {
            t = (px * dx + pz * dz) / (double) roadLengthSquared;
            t = Math.max(0.0, Math.min(1.0, t));
        }

        // Calculate closest point on road
        int closestX = (int) Math.round(road.getFromX() + t * dx);
        int closestZ = (int) Math.round(road.getFromZ() + t * dz);

        return new int[]{closestX, closestZ};
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
     * Village configuration parsed from parameters.
     */
    @Data
    private static class VillageConfig {
        private int level;
        private int material;
        private List<PlotDefinition> plots;
        private List<RoadDefinition> roads;
    }

    /**
     * Plot definition.
     */
    @Data
    private static class PlotDefinition {
        private int lx;
        private int lz;
        private int level;
        private int material;
        private String groupId;
        private int road;  // Road index to connect to (-1 = no connection)

        // Rectangular plot
        private int sizeX;
        private int sizeZ;

        // Circular plot
        private boolean circular;
        private int size;
    }

    /**
     * Road definition.
     */
    @Data
    private static class RoadDefinition {
        private int fromX;
        private int fromZ;
        private int fromLevel;
        private int toX;
        private int toZ;
        private int toLevel;
        private int width;
        private int material;
    }
}
