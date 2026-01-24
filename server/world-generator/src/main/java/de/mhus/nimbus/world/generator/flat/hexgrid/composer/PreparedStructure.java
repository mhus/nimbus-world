package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Prepared structure (village, town, etc.) with calculated hex grid dimensions.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class PreparedStructure extends PreparedArea {
    private int calculatedHexGridWidth;
    private int calculatedHexGridHeight;
}
