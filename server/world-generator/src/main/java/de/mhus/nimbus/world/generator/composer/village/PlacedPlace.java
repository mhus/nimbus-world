package de.mhus.nimbus.world.generator.composer.village;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Place that has been positioned within a district grid.
 * Contains position information and building assignment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacedPlace {

    /**
     * The place definition
     */
    private Place place;

    /**
     * Hexagonal Q coordinate within the local hex grid
     */
    private int hexQ;

    /**
     * Hexagonal R coordinate within the local hex grid
     */
    private int hexR;

    /**
     * Local X coordinate within the grid (0 to grid size)
     * Calculated from hex coordinates in VillageBuilder
     */
    private int localX;

    /**
     * Local Z coordinate within the grid (0 to grid size)
     * Calculated from hex coordinates in VillageBuilder
     */
    private int localZ;

    /**
     * The divider used for this place (1, 3, 5, or 7)
     * Determines the size of the slot this place occupies
     */
    private int divider;

    /**
     * Slot index within the district
     */
    private int slotIndex;

    /**
     * Building ID assigned to this place (for BuildingPlace only)
     */
    private String buildingId;

    /**
     * Whether the assigned building is oversized (exceeds normal slot size but within tolerance)
     */
    private boolean oversized;

    /**
     * Rotation of the building in degrees (0, 90, 180, 270)
     * Determines which direction the building faces relative to streets
     */
    private int rotation;

    /**
     * Gets the place name
     */
    public String getName() {
        return place != null ? place.getName() : null;
    }

    /**
     * Checks if this is a connection point
     */
    public boolean isConnectionPoint() {
        return place != null && place.isConnectionPoint();
    }
}
