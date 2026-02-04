package de.mhus.nimbus.world.generator.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.structure.Structure;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a structure that has been placed in the world.
 *
 * A structure is a composite of multiple hex grids that form a cohesive unit
 * (e.g., village, town, castle).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacedStructure {

    /**
     * The structure definition (Village, Town, etc.)
     */
    private Structure structure;

    /**
     * The center grid coordinate of the structure
     */
    private HexVector2 center;

    /**
     * All grid coordinates occupied by this structure
     */
    private List<HexVector2> grids;

    /**
     * Number of grids in this structure
     */
    private int gridCount;

    /**
     * Gets the structure name
     */
    public String getName() {
        return structure != null ? structure.getName() : null;
    }

    /**
     * Gets the structure title
     */
    public String getTitle() {
        return structure != null ? structure.getTitle() : null;
    }
}
