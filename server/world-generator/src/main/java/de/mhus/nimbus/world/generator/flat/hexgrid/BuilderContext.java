package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Context object containing all information needed for terrain generation.
 * Passed to CompositionBuilder implementations to avoid too many parameters.
 */
@Builder
@Getter
public class BuilderContext {

    /**
     * The flat terrain to manipulate.
     */
    private final WFlat flat;

    /**
     * The hex grid configuration for this flat.
     */
    private final WHexGrid hexGrid;

    /**
     * Scenario types of neighboring hex grids.
     * Key is the neighbor position (TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, LEFT, TOP_LEFT).
     * Value is the scenario type (g.type parameter) or null if neighbor doesn't exist or has no type.
     */
    private final Map<WHexGrid.NEIGHBOR, String> neighborTypes;

    /**
     * Neighboring hex grids (loaded if they exist).
     * Key is the neighbor position.
     * Value is the loaded WHexGrid or null if neighbor doesn't exist.
     */
    private final Map<WHexGrid.NEIGHBOR, WHexGrid> neighborGrids;
}
