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
    private int asl;
    private int offset;
    @Setter @Getter
    protected BuilderContext context;

    /**
     * Build terrain composition for the hex grid flat.
     */
    public abstract void buildFlat();

    public void init(Map<String, String> parameters) {
        this.parameters = parameters;
        this.asl = CastUtil.toint(parameters.get("g_asl"), getDefaultAsl());
        this.offset = CastUtil.toint(parameters.get("g_offset"), getDefaultOffset());
    }

    protected abstract int getDefaultOffset();
    protected abstract int getDefaultAsl();

    public abstract int getLandSideLevel(WHexGrid.EDGE side);

    public int getCenterAsl() {
        return asl;
    }

    public int getOffset() {
        return offset;
    }

    public int getHexGridAsl() {
        return asl + context.getWorld().getSeaLevel();
    }

    public int getSeaLevel() {
        return context.getWorld().getSeaLevel();
    }

}
