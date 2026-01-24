package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Prepared road with resolved waypoints.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PreparedRoad extends PreparedFlow {
    private List<String> waypointIds;
    private String endPointId;
    private String roadType;
    // Note: level is inherited from PreparedFlow as Integer
}
