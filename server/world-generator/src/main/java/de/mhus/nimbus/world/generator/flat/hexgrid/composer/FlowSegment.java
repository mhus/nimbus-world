package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.world.shared.world.WHexGrid.SIDE;
import lombok.*;

/**
 * Represents a flow (road/river/wall) segment through a single HexGrid.
 * Stores connection information for how the flow enters and exits the grid.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowSegment {

    /**
     * Type of flow (ROAD, RIVER, WALL)
     */
    private FlowType flowType;

    /**
     * Side where flow enters the grid (null if this is the start point)
     */
    private SIDE fromSide;

    /**
     * Side where flow exits the grid (null if this is the end point)
     */
    private SIDE toSide;

    /**
     * Width of the flow in blocks
     */
    private Integer width;

    /**
     * Level/height of the flow
     */
    private Integer level;

    /**
     * Type-specific attribute (e.g., "cobblestone" for road, null for rivers)
     */
    private String type;

    /**
     * Depth for rivers (not used for roads/walls)
     */
    private Integer depth;

    /**
     * Material for walls (e.g., "stone", "wood")
     */
    private String material;

    /**
     * Height for walls
     */
    private Integer height;

    /**
     * Optional group ID to track which flow feature this belongs to
     */
    private String flowFeatureId;

    /**
     * Optional name for debugging
     */
    private String segmentName;

    /**
     * Returns true if this is a start segment (no fromSide)
     */
    public boolean isStartSegment() {
        return fromSide == null;
    }

    /**
     * Returns true if this is an end segment (no toSide)
     */
    public boolean isEndSegment() {
        return toSide == null;
    }

    /**
     * Returns true if this is a through segment (has both fromSide and toSide)
     */
    public boolean isThroughSegment() {
        return fromSide != null && toSide != null;
    }
}
