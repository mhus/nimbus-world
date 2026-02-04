package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of village design process.
 * Contains positioned districts and building assignments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VillageDesignResult {

    /**
     * The village that was designed
     */
    private Village village;

    /**
     * List of district grids with positioned places
     */
    private List<DistrictGrid> districtGrids;

    /**
     * Whether the design was successful
     */
    private boolean success;

    /**
     * List of errors encountered during design
     */
    private List<String> errors;

    /**
     * Gets the total number of districts
     */
    public int getDistrictCount() {
        return districtGrids != null ? districtGrids.size() : 0;
    }

    /**
     * Gets the total number of placed places across all districts
     */
    public int getTotalPlaceCount() {
        if (districtGrids == null) {
            return 0;
        }
        return districtGrids.stream()
            .mapToInt(d -> d.getPlacedPlaces() != null ? d.getPlacedPlaces().size() : 0)
            .sum();
    }
}
