package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
