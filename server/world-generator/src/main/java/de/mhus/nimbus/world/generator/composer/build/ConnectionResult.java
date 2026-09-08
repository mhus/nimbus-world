package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of road and river connection process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionResult {
    /**
     * All hex grids with updated road/river parameters
     */
    private List<WHexGrid> hexGrids;

    /**
     * Number of roads applied
     */
    private int roadsApplied;

    /**
     * Number of rivers applied
     */
    private int riversApplied;

    /**
     * Success flag
     */
    private boolean success;

    /**
     * Error message if failed
     */
    private String errorMessage;
}
