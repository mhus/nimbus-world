package de.mhus.nimbus.world.generator.composer.town;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.world.generator.composer.point.Direction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * District represents a section of a village with a specific layout and slot configuration.
 * Each district occupies one grid in the village and contains building slots of various sizes.
 *
 * Districts are placed at specific positions within the village grid using axial hex coordinates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class District {

    /**
     * Technical unique name of the district.
     * Used for referencing and identification.
     * Example: "market-square", "residential-north", "central-plaza"
     */
    private String name;

    /**
     * Display name of the district.
     * Human-readable name shown to users.
     * Example: "Market Square", "Northern Residences", "Central Plaza"
     */
    private String title;

    /**
     * Direction relative to anchor district.
     * If null, this is the center district (0,0).
     * Example: N, NE, E, SE, S, SW, W, NW
     */
    private Direction direction;

    /**
     * Name of the anchor district to position relative to.
     * If null, positions relative to village origin (0,0).
     * Example: "center", "market-square"
     */
    private String anchorDistrict;

    /**
     * Size of building slots in this district.
     * Determines how many and what size buildings can be placed.
     *
     * Slot sizes:
     * - BIG: 1 large building slot
     * - MEDIUM: 3 medium building slots
     * - SMALL: 5 small building slots
     * - TINY: 7 tiny building slots
     */
    private DistrictSlotSize slots;

    /**
     * List of places (plots) within this district.
     * Each place defines what can be built at that location:
     * - BuildingPlace: A building plot
     * - FreePlace: An open space (park, square, garden, plaza)
     * - RoadPlace: A street or path
     * - RiverPlace: A water feature
     * - WallPlace: A defensive or boundary structure
     *
     * Places can be marked as connection points for roads, rivers, or walls.
     */
    private List<Place> places;

    /**
     * Target occupancy rate for this district (total fill rate).
     * Value between 0.0 and 1.0:
     * - 0.0 = no slots filled (only explicit places from places list)
     * - 0.75 = district should be 75% occupied
     * - 1.0 = district should be 100% filled
     *
     * If explicit places already exceed fillRate, no additional slots are filled.
     * Additional slots are filled starting with those nearest to streets.
     * If null, defaults to 0.75 (75% occupancy).
     */
    private Double fillRate;

    /**
     * Enum defining the slot size configurations for districts.
     * Each size determines the number and size of building plots available.
     */
    public enum DistrictSlotSize {
        /**
         * One large building slot (e.g., town hall, cathedral, castle)
         * Hexagonal divider: 1, Slots: 1 (center only)
         */
        BIG(1),

        /**
         * Seven medium building slots (e.g., shops, warehouses, large houses)
         * Hexagonal divider: 3, Slots: 7 (center + ring 1)
         */
        MEDIUM(7),

        /**
         * Nineteen small building slots (e.g., houses, workshops)
         * Hexagonal divider: 5, Slots: 19 (center + ring 1 + ring 2)
         */
        SMALL(19),

        /**
         * Thirty-seven tiny building slots (e.g., stalls, small huts, storage)
         * Hexagonal divider: 7, Slots: 37 (center + ring 1 + ring 2 + ring 3)
         */
        TINY(37);

        private final int slotCount;

        DistrictSlotSize(int slotCount) {
            this.slotCount = slotCount;
        }

        /**
         * Gets the number of building slots for this size category.
         *
         * @return Number of hexagonal slots (1, 7, 19, or 37)
         */
        public int getSlotCount() {
            return slotCount;
        }
    }
}
