package de.mhus.nimbus.world.generator.composer.village;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a connection point in the village street network.
 * Connection points are places marked as connectionPoint=true that serve
 * as nodes in the street graph spanning across all districts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPoint {

    /**
     * The district grid this connection point belongs to
     */
    private DistrictGrid districtGrid;

    /**
     * The placed place that is the connection point
     */
    private PlacedPlace placedPlace;

    /**
     * District position in the village (relative hex coordinates)
     */
    private HexVector2 districtPosition;

    /**
     * Local X coordinate within the district grid
     */
    private int localX;

    /**
     * Local Z coordinate within the district grid
     */
    private int localZ;

    /**
     * Gets the place definition
     */
    public Place getPlace() {
        return placedPlace != null ? placedPlace.getPlace() : null;
    }

    /**
     * Gets the place name
     */
    public String getName() {
        Place place = getPlace();
        return place != null ? place.getName() : null;
    }
}
