package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Prepared area with concrete size ranges and positions.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class PreparedArea extends PreparedFeature {
    private AreaShape shape;
    private int sizeFrom;
    private int sizeTo;
    private List<PreparedPosition> positions;
}
