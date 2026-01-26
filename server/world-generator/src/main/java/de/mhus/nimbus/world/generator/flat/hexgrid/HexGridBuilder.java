package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Interface for building terrain compositions in hex grid flats.
 */
public abstract class HexGridBuilder {

    protected Map<String, String> parameters;
    private int landLevel;
    private int landOffset;
    @Setter @Getter
    protected BuilderContext context;

    /**
     * Build terrain composition for the hex grid flat.
     */
    public abstract void buildFlat();

    public void init(Map<String, String> parameters) {
        this.parameters = parameters;
        this.landLevel = CastUtil.toint(parameters.get("g_asl"), getDefaultLandLevel());
        this.landOffset = CastUtil.toint(parameters.get("g_offset"), getDefaultLandOffset());
    }

    protected abstract int getDefaultLandOffset();
    protected abstract int getDefaultLandLevel();

    public abstract int getLandSideLevel(WHexGrid.SIDE side);

    public int getLandCenterLevel() {
        return landLevel;
    }

    public int getLandOffset() {
        return landOffset;
    }

    public int getHexGridLevel() {
        return landLevel + context.getWorld().getSeaLevel();
    }

    public int getOceanLevel() {
        return context.getWorld().getSeaLevel();
    }

}
