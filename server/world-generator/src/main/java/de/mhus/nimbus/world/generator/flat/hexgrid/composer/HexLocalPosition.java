package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a position within a hex grid.
 * Combines hex coordinate (q,r) with local position (lx,lz) within that hex.
 *
 * @deprecated Use de.mhus.nimbus.world.shared.world.HexLocalPosition instead.
 *             This composer-specific version is being phased out in favor of the shared version.
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HexLocalPosition {
    /**
     * Hex coordinate where this position is located.
     */
    private HexVector2 coordinate;

    /**
     * Local X position within the hex grid (0-511 for 512x512 grid).
     */
    private Integer lx;

    /**
     * Local Z position within the hex grid (0-511 for 512x512 grid).
     */
    private Integer lz;

    /**
     * Name of the biome this position is in.
     */
    private String biome;
}
