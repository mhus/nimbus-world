package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a point position relative to another point in the same biome.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelativeToPoint {
    /**
     * ID of the point this is relative to (must be in the same biome)
     */
    private String pointId;

    /**
     * Direction from the reference point
     */
    private Direction direction;

    /**
     * Distance in blocks from the reference point
     */
    private Integer distance;
}
