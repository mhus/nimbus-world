package de.mhus.nimbus.world.generator.composer.town;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.flow.StreetSegment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a District that has been positioned as a grid in the world.
 * Contains the district definition and all placed places within it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictGrid {

    /**
     * The district definition
     */
    private District district;

    /**
     * The grid position in world coordinates (axial hex coordinates)
     */
    private HexVector2 gridPosition;

    /**
     * List of places that have been positioned within this district
     */
    private List<PlacedPlace> placedPlaces;

    /**
     * Street segments within this district
     */
    private List<StreetSegment> streets;

    /**
     * Gets the district name
     */
    public String getName() {
        return district != null ? district.getName() : null;
    }

    /**
     * Gets the district title
     */
    public String getTitle() {
        return district != null ? district.getTitle() : null;
    }
}
