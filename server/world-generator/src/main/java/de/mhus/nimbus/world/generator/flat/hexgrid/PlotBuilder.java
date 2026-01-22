package de.mhus.nimbus.world.generator.flat.hexgrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * PlotBuilder manipulator builder.
 * Creates rectangular or circular plots in hex grids.
 * Plots are areas with specified material and level.
 * <p>
 * Parameter format in HexGrid:
 * plot=[{
 *   position: { lx: 50, lz: 50 },
 *   sizeX: 20,
 *   sizeZ: 20,
 *   level: 95,
 *   material: 1,
 *   groupId: "plot-1234"
 * },
 * {
 *   center: { lx: 50, lz: 50 },
 *   size: 15,
 *   level: 96,
 *   material: 2,
 *   groupId: "plot-5678"
 * }]
 */
@Slf4j
public class PlotBuilder extends HexGridBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();
        WHexGrid hexGrid = context.getHexGrid();

        log.info("Building plots for flat: {}", flat.getFlatId());

        // Get plot parameter from hex grid
        String plotParam = hexGrid.getParameters() != null ? hexGrid.getParameters().get("plot") : null;
        if (plotParam == null || plotParam.isBlank()) {
            log.debug("No plot parameter found, skipping");
            return;
        }

        try {
            // Parse plot definitions
            List<PlotDefinition> plots = parsePlots(plotParam);
            log.debug("Parsed {} plots", plots.size());

            // Build each plot
            for (PlotDefinition plot : plots) {
                if (plot.isCircular()) {
                    buildCircularPlot(flat, plot);
                } else {
                    buildRectangularPlot(flat, plot);
                }
            }

            log.info("Plots completed for flat: {} plots built", plots.size());
        } catch (Exception e) {
            log.error("Failed to build plots for flat: {}", flat.getFlatId(), e);
        }
    }

    /**
     * Parse plot definitions from JSON string.
     */
    private List<PlotDefinition> parsePlots(String plotParam) throws Exception {
        JsonNode root = objectMapper.readTree(plotParam);
        List<PlotDefinition> plots = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode plotNode : root) {
                PlotDefinition plot = new PlotDefinition();

                // Check if circular (has center and size) or rectangular (has position and sizeX/sizeZ)
                boolean isCircular = plotNode.has("center") && plotNode.has("size");
                plot.setCircular(isCircular);

                if (isCircular) {
                    // Parse circular plot
                    JsonNode centerNode = plotNode.get("center");
                    plot.setCenterX(centerNode.get("lx").asInt());
                    plot.setCenterZ(centerNode.get("lz").asInt());
                    plot.setSize(plotNode.get("size").asInt());
                } else {
                    // Parse rectangular plot
                    if (plotNode.has("position")) {
                        JsonNode posNode = plotNode.get("position");
                        plot.setPositionX(posNode.get("lx").asInt());
                        plot.setPositionZ(posNode.get("lz").asInt());
                    }
                    plot.setSizeX(plotNode.has("sizeX") ? plotNode.get("sizeX").asInt() : 0);
                    plot.setSizeZ(plotNode.has("sizeZ") ? plotNode.get("sizeZ").asInt() : 0);
                }

                // Common parameters
                plot.setLevel(plotNode.get("level").asInt());
                plot.setMaterial(plotNode.get("material").asInt());
                plot.setGroupId(plotNode.has("groupId") ? plotNode.get("groupId").asText() : null);

                plots.add(plot);
            }
        }

        return plots;
    }

    /**
     * Build a rectangular plot.
     */
    private void buildRectangularPlot(WFlat flat, PlotDefinition plot) {
        log.debug("Building rectangular plot at ({}, {}) with size {}x{}",
                plot.getPositionX(), plot.getPositionZ(), plot.getSizeX(), plot.getSizeZ());

        int startX = plot.getPositionX();
        int startZ = plot.getPositionZ();
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

        log.debug("Rectangular plot completed at ({}, {})", plot.getPositionX(), plot.getPositionZ());
    }

    /**
     * Build a circular plot.
     */
    private void buildCircularPlot(WFlat flat, PlotDefinition plot) {
        log.debug("Building circular plot at ({}, {}) with size {}",
                plot.getCenterX(), plot.getCenterZ(), plot.getSize());

        int centerX = plot.getCenterX();
        int centerZ = plot.getCenterZ();
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

        log.debug("Circular plot completed at ({}, {})", plot.getCenterX(), plot.getCenterZ());
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
     * Plot definition parsed from parameters.
     */
    @Data
    private static class PlotDefinition {
        // Common
        private int level;
        private int material;
        private String groupId;
        private boolean circular;

        // Rectangular plot
        private int positionX;
        private int positionZ;
        private int sizeX;
        private int sizeZ;

        // Circular plot
        private int centerX;
        private int centerZ;
        private int size;
    }
}
