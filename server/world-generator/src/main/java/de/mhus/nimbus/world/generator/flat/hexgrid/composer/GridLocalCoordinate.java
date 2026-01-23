package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a coordinate that includes both the HexGrid position and local flat coordinates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridLocalCoordinate {
    /**
     * Which HexGrid this coordinate belongs to (e.g., (0,0), (1,0), etc.)
     */
    private HexVector2 gridPosition;

    /**
     * Local X position within the flat grid (0-511 for flatSize=512)
     */
    private int localX;

    /**
     * Local Z position within the flat grid (0-511 for flatSize=512)
     */
    private int localZ;
}
