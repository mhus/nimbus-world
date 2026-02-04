package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
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
     * Enum defining the slot size configurations for districts.
     * Each size determines the number and size of building plots available.
     */
    public enum DistrictSlotSize {
        /**
         * One large building slot (e.g., town hall, cathedral, castle)
         */
        BIG(1),

        /**
         * Three medium building slots (e.g., shops, warehouses, large houses)
         */
        MEDIUM(3),

        /**
         * Five small building slots (e.g., houses, workshops)
         */
        SMALL(5),

        /**
         * Seven tiny building slots (e.g., stalls, small huts, storage)
         */
        TINY(7);

        private final int slotCount;

        DistrictSlotSize(int slotCount) {
            this.slotCount = slotCount;
        }

        /**
         * Gets the number of building slots for this size category.
         *
         * @return Number of slots (1, 3, 5, or 7)
         */
        public int getSlotCount() {
            return slotCount;
        }
    }
}
