package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Prepared river with resolved waypoints.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PreparedRiver extends PreparedFlow {
    private List<String> waypointIds;
    private String mergeToId;
    private Integer depth;
    // Note: level is inherited from PreparedFlow as Integer
}
