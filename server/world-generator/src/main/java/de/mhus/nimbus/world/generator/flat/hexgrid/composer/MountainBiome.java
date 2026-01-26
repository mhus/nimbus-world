package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mountain biome with configurable height levels.
 *
 * Supports different mountain heights:
 * - HIGH_PEAKS: landLevel=150, landOffset=40 (max height ~240 blocks)
 * - MEDIUM_PEAKS: landLevel=120, landOffset=30 (max height ~200 blocks)
 * - LOW_PEAKS: landLevel=100, landOffset=20 (max height ~170 blocks)
 * - MEADOW: landLevel=80, landOffset=10 (max height ~140 blocks)
 *
 * Default configuration:
 * - Uses MountainBuilder (g_builder="mountain")
 * - High roughness (g_roughness=0.8) for jagged peaks
 * - Defaults to MEDIUM_PEAKS if height not specified
 *
 * Example usage in JSON:
 * <pre>
 * {
 *   "featureType": "biome",
 *   "type": "MOUNTAINS",
 *   "name": "alpine-peaks",
 *   "size": "LARGE",
 *   "height": "HIGH_PEAKS"
 * }
 * </pre>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MountainBiome extends Biome {

    /**
     * Peak height configuration for this mountain biome.
     * Determines landLevel and landOffset parameters.
     */
    private MountainHeight height;

    /**
     * Mountain height presets with land level and offset values.
     * Formula: maxLevel = landLevel + oceanLevel + landOffset (terrain)
     *          ridgeLevel = landLevel + oceanLevel + landOffset + ridgeOffset (peaks)
     * (with oceanLevel typically = 50)
     */
    public enum MountainHeight {
        HIGH_PEAKS(150, 40, 20),    // max level: 150+50+40 = 240, ridge: 260
        MEDIUM_PEAKS(120, 30, 15),  // max level: 120+50+30 = 200, ridge: 215
        LOW_PEAKS(100, 20, 10),     // max level: 100+50+20 = 170, ridge: 180
        MEADOW(80, 10, 5);          // max level: 80+50+10 = 140, ridge: 145

        private final int landLevel;
        private final int landOffset;
        private final int ridgeOffset;

        MountainHeight(int landLevel, int landOffset, int ridgeOffset) {
            this.landLevel = landLevel;
            this.landOffset = landOffset;
            this.ridgeOffset = ridgeOffset;
        }

        public int getAboveSeaLevel() {
            return landLevel;
        }

        public int getLandOffset() {
            return landOffset;
        }

        public int getRidgeOffset() {
            return ridgeOffset;
        }
    }

    /**
     * Applies mountain-specific default configuration.
     * Sets landLevel and landOffset based on mountain height.
     */
    @Override
    public void applyDefaults() {
        // First apply base defaults from BiomeType enum
        super.applyDefaults();

        // Default to MEDIUM_PEAKS if not specified
        if (height == null) {
            height = MountainHeight.MEDIUM_PEAKS;
        }

        // Apply height-specific parameters
        if (getParameters() == null) {
            setParameters(new HashMap<>());
        }

        // Set landLevel and landOffset based on height
        getParameters().put("g_asl", String.valueOf(height.getAboveSeaLevel()));
        getParameters().put("g_offset", String.valueOf(height.getLandOffset()));

        log.info("Applied MountainBiome defaults for '{}': height={}, landLevel={}, landOffset={}",
            getName(), height, height.getAboveSeaLevel(), height.getLandOffset());
    }

    /**
     * Configures HexGrids for mountains with ridge configurations.
     * Creates ridge=[{"side":"NE","level":200},...] parameters for connected grids.
     */
    @Override
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Call base implementation to create standard FeatureHexGrids
        super.configureHexGrids(coordinates);

        // Build coordinate set for fast neighbor lookups
        Set<String> coordSet = coordinates.stream()
            .map(c -> c.getQ() + ":" + c.getR())
            .collect(Collectors.toSet());

        // Calculate ridge level: landLevel + landOffset + ridgeOffset (+ oceanLevel in builder)
        int ridgeLevel = height.getAboveSeaLevel() + height.getLandOffset() + height.getRidgeOffset();

        // For each grid, check neighbors and create ridge configuration
        for (FeatureHexGrid hexGrid : getHexGrids()) {
            HexVector2 coord = hexGrid.getCoordinate();
            if (coord == null) {
                continue;
            }

            List<Map<String, Object>> ridgeEntries = new ArrayList<>();

            // Check all 6 hex neighbors
            for (int dir = 0; dir < 6; dir++) {
                HexVector2 neighbor = getHexNeighbor(coord, dir);
                String neighborKey = neighbor.getQ() + ":" + neighbor.getR();

                // If neighbor is part of this mountain, add ridge entry
                if (coordSet.contains(neighborKey)) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("side", getDirectionSide(dir).name());
                    entry.put("level", ridgeLevel);
                    ridgeEntries.add(entry);
                }
            }

            // Add ridge configuration if there are connected neighbors
            if (!ridgeEntries.isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String ridgeJson = mapper.writeValueAsString(ridgeEntries);
                    hexGrid.addParameter("g_ridge", ridgeJson);
                    log.debug("Added ridge config to grid {}: {} neighbors",
                        coord.getQ() + ":" + coord.getR(), ridgeEntries.size());
                } catch (Exception e) {
                    log.error("Failed to create ridge JSON for grid {}: {}",
                        coord.getQ() + ":" + coord.getR(), e.getMessage());
                }
            }
        }

        log.info("Configured {} mountain grids with ridge parameters (ridgeLevel={})",
            getHexGrids().size(), ridgeLevel);
    }

    /**
     * Gets the hex neighbor in the specified direction.
     * Directions: 0=N, 1=NE, 2=E, 3=SE, 4=SW, 5=W
     */
    private HexVector2 getHexNeighbor(HexVector2 coord, int direction) {
        int[] delta = getHexDirectionDelta(direction);
        return HexVector2.builder()
            .q(coord.getQ() + delta[0])
            .r(coord.getR() + delta[1])
            .build();
    }

    /**
     * Gets the q,r delta for a hex direction.
     * Directions: 0=N, 1=NE, 2=E, 3=SE, 4=SW, 5=W
     */
    private int[] getHexDirectionDelta(int direction) {
        return switch (direction % 6) {
            case 0 -> new int[]{0, -1};   // N
            case 1 -> new int[]{1, -1};   // NE
            case 2 -> new int[]{1, 0};    // E
            case 3 -> new int[]{0, 1};    // SE
            case 4 -> new int[]{-1, 1};   // SW
            case 5 -> new int[]{-1, 0};   // W
            default -> new int[]{0, 0};
        };
    }

    /**
     * Maps hex direction to WHexGrid.SIDE enum.
     * Hex directions: 0=N(top), 1=NE(top-right), 2=E(right), 3=S(bottom), 4=SW(bottom-left), 5=W(left)
     * SIDE values: NORTH_EAST, EAST, SOUTH_EAST, SOUTH_WEST, WEST, NORTH_WEST (6 sides of hexagon)
     */
    private SIDE getDirectionSide(int direction) {
        return switch (direction % 6) {
            case 0 -> SIDE.NORTH_WEST;  // N (top) -> NORTH_WEST side
            case 1 -> SIDE.NORTH_EAST;  // NE (top-right)
            case 2 -> SIDE.EAST;        // E (right)
            case 3 -> SIDE.SOUTH_EAST;  // S (bottom) -> SOUTH_EAST side
            case 4 -> SIDE.SOUTH_WEST;  // SW (bottom-left)
            case 5 -> SIDE.WEST;        // W (left)
            default -> SIDE.NORTH_EAST;
        };
    }
}
