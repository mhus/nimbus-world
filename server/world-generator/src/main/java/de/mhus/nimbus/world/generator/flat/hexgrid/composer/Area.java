package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.mhus.nimbus.generated.types.HexVector2;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Area extends Feature {
    private AreaShape shape;
    private AreaSize size;
    private Integer sizeFrom;
    private Integer sizeTo;
    private List<RelativePosition> positions;

    public int getEffectiveSizeFrom() {
        return sizeFrom != null ? sizeFrom : (size != null ? size.getFrom() : 1);
    }

    public int getEffectiveSizeTo() {
        return sizeTo != null ? sizeTo : (size != null ? size.getTo() : 1);
    }

    /**
     * Configures HexGrids for this area at the given coordinates.
     * Called by BiomeComposer after placement to let the area configure its own grids.
     * Override in subclasses for type-specific configuration.
     *
     * @param coordinates List of coordinates assigned to this area
     */
    public void configureHexGrids(List<HexVector2> coordinates) {
        // Default implementation - override in subclasses
        // Base areas do nothing special
    }
}
