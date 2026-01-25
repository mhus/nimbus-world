package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.Builder;
import lombok.Data;

/**
 * Context for building features with all necessary parameters.
 * Can be extended with additional parameters as needed.
 */
@Data
@Builder
public class BuildContext {

    /**
     * World ID for the generated grids
     */
    private String worldId;

    /**
     * Optional world object
     */
    private WWorld world;

    /**
     * Random seed for composition
     */
    @Builder.Default
    private Long seed = System.currentTimeMillis();

    /**
     * Whether to fill gaps with ocean/land/coast (default: true)
     */
    @Builder.Default
    private boolean fillGaps = true;

    /**
     * Number of ocean border rings around all features (default: 1)
     */
    @Builder.Default
    private int oceanBorderRings = 1;

    /**
     * Whether to generate WHexGrids (default: false)
     */
    @Builder.Default
    private boolean generateWHexGrids = false;

    /**
     * Optional repository for WHexGrid persistence
     */
    private WHexGridRepository repository;

    /**
     * Creates a minimal context with just worldId and seed
     */
    public static BuildContext of(String worldId, Long seed) {
        return BuildContext.builder()
            .worldId(worldId)
            .seed(seed)
            .build();
    }

    /**
     * Creates a minimal context with just worldId (seed from timestamp)
     */
    public static BuildContext of(String worldId) {
        return BuildContext.builder()
            .worldId(worldId)
            .build();
    }
}
