package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.world.shared.world.WHexGrid.EDGE;
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
     * Side where flow enters the grid (null if this is the start point or if fromLx/fromLz is used)
     * DEPRECATED: Use fromPosition instead for precise edge positioning
     */
    private EDGE fromSide;

    /**
     * Side where flow exits the grid (null if this is the end point or if toLx/toLz is used)
     * DEPRECATED: Use toPosition instead for precise edge positioning
     */
    private EDGE toSide;

    /**
     * HexLocal position string where flow enters (e.g., "<NE 2>" for grid-to-grid transitions)
     * Takes precedence over fromSide when set
     */
    private String fromPosition;

    /**
     * HexLocal position string where flow exits (e.g., "<SW 2>" for grid-to-grid transitions)
     * Takes precedence over toPosition when set
     */
    private String toPosition;

    /**
     * Local X coordinate where flow enters (alternative to fromSide/fromPosition, used when endpoint is a Point)
     */
    private Integer fromLx;

    /**
     * Local Z coordinate where flow enters (alternative to fromSide, used when endpoint is a Point)
     */
    private Integer fromLz;

    /**
     * Local X coordinate where flow exits (alternative to toSide, used when endpoint is a Point)
     */
    private Integer toLx;

    /**
     * Local Z coordinate where flow exits (alternative to toSide, used when endpoint is a Point)
     */
    private Integer toLz;

    /**
     * Width of the flow in blocks
     */
    private Integer width;

    /**
     * Level/height of the flow (deprecated - use fromLevel/toLevel instead)
     * @deprecated Use fromLevel and toLevel for proper level transitions
     */
    @Deprecated
    private Integer level;

    /**
     * Level/height at flow entry point (where flow enters the grid)
     */
    private Integer fromLevel;

    /**
     * Level/height at flow exit point (where flow exits the grid)
     */
    private Integer toLevel;

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
     * Returns true if this is a start segment (no fromSide, fromPosition and no fromLx/fromLz)
     */
    public boolean isStartSegment() {
        return fromSide == null && fromPosition == null && fromLx == null && fromLz == null;
    }

    /**
     * Returns true if this is an end segment (no toSide, toPosition and no toLx/toLz)
     */
    public boolean isEndSegment() {
        return toSide == null && toPosition == null && toLx == null && toLz == null;
    }

    /**
     * Returns true if this is a through segment (has both entry and exit)
     */
    public boolean isThroughSegment() {
        boolean hasFrom = fromSide != null || fromPosition != null || (fromLx != null && fromLz != null);
        boolean hasTo = toSide != null || toPosition != null || (toLx != null && toLz != null);
        return hasFrom && hasTo;
    }

    /**
     * Returns true if entry point uses coordinates instead of side/position
     */
    public boolean hasFromCoordinates() {
        return fromLx != null && fromLz != null;
    }

    /**
     * Returns true if exit point uses coordinates instead of side/position
     */
    public boolean hasToCoordinates() {
        return toLx != null && toLz != null;
    }

    /**
     * Returns true if entry point uses HexLocal position string
     */
    public boolean hasFromPosition() {
        return fromPosition != null && !fromPosition.isEmpty();
    }

    /**
     * Returns true if exit point uses HexLocal position string
     */
    public boolean hasToPosition() {
        return toPosition != null && !toPosition.isEmpty();
    }
}
