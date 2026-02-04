package de.mhus.nimbus.world.generator.composer.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a river connection between two points.
 * Grid coordinates and sides are calculated during composition from point positions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiverConnection {
    /**
     * Start point ID
     */
    private String fromPointId;

    /**
     * End point ID
     */
    private String toPointId;

    /**
     * River width
     */
    private int width;

    /**
     * River depth
     */
    private int depth;

    /**
     * River bed level
     */
    private int level;

    /**
     * Optional group ID for the entire river
     */
    private String groupId;
}
