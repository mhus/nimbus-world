package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Area extends Feature {
    private AreaShape shape;
    private AreaSize size;
    private Integer sizeFrom;
    private Integer sizeTo;
    private List<RelativePosition> positions;

    /**
     * Shape hint for CUSTOM shapes (e.g., "RECTANGLE", "OVAL", "IRREGULAR").
     * Used by BiomeComposer to understand the intended form of custom-shaped areas.
     * Only relevant when shape = CUSTOM.
     */
    private String customShapeHint;

    /**
     * List of feature names that enclose this area (for CUSTOM shapes).
     * Example: Mordor plateau enclosed by mountain ranges.
     * BiomeComposer uses this to place the area within the boundaries of the enclosing features.
     * Only relevant when shape = CUSTOM.
     */
    private List<String> enclosedBy;

    /**
     * Direction deviation for LINE-shaped areas (0.0 = never deviate, 1.0 = always deviate).
     * When placing LINE-shaped areas, this determines the probability of changing direction
     * at each step, creating more organic/natural shapes instead of perfectly straight lines.
     *
     * @deprecated Use tendLeft and tendRight instead for better readability
     */
    @Deprecated
    private Double directionDeviation;

    /**
     * Deviation probability to the left (overrides directionDeviation if set).
     * Allows asymmetric deviation patterns.
     *
     * @deprecated Use tendLeft instead for better readability
     */
    @Deprecated
    private Double deviationLeft;

    /**
     * Deviation probability to the right (overrides directionDeviation if set).
     * Allows asymmetric deviation patterns.
     *
     * @deprecated Use tendRight instead for better readability
     */
    @Deprecated
    private Double deviationRight;

    /**
     * Tendency to deviate left for LINE-shaped areas.
     * Creates more organic/natural shapes instead of perfectly straight lines.
     * Use NONE for straight lines, SLIGHT for subtle curves, MODERATE for natural curves,
     * STRONG for pronounced curves.
     */
    private DeviationTendency tendLeft;

    /**
     * Tendency to deviate right for LINE-shaped areas.
     * Creates more organic/natural shapes instead of perfectly straight lines.
     * Use NONE for straight lines, SLIGHT for subtle curves, MODERATE for natural curves,
     * STRONG for pronounced curves.
     */
    private DeviationTendency tendRight;

    // Calculated values (runtime, set during composition)
    private Integer calculatedSizeFrom;  // Resolved from size enum
    private Integer calculatedSizeTo;    // Resolved from size enum
    private HexVector2 placedCenter;     // Where this area was actually placed
    private List<HexVector2> assignedCoordinates;  // Actual coordinates assigned
    private List<PreparedPosition> preparedPositions;  // Resolved positions with angles

    public int getEffectiveSizeFrom() {
        return sizeFrom != null ? sizeFrom : (size != null ? size.getFrom() : 1);
    }

    public int getEffectiveSizeTo() {
        return sizeTo != null ? sizeTo : (size != null ? size.getTo() : 1);
    }

    /**
     * Prepares this area for composition by calculating concrete values from enums.
     * Called by HexCompositionPreparer before BiomeComposer places the area.
     */
    public void prepareForComposition() {
        // Calculate size ranges from enum
        calculatedSizeFrom = getEffectiveSizeFrom();
        calculatedSizeTo = getEffectiveSizeTo();

        // Prepare positions (convert RelativePosition → PreparedPosition)
        if (positions != null && !positions.isEmpty()) {
            preparedPositions = new java.util.ArrayList<>();
            for (RelativePosition pos : positions) {
                preparedPositions.add(preparePosition(pos));
            }
        } else {
            // No positions specified - create default position at origin
            preparedPositions = new java.util.ArrayList<>();
            PreparedPosition defaultPosition = new PreparedPosition();
            defaultPosition.setAnchor("origin");
            defaultPosition.setDirection(Direction.N);
            defaultPosition.setDirectionAngle(0);
            defaultPosition.setDistanceFrom(0);
            defaultPosition.setDistanceTo(0);
            defaultPosition.setPriority(5);
            preparedPositions.add(defaultPosition);
        }
    }

    /**
     * Converts RelativePosition to PreparedPosition with concrete values
     */
    private PreparedPosition preparePosition(RelativePosition position) {
        PreparedPosition prepared = new PreparedPosition();
        prepared.setOriginal(position);
        prepared.setDirection(position.getDirection());
        prepared.setDirectionAngle(convertDirectionToAngle(position.getDirection()));
        prepared.setDistanceFrom(position.getEffectiveDistanceFrom());
        prepared.setDistanceTo(position.getEffectiveDistanceTo());
        prepared.setAnchor(position.getAnchor());
        prepared.setPriority(position.getPriority());  // priority has default value 5
        return prepared;
    }

    /**
     * Converts Direction enum to angle in degrees
     */
    private int convertDirectionToAngle(Direction direction) {
        if (direction == null) return 0;
        // Pointy-top hex has 6 sides at 60° intervals (no N/S, starts with NE):
        // NE(0°), E(60°), SE(120°), SW(180°), W(240°), NW(300°)
        // N and S are mapped to nearest hex directions
        return switch (direction) {
            case N -> 330;    // North (top spike) → rounds to NW/NE
            case NE -> 0;     // Northeast: top-right side
            case E -> 60;     // East: right side
            case SE -> 120;   // Southeast: bottom-right side
            case S -> 150;    // South (bottom spike) → rounds to SE/SW
            case SW -> 180;   // Southwest: bottom-left side
            case W -> 240;    // West: left side
            case NW -> 300;   // Northwest: top-left side
        };
    }

    /**
     * Gets the effective left deviation probability.
     * Prefers tendLeft enum over deprecated deviationLeft/directionDeviation.
     *
     * @return Probability value (0.0 - 1.0)
     */
    public double getEffectiveDeviationLeft() {
        // Priority 1: tendLeft enum (preferred)
        if (tendLeft != null) {
            return tendLeft.getProbability();
        }

        // Priority 2: deprecated deviationLeft
        if (deviationLeft != null) {
            return Math.max(0.0, Math.min(1.0, deviationLeft));
        }

        // Priority 3: deprecated directionDeviation (split 50/50)
        if (directionDeviation != null) {
            return Math.max(0.0, Math.min(1.0, directionDeviation)) / 2.0;
        }

        return 0.0;
    }

    /**
     * Gets the effective right deviation probability.
     * Prefers tendRight enum over deprecated deviationRight/directionDeviation.
     *
     * @return Probability value (0.0 - 1.0)
     */
    public double getEffectiveDeviationRight() {
        // Priority 1: tendRight enum (preferred)
        if (tendRight != null) {
            return tendRight.getProbability();
        }

        // Priority 2: deprecated deviationRight
        if (deviationRight != null) {
            return Math.max(0.0, Math.min(1.0, deviationRight));
        }

        // Priority 3: deprecated directionDeviation (split 50/50)
        if (directionDeviation != null) {
            return Math.max(0.0, Math.min(1.0, directionDeviation)) / 2.0;
        }

        return 0.0;
    }

    /**
     * Configures HexGrids for this area at the given coordinates.
     * Called by BiomeComposer after placement to let the area configure its own grids.
     * Override in subclasses for type-specific configuration.
     *
     * @param coordinates List of coordinates assigned to this area
     */
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Store assigned coordinates
        this.assignedCoordinates = coordinates;

        // Default implementation - override in subclasses
        // Base areas do nothing special
    }
}
