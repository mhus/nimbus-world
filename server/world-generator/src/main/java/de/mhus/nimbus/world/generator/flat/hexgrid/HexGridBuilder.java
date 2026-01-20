package de.mhus.nimbus.world.generator.flat.hexgrid;

import java.util.Map;

/**
 * Interface for building terrain compositions in hex grid flats.
 */
public abstract class HexGridBuilder {

    protected Map<String, String> parameters;

    /**
     * Build terrain composition for the hex grid flat.
     *
     * @param context BuilderContext containing flat, hex grid, parameters, and neighbor information
     */
    public abstract void build(BuilderContext context);

    public void init(Map<String, String> parameters) {
        this.parameters = parameters;
    }
}
