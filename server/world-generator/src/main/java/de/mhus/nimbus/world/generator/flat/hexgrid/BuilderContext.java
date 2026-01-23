package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.FlatManipulatorService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

/**
 * Context object containing all information needed for terrain generation.
 * Passed to CompositionBuilder implementations to avoid too many parameters.
 */
@Builder
@Getter
public class BuilderContext {

    private final WWorld world;

    /**
     * The flat terrain to manipulate.
     */
    private final WFlat flat;

    /**
     * The hex grid configuration for this flat.
     */
    private final WHexGrid hexGrid;

    /**
     * Neighboring hex grids (loaded if they exist).
     * Key is the neighbor position.
     * Value is the loaded WHexGrid or null if neighbor doesn't exist.
     */
    private final Map<WHexGrid.SIDE, WHexGrid> neighborGrids;

    private HexGridBuilderService builderService;

    /**
     * Service for accessing flat manipulators.
     */
    private final FlatManipulatorService manipulatorService;

    /**
     * Service for accessing chunk operations (e.g., noise-based height generation).
     */
    private final WChunkService chunkService;

    public Optional<HexGridBuilder> getBuilderFor(WHexGrid.SIDE neighbor) {
        WHexGrid grid = neighborGrids.get(neighbor);
        if (grid == null) return Optional.empty();
        var ret = builderService.createBuilder(grid);
        ret.ifPresent(b -> b.setContext(this));
        return ret;
    }

}
