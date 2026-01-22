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
 *   lx: 50,
 *   lz: 50,
 *   sizeX: 20,
 *   sizeZ: 20,
 *   level: 95,
 *   material: 1,
 *   groupId: "plot-1234"
 * },
 * {
 *   lx: 50,
 *   lz: 50,
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

                // Position (required for all plots)
                plot.setLx(plotNode.get("lx").asInt());
                plot.setLz(plotNode.get("lz").asInt());

                // Check if circular (has size) or rectangular (has sizeX and sizeZ)
                boolean isCircular = plotNode.has("size");
                plot.setCircular(isCircular);

                if (isCircular) {
                    // Parse circular plot
                    plot.setSize(plotNode.get("size").asInt());
                } else {
                    // Parse rectangular plot
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
        // Position (common for all plots)
        private int lx;
        private int lz;

        // Common properties
        private int level;
        private int material;
        private String groupId;
        private boolean circular;

        // Rectangular plot (sizeX and sizeZ)
        private int sizeX;
        private int sizeZ;

        // Circular plot (size)
        private int size;
    }
}
