package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Prepared wall with resolved waypoints.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PreparedWall extends PreparedFlow {
    private List<String> waypointIds;
    private String endPointId;
    private Integer height;
    private String material;
}
