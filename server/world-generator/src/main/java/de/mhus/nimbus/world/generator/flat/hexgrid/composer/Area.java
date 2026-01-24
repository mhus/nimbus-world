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
        return switch (direction) {
            case N -> 0;
            case NE -> 60;
            case E -> 120;
            case SE -> 180;
            case S -> 240;
            case SW -> 300;
            case W -> 360;  // 360 == 0
            case NW -> 300;  // NW same as SW in hex
        };
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
