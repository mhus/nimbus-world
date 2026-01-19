package de.mhus.nimbus.world.generator.flat.hexgrid;

/**
 * Interface for building terrain compositions in hex grid flats.
 */
public interface CompositionBuilder {

    /**
     * Build terrain composition for the hex grid flat.
     *
     * @param context BuilderContext containing flat, hex grid, parameters, and neighbor information
     */
    void build(BuilderContext context);

    /**
     * Get the type/scenario name this builder handles.
     */
    String getType();
}
